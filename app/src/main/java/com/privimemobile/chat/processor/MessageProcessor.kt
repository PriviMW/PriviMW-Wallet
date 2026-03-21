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
        val senderWalletId = raw["sender"] as? String

        // Determine if this is sent by us
        val sent = from == myHandle

        // ── Group payloads: route FIRST, before DM convKey/blocked/tombstone checks ──
        // Group payloads have "to" = target handle (for tips) or groupId, NOT a DM recipient.
        // Running DM convKey logic on group payloads causes wrong routing/drops.
        val groupId = payload["group_id"] as? String
        if (groupId != null && groupId.isNotEmpty()) {
            when (type) {
                "group_msg" -> handleGroupMessage(payload, raw, ts, from, sent, myHandle)
                "group_service" -> handleGroupService(payload, from)
                "group_info_update" -> handleGroupInfoUpdate(payload, from)
                "group_info_request" -> handleGroupInfoRequest(payload, from, groupId)
                "group_info_response" -> handleGroupInfoResponse(payload, groupId)
                "group_delete" -> handleGroupDelete(payload, from)
                else -> handleGroupGenericPayload(payload, raw, type, ts, from, sent, myHandle, groupId)
            }
            return
        }

        // ── DM / 1-on-1 messages below ──
        val toRaw = payload["to"] as? String ?: ""
        val to = sanitizeHandle(toRaw)

        // Skip if from blocked user
        val convKey = if (sent) "@$to" else if (from.isNotEmpty()) "@$from" else senderWalletId ?: return
        val isBlocked = db.conversationDao().isBlocked(convKey) ?: false
        if (isBlocked && !sent) return

        // Check tombstone
        val tombstoneTs = db.conversationDao().getDeletedTs(convKey)
        if (tombstoneTs != null && tombstoneTs > 0 && ts < tombstoneTs) return

        // Route by message type (DM / 1-on-1 only)
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
            "profile_update" -> handleProfileUpdate(payload, from)
            "avatar_request" -> handleAvatarRequest(from, senderWalletId)
            "avatar_response" -> handleAvatarResponse(payload, from)
            "group_invite" -> handleGroupInvite(payload, ts, from, sent, convKey)
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

        // Sticker pack metadata
        val stickerPackName = payload["pack_name"] as? String
        val stickerPackId = payload["pack_id"] as? String
        val stickerEmoji = payload["sticker_emoji"] as? String
        val stickerPackTotal = (payload["pack_total"] as? Number)?.toInt() ?: 0

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
            stickerPackName = stickerPackName,
            stickerPackId = stickerPackId,
            stickerEmoji = stickerEmoji,
            stickerPackTotal = stickerPackTotal,
        )

        // Insert — IGNORE on duplicate
        val messageId = db.messageDao().insert(message)
        if (messageId == -1L) { Log.d(TAG, "DEDUP: $dedupKey (${text?.take(20)})"); return }

        // Un-delete conversation if tombstoned (genuinely new message passed tombstone + dedup checks)
        if (conv.deletedAtTs > 0) {
            db.conversationDao().undelete(conv.id)
            Log.d(TAG, "Un-deleted conversation ${conv.convKey} for new message")
        }

        // Handle file attachment (file, sticker, and sticker_pack types carry file data)
        if (type == "file" || type == "sticker" || type == "sticker_pack") {
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
            "sticker" -> "${stickerEmoji ?: "\uD83C\uDFAD"} Sticker"
            "sticker_pack" -> "\uD83D\uDCE6 Sticker pack: ${stickerPackName ?: "Stickers"}"
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
    /** Rate-limit avatar responses: max 1 per contact per hour. */
    private val avatarResponseTimes = mutableMapOf<String, Long>()

    /** Handle avatar_request: someone wants our avatar. Auto-respond (with security checks). */
    private suspend fun handleAvatarRequest(from: String, senderWalletId: String?) {
        if (senderWalletId.isNullOrEmpty()) return

        // Security: only respond to known contacts
        val contact = db.contactDao().findByHandle(from) ?: run {
            Log.d(TAG, "Ignoring avatar_request from unknown handle @$from")
            return
        }

        // Security: rate-limit — max 1 response per contact per hour
        val now = System.currentTimeMillis() / 1000
        val lastResponse = avatarResponseTimes[from] ?: 0
        if (now - lastResponse < 3600) {
            Log.d(TAG, "Rate-limited avatar_request from @$from (last=${now - lastResponse}s ago)")
            return
        }

        val filesDir = com.privimemobile.chat.transport.IpfsTransport.filesDir ?: return
        val myAvatarFile = java.io.File(filesDir, "my_avatar.webp")
        if (!myAvatarFile.exists()) return

        val state = db.chatStateDao().get() ?: return
        val myHandle = state.myHandle ?: return
        val avatarHash = state.myAvatarCid ?: return

        val bytes = myAvatarFile.readBytes()
        val base64Data = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)

        val payload = mapOf(
            "v" to 1, "t" to "avatar_response",
            "ts" to System.currentTimeMillis() / 1000,
            "from" to myHandle, "to" to from,
            "avatar_hash" to avatarHash,
            "avatar_data" to base64Data,
        )
        ChatService.sbbs.sendWithRetry(senderWalletId, payload)
        avatarResponseTimes[from] = now
        Log.d(TAG, "Sent avatar_response to @$from (${bytes.size} bytes)")
    }

    /** Handle avatar_response: received avatar image we requested. Verify hash before saving. */
    private suspend fun handleAvatarResponse(payload: Map<String, Any?>, from: String) {
        val avatarData = payload["avatar_data"] as? String ?: return
        val claimedHash = payload["avatar_hash"] as? String ?: return

        // Security: verify image hash matches on-chain avatar_hash for this contact
        val contact = db.contactDao().findByHandle(from)
        val onChainHash = contact?.avatarCid
        if (onChainHash != null && onChainHash != claimedHash) {
            Log.w(TAG, "Avatar hash mismatch for @$from: claimed=$claimedHash, on-chain=$onChainHash — REJECTED")
            return
        }

        // Verify SHA-256 of received data matches claimed hash
        try {
            val bytes = android.util.Base64.decode(avatarData, android.util.Base64.NO_WRAP)
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val computedHash = digest.digest(bytes).joinToString("") { "%02x".format(it) }
            if (computedHash != claimedHash) {
                Log.w(TAG, "Avatar data hash mismatch for @$from: computed=$computedHash, claimed=$claimedHash — REJECTED")
                return
            }

            // Hash verified — safe to save
            val filesDir = com.privimemobile.chat.transport.IpfsTransport.filesDir ?: return
            val avatarDir = java.io.File(filesDir, "avatars").also { it.mkdirs() }
            val avatarFile = java.io.File(avatarDir, "${from}.webp")
            avatarFile.writeBytes(bytes)
            db.contactDao().updateAvatarHash(from, claimedHash)
            Log.d(TAG, "Saved verified avatar for @$from (${bytes.size} bytes)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to process avatar for @$from: ${e.message}")
        }
    }

    /** Handle profile_update: receive avatar image from contact (with hash verification). */
    private suspend fun handleProfileUpdate(payload: Map<String, Any?>, from: String) {
        val avatarHash = payload["avatar_hash"] as? String ?: return
        val avatarData = payload["avatar_data"] as? String
        val displayName = com.privimemobile.protocol.Helpers.fixBvmUtf8(payload["dn"] as? String)

        // Update display name if provided
        if (displayName != null) {
            db.contactDao().updateDisplayName(from, displayName)
        }

        // Save avatar image locally if data provided (with hash verification)
        if (avatarData != null) {
            try {
                val bytes = android.util.Base64.decode(avatarData, android.util.Base64.NO_WRAP)

                // Verify SHA-256 hash matches claimed hash
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                val computedHash = digest.digest(bytes).joinToString("") { "%02x".format(it) }
                if (computedHash != avatarHash) {
                    Log.w(TAG, "profile_update avatar hash mismatch for @$from — REJECTED")
                    return
                }

                val filesDir = com.privimemobile.chat.transport.IpfsTransport.filesDir ?: return
                val avatarDir = java.io.File(filesDir, "avatars").also { it.mkdirs() }
                val avatarFile = java.io.File(avatarDir, "${from}.webp")
                avatarFile.writeBytes(bytes)
                db.contactDao().updateAvatarHash(from, avatarHash)
                Log.d(TAG, "Saved verified avatar for @$from (${bytes.size} bytes)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save avatar for @$from: ${e.message}")
            }
        }
    }

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
    /** Handle group_invite — someone invited us to join a group. Store as a special DM message. */
    private suspend fun handleGroupInvite(
        payload: Map<String, Any?>,
        ts: Long,
        from: String,
        sent: Boolean,
        convKey: String,
    ) {
        if (sent) return // ignore our own invite echoes
        val groupId = payload["invite_group_id"] as? String ?: return
        val groupName = payload["group_name"] as? String ?: "Group"
        val memberCount = (payload["member_count"] as? Number)?.toInt() ?: 0
        val displayName = Helpers.fixBvmUtf8(payload["dn"] as? String)

        val conv = db.conversationDao().getOrCreate(convKey, from, displayName)
        if (conv.deletedAtTs > 0) db.conversationDao().undelete(conv.id)

        val dedupKey = "$ts:group_invite:$groupId:$from".hashCode().toString(16)
        val joinPassword = payload["join_password"] as? String
        val inviteText = "\uD83D\uDC65 Group invite: $groupName ($memberCount members)"
        val inviteData = org.json.JSONObject().apply {
            put("group_id", groupId)
            put("group_name", groupName)
            put("invited_by", from)
            put("member_count", memberCount)
            if (joinPassword != null) put("join_password", joinPassword)
        }.toString()

        val entity = MessageEntity(
            conversationId = conv.id,
            timestamp = ts,
            senderHandle = from,
            text = inviteText,
            type = "group_invite",
            sent = false,
            sbbsDedupKey = dedupKey,
            pollData = inviteData, // reuse pollData to store invite metadata
        )
        val insertedId = db.messageDao().insert(entity)
        if (insertedId == -1L) return

        db.conversationDao().updateLastMessage(conv.id, ts, inviteText)
        if (!conv.muted) {
            com.privimemobile.chat.notification.ChatNotificationManager.notifyMessage(
                convKey = convKey, convId = conv.id,
                senderName = displayName ?: "@$from",
                text = inviteText, type = "group_invite",
                isMuted = false, totalUnread = 0,
            )
        }
        db.conversationDao().incrementUnread(conv.id)
        Log.d(TAG, "Group invite from @$from for '$groupName' (groupId=$groupId)")
    }

    private fun sanitizeHandle(handle: String): String {
        return handle.trimStart('@').lowercase().replace(Regex("[^a-z0-9_]"), "")
    }

    /** Sanitize filename: remove special chars, limit length. */
    private fun sanitizeFilename(name: String): String {
        val clean = name.replace(Regex("[^a-zA-Z0-9._\\- ]"), "")
        return if (clean.length > 80) clean.take(80) else clean.ifEmpty { "file" }
    }

    // ========================================================================
    // Group message handlers
    // ========================================================================

    /** Handle group_msg — regular group chat message. */
    private suspend fun handleGroupMessage(
        payload: Map<String, Any?>,
        raw: Map<*, *>,
        ts: Long,
        from: String,
        sent: Boolean,
        myHandle: String,
    ) {
        val groupId = payload["group_id"] as? String ?: return
        val text = payload["msg"] as? String
        val displayName = Helpers.fixBvmUtf8(payload["dn"] as? String)

        // Verify group exists in our DB
        val group = db.groupDao().findByGroupId(groupId)
        if (group == null) {
            Log.w(TAG, "DROP group_msg: unknown group $groupId")
            return
        }

        // Get or create conversation for this group
        val convId = ChatService.groups.getOrCreateGroupConversation(groupId, group.name)

        // Parse ALL payload fields (same as DM handler)
        val replyText = payload["reply"] as? String
        val ttl = (payload["ttl"] as? Number)?.toLong() ?: 0
        val expiresAt = if (ttl > 0) (System.currentTimeMillis() / 1000) + ttl else 0L
        val fwdFrom = payload["fwd_from"] as? String
        val fwdTs = (payload["fwd_ts"] as? Number)?.toLong() ?: 0

        // Dedup key includes group context
        val dedupKey = "$ts:${text.hashCode().toString(16)}:$from:$groupId".hashCode().toString(16)

        // Build message entity with ALL fields
        val entity = MessageEntity(
            conversationId = convId,
            timestamp = ts,
            senderHandle = from,
            text = text,
            type = "group_msg",
            sent = sent,
            sbbsDedupKey = dedupKey,
            replyText = replyText,
            expiresAt = expiresAt,
            fwdFrom = fwdFrom,
            fwdTs = fwdTs,
        )

        val insertedId = db.messageDao().insert(entity)
        if (insertedId == -1L) return // Dedup — already exists

        // Update group last message + unread
        val senderLabel = if (sent) "You" else (displayName ?: "@$from")
        val preview = "$senderLabel: ${text?.take(40) ?: "message"}"
        db.groupDao().updateLastMessage(groupId, ts, preview)

        if (!sent) {
            db.groupDao().incrementUnread(groupId)

            // Notification (if not muted)
            if (!group.muted) {
                val groupConvKey = "g_${groupId.take(16)}"
                com.privimemobile.chat.notification.ChatNotificationManager.notifyMessage(
                    convKey = groupConvKey,
                    convId = convId,
                    senderName = "${group.name}: $senderLabel",
                    text = text ?: "sent a message",
                    type = "group_msg",
                    isMuted = false,
                    totalUnread = 0,
                )
            }
        }

        // Update member display name if provided
        if (displayName != null && from.isNotEmpty()) {
            db.groupDao().updateMemberDisplayName(groupId, from, displayName)
        }

        Log.d(TAG, "Group msg in $groupId from @$from: ${text?.take(20)}")
    }

    /**
     * Handle generic group payloads — tip, file, sticker, react, unreact, delete, edit, poll, poll_vote, etc.
     * These arrive with group_id set but their original type preserved.
     * Route them to existing DM handlers but with group context (group convKey/convId).
     */
    private suspend fun handleGroupGenericPayload(
        payload: Map<String, Any?>,
        raw: Map<*, *>,
        type: String,
        ts: Long,
        from: String,
        sent: Boolean,
        myHandle: String,
        groupId: String,
    ) {
        val group = db.groupDao().findByGroupId(groupId)
        if (group == null) {
            Log.w(TAG, "DROP group generic ($type): unknown group $groupId")
            return
        }
        val groupConvKey = "g_${groupId.take(16)}"
        val convId = ChatService.groups.getOrCreateGroupConversation(groupId, group.name)

        when (type) {
            "react" -> handleReaction(payload, groupConvKey, from)
            "unreact" -> handleUnreact(payload, from)
            "delete" -> handleDelete(payload, groupConvKey, from)
            "edit" -> handleEdit(payload, groupConvKey, from)
            "poll_vote" -> handlePollVote(payload, groupConvKey, from, false)
            "poll_unvote" -> handlePollVote(payload, groupConvKey, from, true)
            "group_pin" -> {
                // Pin/unpin a message by timestamp in the group conversation
                val msgTs = (payload["msg_ts"] as? Number)?.toLong() ?: return
                val isPinning = payload["pin"] == true
                if (isPinning) {
                    db.messageDao().pinByTimestamp(convId, msgTs)
                } else {
                    db.messageDao().unpinByTimestamp(convId, msgTs)
                }
                Log.d(TAG, "Group pin: ts=$msgTs pin=$isPinning in $groupId by @$from")
            }
            "tip", "file", "sticker", "sticker_pack", "poll" -> {
                // Insert as a group message with the original type preserved
                val toHandle = payload["to"] as? String
                val rawText = payload["msg"] as? String
                // For tips: encode target handle so UI can show "Tip to @handle"
                val text = if (type == "tip" && !toHandle.isNullOrEmpty()) {
                    "\u2192@$toHandle" + if (!rawText.isNullOrEmpty()) "\n$rawText" else ""
                } else rawText
                val displayName = Helpers.fixBvmUtf8(payload["dn"] as? String)
                val replyText = payload["reply"] as? String
                val ttl = (payload["ttl"] as? Number)?.toInt() ?: 0
                val expiresAt = if (ttl > 0) ts + ttl else 0L

                val fwdFrom = payload["fwd_from"] as? String
                val fwdTs = (payload["fwd_ts"] as? Number)?.toLong() ?: 0

                val dedupKey = "$ts:$type:$from:$groupId".hashCode().toString(16)
                val entity = MessageEntity(
                    conversationId = convId,
                    timestamp = ts,
                    senderHandle = from,
                    text = text,
                    type = type,
                    sent = sent,
                    sbbsDedupKey = dedupKey,
                    replyText = replyText,
                    expiresAt = expiresAt,
                    fwdFrom = fwdFrom,
                    fwdTs = fwdTs,
                    tipAmount = (payload["amount"] as? Number)?.toLong() ?: 0,
                    tipAssetId = (payload["asset_id"] as? Number)?.toInt() ?: 0,
                    pollData = payload["poll"] as? String,
                    stickerPackName = payload["pack_name"] as? String,
                    stickerPackId = payload["pack_id"] as? String,
                    stickerEmoji = payload["sticker_emoji"] as? String,
                    stickerPackTotal = (payload["pack_total"] as? Number)?.toInt() ?: 0,
                )
                val insertedId = db.messageDao().insert(entity)
                if (insertedId == -1L) return // Dedup

                // Handle file attachment if present
                val fileData = payload["file"] as? Map<*, *>
                if (fileData != null && insertedId > 0) {
                    val cid = fileData["cid"] as? String ?: ""
                    if (cid.isNotEmpty()) {
                        db.attachmentDao().insert(
                            AttachmentEntity(
                                messageId = insertedId,
                                conversationId = convId,
                                ipfsCid = cid,
                                encryptionKey = fileData["key"] as? String ?: "",
                                encryptionIv = fileData["iv"] as? String ?: "",
                                fileName = fileData["name"] as? String ?: "file",
                                fileSize = (fileData["size"] as? Number)?.toLong() ?: 0,
                                mimeType = fileData["mime"] as? String ?: "",
                                inlineData = fileData["data"] as? String,
                                downloadStatus = "idle",
                            )
                        )
                    }
                }

                // Update group preview + unread
                val senderLabel = if (sent) "You" else (displayName ?: "@$from")
                val preview = when (type) {
                    "tip" -> "$senderLabel: Tip"
                    "file" -> "$senderLabel: \uD83D\uDCCE File"
                    "sticker" -> "$senderLabel: Sticker"
                    "sticker_pack" -> "$senderLabel: \uD83D\uDCE6 Sticker pack"
                    "poll" -> "$senderLabel: \uD83D\uDCCA ${text ?: "Poll"}"
                    else -> "$senderLabel: ${text?.take(40) ?: type}"
                }
                db.groupDao().updateLastMessage(groupId, ts, preview)

                if (!sent) {
                    db.groupDao().incrementUnread(groupId)
                    if (!group.muted) {
                        com.privimemobile.chat.notification.ChatNotificationManager.notifyMessage(
                            convKey = groupConvKey,
                            convId = convId,
                            senderName = "${group.name}: $senderLabel",
                            text = preview,
                            type = type,
                            isMuted = false,
                            totalUnread = 0,
                        )
                    }
                }

                if (displayName != null && from.isNotEmpty()) {
                    db.groupDao().updateMemberDisplayName(groupId, from, displayName)
                }

                Log.d(TAG, "Group $type in $groupId from @$from")
            }
            else -> {
                Log.w(TAG, "Unknown group payload type: $type in group $groupId")
            }
        }
    }

    /** Handle group_service — member join/leave/kick/ban/promote notifications. */
    private suspend fun handleGroupService(payload: Map<String, Any?>, from: String) {
        val groupId = payload["group_id"] as? String ?: return
        val action = payload["action"] as? String ?: return
        val target = payload["target"] as? String

        val group = db.groupDao().findByGroupId(groupId) ?: return
        val convId = ChatService.groups.getOrCreateGroupConversation(groupId, group.name)

        val serviceText = when (action) {
            "joined" -> "@${target ?: from} joined the group"
            "left" -> "@$from left the group"
            "kicked" -> "@$target was removed by @$from"
            "banned" -> "@$target was banned by @$from"
            "promoted" -> "@$target was promoted to admin by @$from"
            "demoted" -> "@$target was demoted by @$from"
            "ownership_transferred" -> "@$from transferred ownership to @$target"
            "group_deleted" -> "Group was deleted by @$from"
            else -> "$action by @$from"
        }

        // Check if I'm being removed or group is deleted — clean up locally
        val myHandle = db.chatStateDao().get()?.myHandle
        if (action == "group_deleted") {
            db.groupDao().deleteByGroupId(groupId)
            db.groupDao().removeAllMembers(groupId)
            Log.d(TAG, "Group $groupId deleted by @$from — removed locally")
            return
        }
        if ((action == "kicked" || action == "banned") && target == myHandle) {
            db.groupDao().deleteByGroupId(groupId)
            db.groupDao().removeAllMembers(groupId)
            Log.d(TAG, "I was $action from group $groupId — removed locally")
            return
        }

        // Insert as a service message — dedup uses action+target (no timestamp, prevents spam from multiple clicks)
        val svcTs = (payload["ts"] as? Number)?.toLong() ?: (System.currentTimeMillis() / 1000)
        val dedupKey = "svc:$action:${target ?: from}:$groupId".hashCode().toString(16)
        val entity = MessageEntity(
            conversationId = convId,
            timestamp = svcTs,
            senderHandle = from,
            text = serviceText,
            type = "group_service",
            sent = false,
            sbbsDedupKey = dedupKey,
        )
        val insertedId = db.messageDao().insert(entity)
        if (insertedId == -1L) return // Dedup — already inserted this service message

        // Update preview
        db.groupDao().updateLastMessage(groupId, entity.timestamp, serviceText)

        // Update local member list immediately (count comes from contract refresh)
        when (action) {
            "joined" -> {
                val joinedHandle = target ?: from
                if (db.groupDao().findMember(groupId, joinedHandle) == null) {
                    db.groupDao().insertMember(com.privimemobile.chat.db.entities.GroupMemberEntity(
                        groupId = groupId, handle = joinedHandle, role = 0,
                    ))
                }
            }
            "left" -> db.groupDao().removeMember(groupId, from)
            "kicked" -> if (target != null) db.groupDao().removeMember(groupId, target)
            "banned" -> if (target != null) db.groupDao().removeMember(groupId, target)
            "promoted" -> if (target != null) db.groupDao().updateMemberRole(groupId, target, 1, 0)
            "demoted" -> if (target != null) db.groupDao().updateMemberRole(groupId, target, 0, 0)
            "ownership_transferred" -> if (target != null) {
                db.groupDao().updateMemberRole(groupId, target, 2, 0)
                db.groupDao().updateMemberRole(groupId, from, 1, 0)
            }
        }

        // Refresh from contract — gets correct member count + full member list
        scope.launch {
            kotlinx.coroutines.delay(3000)
            ChatService.groups.refreshGroupInfo(groupId)
            ChatService.groups.refreshGroupMembers(groupId)
        }

        // Auto-send group avatar to new member if we have it locally
    }

    /** Handle group_info_update — group name/settings/avatar/description changed. */
    private suspend fun handleGroupInfoUpdate(payload: Map<String, Any?>, from: String) {
        val groupId = payload["group_id"] as? String ?: return

        // Update description if provided
        val description = payload["description"] as? String
        if (description != null) {
            db.groupDao().updateDescription(groupId, description)
        }

        // Update group avatar if provided (base64 image bytes via SBBS)
        val avatarBase64 = payload["avatar"] as? String
        val avatarHash = payload["avatar_hash"] as? String
        if (avatarBase64 != null) {
            try {
                val bytes = android.util.Base64.decode(avatarBase64, android.util.Base64.NO_WRAP)
                // Verify hash if provided
                val valid = if (avatarHash != null) {
                    val digest = java.security.MessageDigest.getInstance("SHA-256")
                    val computed = digest.digest(bytes).joinToString("") { "%02x".format(it) }
                    computed == avatarHash
                } else true
                if (valid) {
                    val filesDir = com.privimemobile.chat.transport.IpfsTransport.filesDir ?: return
                    val dir = java.io.File(filesDir, "group_avatars")
                    dir.mkdirs()
                    java.io.File(dir, "$groupId.webp").writeBytes(bytes)
                    db.groupDao().updateAvatarHash(groupId, avatarHash)
                    Log.d(TAG, "Group avatar updated for $groupId (${bytes.size} bytes)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save group avatar for $groupId: ${e.message}")
            }
        }

        // Refresh group info from contract
        scope.launch {
            kotlinx.coroutines.delay(2000)
            ChatService.groups.refreshGroupInfo(groupId)
        }

        // Insert service message in chat
        val changeText = payload["change"] as? String
            ?: if (avatarBase64 != null) "updated group picture"
            else if (description != null) "updated group description"
            else "updated group info"
        val group2 = db.groupDao().findByGroupId(groupId) ?: return
        val convId = ChatService.groups.getOrCreateGroupConversation(groupId, group2.name)
        val svcTs = (payload["ts"] as? Number)?.toLong() ?: (System.currentTimeMillis() / 1000)
        val serviceText = "@$from $changeText"
        val dedupKey = "$svcTs:info_update:$from:$groupId".hashCode().toString(16)
        val entity = MessageEntity(
            conversationId = convId,
            timestamp = svcTs,
            senderHandle = from,
            text = serviceText,
            type = "group_service",
            sent = false,
            sbbsDedupKey = dedupKey,
        )
        val insertedId = db.messageDao().insert(entity)
        if (insertedId != -1L) {
            db.groupDao().updateLastMessage(groupId, svcTs, serviceText)
        }
    }

    /**
     * Handle group_info_request — someone asks for group avatar + description.
     * If we have them locally, respond directly to the requester.
     */
    private suspend fun handleGroupInfoRequest(payload: Map<String, Any?>, from: String, groupId: String) {
        val group = db.groupDao().findByGroupId(groupId) ?: return
        val senderWalletId = payload["requester_wallet_id"] as? String ?: return

        val filesDir = com.privimemobile.chat.transport.IpfsTransport.filesDir
        val avatarFile = if (filesDir != null) java.io.File(filesDir, "group_avatars/$groupId.webp") else null
        val hasAvatar = avatarFile?.exists() == true
        val hasDesc = !group.description.isNullOrEmpty()

        if (!hasAvatar && !hasDesc) return // nothing to send

        val state = db.chatStateDao().get() ?: return
        val response = mutableMapOf<String, Any?>(
            "v" to 1,
            "t" to "group_info_response",
            "ts" to System.currentTimeMillis() / 1000,
            "from" to (state.myHandle ?: ""),
            "group_id" to groupId,
        )
        if (hasAvatar) {
            val bytes = avatarFile!!.readBytes()
            val hash = java.security.MessageDigest.getInstance("SHA-256")
                .digest(bytes).joinToString("") { "%02x".format(it) }
            response["avatar"] = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            response["avatar_hash"] = hash
        }
        if (hasDesc) {
            response["description"] = group.description
        }

        try {
            ChatService.sbbs.sendOnce(senderWalletId, response)
            Log.d(TAG, "Responded to group_info_request from @$from for $groupId (avatar=$hasAvatar, desc=$hasDesc)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to respond to group_info_request: ${e.message}")
        }
    }

    /**
     * Handle group_info_response — someone sent us the group avatar + description we requested.
     * Save silently (no service message in chat).
     */
    private suspend fun handleGroupInfoResponse(payload: Map<String, Any?>, groupId: String) {
        val group = db.groupDao().findByGroupId(groupId) ?: return

        // Save description
        val description = payload["description"] as? String
        if (description != null && group.description.isNullOrEmpty()) {
            db.groupDao().updateDescription(groupId, description)
            Log.d(TAG, "Received group description for $groupId")
        }

        // Save avatar
        val avatarBase64 = payload["avatar"] as? String
        val avatarHash = payload["avatar_hash"] as? String
        if (avatarBase64 != null) {
            try {
                val bytes = android.util.Base64.decode(avatarBase64, android.util.Base64.NO_WRAP)
                val valid = if (avatarHash != null) {
                    val digest = java.security.MessageDigest.getInstance("SHA-256")
                    val computed = digest.digest(bytes).joinToString("") { "%02x".format(it) }
                    computed == avatarHash
                } else true
                if (valid) {
                    val filesDir = com.privimemobile.chat.transport.IpfsTransport.filesDir ?: return
                    val dir = java.io.File(filesDir, "group_avatars")
                    dir.mkdirs()
                    java.io.File(dir, "$groupId.webp").writeBytes(bytes)
                    db.groupDao().updateAvatarHash(groupId, avatarHash)
                    Log.d(TAG, "Received group avatar for $groupId (${bytes.size} bytes)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save group avatar response: ${e.message}")
            }
        }
    }

    /** Handle group_delete — admin deleted a message for everyone. */
    private suspend fun handleGroupDelete(payload: Map<String, Any?>, from: String) {
        val groupId = payload["group_id"] as? String ?: return
        val msgTs = (payload["msg_ts"] as? Number)?.toLong() ?: return
        val senderHandle = payload["msg_sender"] as? String ?: return

        val group = db.groupDao().findByGroupId(groupId) ?: return
        val convId = ChatService.groups.getOrCreateGroupConversation(groupId, group.name)

        // Soft-delete the message
        db.messageDao().markDeleted(convId, msgTs, senderHandle)
    }
}
