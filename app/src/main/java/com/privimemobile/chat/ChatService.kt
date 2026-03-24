package com.privimemobile.chat

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.privimemobile.chat.contacts.ContactManager
import com.privimemobile.chat.db.ChatDatabase
import com.privimemobile.chat.db.entities.ChatStateEntity
import com.privimemobile.chat.identity.IdentityManager
import com.privimemobile.chat.processor.MessageProcessor
import com.privimemobile.chat.notification.ChatNotificationManager
import com.privimemobile.chat.transport.IpfsTransport
import com.privimemobile.chat.transport.SbbsTransport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * ChatService — singleton hub for the entire messaging system.
 *
 * Owns its own SupervisorJob scope that survives Activity lifecycle.
 * Manages: database, identity, SBBS transport, message processing, contacts.
 *
 * Initialized once from MainActivity after wallet opens.
 * Survives swipe-away (BackgroundService keeps process alive).
 */
object ChatService {
    private const val TAG = "ChatService"

    // Timestamp (epoch seconds) when this app session started — used to filter stale SBBS
    val sessionStartTs: Long = System.currentTimeMillis() / 1000

    // Scope — never tied to Activity. Cancelled only on wallet deletion.
    private val serviceJob = SupervisorJob()
    val scope = CoroutineScope(Dispatchers.Main + serviceJob)

    // Core components
    var db: ChatDatabase? = null
        private set
    lateinit var identity: IdentityManager
        private set
    lateinit var sbbs: SbbsTransport
        private set
    lateinit var processor: MessageProcessor
        private set
    lateinit var contacts: ContactManager
        private set
    lateinit var groups: com.privimemobile.chat.group.GroupManager
        private set
    lateinit var pendingTxs: com.privimemobile.chat.group.PendingTxManager
        private set

    // Active chat — suppress unread + notifications for this conversation
    private val _activeChat = MutableStateFlow<String?>(null)
    val activeChat: StateFlow<String?> = _activeChat.asStateFlow()

    // Typing indicators — convKey → version counter (increment = typing, even = idle)
    private val _typingVersion = MutableStateFlow(0)
    val typingVersion: StateFlow<Int> = _typingVersion.asStateFlow()
    private val typingExpiry = mutableMapOf<String, Long>()

    /** Called by MessageProcessor when a typing indicator arrives. */
    fun onTypingReceived(convKey: String) {
        Log.d(TAG, "onTypingReceived: $convKey")
        val now = System.currentTimeMillis()
        if (typingExpiry.size > 100) typingExpiry.entries.removeAll { it.value <= now } // cap size
        typingExpiry[convKey] = now + 5_000
        _typingVersion.value++ // force recomposition
        // Auto-clear after 5s
        scope.launch {
            delay(5_100)
            if ((typingExpiry[convKey] ?: 0) <= System.currentTimeMillis()) {
                typingExpiry.remove(convKey)
                _typingVersion.value++ // force recomposition
            }
        }
    }

    /** Check if someone is typing in a conversation (call from Compose). */
    fun isTyping(convKey: String): Boolean {
        return (typingExpiry[convKey] ?: 0) > System.currentTimeMillis()
    }

    /** Clear typing indicator (called when a real message arrives from this person). */
    fun clearTyping(convKey: String) {
        if (typingExpiry.remove(convKey) != null) {
            _typingVersion.value++
        }
    }

    // Group typing: convKey → (handle → expiryMs)
    private val groupTypingMap = mutableMapOf<String, MutableMap<String, Long>>()

    /** Called when a group typing indicator arrives. */
    fun onGroupTypingReceived(groupConvKey: String, handle: String) {
        val now = System.currentTimeMillis()
        if (groupTypingMap.size > 50) groupTypingMap.entries.removeAll { e -> e.value.all { it.value <= now } } // cap size
        val map = groupTypingMap.getOrPut(groupConvKey) { mutableMapOf() }
        if (map.size > 50) map.entries.removeAll { it.value <= now }
        map[handle] = now + 5_000
        _typingVersion.value++
        scope.launch {
            delay(5_100)
            val m = groupTypingMap[groupConvKey]
            if (m != null && (m[handle] ?: 0) <= System.currentTimeMillis()) {
                m.remove(handle)
                _typingVersion.value++
            }
        }
    }

