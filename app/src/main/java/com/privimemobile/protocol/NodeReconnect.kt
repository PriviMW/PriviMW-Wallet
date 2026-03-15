package com.privimemobile.protocol

import android.util.Log
import com.mw.beam.beamwallet.core.Api
import com.privimemobile.wallet.WalletEventBus
import com.privimemobile.wallet.WalletManager
import com.privimemobile.wallet.NodeConnectionEvent
import kotlinx.coroutines.*

/**
 * Node auto-reconnect — cycles through Beam mainnet peers on connection failure.
 *
 * Ports useBeamEvents.ts reconnection logic to Kotlin.
 *
 * CRITICAL: On Beam 7.x core, error 4 fires for secondary connections even when
 * the primary is fine and data is flowing. We suppress these by checking if data
 * arrived recently (10-second window, matching RN).
 */
object NodeReconnect {
    private const val TAG = "NodeReconnect"

    private var nodePool = Config.MAINNET_NODES.toMutableList()
    private var currentNodeIdx = 0
    private var failCount = 0
    @Volatile
    var lastDataTs = 0L  // Public so WalletListener can update on wallet status events
        private set
    private var listenerJob: Job? = null
    private var walletStatusJob: Job? = null

    // Own node retry tracking (matches RN ownNodeRetried flag)
    private var savedNodeMode = "random"
    private var savedOwnNodeAddr: String? = null
    private var ownNodeRetried = false

    /** Update last data timestamp — call from WalletListener on any real data event. */
    fun onDataReceived() {
        lastDataTs = System.currentTimeMillis()
    }

    /** Start listening for node connection events. */
    fun start(scope: CoroutineScope) {
        if (listenerJob != null) return

        // Load node mode and own node address
        savedNodeMode = SecureStorage.getString("node_mode") ?: "random"
        savedOwnNodeAddr = SecureStorage.getString("own_node_address")

        // Load peer pool from C++ core
        try {
            val peers = Api.getDefaultPeers()
            if (peers.isNotEmpty()) nodePool = peers.toMutableList()
        } catch (_: Exception) {}

        // Listen for node connection events
        listenerJob = scope.launch {
            WalletEventBus.nodeConnection.collect { event ->
                handleConnectionEvent(event)
            }
        }

        // Also listen for wallet status events to track lastDataTs
        // (matches RN: lastDataTs.current = Date.now() on every status with height > 0)
        // walletStatus is now a SharedFlow (per-asset events) — any event with height > 0 counts
        walletStatusJob = scope.launch {
            WalletEventBus.walletStatus.collect { status ->
                if (status.height > 0) {
                    lastDataTs = System.currentTimeMillis()
                }
            }
        }
    }

    fun stop() {
        listenerJob?.cancel()
        listenerJob = null
        walletStatusJob?.cancel()
        walletStatusJob = null
    }

    /**
     * Called directly from WalletListener when a real connection failure occurs
     * (already passed the 10s suppression check).
     */
    fun onConnectionFailed(error: Int) {
        failCount++
        Log.d(TAG, "Node connection failed (count=$failCount, error=$error)")
        if (failCount >= 2) {
            tryNextNode()
        }
    }

    private fun handleConnectionEvent(event: NodeConnectionEvent) {
        if (event.connected) {
            failCount = 0
            lastDataTs = System.currentTimeMillis()
            ownNodeRetried = false
            WalletManager.setApiReady(true)
            Log.d(TAG, "Node connected")
        }
        // Failures are now handled by onConnectionFailed() called from WalletListener directly
    }

    private fun tryNextNode() {
        val wallet = WalletManager.walletInstance ?: return

        // Own node mode: try own node first before falling back to random (matches RN)
        if (savedNodeMode == "own" && !savedOwnNodeAddr.isNullOrEmpty() && !ownNodeRetried) {
            ownNodeRetried = true
            Log.d(TAG, "Auto-reconnect: retrying own node ${savedOwnNodeAddr}")
            try {
                wallet.changeNodeAddress(savedOwnNodeAddr!!)
                SecureStorage.storeNodeAddress(savedOwnNodeAddr!!)
                failCount = 0
                return
            } catch (_: Exception) {}
        }

        // Round-robin through node pool
        if (nodePool.isEmpty()) return
        currentNodeIdx = (currentNodeIdx + 1) % nodePool.size
        val nextNode = nodePool[currentNodeIdx]

        Log.d(TAG, "Auto-reconnect: switching to $nextNode")
        try {
            wallet.changeNodeAddress(nextNode)
            SecureStorage.storeNodeAddress(nextNode)
            failCount = 0
        } catch (e: Exception) {
            Log.w(TAG, "Failed to switch node: ${e.message}")
        }
    }
}
