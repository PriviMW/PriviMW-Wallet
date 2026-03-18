package com.privimemobile.wallet

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.privimemobile.MainActivity
import com.privimemobile.R
import com.privimemobile.protocol.Helpers
import com.privimemobile.protocol.SecureStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * TxNotificationManager — shows notifications when transaction status changes.
 *
 * Watches WalletEventBus.transactions flow, compares with previous state,
 * and notifies on: new incoming TX (received), TX completed, TX failed/cancelled.
 *
 * Respects the "tx_status_notif" setting from Settings screen.
 */
object TxNotificationManager {
    private const val TAG = "TxNotifMgr"

    private const val GROUP_ID = "privime_chat"  // Same group as chat — all PriviMW notifications together
    private const val CHANNEL_TX = "privime_transactions"
    private const val SUMMARY_ID = 9000

    private const val MAX_STACKED_LINES = 5

    private var initialized = false
    private lateinit var appContext: Context

    /** Previous TX state: txId → status. Used to detect changes. */
    private val previousTxState = mutableMapOf<String, Int>()

    /** TX IDs we've already notified about (prevent re-notification on repeated pushes). */
    private val notifiedTxIds = mutableSetOf<String>()

    /** Recent notification lines for InboxStyle stacking. */
    private val recentLines = mutableListOf<String>()

    // TX status constants (mirrors TxStatus in WalletScreen)
    private const val PENDING = 0
    private const val IN_PROGRESS = 1
    private const val CANCELLED = 2
    private const val COMPLETED = 3
    private const val FAILED = 4
    private const val REGISTERING = 5

    fun init(context: Context) {
        appContext = context.applicationContext
        createChannel()
        initialized = true
    }

    /**
     * Start observing TX changes. Call once from ChatService or BackgroundService.
     */
    fun startObserving(scope: CoroutineScope) {
        scope.launch {
            WalletEventBus.transactions.collect { json ->
                if (!initialized) return@collect
                if (!SecureStorage.getBoolean("tx_status_notif", true)) return@collect
                processTxUpdate(json)
            }
        }
    }

    private fun processTxUpdate(json: String) {
        try {
            val arr = JSONArray(json)
            val currentTxs = mutableMapOf<String, TxSnapshot>()

            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val txId = obj.optString("txId")
                if (txId.isEmpty()) continue
                currentTxs[txId] = TxSnapshot(
                    txId = txId,
                    status = obj.optInt("status"),
                    sender = obj.optBoolean("sender"),
                    amount = obj.optLong("amount"),
                    assetId = obj.optInt("assetId"),
                    isDapps = obj.optBoolean("isDapps"),
                    contractCids = obj.optString("contractCids", "").ifEmpty { null },
                    appName = obj.optString("appName", "").ifEmpty { null },
                )
            }

            // First call — just populate state, don't notify
            if (previousTxState.isEmpty() && currentTxs.isNotEmpty()) {
                for ((txId, tx) in currentTxs) {
                    previousTxState[txId] = tx.status
                }
                Log.d(TAG, "Initial state: ${currentTxs.size} TXs")
                return
            }

            // Check for changes
            for ((txId, tx) in currentTxs) {
                val prevStatus = previousTxState[txId]

                if (prevStatus == null) {
                    // New TX appeared
                    if (!tx.sender && tx.status <= IN_PROGRESS) {
                        // New incoming TX — notify "Receiving..."
                        notifyTx(tx, "Receiving")
                    }
                } else if (prevStatus != tx.status) {
                    // Status changed
                    when (tx.status) {
                        COMPLETED -> notifyTx(tx, if (tx.sender) "Sent" else "Received")
                        FAILED -> notifyTx(tx, "Failed")
                        CANCELLED -> notifyTx(tx, "Cancelled")
                    }
                }

                previousTxState[txId] = tx.status
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error processing TX update: ${e.message}")
        }
    }

    private fun notifyTx(tx: TxSnapshot, action: String) {
        // Deduplicate: don't re-notify for same txId + action
        val key = "${tx.txId}:$action"
        if (!notifiedTxIds.add(key)) return

        // Cap dedup set to prevent memory growth
        if (notifiedTxIds.size > 200) {
            val iter = notifiedTxIds.iterator()
            repeat(100) { if (iter.hasNext()) { iter.next(); iter.remove() } }
        }

        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Format amount
        val ticker = assetTicker(tx.assetId)
        val amountStr = Helpers.grothToBeam(tx.amount)

        // Build notification line
        val line = when (action) {
            "Receiving" -> "Receiving $amountStr $ticker..."
            "Received" -> "Received $amountStr $ticker"
            "Sent" -> "Sent $amountStr $ticker"
            "Failed" -> "${if (tx.sender) "Send" else "Receive"} $amountStr $ticker failed"
            "Cancelled" -> "${if (tx.sender) "Send" else "Receive"} $amountStr $ticker cancelled"
            else -> return
        }

        // Add to stacked lines (keep last N)
        recentLines.add(line)
        if (recentLines.size > MAX_STACKED_LINES) {
            recentLines.removeAt(0)
        }

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tx", tx.txId)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext, tx.txId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Build InboxStyle with stacked lines
        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle("PriviMW Transactions")
        for (l in recentLines) {
            inboxStyle.addLine(l)
        }
        if (recentLines.size > 1) {
            inboxStyle.setSummaryText("${recentLines.size} updates")
        }

        // Single stacked notification (always same ID so they merge)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_TX)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (recentLines.size > 1) "PriviMW Transactions" else "Transaction")
            .setContentText(line)
            .setStyle(inboxStyle)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(GROUP_ID)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setNumber(recentLines.size)
            .build()

        nm.notify(SUMMARY_ID, notification)
        Log.d(TAG, "TX notification: $line (${recentLines.size} stacked)")
    }

    /** Clear stacked notifications (e.g., when user opens wallet). */
    fun clearNotifications() {
        if (!initialized) return
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(SUMMARY_ID)
        recentLines.clear()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Reuse the shared "PriviMe Chat" channel group (created by ChatNotificationManager)
        // so all notifications appear under one PriviMW header in the pulldown
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_TX, "Transactions", NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Transaction status updates"
            group = GROUP_ID
            setShowBadge(false)
        })

        Log.d(TAG, "TX notification channel created")
    }

    private data class TxSnapshot(
        val txId: String,
        val status: Int,
        val sender: Boolean,
        val amount: Long,
        val assetId: Int,
        val isDapps: Boolean,
        val contractCids: String?,
        val appName: String?,
    )
}
