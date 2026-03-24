package com.privimemobile.wallet

import android.content.Context
import android.util.Log
import com.privimemobile.protocol.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/**
 * Checks GitHub Releases API for new app versions.
 * Respects the "wallet_updates_notif" toggle in Settings.
 */
object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val REPO = "PriviMW/PriviMW-Wallet"
    private const val API_URL = "https://api.github.com/repos/$REPO/releases"
    private const val KEY_LAST_CHECK = "update_last_check_ts"
    private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours

    data class UpdateInfo(
        val latestVersion: String,
        val currentVersion: String,
        val releaseUrl: String,
        val releaseNotes: String?
    )

    /**
     * Check if enough time has passed since last check (24h).
     */
    fun shouldCheck(): Boolean {
        if (!SecureStorage.getBoolean("wallet_updates_notif", true)) return false
        val lastCheck = SecureStorage.getLong(KEY_LAST_CHECK, 0L)
        return System.currentTimeMillis() - lastCheck > CHECK_INTERVAL_MS
    }

    /**
     * Check GitHub releases for a newer version.
     * Returns UpdateInfo if update available, null if up-to-date or error.
     */
    suspend fun checkForUpdate(context: Context, force: Boolean = false): UpdateInfo? {
        if (!force && !shouldCheck()) return null

        return withContext(Dispatchers.IO) {
            try {
                val currentVersion = context.packageManager
                    .getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"

                val connection = URL(API_URL).openConnection().apply {
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    connectTimeout = 10000
                    readTimeout = 10000
                }
                val response = connection.getInputStream().bufferedReader().readText()
                val jsonArray = org.json.JSONArray(response)
                if (jsonArray.length() == 0) return@withContext null

                // Find first non-prerelease, non-draft release
                var json: JSONObject? = null
                for (i in 0 until jsonArray.length()) {
                    val r = jsonArray.getJSONObject(i)
                    if (!r.optBoolean("prerelease", false) && !r.optBoolean("draft", false)) {
                        json = r
                        break
                    }
                }
                if (json == null) return@withContext null

                val tagName = json.optString("tag_name", "").removePrefix("v")
                val htmlUrl = json.optString("html_url", "")
                val body = json.optString("body", null)

                // Record check time
                SecureStorage.putLong(KEY_LAST_CHECK, System.currentTimeMillis())

                if (tagName.isNotEmpty() && isNewer(tagName, currentVersion)) {
                    Log.d(TAG, "Update available: $tagName (current: $currentVersion)")
                    UpdateInfo(tagName, currentVersion, htmlUrl, body)
                } else {
                    Log.d(TAG, "Up to date: $currentVersion (latest: $tagName)")
                    null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed: ${e.message}")
                null
            }
        }
    }

    /**
     * Compare semantic versions: "1.1.0" > "1.0.0"
     */
    private fun isNewer(latest: String, current: String): Boolean {
        val l = latest.split(".").mapNotNull { it.toIntOrNull() }
        val c = current.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return false
    }
}