    /** Get list of handles currently typing in a group. */
    fun getGroupTyping(groupConvKey: String): List<String> {
        val now = System.currentTimeMillis()
        val map = groupTypingMap[groupConvKey] ?: return emptyList()
        return map.filter { it.value > now }.keys.toList()
    }

    // Initialization state
    private val _initialized = MutableStateFlow(false)
    val initialized: StateFlow<Boolean> = _initialized.asStateFlow()

    /**
     * Initialize the chat system. Call once after wallet opens.
     * Safe to call multiple times — skips if already initialized.
     */
    fun init(context: Context) {
        if (_initialized.value && db != null) {
            Log.d(TAG, "Already initialized — restarting polling")
            com.privimemobile.protocol.WalletApi.subscribeToEvents()
            sbbs.restartPolling()  // Kill stale job, start fresh
            return
        }

        Log.d(TAG, "Initializing chat system...")

        // Derive SQLCipher passphrase from encrypted storage
        val passphrase = getOrCreateDbPassphrase(context)

        // Open encrypted database
        db = ChatDatabase.getInstance(context, passphrase)
        // Check group count immediately after DB open
        scope.launch {
            val groups = db?.groupDao()?.getAllGroups()
            Log.d(TAG, "DB opened — groups in DB: ${groups?.size}, ids: ${groups?.map { it.groupId.take(8) + "(p=${it.pinned},m=${it.muted})" }}")
        }

        // Initialize components
        IpfsTransport.init(context)
        ChatNotificationManager.init(context)
        com.privimemobile.wallet.TxNotificationManager.init(context)
        com.privimemobile.wallet.TxNotificationManager.startObserving(scope)
        identity = IdentityManager(db!!, scope)
        contacts = ContactManager(db!!, scope)
        processor = MessageProcessor(db!!, contacts, scope)
        sbbs = SbbsTransport(db!!, processor, scope)
        groups = com.privimemobile.chat.group.GroupManager(db!!, scope)
        pendingTxs = com.privimemobile.chat.group.PendingTxManager(db!!, scope)

        // Initialize chat_state row + migrate legacy SharedPreferences data
        scope.launch {
            db!!.chatStateDao().ensureInitialized()
            LegacyMigration.migrateIfNeeded(context.applicationContext, db!!)
        }

        // Ensure wallet events are subscribed (may have been skipped on wallet reuse path)
        com.privimemobile.protocol.WalletApi.subscribeToEvents()

        // Start identity check + SBBS polling
        scope.launch {
            identity.refreshIdentity()
            sbbs.startPolling()
        }

        // Observe total unread → update launcher badge via notification summary
        scope.launch {
            db!!.conversationDao().observeTotalUnread().collect { count ->
                ChatNotificationManager.updateBadge(count)
            }
        }

        // Periodic cleanup of expired messages + send scheduled messages + check pending TXs (every 15s)
        scope.launch {
            try { cleanupExpiredMessages() } catch (_: Exception) {}
            try { sendScheduledMessages() } catch (_: Exception) {}
            while (true) {
                delay(15_000)
                try { cleanupExpiredMessages() } catch (e: Exception) {
                    Log.w(TAG, "Disappearing cleanup error: ${e.message}")
                }
                try { sendScheduledMessages() } catch (e: Exception) {
                    Log.w(TAG, "Scheduled send error: ${e.message}")
                }
                try { pendingTxs.checkPendingTxs() } catch (_: Exception) {}
            }
        }

        // Periodic avatar + group info re-request (every 30 min until all resolved)
        scope.launch {
            delay(10_000) // wait for initial setup
            while (true) {
                try { requestMissingAvatars() } catch (_: Exception) {}
                try { requestMissingGroupInfo() } catch (_: Exception) {}
                delay(10 * 60 * 1000L) // 10 minutes
            }
        }

        _initialized.value = true
        Log.d(TAG, "Chat system initialized")
    }

