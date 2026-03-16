package com.privimemobile.protocol

import android.util.Log

/**
 * Contact resolution — looks up PriviMe handles on-chain.
 *
 * Fully ports contacts.ts from RN build.
 * Uses the PriviMe shader to resolve handles into wallet IDs.
 *
 * CRITICAL: Uses "resolve_handle" action (not "view_user") to match RN + contract.
 */
object ContactResolver {
    private const val TAG = "ContactResolver"

    /**
     * Resolve a handle to a Contact.
     * Calls the PriviMe contract `resolve_handle` action.
     * Returns null if the handle is not registered.
     */
    fun resolveHandle(handle: String, callback: (Contact?) -> Unit) {
        ShaderInvoker.invoke("user", "resolve_handle", mapOf("handle" to handle)) { result ->
            if (result.containsKey("error") || (result["registered"] as? Number)?.toInt() != 1) {
                // Also check if wallet_id is present (some contract versions use different field)
                val walletId = result["wallet_id"] as? String
                if (walletId != null && walletId.isNotEmpty()) {
                    callback(Contact(
                        handle = result["handle"] as? String ?: handle,
                        displayName = Helpers.fixBvmUtf8(result["display_name"] as? String ?: handle),
                        walletId = Helpers.normalizeWalletId(walletId) ?: walletId,
                    ))
                } else {
                    callback(null)
                }
                return@invoke
            }
            val walletId = Helpers.normalizeWalletId(result["wallet_id"] as? String ?: "") ?: ""
            val displayName = Helpers.fixBvmUtf8(result["display_name"] as? String ?: handle)
            callback(Contact(
                handle = result["handle"] as? String ?: handle,
                displayName = displayName,
                walletId = walletId,
            ))
        }
    }

    /** Suspending version for coroutines. */
    suspend fun resolveHandleAsync(handle: String): Contact? {
        val result = ShaderInvoker.invokeAsync("user", "resolve_handle", mapOf("handle" to handle))
        val walletId = result["wallet_id"] as? String ?: ""
        if (walletId.isEmpty()) return null
        val registered = (result["registered"] as? Number)?.toInt() ?: 0
        if (registered != 1) return null
        return Contact(
            handle = result["handle"] as? String ?: handle,
            displayName = Helpers.fixBvmUtf8(result["display_name"] as? String ?: handle),
            walletId = Helpers.normalizeWalletId(walletId) ?: walletId,
        )
    }

    /**
     * Resolve a raw wallet ID to a contact handle via contract lookup.
     * Ports resolveWalletIdToContact() from RN contacts.ts.
     */
    fun resolveWalletIdToContact(
        walletId: String,
        contacts: MutableMap<String, Contact>,
        onUpdate: (key: String, contact: Contact) -> Unit,
    ) {
        if (contacts[walletId]?.handle?.isNotEmpty() == true) return
        val norm = Helpers.normalizeWalletId(walletId) ?: walletId
        ShaderInvoker.invoke("user", "resolve_walletid", mapOf("walletid" to norm)) { result ->
            val handle = result["handle"] as? String ?: return@invoke
            if (handle.isEmpty()) return@invoke
            val updated = contacts[walletId]?.copy(
                handle = handle,
                displayName = Helpers.fixBvmUtf8(result["display_name"] as? String ?: ""),
            ) ?: Contact(
                handle = handle,
                displayName = Helpers.fixBvmUtf8(result["display_name"] as? String ?: ""),
                walletId = norm,
            )
            onUpdate(walletId, updated)
        }
    }

