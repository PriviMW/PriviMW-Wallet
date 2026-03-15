package com.privimemobile.protocol

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted key-value storage for sensitive wallet data.
 *
 * Uses Android's EncryptedSharedPreferences (AES-256-GCM).
 * Stores: wallet password, node address, biometric settings.
 */
object SecureStorage {
    private const val TAG = "SecureStorage"
    private const val PREFS_NAME = "privimw_secure_prefs"

    // Keys
    const val KEY_WALLET_PASSWORD = "wallet_password"
    const val KEY_NODE_ADDRESS = "node_address"
    const val KEY_NODE_MODE = "node_mode" // "random" or "own"
    const val KEY_HAS_WALLET = "has_wallet"
    const val KEY_ASK_PASSWORD_ON_SEND = "ask_password_on_send"
    const val KEY_FINGERPRINT_ENABLED = "fingerprint_enabled"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            prefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init encrypted prefs: ${e.message}")
        }
    }

    fun getString(key: String, default: String? = null): String? = prefs?.getString(key, default)
    fun getBoolean(key: String, default: Boolean = false): Boolean = prefs?.getBoolean(key, default) ?: default

    fun putString(key: String, value: String) {
        prefs?.edit()?.putString(key, value)?.apply()
    }

    fun putBoolean(key: String, value: Boolean) {
        prefs?.edit()?.putBoolean(key, value)?.apply()
    }

    fun remove(key: String) {
        prefs?.edit()?.remove(key)?.apply()
    }

    // Convenience methods
    fun storeWalletPassword(password: String) = putString(KEY_WALLET_PASSWORD, password)
    fun getWalletPassword(): String? = getString(KEY_WALLET_PASSWORD)
    fun storeNodeAddress(address: String) = putString(KEY_NODE_ADDRESS, address)
    fun getNodeAddress(): String = getString(KEY_NODE_ADDRESS) ?: Config.DEFAULT_NODE
    fun setHasWallet(value: Boolean) = putBoolean(KEY_HAS_WALLET, value)
    fun hasWallet(): Boolean = getBoolean(KEY_HAS_WALLET)
}
