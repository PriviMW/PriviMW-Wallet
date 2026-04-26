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
 * For OWN NODE: retries the user's node up to 3 times, then falls back to a random node.
 *   In fallback mode, stays idle until user taps status to reconnect (or app goes to foreground).
 * For RANDOM NODE: cycles through Beam mainnet peers on failure.
 * Includes periodic health check to detect silent disconnects.
 */
object NodeReconnect {
    private const val TAG = "NodeReconnect"
    private const val HEALTH_CHECK_INTERVAL = 30_000L // 30s
    private const val RECONNECT_BASE_DELAY = 3_000L   // 3s initial
    private const val RECONNECT_MAX_DELAY = 60_000L   // 60s max
    private const val DATA_STALE_THRESHOLD = 60_000L  // 60s without data = stale

    private var nodePool = Config.MAINNET_NODES.toMutableList()
    private var currentNodeIdx = 0
    private var failCount = 0
    private var reconnectDelay = RECONNECT_BASE_DELAY

    // Fallback tracking — when own node is unreachable we switch to a random node
    private var savedFallbackNode: String? = null
    @Volatile
    private var autoReconnect = true

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

    /**
     * Gentle poke — ask wallet for status to trigger data callbacks.
     * Used by error 4 handler: doesn't flip UI or trigger reconnect.
     * If wallet is alive, onStatus/onSyncProgress will fire → onDataReceived() resets timer.
     * If wallet is dead, health check (every 30s) will catch it.
     */
    fun pokeWallet() {
        try { WalletManager.walletInstance?.getWalletStatus() } catch (_: Exception) {}
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

    /** Called on app foreground recovery — attempt to reconnect to user's own node. */
    fun onForegroundRecovery() {
        if (!connected) {
            Log.d(TAG, "Foreground recovery — forcing immediate reconnect")
            reconnectJob?.cancel()
            reconnectJob = null
            reconnectDelay = RECONNECT_BASE_DELAY
            failCount = 0
            tryReconnect()
        } else if (savedNodeMode == "fallback" && autoReconnect) {
            // In fallback mode with auto-reconnect on — try own node
            Log.d(TAG, "Foreground recovery — trying own node from fallback")
            reconnectJob?.cancel()
            reconnectJob = null
            reconnectDelay = RECONNECT_BASE_DELAY
            failCount = 0
            savedNodeMode = "own"
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

            // Compute isFallback from actual trust state at connection time
            val trusted = WalletManager.walletInstance?.isConnectionTrusted() ?: true
            val isInOwnMode = savedNodeMode == "own"
            val isFallback = isInOwnMode && !trusted
            Log.d(TAG, "Connected: trusted=$trusted, isInOwnMode=$isInOwnMode, isFallback=$isFallback")
            WalletEventBus.emitNodeConnection(NodeConnectionEvent(connected = true, isFallback = isFallback))
        }
    }

    private fun checkHealth() {
        if (!connected) return
        val wallet = WalletManager.walletInstance ?: return
        val sinceLastData = System.currentTimeMillis() - lastDataTs

        if (sinceLastData > DATA_STALE_THRESHOLD && lastDataTs > 0) {
            Log.w(TAG, "Stale connection detected (${sinceLastData / 1000}s since last data)")
            // Poke the wallet — if alive, data callbacks will fire and reset lastDataTs
            try { wallet.getWalletStatus() } catch (_: Exception) {}
            val probeTs = System.currentTimeMillis()
            scope?.launch {
                delay(5000)
                if (lastDataTs < probeTs) {
                    // No data came back from poke — genuinely disconnected
                    Log.w(TAG, "Health check probe failed — marking disconnected")
                    connected = false
                    WalletEventBus.emitNodeConnection(NodeConnectionEvent(connected = false))
                    scheduleReconnect()
                } else {
                    Log.d(TAG, "Health check probe OK — data received after poke")
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

        when (savedNodeMode) {
            "own" -> {
                // OWN NODE: retry user's node, switch to fallback after 3 failures
                if (failCount >= 3 && savedFallbackNode != null) {
                    // Already have a fallback — use it
                    Log.d(TAG, "Own node failed 3+ times — switching to fallback: $savedFallbackNode")
                    wallet.changeNodeAddress(savedFallbackNode!!)
                    WalletEventBus.emitNodeConnection(NodeConnectionEvent(connected = true, isFallback = true))
                } else {
                    Log.d(TAG, "Reconnecting to own node: $savedOwnNodeAddr")
                    try {
                        wallet.changeNodeAddress(savedOwnNodeAddr!!)
                    } catch (e: Exception) {
                        Log.w(TAG, "Own node reconnect failed: ${e.message}")
                        // After 3 failures, pick a fallback node
                        if (failCount >= 3 && nodePool.isNotEmpty()) {
                            currentNodeIdx = (currentNodeIdx + 1) % nodePool.size
                            savedFallbackNode = nodePool[currentNodeIdx]
                            savedNodeMode = "fallback"
                            autoReconnect = false
                            Log.d(TAG, "Picked fallback node: $savedFallbackNode")
                        }
                    }
                }
            }
            "fallback" -> {
                // FALLBACK: cycle through random nodes, no retry of own node
                if (nodePool.isEmpty()) return
                currentNodeIdx = (currentNodeIdx + 1) % nodePool.size
                val nextNode = nodePool[currentNodeIdx]
                savedFallbackNode = nextNode
                Log.d(TAG, "Fallback cycling to node: $nextNode")
                try {
                    wallet.changeNodeAddress(nextNode)
                } catch (e: Exception) {
                    Log.w(TAG, "Fallback node reconnect failed: ${e.message}")
                }
            }
            else -> {
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

    /**
     * Public method — manually trigger reconnection to user's own node.
     * Called when user taps the status text in fallback mode.
     */
    fun reconnectOwnNode() {
        val wallet = WalletManager.walletInstance
        if (wallet == null) {
            Log.w(TAG, "reconnectOwnNode: wallet instance is null")
            return
        }
        val savedAddr = SecureStorage.getString("own_node_address")
        if (savedAddr == null || savedAddr.isBlank()) {
            Log.w(TAG, "reconnectOwnNode: no own node address stored")
            return
        }
        Log.d(TAG, "reconnectOwnNode: reconnecting to $savedAddr")
        reconnectJob?.cancel()
        reconnectJob = null
        connected = false
        savedNodeMode = "own"
        autoReconnect = true
        reconnectDelay = RECONNECT_BASE_DELAY
        failCount = 0
        WalletEventBus.emitNodeConnection(NodeConnectionEvent(connected = false))
        try {
            wallet.changeNodeAddress(savedAddr)
            wallet.enableBodyRequests(false)
            Log.d(TAG, "reconnectOwnNode: changeNodeAddress called")
        } catch (e: Exception) {
            Log.w(TAG, "reconnectOwnNode: changeNodeAddress failed: ${e.message}")
        }
    }
}
