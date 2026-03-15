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

    fun getDbPath(): String {
        val ctx = appContext ?: throw IllegalStateException("WalletManager not initialized")
        return "${ctx.filesDir.absolutePath}/wallet.db"
    }

    fun isWalletCreated(): Boolean {
        return Api.isWalletInitialized(getDbPath())
    }

    fun createWallet(
        seed: String,
        password: String,
        nodeAddr: String,
    ): Boolean {
        Log.d(TAG, "Creating wallet, node=$nodeAddr")
        val result = Api.createWallet(APP_VERSION, nodeAddr, getDbPath(), password, seed)
        if (result != null) {
            walletInstance = result
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
        val result = Api.createWallet(APP_VERSION, nodeAddr, getDbPath(), password, seed, restore = true)
        if (result != null) {
            walletInstance = result
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
