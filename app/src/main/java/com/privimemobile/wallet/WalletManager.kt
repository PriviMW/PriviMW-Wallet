package com.privimemobile.wallet

import android.content.Context
import android.util.Log
import com.mw.beam.beamwallet.core.Api
import com.mw.beam.beamwallet.core.entities.Wallet

/**
 * Wallet lifecycle manager — pure Kotlin, no React Native dependencies.
 *
 * Manages: wallet creation, opening, closing, API calls, and node connection.
 * The C++ wallet core is accessed via JNI through [Wallet] and [Api].
 */
object WalletManager {
    private const val TAG = "WalletManager"
    private const val APP_VERSION = "PriviMW 2.0"

    @Volatile
    var walletInstance: Wallet? = null
        private set

    @Volatile
    var isApiReady: Boolean = false
        private set

    @Volatile
    var appContext: Context? = null
        private set

    @Volatile
    var currentActivity: android.app.Activity? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Returns the directory path (NOT file path) — C++ core appends /wallet.db internally. */
    fun getDbPath(): String {
        val ctx = appContext ?: throw IllegalStateException("WalletManager not initialized")
        return ctx.filesDir.absolutePath
    }

    fun isWalletCreated(): Boolean {
        // Check if wallet.db exists in the files directory
        val ctx = appContext ?: return false
        return java.io.File(ctx.filesDir, "wallet.db").exists()
    }

    /** Clean up leftover wallet files — Api.createWallet crashes if wallet.db already exists. */
    private fun cleanWalletFiles() {
        val ctx = appContext ?: return
        var deleted = 0
        ctx.filesDir.listFiles()?.forEach { file ->
            if (file.name.contains("wallet") || file.name.endsWith(".db") ||
                file.name.endsWith(".db-journal") || file.name.endsWith(".db-wal") ||
                file.name.endsWith(".db-shm")) {
                if (file.delete()) deleted++
            }
        }
        if (deleted > 0) Log.d(TAG, "Cleaned $deleted leftover wallet files")
    }

    fun createWallet(
        seed: String,
        password: String,
        nodeAddr: String,
    ): Boolean {
        Log.d(TAG, "Creating wallet, node=$nodeAddr")
        cleanWalletFiles()
        val result = Api.createWallet(APP_VERSION, nodeAddr, getDbPath(), password, seed)
        if (result != null) {
            walletInstance = result
            try { result.launchApp("PriviMe", "") } catch (_: Exception) {}
            Log.d(TAG, "Wallet created successfully")
            return true
        }
        Log.e(TAG, "Wallet creation failed")
        return false
    }

    fun restoreWallet(
        seed: String,
        password: String,
        nodeAddr: String,
    ): Boolean {
        Log.d(TAG, "Restoring wallet, node=$nodeAddr")
        cleanWalletFiles()
        // CRITICAL: restore=false! When restore=true, the JNI layer starts a LOCAL
        // embedded Beam node and ignores the remote nodeAddr entirely — scanning 33M+
        // blocks on a local node takes hours on mobile.
        // Instead: create wallet normally with the seed (connects to remote/own node).
        // With own node + owner key, sync is instant because the node already tracks UTXOs.
        val result = Api.createWallet(APP_VERSION, nodeAddr, getDbPath(), password, seed, restore = false)
        if (result != null) {
            walletInstance = result
            try { result.launchApp("PriviMe", "") } catch (_: Exception) {}
            Log.d(TAG, "Wallet restored successfully")
            return true
        }
        Log.e(TAG, "Wallet restore failed")
        return false
    }

    fun openWallet(
        password: String,
        nodeAddr: String,
    ): Boolean {
        Log.d(TAG, "Opening wallet, node=$nodeAddr")
        val result = Api.openWallet(APP_VERSION, nodeAddr, getDbPath(), password, enableBodyRequests = false)
        if (result != null) {
            walletInstance = result
            try { result.launchApp("PriviMe", "") } catch (_: Exception) {}
            Log.d(TAG, "Wallet opened successfully")
            return true
        }
        Log.e(TAG, "Wallet open failed")
        return false
    }

    fun closeWallet() {
        Api.closeWallet()
        walletInstance = null
        isApiReady = false
        Log.d(TAG, "Wallet closed")
    }

    fun setApiReady(ready: Boolean) {
        isApiReady = ready
        if (ready) Log.d(TAG, "API ready — node connected")
    }

    fun callWalletApi(json: String) {
        walletInstance?.callWalletApi(json)
    }

    fun generateSeed(): List<String> {
        return Api.createMnemonic().toList()
    }

    fun getDictionary(): List<String> {
        return Api.getDictionary().toList()
    }

    fun getDefaultNodes(): List<String> {
        return Api.getDefaultPeers().toList()
    }
}
