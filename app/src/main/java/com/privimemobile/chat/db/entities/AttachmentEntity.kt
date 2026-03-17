package com.privimemobile.chat.db.entities

import androidx.room.*

@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(
        entity = MessageEntity::class,
        parentColumns = ["id"],
        childColumns = ["message_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["message_id"]),
        Index(value = ["conversation_id"]),
        Index(value = ["ipfs_cid"]),
    ]
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "message_id") val messageId: Long,
    @ColumnInfo(name = "conversation_id") val conversationId: Long,
    @ColumnInfo(name = "ipfs_cid") val ipfsCid: String? = null,
    @ColumnInfo(name = "encryption_key") val encryptionKey: String,   // 64 hex chars
    @ColumnInfo(name = "encryption_iv") val encryptionIv: String,     // 24 hex chars
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "file_size") val fileSize: Long,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "inline_data") val inlineData: String? = null, // base64 ciphertext
    @ColumnInfo(name = "local_path") val localPath: String? = null,
    @ColumnInfo(name = "download_status") val downloadStatus: String = "idle", // idle/downloading/decrypting/done/error
)
