package com.privimemobile.protocol

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Protocol orchestration — ties together shader, SBBS, contacts, and messages.
 *
 * Ports startup.ts to Kotlin.
 * Manages: identity check, message polling, conversation state, contact resolution.
 */
object ProtocolStartup {
    private const val TAG = "ProtocolStartup"
    private var pollingJob: Job? = null

    // Protocol state exposed as StateFlow for Compose UI
    private val _identity = MutableStateFlow<Identity?>(null)
    val identity: StateFlow<Identity?> = _identity.asStateFlow()

    private val _conversations = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    val conversations: StateFlow<Map<String, List<ChatMessage>>> = _conversations.asStateFlow()

    private val _contacts = MutableStateFlow<Map<String, Contact>>(emptyMap())
    val contacts: StateFlow<Map<String, Contact>> = _contacts.asStateFlow()

    private val _unreadCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadCounts: StateFlow<Map<String, Int>> = _unreadCounts.asStateFlow()

    private var contractStartTs: Long = 0
    private val deletedConvs = mutableMapOf<String, Long>()
    private val blockedUsers = mutableSetOf<String>()
    var activeChat: String? = null

    /**
     * Initialize the protocol — call after wallet is opened and API is ready.
     *
     * 1. Load shader bytes
     * 2. Check own identity
     * 3. Subscribe to wallet events
     * 4. Start message polling
     */
    fun init(context: Context, scope: CoroutineScope) {
        Log.d(TAG, "Initializing protocol...")

        // Init subsystems
        ProtocolStorage.init(context)
        FileCache.init(context)
        ShaderInvoker.loadShader(context)
        DAppStore.loadShader(context)
        NodeReconnect.start(scope)

        // Load persisted state
        val savedConvs = ProtocolStorage.loadConversations()
        val savedContacts = ProtocolStorage.loadContacts()
        val savedUnread = ProtocolStorage.loadUnreadCounts()
        contractStartTs = ProtocolStorage.getContractStartTs()
        deletedConvs.putAll(ProtocolStorage.loadDeletedConvs())
        blockedUsers.addAll(ProtocolStorage.loadBlockedUsers())

        if (savedConvs.isNotEmpty()) _conversations.value = savedConvs
        if (savedContacts.isNotEmpty()) _contacts.value = savedContacts
        if (savedUnread.isNotEmpty()) _unreadCounts.value = savedUnread

        // Wire wallet events to protocol actions
        WalletApi.onSystemStateChanged = { checkIdentity() }
        WalletApi.onTxsChanged = { loadMessages(fetchAll = false) }

        // Check identity
        checkIdentity()

        // Start message polling
        startPolling(scope)
    }

    fun shutdown() {
        pollingJob?.cancel()
        pollingJob = null
        NodeReconnect.stop()
        WalletApi.onSystemStateChanged = null
        WalletApi.onTxsChanged = null

        // Persist state
        ProtocolStorage.saveAll(
            _conversations.value,
            _contacts.value,
            deletedConvs,
            _unreadCounts.value,
        )
    }

    /** Check our own PriviMe identity on-chain. */
    fun checkIdentity() {
        ContactResolver.checkOwnIdentity { id ->
            val previousHandle = _identity.value?.handle
            _identity.value = id
            if (id.registered) {
                Log.d(TAG, "Identity: @${id.handle} (${id.displayName})")
                // Set storage scope when identity is first discovered or changes
                if (previousHandle != id.handle) {
                    ProtocolStorage.setUserScope(id.handle)
                    // Set contract start timestamp on first run
                    if (contractStartTs == 0L) {
                        contractStartTs = System.currentTimeMillis() / 1000
                        ProtocolStorage.setContractStartTs(contractStartTs)
                    }
                }
            } else {
                Log.d(TAG, "Not registered")
            }
        }
    }

    /** Load messages from SBBS inbox. */
    fun loadMessages(fetchAll: Boolean = false) {
        SbbsMessaging.readMessages(fetchAll) { result ->
            // Result is the message array (or error)
            if (result.containsKey("error")) {
                Log.w(TAG, "read_messages error: ${result["error"]}")
                return@readMessages
            }

            // The result itself is the array when read_messages returns directly
            // But our WalletApi parses it — we need the raw JSON
            // For now, use the WalletEventBus approach
        }
    }

    /** Process raw SBBS messages and update state. */
    fun processRawMessages(rawJson: String) {
        val state = ProtocolState(
            conversations = _conversations.value,
            contacts = _contacts.value,
            unreadCounts = _unreadCounts.value,
            deletedConvs = deletedConvs,
            activeChat = activeChat,
            myHandle = _identity.value,
            contractStartTs = contractStartTs,
            blockedUsers = blockedUsers,
        )

        val result = MessageProcessor.processMessages(rawJson, state)
        _conversations.value = result.conversations
        _contacts.value = result.contacts
        _unreadCounts.value = result.unreadCounts

        // Resolve pending contacts
        result.pendingResolves.forEach { resolve ->
            ContactResolver.resolveHandle(resolve.value) { contact ->
                if (contact != null) {
                    _contacts.value = _contacts.value.toMutableMap().also {
                        it[resolve.key] = contact
                    }
                }
            }
        }
    }

    /** Send a chat message to a handle. */
    fun sendMessage(toHandle: String, text: String) {
        val myId = _identity.value ?: return
        val contact = _contacts.value["@$toHandle"] ?: return
        if (contact.walletId.isEmpty()) {
            Log.w(TAG, "No wallet ID for @$toHandle — resolving...")
            ContactResolver.resolveHandle(toHandle) { resolved ->
                if (resolved != null && resolved.walletId.isNotEmpty()) {
                    _contacts.value = _contacts.value.toMutableMap().also {
                        it["@$toHandle"] = resolved
                    }
                    doSend(resolved.walletId, myId, toHandle, text)
                }
            }
            return
        }
        doSend(contact.walletId, myId, toHandle, text)
    }

    private fun doSend(walletId: String, myId: Identity, toHandle: String, text: String) {
        val payload = SbbsMessaging.buildChatPayload(
            fromHandle = myId.handle,
            toHandle = toHandle,
            text = text,
            displayName = myId.displayName,
        )
        SbbsMessaging.sendWithRetry(walletId, payload)

        // Add to local conversations immediately (optimistic)
        val convKey = "@$toHandle"
        val msg = ChatMessage(
            id = "${System.currentTimeMillis() / 1000}-${myId.handle}-$toHandle",
            from = myId.handle,
            to = toHandle,
            text = text,
            timestamp = System.currentTimeMillis() / 1000,
            sent = true,
            displayName = myId.displayName,
        )
        val updated = _conversations.value.toMutableMap()
        val msgs = updated.getOrPut(convKey) { emptyList() }.toMutableList()
        msgs.add(msg)
        updated[convKey] = msgs
        _conversations.value = updated
    }

    /** Clear unread count for a conversation. */
    fun clearUnread(convKey: String) {
        if (_unreadCounts.value.containsKey(convKey)) {
            _unreadCounts.value = _unreadCounts.value.toMutableMap().also {
                it.remove(convKey)
            }
        }
    }

    private fun startPolling(scope: CoroutineScope) {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                loadMessages(fetchAll = false)
                delay(Config.MSG_REFRESH_MS)
            }
        }
    }
}
