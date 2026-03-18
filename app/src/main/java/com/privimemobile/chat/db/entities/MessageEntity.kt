package com.privimemobile.chat.db.entities

import androidx.room.*

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversation_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["conversation_id"]),
        Index(value = ["sbbs_dedup_key"], unique = true),
        Index(value = ["timestamp"]),
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "conversation_id") val conversationId: Long,
    val text: String? = null,
    val timestamp: Long,               // unix seconds
    val sent: Boolean,                 // true = I sent, false = I received
    val type: String = "dm",           // dm, tip, file, react, delete, typing
    val read: Boolean = false,         // recipient read this (for sent messages)
    val delivered: Boolean = false,    // recipient confirmed delivery (for sent messages)
    val acked: Boolean = false,        // we sent ack for this (for received messages)
    @ColumnInfo(name = "reply_text") val replyText: String? = null,
    @ColumnInfo(name = "tip_amount") val tipAmount: Long = 0,  // groths
    @ColumnInfo(name = "tip_asset_id", defaultValue = "0") val tipAssetId: Int = 0,
    @ColumnInfo(name = "fwd_from") val fwdFrom: String? = null,
    @ColumnInfo(name = "fwd_ts") val fwdTs: Long = 0,
    val failed: Boolean = false,       // send failed after retries
    val deleted: Boolean = false,      // deleted for everyone
    @ColumnInfo(name = "sender_handle") val senderHandle: String? = null,
    @ColumnInfo(name = "sbbs_dedup_key") val sbbsDedupKey: String,  // "ts:hash:sent" — unique
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis() / 1000,
)
