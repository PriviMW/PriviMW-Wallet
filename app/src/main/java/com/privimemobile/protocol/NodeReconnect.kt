package com.privimemobile.protocol

import android.util.Log
import com.mw.beam.beamwallet.core.Api
import com.privimemobile.wallet.WalletEventBus
import com.privimemobile.wallet.WalletManager
import com.privimemobile.wallet.NodeConnectionEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * Node auto-reconnect — cycles through Beam mainnet peers on connection failure.
 *
 * Ports useBeamEvents.ts reconnection logic to Kotlin.
 * Tracks consecutive failures and switches nodes after 2+ failures.
 */
object NodeReconnect {
    private const val TAG = "NodeReconnect"

    private val MAINNET_NODES = listOf(
        "eu-nodes.mainnet.beam.mw:8100",
        "us-nodes.mainnet.beam.mw:8100",
    )

    private var nodePool = MAINNET_NODES.toMutableList()
    private var currentNodeIdx = 0
    private var failCount = 0
    private var lastDataTs = 0L
    private var listenerJob: Job? = null

    /** Start listening for node connection events. */
    fun start(scope: CoroutineScope) {
        if (listenerJob != null) return

        // Load peer pool from C++ core
        try {
            val peers = Api.getDefaultPeers()
            if (peers.isNotEmpty()) nodePool = peers.toMutableList()
        } catch (_: Exception) {}

        listenerJob = scope.launch {
            WalletEventBus.nodeConnection.collectLatest { event ->
                handleConnectionEvent(event)
            }
        }
    }

    fun stop() {
        listenerJob?.cancel()
        listenerJob = null
    }

    private fun handleConnectionEvent(event: NodeConnectionEvent) {
        if (event.connected) {
            failCount = 0
            lastDataTs = System.currentTimeMillis()
            WalletManager.setApiReady(true)
            Log.d(TAG, "Node connected")
        } else if (event.error != 0) {
            // Suppress false positives: if data arrived recently, ignore error
            if (System.currentTimeMillis() - lastDataTs < 5000) return

            failCount++
            Log.d(TAG, "Node connection failed (count=$failCount, error=${event.error})")

            if (failCount >= 2) {
                tryNextNode()
            }
        }
    }

    private fun tryNextNode() {
        if (nodePool.isEmpty()) return
        val wallet = WalletManager.walletInstance ?: return

        currentNodeIdx = (currentNodeIdx + 1) % nodePool.size
        val nextNode = nodePool[currentNodeIdx]

        Log.d(TAG, "Switching to node: $nextNode")
        try {
            wallet.changeNodeAddress(nextNode)
            SecureStorage.storeNodeAddress(nextNode)
            failCount = 0
        } catch (e: Exception) {
            Log.w(TAG, "Failed to switch node: ${e.message}")
        }
    }
}
