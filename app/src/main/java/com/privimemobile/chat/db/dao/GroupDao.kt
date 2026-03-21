package com.privimemobile.chat.db.dao

import androidx.room.*
import com.privimemobile.chat.db.entities.GroupEntity
import com.privimemobile.chat.db.entities.GroupMemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {

    // --- Group CRUD ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity): Long

    @Update
    suspend fun updateGroup(group: GroupEntity)

    @Query("SELECT * FROM groups WHERE group_id = :groupId LIMIT 1")
    suspend fun findByGroupId(groupId: String): GroupEntity?

    @Query("SELECT * FROM groups WHERE archived = 0 ORDER BY last_message_ts DESC")
    fun observeAll(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE archived = 0 ORDER BY last_message_ts DESC")
    suspend fun getAll(): List<GroupEntity>

    @Query("SELECT * FROM groups WHERE archived = 1 ORDER BY last_message_ts DESC")
    fun observeArchived(): Flow<List<GroupEntity>>

    @Query("DELETE FROM groups WHERE group_id = :groupId")
    suspend fun deleteByGroupId(groupId: String)

    // --- Group updates ---

    @Query("UPDATE groups SET name = :name WHERE group_id = :groupId")
    suspend fun updateName(groupId: String, name: String)

    @Query("UPDATE groups SET member_count = :count WHERE group_id = :groupId")
    suspend fun updateMemberCount(groupId: String, count: Int)

    @Query("UPDATE groups SET last_message_ts = :ts, last_message_preview = :preview WHERE group_id = :groupId")
    suspend fun updateLastMessage(groupId: String, ts: Long, preview: String?)

    @Query("UPDATE groups SET unread_count = unread_count + 1 WHERE group_id = :groupId")
    suspend fun incrementUnread(groupId: String)

    @Query("UPDATE groups SET unread_count = 0 WHERE group_id = :groupId")
    suspend fun clearUnread(groupId: String)

    @Query("UPDATE groups SET muted = :muted WHERE group_id = :groupId")
    suspend fun setMuted(groupId: String, muted: Boolean)

    @Query("UPDATE groups SET archived = :archived WHERE group_id = :groupId")
    suspend fun setArchived(groupId: String, archived: Boolean)

    @Query("UPDATE groups SET my_role = :role, my_permissions = :permissions WHERE group_id = :groupId")
    suspend fun updateMyRole(groupId: String, role: Int, permissions: Int)

    @Query("UPDATE groups SET avatar_hash = :hash WHERE group_id = :groupId")
    suspend fun updateAvatarHash(groupId: String, hash: String?)

    // --- Members ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: GroupMemberEntity): Long

    @Query("SELECT * FROM group_members WHERE group_id = :groupId AND handle = :handle LIMIT 1")
    suspend fun findMember(groupId: String, handle: String): GroupMemberEntity?

    @Query("SELECT * FROM group_members WHERE group_id = :groupId AND role != 3 ORDER BY role DESC, handle ASC")
    suspend fun getActiveMembers(groupId: String): List<GroupMemberEntity>

    @Query("SELECT * FROM group_members WHERE group_id = :groupId AND role != 3 ORDER BY role DESC, handle ASC")
    fun observeMembers(groupId: String): Flow<List<GroupMemberEntity>>

    @Query("SELECT COUNT(*) FROM group_members WHERE group_id = :groupId AND role != 3")
    suspend fun countActiveMembers(groupId: String): Int

    @Query("UPDATE group_members SET role = :role, permissions = :permissions WHERE group_id = :groupId AND handle = :handle")
    suspend fun updateMemberRole(groupId: String, handle: String, role: Int, permissions: Int)

    @Query("UPDATE group_members SET display_name = :displayName WHERE group_id = :groupId AND handle = :handle")
    suspend fun updateMemberDisplayName(groupId: String, handle: String, displayName: String?)

    @Query("UPDATE group_members SET wallet_id = :walletId WHERE group_id = :groupId AND handle = :handle")
    suspend fun updateMemberWalletId(groupId: String, handle: String, walletId: String?)

    @Query("DELETE FROM group_members WHERE group_id = :groupId AND handle = :handle")
    suspend fun removeMember(groupId: String, handle: String)

    @Query("DELETE FROM group_members WHERE group_id = :groupId")
    suspend fun removeAllMembers(groupId: String)

    // --- Convenience ---

    @Query("SELECT wallet_id FROM group_members WHERE group_id = :groupId AND role != 3 AND handle != :excludeHandle")
    suspend fun getMemberWalletIds(groupId: String, excludeHandle: String): List<String?>

    @Query("SELECT handle FROM group_members WHERE group_id = :groupId AND role != 3")
    suspend fun getMemberHandles(groupId: String): List<String>
}
