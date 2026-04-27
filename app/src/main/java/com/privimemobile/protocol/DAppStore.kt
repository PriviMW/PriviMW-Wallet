package com.privimemobile.protocol

import android.content.Context
import android.util.Log
import java.util.zip.ZipInputStream
import kotlinx.coroutines.suspendCancellableCoroutine

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

    /** Cached publisher list from on-chain store. */
    private var cachedPublishers: List<Publisher> = emptyList()

    /** SharedPreferences key for unwanted publisher blocklist. */
    private const val PREFS_PUBLISHERS = "privimw_publishers"
    private const val KEY_UNWANTED = "unwanted_publishers"

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

        // First load publishers, then load DApps
        loadPublishers(shader) {
            loadDApps(context, shader, callback)
        }
    }

    /** Query publisher list from on-chain store. */
    private fun loadPublishers(shader: List<Int>, onDone: () -> Unit) {
        if (cachedPublishers.isNotEmpty()) { onDone(); return }

        val args = "action=view_publishers,cid=${Config.DAPP_STORE_CID}"
        val params = mapOf<String, Any?>(
            "args" to args,
            "create_tx" to false,
            "contract" to shader,
        )
        WalletApi.call("invoke_contract", params) { result ->
            try {
                @Suppress("UNCHECKED_CAST")
                val publishers = result["publishers"] as? List<Map<String, Any?>>
                if (publishers != null) {
                    cachedPublishers = publishers.mapNotNull { pub ->
                        val pk = pub["pubkey"] as? String ?: return@mapNotNull null
                        val name = hexToString(pub["name"] as? String ?: "")
                        if (name.isEmpty()) return@mapNotNull null
                        Publisher(
                            pubkey = pk,
                            name = name,
                            aboutMe = hexToString(pub["about_me"] as? String ?: ""),
                            website = hexToString(pub["website"] as? String ?: ""),
                            twitter = hexToString(pub["twitter"] as? String ?: ""),
                            linkedin = hexToString(pub["linkedin"] as? String ?: ""),
                            instagram = hexToString(pub["instagram"] as? String ?: ""),
                            telegram = hexToString(pub["telegram"] as? String ?: ""),
                            discord = hexToString(pub["discord"] as? String ?: ""),
                        )
                    }
                    Log.d(TAG, "Loaded ${cachedPublishers.size} publishers")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load publishers: ${e.message}")
            }
            onDone()
        }
    }

    /** Query DApps list from on-chain store. */
    private fun loadDApps(context: Context, shader: List<Int>, callback: (List<AvailableDApp>) -> Unit) {
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

                val unwanted = getUnwantedPublishers(context)
                val results = dapps.mapNotNull { item ->
                    val guid = item["id"] as? String ?: return@mapNotNull null
                    val name = hexToString(item["name"] as? String ?: "")
                    if (name.isEmpty()) return@mapNotNull null
                    val description = hexToString(item["description"] as? String ?: "")
                    val ipfsCid = item["ipfs_id"] as? String ?: ""
                    val publisherPk = item["publisher"] as? String ?: ""

                    // Skip DApps from unwanted publishers
                    if (publisherPk.isNotEmpty() && unwanted.contains(publisherPk.lowercase())) {
                        return@mapNotNull null
                    }

                    val publisherName = cachedPublishers.find { it.pubkey.equals(publisherPk, ignoreCase = true) }?.name ?: ""

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

                    AvailableDApp(guid, name, description, version, ipfsCid, publisherPk, icon, publisherName = publisherName)
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

    /** Download a DApp ZIP from IPFS via the wallet API (Beam private IPFS network). */
    suspend fun downloadFromIpfs(cid: String): ByteArray = suspendCancellableCoroutine { cont ->
        WalletApi.call("ipfs_get", mapOf(
            "hash" to cid,
            "timeout" to Config.IPFS_GET_TIMEOUT,
        )) { result ->
            if (result.containsKey("error")) {
                val err = result["error"]
                val msg = when (err) {
                    is Map<*, *> -> err["message"] as? String ?: "IPFS download failed"
                    is String -> err
                    else -> "IPFS download failed"
                }
                if (cont.isActive) cont.resumeWith(Result.failure(Exception(msg)))
            } else {
                @Suppress("UNCHECKED_CAST")
                val data = result["data"] as? List<Number>
                if (data != null && cont.isActive) {
                    cont.resume(ByteArray(data.size) { data[it].toByte() }) {}
                } else if (cont.isActive) {
                    cont.resumeWith(Result.failure(Exception("No data returned from IPFS")))
                }
            }
        }
    }

    /** Query available DApps and return result (suspend wrapper). */
    suspend fun queryAvailableDAppsAsync(context: Context): List<AvailableDApp> = suspendCancellableCoroutine { cont ->
        queryAvailableDApps(context) { dapps ->
            if (cont.isActive) cont.resume(dapps) {}
        }
    }

    /** Parse semantic version string "a.b.c" into comparable Int list. */
    private fun parseVersion(v: String): List<Int> {
        return v.split(".").mapNotNull { it.toIntOrNull() }
    }

    /** Returns true if v1 < v2 (semantic version comparison). */
    fun isVersionOlder(v1: String, v2: String): Boolean {
        val a = parseVersion(v1)
        val b = parseVersion(v2)
        val maxLen = maxOf(a.size, b.size)
        for (i in 0 until maxLen) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (av != bv) return av < bv
        }
        return false
    }

    /**
     * Check if an installed DApp has an update, and install it if so.
     * Returns true if an update was applied.
     */
    suspend fun checkAndUpdate(context: Context, dapp: DApp): Boolean {
        return try {
            val availableList = queryAvailableDAppsAsync(context)
            val available = availableList.find { it.guid == dapp.guid } ?: return false
            if (!isVersionOlder(dapp.version, available.version)) return false

            if (available.bundledAsset.isNotEmpty()) {
                DAppManager.installFromAsset(context, available.guid, available.bundledAsset, available.name)
            } else if (available.ipfsCid.isNotEmpty()) {
                val zipData = downloadFromIpfs(available.ipfsCid)
                DAppManager.installFromZip(context, available.guid, zipData, available.name, available.icon)
            } else {
                return false
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    // --- Publisher blocklist storage ---

    /** Get set of unwanted publisher pubkeys (lowercase). */
    fun getUnwantedPublishers(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_PUBLISHERS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_UNWANTED, null) ?: return emptySet()
        return try {
            org.json.JSONArray(json).let { arr ->
                (0 until arr.length()).map { arr.getString(it).lowercase() }.toSet()
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    /** Add a publisher to the unwanted blocklist. */
    fun addUnwantedPublisher(context: Context, pubkey: String) {
        val set = getUnwantedPublishers(context).toMutableSet()
        set.add(pubkey.lowercase())
        saveUnwantedPublishers(context, set)
    }

    /** Remove a publisher from the unwanted blocklist. */
    fun removeUnwantedPublisher(context: Context, pubkey: String) {
        val set = getUnwantedPublishers(context).toMutableSet()
        set.remove(pubkey.lowercase())
        saveUnwantedPublishers(context, set)
    }

    private fun saveUnwantedPublishers(context: Context, set: Set<String>) {
        val arr = org.json.JSONArray()
        set.forEach { arr.put(it) }
        context.getSharedPreferences(PREFS_PUBLISHERS, Context.MODE_PRIVATE)
            .edit().putString(KEY_UNWANTED, arr.toString()).apply()
    }

    /** Query all publishers from cache or on-chain store. */
    suspend fun queryPublishersAsync(context: Context): List<Publisher> {
        return try {
            if (cachedPublishers.isNotEmpty()) return cachedPublishers
            loadShader(context)
            val shader = storeShaderBytes ?: return emptyList()
            suspendCancellableCoroutine { cont ->
                loadPublishers(shader) {
                    if (cont.isActive) cont.resume(cachedPublishers) {}
                }
            }
        } catch (_: Exception) {
            emptyList()
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
    val publisherName: String = "",
)

/** A publisher in the DApp Store. */
data class Publisher(
    val pubkey: String,
    val name: String,
    val aboutMe: String = "",
    val website: String = "",
    val twitter: String = "",
    val linkedin: String = "",
    val instagram: String = "",
    val telegram: String = "",
    val discord: String = "",
)
