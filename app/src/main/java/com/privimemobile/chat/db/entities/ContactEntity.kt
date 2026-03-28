package com.privimemobile.chat.db.entities

import androidx.room.*

@Entity(
    tableName = "contacts",
    indices = [
        Index(value = ["handle"], unique = true),
        Index(value = ["wallet_id"]),
    ]
)
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val handle: String,
    @ColumnInfo(name = "wallet_id") val walletId: String? = null,
    @ColumnInfo(name = "display_name") val displayName: String? = null,
    @ColumnInfo(name = "avatar_cid") val avatarCid: String? = null,
    @ColumnInfo(name = "registered_height") val registeredHeight: Long = 0,
    @ColumnInfo(name = "last_resolved_at") val lastResolvedAt: Long = 0,
    @ColumnInfo(name = "last_profile_update_ts") val lastProfileUpdateTs: Long = 0,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
)
