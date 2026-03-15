package com.privimemobile.protocol

import android.content.Context
import android.util.Log

/**
 * DApp Store — queries the on-chain DApp Store contract for published DApps.
 *
 * Ports dapp-store.ts to Kotlin.
 * Uses dapps_store_app.wasm shader to call view_dapps on the store contract.
 */
object DAppStore {
    private const val TAG = "DAppStore"
    private var storeShaderBytes: List<Int>? = null

    /** Load the DApp Store query shader from bundled assets. */
    fun loadShader(context: Context) {
        if (storeShaderBytes != null) return
        try {
            val bytes = context.assets.open("dapps_store_app.wasm").use { it.readBytes() }
            storeShaderBytes = bytes.map { it.toInt() and 0xFF }
            Log.d(TAG, "Store shader loaded: ${storeShaderBytes!!.size} bytes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load store shader: ${e.message}")
        }
    }

    /**
     * Query available DApps from the on-chain store.
     *
     * Returns list of available DApps (excluding already installed).
     */
    fun queryAvailableDApps(
        context: Context,
        callback: (List<AvailableDApp>) -> Unit,
    ) {
        loadShader(context)
        val shader = storeShaderBytes
        if (shader == null) {
            Log.w(TAG, "Store shader not loaded")
            callback(emptyList())
            return
        }

        val args = "action=view_dapps,cid=${Config.DAPP_STORE_CID}"
        val params = mapOf<String, Any?>(
            "args" to args,
            "create_tx" to false,
            "contract" to shader,
        )

        Log.d(TAG, "Querying on-chain DApp Store...")
        WalletApi.call("invoke_contract", params) { result ->
            if (result.containsKey("error")) {
                Log.w(TAG, "view_dapps error: ${result["error"]}")
                callback(emptyList())
                return@call
            }

            try {
                @Suppress("UNCHECKED_CAST")
                val dapps = result["dapps"] as? List<Map<String, Any?>>
                if (dapps == null) {
                    Log.w(TAG, "No dapps array in response")
                    callback(emptyList())
                    return@call
                }

                val results = dapps.mapNotNull { item ->
                    val guid = item["id"] as? String ?: return@mapNotNull null
                    val name = hexToString(item["name"] as? String ?: "")
                    if (name.isEmpty()) return@mapNotNull null
                    val description = hexToString(item["description"] as? String ?: "")
                    val ipfsCid = item["ipfs_id"] as? String ?: ""
                    val publisher = item["publisher"] as? String ?: ""

                    // Parse version
                    @Suppress("UNCHECKED_CAST")
                    val ver = item["version"] as? Map<String, Any?>
                    val version = if (ver != null) {
                        val major = (ver["major"] as? Number)?.toInt() ?: 1
                        val minor = (ver["minor"] as? Number)?.toInt() ?: 0
                        val release = (ver["release"] as? Number)?.toInt() ?: 0
                        "$major.$minor.$release"
                    } else "1.0.0"

                    // Decode icon (hex-encoded SVG)
                    val iconHex = item["icon"] as? String ?: ""
                    val icon = if (iconHex.isNotEmpty()) {
                        val decoded = hexToString(iconHex).trim()
                        val prefix = "data:image/svg+xml;utf8,"
                        val svg = if (decoded.startsWith(prefix)) decoded.substring(prefix.length) else decoded
                        if (svg.startsWith("<svg") || svg.startsWith("<?xml")) svg else ""
                    } else ""

                    AvailableDApp(guid, name, description, version, ipfsCid, publisher, icon)
                }

                Log.d(TAG, "Found ${results.size} DApps on-chain")
                callback(results)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse DApp Store response: ${e.message}")
                callback(emptyList())
            }
        }
    }

    /** Decode hex-encoded UTF-8 string (names, descriptions from contract). */
    private fun hexToString(hex: String): String {
        if (hex.isEmpty()) return ""
        return try {
            val bytes = ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }
}

/** A DApp available for installation from the on-chain store. */
data class AvailableDApp(
    val guid: String,
    val name: String,
    val description: String,
    val version: String,
    val ipfsCid: String,
    val publisher: String,
    val icon: String = "",
    val bundledAsset: String = "",
)
