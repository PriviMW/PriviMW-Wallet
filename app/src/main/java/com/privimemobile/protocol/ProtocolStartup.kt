package com.privimemobile.protocol

import android.content.Context
import android.util.Log
import com.privimemobile.wallet.WalletEventBus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Protocol orchestration — ties together shader, SBBS, contacts, and messages.
 *
 * Ports startup.ts to Kotlin.
 * Manages: identity check, message polling, conversation state, contact resolution.
 */
object ProtocolStartup {
    private const val TAG = "ProtocolStartup"
    private var pollingJob: Job? = null
    private var nodeRecoveryJob: Job? = null
    private var pollingScope: CoroutineScope? = null
    private var wasDisconnected = false

    // Protocol state exposed as StateFlow for Compose UI
    private val _identity = MutableStateFlow<Identity?>(null)
    val identity: StateFlow<Identity?> = _identity.asStateFlow()

    private val _conversations = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    val conversations: StateFlow<Map<String, List<ChatMessage>>> = _conversations.asStateFlow()

    private val _contacts = MutableStateFlow<Map<String, Contact>>(emptyMap())
    val contacts: StateFlow<Map<String, Contact>> = _contacts.asStateFlow()

    private val _unreadCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadCounts: StateFlow<Map<String, Int>> = _unreadCounts.asStateFlow()

    private val _registrationFee = MutableStateFlow(0.0)
    val registrationFee: StateFlow<Double> = _registrationFee.asStateFlow()

    // SBBS address needs re-registration (restored wallet detected)
    private val _sbbsNeedsUpdate = MutableStateFlow(false)
    val sbbsNeedsUpdate: StateFlow<Boolean> = _sbbsNeedsUpdate.asStateFlow()

    private val _sbbsUpdating = MutableStateFlow(false)
    val sbbsUpdating: StateFlow<Boolean> = _sbbsUpdating.asStateFlow()

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

        pollingScope = scope

        // Wire wallet events to protocol actions
        WalletApi.onSystemStateChanged = {
            checkIdentity()
            refreshWalletData()
        }
        WalletApi.onTxsChanged = {
            loadMessages(fetchAll = false)
            refreshWalletData()
        }

        // Node reconnection recovery (matches RN useProtocol wasDisconnected pattern)
        nodeRecoveryJob = scope.launch {
            WalletEventBus.nodeConnection.collect { event ->
                if (!event.connected) {
                    wasDisconnected = true
                } else if (wasDisconnected) {
                    wasDisconnected = false
                    Log.d(TAG, "Node reconnected — re-syncing protocol")
                    WalletApi.resubscribeEvents()
                    ensurePollingRunning()
                    // Delay to let the node stabilize (matches RN 3s delay)
                    delay(3000)
                    refreshAll()
                    loadMessages(fetchAll = true) // catch missed messages
                }
            }
        }

        // Start message polling immediately if we have a cached identity
        if (_identity.value != null || savedConvs.isNotEmpty()) {
            Log.d(TAG, "Cached identity found, starting message poll immediately")
            startPolling(scope)
            loadMessages(fetchAll = true) // fetch all on startup to get offline messages
        }

