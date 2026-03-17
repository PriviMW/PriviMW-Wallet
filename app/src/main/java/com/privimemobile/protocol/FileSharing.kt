package com.privimemobile.protocol

import android.content.Context
import android.util.Log

/**
 * STUB — will be replaced by chat/transport/IpfsTransport in Phase B.
 * Keeps ChatScreen compiling until file sharing is migrated.
 */
object FileSharing {
    private const val TAG = "FileSharing"
    var uploadInProgress = false

    fun getLocalFilePath(cid: String): String? = null

    suspend fun sendFile(
        context: Context,
        fileUri: android.net.Uri,
        fileName: String,
        fileSize: Long,
        mimeType: String,
        convKey: String,
        caption: String = "",
    ) {
        Log.w(TAG, "sendFile stub — not implemented yet, use IpfsTransport")
    }

    suspend fun downloadFile(
        context: Context,
        cid: String,
        keyHex: String,
        ivHex: String,
        mime: String,
        inlineData: String? = null,
    ): String {
        Log.w(TAG, "downloadFile stub — not implemented yet")
        throw UnsupportedOperationException("File sharing not yet migrated to new chat system")
    }

    suspend fun autoDownloadImages(context: Context, messages: List<ChatMessage>): Map<String, String> {
        return emptyMap()
    }
}
