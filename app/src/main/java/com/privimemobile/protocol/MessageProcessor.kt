package com.privimemobile.protocol

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Message processor — core routing engine.
 *
 * Ports message-processor.ts to Kotlin.
 * Pure logic: takes raw SBBS messages and updates conversation state.
 * No UI, no framework dependencies.
 */
object MessageProcessor {
    private const val TAG = "MessageProcessor"

    /**
     * Process a batch of raw SBBS messages into conversation state.
     *
     * @param rawMessages JSON array string from read_messages API
     * @param state Current protocol state
     * @return Updated conversations, contacts, unread counts
     */
    fun processMessages(
        rawMessages: String,
        state: ProtocolState,
    ): ProcessResult {
        val messages = try { JSONArray(rawMessages) } catch (_: Exception) { JSONArray() }
        val conversations = state.conversations.toMutableMap()
        val contacts = state.contacts.toMutableMap()
        val unreadCounts = state.unreadCounts.toMutableMap()
        val pendingResolves = mutableListOf<PendingResolve>()

        for (i in 0 until messages.length()) {
            val raw = messages.optJSONObject(i) ?: continue
            val payload = extractPayload(raw) ?: continue
            if (payload.optInt("v") != 1) continue

            val ts = payload.optLong("ts", 0)
            if (ts > 0 && ts < state.contractStartTs) continue

            val type = payload.optString("t", "")
            var from = payload.optString("from", "")
                .removePrefix("@").lowercase()
                .replace(Regex("[^a-z0-9_]"), "")
            if (from.isEmpty() && type != "ack") continue

            // Handle read receipts
            if (type == "ack") {
                val readArr = payload.optJSONArray("read")
                if (readArr != null && from.isNotEmpty()) {
                    val convKey = "@$from"
                    val msgs = conversations[convKey]?.toMutableList()
                    if (msgs != null) {
                        for (j in 0 until readArr.length()) {
                            val ackTs = readArr.optLong(j)
                            msgs.forEachIndexed { idx, msg ->
                                if (msg.sent && msg.timestamp == ackTs) {
                                    msgs[idx] = msg // read receipt noted
                                }
                            }
                        }
                        conversations[convKey] = msgs
                    }
                }
                continue
            }

            if (type !in listOf("dm", "tip", "file")) continue
            if (state.blockedUsers.contains(from)) continue

            val senderWalletId = raw.optString("sender", "").trim()
            if (senderWalletId.isEmpty()) continue

            val text = payload.optString("msg", "")
            val displayName = Helpers.fixBvmUtf8(payload.optString("dn", ""))

            // Detect sent messages
            val sent = state.myHandle != null &&
                from == state.myHandle.handle.lowercase()

            // Determine conversation key
            val convKey: String
            if (sent) {
                val to = payload.optString("to", "").lowercase()
                convKey = if (to.isNotEmpty()) "@$to" else continue
            } else {
                convKey = "@$from"
                if (!contacts.containsKey(convKey)) {
                    contacts[convKey] = Contact(
                        handle = from,
                        displayName = displayName.ifEmpty { from },
                        walletId = "",
                    )
                    pendingResolves.add(PendingResolve("handle", convKey, from))
                } else if (displayName.isNotEmpty()) {
                    val existing = contacts[convKey]!!
                    if (existing.displayName != displayName) {
                        contacts[convKey] = existing.copy(displayName = displayName)
                    }
                }
            }

            // Tombstone check
            val tombstoneTs = state.deletedConvs[convKey]
            if (tombstoneTs != null && ts <= tombstoneTs) continue

            val msgList = conversations.getOrPut(convKey) { mutableListOf() } as MutableList

            // Dedup check
            val exists = msgList.any { it.timestamp == ts && it.text == text && it.sent == sent }
            if (exists) continue

            val msg = ChatMessage(
                id = "$ts-$from-${payload.optString("to", "")}",
                from = from,
                to = payload.optString("to", ""),
                text = text,
                timestamp = ts,
                sent = sent,
                displayName = displayName,
                fileHash = payload.optJSONObject("file")?.optString("cid", "") ?: "",
                fileName = payload.optJSONObject("file")?.optString("name", "") ?: "",
                type = type,
            )

            msgList.add(msg)

            if (!sent && convKey != state.activeChat) {
                unreadCounts[convKey] = (unreadCounts[convKey] ?: 0) + 1
            }
        }

        // Sort each conversation chronologically
        conversations.forEach { (key, msgs) ->
            conversations[key] = msgs.sortedBy { it.timestamp }
        }

        return ProcessResult(conversations, contacts, unreadCounts, pendingResolves)
    }

    private fun extractPayload(raw: JSONObject): JSONObject? {
        return try {
            val msg = raw.opt("message")
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
