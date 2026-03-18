package com.privimemobile.chat.processor

import android.util.Log
import com.privimemobile.chat.ChatService
import com.privimemobile.chat.contacts.ContactManager
import com.privimemobile.chat.db.ChatDatabase
import com.privimemobile.chat.db.entities.*
import com.privimemobile.protocol.Helpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.MessageDigest

/**
 * MessageProcessor — parses raw SBBS messages → inserts into Room DB.
 *
 * Handles all message types: dm, tip, file, ack, react, delete, typing.
 * Dedup via unique index on sbbs_dedup_key (OnConflictStrategy.IGNORE).
 *
 * Ports message-processor.ts from the RN build.
 */
class MessageProcessor(
    private val db: ChatDatabase,
    private val contacts: ContactManager,
    private val scope: CoroutineScope,
) {
    private val TAG = "MessageProcessor"

    /**
     * Process a batch of raw SBBS messages.
     * Called by SbbsTransport after read_messages.
     */
    suspend fun processRawMessages(rawMessages: List<*>) {
        val state = db.chatStateDao().get() ?: return
        val myHandle = state.myHandle ?: return
        val contractStartTs = state.contractStartTs

        for (raw in rawMessages) {
            try {
                val msg = raw as? Map<*, *> ?: continue
                processOneMessage(msg, myHandle, contractStartTs)
            } catch (e: Exception) {
                Log.w(TAG, "Error processing message: ${e.message}")
            }
        }
    }

    private suspend fun processOneMessage(
        raw: Map<*, *>,
        myHandle: String,
        contractStartTs: Long,
    ) {
        // Extract payload — try .message, then .payload, then the object itself
        val payload = extractPayload(raw) ?: return

        // Version check
        val version = (payload["v"] as? Number)?.toInt() ?: return
        if (version != 1) return

        val type = payload["t"] as? String ?: "dm"
        val ts = (payload["ts"] as? Number)?.toLong() ?: (raw["timestamp"] as? Number)?.toLong() ?: return

        // Filter: skip messages before contract start
        if (contractStartTs > 0 && ts < contractStartTs) return

        // Sanitize sender handle
        val fromRaw = payload["from"] as? String ?: ""
        val from = sanitizeHandle(fromRaw)
        val toRaw = payload["to"] as? String ?: ""
        val to = sanitizeHandle(toRaw)
        val senderWalletId = raw["sender"] as? String

        // Determine if this is sent by us
        val sent = from == myHandle

        // Skip if from blocked user
        val convKey = if (sent) "@$to" else if (from.isNotEmpty()) "@$from" else senderWalletId ?: return
        val isBlocked = db.conversationDao().isBlocked(convKey) ?: false
        if (isBlocked && !sent) return

        // Check tombstone
        val tombstoneTs = db.conversationDao().getDeletedTs(convKey)
        if (tombstoneTs != null && tombstoneTs > 0 && ts < tombstoneTs) return

        // Route by message type
        when (type) {
            "ack" -> handleAck(payload, convKey)
            "typing" -> handleTyping(from)
            "react" -> handleReaction(payload, convKey, from)
            "delete" -> handleDelete(payload, convKey, from)
            else -> {
                // Clear typing indicator when a real message arrives from this person
                if (!sent) ChatService.clearTyping(convKey)
                handleMessage(payload, raw, type, ts, from, to, sent, convKey, senderWalletId)
            }
        }
    }

    /** Handle dm/tip/file messages. */
    private suspend fun handleMessage(
        payload: Map<String, Any?>,
        raw: Map<*, *>,
        type: String,
        ts: Long,
        from: String,
        to: String,
        sent: Boolean,
        convKey: String,
        senderWalletId: String?,
    ) {
        val text = payload["msg"] as? String
        val displayName = Helpers.fixBvmUtf8(payload["dn"] as? String)

        // Build dedup key: "timestamp:hash:sent"
        val hashInput = "${ts}:${text ?: ""}:${type}:$sent"
        val hash = hashInput.hashCode().toString(16)
        val dedupKey = "$ts:$hash:$sent"

        // Get or create conversation
        val conv = db.conversationDao().getOrCreate(
            convKey = convKey,
            handle = if (sent) to else from,
            displayName = displayName,
            walletId = if (sent) null else senderWalletId,
        )

        // Build message entity
        val message = MessageEntity(
            conversationId = conv.id,
            text = text,
            timestamp = ts,
            sent = sent,
            type = type,
            replyText = payload["reply"] as? String,
            tipAmount = (payload["amount"] as? Number)?.toLong() ?: 0,
            fwdFrom = payload["fwd_from"] as? String,
            fwdTs = (payload["fwd_ts"] as? Number)?.toLong() ?: 0,
            senderHandle = from.ifEmpty { null },
            sbbsDedupKey = dedupKey,
        )

        // Insert — IGNORE on duplicate
        val messageId = db.messageDao().insert(message)
        if (messageId == -1L) return  // Duplicate, skip

        // Handle file attachment
        if (type == "file") {
            val rawFile = payload["file"]
            Log.d(TAG, "File attachment: type=${rawFile?.javaClass?.simpleName}, value=${rawFile.toString().take(100)}")
            val fileData = rawFile as? Map<*, *>
            if (fileData != null) {
                insertAttachment(messageId, conv.id, fileData)
            }
        }

        // Update conversation
        val preview = when (type) {
            "tip" -> "Tip: ${Helpers.grothToBeam(message.tipAmount)} BEAM"
            "file" -> {
                val fileName = (payload["file"] as? Map<*, *>)?.get("name") as? String
                "\uD83D\uDCCE ${fileName ?: "File"}"  // 📎
            }
            else -> text?.take(100)
        }
        db.conversationDao().updateLastMessage(conv.id, ts, preview)

        // Increment unread (only for received, not in active chat)
        if (!sent && ChatService.activeChat.value != convKey) {
            db.conversationDao().incrementUnread(conv.id)
        }

        // Queue contact resolution for unknown senders
        if (!sent && from.isNotEmpty()) {
            contacts.ensureContact(from, displayName, senderWalletId)
        }

        Log.d(TAG, "Inserted ${if (sent) "sent" else "received"} $type message in $convKey")
    }

    /** Insert file attachment for a message. */
    private suspend fun insertAttachment(messageId: Long, convId: Long, fileData: Map<*, *>) {
        Log.d(TAG, "insertAttachment: msgId=$messageId, keys=${fileData.keys}")
        val key = fileData["key"] as? String
        val iv = fileData["iv"] as? String
        Log.d(TAG, "  key=${key?.take(16)}... (${key?.length}), iv=${iv?.take(12)}... (${iv?.length})")
        if (key == null || iv == null || key.length != 64 || iv.length != 24) {
            Log.w(TAG, "  Invalid key/iv — skipping attachment")
            return
        }

        val cid = fileData["cid"] as? String
        val inlineData = fileData["data"] as? String

        // Need either CID (IPFS) or data (inline)
        if (cid == null && inlineData == null) return

        val attachment = AttachmentEntity(
            messageId = messageId,
            conversationId = convId,
            ipfsCid = cid ?: "inline-${System.currentTimeMillis().toString(36)}",
            encryptionKey = key,
            encryptionIv = iv,
            fileName = sanitizeFilename(fileData["name"] as? String ?: "file"),
            fileSize = (fileData["size"] as? Number)?.toLong() ?: 0,
            mimeType = fileData["mime"] as? String ?: "application/octet-stream",
            inlineData = inlineData,
            downloadStatus = if (inlineData != null) "idle" else "idle",
        )
        db.attachmentDao().insert(attachment)
    }

    /** Handle ack (read receipt) message. */
    private suspend fun handleAck(payload: Map<String, Any?>, convKey: String) {
        val readTimestamps = (payload["read"] as? List<*>)?.mapNotNull {
            (it as? Number)?.toLong()
        } ?: return

        val conv = db.conversationDao().findByKey(convKey) ?: return
        db.messageDao().markRead(conv.id, readTimestamps)
        Log.d(TAG, "Marked ${readTimestamps.size} messages as read in $convKey")
    }

    /** Handle typing indicator (ephemeral, no DB storage). */
    private fun handleTyping(from: String) {
        val convKey = "@$from"
        Log.d(TAG, "Typing indicator from $convKey")
        ChatService.onTypingReceived(convKey)
    }

    /** Handle reaction. */
    private suspend fun handleReaction(payload: Map<String, Any?>, convKey: String, from: String) {
        val msgTs = (payload["msg_ts"] as? Number)?.toLong() ?: return
        val emoji = payload["emoji"] as? String ?: return
        val ts = (payload["ts"] as? Number)?.toLong() ?: System.currentTimeMillis() / 1000

        db.reactionDao().insert(ReactionEntity(
            messageTs = msgTs,
            senderHandle = from,
            emoji = emoji,
            timestamp = ts,
        ))
        Log.d(TAG, "Reaction $emoji from @$from on message $msgTs")
    }

    /** Handle delete-for-everyone. */
    private suspend fun handleDelete(payload: Map<String, Any?>, convKey: String, from: String) {
        val msgTs = (payload["msg_ts"] as? Number)?.toLong() ?: return
        val conv = db.conversationDao().findByKey(convKey) ?: return
        db.messageDao().markDeleted(conv.id, msgTs, from)
        Log.d(TAG, "Delete for everyone from @$from, ts=$msgTs")
    }

    /** Extract payload from raw SBBS message. */
    private fun extractPayload(raw: Map<*, *>): Map<String, Any?>? {
        // Try .message field (parsed object)
        val message = raw["message"]
        if (message is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            return message as Map<String, Any?>
        }

        // Try parsing .message as JSON string
        if (message is String) {
            try {
                val json = JSONObject(message)
                val map = mutableMapOf<String, Any?>()
                json.keys().forEach { key -> map[key] = json.opt(key) }
                return map
            } catch (_: Exception) {}
        }

        // Try .payload field
        val payload = raw["payload"]
        if (payload is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            return payload as Map<String, Any?>
        }

        return null
    }

    /** Sanitize handle: strip @, lowercase, keep alphanumeric + underscore. */
    private fun sanitizeHandle(handle: String): String {
        return handle.trimStart('@').lowercase().replace(Regex("[^a-z0-9_]"), "")
    }

    /** Sanitize filename: remove special chars, limit length. */
    private fun sanitizeFilename(name: String): String {
        val clean = name.replace(Regex("[^a-zA-Z0-9._\\- ]"), "")
        return if (clean.length > 80) clean.take(80) else clean.ifEmpty { "file" }
    }
}
