package com.privimemobile.chat.notification

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.privimemobile.MainActivity
import com.privimemobile.R
import com.privimemobile.chat.ChatService

/**
 * ChatNotificationManager — shows notifications for incoming chat messages.
 *
 * Features:
 * - MessagingStyle with last 3 messages per conversation
 * - Separate channels for messages, tips, and files
 * - Suppresses notifications for active chat and muted conversations
 * - Groups notifications per conversation
 * - Updates badge count via notification summary
 * - Tap notification → opens chat screen directly
 */
object ChatNotificationManager {
    private const val TAG = "ChatNotifMgr"

    // Channel IDs
    private const val GROUP_ID = "privime_chat"
    private const val CHANNEL_MESSAGES = "privime_messages"
    private const val CHANNEL_TIPS = "privime_tips"
    private const val CHANNEL_FILES = "privime_files"

    // Notification IDs: use convId hash to group per conversation
    private const val SUMMARY_ID = 8000
    private const val MAX_MESSAGES_PER_CONV = 3

    private var initialized = false
    private lateinit var appContext: Context

    /** In-memory buffer of recent messages per conversation (convKey → list of messages). */
    private val messageHistory = mutableMapOf<String, MutableList<NotifMessage>>()

    private data class NotifMessage(
        val senderName: String,
        val text: String,
        val timestamp: Long,
    )

    fun init(context: Context) {
        appContext = context.applicationContext
        createChannels()
        initialized = true
    }

    /**
     * Show notification for an incoming message.
     * Called from MessageProcessor after inserting a received message.
     */
    fun notifyMessage(
        convKey: String,
        convId: Long,
        senderName: String,
        text: String,
        type: String,
        isMuted: Boolean,
        totalUnread: Int,
    ) {
        if (!initialized) return

        // Suppress if chat is currently open
        if (ChatService.activeChat.value == convKey) return

        // Suppress if conversation is muted
        if (isMuted) {
            // Still update badge count even for muted
            updateBadge(totalUnread)
            return
        }

        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Pick channel based on message type
        val channelId = when (type) {
            "tip" -> CHANNEL_TIPS
            "file" -> CHANNEL_FILES
            else -> CHANNEL_MESSAGES
        }

        // Content text
        val contentText = when (type) {
            "tip" -> "\uD83D\uDCB0 $text"  // 💰
            "file" -> "\uD83D\uDCCE $text"  // 📎
            else -> text
        }

        // Add to message history buffer (keep last 3)
        val history = messageHistory.getOrPut(convKey) { mutableListOf() }
        history.add(NotifMessage(senderName, contentText, System.currentTimeMillis()))
        if (history.size > MAX_MESSAGES_PER_CONV) {
            history.removeAt(0)
        }

        // Intent to open the chat directly
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_chat", convKey)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext, convKey.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Build MessagingStyle with last 3 messages
        val sender = Person.Builder().setName(senderName).build()
        val messagingStyle = NotificationCompat.MessagingStyle(
            Person.Builder().setName("Me").build()
        ).setConversationTitle(senderName)

        for (msg in history) {
            val person = Person.Builder().setName(msg.senderName).build()
            messagingStyle.addMessage(msg.text, msg.timestamp, person)
        }

        // Individual message notification with MessagingStyle
        val notifId = (convKey.hashCode() and 0x7FFFFFFF) % 7000 + 1000  // 1000-7999 range
        val notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(messagingStyle)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(GROUP_ID)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .build()

        nm.notify(notifId, notification)

        // Summary notification (for grouping + badge count)
        updateBadge(totalUnread)

        Log.d(TAG, "Notification: $senderName ($type) in $convKey, ${history.size} msgs, unread=$totalUnread")
    }

    /** Update the summary notification with total unread count (drives badge). */
    fun updateBadge(totalUnread: Int) {
        if (!initialized) return

        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (totalUnread <= 0) {
            nm.cancel(SUMMARY_ID)
            return
        }

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val summary = NotificationCompat.Builder(appContext, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("PriviMe")
            .setContentText("$totalUnread unread message${if (totalUnread > 1) "s" else ""}")
            .setNumber(totalUnread)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(GROUP_ID)
            .setGroupSummary(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .build()

        nm.notify(SUMMARY_ID, summary)
    }

    /** Cancel notifications for a specific conversation (when chat is opened). */
    fun cancelForConversation(convKey: String) {
        if (!initialized) return
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifId = (convKey.hashCode() and 0x7FFFFFFF) % 7000 + 1000
        nm.cancel(notifId)
        messageHistory.remove(convKey)
    }

    /** Cancel all chat notifications. */
    fun cancelAll() {
        if (!initialized) return
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(SUMMARY_ID)
        messageHistory.clear()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Channel group
        nm.createNotificationChannelGroup(
            NotificationChannelGroup(GROUP_ID, "PriviMe Chat")
        )

        // Messages channel
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "New chat messages"
            group = GROUP_ID
            setShowBadge(true)
        })

        // Tips channel
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_TIPS, "Tips", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Received BEAM tips"
            group = GROUP_ID
            setShowBadge(true)
        })

        // Files channel
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_FILES, "Files", NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Received files and images"
            group = GROUP_ID
            setShowBadge(true)
        })

        // Remove old badge-only channel if it exists
        nm.deleteNotificationChannel("privime_badge")

        Log.d(TAG, "Notification channels created")
    }
}
