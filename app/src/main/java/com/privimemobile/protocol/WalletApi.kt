package com.privimemobile.protocol

import android.util.Log
import com.privimemobile.wallet.WalletEventBus
import com.privimemobile.wallet.WalletManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Wallet API — JSON-RPC caller via JNI.
 *
 * Ports wallet-api.ts to Kotlin with coroutines.
 * Uses [WalletEventBus.apiResult] for responses instead of RN NativeEventEmitter.
 *
 * Call IDs start at 1,000,000 to avoid collision with DApp call IDs (DApps start from 1).
 * DApp responses are routed by [DAppResponseRouter] on the native side and never reach here.
 */
object WalletApi {
    private const val TAG = "WalletApi"
    private const val BASE_CALL_ID = 1_000_000

    private var callIdCounter = BASE_CALL_ID
    private val callbacks = ConcurrentHashMap<Int, CallbackInfo>()

    // Own persistent scope — survives Activity death (like ChatService).
    // Only cancelled on explicit stop() (wallet deletion / full shutdown).
    private var ownJob = SupervisorJob()
    private var ownScope = CoroutineScope(Dispatchers.Main + ownJob)

    private var listenerJob: Job? = null
    private var isSubscribed = false

    // Event handlers — set by protocol startup
    var onSystemStateChanged: (() -> Unit)? = null
    var onTxsChanged: (() -> Unit)? = null

    private data class CallbackInfo(
        val method: String,
        val callback: (result: Map<String, Any?>) -> Unit,
    )

    /**
     * Start listening for API results from the C++ core.
     * Call once at app startup after wallet is opened.
     *
     * The scope parameter is accepted for backward compatibility but the listener
     * runs on WalletApi's own persistent scope so it survives Activity death.
     * This is critical for background message delivery when the app is swiped away.
     */
    fun start(scope: CoroutineScope) {
        // Skip if already running with active scope.
        if (listenerJob?.isActive == true) return
        listenerJob?.cancel()
        // Use our own scope — not tied to any Activity lifecycle.
        // Survives swipe-away as long as BackgroundService keeps the process alive.
        listenerJob = ownScope.launch {
            WalletEventBus.apiResult.collectLatest { json ->
                handleApiResult(json)
            }
        }
    }

    fun stop() {
        listenerJob?.cancel()
        listenerJob = null
        callbacks.clear()
        isSubscribed = false
        // Cancel and recreate scope for clean restart
        ownJob.cancel()
        ownJob = SupervisorJob()
        ownScope = CoroutineScope(Dispatchers.Main + ownJob)
    }

