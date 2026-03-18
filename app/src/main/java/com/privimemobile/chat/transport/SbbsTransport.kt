package com.privimemobile.chat.transport

import android.util.Log
import com.privimemobile.chat.ChatService
import com.privimemobile.chat.db.ChatDatabase
import com.privimemobile.chat.processor.MessageProcessor
import com.privimemobile.protocol.Config
import com.privimemobile.protocol.Helpers
import com.privimemobile.protocol.WalletApi
import kotlinx.coroutines.*

/**
 * SbbsTransport — handles SBBS send/receive, polling, event-driven refresh.
 *
 * Uses callback ID range 2,000,000+ to coexist with WalletApi (1,000,000+).
 * Primary trigger: ev_txs_changed event. Fallback: 30s polling timer.
 */
class SbbsTransport(
    private val db: ChatDatabase,
    private val processor: MessageProcessor,
    private val scope: CoroutineScope,
) {
    private val TAG = "SbbsTransport"
    private var pollingJob: Job? = null
    private var isPolling = false

    companion object {
        const val POLL_ACTIVE_MS = 2_000L   // 2s when chat is open — near-instant feel
        const val POLL_IDLE_MS = 15_000L   // 15s when no chat is open
    }

    /** Start adaptive polling — fast when chat open, slow otherwise. */
    fun startPolling() {
        if (pollingJob?.isActive == true) {
            Log.d(TAG, "Polling already active — skipping")
            return
        }
        Log.d(TAG, "Starting SBBS adaptive polling")

        pollingJob = scope.launch {
            while (isActive) {
                pollNow()
                val interval = if (ChatService.activeChat.value != null) POLL_ACTIVE_MS else POLL_IDLE_MS
                delay(interval)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    /** Force restart polling — kills old job, starts fresh. Use on foreground recovery. */
    fun restartPolling() {
        Log.d(TAG, "Force restarting SBBS polling")
        pollingJob?.cancel()
        pollingJob = null
        isPolling = false
        startPolling()
    }

    /** Called by ProtocolStartup's onTxsChanged hook — event-driven, INSTANT poll. */
    fun onTxsChanged() {
        Log.d(TAG, "ev_txs_changed — immediate poll")
        scope.launch {
            readMessages()
        }
    }

    /** Called by ProtocolStartup's onSystemStateChanged hook. */
    fun onSystemState() {
        // Identity check delegated to IdentityManager
    }

    /** Poll from timer or manual refresh. */
    fun pollNow() {
        scope.launch {
            try {
                readMessages()
            } catch (e: Exception) {
                Log.w(TAG, "pollNow error: ${e.message}")
            }
        }
    }

    /** Read messages from SBBS inbox. */
    private suspend fun readMessages() {
        try {
            val result = WalletApi.callAsync("read_messages", emptyMap())
            Log.d(TAG, "read_messages result keys: ${result.keys}, size=${result.size}")
            val messages = result["messages"] as? List<*>
            if (messages == null) {
                // The result might be the array directly for read_messages
                Log.d(TAG, "No 'messages' key — checking result directly: ${result.keys}")
                return
            }
            if (messages.isEmpty()) return
            Log.d(TAG, "Received ${messages.size} SBBS messages")
            processor.processRawMessages(messages)
        } catch (e: Exception) {
            Log.e(TAG, "read_messages error: ${e.message}")
        }
    }

    /**
     * Send a text message via SBBS with 3x retry (0s, 5s, 10s).
     *
     * @param toWalletId Recipient SBBS address (68 hex chars)
     * @param payload Message as Map (will be serialized as JSON object, NOT string)
     */
    fun sendWithRetry(toWalletId: String, payload: Map<String, Any?>) {
        // Immediate send
        sendSbbsMessage(toWalletId, payload)

        // Retry at 5s and 10s
        scope.launch {
            delay(5000)
            sendSbbsMessage(toWalletId, payload)
        }
        scope.launch {
            delay(10000)
            sendSbbsMessage(toWalletId, payload)
        }
    }

    /** Send read receipts (fire-and-forget, no retry). */
    fun sendReadReceipts(convKey: String, timestamps: List<Long>) {
        scope.launch {
            val state = db.chatStateDao().get() ?: return@launch
            if (state.myHandle == null) return@launch

            // Resolve recipient wallet_id
            val conv = db.conversationDao().findByKey(convKey) ?: return@launch
            val toWalletId = conv.walletId ?: return@launch

            val payload = mapOf(
                "v" to 1,
                "t" to "ack",
                "from" to state.myHandle,
                "read" to timestamps,
            )
            sendSbbsMessage(toWalletId, payload)

            // Mark local messages as acked
            db.messageDao().markAcked(conv.id, timestamps)
        }
    }

    // Typing throttle — max one typing indicator per conversation per 3s
    private val lastTypingSent = mutableMapOf<String, Long>()

    /** Send typing indicator (fire-and-forget, throttled, no retry). */
    fun sendTyping(convKey: String) {
        val now = System.currentTimeMillis()
        val last = lastTypingSent[convKey] ?: 0
        if (now - last < 3_000) return // throttle: max once per 3s

        lastTypingSent[convKey] = now
        scope.launch {
            val state = db.chatStateDao().get() ?: return@launch
            if (state.myHandle == null) return@launch

            val handle = convKey.removePrefix("@")
            val conv = db.conversationDao().findByKey(convKey) ?: return@launch
            val toWalletId = conv.walletId ?: return@launch

            val payload = mapOf(
                "v" to 1,
                "t" to "typing",
                "from" to state.myHandle,
                "to" to handle,
            )
            sendSbbsMessage(toWalletId, payload)
        }
    }

    /** Low-level SBBS send — message must be a Map (serialized as JSON object by WalletApi). */
    private fun sendSbbsMessage(toWalletId: String, message: Map<String, Any?>) {
        Log.d(TAG, "Sending SBBS to ${toWalletId.take(16)}...: ${message["msg"] ?: message["t"]}")
        WalletApi.call("send_message", mapOf(
            "receiver" to toWalletId,
            "message" to message,
        ))
    }
}
