package com.privimemobile.protocol

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject

/**
 * SBBS messaging — send/read messages via Beam's SBBS protocol.
 *
 * Ports sbbs.ts to Kotlin.
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
     */
    fun buildChatPayload(
        fromHandle: String,
        toHandle: String,
        text: String,
        displayName: String = "",
        fileHash: String = "",
        fileName: String = "",
        fileSize: Long = 0,
    ): Map<String, Any?> {
        val payload = mutableMapOf<String, Any?>(
            "v" to 1,
            "t" to "dm",
            "from" to fromHandle,
            "to" to toHandle,
            "msg" to text,
            "ts" to (System.currentTimeMillis() / 1000),
        )
        if (displayName.isNotEmpty()) payload["dn"] = displayName
        if (fileHash.isNotEmpty()) {
            payload["fh"] = fileHash
            payload["fn"] = fileName
            payload["fs"] = fileSize
        }
        return payload
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
