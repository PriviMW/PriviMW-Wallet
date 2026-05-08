package com.privimemobile.chat.transport

import android.util.Log
import com.privimemobile.chat.ChatService
import com.privimemobile.chat.db.ChatDatabase
import com.privimemobile.chat.processor.MessageProcessor
import com.privimemobile.protocol.WalletApi
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SbbsTransport — handles SBBS send/receive, polling, event-driven refresh.
 *
 * Uses callback ID range 2,000,000+ to coexist with WalletApi (1,000,000+).
 * Primary trigger: onInstantMessage push from C++ wallet core (fires on every incoming SBBS message).
 * Fallback: slow polling timer (3 min idle, 2s active) catches any missed push callbacks.
 */
class SbbsTransport(
    private val db: ChatDatabase,
    private val processor: MessageProcessor,
    private val scope: CoroutineScope,
) {
    private val TAG = "SbbsTransport"
    private var pollingJob: Job? = null
    private val reading = AtomicBoolean(false)
    private var readStartTime = 0L  // auto-reset if stuck >30s

    companion object {
        const val POLL_ACTIVE_MS = 2_000L    // 2s when chat is open — near-instant feel
        const val POLL_IDLE_MS = 180_000L   // 3 min safety net when idle (onInstantMessage is primary)
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
        reading.set(false)
        startPolling()
    }

    /** Called by ProtocolStartup's onTxsChanged hook — fires on financial TX changes, not SBBS messages. */
    fun onTxsChanged() {
        Log.d(TAG, "ev_txs_changed — immediate poll")
        scope.launch { safeReadMessages() }
    }

    /** Called by ProtocolStartup's onSystemStateChanged hook. */
    fun onSystemState() {
        // Identity check delegated to IdentityManager
    }

    /** Poll from timer or manual refresh. */
    fun pollNow() {
        scope.launch { safeReadMessages() }
    }

    /**
     * Atomic guard — skip if a read is already in progress.
     * Auto-resets after 30s in case callAsync hangs (prevents permanent deadlock).
     */
    private suspend fun safeReadMessages() {
        val now = System.currentTimeMillis()
        // Auto-reset if stuck for >30s
        if (reading.get() && now - readStartTime > 30_000) {
            Log.w(TAG, "Read stuck for >30s — force resetting")
            reading.set(false)
        }

        if (!reading.compareAndSet(false, true)) {
            Log.d(TAG, "Read already in progress — skipping")
            return
        }

        readStartTime = now
        try {
            readMessages()
        } catch (e: Exception) {
            Log.w(TAG, "safeReadMessages error: ${e.message}")
        } finally {
            reading.set(false)
        }
    }

    /** Read messages from SBBS inbox. */
    private suspend fun readMessages() {
        try {
            val result = WalletApi.callAsync("read_messages", emptyMap())
            Log.d(TAG, "read_messages result keys: ${result.keys}, size=${result.size}")
            val messages = result["messages"] as? List<*>
            if (messages == null) {
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

    /** Send delivery ack — confirms message arrived (3x retry like regular messages). */
    fun sendDeliveryAck(convKey: String, timestamps: List<Long>) {
        Log.d(TAG, "sendDeliveryAck($convKey): ${timestamps.size} timestamps")
        scope.launch {
            val state = db.chatStateDao().get() ?: return@launch
            if (state.myHandle == null) return@launch

            val conv = db.conversationDao().findByKey(convKey) ?: return@launch
            var toWalletId = conv.walletId
            if (toWalletId.isNullOrEmpty()) {
                val handle = convKey.removePrefix("@")
                toWalletId = db.contactDao().findByHandle(handle)?.walletId
                if (toWalletId.isNullOrEmpty()) {
                    val resolved = com.privimemobile.chat.ChatService.contacts.resolveHandle(handle)
                    toWalletId = resolved?.walletId
                }
                if (!toWalletId.isNullOrEmpty()) {
                    db.conversationDao().updateContactInfo(convKey, null, toWalletId, null)
                } else return@launch
            }

            val payload = mapOf(
                "v" to 1,
                "t" to "delivered",
                "from" to state.myHandle,
                "delivered" to timestamps,
            )
            sendWithRetry(toWalletId, payload)
        }
    }

    /** Send read receipts (3x retry — SBBS can drop fire-and-forget messages). */
    fun sendReadReceipts(convKey: String, timestamps: List<Long>) {
        Log.d(TAG, "sendReadReceipts($convKey): ${timestamps.size} timestamps")
        scope.launch {
            val state = db.chatStateDao().get() ?: return@launch
            if (state.myHandle == null) { Log.w(TAG, "sendReadReceipts: no myHandle"); return@launch }

            val conv = db.conversationDao().findByKey(convKey) ?: run { Log.w(TAG, "sendReadReceipts: conv not found for $convKey"); return@launch }
            var toWalletId = conv.walletId
            if (toWalletId.isNullOrEmpty()) {
                // Resolve wallet_id from contact DB or on-chain
                val handle = convKey.removePrefix("@")
                toWalletId = db.contactDao().findByHandle(handle)?.walletId
                if (toWalletId.isNullOrEmpty()) {
                    val resolved = com.privimemobile.chat.ChatService.contacts.resolveHandle(handle)
                    toWalletId = resolved?.walletId
                }
                if (!toWalletId.isNullOrEmpty()) {
                    db.conversationDao().updateContactInfo(convKey, null, toWalletId, null)
                } else {
                    Log.w(TAG, "sendReadReceipts: no walletId for $convKey after resolve")
                    return@launch
                }
            }

            val payload = mapOf(
                "v" to 1,
                "t" to "ack",
                "from" to state.myHandle,
                "read" to timestamps,
            )
            sendWithRetry(toWalletId, payload)

            // Mark local messages as acked
            db.messageDao().markAcked(conv.id, timestamps)
        }
    }

    /**
     * Send group read receipts — sends ack to each sender individually with group_id.
     * Groups messages by senderHandle, resolves wallet IDs, sends one ack per sender.
     */
    fun sendGroupReadReceipts(groupId: String, groupConvId: Long) {
        scope.launch {
            val state = db.chatStateDao().get() ?: return@launch
            if (state.myHandle == null) return@launch

            // Get all received (non-self) messages grouped by sender
            val allReceived = db.messageDao().getAllReceivedWithSender(groupConvId) ?: return@launch
            if (allReceived.isEmpty()) return@launch

            // Group timestamps by sender handle
            val bySender = mutableMapOf<String, MutableList<Long>>()
            for (msg in allReceived) {
                val sender = msg.senderHandle ?: continue
                if (sender == state.myHandle) continue
                bySender.getOrPut(sender) { mutableListOf() }.add(msg.timestamp)
            }

            Log.d(TAG, "sendGroupReadReceipts($groupId): ${bySender.size} senders, ${allReceived.size} msgs")

            for ((senderHandle, timestamps) in bySender) {
                // Resolve sender's wallet ID from group members or contacts
                val walletId = db.groupDao().getMemberWalletId(groupId, senderHandle)
                    ?: db.contactDao().findByHandle(senderHandle)?.walletId
                if (walletId.isNullOrEmpty()) {
                    Log.w(TAG, "sendGroupReadReceipts: no walletId for @$senderHandle")
                    continue
                }

                val payload = mapOf(
                    "v" to 1,
                    "t" to "ack",
                    "from" to state.myHandle,
                    "group_id" to groupId,
                    "read" to timestamps,
                )
                sendOnce(walletId, payload)
                delay(100) // small spacing between sends
            }

            // Mark local messages as acked
            db.messageDao().markAcked(groupConvId, allReceived.map { it.timestamp })
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

    /** Send a single SBBS message with no retry (for group messages). */
    fun sendOnce(toWalletId: String, message: Map<String, Any?>) {
        sendSbbsMessage(toWalletId, message)
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
