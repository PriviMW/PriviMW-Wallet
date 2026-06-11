package com.privimemobile.ui.chat.media

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import com.privimemobile.R

/**
 * Save a cached file to the public Downloads folder using MediaStore.
 * Works on API 29+ (scoped storage) and falls back for older APIs.
 */
internal fun saveFileToDownloads(
    context: Context,
    srcPath: String,
    fileName: String,
    mimeType: String = "application/octet-stream",
) {
    try {
        val srcFile = java.io.File(srcPath)
        if (!srcFile.exists()) {
            Toast.makeText(context, R.string.toast_file_not_found, Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    srcFile.inputStream().use { input -> input.copyTo(out) }
                }
                Toast.makeText(
                    context,
                    context.getString(R.string.media_saved_to_downloads, fileName),
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                Toast.makeText(context, R.string.media_save_failed, Toast.LENGTH_SHORT).show()
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val destFile = java.io.File(downloadsDir, fileName)
            srcFile.inputStream().use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            Toast.makeText(
                context,
                context.getString(R.string.media_saved_to_downloads, fileName),
                Toast.LENGTH_SHORT,
            ).show()
        }
    } catch (e: Exception) {
        Toast.makeText(
            context,
            context.getString(R.string.media_save_failed) + ": ${e.message}",
            Toast.LENGTH_SHORT,
        ).show()
    }
}

/**
 * Save raw bytes to the public Downloads folder using MediaStore.
 * Used by the DApp BEAM.saveToDownloads JS bridge — no intermediate cache file.
 * Returns true on success, false on failure (caller must surface this to JS).
 *
 * MIME type is validated against the filename extension to prevent content-type
 * confusion (e.g. saving a file as .jpg with text/html MIME that opens in a browser).
 */
internal fun saveBytesToDownloads(
    context: Context,
    bytes: ByteArray,
    fileName: String,
    mimeType: String = "application/octet-stream",
): Boolean {
    return try {
        val validatedMime = sanitizeMimeType(fileName, mimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, validatedMime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out -> out.write(bytes) }
                Toast.makeText(
                    context,
                    context.getString(R.string.media_saved_to_downloads, fileName),
                    Toast.LENGTH_SHORT,
                ).show()
                true
            } else {
                Toast.makeText(context, R.string.media_save_failed, Toast.LENGTH_SHORT).show()
                false
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val destFile = java.io.File(downloadsDir, fileName)
            destFile.outputStream().use { out -> out.write(bytes) }
            Toast.makeText(
                context,
                context.getString(R.string.media_saved_to_downloads, fileName),
                Toast.LENGTH_SHORT,
            ).show()
            true
        }
    } catch (e: Exception) {
        Toast.makeText(
            context,
            context.getString(R.string.media_save_failed) + ": ${e.message}",
            Toast.LENGTH_SHORT,
        ).show()
        false
    }
}

/**
 * If the supplied MIME type doesn't match the filename extension, derive MIME
 * from the extension instead. This prevents a DApp from saving a .jpg file
 * that the system will open as text/html or another executable type.
 */
private fun sanitizeMimeType(fileName: String, declared: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    val extMime = when (ext) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        "json" -> "application/json"
        "mp4" -> "video/mp4"
        "mp3" -> "audio/mpeg"
        "zip" -> "application/zip"
        else -> null
    }
    // If extension is known and declared MIME disagrees, prefer the extension.
    // If extension is unknown, accept the declared MIME (or default).
    return when {
        extMime != null && declared.isBlank() -> extMime
        extMime != null && !declared.equals(extMime, ignoreCase = true) -> extMime
        else -> declared.ifBlank { "application/octet-stream" }
    }
}
