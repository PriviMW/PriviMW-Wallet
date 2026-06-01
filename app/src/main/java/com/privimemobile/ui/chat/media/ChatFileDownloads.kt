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
