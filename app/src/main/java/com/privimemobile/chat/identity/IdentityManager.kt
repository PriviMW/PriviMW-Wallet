package com.privimemobile.chat.identity

import android.util.Log
import com.privimemobile.chat.db.ChatDatabase
import com.privimemobile.chat.db.entities.ChatStateEntity
import com.privimemobile.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * IdentityManager — handles PriviMe registration, profile updates, SBBS verification.
 *
 * Ports identity-related logic from ProtocolStartup.kt + ContactResolver.kt.
 *
 * Key behaviors:
 * - Check `my_handle` on startup + on `ev_system_state`
 * - Throttle: skip re-check if registered_height > 0
 * - Set contractStartTs = now() on first registration (never reset)
 * - Detect restored wallet (SBBS address not mine) → prompt re-registration
 */
class IdentityManager(
    private val db: ChatDatabase,
    private val scope: CoroutineScope,
) {
    private val TAG = "IdentityManager"

    // SBBS needs update (restored wallet detected)
    private val _sbbsNeedsUpdate = MutableStateFlow(false)
    val sbbsNeedsUpdate: StateFlow<Boolean> = _sbbsNeedsUpdate.asStateFlow()

    // Registration fee (from contract)
    private val _registrationFee = MutableStateFlow(0L)
    val registrationFee: StateFlow<Long> = _registrationFee.asStateFlow()

    // Currently refreshing identity (prevent concurrent calls)
    private var refreshing = false

    /**
     * Refresh identity from contract.
     * Called on startup and on ev_system_state events.
     */
    suspend fun refreshIdentity() {
        if (refreshing) return
        refreshing = true
        try {
            // Check current state
            val state = db.chatStateDao().get() ?: db.chatStateDao().ensureInitialized()
            if (state.myHandle != null && state.myRegisteredHeight > 0) {
                // Already registered — verify SBBS address still belongs to us (detects restored wallet)
                if (state.myWalletId?.isNotEmpty() == true) {
                    verifySbbsAddress(state.myWalletId)
                }
                refreshRegistrationFee()
                return
            }

            // Query contract for our handle
            val result = ShaderInvoker.invokeAsync("user", "my_handle")
            if (result.containsKey("error")) {
                Log.w(TAG, "my_handle query error: ${result["error"]}")
                return
            }

            val handle = result["handle"] as? String
            if (handle.isNullOrEmpty()) {
                Log.d(TAG, "Not registered")
                refreshRegistrationFee()
                return
            }

            val walletId = Helpers.normalizeWalletId(result["wallet_id"] as? String ?: "") ?: ""
            val displayName = Helpers.fixBvmUtf8(result["display_name"] as? String)
            val height = (result["registered_height"] as? Number)?.toLong() ?: 0
            Log.d(TAG, "Identity: @$handle, height=$height")

            // Update DB
            db.chatStateDao().updateIdentity(handle, walletId, displayName, height)

            // Set contractStartTs on first registration (one-time)
            db.chatStateDao().setContractStartTs(System.currentTimeMillis() / 1000)

            // Verify SBBS address ownership
            if (walletId.isNotEmpty()) {
                verifySbbsAddress(walletId)
            }

            refreshRegistrationFee()
        } catch (e: Exception) {
            Log.e(TAG, "refreshIdentity error: ${e.message}")
        } finally {
            refreshing = false
        }
    }

    /** Called on ev_system_state — only re-check if not yet confirmed. */
    fun onSystemState() {
        scope.launch {
            val state = db.chatStateDao().get()
            // Skip if already registered with confirmed height
            if (state?.myHandle != null && state.myRegisteredHeight > 0) return@launch
            refreshIdentity()
        }
    }

    /** Refresh registration fee from contract. */
    private suspend fun refreshRegistrationFee() {
        try {
            val result = ShaderInvoker.invokeAsync("manager", "view_pool")
            val pool = result["pool"] as? Map<*, *>
            val fee = (pool?.get("registration_fee") as? Number)?.toLong() ?: 0
            _registrationFee.value = fee
            db.chatStateDao().updateRegistrationFee(fee)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get registration fee: ${e.message}")
        }
    }

    /**
     * Verify that the SBBS address on-chain belongs to this wallet.
     * If not (restored wallet), set sbbsNeedsUpdate = true.
     */
    private suspend fun verifySbbsAddress(walletId: String) {
        try {
            val result = WalletApi.callAsync("validate_address", mapOf("address" to walletId))
            val isMine = result["is_mine"] as? Boolean ?: false
            if (!isMine) {
                Log.w(TAG, "SBBS address is not mine — restored wallet detected")
                _sbbsNeedsUpdate.value = true
            } else {
                _sbbsNeedsUpdate.value = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "SBBS verify error: ${e.message}")
        }
    }

    /**
     * Register a new handle.
     *
     * @param handle The @handle (lowercase, 3-32 chars, alphanumeric + underscore)
     * @param displayName Optional display name (max 40 chars)
     * @param sbbsAddress The SBBS wallet_id to register (68 hex chars)
     * @param onTxReady Called when TX is ready for user approval (NativeTxApprovalDialog handles this)
     * @param onResult Called with success/error
     */
    fun registerHandle(
        handle: String,
        displayName: String?,
        sbbsAddress: String,
        onTxReady: (() -> Unit)? = null,
        onResult: ((success: Boolean, error: String?) -> Unit)? = null,
    ) {
        val extra = mutableMapOf<String, Any?>(
            "handle" to handle,
            "wallet_id" to sbbsAddress,
        )
        if (!displayName.isNullOrBlank()) {
            extra["display_name"] = displayName
        }

        ShaderInvoker.tx("user", "register_handle", extra,
            onReady = onTxReady,
            callback = { result ->
                if (result.containsKey("error")) {
                    val error = result["error"]?.toString() ?: "Unknown error"
                    Log.e(TAG, "Register failed: $error")
                    onResult?.invoke(false, error)
                } else {
                    val txId = result["txid"]?.toString() ?: result["txId"]?.toString()
                    Log.d(TAG, "Register TX submitted for @$handle txId=$txId")
                    // Track TX — identity will refresh on confirmation
                    if (txId != null) {
                        scope.launch {
                            com.privimemobile.chat.ChatService.pendingTxs.trackTx(
                                txId,
                                com.privimemobile.chat.db.entities.PendingTxEntity.ACTION_REGISTER_HANDLE,
                                handle
                            )
                        }
                    }
                    // Optimistic update — will be confirmed on next refreshIdentity
                    scope.launch {
                        db.chatStateDao().updateIdentity(handle, sbbsAddress, displayName, 0)
                        db.chatStateDao().setContractStartTs(System.currentTimeMillis() / 1000)
                    }
                    onResult?.invoke(true, null)
                }
            }
        )
    }

    /**
     * Check if a handle is available for registration.
     */
    fun checkAvailability(handle: String, callback: (available: Boolean?, error: String?) -> Unit) {
        ShaderInvoker.invoke("user", "resolve_handle", mapOf("handle" to handle)) { result ->
            val error = result["error"]?.toString() ?: ""
            if (error.contains("handle not found", ignoreCase = true)) {
                // "handle not found" means it's available
                callback(true, null)
                return@invoke
            }
            if (error.isNotEmpty()) {
                callback(null, error)
                return@invoke
            }
            // Handle exists — check if it's registered
            val registered = (result["registered"] as? Number)?.toInt() ?: 0
            callback(registered == 0, null)
        }
    }

    /**
     * Update display name on-chain.
     */
    fun updateDisplayName(
        newDisplayName: String,
        onResult: ((success: Boolean, error: String?) -> Unit)? = null,
    ) {
        scope.launch {
            val state = db.chatStateDao().get() ?: return@launch
            if (state.myHandle == null || state.myWalletId == null) return@launch

            ShaderInvoker.tx("user", "update_profile",
                mapOf(
                    "display_name" to newDisplayName,
                    "wallet_id" to state.myWalletId,
                ),
                callback = { result ->
                    if (result.containsKey("error")) {
                        onResult?.invoke(false, result["error"]?.toString())
                    } else {
                        val txId = result["txid"]?.toString() ?: result["txId"]?.toString()
                        if (txId != null) {
                            scope.launch {
                                com.privimemobile.chat.ChatService.pendingTxs.trackTx(
                                    txId,
                                    com.privimemobile.chat.db.entities.PendingTxEntity.ACTION_UPDATE_PROFILE,
                                    state.myHandle ?: ""
                                )
                            }
                        }
                        scope.launch { db.chatStateDao().update(state.copy(myDisplayName = newDisplayName)) }
                        onResult?.invoke(true, null)
                    }
                }
            )
        }
    }

    /**
     * Update messaging address (re-register SBBS).
     * Creates new SBBS address → updates on-chain via update_profile.
     */
    fun updateMessagingAddress(
        newAddress: String,
        displayName: String? = null,
        onResult: ((success: Boolean, error: String?) -> Unit)? = null,
    ) {
        scope.launch {
            val state = db.chatStateDao().get() ?: return@launch
            if (state.myHandle == null) return@launch

            val extra = mutableMapOf<String, Any?>(
                "wallet_id" to newAddress,
            )
            // Keep existing display name unless a new one is provided
            val dn = displayName ?: state.myDisplayName
            if (!dn.isNullOrBlank()) {
                extra["display_name"] = dn
            }

            ShaderInvoker.tx("user", "update_profile", extra,
                callback = { result ->
                    if (result.containsKey("error")) {
                        onResult?.invoke(false, result["error"]?.toString())
                    } else {
                        val txId = result["txid"]?.toString() ?: result["txId"]?.toString()
                        if (txId != null) {
                            scope.launch {
                                com.privimemobile.chat.ChatService.pendingTxs.trackTx(
                                    txId,
                                    com.privimemobile.chat.db.entities.PendingTxEntity.ACTION_UPDATE_PROFILE,
                                    state.myHandle ?: ""
                                )
                            }
                        }
                        scope.launch {
                            db.chatStateDao().update(state.copy(
                                myWalletId = newAddress,
                                myDisplayName = dn,
                            ))
                            _sbbsNeedsUpdate.value = false
                        }
                        onResult?.invoke(true, null)
                    }
                }
            )
        }
    }

    /**
     * Release handle — unregister from contract.
     */
    fun releaseHandle(
        onResult: ((success: Boolean, error: String?) -> Unit)? = null,
    ) {
        ShaderInvoker.tx("user", "release_handle", emptyMap(),
            callback = { result ->
                if (result.containsKey("error")) {
                    onResult?.invoke(false, result["error"]?.toString())
                } else {
                    val txId = result["txid"]?.toString() ?: result["txId"]?.toString()
                    if (txId != null) {
                        scope.launch {
                            com.privimemobile.chat.ChatService.pendingTxs.trackTx(
                                txId, com.privimemobile.chat.db.entities.PendingTxEntity.ACTION_RELEASE_HANDLE, "release"
                            )
                        }
                    }
                    scope.launch { db.chatStateDao().clearIdentity() }
                    onResult?.invoke(true, null)
                }
            }
        )
    }

    /**
     * Set avatar locally (SBBS-only, no contract TX).
     * @param avatarHash 64-char hex SHA-256 hash of compressed avatar image. Empty/null = clear avatar.
     */
    fun setAvatar(
        avatarHash: String?,
        onResult: ((success: Boolean, error: String?) -> Unit)? = null,
    ) {
        scope.launch {
            try {
                db.chatStateDao().updateAvatarHash(if (avatarHash.isNullOrEmpty()) null else avatarHash)
                onResult?.invoke(true, null)
            } catch (e: Exception) {
                onResult?.invoke(false, e.message)
            }
        }
    }

    /** Mark SBBS as updated (after successful re-registration). */
    fun clearSbbsNeedsUpdate() {
        _sbbsNeedsUpdate.value = false
    }
}