        // Refresh identity from contract (non-blocking — poll already running)
        refreshAll()
        refreshWalletData()
    }

    fun shutdown() {
        pollingJob?.cancel()
        pollingJob = null
        nodeRecoveryJob?.cancel()
        nodeRecoveryJob = null
        NodeReconnect.stop()
        WalletApi.onSystemStateChanged = null
        WalletApi.onTxsChanged = null

        // Persist state
        persistState()
    }

    /**
     * Foreground recovery — called when app returns from background.
     * Matches RN useProtocol handleAppState('active') handler.
     */
    fun onForegroundRecovery() {
        Log.d(TAG, "Foreground recovery — restarting polling + refreshing")
        // Restart message poll timer (setInterval is suspended by Android in background)
        ensurePollingRunning()
        // Refresh state after C++ core wakes up
        refreshAll()
        loadMessages(fetchAll = false)
    }

    private fun ensurePollingRunning() {
        if (pollingJob?.isActive == true) return
        val scope = pollingScope ?: return
        startPolling(scope)
    }

    /** Refresh wallet balance, transactions, and addresses. */
    private fun refreshWalletData() {
        val wallet = com.privimemobile.wallet.WalletManager.walletInstance ?: return
        try {
            wallet.getWalletStatus()
            wallet.getTransactions()
            wallet.getAddresses(true)
        } catch (e: Exception) {
            Log.w(TAG, "refreshWalletData failed: ${e.message}")
        }
    }

    /**
     * Refresh identity + registration fee from contract.
     * Ports refreshAll() from RN startup.ts.
     */
    fun refreshAll() {
        ShaderInvoker.invoke("user", "my_handle") { result ->
            if (!result.containsKey("error")) {
                // Contract responded successfully — apply whatever it says
                applyMyHandle(result)
            } else if (_identity.value != null) {
                // Error/timeout but we have a cached identity — keep it, don't wipe
                Log.w(TAG, "my_handle failed, keeping cached identity")
            } else {
                Log.w(TAG, "my_handle failed, no cached identity")
            }

            // Fetch registration fee AFTER my_handle completes — avoids concurrent invoke_contract
            ShaderInvoker.invoke("manager", "view_pool") { r ->
                @Suppress("UNCHECKED_CAST")
                val pool = r["pool"] as? Map<String, Any?>
                val fee = pool?.get("registration_fee")
                if (fee != null) {
                    val feeGroth = when (fee) {
                        is Number -> fee.toLong()
                        else -> 0L
                    }
                    if (feeGroth > 0) {
                        _registrationFee.value = feeGroth.toDouble() / Config.GROTH_PER_BEAM
                    }
                }
            }
        }
    }

    /**
     * Apply identity result from my_handle shader call.
     * Ports applyMyHandle() from RN startup.ts.
     */
    private fun applyMyHandle(result: Map<String, Any?>) {
        val registered = (result["registered"] as? Number)?.toInt() ?: 0
        if (registered == 1) {
            val confirmedHandle = (result["handle"] as? String ?: "").lowercase()
            val previousHandle = _identity.value?.handle?.lowercase()
            val scopeChanged = previousHandle == null || previousHandle != confirmedHandle

            if (scopeChanged && confirmedHandle.isNotEmpty()) {
                ProtocolStorage.setUserScope(confirmedHandle)
                // Reload persisted state for the new scope
                val loaded = ProtocolStorage.loadConversations()
                val loadedContacts = ProtocolStorage.loadContacts()
                val loadedUnread = ProtocolStorage.loadUnreadCounts()
                if (loaded.isNotEmpty()) _conversations.value = loaded
                if (loadedContacts.isNotEmpty()) _contacts.value = loadedContacts
                if (loadedUnread.isNotEmpty()) _unreadCounts.value = loadedUnread
            }

            val walletId = result["wallet_id"] as? String ?: ""
            _identity.value = Identity(
                handle = result["handle"] as? String ?: "",
                displayName = Helpers.fixBvmUtf8(result["display_name"] as? String ?: ""),
                walletId = Helpers.normalizeWalletId(walletId) ?: walletId,
                registered = true,
                registeredHeight = (result["registered_height"] as? Number)?.toLong() ?: 0,
            )

            // Set contract start timestamp on first run
            if (contractStartTs == 0L) {
                contractStartTs = System.currentTimeMillis() / 1000
                ProtocolStorage.setContractStartTs(contractStartTs)
            }

            persistState()

            // Verify SBBS address belongs to this wallet (handles restored wallets)
            verifySbbsAddress()

            // Ensure message poll is running
            // (startPolling is idempotent — cancels old job before starting new)
            pollingJob?.let { /* already running */ } ?: run {
                // If not started yet, we need a scope — but polling should already
                // be started from init(). This is a safety net.
                Log.d(TAG, "Identity confirmed: @${_identity.value?.handle}")
            }
        } else {
            _identity.value = null
        }
    }

    /**
     * Verify that the registered SBBS address belongs to this wallet instance.
     * After restore, the on-chain address points to the old device.
     * If is_mine=false, set sbbsNeedsUpdate flag so the UI can show a prompt.
     */
    private fun verifySbbsAddress() {
        val identity = _identity.value ?: return
        if (!identity.registered || identity.walletId.isEmpty()) return

        SbbsMessaging.validateAddress(identity.walletId) { isValid, isMine ->
            if (isValid && !isMine) {
                Log.w(TAG, "SBBS address not mine — restored wallet detected, needs re-registration")
                _sbbsNeedsUpdate.value = true
            } else if (isMine) {
                _sbbsNeedsUpdate.value = false
            }
        }
    }

    /**
     * Re-register SBBS address for a restored wallet.
     * Creates a fresh address and updates the on-chain contract.
     * Called from the UI when user taps "Update Address".
     */
    fun reRegisterSbbsAddress(newDisplayName: String? = null) {
        Log.d(TAG, "reRegisterSbbsAddress called, displayName=${newDisplayName}")
        val identity = _identity.value
        if (identity == null) {
            Log.w(TAG, "reRegisterSbbsAddress: no identity")
            return
        }
        _sbbsUpdating.value = true

        // Use raw callWalletApi — WalletApi.call("create_address") returns empty
        // because the address comes via separate callback, not JSON-RPC response.
        val sbbsReqId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val payload = org.json.JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", sbbsReqId)
            put("method", "create_address")
            put("params", org.json.JSONObject().apply {
                put("expiration", "never")
                put("comment", "PriviMe restored")
            })
        }.toString()

        // Listen for the address response, then update contract
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            WalletEventBus.apiResult.collect { json ->
                try {
                    val parsed = org.json.JSONObject(json)
                    val id = parsed.optInt("id", -1)
                    if (id != sbbsReqId) return@collect

                    val result = parsed.opt("result")
                    val newAddr = when (result) {
                        is String -> result
                        is org.json.JSONObject -> result.optString("address", "")
                            .ifEmpty { result.optString("token", "") }
                        else -> null
                    }

                    if (newAddr == null || newAddr.length < 10) {
                        Log.w(TAG, "Failed to create new SBBS address")
                        _sbbsUpdating.value = false
                        return@collect
                    }

                    Log.d(TAG, "New SBBS address created: len=${newAddr.length} addr=${newAddr.take(40)}...")

                    // The contract expects wallet_id as 34 bytes (68 hex chars).
                    // create_address may return a long token or a short walletID.
                    // Normalize: if it's hex and 62-68 chars, pad to 68.
                    // If it's longer, it's a token — extract the walletID portion (first 68 hex chars).
                    val normalizedAddr = Helpers.normalizeWalletId(newAddr)
                        ?: if (newAddr.length >= 68 && newAddr.matches(Regex("^[0-9a-fA-F]+"))) {
                            newAddr.take(68).lowercase()
                        } else {
                            Log.w(TAG, "Cannot normalize SBBS address: $newAddr")
                            _sbbsUpdating.value = false
                            return@collect
                        }
                    Log.d(TAG, "Normalized wallet ID: ${normalizedAddr.take(20)}... (len=${normalizedAddr.length})")

                    // Update contract with new address + optional display name (triggers TX approval dialog)
                    val txParams = mutableMapOf<String, Any>(
                        "wallet_id" to normalizedAddr,
                    )
                    // Include display name — use provided name, fall back to current
                    val displayNameToSend = newDisplayName ?: identity.displayName
                    if (displayNameToSend.isNotEmpty()) {
                        txParams["display_name"] = displayNameToSend
                    }
                    ShaderInvoker.tx("user", "update_profile", txParams) { txResult ->
                        _sbbsUpdating.value = false
                        if (!txResult.containsKey("error")) {
                            Log.d(TAG, "SBBS address updated on-chain")
                            val updatedName = newDisplayName ?: identity.displayName
                            _identity.value = identity.copy(walletId = newAddr, displayName = updatedName)
                            _sbbsNeedsUpdate.value = false
                        } else {
                            Log.w(TAG, "Failed to update SBBS: ${txResult["error"]}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing create_address response: ${e.message}")
                }
            }
        }

        // Send the create_address request
        Log.d(TAG, "Sending create_address request (id=$sbbsReqId)")
        com.privimemobile.wallet.WalletManager.callWalletApi(payload)
    }

    /**
     * Check our own PriviMe identity on-chain.
     * Ports checkIdentityChange() from RN startup.ts.
     *
     * IMPORTANT: If already registered with a confirmed height, skip re-check
     * to avoid queuing an invoke_contract that would block user-initiated calls.
     */
    fun checkIdentity() {
        val current = _identity.value
        // Skip if already registered with a confirmed height
        if (current != null && current.registered && current.registeredHeight > 0) return

        ShaderInvoker.invoke("user", "my_handle") { result ->
            if (!result.containsKey("error")) {
                val registered = (result["registered"] as? Number)?.toInt() ?: 0
                if (registered == 1) {
                    val newHandle = (result["handle"] as? String ?: "").lowercase()
                    // Apply if: no handle yet (new registration) OR handle changed
                    if (current == null || !current.registered ||
                        current.handle.lowercase() != newHandle) {
                        applyMyHandle(result)
                    }
                } else if (current != null && current.registered) {
                    // Was registered but no longer — clear identity
                    _identity.value = null
                }
            }

            // Fetch registration fee after identity check
            ShaderInvoker.invoke("manager", "view_pool") { r ->
                @Suppress("UNCHECKED_CAST")
                val pool = r["pool"] as? Map<String, Any?>
                val fee = pool?.get("registration_fee")
                if (fee != null) {
                    val feeGroth = when (fee) {
                        is Number -> fee.toLong()
                        else -> 0L
                    }
                    if (feeGroth > 0) {
                        _registrationFee.value = feeGroth.toDouble() / Config.GROTH_PER_BEAM
                    }
                }
            }
        }
    }

    /**
     * Load messages from SBBS inbox.
     * Ports loadMessages() from RN startup.ts (lines 158-217).
     *
     * 1. Calls readMessages with callback
     * 2. Extracts message array from result
     * 3. Converts to JSON string for MessageProcessor.processMessages()
     * 4. Updates conversations, contacts, unreadCounts from result
     * 5. Triggers async contact resolution for pending resolves
     * 6. Sends read receipts if active chat has new messages
     * 7. Persists state
     */
    fun loadMessages(fetchAll: Boolean = false) {
        SbbsMessaging.readMessages(fetchAll) { result ->
            if (result.containsKey("error")) {
                Log.w(TAG, "read_messages error: ${result["error"]}")
                return@readMessages
            }

            // Extract the raw message array from the result
            // WalletApi parses JSON into Map — we need to reconstruct a JSON array string
            // for MessageProcessor which expects a JSON string.
            val rawMessages = extractMessageArray(result)
            if (rawMessages == null || rawMessages == "[]") return@readMessages

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

            val processResult = MessageProcessor.processMessages(rawMessages, state)

            _conversations.value = processResult.conversations
            _contacts.value = processResult.contacts
            _unreadCounts.value = processResult.unreadCounts

            // Trigger async contact resolution for newly discovered contacts
            for (resolve in processResult.pendingResolves) {
                if (resolve.type == "handle") {
                    val mutableContacts = processResult.contacts.toMutableMap()
                    val mutableConvs = processResult.conversations
                        .mapValues { it.value.toMutableList() }.toMutableMap()
                    val mutableUnread = processResult.unreadCounts.toMutableMap()

                    ContactResolver.resolveHandleIntoContact(
                        handle = resolve.value,
                        convKey = resolve.key,
                        contacts = mutableContacts,
                        conversations = mutableConvs,
                        deletedConvs = deletedConvs,
                        unreadCounts = mutableUnread,
                    ) { updatedContacts, updatedConvs ->
                        _contacts.value = updatedContacts
                        _conversations.value = updatedConvs
                        persistState()
                    }
                } else {
                    // walletid resolve
                    val mutableContacts = processResult.contacts.toMutableMap()
                    ContactResolver.resolveWalletIdToContact(
                        walletId = resolve.value,
                        contacts = mutableContacts,
                    ) { key, contact ->
                        _contacts.value = _contacts.value.toMutableMap().also {
                            it[key] = contact
                        }
                        persistState()
                    }
                }
            }

            // Send read receipts if active chat has new messages
            val currentActiveChat = activeChat
            if (currentActiveChat != null && processResult.unreadCounts.containsKey(currentActiveChat)) {
                clearUnread(currentActiveChat)

                val mutableConvs = processResult.conversations
                    .mapValues { it.value.toMutableList() }.toMutableMap()

                SbbsMessaging.sendReadReceipts(
                    convKey = currentActiveChat,
                    conversations = mutableConvs,
                    contacts = processResult.contacts,
                    myHandle = _identity.value,
                    myWalletId = _identity.value?.walletId,
                    onSave = { persistState() },
                )
            }

            persistState()
        }
    }

    /**
     * Extract message array from read_messages API result.
     * The result may have messages at result["messages"] (as a List) or be the array itself.
     * Returns a JSON array string suitable for MessageProcessor.
     */
    @Suppress("UNCHECKED_CAST")
    private fun extractMessageArray(result: Map<String, Any?>): String? {
        // Try result["messages"] first (standard read_messages response)
        val messages = result["messages"]
        if (messages is List<*>) {
            return listToJsonArray(messages).toString()
        }

        // If the result itself is structured as an array-like response,
        // try to reconstruct from the map.
        // WalletApi.handleApiResult returns jsonToMap(result) which is a Map,
        // but read_messages returns { messages: [...] } from the wallet API.
        // If "messages" key is absent, there are no messages.
        return null
    }

    /** Convert a parsed List<*> (from jsonToMap/jsonArrayToList) back to JSONArray. */
    private fun listToJsonArray(list: List<*>): JSONArray {
        val arr = JSONArray()
        for (item in list) {
            when (item) {
                is Map<*, *> -> arr.put(mapToJsonObject(item))
                is List<*> -> arr.put(listToJsonArray(item))
                null -> arr.put(JSONObject.NULL)
                else -> arr.put(item)
            }
        }
        return arr
    }

    /** Convert a parsed Map back to JSONObject. */
    private fun mapToJsonObject(map: Map<*, *>): JSONObject {
        val obj = JSONObject()
        for ((k, v) in map) {
            val key = k?.toString() ?: continue
            when (v) {
                is Map<*, *> -> obj.put(key, mapToJsonObject(v))
                is List<*> -> obj.put(key, listToJsonArray(v))
                null -> obj.put(key, JSONObject.NULL)
                else -> obj.put(key, v)
            }
        }
        return obj
    }

    /** Process raw SBBS messages and update state (for direct JSON input). */
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
            if (resolve.type == "handle") {
                val mutableContacts = result.contacts.toMutableMap()
                val mutableConvs = result.conversations
                    .mapValues { it.value.toMutableList() }.toMutableMap()
                val mutableUnread = result.unreadCounts.toMutableMap()

                ContactResolver.resolveHandleIntoContact(
                    handle = resolve.value,
                    convKey = resolve.key,
                    contacts = mutableContacts,
                    conversations = mutableConvs,
                    deletedConvs = deletedConvs,
                    unreadCounts = mutableUnread,
                ) { updatedContacts, updatedConvs ->
                    _contacts.value = updatedContacts
                    _conversations.value = updatedConvs
                    persistState()
                }
            } else {
                val mutableContacts = result.contacts.toMutableMap()
                ContactResolver.resolveWalletIdToContact(
                    walletId = resolve.value,
                    contacts = mutableContacts,
                ) { key, contact ->
                    _contacts.value = _contacts.value.toMutableMap().also {
                        it[key] = contact
                    }
                    persistState()
                }
            }
        }

        persistState()
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

    /**
     * Update conversations from outside (e.g., FileSharing optimistic update).
     * Also persists the updated state.
     */
    fun updateConversations(conversations: Map<String, List<ChatMessage>>) {
        _conversations.value = conversations
        persistState()
    }

    /**
     * Update contacts from outside (e.g., NewChatScreen adding a resolved contact).
     * Also persists the updated state.
     */
    fun updateContacts(contacts: Map<String, Contact>) {
        _contacts.value = contacts
        persistState()
    }

    /** Delete a conversation (tombstone it). */
    fun deleteConversation(convKey: String) {
        val msgs = _conversations.value[convKey]
        val lastTs = msgs?.maxOfOrNull { it.timestamp } ?: (System.currentTimeMillis() / 1000)
        deletedConvs[convKey] = lastTs

        val updatedConvs = _conversations.value.toMutableMap()
        updatedConvs.remove(convKey)
        _conversations.value = updatedConvs

        val updatedUnread = _unreadCounts.value.toMutableMap()
        updatedUnread.remove(convKey)
        _unreadCounts.value = updatedUnread

        persistState()
    }

    /** Block a user by handle. */
    fun blockUser(handle: String) {
        blockedUsers.add(handle.lowercase())
        ProtocolStorage.saveBlockedUsers(blockedUsers)
    }

    /** Unblock a user. */
    fun unblockUser(handle: String) {
        blockedUsers.remove(handle.lowercase())
        ProtocolStorage.saveBlockedUsers(blockedUsers)
    }

    /** Get current blocked users. */
    fun getBlockedUsers(): Set<String> = blockedUsers.toSet()

    /** Clear unread count for a conversation. */
    fun clearUnread(convKey: String) {
        if (_unreadCounts.value.containsKey(convKey)) {
            _unreadCounts.value = _unreadCounts.value.toMutableMap().also {
                it.remove(convKey)
            }
        }
    }

    /** Persist all protocol state to storage. */
    private fun persistState() {
        ProtocolStorage.saveAll(
            _conversations.value,
            _contacts.value,
            deletedConvs,
            _unreadCounts.value,
        )
    }

    private fun startPolling(scope: CoroutineScope) {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                if (_identity.value != null) {
                    loadMessages(fetchAll = false)
                }
                delay(Config.MSG_REFRESH_MS)
            }
        }
    }
}
