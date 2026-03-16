package com.privimemobile.protocol

import android.content.Context
import android.util.Log
import java.util.zip.ZipInputStream

/**
 * DApp Store — queries the on-chain DApp Store contract for published DApps.
 *
 * Ports dapp-store.ts to Kotlin.
 * Uses dapps_store_app.wasm shader to call view_dapps on the store contract.
 */
object DAppStore {
    private const val TAG = "DAppStore"
    private var storeShaderBytes: List<Int>? = null

    /** Bundled DApps shipped with the APK — available for instant install without IPFS. */
    private val BUNDLED_DAPPS = listOf(
        AvailableDApp(
            guid = "abcc470e12c6422291f360f83d79355e",
            name = "BeamX DAO",
            description = "Governance, staking and voting",
            version = "1.0.0",
            ipfsCid = "",
            publisher = "",
            bundledAsset = "dao-core-app.dapp",
        ),
        AvailableDApp(
            guid = "c26538f5ce9e410b89c1fd0dff783f97",
            name = "BeamX DAO Voting",
            description = "Voting on Beam community proposals",
            version = "1.0.0",
            ipfsCid = "",
            publisher = "",
            bundledAsset = "dao-voting-app.dapp",
        ),
    )

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
            Log.w(TAG, "Store shader not loaded — returning bundled DApps only")
            callback(getBundledDApps(context))
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
                Log.w(TAG, "view_dapps error: ${result["error"]} — returning bundled DApps only")
                callback(BUNDLED_DAPPS)
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

                // Merge bundled DApps (add first, skip if already in on-chain list)
                val onChainGuids = results.map { it.guid }.toSet()
                val bundled = getBundledDApps(context).filter { it.guid !in onChainGuids }
                val merged = bundled + results
                Log.d(TAG, "Found ${results.size} on-chain + ${bundled.size} bundled DApps")
                callback(merged)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse DApp Store response: ${e.message}")
                callback(emptyList())
            }
        }
    }

    /** Extract icon SVG from a bundled .dapp ZIP asset. */
    private fun extractIconFromDapp(context: Context, assetName: String): String {
        return try {
            context.assets.open(assetName).use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == "app/icon.svg" || entry.name == "app/appicon.svg") {
                            val svg = zip.bufferedReader().readText()
                            if (svg.trimStart().startsWith("<svg") || svg.trimStart().startsWith("<?xml")) {
                                return svg
                            }
                        }
                        entry = zip.nextEntry
                    }
                }
            }
            ""
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract icon from $assetName: ${e.message}")
            ""
        }
    }

    /** Get bundled DApps with icons extracted from .dapp assets. */
    fun getBundledDApps(context: Context): List<AvailableDApp> {
        return BUNDLED_DAPPS.map { dapp ->
            if (dapp.icon.isEmpty() && dapp.bundledAsset.isNotEmpty()) {
                val icon = extractIconFromDapp(context, dapp.bundledAsset)
                dapp.copy(icon = icon)
            } else dapp
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
