package com.privimemobile.protocol

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * DApp lifecycle manager — install, uninstall, list, and launch DApps.
 *
 * Ports dapp-manager.ts to Kotlin.
 * Uses java.util.zip (no JSZip dependency) and SharedPreferences (no AsyncStorage).
 */
object DAppManager {
    private const val TAG = "DAppManager"
    private const val PREFS_NAME = "privimw_dapps"
    private const val KEY_INSTALLED = "installed_dapps"
    private val GUID_REGEX = Regex("^[a-fA-F0-9]{32,64}$")

    private fun dappsDir(ctx: Context): File = File(ctx.filesDir, "dapps")

    /** All installed DApps. */
    fun getInstalled(ctx: Context): List<DApp> {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_INSTALLED, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                DApp(
                    guid = obj.optString("guid"),
                    name = obj.optString("name"),
                    description = obj.optString("description"),
                    version = obj.optString("version", "1.0"),
                    localPath = obj.optString("localPath"),
                    icon = obj.optString("icon", ""),
                    url = obj.optString("url", "localapp/app/index.html"),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Save installed DApps list. */
    private fun saveInstalled(ctx: Context, dapps: List<DApp>) {
        val arr = JSONArray()
        dapps.forEach { d ->
            arr.put(JSONObject().apply {
                put("guid", d.guid)
                put("name", d.name)
                put("description", d.description)
                put("version", d.version)
                put("localPath", d.localPath)
                put("icon", d.icon)
                put("url", d.url)
            })
        }
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_INSTALLED, arr.toString())
            .apply()
    }

    /** Install a DApp from a ZIP byte array. */
    fun installFromZip(ctx: Context, guid: String, zipData: ByteArray, fallbackName: String = "DApp", fallbackIcon: String = ""): DApp {
        if (!GUID_REGEX.matches(guid)) throw IllegalArgumentException("Invalid GUID: $guid")

        val dir = dappsDir(ctx)
        if (!dir.exists()) dir.mkdirs()

        val dappDir = File(dir, guid)
        if (dappDir.exists()) dappDir.deleteRecursively()
        dappDir.mkdirs()

        // Extract ZIP
        var manifestJson: String? = null
        var icon: String? = null
        ZipInputStream(zipData.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                // Security: reject path traversal
                if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                    Log.w(TAG, "Blocked path traversal in ZIP: $name")
                    entry = zis.nextEntry
                    continue
                }

                val outFile = File(dappDir, name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        zis.copyTo(fos)
                    }

                    // Capture manifest
                    if (name == "manifest.json") {
                        manifestJson = outFile.readText()
                    }
                    // Capture icon
                    if (name in listOf("app/appicon.svg", "app/icon.svg", "app/logo.svg")) {
                        icon = outFile.readText()
                    }
                }
                entry = zis.nextEntry
            }
        }

        val manifest = if (manifestJson != null) JSONObject(manifestJson!!) else JSONObject()

        val rawUrl = manifest.optString("url", "localapp/app/index.html")
        val safeUrl = validateDAppUrl(rawUrl) ?: "localapp/app/index.html"
        if (safeUrl != rawUrl) {
            Log.w(TAG, "Sanitized manifest url for $guid: '$rawUrl' → '$safeUrl'")
        }

        val dapp = DApp(
            guid = guid,
            name = manifest.optString("name", fallbackName),
            description = manifest.optString("description", ""),
            version = manifest.optString("version", "1.0"),
            localPath = dappDir.absolutePath,
            icon = icon ?: fallbackIcon,
            url = safeUrl,
        )

        // Add to installed list
        val installed = getInstalled(ctx).toMutableList()
        installed.removeAll { it.guid == guid }
        installed.add(dapp)
        saveInstalled(ctx, installed)

        Log.d(TAG, "Installed ${dapp.name} to ${dapp.localPath}")
        return dapp
    }

    /** Install from bundled APK asset. */
    fun installFromAsset(ctx: Context, guid: String, assetName: String, fallbackName: String = "DApp"): DApp {
        val zipData = ctx.assets.open(assetName).use { it.readBytes() }
        return installFromZip(ctx, guid, zipData, fallbackName)
    }

    /** Uninstall a DApp. */
    fun uninstall(ctx: Context, guid: String) {
        Log.d(TAG, "Uninstalling $guid")
        val dappDir = File(dappsDir(ctx), guid)
        if (dappDir.exists()) dappDir.deleteRecursively()
        val installed = getInstalled(ctx).toMutableList()
        installed.removeAll { it.guid == guid }
        saveInstalled(ctx, installed)
    }

    /** Get file:// launch URL for an installed DApp. */
    fun getLaunchUrl(dapp: DApp): String {
        val relativeUrl = dapp.url.removePrefix("localapp/")
        val resolved = File(dapp.localPath, relativeUrl).canonicalFile
        val root = File(dapp.localPath).canonicalFile
        val safe = if (
            !resolved.path.startsWith(root.path + File.separator) &&
            resolved.path != root.path
        ) {
            Log.e(TAG, "BLOCKED path traversal: ${dapp.url} → ${resolved.path} (outside ${root.path})")
            File(dapp.localPath, "app/index.html").canonicalFile
        } else resolved
        return "file://${safe.path}"
    }

    /**
     * Validate a DApp manifest url field.
     * Rejects path traversal, absolute paths, and anything outside the DApp directory.
     * Returns a safe url string, or null if the input is malicious.
     */
    private fun validateDAppUrl(url: String): String? {
        if (url.isEmpty()) return "localapp/app/index.html"
        // Reject path traversal sequences
        if (url.contains("..") || url.contains("//")) return null
        // Reject absolute paths
        if (url.startsWith("/") || url.startsWith("\\")) return null
        // Only allow relative paths within localapp/
        if (!url.startsWith("localapp/") && url.contains("/")) return null
        return url
    }
}

/** Installed DApp metadata. */
data class DApp(
    val guid: String,
    val name: String,
    val description: String,
    val version: String,
    val localPath: String,
    val icon: String = "",
    val url: String = "localapp/app/index.html",
)