    /**
     * Resolve @handle -> wallet_id + populate contact entry.
     * Handles conversation migration and tombstone propagation.
     * Ports resolveHandleIntoContact() from RN contacts.ts.
     */
    fun resolveHandleIntoContact(
        handle: String,
        convKey: String,
        contacts: MutableMap<String, Contact>,
        conversations: MutableMap<String, MutableList<ChatMessage>>,
        deletedConvs: MutableMap<String, Long>,
        unreadCounts: MutableMap<String, Int>,
        onUpdate: (contacts: Map<String, Contact>, conversations: Map<String, List<ChatMessage>>) -> Unit,
    ) {
        ShaderInvoker.invoke("user", "resolve_handle", mapOf("handle" to handle)) { result ->
            if (result.containsKey("error") || result["wallet_id"] == null) return@invoke
            val receiveAddr = result["wallet_id"] as? String ?: return@invoke

            // Populate '@handle' contact
            contacts[convKey] = Contact(
                handle = handle,
                walletId = receiveAddr,
                displayName = Helpers.fixBvmUtf8(result["display_name"] as? String ?: ""),
            )

            // Propagate tombstone
            if (deletedConvs.containsKey(convKey) && !deletedConvs.containsKey(receiveAddr)) {
                deletedConvs[receiveAddr] = deletedConvs[convKey]!!
            }

            // Migrate legacy wallet_id-keyed conversations -> '@handle' key
            if (receiveAddr != convKey && conversations.containsKey(receiveAddr)) {
                val existing = conversations.getOrPut(convKey) { mutableListOf() }
                conversations[receiveAddr]?.forEach { msg ->
                    val isDup = existing.any { x ->
                        x.timestamp == msg.timestamp && x.text == msg.text && x.sent == msg.sent
                    }
                    if (!isDup) existing.add(msg)
                }
                existing.sortBy { it.timestamp }
                conversations.remove(receiveAddr)
                unreadCounts.remove(receiveAddr)
            }

            onUpdate(contacts.toMap(), conversations.mapValues { it.value.toList() })
        }
    }

    /** Resolve a handle for new chat / search. Ports resolveHandleToContact(). */
    fun resolveHandleToContact(
        handle: String,
        callback: (error: String?, result: HandleResult?) -> Unit,
    ) {
        ShaderInvoker.invoke("user", "resolve_handle", mapOf("handle" to handle.lowercase())) { result ->
            val walletId = result["wallet_id"] as? String
            if (walletId != null && walletId.isNotEmpty()) {
                callback(null, HandleResult(
                    registered = true,
                    walletId = walletId,
                    handle = handle.lowercase(),
                    displayName = Helpers.fixBvmUtf8(result["display_name"] as? String ?: ""),
                    registeredHeight = (result["registered_height"] as? Number)?.toLong() ?: 0,
                ))
            } else {
                val err = Helpers.extractError(result)
                callback(err, null)
            }
        }
    }

    /** Check our own registration status. */
    fun checkOwnIdentity(callback: (Identity) -> Unit) {
        ShaderInvoker.invoke("user", "view_my_info") { result ->
            val registered = (result["registered"] as? Number)?.toInt() ?: 0
            callback(Identity(
                handle = result["handle"] as? String ?: "",
                displayName = Helpers.fixBvmUtf8(result["display_name"] as? String ?: ""),
                walletId = Helpers.normalizeWalletId(result["wallet_id"] as? String ?: "") ?: "",
                registered = registered == 1,
                registeredHeight = (result["registered_height"] as? Number)?.toLong() ?: 0,
            ))
        }
    }

    /** Register a new PriviMe handle on-chain. */
    fun registerHandle(
        handle: String,
        displayName: String,
        walletId: String,
        callback: (success: Boolean, error: String?) -> Unit,
    ) {
        val normalizedAddr = Helpers.normalizeWalletId(walletId)
        if (normalizedAddr == null) {
            callback(false, "Invalid wallet address")
            return
        }
        Log.d(TAG, "Registering handle: @$handle ($displayName), addr=${normalizedAddr.take(12)}... (len=${normalizedAddr.length})")
        ShaderInvoker.tx(
            "user", "register_handle",
            mapOf(
                "handle" to handle,
                "display_name" to displayName,
                "wallet_id" to normalizedAddr,
            ),
        ) { result ->
            if (result.containsKey("error")) {
                callback(false, Helpers.extractError(result))
            } else {
                callback(true, null)
            }
        }
    }
}
