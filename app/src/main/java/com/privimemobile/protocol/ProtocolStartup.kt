package com.privimemobile.protocol

import android.content.Context
import android.util.Log
import com.privimemobile.wallet.PortfolioSnapshotStore
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
    private const val KEY_CACHED_RATES = SecureStorage.KEY_CACHED_RATES
    private const val KEY_CACHED_RATES_TS = SecureStorage.KEY_CACHED_RATES_TS
    private const val RATE_CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000L // 24 hours

    fun init(context: Context, scope: CoroutineScope) {
        Log.d(TAG, "Initializing protocol...")

        ProtocolStorage.init(context)
        ShaderInvoker.loadShader(context)
        DAppStore.loadShader(context)
        NodeReconnect.start(scope)

        pollingScope = scope

        // Restore cached exchange rates so UI shows fiat immediately
        restoreCachedRates()
        maybeSaveSnapshot()

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
                    val vsCurrencies = "usd,eur,gbp,sgd,btc,eth,jpy,cad,aud,cny,krw,inr,brl,chf,hkd,nzd,mxn,rub,sek,nok,dkk,pln,thb,idr,php,vnd,try,aed,sar,zar,ars,clp,twd"
                    val price = withContext(Dispatchers.IO) {
                        val url = java.net.URL("https://api.coingecko.com/api/v3/simple/price?ids=beam&vs_currencies=$vsCurrencies&include_24hr_change=true")
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
                        val currencies = vsCurrencies.split(",")
                        for (c in currencies) {
                            if (price.has(c)) rates["beam_$c"] = price.getDouble(c)
                            val changeKey = "${c}_24h_change"
                            if (price.has(changeKey)) rates["beam_${c}_change"] = price.getDouble(changeKey)
                        }
                        WalletEventBus.emitExchangeRates(rates)
                        cacheRates(rates)
                        maybeSaveSnapshot()
                        Log.d(TAG, "Exchange rates: ${rates.filter { !it.key.endsWith("_change") }}")
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
        maybeSaveSnapshot()
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

    /** Restore cached exchange rates if they are not stale (< 24h old). */
    private fun restoreCachedRates() {
        try {
            val ts = SecureStorage.getLong(KEY_CACHED_RATES_TS, 0)
            if (ts == 0L || System.currentTimeMillis() - ts > RATE_CACHE_MAX_AGE_MS) return
            val json = SecureStorage.getString(KEY_CACHED_RATES) ?: return
            val obj = org.json.JSONObject(json)
            val rates = mutableMapOf<String, Double>()
            obj.keys().forEach { key ->
                rates[key] = obj.getDouble(key)
            }
            if (rates.isNotEmpty()) {
                com.privimemobile.wallet.WalletEventBus.emitExchangeRates(rates)
                Log.d(TAG, "Restored ${rates.size} cached rates")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to restore cached rates: ${e.message}")
        }
    }

    /** Save portfolio snapshot using current BEAM balance + rates, if available. */
    private fun maybeSaveSnapshot() {
        try {
            val rates = WalletEventBus.exchangeRates.value
            if (rates.isEmpty()) return
            val beam = WalletEventBus.beamStatus.value
            val totalGroth = beam.available + beam.shielded
            if (totalGroth > 0) {
                PortfolioSnapshotStore.saveSnapshot(totalGroth, rates)
            }
        } catch (_: Exception) {}
    }

    /** Persist exchange rates to SecureStorage. */
    private fun cacheRates(rates: Map<String, Double>) {
        try {
            val obj = org.json.JSONObject()
            for ((key, value) in rates) {
                obj.put(key, value)
            }
            SecureStorage.putString(KEY_CACHED_RATES, obj.toString())
            SecureStorage.putLong(KEY_CACHED_RATES_TS, System.currentTimeMillis())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache rates: ${e.message}")
        }
    }
}
