package com.privimemobile.protocol

import android.util.Log

/**
 * Contact resolution — looks up PriviMe handles on-chain.
 *
 * Ports contacts.ts to Kotlin.
 * Uses the PriviMe shader to resolve handles into wallet IDs.
 */
object ContactResolver {
    private const val TAG = "ContactResolver"

    /**
     * Resolve a handle to a Contact.
     *
     * Calls the PriviMe contract `view_user` action.
     * Returns null if the handle is not registered.
     */
    fun resolveHandle(handle: String, callback: (Contact?) -> Unit) {
        ShaderInvoker.invoke("user", "view_user", mapOf("handle" to handle)) { result ->
            val registered = (result["registered"] as? Number)?.toInt() ?: 0
            if (registered != 1) {
                callback(null)
                return@invoke
            }
            val walletId = Helpers.normalizeWalletId(result["wallet_id"] as? String ?: "")
            val displayName = Helpers.fixBvmUtf8(result["display_name"] as? String ?: handle)
            callback(Contact(
                handle = result["handle"] as? String ?: handle,
                displayName = displayName,
                walletId = walletId,
            ))
        }
    }

    /**
     * Suspending version for coroutines.
     */
    suspend fun resolveHandleAsync(handle: String): Contact? {
        val result = ShaderInvoker.invokeAsync("user", "view_user", mapOf("handle" to handle))
        val registered = (result["registered"] as? Number)?.toInt() ?: 0
        if (registered != 1) return null
        val walletId = Helpers.normalizeWalletId(result["wallet_id"] as? String ?: "")
        val displayName = Helpers.fixBvmUtf8(result["display_name"] as? String ?: handle)
        return Contact(
            handle = result["handle"] as? String ?: handle,
            displayName = displayName,
            walletId = walletId,
        )
    }

    /**
     * Check our own registration status.
     */
    fun checkOwnIdentity(callback: (Identity) -> Unit) {
        ShaderInvoker.invoke("user", "view_my_info") { result ->
            val registered = (result["registered"] as? Number)?.toInt() ?: 0
            callback(Identity(
                handle = result["handle"] as? String ?: "",
                displayName = Helpers.fixBvmUtf8(result["display_name"] as? String ?: ""),
                walletId = Helpers.normalizeWalletId(result["wallet_id"] as? String ?: ""),
                registered = registered == 1,
            ))
        }
    }

    /**
     * Register a new PriviMe handle on-chain.
     */
    fun registerHandle(
        handle: String,
        displayName: String,
        walletId: String,
        callback: (success: Boolean, error: String?) -> Unit,
    ) {
        Log.d(TAG, "Registering handle: @$handle ($displayName)")
        ShaderInvoker.tx(
            "user", "register_user",
            mapOf(
                "handle" to handle,
                "display_name" to displayName,
                "wallet_id" to walletId,
            ),
        ) { result ->
            if (result.containsKey("error")) {
                val err = result["error"]
                val errMsg = when (err) {
                    is Map<*, *> -> err["message"] as? String ?: "Registration failed"
                    is String -> err
                    else -> "Registration failed"
                }
                callback(false, errMsg)
            } else {
                callback(true, null)
            }
        }
    }
}
