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
    @ColumnInfo(name = "group_id") val groupId: String,   // contract hash
    val name: String,
    val description: String? = null,
    @ColumnInfo(name = "admin_handle") val adminHandle: String,
    @ColumnInfo(name = "member_count") val memberCount: Int = 0,
    @ColumnInfo(name = "sbbs_address") val sbbsAddress: String? = null,
    @ColumnInfo(name = "avatar_cid") val avatarCid: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis() / 1000,
)

@Entity(
    tableName = "group_members",
    foreignKeys = [ForeignKey(
        entity = GroupEntity::class,
        parentColumns = ["id"],
        childColumns = ["group_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["group_id", "handle"], unique = true),
    ]
)
data class GroupMemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "group_id") val groupId: Long,
    val handle: String,
    @ColumnInfo(name = "display_name") val displayName: String? = null,
    @ColumnInfo(name = "avatar_cid") val avatarCid: String? = null,
    @ColumnInfo(name = "joined_at") val joinedAt: Long = System.currentTimeMillis() / 1000,
)
