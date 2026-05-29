package com.privimemobile.protocol

import android.content.Context
import org.json.JSONObject

/**
 * On-chain DApp Store version snapshot (guid → semver string).
 * Used for fast launch: compare installed version to cache without hitting the chain.
 */
object DAppStoreCatalogCache {
    private const val PREFS_NAME = "privimw_dapp_store_catalog"
    private const val KEY_VERSIONS = "versions_json"
    private const val KEY_FETCHED_AT_MS = "fetched_at_ms"

    /** Trust cached versions for instant open (background refresh still runs on DApps tab). */
    const val CATALOG_TTL_MS = 24 * 60 * 60 * 1000L

    /** Min age before a post-launch background verify hits the chain. */
    const val BACKGROUND_VERIFY_MIN_AGE_MS = 60 * 60 * 1000L

    /** Debounce catalog refresh when opening the My DApps tab. */
    const val TAB_REFRESH_INTERVAL_MS = 15 * 60 * 1000L

    fun getFetchedAtMs(context: Context): Long =
        prefs(context).getLong(KEY_FETCHED_AT_MS, 0L)

    fun isStale(context: Context, maxAgeMs: Long): Boolean {
        val at = getFetchedAtMs(context)
        return at == 0L || System.currentTimeMillis() - at > maxAgeMs
    }

    fun getStoreVersion(context: Context, guid: String): String? =
        loadVersions(context)[guid]

    fun save(context: Context, available: List<AvailableDApp>) {
        val map = available.associate { it.guid to it.version }
        saveVersions(context, map)
    }

    fun putVersion(context: Context, guid: String, version: String) {
        val map = loadVersions(context).toMutableMap()
        map[guid] = version
        saveVersions(context, map)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun loadVersions(context: Context): Map<String, String> {
        val json = prefs(context).getString(KEY_VERSIONS, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(json)
            buildMap {
                obj.keys().forEach { key ->
                    val v = obj.optString(key, "")
                    if (v.isNotEmpty()) put(key, v)
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun saveVersions(context: Context, versions: Map<String, String>) {
        val obj = JSONObject()
        versions.forEach { (guid, version) -> obj.put(guid, version) }
        prefs(context).edit()
            .putString(KEY_VERSIONS, obj.toString())
            .putLong(KEY_FETCHED_AT_MS, System.currentTimeMillis())
            .apply()
    }
}
