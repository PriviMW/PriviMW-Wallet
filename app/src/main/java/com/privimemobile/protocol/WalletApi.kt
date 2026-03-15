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
     */
    fun start(scope: CoroutineScope) {
        if (listenerJob != null) return
        listenerJob = scope.launch {
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
    fun call(
        method: String,
        params: Map<String, Any?> = emptyMap(),
        callback: ((Map<String, Any?>) -> Unit)? = null,
    ) {
        val wallet = WalletManager.walletInstance
        if (wallet == null) {
            Log.w(TAG, "call($method): wallet not available")
            callback?.invoke(mapOf("error" to mapOf("message" to "Wallet not connected")))
            return
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
            wallet.callWalletApi(payload)
        } catch (e: Exception) {
            callbacks.remove(id)
            Log.e(TAG, "call($method) failed: ${e.message}")
            callback?.invoke(mapOf("error" to mapOf("message" to (e.message ?: "Unknown error"))))
        }
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
        call(method, params) { result ->
            if (cont.isActive) cont.resume(result)
        }
        cont.invokeOnCancellation {
            // Clean up if coroutine is cancelled
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

    /** Clear stale callbacks (e.g. after backgrounding). */
    fun cleanupStaleCallbacks() {
        if (callbacks.isNotEmpty()) {
            Log.d(TAG, "Clearing ${callbacks.size} stale callbacks")
            callbacks.clear()
        }
    }

    // --- Internal ---

    private fun handleApiResult(json: String) {
        val answer: JSONObject
        try {
            answer = JSONObject(json)
        } catch (_: Exception) {
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
        Log.d(TAG, "← ${info.method} (id=$callId, remaining=${callbacks.size})")

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

        val result = answer.optJSONObject("result")

        // Parse shader output (invoke_contract responses)
        if (result != null && result.has("output")) {
            val output = result.optString("output", "")
            try {
                val shader = JSONObject(output)
                if (shader.has("error")) {
                    info.callback(mapOf("error" to shader.opt("error")))
                    return
                }
                val shaderMap = jsonToMap(shader).toMutableMap()
                if (result.has("raw_data")) {
                    shaderMap["raw_data"] = result.opt("raw_data")
                }
                info.callback(shaderMap)
            } catch (_: Exception) {
                info.callback(mapOf("error" to "Failed to parse shader response"))
            }
            return
        }

        info.callback(if (result != null) jsonToMap(result) else emptyMap())
    }

    private fun onWalletEvent(eventId: String) {
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