    /** Clean up expired disappearing messages and update affected conversation previews. */
    private suspend fun cleanupExpiredMessages() {
        val now = System.currentTimeMillis() / 1000
        // Find which conversations are affected BEFORE deleting
        val affectedConvIds = db?.messageDao()?.getConversationsWithExpired(now) ?: emptyList()
        val deleted = db?.messageDao()?.deleteExpired(now) ?: 0
        if (deleted > 0) {
            Log.d(TAG, "Cleaned up $deleted expired disappearing messages")
            // Update conversation previews for affected conversations
            for (convId in affectedConvIds) {
                val latest = db?.messageDao()?.getLatestMessage(convId)
                if (latest != null) {
                    val preview = when (latest.type) {
                        "tip" -> "Tip"
                        "file" -> "\uD83D\uDCCE File"
                        else -> latest.text?.take(100)
                    }
                    db?.conversationDao()?.updateLastMessage(convId, latest.timestamp, preview)
                } else {
                    // No messages left — clear preview
                    db?.conversationDao()?.updateLastMessage(convId, 0, null)
                }
            }
        }
    }

    /** Send any scheduled messages whose time has arrived. */
    private suspend fun sendScheduledMessages() {
        val now = System.currentTimeMillis() / 1000
        val ready = db?.messageDao()?.getReadyScheduledMessages(now) ?: return
        if (ready.isNotEmpty()) Log.d(TAG, "sendScheduledMessages: ${ready.size} ready (now=$now)")
        if (ready.isEmpty()) return
        val state = db?.chatStateDao()?.get() ?: return
        val myHandle = state.myHandle ?: return

        for (msg in ready) {
            try {
                val conv = db?.conversationDao()?.findById(msg.conversationId)
                if (conv == null) {
                    Log.w(TAG, "Scheduled msg ${msg.id}: conv not found for conversationId=${msg.conversationId}")
                    continue
                }
                val ts = System.currentTimeMillis() / 1000
                Log.d(TAG, "Scheduled msg ${msg.id}: convKey=${conv.convKey} text=${msg.text?.take(20)}")

                if (conv.convKey.startsWith("g_")) {
                    // Group scheduled message — broadcast only (message already in DB)
                    val groupPrefix = conv.convKey.removePrefix("g_")
                    val group = db?.groupDao()?.findByConvKey(groupPrefix)
                    if (group == null) {
                        Log.w(TAG, "Scheduled msg ${msg.id}: group not found for convKey=${conv.convKey}")
                        continue
                    }
                    val memberWalletIds = db?.groupDao()?.getMemberWalletIds(group.groupId, myHandle)
                        ?.filterNotNull()?.filter { it.isNotEmpty() } ?: continue
                    val payload = mutableMapOf<String, Any?>(
                        "v" to 1, "t" to "group_msg", "ts" to ts,
                        "from" to myHandle, "group_id" to group.groupId,
                        "msg" to (msg.text ?: ""),
                    )
                    if (!state.myDisplayName.isNullOrEmpty()) payload["dn"] = state.myDisplayName
                    for (walletId in memberWalletIds) {
                        try { sbbs.sendOnce(walletId, payload) } catch (_: Exception) {}
                        kotlinx.coroutines.delay(200)
                    }
                    db?.groupDao()?.updateLastMessage(group.groupId, ts, "You: ${msg.text?.take(40) ?: "message"}")
                } else {
                    // DM scheduled message
                    val contactHandle = conv.convKey.removePrefix("@")
                    val contact = db?.contactDao()?.findByHandle(contactHandle)
                    val walletId = contact?.walletId ?: continue
                    val payload = mutableMapOf<String, Any?>(
                        "v" to 1, "t" to "dm", "ts" to ts,
                        "from" to myHandle, "to" to contactHandle,
                        "dn" to (state.myDisplayName ?: ""),
                        "msg" to (msg.text ?: ""),
                    )
                    sbbs.sendWithRetry(walletId, payload)
                }
                // Update message: clear scheduled_at + move timestamp to actual send time
                db?.messageDao()?.clearScheduled(msg.id)
                db?.messageDao()?.updateTimestamp(msg.id, ts)
                // Update conversation preview
                val preview = msg.text?.take(100)
                db?.conversationDao()?.updateLastMessage(msg.conversationId, ts, preview)
                Log.d(TAG, "Sent scheduled message id=${msg.id} convKey=${conv.convKey} at ts=$ts")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send scheduled msg id=${msg.id}: ${e.message}")
            }
        }
    }

