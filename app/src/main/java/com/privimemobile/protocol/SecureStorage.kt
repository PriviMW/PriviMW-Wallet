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
    const val KEY_FAILED_ATTEMPTS = "failed_unlock_attempts"
    const val KEY_LOCKOUT_UNTIL = "lockout_until_ts"

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

    // ── Brute-force protection ──
    fun recordFailedAttempt() {
        val attempts = getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        putInt(KEY_FAILED_ATTEMPTS, attempts)
        // Exponential lockout: 1→0s, 3→30s, 5→60s, 7→5min, 10→15min
        val lockoutMs = when {
            attempts >= 10 -> 15 * 60 * 1000L
            attempts >= 7 -> 5 * 60 * 1000L
            attempts >= 5 -> 60 * 1000L
            attempts >= 3 -> 30 * 1000L
            else -> 0L
        }
        if (lockoutMs > 0) putLong(KEY_LOCKOUT_UNTIL, System.currentTimeMillis() + lockoutMs)
    }

    fun clearFailedAttempts() {
        putInt(KEY_FAILED_ATTEMPTS, 0)
        putLong(KEY_LOCKOUT_UNTIL, 0)
    }

    fun getFailedAttempts(): Int = getInt(KEY_FAILED_ATTEMPTS, 0)

    /** Returns remaining lockout seconds, or 0 if not locked out. */
    fun getLockoutRemaining(): Long {
        val until = getLong(KEY_LOCKOUT_UNTIL, 0)
        val remaining = until - System.currentTimeMillis()
        return if (remaining > 0) remaining / 1000 else 0
    }

    fun getString(key: String, default: String? = null): String? = prefs?.getString(key, default)
    fun getBoolean(key: String, default: Boolean = false): Boolean = prefs?.getBoolean(key, default) ?: default
    fun getInt(key: String, default: Int = 0): Int = prefs?.getInt(key, default) ?: default

    fun putString(key: String, value: String) {
        prefs?.edit()?.putString(key, value)?.apply()
    }

    fun putBoolean(key: String, value: Boolean) {
        prefs?.edit()?.putBoolean(key, value)?.apply()
    }

    fun putInt(key: String, value: Int) {
        prefs?.edit()?.putInt(key, value)?.apply()
    }

    fun getLong(key: String, default: Long = 0L): Long = prefs?.getLong(key, default) ?: default
    fun putLong(key: String, value: Long) {
        prefs?.edit()?.putLong(key, value)?.apply()
    }

    fun remove(key: String) {
        prefs?.edit()?.remove(key)?.apply()
    }

    /** Clear all encrypted preferences — used on wallet deletion. commit() for sync write before process kill. */
    fun clearAll() {
        prefs?.edit()?.clear()?.commit()
    }

    // Convenience methods
    fun storeWalletPassword(password: String) = putString(KEY_WALLET_PASSWORD, password)
    fun getWalletPassword(): String? = getString(KEY_WALLET_PASSWORD)
    fun storeNodeAddress(address: String) = putString(KEY_NODE_ADDRESS, address)
    fun getNodeAddress(): String = getString(KEY_NODE_ADDRESS) ?: Config.DEFAULT_NODE
    fun setHasWallet(value: Boolean) = putBoolean(KEY_HAS_WALLET, value)
    fun hasWallet(): Boolean = getBoolean(KEY_HAS_WALLET)
}
