package com.privimemobile.chat.notification

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Canvas
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
 * - MessagingStyle with last N messages per conversation
 * - Separate channels for messages, tips, and files
 * - Reaction notifications merged into message notifications (Telegram-style)
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

    // Notification IDs: 1000-7999 (single range, shared by messages and reactions)
    private const val SUMMARY_ID = 8000
    private const val MAX_MESSAGES_PER_CONV = 8

    private var initialized = false
    private lateinit var appContext: Context

    /** In-memory buffer of recent messages per conversation (convKey → list of messages). */
    private val messageHistory = mutableMapOf<String, MutableList<NotifMessage>>()

    private data class NotifMessage(
        val senderName: String,
        val text: String,
        val timestamp: Long,
        val senderHandle: String? = null,
    )

    fun init(context: Context) {
        appContext = context.applicationContext
        createChannels()
        initialized = true
    }

    /**
     * Show notification for an incoming message.
     * Optionally appends a reaction entry (Telegram-style: reaction merged into message notification).
     *
     * @param reactionEmoji When set, this call represents a reaction notification that is merged into
     *                      the conversation's message notification. The senderName is the reactor's
     *                      display name, text is "Reacted to your message: $reactionEmoji".
     */
    fun notifyMessage(
        convKey: String,
        convId: Long,
        senderName: String,
        text: String,
        type: String,
        isMuted: Boolean,
        totalUnread: Int,
        senderHandle: String? = null,
        reactionEmoji: String? = null,
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

        // Add to message history buffer (keep last N)
        val history = messageHistory.getOrPut(convKey) { mutableListOf() }
        if (reactionEmoji != null) {
            // Reaction: preserve the existing last entry's sender info for the conversation title,
            // then append a new reaction entry so the notification shows both the prior message
            // and the reaction. This keeps the DM title as the contact's name, not the reactor's.
            val lastEntry = if (history.isNotEmpty()) history.removeAt(history.size - 1) else null
            if (lastEntry != null) {
                // Re-add the original message first
                history.add(lastEntry)
                // Then add the reaction as a new entry using the original sender's name for the title
                history.add(NotifMessage(
                    senderName = lastEntry.senderName,
                    text = "Reacted to your message: $reactionEmoji",
                    timestamp = System.currentTimeMillis(),
                    senderHandle = lastEntry.senderHandle,
                ))
            } else {
                // No prior messages — add a reaction entry directly
                history.add(NotifMessage(senderName, "Reacted to your message: $reactionEmoji",
                    System.currentTimeMillis(), senderHandle))
            }
        } else {
            // Regular message
            val contentText = when (type) {
                "tip" -> "\uD83D\uDCB0 $text"  // 💰
                "file" -> "\uD83D\uDCCE $text"  // 📎
                else -> text
            }
            history.add(NotifMessage(senderName, contentText, System.currentTimeMillis(), senderHandle))
        }
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

        // Load avatar for notification icon
        val avatarIcon: androidx.core.graphics.drawable.IconCompat? = try {
            val avatarFile = if (convKey.startsWith("g_")) {
                // Group avatar — scan group_avatars dir for file matching prefix
                val prefix = convKey.removePrefix("g_")
                val dir = java.io.File(appContext.filesDir, "group_avatars")
                dir.listFiles()?.firstOrNull { it.name.startsWith(prefix) && it.name.endsWith(".webp") }
            } else {
                val handle = convKey.removePrefix("@")
                java.io.File(appContext.filesDir, "avatars/$handle.webp")
            }
            if (avatarFile != null && avatarFile.exists()) {
                val bmp = android.graphics.BitmapFactory.decodeFile(avatarFile.absolutePath)
                if (bmp != null) {
                    // Crop to circle for notification
                    val size = minOf(bmp.width, bmp.height)
                    val cropped = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(cropped)
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
                    paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
                    canvas.drawBitmap(bmp, -(bmp.width - size) / 2f, -(bmp.height - size) / 2f, paint)
                    androidx.core.graphics.drawable.IconCompat.createWithBitmap(cropped)
                } else null
            } else null
        } catch (_: Exception) { null }

        // Build MessagingStyle
        val isGroup = convKey.startsWith("g_")
        val mePerson = Person.Builder().setName("Me").build()
        val messagingStyle = NotificationCompat.MessagingStyle(mePerson)

        if (isGroup) {
            // Group: title = group name only, each message shows sender with their avatar
            val groupName = senderName.substringBefore(": ").ifEmpty { senderName }
            messagingStyle.setConversationTitle(groupName)
            messagingStyle.isGroupConversation = true
            for (msg in history) {
                val msgSender = msg.senderName.substringAfter(": ", msg.senderName)
                // Use actual handle for avatar lookup (not display name)
                val handle = msg.senderHandle ?: msgSender.removePrefix("@").replace(Regex("[^a-zA-Z0-9_]"), "")
                val senderIcon: androidx.core.graphics.drawable.IconCompat? = try {
                    val f = java.io.File(appContext.filesDir, "avatars/$handle.webp")
                    if (f.exists()) {
                        val bmp = android.graphics.BitmapFactory.decodeFile(f.absolutePath)
                        if (bmp != null) {
                            val sz = minOf(bmp.width, bmp.height)
                            val c = android.graphics.Bitmap.createBitmap(sz, sz, android.graphics.Bitmap.Config.ARGB_8888)
                            val cv = android.graphics.Canvas(c)
                            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                            cv.drawCircle(sz / 2f, sz / 2f, sz / 2f, p)
                            p.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
                            cv.drawBitmap(bmp, -(bmp.width - sz) / 2f, -(bmp.height - sz) / 2f, p)
                            androidx.core.graphics.drawable.IconCompat.createWithBitmap(c)
                        } else null
                    } else null
                } catch (_: Exception) { null }
                val person = Person.Builder().setName(msgSender).apply {
                    if (senderIcon != null) setIcon(senderIcon)
                }.build()
                messagingStyle.addMessage(msg.text, msg.timestamp, person)
            }
        } else {
            // DM: title = sender name, avatar on person
            val senderPerson = Person.Builder().setName(senderName).apply {
                if (avatarIcon != null) setIcon(avatarIcon)
            }.build()
            messagingStyle.setConversationTitle(senderName)
            for (msg in history) {
                val person = if (msg.senderName == senderName) senderPerson
                    else Person.Builder().setName(msg.senderName).build()
                messagingStyle.addMessage(msg.text, msg.timestamp, person)
            }
        }

        // Per-chat notification channel (Android O+) for custom sound
        val prefs = appContext.getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)
        val soundUri = prefs.getString("notif_sound_$convKey", null)
        val effectiveChannelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !soundUri.isNullOrEmpty()) {
            val nm2 = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val chatChannelId = "privime_chat_${convKey.hashCode().and(0x7FFFFFFF)}"
            val version = prefs.getInt("notif_channel_ver_$convKey", 0)
            val versionedChannelId = "${chatChannelId}_v$version"
            // Create/recreate channel with custom sound
            nm2.createNotificationChannel(NotificationChannel(
                versionedChannelId,
                "Chat: ${convKey.removePrefix("@")}",
                if (soundUri == "silent") NotificationManager.IMPORTANCE_LOW else NotificationManager.IMPORTANCE_HIGH
            ).apply {
                group = GROUP_ID
                setShowBadge(true)
                if (soundUri == "silent") {
                    setSound(null, null)
                    enableVibration(false)
                } else {
                    setSound(android.net.Uri.parse(soundUri), android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                }
            })
            versionedChannelId
        } else {
            channelId // Use default channel
        }

        // Individual notification — same ID used for both messages and reactions (merged into one)
        val notifId = (convKey.hashCode() and 0x7FFFFFFF) % 7000 + 1000  // 1000-7999 range
        val notification = NotificationCompat.Builder(appContext, effectiveChannelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(messagingStyle)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(GROUP_ID)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .apply { if (avatarIcon != null) setLargeIcon(avatarIcon.toIcon(appContext)) }
            .build()

        nm.notify(notifId, notification)

        // Summary notification (for grouping + badge count)
        updateBadge(totalUnread)

        Log.d(TAG, "Notification: $senderName ($type) in $convKey, ${history.size} msgs, unread=$totalUnread${if (reactionEmoji != null) ", reaction=$reactionEmoji" else ""}")
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
