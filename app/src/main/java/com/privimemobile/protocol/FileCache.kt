package com.privimemobile.protocol

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * File cache — stores decrypted files on disk with LRU eviction.
 *
 * Ports file-cache.ts to Kotlin.
 * Uses SharedPreferences for metadata (replacing AsyncStorage).
 */
object FileCache {
    private const val TAG = "FileCache"
    private const val PREFS_NAME = "privimw_file_cache"
    private const val META_KEY = "cache_meta"
    private const val MAX_CACHED = 100

    private var cacheDir: File? = null

    fun init(context: Context) {
        cacheDir = File(context.cacheDir, "privime-files").also {
            if (!it.exists()) it.mkdirs()
        }
    }

    private fun cidToFilename(cid: String): String {
        return cid.replace(Regex("[^a-zA-Z0-9]"), "_")
    }

    /** Get cached file path if it exists on disk. */
    fun getCachedFilePath(cid: String): String? {
        val dir = cacheDir ?: return null
        val file = File(dir, cidToFilename(cid))
        return if (file.exists()) file.absolutePath else null
    }

    /** Store decrypted file to disk cache. Returns local file path. */
    fun cacheFile(context: Context, cid: String, data: ByteArray, mime: String): String {
        val dir = cacheDir ?: run {
            init(context)
            cacheDir!!
        }
        val file = File(dir, cidToFilename(cid))
        file.writeBytes(data)

        // Update metadata
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val metaJson = prefs.getString(META_KEY, "[]")
        val meta = try { JSONArray(metaJson) } catch (_: Exception) { JSONArray() }

        // Remove existing entry
        for (i in meta.length() - 1 downTo 0) {
            if (meta.optJSONObject(i)?.optString("cid") == cid) {
                meta.remove(i)
            }
        }

        meta.put(JSONObject().apply {
            put("cid", cid)
            put("mime", mime)
            put("ts", System.currentTimeMillis())
            put("size", data.size)
        })

        // Evict oldest if over limit
        while (meta.length() > MAX_CACHED) {
            val oldest = meta.optJSONObject(0)
            if (oldest != null) {
                val oldCid = oldest.optString("cid")
                File(dir, cidToFilename(oldCid)).delete()
            }
            meta.remove(0)
        }

        prefs.edit().putString(META_KEY, meta.toString()).apply()
        return file.absolutePath
    }

    /** Read cached file as byte array. */
    fun readCachedFile(cid: String): ByteArray? {
        val path = getCachedFilePath(cid) ?: return null
        return try { File(path).readBytes() } catch (_: Exception) { null }
    }

    /** Clear entire file cache. */
    fun clearCache(context: Context) {
        cacheDir?.listFiles()?.forEach { it.delete() }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(META_KEY).apply()
    }
}