    /** Re-request missing contact avatars. Runs periodically until all resolved. */
    private suspend fun requestMissingAvatars() {
        val filesDir = com.privimemobile.chat.transport.IpfsTransport.filesDir ?: return
        val allContacts = db?.contactDao()?.getAll() ?: return
        var requested = 0
        for (c in allContacts) {
            if (c.walletId.isNullOrEmpty() || c.isDeleted) continue
            val avatarFile = java.io.File(filesDir, "avatars/${c.handle}.webp")
            if (!avatarFile.exists()) {
                try {
                    contacts.requestAvatar(c.handle, c.walletId!!)
                    requested++
                    kotlinx.coroutines.delay(500)
                } catch (_: Exception) {}
            }
        }
        if (requested > 0) Log.d(TAG, "Requested $requested missing contact avatars")
    }

    /** Re-request missing group avatars + descriptions. */
    private suspend fun requestMissingGroupInfo() {
        val filesDir = com.privimemobile.chat.transport.IpfsTransport.filesDir ?: return
        val state = db?.chatStateDao()?.get() ?: return
        val myHandle = state.myHandle ?: return
        val groups = db?.groupDao()?.getAllGroups() ?: return
        var requested = 0
        for (group in groups) {
            val avatarFile = java.io.File(filesDir, "group_avatars/${group.groupId}.webp")
            val needsAvatar = group.avatarHash != null && !avatarFile.exists()
            val needsDesc = group.description.isNullOrEmpty()
            if (needsAvatar || needsDesc) {
                // Send request to first member with a wallet_id
                val memberWids = db?.groupDao()?.getMemberWalletIds(group.groupId, myHandle)
                    ?.filterNotNull()?.filter { it.isNotEmpty() } ?: continue
                val targetWid = memberWids.firstOrNull() ?: continue
                val reqPayload = mapOf(
                    "v" to 1, "t" to "group_info_request",
                    "from" to myHandle,
                    "group_id" to group.groupId,
                    "requester_wallet_id" to (state.myWalletId ?: ""),
                    "ts" to (System.currentTimeMillis() / 1000),
                )
                try {
                    sbbs.sendOnce(targetWid, reqPayload)
                    requested++
                    kotlinx.coroutines.delay(500)
                } catch (_: Exception) {}
            }
        }
        if (requested > 0) Log.d(TAG, "Requested info for $requested groups with missing avatar/description")
    }

