package com.privimemobile.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.privimemobile.chat.db.entities.ContactEntity
import com.privimemobile.chat.db.entities.ConversationEntity

/** Room snapshot on DM open — avoids input-bar flash while contact/conv Flows emit empty initial. */
internal data class DmOpenSeed(
    val contact: ContactEntity?,
    val conv: ConversationEntity?,
    val resolvedAddress: String?,
    val draftText: String?,
)

data class StickerMeta(
    val packName: String,
    val packId: String,
    val packTotal: Int,
    val emoji: String? = null,
)

data class FullscreenImageItem(
    val filePath: String,
    val fileName: String,
    val msgId: Long = 0,
    val msgTs: Long = 0,
    val isMine: Boolean = false,
)

data class FullscreenImageData(
    val images: List<FullscreenImageItem>,
    val initialIndex: Int = 0,
) {
    init {
        require(images.isNotEmpty())
        require(initialIndex in images.indices)
    }

    constructor(
        filePath: String,
        fileName: String,
        msgId: Long = 0,
        msgTs: Long = 0,
        isMine: Boolean = false,
    ) : this(listOf(FullscreenImageItem(filePath, fileName, msgId, msgTs, isMine)))
}

/** File picked for send (URI + metadata). */
data class PendingFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String,
)

data class FileInfo(val name: String, val size: Long, val mimeType: String)

internal fun getFileInfo(context: Context, uri: Uri): FileInfo? {
    return try {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIndex >= 0) it.getString(nameIndex) else "file"
                val size = if (sizeIndex >= 0) it.getLong(sizeIndex) else 0L
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                FileInfo(name, size, mimeType)
            } else {
                null
            }
        }
    } catch (_: Exception) {
        null
    }
}
