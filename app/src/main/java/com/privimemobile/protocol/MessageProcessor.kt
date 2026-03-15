package com.privimemobile.protocol

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Message processor — core routing engine.
 *
 * Fully ports message-processor.ts from RN build.
 * Pure logic: takes raw SBBS messages and updates conversation state.
 * Includes file attachment parsing, tip/reply support, read receipt marking.
 */
object MessageProcessor {
    private const val TAG = "MessageProcessor"

    /**
     * Process a batch of raw SBBS messages into conversation state.
     *
     * @param rawMessages JSON array string from read_messages API
     * @param state Current protocol state
     * @return Updated conversations, contacts, unread counts, pending resolves
     */
    fun processMessages(
        rawMessages: String,
        state: ProtocolState,
    ): ProcessResult {
        val messages = try { JSONArray(rawMessages) } catch (_: Exception) { JSONArray() }
        val conversations = state.conversations.mapValues { it.value.toMutableList() }.toMutableMap()
        val contacts = state.contacts.toMutableMap()
        val unreadCounts = state.unreadCounts.toMutableMap()
        val newUnread = mutableMapOf<String, Int>()
        val pendingResolves = mutableListOf<PendingResolve>()

        for (i in 0 until messages.length()) {
            val raw = messages.optJSONObject(i) ?: continue
            val payload = extractPayload(raw) ?: continue
            if (payload.optInt("v") != 1) continue

            val ts = payload.optLong("ts", 0)
            if (ts > 0 && ts < state.contractStartTs) continue

            val type = payload.optString("t", "")

            // Sanitize payload.from — match RN exactly
            var from = payload.optString("from", "")
                .removePrefix("@").lowercase()
                .replace(Regex("[^a-z0-9_]"), "")
            if (from.isEmpty() && type != "ack") continue

            // Handle read receipts (ack messages)
            if (type == "ack") {
                val readArr = payload.optJSONArray("read")
                if (readArr != null && from.isNotEmpty()) {
                    val ackConvKey = "@$from"
                    val msgs = conversations[ackConvKey]?.toMutableList()
                    if (msgs != null) {
                        for (j in 0 until readArr.length()) {
                            val ackTs = readArr.optLong(j)
                            msgs.forEachIndexed { idx, msg ->
                                if (msg.sent && msg.timestamp == ackTs && !msg.read) {
                                    msgs[idx] = msg.copy(read = true)
                                }
                            }
                        }
                        conversations[ackConvKey] = msgs
                    }
                }
                continue
            }

            if (type !in listOf("dm", "tip", "file")) continue

            // Skip blocked users
            if (from.isNotEmpty() && state.blockedUsers.contains(from)) continue

            val senderWalletId = (raw.optString("sender", "").trim()
                .ifEmpty { raw.optString("from", "").trim() })
            if (senderWalletId.isEmpty()) continue

            val text = payload.optString("msg", "")
            val displayName = Helpers.fixBvmUtf8(payload.optString("dn", ""))

            // Detect sent messages
            val sent = state.myHandle != null &&
                from.isNotEmpty() &&
                from == state.myHandle.handle.lowercase()

            // Determine conversation key
            val convKey: String
            if (sent) {
                val to = payload.optString("to", "").lowercase()
                if (to.isEmpty()) {
                    val receiverWid = raw.optString("receiver", "").trim()
                        .ifEmpty { raw.optString("to", "").trim() }
                    if (receiverWid.isEmpty()) continue
                    convKey = receiverWid
                } else {
                    convKey = "@$to"
                }
            } else if (from.isNotEmpty()) {
                val hKey = "@$from"
                if (!contacts.containsKey(hKey)) {
                    contacts[hKey] = Contact(
                        handle = from,
                        displayName = displayName.ifEmpty { from },
                        walletId = "",
                    )
                }
                // Update display name if changed
                val existing = contacts[hKey]!!
                if (displayName.isNotEmpty() && existing.displayName != displayName) {
                    contacts[hKey] = existing.copy(displayName = displayName)
                }
                // Queue resolution if no wallet_id
                if (existing.walletId.isEmpty() && !existing.resolving) {
                    contacts[hKey] = (contacts[hKey] ?: existing).copy(resolving = true)
                    pendingResolves.add(PendingResolve("handle", hKey, from))
                }
                convKey = hKey
            } else {
                convKey = senderWalletId
                if (!contacts.containsKey(senderWalletId)) {
                    contacts[senderWalletId] = Contact(
                        handle = "",
                        displayName = "",
                        walletId = senderWalletId,
                        resolving = true,
                    )
                    pendingResolves.add(PendingResolve("walletid", senderWalletId, senderWalletId))
                }
            }

            // Tombstone check
            val tombstoneTs = state.deletedConvs[convKey]
            if (tombstoneTs != null && ts <= tombstoneTs) continue

            val msgList = conversations.getOrPut(convKey) { mutableListOf() } as MutableList

            // Dedup check — file dedup by CID, text dedup by ts+text+sent
            val fileObj = payload.optJSONObject("file")
            val exists = if (type == "file" && fileObj != null) {
                val fileCid = fileObj.optString("cid", "")
                msgList.any { it.timestamp == ts && it.sent == sent && it.file?.cid == fileCid }
            } else {
                msgList.any { it.timestamp == ts && it.text == text && it.sent == sent }
            }
            if (exists) continue

            // Build chat message — with file, tip, reply support
            var msgFile: FileAttachment? = null
            var msgText = text
            val msgReply = payload.optString("reply", "").ifEmpty { null }

            if (type == "file" && fileObj != null) {
                val fKey = fileObj.optString("key", "")
                val fIv = fileObj.optString("iv", "")
                var fCid = fileObj.optString("cid", "")
                var fMime = fileObj.optString("mime", "application/octet-stream")
                val fSize = fileObj.optLong("size", 0)
                val fName = fileObj.optString("name", "file")
                    .replace(Regex("[<>\"'&\\\\]"), "_")
                    .take(80)
                val fData = if (fileObj.has("data")) fileObj.optString("data") else null

                // Validate: key=64 hex + iv=24 hex required
                if (!Regex("^[a-fA-F0-9]{64}$").matches(fKey) ||
                    !Regex("^[a-fA-F0-9]{24}$").matches(fIv)) continue
                if (fCid.isEmpty() && fData == null) continue
                // Generate synthetic CID for inline files without one
                if (fCid.isEmpty() && fData != null) fCid = "inline-${fData.take(16)}"
                if (fMime !in Config.ALLOWED_MIME_TYPES) fMime = "application/octet-stream"

                msgFile = FileAttachment(
                    cid = fCid,
                    key = fKey,
                    iv = fIv,
                    name = fName,
                    size = fSize,
                    mime = fMime,
                    data = fData,
                )
                msgText = payload.optString("msg", "")
            }

            val isTip = type == "tip"
            val tipAmount = if (isTip) payload.optLong("amount", 0) else 0

            val msg = ChatMessage(
                id = "$ts-$from-${payload.optString("to", "")}",
                from = from,
                to = payload.optString("to", ""),
                text = msgText,
                timestamp = ts,
                sent = sent,
                displayName = displayName,
                file = msgFile,
                isTip = isTip,
                tipAmount = tipAmount,
                reply = msgReply,
                type = type,
            )

            msgList.add(msg)

            if (!sent && convKey != state.activeChat) {
                newUnread[convKey] = (newUnread[convKey] ?: 0) + 1
            }
        }

        // Sort each conversation chronologically
        conversations.forEach { (key, msgs) ->
            conversations[key] = msgs.sortedBy { it.timestamp }.toMutableList()
        }

        // Merge new unreads
        for ((k, v) in newUnread) {
            unreadCounts[k] = (unreadCounts[k] ?: 0) + v
        }

        return ProcessResult(conversations, contacts, unreadCounts, pendingResolves)
    }

    private fun extractPayload(raw: JSONObject): JSONObject? {
        return try {
            val msg = raw.opt("message") ?: raw.opt("payload") ?: return null
            when (msg) {
                is JSONObject -> msg
                is String -> JSONObject(msg)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}

/** Current protocol state (input to message processor). */
data class ProtocolState(
    val conversations: Map<String, List<ChatMessage>> = emptyMap(),
    val contacts: Map<String, Contact> = emptyMap(),
    val unreadCounts: Map<String, Int> = emptyMap(),
    val deletedConvs: Map<String, Long> = emptyMap(),
    val activeChat: String? = null,
    val myHandle: Identity? = null,
    val contractStartTs: Long = 0,
    val blockedUsers: Set<String> = emptySet(),
)

/** Result from message processing. */
data class ProcessResult(
    val conversations: Map<String, List<ChatMessage>>,
    val contacts: Map<String, Contact>,
    val unreadCounts: Map<String, Int>,
    val pendingResolves: List<PendingResolve>,
)

data class PendingResolve(val type: String, val key: String, val value: String)
