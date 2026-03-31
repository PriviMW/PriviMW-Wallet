package com.privimemobile.chat.db.entities

import androidx.room.*

@Entity(
    tableName = "reactions",
    indices = [
        Index(value = ["message_ts", "sender_handle", "emoji"], unique = true),
    ]
)
data class ReactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "message_ts") val messageTs: Long,
    @ColumnInfo(name = "sender_handle") val senderHandle: String,
    val emoji: String,
    val timestamp: Long,
    @ColumnInfo(name = "removed", defaultValue = "0") val removed: Boolean = false,
    @ColumnInfo(name = "notified_at", defaultValue = "0") val notifiedAt: Long = 0,
)
