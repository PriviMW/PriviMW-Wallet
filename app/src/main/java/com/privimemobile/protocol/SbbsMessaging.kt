package com.privimemobile.protocol

import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * SBBS messaging — send/read messages via Beam's SBBS protocol.
 *
 * Fully ports sbbs.ts from RN build.
 * SBBS is best-effort — retry pattern improves delivery reliability.
 */
object SbbsMessaging {
    private const val TAG = "SbbsMessaging"
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Send a single SBBS message to a wallet ID.
     */
    fun sendMessage(
        toWalletId: String,
        message: Map<String, Any?>,
        callback: ((Map<String, Any?>) -> Unit)? = null,
    ) {
        WalletApi.call("send_message", mapOf(
            "receiver" to toWalletId,
            "message" to message,
        ), callback)
    }

    /**
     * Send with retries — SBBS is best-effort.
     * Sends immediately + 2 retries at 5s intervals.
     * Recipient deduplicates via (ts + text + sent).
     */
    fun sendWithRetry(toWalletId: String, message: Map<String, Any?>, retries: Int = 2) {
        sendMessage(toWalletId, message)
        for (i in 1..retries) {
            handler.postDelayed({ sendMessage(toWalletId, message) }, i * 5000L)
        }
    }

    /**
     * Read messages from SBBS inbox.
     *
     * @param fetchAll If true, fetches all messages. If false, only new messages.
     */
    fun readMessages(
        fetchAll: Boolean = false,
        callback: ((Map<String, Any?>) -> Unit)? = null,
    ) {
        WalletApi.call("read_messages", mapOf("all" to fetchAll), callback)
    }

    /**
     * Build a chat message payload for SBBS.
     * Supports text, file attachments, tips, and replies.
     */
    fun buildChatPayload(
        fromHandle: String,
        toHandle: String,
        text: String,
        displayName: String = "",
        file: FileAttachment? = null,
        isTip: Boolean = false,
        tipAmount: Long = 0,
        reply: String? = null,
    ): Map<String, Any?> {
        val payload = mutableMapOf<String, Any?>(
            "v" to 1,
            "t" to when {
                file != null -> "file"
                isTip -> "tip"
                else -> "dm"
            },
            "from" to fromHandle,
            "to" to toHandle,
            "msg" to text,
            "ts" to (System.currentTimeMillis() / 1000),
        )
        if (displayName.isNotEmpty()) payload["dn"] = displayName
        if (file != null) {
            payload["file"] = mapOf(
                "cid" to file.cid,
                "key" to file.key,
                "iv" to file.iv,
                "name" to file.name,
                "size" to file.size,
                "mime" to file.mime,
            ).let { m -> if (file.data != null) m + ("data" to file.data) else m }
        }
        if (isTip && tipAmount > 0) payload["amount"] = tipAmount
        if (reply != null) payload["reply"] = reply
        return payload
    }

    /**
     * Send read receipts for received messages the user has seen.
     * Ports sendReadReceipts() from RN sbbs.ts.
     */
    fun sendReadReceipts(
        convKey: String,
        conversations: MutableMap<String, MutableList<ChatMessage>>,
        contacts: Map<String, Contact>,
        myHandle: Identity?,
        myWalletId: String?,
        onSave: () -> Unit,
    ) {
        if (myHandle == null || myWalletId.isNullOrEmpty()) return
        val msgs = conversations[convKey] ?: return
        if (msgs.isEmpty()) return

        val receiver = contacts[convKey]?.walletId ?: return
        if (!Helpers.isValidWalletId(receiver)) return

        // Collect unacked received messages
        val unacked = mutableListOf<Long>()
        msgs.forEach { m ->
            if (!m.sent && !m.acked) unacked.add(m.timestamp)
        }
        if (unacked.isEmpty()) return

        // Mark locally as acked
        val updatedMsgs = msgs.map { m ->
            if (!m.sent && !m.acked) m.copy(acked = true) else m
        }.toMutableList()
        conversations[convKey] = updatedMsgs
        onSave()

        // Fire-and-forget ack message
        val ackObj = mapOf<String, Any?>(
            "v" to 1,
            "t" to "ack",
            "read" to unacked,
            "from" to myHandle.handle,
        )
        sendMessage(receiver, ackObj)
    }

    /**
     * Validate an SBBS address (wallet ID).
     */
    fun validateAddress(
        walletId: String,
        callback: (isValid: Boolean, isMine: Boolean) -> Unit,
    ) {
        WalletApi.call("validate_address", mapOf("address" to walletId)) { result ->
            val isValid = result["is_valid"] as? Boolean ?: false
            val isMine = result["is_mine"] as? Boolean ?: false
            callback(isValid, isMine)
        }
    }
}
