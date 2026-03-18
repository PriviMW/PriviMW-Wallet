package com.privimemobile.chat

import android.content.Context
import android.util.Log
import com.privimemobile.chat.db.ChatDatabase
import com.privimemobile.chat.db.entities.*
import com.privimemobile.protocol.ProtocolStorage

/**
 * One-time migration of chat data from SharedPreferences (ProtocolStorage)
 * into Room database. Runs on first ChatService init when DB is empty.
 *
 * Best-effort: failures are logged but don't block startup.
 */
object LegacyMigration {
    private const val TAG = "LegacyMigration"
    private const val PREF_KEY = "privime_legacy_migrated"

    suspend fun migrateIfNeeded(context: Context, db: ChatDatabase) {
        val prefs = context.getSharedPreferences("privimw_protocol", Context.MODE_PRIVATE)
        // Check if already migrated
        if (prefs.getBoolean(PREF_KEY, false)) return

        // Check if there's any data to migrate
        val conversations = ProtocolStorage.loadConversations()
        if (conversations.isEmpty()) {
            prefs.edit().putBoolean(PREF_KEY, true).apply()
            return
        }

        Log.d(TAG, "Migrating ${conversations.size} conversations from SharedPreferences...")

        try {
            val contacts = ProtocolStorage.loadContacts()
            val deletedConvs = ProtocolStorage.loadDeletedConvs()
            val unreadCounts = ProtocolStorage.loadUnreadCounts()
            val blockedUsers = ProtocolStorage.loadBlockedUsers()

            // Migrate contacts
            var contactCount = 0
            contacts.forEach { (_, contact) ->
                if (contact.handle.isNotEmpty()) {
                    db.contactDao().insert(ContactEntity(
                        handle = contact.handle,
                        walletId = contact.walletId,
                        displayName = contact.displayName,
                    ))
                    contactCount++
                }
            }
            Log.d(TAG, "Migrated $contactCount contacts")

            // Migrate conversations and messages
            var msgCount = 0
            conversations.forEach { (convKey, messages) ->
                if (messages.isEmpty()) return@forEach

                // Determine handle from convKey
                val handle = if (convKey.startsWith("@")) convKey.removePrefix("@") else convKey
                val displayName = contacts[convKey]?.displayName
                    ?: contacts[handle]?.displayName
                val walletId = contacts[convKey]?.walletId
                    ?: contacts[handle]?.walletId

                // Create conversation
                val lastMsg = messages.maxByOrNull { it.timestamp }
                val unread = unreadCounts[convKey] ?: 0
                val deletedTs = deletedConvs[convKey] ?: 0
                val isBlocked = blockedUsers.contains(convKey) || blockedUsers.contains(handle)

                val conv = db.conversationDao().getOrCreate(
                    convKey = convKey,
                    handle = handle,
                    displayName = displayName,
                    walletId = walletId,
                )

                // Update conversation metadata
                if (lastMsg != null) {
                    db.conversationDao().updateLastMessage(conv.id, lastMsg.timestamp, lastMsg.text.take(100))
                }
                if (unread > 0) {
                    db.conversationDao().setUnread(conv.id, unread)
                }
                if (deletedTs > 0) {
                    db.conversationDao().setDeletedTs(conv.id, deletedTs)
                }
                if (isBlocked) {
                    db.conversationDao().setBlocked(conv.id, true)
                }

                // Migrate messages
                messages.forEach { msg ->
                    val hashInput = "${msg.timestamp}:${msg.text}:${msg.type}:${msg.sent}"
                    val hash = hashInput.hashCode().toString(16)
                    val dedupKey = "${msg.timestamp}:$hash:${msg.sent}"

                    val entity = MessageEntity(
                        conversationId = conv.id,
                        text = msg.text.ifEmpty { null },
                        timestamp = msg.timestamp,
                        sent = msg.sent,
                        type = msg.type,
                        tipAmount = msg.tipAmount,
                        tipAssetId = msg.tipAssetId,
                        fwdFrom = msg.fwdFrom,
                        senderHandle = msg.from.ifEmpty { null },
                        sbbsDedupKey = dedupKey,
                    )
                    val msgId = db.messageDao().insert(entity)

                    // Migrate file attachment
                    if (msgId != -1L && msg.file != null && msg.file.key.isNotEmpty()) {
                        db.attachmentDao().insert(AttachmentEntity(
                            messageId = msgId,
                            conversationId = conv.id,
                            ipfsCid = msg.file.cid.ifEmpty { "inline-legacy" },
                            encryptionKey = msg.file.key,
                            encryptionIv = msg.file.iv,
                            fileName = msg.file.name,
                            fileSize = msg.file.size,
                            mimeType = msg.file.mime.ifEmpty { "application/octet-stream" },
                            inlineData = msg.file.data,
                        ))
                    }
                    if (msgId != -1L) msgCount++
                }
            }

            Log.d(TAG, "Migration complete: $msgCount messages across ${conversations.size} conversations")

            // Mark as migrated
            prefs.edit().putBoolean(PREF_KEY, true).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Migration failed (non-fatal): ${e.message}")
            // Still mark as migrated to avoid retrying on every launch
            prefs.edit().putBoolean(PREF_KEY, true).apply()
        }
    }
}