    /** Set the currently active chat (suppress unread/notifications). */
    fun setActiveChat(convKey: String?) {
        _activeChat.value = convKey
        Log.d(TAG, "setActiveChat: $convKey")
        if (convKey != null) {
            // Cancel notification for this conversation
            ChatNotificationManager.cancelForConversation(convKey)
            scope.launch {
                val conv = db?.conversationDao()?.findByKey(convKey) ?: run {
                    Log.w(TAG, "setActiveChat: conv not found for $convKey")
                    return@launch
                }
                // Clear unread badge
                if (conv.unreadCount > 0) {
                    db?.conversationDao()?.clearUnread(conv.id)
                }

                if (convKey.startsWith("g_")) {
                    // Group chat — send acks to each sender individually
                    val groupIdPrefix = convKey.removePrefix("g_")
                    val groupId = db?.groupDao()?.findByConvKey(groupIdPrefix)?.groupId
                    Log.d(TAG, "setActiveChat group: prefix=$groupIdPrefix groupId=$groupId convId=${conv.id}")
                    if (groupId != null) {
                        sbbs.sendGroupReadReceipts(groupId, conv.id)
                        // Also clear group unread
                        db?.groupDao()?.clearUnread(groupId)
                    }
                } else {
                    // DM — send read receipts for ALL received messages (catch-all)
                    sendAllAcksForConv(convKey, conv.id)
                }
            }
        }
    }

    /** Send read receipts for ALL received messages in a conversation (catch-all on chat open). */
    private fun sendAllAcksForConv(convKey: String, convId: Long) {
        scope.launch {
            val allTimestamps = db?.messageDao()?.getAllReceivedTimestamps(convId) ?: return@launch
            Log.d(TAG, "sendAllAcksForConv($convKey, convId=$convId): ${allTimestamps.size} total received")
            if (allTimestamps.isNotEmpty()) {
                sbbs.sendReadReceipts(convKey, allTimestamps)
            }
        }
    }

    /** Send read receipts for unacked received messages (called per-message on arrival). */
    fun sendAcksForConv(convKey: String, convId: Long) {
        scope.launch {
            val unacked = db?.messageDao()?.getUnackedTimestamps(convId) ?: return@launch
            Log.d(TAG, "sendAcksForConv($convKey, convId=$convId): ${unacked.size} unacked")
            if (unacked.isNotEmpty()) {
                sbbs.sendReadReceipts(convKey, unacked)
            }
        }
    }

    /** Observe chat state (identity, registration fee, etc). */
    fun observeState(): Flow<ChatStateEntity?> {
        return db?.chatStateDao()?.observe() ?: flowOf(null)
    }

    /** Check if user is registered. */
    suspend fun isRegistered(): Boolean {
        val state = db?.chatStateDao()?.get()
        return state?.myHandle != null
    }

    /** Get current handle. */
    suspend fun getMyHandle(): String? {
        return db?.chatStateDao()?.get()?.myHandle
    }

    /** Called when app comes to foreground after background. */
    fun onForegroundRecovery() {
        if (!_initialized.value) return
        Log.d(TAG, "Foreground recovery — refreshing")
        // Re-subscribe to wallet events (may have been dropped)
        com.privimemobile.protocol.WalletApi.subscribeToEvents()
        // Force restart polling (old job may be stuck)
        sbbs.restartPolling()
        scope.launch {
            identity.refreshIdentity()
            sbbs.pollNow()
        }
    }

    /** Full shutdown — used on wallet deletion. */
    fun shutdown() {
        Log.d(TAG, "Shutting down chat system")
        sbbs.stopPolling()
        ChatDatabase.close()
        db = null
        _initialized.value = false
        _activeChat.value = null
    }

    /** Derive or retrieve the SQLCipher database passphrase. */
    private fun getOrCreateDbPassphrase(context: Context): ByteArray {
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val prefs = EncryptedSharedPreferences.create(
            "privime_chat_keys",
            masterKey,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

        val existingKey = prefs.getString("db_passphrase", null)
        if (existingKey != null) {
            return existingKey.toByteArray()
        }

        // Generate random 32-byte key
        val key = ByteArray(32)
        java.security.SecureRandom().nextBytes(key)
        val keyHex = key.joinToString("") { "%02x".format(it) }
        prefs.edit().putString("db_passphrase", keyHex).commit()
        return keyHex.toByteArray()
    }
}
