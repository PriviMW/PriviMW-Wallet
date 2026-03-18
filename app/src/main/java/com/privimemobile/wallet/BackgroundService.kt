package com.privimemobile.wallet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.privimemobile.MainActivity
import com.privimemobile.R

/**
 * Foreground service — keeps the Beam wallet alive for instant message delivery.
 *
 * Holds a WiFi lock and wake lock to prevent the OS from killing the node connection.
 * Required for reliable SBBS message reception.
 */
class BackgroundService : Service() {
    private val TAG = "BeamBgService"
    private val CHANNEL_ID = "privimw_bg_service"
    private val NOTIFICATION_ID = 1

    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            isRunning = false
            stopSelf()
            return START_NOT_STICKY
        }
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        isRunning = true
        Log.d(TAG, "Foreground service started")

        // Ensure ChatService is running (may need re-init after process restart from swipe-away)
        ensureChatService()

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Task removed (app swiped away) — service continues")
        // Re-ensure ChatService stays alive for background message delivery
        ensureChatService()
    }

    /** Initialize ChatService if wallet is ready but chat isn't running. */
    private fun ensureChatService() {
        if (WalletManager.isApiReady && !com.privimemobile.chat.ChatService.initialized.value) {
            Log.d(TAG, "Re-initializing ChatService from BackgroundService")
            com.privimemobile.chat.ChatService.init(applicationContext)
        }
    }

    companion object {
        const val ACTION_STOP = "com.privimemobile.STOP_SERVICE"
        @Volatile var isRunning = false
    }

    override fun onDestroy() {
        isRunning = false
        releaseLocks()
        Log.d(TAG, "Foreground service stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PriviMW Background",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps wallet connected for instant messages"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PriviMW")
            .setContentText("Connected to Beam network")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    @Suppress("DEPRECATION")
    private fun acquireLocks() {
        try {
            val wifiMgr = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
            wifiLock = wifiMgr?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "PriviMW:wifi")
            wifiLock?.acquire()
            Log.d(TAG, "WiFi lock acquired")
        } catch (e: Exception) {
            Log.w(TAG, "WiFi lock failed: ${e.message}")
        }
        try {
            val pm = getSystemService(POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PriviMW:wake")
            wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24h — renewed on service restart (matches RN)
            Log.d(TAG, "Wake lock acquired")
        } catch (e: Exception) {
            Log.w(TAG, "Wake lock failed: ${e.message}")
        }
    }

    private fun releaseLocks() {
        wifiLock?.let { if (it.isHeld) it.release() }
        wakeLock?.let { if (it.isHeld) it.release() }
        Log.d(TAG, "Locks released")
    }
}
