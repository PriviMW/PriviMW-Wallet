package com.privimemobile.protocol

import android.util.Log
import com.mw.beam.beamwallet.core.Api
import com.privimemobile.wallet.WalletEventBus
import com.privimemobile.wallet.WalletManager
import com.privimemobile.wallet.NodeConnectionEvent
import kotlinx.coroutines.*

/**
 * Persistent node connection manager — ensures the wallet stays connected.
 *
 * For OWN NODE: always retries the user's node (never falls back to random pool).
 * For RANDOM NODE: cycles through Beam mainnet peers on failure.
 * Includes periodic health check to detect silent disconnects.
 */
object NodeReconnect {
    private const val TAG = "NodeReconnect"
    private const val HEALTH_CHECK_INTERVAL = 30_000L // 30s
    private const val RECONNECT_BASE_DELAY = 3_000L   // 3s initial
    private const val RECONNECT_MAX_DELAY = 60_000L   // 60s max
    private const val DATA_STALE_THRESHOLD = 90_000L  // 90s without data = stale

    private var nodePool = Config.MAINNET_NODES.toMutableList()
    private var currentNodeIdx = 0
    private var failCount = 0
    private var reconnectDelay = RECONNECT_BASE_DELAY

    @Volatile
    var lastDataTs = 0L
        private set
    @Volatile
    private var connected = false

    private var listenerJob: Job? = null
    private var walletStatusJob: Job? = null
    private var healthCheckJob: Job? = null
    private var reconnectJob: Job? = null
    private var scope: CoroutineScope? = null

    // Node settings
    private var savedNodeMode = "random"
    private var savedOwnNodeAddr: String? = null

    fun onDataReceived() {
        lastDataTs = System.currentTimeMillis()
    }

    fun start(coroutineScope: CoroutineScope) {
        if (listenerJob != null) return
        scope = coroutineScope

        savedNodeMode = SecureStorage.getString("node_mode") ?: "random"
        savedOwnNodeAddr = SecureStorage.getString("custom_node")
            ?: SecureStorage.getString("own_node_address")

        try {
            val peers = Api.getDefaultPeers()
            if (peers.isNotEmpty()) nodePool = peers.toMutableList()
        } catch (_: Exception) {}

        // Add US nodes if not present
        Config.MAINNET_NODES.forEach { n ->
            if (n !in nodePool) nodePool.add(n)
        }

        listenerJob = coroutineScope.launch {
            WalletEventBus.nodeConnection.collect { event ->
                handleConnectionEvent(event)
            }
        }

        walletStatusJob = coroutineScope.launch {
            WalletEventBus.walletStatus.collect { status ->
                if (status.height > 0) {
                    lastDataTs = System.currentTimeMillis()
                    connected = true
                }
            }
        }

        // Periodic health check — detect silent disconnects
        healthCheckJob = coroutineScope.launch {
            delay(HEALTH_CHECK_INTERVAL) // initial wait
            while (isActive) {
                checkHealth()
                delay(HEALTH_CHECK_INTERVAL)
            }
        }
    }

    fun stop() {
        listenerJob?.cancel(); listenerJob = null
        walletStatusJob?.cancel(); walletStatusJob = null
        healthCheckJob?.cancel(); healthCheckJob = null
        reconnectJob?.cancel(); reconnectJob = null
        scope = null
    }

    /** Called on app foreground recovery — force immediate reconnect if disconnected. */
    fun onForegroundRecovery() {
        if (!connected) {
            Log.d(TAG, "Foreground recovery — forcing immediate reconnect")
            reconnectJob?.cancel()
            reconnectJob = null
            reconnectDelay = RECONNECT_BASE_DELAY
            failCount = 0
            tryReconnect()
        }
    }

    fun onConnectionFailed(error: Int) {
        connected = false
        failCount++
        Log.d(TAG, "Connection failed (count=$failCount, error=$error, mode=$savedNodeMode)")
        scheduleReconnect()
    }

    private fun handleConnectionEvent(event: NodeConnectionEvent) {
        if (event.connected) {
            connected = true
            failCount = 0
            reconnectDelay = RECONNECT_BASE_DELAY
            lastDataTs = System.currentTimeMillis()
            reconnectJob?.cancel()
            WalletManager.setApiReady(true)
            Log.d(TAG, "Connected")
        }
    }

    private fun checkHealth() {
        if (!connected) return
        val wallet = WalletManager.walletInstance ?: return
        val sinceLastData = System.currentTimeMillis() - lastDataTs

        if (sinceLastData > DATA_STALE_THRESHOLD && lastDataTs > 0) {
            Log.w(TAG, "Stale connection detected (${sinceLastData / 1000}s since last data)")
            connected = false
            // Poke the wallet to trigger a status update — if connection is dead, this will trigger onConnectionFailed
            try { wallet.getWalletStatus() } catch (_: Exception) {}
            // If still no data after a short wait, force reconnect
            scope?.launch {
                delay(5000)
                if (!connected) {
                    Log.w(TAG, "Still disconnected after health check poke — forcing reconnect")
                    scheduleReconnect()
                }
            }
        }
    }

    private fun scheduleReconnect() {
        // Don't cancel existing reconnect job — let it finish. Error 4 fires every 5s
        // and would keep cancelling+restarting the job, preventing reconnect from ever executing.
        if (reconnectJob?.isActive == true) return
        val s = scope ?: return

        // After many failures, try switching node more aggressively
        val delay = if (failCount > 5) 3000L else reconnectDelay

        reconnectJob = s.launch {
            delay(delay)
            tryReconnect()
            // Exponential backoff: 3s → 6s → 12s → max 15s (was 60s — too long for mobile)
            reconnectDelay = (reconnectDelay * 2).coerceAtMost(15_000L)
        }
    }

    private fun tryReconnect() {
        val wallet = WalletManager.walletInstance ?: return

        if (savedNodeMode == "own" && !savedOwnNodeAddr.isNullOrEmpty()) {
            // OWN NODE: always retry the user's node — never fall back to random
            Log.d(TAG, "Reconnecting to own node: $savedOwnNodeAddr")
            try {
                wallet.changeNodeAddress(savedOwnNodeAddr!!)
            } catch (e: Exception) {
                Log.w(TAG, "Own node reconnect failed: ${e.message}")
            }
        } else {
            // RANDOM NODE: cycle through pool
            if (nodePool.isEmpty()) return
            currentNodeIdx = (currentNodeIdx + 1) % nodePool.size
            val nextNode = nodePool[currentNodeIdx]
            Log.d(TAG, "Reconnecting to random node: $nextNode (idx=$currentNodeIdx/${nodePool.size})")
            try {
                wallet.changeNodeAddress(nextNode)
                SecureStorage.storeNodeAddress(nextNode)
            } catch (e: Exception) {
                Log.w(TAG, "Random node reconnect failed: ${e.message}")
            }
        }
    }
}
