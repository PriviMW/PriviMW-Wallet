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

    // Active chat — suppress unread + notifications for this conversation
    private val _activeChat = MutableStateFlow<String?>(null)
    val activeChat: StateFlow<String?> = _activeChat.asStateFlow()

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

        // Initialize components
        IpfsTransport.init(context)
        identity = IdentityManager(db!!, scope)
        contacts = ContactManager(db!!, scope)
        processor = MessageProcessor(db!!, contacts, scope)
        sbbs = SbbsTransport(db!!, processor, scope)

        // Initialize chat_state row
        scope.launch {
            db!!.chatStateDao().ensureInitialized()
        }

        // Ensure wallet events are subscribed (may have been skipped on wallet reuse path)
        com.privimemobile.protocol.WalletApi.subscribeToEvents()

        // Start identity check + SBBS polling
        scope.launch {
            identity.refreshIdentity()
            sbbs.startPolling()
        }

        _initialized.value = true
        Log.d(TAG, "Chat system initialized")
    }

    /** Set the currently active chat (suppress unread/notifications). */
    fun setActiveChat(convKey: String?) {
        _activeChat.value = convKey
        if (convKey != null) {
            // Clear unread for this conversation
            scope.launch {
                val conv = db?.conversationDao()?.findByKey(convKey)
                if (conv != null && conv.unreadCount > 0) {
                    db?.conversationDao()?.clearUnread(conv.id)
                    // Send read receipts
                    val unacked = db?.messageDao()?.getUnackedTimestamps(conv.id) ?: emptyList()
                    if (unacked.isNotEmpty()) {
                        sbbs.sendReadReceipts(convKey, unacked)
                    }
                }
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
