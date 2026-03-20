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

    private const val GROUP_ID = "privime_chat"  // Same visual group as chat — all under "PriviMW"
    private const val CHANNEL_TX = "privime_transactions"
    private const val SUMMARY_ID = 9000

    private var activeNotifCount = 0

    private var initialized = false
    private lateinit var appContext: Context

    /** Previous TX state: txId → status. Used to detect changes. */
    private val previousTxState = mutableMapOf<String, Int>()

    /** TX IDs we've already notified about (prevent re-notification on repeated pushes). */
    private val notifiedTxIds = mutableSetOf<String>()

    /** Recent notification lines for InboxStyle stacking. */
    // recentLines removed — each TX is now its own notification card

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
                val caList = obj.optJSONArray("contractAssets")?.let { ca ->
                    (0 until ca.length()).mapNotNull { j ->
                        val ao = ca.optJSONObject(j) ?: return@mapNotNull null
                        ContractAssetEntry(
                            assetId = ao.optInt("assetId"),
                            sending = ao.optLong("sending"),
                            receiving = ao.optLong("receiving"),
                        )
                    }
                } ?: emptyList()
                currentTxs[txId] = TxSnapshot(
                    txId = txId,
                    status = obj.optInt("status"),
                    sender = obj.optBoolean("sender"),
                    amount = obj.optLong("amount"),
                    assetId = obj.optInt("assetId"),
                    isDapps = obj.optBoolean("isDapps"),
                    contractCids = obj.optString("contractCids", "").ifEmpty { null },
                    appName = obj.optString("appName", "").ifEmpty { null },
                    contractAssets = caList,
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

        // For DApp contract TXs, sender flag is inverted (positive amount = spending but sender=false)
        val isOutgoing = if (tx.isDapps && tx.amount > 0 && !tx.contractCids.isNullOrEmpty()) !tx.sender else tx.sender
        val dappName = if (tx.isDapps) tx.appName ?: "DApp" else null

        // Build per-asset breakdown for DApp TXs (e.g., "-10.0 BEAM, +50.0 BEAMX")
        val assetBreakdown = if (dappName != null && tx.contractAssets.isNotEmpty()) {
            tx.contractAssets.mapNotNull { ca ->
                val isSpending = ca.sending != 0L
                val amt = Math.abs(if (isSpending) ca.sending else ca.receiving)
                if (amt == 0L) return@mapNotNull null
                val prefix = if (isSpending) "-" else "+"
                "$prefix${Helpers.grothToBeam(amt)} ${assetTicker(ca.assetId)}"
            }.joinToString(", ")
        } else null

        // Build notification line
        val amountDisplay = assetBreakdown ?: "$amountStr $ticker"
        val hasBreakdown = assetBreakdown != null
        val line = if (dappName != null) {
            // DApp TX: use "processing/completed/failed" instead of "receiving/sent"
            when (action) {
                "Receiving" -> "Processing: $amountDisplay"
                "Received", "Sent" -> if (hasBreakdown) amountDisplay
                    else "${if (isOutgoing) "Spent" else "Received"}: $amountDisplay"
                "Failed" -> "Failed: $amountDisplay"
                "Cancelled" -> "Cancelled: $amountDisplay"
                else -> amountDisplay
            }
        } else {
            // Regular TX: standard format
            when (action) {
                "Receiving" -> "Receiving $amountStr $ticker..."
                "Received" -> "Received $amountStr $ticker"
                "Sent" -> "Sent $amountStr $ticker"
                "Failed" -> "${if (isOutgoing) "Send" else "Receive"} $amountStr $ticker failed"
                "Cancelled" -> "${if (isOutgoing) "Send" else "Receive"} $amountStr $ticker cancelled"
                else -> "$amountStr $ticker"
            }
        }

        // Track active notification count
        activeNotifCount++

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tx", tx.txId)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext, tx.txId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Title: DApp name or direction
        val title = when {
            dappName != null -> dappName
            action == "Receiving" -> "Receiving"
            action == "Received" -> "Received"
            action == "Sent" -> "Sent"
            action == "Failed" -> "Failed"
            action == "Cancelled" -> "Cancelled"
            else -> "Transaction"
        }

        // Individual notification per TX — grouped under app
        val notifId = tx.txId.hashCode()
        val notification = NotificationCompat.Builder(appContext, CHANNEL_TX)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(line)
            .setStyle(NotificationCompat.BigTextStyle().bigText(line))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(GROUP_ID)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .build()

        nm.notify(notifId, notification)

        // Summary notification (Android groups individual notifications under this)
        if (activeNotifCount > 1) {
            val summary = NotificationCompat.Builder(appContext, CHANNEL_TX)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("PriviMW Transactions")
                .setContentText("$activeNotifCount updates")
                .setGroup(GROUP_ID)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .build()
            nm.notify(SUMMARY_ID, summary)
        }

        Log.d(TAG, "TX notification: $line (id=$notifId)")
    }

    /** Clear all TX notifications (e.g., when user opens wallet). */
    fun clearNotifications() {
        if (!initialized) return
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(SUMMARY_ID)
        // Cancel all active TX notifications
        nm.activeNotifications.filter { it.groupKey?.contains(GROUP_ID) == true }.forEach {
            nm.cancel(it.id)
        }
        activeNotifCount = 0
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

    private data class ContractAssetEntry(
        val assetId: Int,
        val sending: Long,
        val receiving: Long,
    )

    private data class TxSnapshot(
        val txId: String,
        val status: Int,
        val sender: Boolean,
        val amount: Long,
        val assetId: Int,
        val isDapps: Boolean,
        val contractCids: String?,
        val appName: String?,
        val contractAssets: List<ContractAssetEntry> = emptyList(),
    )
}
