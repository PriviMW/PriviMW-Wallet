package com.privimemobile.chat.db.entities

import androidx.room.*

@Entity(
    tableName = "conversations",
    indices = [
        Index(value = ["conv_key"], unique = true),
        Index(value = ["last_message_ts"]),
    ]
)
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "conv_key") val convKey: String,           // "@handle" or wallet_id or "group_{id}"
    val handle: String? = null,
    @ColumnInfo(name = "display_name") val displayName: String? = null,
    @ColumnInfo(name = "wallet_id") val walletId: String? = null,
    @ColumnInfo(name = "avatar_cid") val avatarCid: String? = null,
    @ColumnInfo(name = "unread_count") val unreadCount: Int = 0,
    @ColumnInfo(name = "last_message_ts") val lastMessageTs: Long = 0,
    @ColumnInfo(name = "last_message_preview") val lastMessagePreview: String? = null,
    @ColumnInfo(name = "deleted_at_ts") val deletedAtTs: Long = 0,  // tombstone — 0 = not deleted
    @ColumnInfo(name = "is_blocked") val isBlocked: Boolean = false,
    val pinned: Boolean = false,
    val muted: Boolean = false,
    @ColumnInfo(name = "is_group") val isGroup: Boolean = false,
    @ColumnInfo(name = "draft_text") val draftText: String? = null,
    @ColumnInfo(name = "disappear_timer") val disappearTimer: Int = 0,  // 0=off, seconds (30, 300, 3600, 86400)
    @ColumnInfo(name = "pinned_message_ts") val pinnedMessageTs: Long = 0,  // 0=none, timestamp of pinned msg
)
