package com.privimemobile.protocol

import android.content.Context
import android.util.Base64
import android.util.Log

/**
 * Shader invocation helpers — wraps invoke_contract for PriviMe contract calls.
 *
 * Ports shader.ts to Kotlin.
 * Handles both view calls (read-only) and TX calls (two-step: invoke → process_invoke_data).
 */
object ShaderInvoker {
    private const val TAG = "ShaderInvoker"
    private var shaderBytes: List<Int>? = null

    /** Check if shader bytes have been loaded. */
    fun hasShaderBytes(): Boolean = shaderBytes != null

    /** Load PriviMe app.wasm shader bytes from bundled assets. */
    fun loadShader(context: Context) {
        if (shaderBytes != null) return
        try {
            val bytes = context.assets.open("app.wasm").use { it.readBytes() }
            shaderBytes = bytes.map { it.toInt() and 0xFF }
            Log.d(TAG, "Shader loaded: ${shaderBytes!!.size} bytes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load shader: ${e.message}")
        }
    }

    /**
     * View call — read-only, no transaction.
     *
     * Usage:
     * ```
     * ShaderInvoker.invoke("user", "view_user", mapOf("handle" to "jay")) { result ->
     *     val registered = result["registered"] as? Int
     * }
     * ```
     */
    fun invoke(
        role: String,
        action: String,
        extra: Map<String, Any?> = emptyMap(),
        callback: ((Map<String, Any?>) -> Unit)? = null,
    ) {
        val args = buildArgs(role, action, extra)
        val params = mutableMapOf<String, Any?>(
            "args" to args,
            "create_tx" to false,
        )
        shaderBytes?.let { params["contract"] = it }
        WalletApi.call("invoke_contract", params, callback)
    }

    /**
     * Suspending view call for coroutines.
     */
    suspend fun invokeAsync(
        role: String,
        action: String,
        extra: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> {
        val args = buildArgs(role, action, extra)
        val params = mutableMapOf<String, Any?>(
            "args" to args,
            "create_tx" to false,
        )
        shaderBytes?.let { params["contract"] = it }
        return WalletApi.callAsync("invoke_contract", params)
    }

    /**
     * Transaction call — two-step: invoke_contract → process_invoke_data.
     *
     * @param onReady Called when raw_data is obtained (before TX consent).
     * @param callback Called with final TX result.
     */
    fun tx(
        role: String,
        action: String,
        extra: Map<String, Any?> = emptyMap(),
        onReady: (() -> Unit)? = null,
        callback: ((Map<String, Any?>) -> Unit)? = null,
    ) {
        Log.d(TAG, "TX: $role.$action")
        invoke(role, action, extra) { result ->
            if (result.containsKey("error")) {
                callback?.invoke(result)
                return@invoke
            }
            val rawData = result["raw_data"]
            if (rawData != null) {
                Log.d(TAG, "Got raw_data, calling process_invoke_data")
                onReady?.invoke()
                WalletApi.call("process_invoke_data", mapOf("data" to rawData), callback)
            } else {
                Log.w(TAG, "No raw_data in result — TX not created")
                callback?.invoke(result)
            }
        }
    }

    private fun buildArgs(role: String, action: String, extra: Map<String, Any?>): String {
        val parts = mutableListOf("role=$role", "action=$action")
        extra.forEach { (k, v) ->
            if (v != null && v.toString().isNotEmpty()) {
                parts.add("$k=$v")
            }
        }
        parts.add("cid=${Config.PRIVIME_CID}")
        return parts.joinToString(",")
    }
}
