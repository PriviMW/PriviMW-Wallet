package com.privimemobile.chat.db.dao

import androidx.room.*
import com.privimemobile.chat.db.entities.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    /** All active conversations sorted: pinned first, then by last message time. */
    @Query("""
        SELECT * FROM conversations
        WHERE deleted_at_ts = 0
        ORDER BY pinned DESC, last_message_ts DESC
    """)
    fun observeAll(): Flow<List<ConversationEntity>>

    /** Total unread count across all conversations (for badge). */
    @Query("SELECT COALESCE(SUM(unread_count), 0) FROM conversations WHERE deleted_at_ts = 0 AND is_blocked = 0")
    fun observeTotalUnread(): Flow<Int>

    /** Find by conv_key. */
    @Query("SELECT * FROM conversations WHERE conv_key = :convKey LIMIT 1")
    suspend fun findByKey(convKey: String): ConversationEntity?

    /** Find by id. */
    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ConversationEntity?

    /** Insert or get existing. Returns row id. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(conversation: ConversationEntity): Long

    @Update
    suspend fun update(conversation: ConversationEntity)

    /** Get or create conversation for a conv_key. */
    @Transaction
    suspend fun getOrCreate(convKey: String, handle: String? = null, displayName: String? = null, walletId: String? = null): ConversationEntity {
        val existing = findByKey(convKey)
        if (existing != null) {
            // Un-delete if it was soft-deleted (user deleted chat then started new conversation)
            if (existing.deletedAtTs > 0) {
                setDeletedTs(existing.id, 0)
                clearUnread(existing.id)
                updateLastMessage(existing.id, 0, null)
                return existing.copy(deletedAtTs = 0, unreadCount = 0, lastMessageTs = 0, lastMessagePreview = null)
            }
            return existing
        }
        val entity = ConversationEntity(
            convKey = convKey,
            handle = handle,
            displayName = displayName,
            walletId = walletId,
        )
        val id = insert(entity)
        return entity.copy(id = id)
    }

    /** Update last message info. */
    @Query("""
        UPDATE conversations
        SET last_message_ts = :ts, last_message_preview = :preview
        WHERE id = :convId
    """)
    suspend fun updateLastMessage(convId: Long, ts: Long, preview: String?)

    /** Increment unread count. */
    @Query("UPDATE conversations SET unread_count = unread_count + 1 WHERE id = :convId")
    suspend fun incrementUnread(convId: Long)

    /** Clear unread count. */
    @Query("UPDATE conversations SET unread_count = 0 WHERE id = :convId")
    suspend fun clearUnread(convId: Long)

    /** Soft delete (tombstone). */
    @Query("UPDATE conversations SET deleted_at_ts = :ts WHERE id = :convId")
    suspend fun softDelete(convId: Long, ts: Long = System.currentTimeMillis() / 1000)

    /** Block/unblock. */
    @Query("UPDATE conversations SET is_blocked = :blocked WHERE id = :convId")
    suspend fun setBlocked(convId: Long, blocked: Boolean)

    /** Pin/unpin. */
    @Query("UPDATE conversations SET pinned = :pinned WHERE id = :convId")
    suspend fun setPinned(convId: Long, pinned: Boolean)

    /** Mute/unmute. */
    @Query("UPDATE conversations SET muted = :muted WHERE id = :convId")
    suspend fun setMuted(convId: Long, muted: Boolean)

    /** Set unread count directly (for migration). */
    @Query("UPDATE conversations SET unread_count = :count WHERE id = :convId")
    suspend fun setUnread(convId: Long, count: Int)

    /** Set deleted-at tombstone timestamp (for migration). */
    @Query("UPDATE conversations SET deleted_at_ts = :ts WHERE id = :convId")
    suspend fun setDeletedTs(convId: Long, ts: Long)

    /** Update contact info on conversation. */
    @Query("""
        UPDATE conversations
        SET display_name = :displayName, wallet_id = :walletId, avatar_cid = :avatarCid
        WHERE conv_key = :convKey
    """)
    suspend fun updateContactInfo(convKey: String, displayName: String?, walletId: String?, avatarCid: String?)

    /** Check if conversation is blocked. */
    @Query("SELECT is_blocked FROM conversations WHERE conv_key = :convKey LIMIT 1")
    suspend fun isBlocked(convKey: String): Boolean?

    /** Check if conversation is muted (by ID). */
    @Query("SELECT muted FROM conversations WHERE id = :convId LIMIT 1")
    suspend fun isMuted(convId: Long): Boolean?

    /** Get total unread count (non-Flow, for one-shot reads). */
    @Query("SELECT COALESCE(SUM(unread_count), 0) FROM conversations WHERE deleted_at_ts = 0 AND is_blocked = 0")
    suspend fun getTotalUnread(): Int

    /** Check if conversation is tombstoned and get the timestamp. */
    @Query("SELECT deleted_at_ts FROM conversations WHERE conv_key = :convKey LIMIT 1")
    suspend fun getDeletedTs(convKey: String): Long?

    /** Save draft text for a conversation (null to clear). */
    @Query("UPDATE conversations SET draft_text = :text WHERE id = :convId")
    suspend fun setDraft(convId: Long, text: String?)

    /** Set disappearing message timer (0 = off). */
    @Query("UPDATE conversations SET disappear_timer = :timer WHERE id = :convId")
    suspend fun setDisappearTimer(convId: Long, timer: Int)
}
