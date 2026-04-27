package com.privimemobile.protocol

import android.content.Context
import android.util.Log
import com.privimemobile.wallet.WalletEventBus
import kotlinx.coroutines.*

/**
 * Protocol orchestration — handles wallet-level initialization and events.
 *
 * Chat/messaging is handled entirely by ChatService (Room + SQLCipher).
 * This class only manages: shader loading, wallet events, node reconnect, wallet data refresh.
 */
object ProtocolStartup {
    private const val TAG = "ProtocolStartup"
    private var nodeRecoveryJob: Job? = null
    private var pollingScope: CoroutineScope? = null
    private var wasDisconnected = false

    /**
     * Initialize — call after wallet opens and API is ready.
     * Loads shaders, subscribes to wallet events, starts node reconnect.
     */
    fun init(context: Context, scope: CoroutineScope) {
        Log.d(TAG, "Initializing protocol...")

        ProtocolStorage.init(context)
        ShaderInvoker.loadShader(context)
        DAppStore.loadShader(context)
        NodeReconnect.start(scope)

        pollingScope = scope

        // Wire wallet events
        WalletApi.onSystemStateChanged = {
            refreshWalletData()
            if (com.privimemobile.chat.ChatService.initialized.value) {
                com.privimemobile.chat.ChatService.identity.onSystemState()
            }
        }
        WalletApi.onTxsChanged = {
            refreshWalletData()
            if (com.privimemobile.chat.ChatService.initialized.value) {
                com.privimemobile.chat.ChatService.sbbs.onTxsChanged()
                com.privimemobile.chat.ChatService.pendingTxs.onTxsChanged()
            }
        }

        // Node reconnection recovery
        nodeRecoveryJob = scope.launch {
            WalletEventBus.nodeConnection.collect { event ->
                if (!event.connected) {
                    wasDisconnected = true
                } else if (wasDisconnected) {
                    wasDisconnected = false
                    Log.d(TAG, "Node reconnected — re-syncing")
                    WalletApi.resubscribeEvents()
                    delay(3000)
                    refreshWalletData()
                }
            }
        }

        refreshWalletData()

        // Fetch BEAM price from CoinGecko (repeat every 10 min)
        scope.launch {
            while (true) {
                try {
                    val price = withContext(Dispatchers.IO) {
                        val url = java.net.URL("https://api.coingecko.com/api/v3/simple/price?ids=beam&vs_currencies=usd,btc,eth")
                        val conn = url.openConnection() as java.net.HttpURLConnection
                        conn.connectTimeout = 10_000
                        conn.readTimeout = 10_000
                        try {
                            val json = conn.inputStream.bufferedReader().readText()
                            org.json.JSONObject(json).optJSONObject("beam")
                        } finally {
                            conn.disconnect()
                        }
                    }
                    if (price != null) {
                        val rates = mutableMapOf<String, Double>()
                        if (price.has("usd")) rates["beam_usd"] = price.getDouble("usd")
                        if (price.has("btc")) rates["beam_btc"] = price.getDouble("btc")
                        if (price.has("eth")) rates["beam_eth"] = price.getDouble("eth")
                        WalletEventBus.emitExchangeRates(rates)
                        Log.d(TAG, "Exchange rates: $rates")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Exchange rate fetch failed: ${e.message}")
                }
                delay(10 * 60 * 1000L) // 10 minutes
            }
        }
    }

    fun shutdown() {
        nodeRecoveryJob?.cancel()
        nodeRecoveryJob = null
        NodeReconnect.stop()
        WalletApi.onSystemStateChanged = null
        WalletApi.onTxsChanged = null
    }

    fun onForegroundRecovery(newScope: CoroutineScope? = null) {
        Log.d(TAG, "Foreground recovery")
        if (newScope != null) {
            pollingScope = newScope
            // Restart NodeReconnect jobs — they died with the old Activity's scope.
            // start() detects cancelled jobs and resets them; is a no-op if already running.
            NodeReconnect.start(newScope)
        }
        NodeReconnect.onForegroundRecovery()
        refreshWalletData()
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
}
