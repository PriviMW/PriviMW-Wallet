package com.privimemobile.chat.db.entities

import androidx.room.*

@Entity(
    tableName = "groups",
    indices = [
        Index(value = ["group_id"], unique = true),
    ]
)
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "group_id") val groupId: String,          // 64 hex chars (32 bytes)
    val name: String,
    val description: String? = null,
    @ColumnInfo(name = "creator_handle") val creatorHandle: String,
    @ColumnInfo(name = "is_public") val isPublic: Boolean = false,
    @ColumnInfo(name = "require_approval") val requireApproval: Boolean = false,
    @ColumnInfo(name = "max_members") val maxMembers: Int = 200,
    @ColumnInfo(name = "member_count") val memberCount: Int = 0,
    @ColumnInfo(name = "default_permissions") val defaultPermissions: Int = 3,  // SendMsg + SendMedia
    @ColumnInfo(name = "avatar_hash") val avatarHash: String? = null,
    @ColumnInfo(name = "my_role") val myRole: Int = 0,           // 0=member, 1=admin, 2=creator
    @ColumnInfo(name = "my_permissions") val myPermissions: Int = 3,
    @ColumnInfo(name = "created_height") val createdHeight: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis() / 1000,
    @ColumnInfo(name = "last_message_ts") val lastMessageTs: Long = 0,
    @ColumnInfo(name = "last_message_preview") val lastMessagePreview: String? = null,
    @ColumnInfo(name = "unread_count") val unreadCount: Int = 0,
    @ColumnInfo(name = "muted") val muted: Boolean = false,
    @ColumnInfo(name = "archived") val archived: Boolean = false,
)

@Entity(
    tableName = "group_members",
    indices = [
        Index(value = ["group_id", "handle"], unique = true),
    ]
)
data class GroupMemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "group_id") val groupId: String,    // matches GroupEntity.groupId
    val handle: String,
    @ColumnInfo(name = "display_name") val displayName: String? = null,
    val role: Int = 0,                                      // 0=member, 1=admin, 2=creator, 3=banned
    val permissions: Int = 3,
    @ColumnInfo(name = "wallet_id") val walletId: String? = null,
    @ColumnInfo(name = "joined_height") val joinedHeight: Long = 0,
)
