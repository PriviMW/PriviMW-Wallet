package com.privimemobile.chat.processor

import android.util.Log
import com.privimemobile.chat.ChatService
import com.privimemobile.chat.contacts.ContactManager
import com.privimemobile.chat.db.ChatDatabase
import com.privimemobile.chat.db.entities.*
import com.privimemobile.protocol.Helpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
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
        // Wait up to 10s for identity to be resolved (myHandle set)
        // Prevents losing messages consumed from SBBS before identity is ready
        var state = db.chatStateDao().get()
        var retries = 0
        while (state?.myHandle == null && retries < 20) {
            Log.d(TAG, "Waiting for identity (attempt ${retries + 1}/20)...")
            kotlinx.coroutines.delay(500)
            state = db.chatStateDao().get()
            retries++
        }

        val myHandle = state?.myHandle
        if (myHandle == null) {
            Log.w(TAG, "DROP BATCH: myHandle still null after 10s — ${rawMessages.size} messages lost")
            return
        }
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
        val payload = extractPayload(raw) ?: run { Log.w(TAG, "DROP: no payload in $raw"); return }

        // Version check
        val version = (payload["v"] as? Number)?.toInt() ?: run { Log.w(TAG, "DROP: no version in ${payload.keys}"); return }
        if (version != 1) { Log.w(TAG, "DROP: version=$version"); return }

        val type = payload["t"] as? String ?: "dm"
        val ts = (payload["ts"] as? Number)?.toLong() ?: (raw["timestamp"] as? Number)?.toLong() ?: run { Log.w(TAG, "DROP: no timestamp"); return }

        val msgPreview = (payload["msg"] as? String)?.take(20) ?: type
        Log.d(TAG, "Processing: type=$type from=${payload["from"]} msg=$msgPreview ts=$ts")

        // Filter: skip messages before contract start
        if (contractStartTs > 0 && ts < contractStartTs) { Log.d(TAG, "DROP: ts=$ts < contractStartTs=$contractStartTs"); return }

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
            "delivered" -> handleDelivered(payload, convKey)
            "typing" -> handleTyping(from, ts)
            "react" -> handleReaction(payload, convKey, from)
            "unreact" -> handleUnreact(payload, from)
            "delete" -> handleDelete(payload, convKey, from)
            "edit" -> handleEdit(payload, convKey, from)
            "disappear_config" -> handleDisappearConfig(payload, convKey)
            "poll_vote" -> handlePollVote(payload, convKey, from, false)
            "poll_unvote" -> handlePollVote(payload, convKey, from, true)
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

        // Compute expiresAt from TTL if present
        val ttl = (payload["ttl"] as? Number)?.toLong() ?: 0
        val expiresAt = if (ttl > 0) (System.currentTimeMillis() / 1000) + ttl else 0L

        // Build message entity
        val message = MessageEntity(
            conversationId = conv.id,
            text = text,
            timestamp = ts,
            sent = sent,
            type = type,
            replyText = payload["reply"] as? String,
            tipAmount = (payload["amount"] as? Number)?.toLong() ?: 0,
            tipAssetId = (payload["asset_id"] as? Number)?.toInt() ?: 0,
            fwdFrom = payload["fwd_from"] as? String,
            fwdTs = (payload["fwd_ts"] as? Number)?.toLong() ?: 0,
            senderHandle = from.ifEmpty { null },
            sbbsDedupKey = dedupKey,
            expiresAt = expiresAt,
            pollData = payload["poll"] as? String,
        )

        // Insert — IGNORE on duplicate
        val messageId = db.messageDao().insert(message)
        if (messageId == -1L) { Log.d(TAG, "DEDUP: $dedupKey (${text?.take(20)})"); return }

        // Un-delete conversation if tombstoned (genuinely new message passed tombstone + dedup checks)
        if (conv.deletedAtTs > 0) {
            db.conversationDao().undelete(conv.id)
            Log.d(TAG, "Un-deleted conversation ${conv.convKey} for new message")
        }

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
            "tip" -> "Tip: ${Helpers.grothToBeam(message.tipAmount)} ${com.privimemobile.wallet.assetTicker(message.tipAssetId)}"
            "file" -> {
                val fileName = (payload["file"] as? Map<*, *>)?.get("name") as? String
                "\uD83D\uDCCE ${fileName ?: "File"}"  // 📎
            }
            else -> text?.take(100)
        }
        db.conversationDao().updateLastMessage(conv.id, ts, preview)

        // Increment unread + show notification (only for received, not in active chat)
        if (!sent && ChatService.activeChat.value != convKey) {
            db.conversationDao().incrementUnread(conv.id)
            // Show notification
            val isMuted = db.conversationDao().isMuted(conv.id) ?: false
            val totalUnread = db.conversationDao().getTotalUnread()
            val senderLabel = displayName?.ifEmpty { null } ?: from.ifEmpty { "Unknown" }
            val notifText = when (type) {
                "tip" -> preview ?: "Sent a tip"
                "file" -> preview ?: "Sent a file"
                else -> text?.take(200) ?: ""
            }
            com.privimemobile.chat.notification.ChatNotificationManager.notifyMessage(
                convKey = convKey,
                convId = conv.id,
                senderName = senderLabel,
                text = notifText,
                type = type,
                isMuted = isMuted,
                totalUnread = totalUnread,
            )
        }

        // Send ack for received messages
        if (!sent) {
            if (ChatService.activeChat.value == convKey) {
                // Chat is open — send read receipt directly (also marks delivered)
                ChatService.sendAcksForConv(convKey, conv.id)
            } else {
                // Chat is closed — send delivery ack only
                ChatService.sbbs.sendDeliveryAck(convKey, listOf(ts))
            }
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
        Log.d(TAG, "handleAck: payload=$payload convKey=$convKey")
        val readTimestamps = (payload["read"] as? List<*>)?.mapNotNull {
            (it as? Number)?.toLong()
        } ?: run { Log.w(TAG, "handleAck: no 'read' timestamps in payload"); return }

        val conv = db.conversationDao().findByKey(convKey) ?: run { Log.w(TAG, "handleAck: conv not found for $convKey"); return }
        db.messageDao().markRead(conv.id, readTimestamps)
        Log.d(TAG, "Marked ${readTimestamps.size} messages as read in $convKey (convId=${conv.id})")
    }

    /** Handle delivery ack — recipient confirmed they received our message. */
    private suspend fun handleDelivered(payload: Map<String, Any?>, convKey: String) {
        val deliveredTimestamps = (payload["delivered"] as? List<*>)?.mapNotNull {
            (it as? Number)?.toLong()
        } ?: run { Log.w(TAG, "handleDelivered: no 'delivered' timestamps in payload"); return }

        val conv = db.conversationDao().findByKey(convKey) ?: run { Log.w(TAG, "handleDelivered: conv not found for $convKey"); return }
        db.messageDao().markDelivered(conv.id, deliveredTimestamps)
        Log.d(TAG, "Marked ${deliveredTimestamps.size} messages as delivered in $convKey")
    }

    /** Handle typing indicator (ephemeral, no DB storage). */
    private fun handleTyping(from: String, ts: Long) {
        // Ignore stale typing indicators (older than 10 seconds)
        val now = System.currentTimeMillis() / 1000
        if (now - ts > 10) return
        val convKey = "@$from"
        ChatService.onTypingReceived(convKey)
    }

    /** Handle reaction. */
    private suspend fun handleReaction(payload: Map<String, Any?>, convKey: String, from: String) {
        val msgTs = (payload["msg_ts"] as? Number)?.toLong() ?: return
        val emoji = payload["emoji"] as? String ?: return
        val ts = (payload["ts"] as? Number)?.toLong() ?: System.currentTimeMillis() / 1000

        val insertId = db.reactionDao().insert(ReactionEntity(
            messageTs = msgTs,
            senderHandle = from,
            emoji = emoji,
            timestamp = ts,
        ))
        if (insertId == -1L) {
            // Row exists — try reactivate ONLY if incoming ts is newer than removal time.
            // Old SBBS re-deliveries have ts <= removal time → ignored.
            // Genuine re-reacts have ts > removal time → reactivated.
            db.reactionDao().reactivate(msgTs, from, emoji, ts)
        }
        Log.d(TAG, "Reaction $emoji from @$from on message $msgTs")
    }

    /** Handle unreact (remove reaction for everyone). */
    private suspend fun handleUnreact(payload: Map<String, Any?>, from: String) {
        val msgTs = (payload["msg_ts"] as? Number)?.toLong() ?: return
        val emoji = payload["emoji"] as? String ?: return
        val unreactTs = (payload["ts"] as? Number)?.toLong() ?: System.currentTimeMillis() / 1000
        db.reactionDao().remove(msgTs, from, emoji, unreactTs)
        Log.d(TAG, "Unreact $emoji from @$from on message $msgTs")
    }

    /** Handle disappear_config — other party changed the disappearing message timer. */
    private suspend fun handleDisappearConfig(payload: Map<String, Any?>, convKey: String) {
        val timer = (payload["timer"] as? Number)?.toInt() ?: 0
        val conv = db.conversationDao().findByKey(convKey) ?: return
        db.conversationDao().setDisappearTimer(conv.id, timer)
        Log.d(TAG, "Disappear timer set to ${timer}s for $convKey")
    }

    /** Handle poll vote/unvote (single-choice). */
    private suspend fun handlePollVote(payload: Map<String, Any?>, convKey: String, from: String, isUnvote: Boolean) {
        val msgTs = (payload["msg_ts"] as? Number)?.toLong() ?: return
        val optIdx = (payload["option"] as? Number)?.toInt() ?: return
        val conv = db.conversationDao().findByKey(convKey) ?: return
        val pollMsg = db.messageDao().findPollByTimestamp(conv.id, msgTs) ?: return
        val msgId = pollMsg.id
        val pollData = pollMsg.pollData ?: return
        try {
            val pollObj = JSONObject(pollData)
            val options = pollObj.optJSONArray("options") ?: return
            if (optIdx >= options.length()) return

            if (isUnvote) {
                // Check if actually voted on this option
                val opt = options.getJSONObject(optIdx)
                val voters = opt.optJSONArray("voters") ?: JSONArray()
                var found = false
                for (i in 0 until voters.length()) { if (voters.getString(i) == from) { found = true; break } }
                if (!found) return // not voted — nothing to unvote
                val cleaned = JSONArray()
                for (i in 0 until voters.length()) {
                    if (voters.getString(i) != from) cleaned.put(voters.getString(i))
                }
                opt.put("voters", cleaned)
                options.put(optIdx, opt)
            } else {
                // Check if already voted on this exact option — skip if duplicate
                val currentVoters = options.getJSONObject(optIdx).optJSONArray("voters") ?: JSONArray()
                for (i in 0 until currentVoters.length()) {
                    if (currentVoters.getString(i) == from) return // already voted here, skip
                }
                // Single choice: remove from all options first
                for (j in 0 until options.length()) {
                    val o = options.getJSONObject(j)
                    val v = o.optJSONArray("voters") ?: JSONArray()
                    val cleaned = JSONArray()
                    for (i in 0 until v.length()) {
                        if (v.getString(i) != from) cleaned.put(v.getString(i))
                    }
                    o.put("voters", cleaned)
                    options.put(j, o)
                }
                // Add vote to tapped option
                val voters = options.getJSONObject(optIdx).optJSONArray("voters") ?: JSONArray()
                voters.put(from)
                options.getJSONObject(optIdx).put("voters", voters)
            }

            pollObj.put("options", options)
            db.messageDao().updatePollData(msgId, pollObj.toString())
            Log.d(TAG, "Poll ${if (isUnvote) "unvote" else "vote"} from @$from on option $optIdx for ts=$msgTs")
        } catch (e: Exception) {
            Log.w(TAG, "Poll vote error: ${e.message}")
        }
    }

    /** Handle message edit. */
    private suspend fun handleEdit(payload: Map<String, Any?>, convKey: String, from: String) {
        val msgTs = (payload["msg_ts"] as? Number)?.toLong() ?: return
        val newText = payload["msg"] as? String ?: return
        val conv = db.conversationDao().findByKey(convKey) ?: return
        db.messageDao().editMessage(conv.id, msgTs, from, newText)
        // Update chat list preview if this was the latest message
        if (conv.lastMessageTs == msgTs) {
            db.conversationDao().updateLastMessage(conv.id, msgTs, newText.take(100))
        }
        Log.d(TAG, "Edit from @$from, ts=$msgTs, newText=${newText.take(30)}")
    }

    /** Handle delete-for-everyone. */
    private suspend fun handleDelete(payload: Map<String, Any?>, convKey: String, from: String) {
        val msgTs = (payload["msg_ts"] as? Number)?.toLong() ?: return
        val conv = db.conversationDao().findByKey(convKey) ?: return
        db.messageDao().markDeleted(conv.id, msgTs, from)
        // Update chat list preview if the deleted message was the latest
        if (conv.lastMessageTs == msgTs) {
            updateConversationPreview(conv.id)
        }
        Log.d(TAG, "Delete for everyone from @$from, ts=$msgTs")
    }

    /** Update conversation preview to reflect the latest non-deleted message. */
    private suspend fun updateConversationPreview(convId: Long) {
        val latest = db.messageDao().getLatestMessage(convId)
        if (latest != null) {
            val preview = when (latest.type) {
                "tip" -> "Tip"
                "file" -> "\uD83D\uDCCE File"
                else -> latest.text?.take(100)
            }
            db.conversationDao().updateLastMessage(convId, latest.timestamp, preview)
        } else {
            db.conversationDao().updateLastMessage(convId, 0, null)
        }
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