    /**
     * Call a wallet API method with callback.
     *
     * Usage:
     * ```
     * WalletApi.call("wallet_status") { result ->
     *     val height = result["current_height"] as? Long
     * }
     * ```
     */
    /**
     * Call a wallet API method with callback.
     * Returns the call ID so callers can cancel or track the request.
     */
    fun call(
        method: String,
        params: Map<String, Any?> = emptyMap(),
        callback: ((Map<String, Any?>) -> Unit)? = null,
    ): Int {
        val wallet = WalletManager.walletInstance
        if (wallet == null || !com.mw.beam.beamwallet.core.Api.isWalletRunning()) {
            Log.w(TAG, "call($method): wallet not available")
            callback?.invoke(mapOf("error" to mapOf("message" to "Wallet not connected")))
            return -1
        }

        val id = ++callIdCounter
        if (callback != null) {
            callbacks[id] = CallbackInfo(method, callback)
        }

        try {
            val payload = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", id)
                put("method", method)
                put("params", JSONObject(params))
            }.toString()

            Log.d(TAG, "→ $method (id=$id, pending=${callbacks.size})")
            if (method.startsWith("ipfs_")) {
                Log.d(TAG, "  IPFS payload: ${payload.take(300)}")
            }
            wallet.callWalletApi(payload)
        } catch (e: Exception) {
            callbacks.remove(id)
            Log.e(TAG, "call($method) failed: ${e.message}")
            callback?.invoke(mapOf("error" to mapOf("message" to (e.message ?: "Unknown error"))))
        }
        return id
    }

    /**
     * Direct wallet API call — bypasses DApp sandbox restrictions.
     * Same as desktop/CLI wallet API. Use for methods like tx_split
     * that are blocked in the DApp bridge.
     */
    fun callDirect(
        method: String,
        params: Map<String, Any?> = emptyMap(),
        callback: ((Map<String, Any?>) -> Unit)? = null,
    ): Int {
        val wallet = WalletManager.walletInstance
        if (wallet == null || !com.mw.beam.beamwallet.core.Api.isWalletRunning()) {
            Log.w(TAG, "callDirect($method): wallet not available")
            callback?.invoke(mapOf("error" to mapOf("message" to "Wallet not connected")))
            return -1
        }

        val id = ++callIdCounter
        if (callback != null) {
            callbacks[id] = CallbackInfo(method, callback)
        }

        try {
            val payload = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", id)
                put("method", method)
                put("params", JSONObject(params))
            }.toString()

            Log.d(TAG, "→ $method (id=$id, direct, pending=${callbacks.size})")
            wallet.callWalletApiDirect(payload)
        } catch (e: Exception) {
            callbacks.remove(id)
            Log.e(TAG, "callDirect($method) failed: ${e.message}")
            callback?.invoke(mapOf("error" to mapOf("message" to (e.message ?: "Unknown error"))))
        }
        return id
    }

    /**
     * Call with pre-built JSON params string — avoids JSONObject serialization for large data.
     * Use for IPFS operations with large byte arrays to prevent OOM.
     */
    fun callRaw(
        method: String,
        paramsJson: String,
        callback: ((Map<String, Any?>) -> Unit)? = null,
    ): Int {
        val wallet = WalletManager.walletInstance
        if (wallet == null || !com.mw.beam.beamwallet.core.Api.isWalletRunning()) {
            Log.w(TAG, "callRaw($method): wallet not available")
            callback?.invoke(mapOf("error" to mapOf("message" to "Wallet not connected")))
            return -1
        }

        val id = ++callIdCounter
        if (callback != null) {
            callbacks[id] = CallbackInfo(method, callback)
        }

        try {
            val payload = """{"jsonrpc":"2.0","id":$id,"method":"$method","params":$paramsJson}"""
            Log.d(TAG, "→ $method (id=$id, pending=${callbacks.size}, rawLen=${payload.length})")
            wallet.callWalletApi(payload)
        } catch (e: Exception) {
            callbacks.remove(id)
            Log.e(TAG, "callRaw($method) failed: ${e.message}")
            callback?.invoke(mapOf("error" to mapOf("message" to (e.message ?: "Unknown error"))))
        }
        return id
    }

    /**
     * Suspending version of [call] for use in coroutines.
     *
     * Usage:
     * ```
     * val result = WalletApi.callAsync("wallet_status")
     * val height = result["current_height"] as? Long
     * ```
     */
    suspend fun callAsync(
        method: String,
        params: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> = suspendCancellableCoroutine { cont ->
        val id = call(method, params) { result ->
            if (cont.isActive) cont.resume(result)
        }
        cont.invokeOnCancellation {
            if (id >= 0) callbacks.remove(id)
        }
    }

    /** Direct version of [callAsync] — bypasses DApp sandbox. */
    suspend fun callAsyncDirect(
        method: String,
        params: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> = suspendCancellableCoroutine { cont ->
        val id = callDirect(method, params) { result ->
            if (cont.isActive) cont.resume(result)
        }
        cont.invokeOnCancellation {
            if (id >= 0) callbacks.remove(id)
        }
    }

    /** Subscribe to wallet system events (block changes, TX changes). */
    fun subscribeToEvents() {
        if (isSubscribed) return
        isSubscribed = true
        call("ev_subunsub", mapOf("ev_txs_changed" to true, "ev_system_state" to true))
    }

    /** Re-subscribe after returning from background or DApp. */
    fun resubscribeEvents() {
        Log.d(TAG, "Re-subscribing to wallet events")
        call("ev_subunsub", mapOf("ev_txs_changed" to true, "ev_system_state" to true))
    }

    // --- Internal ---

    private fun handleApiResult(json: String) {
        Log.d(TAG, "handleApiResult: ${json.length} chars, prefix=${json.take(80)}")
        val answer: JSONObject
        try {
            answer = JSONObject(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse API result (${json.length} chars): ${e.message}")
            return
        }

        // Handle wallet events (string IDs like "ev_system_state")
        val rawId = answer.opt("id")
        if (rawId is String && rawId.startsWith("ev_")) {
            onWalletEvent(rawId)
            return
        }

        // Numeric callback responses
        val callId = when (rawId) {
            is Int -> rawId
            is Long -> rawId.toInt()
            is Number -> rawId.toInt()
            else -> return
        }

        val info = callbacks.remove(callId)
        if (info == null) {
            Log.d(TAG, "← response for unknown id=$callId (stale callback?)")
            return
        }
        Log.d(TAG, "← ${info.method} (id=$callId, remaining=${callbacks.size}), raw=${json.take(300)}")

        // Error response
        if (answer.has("error")) {
            val err = answer.opt("error")
            val errorMap = when (err) {
                is JSONObject -> jsonToMap(err)
                is String -> mapOf("message" to err)
                else -> mapOf("message" to "Unknown error")
            }
            info.callback(mapOf("error" to errorMap))
            return
        }

        val rawResult = answer.opt("result")

        // Result can be: JSONObject, String, Array, or null
        when (rawResult) {
            is JSONObject -> {
                // Parse shader output (invoke_contract responses)
                if (rawResult.has("output")) {
                    val output = rawResult.optString("output", "")
                    try {
                        val shader = JSONObject(output)
                        if (shader.has("error")) {
                            info.callback(mapOf("error" to shader.opt("error")))
                            return
                        }
                        val shaderMap = jsonToMap(shader).toMutableMap()
                        if (rawResult.has("raw_data")) {
                            shaderMap["raw_data"] = rawResult.opt("raw_data")
                        }
                        info.callback(shaderMap)
                    } catch (_: Exception) {
                        info.callback(mapOf("error" to "Failed to parse shader response"))
                    }
                } else {
                    info.callback(jsonToMap(rawResult))
                }
            }
            is String -> {
                // Some methods return raw string (e.g., create_address returns address string)
                info.callback(mapOf("address" to rawResult))
            }
            is org.json.JSONArray -> {
                // Array results (e.g., read_messages returns [{...}, {...}])
                info.callback(mapOf("messages" to jsonArrayToList(rawResult)))
            }
            else -> {
                info.callback(emptyMap())
            }
        }
    }

    private fun onWalletEvent(eventId: String) {
        Log.d(TAG, "Wallet event: $eventId")
        when (eventId) {
            "ev_system_state" -> onSystemStateChanged?.invoke()
            "ev_txs_changed" -> onTxsChanged?.invoke()
        }
    }
}

/** Convert JSONObject to Map recursively. */
internal fun jsonToMap(json: JSONObject): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    json.keys().forEach { key ->
        map[key] = when (val value = json.opt(key)) {
            is JSONObject -> jsonToMap(value)
            is org.json.JSONArray -> jsonArrayToList(value)
            JSONObject.NULL -> null
            else -> value
        }
    }
    return map
}

internal fun jsonArrayToList(arr: org.json.JSONArray): List<Any?> {
    return (0 until arr.length()).map { i ->
        when (val value = arr.opt(i)) {
            is JSONObject -> jsonToMap(value)
            is org.json.JSONArray -> jsonArrayToList(value)
            JSONObject.NULL -> null
            else -> value
        }
    }
}
