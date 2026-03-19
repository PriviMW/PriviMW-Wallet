package com.privimemobile.chat.db.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.privimemobile.chat.db.entities.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    /** Paged messages for a conversation (newest first for lazy loading). */
    @Query("SELECT * FROM messages WHERE conversation_id = :convId AND deleted = 0 ORDER BY timestamp DESC")
    fun observePaged(convId: Long): PagingSource<Int, MessageEntity>

    /** All messages for a conversation (for export, non-paged). */
    @Query("SELECT * FROM messages WHERE conversation_id = :convId AND deleted = 0 ORDER BY timestamp ASC")
    fun observeAll(convId: Long): Flow<List<MessageEntity>>

    /** Insert — IGNORE on dedup key conflict. Returns -1 if ignored. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(messages: List<MessageEntity>): List<Long>

    /** Mark sent messages as delivered by their timestamps (delivery ack received). */
    @Query("UPDATE messages SET delivered = 1 WHERE conversation_id = :convId AND sent = 1 AND timestamp IN (:timestamps)")
    suspend fun markDelivered(convId: Long, timestamps: List<Long>)

    /** Mark sent messages as read by their timestamps (read ack received). Also marks delivered. */
    @Query("UPDATE messages SET read = 1, delivered = 1 WHERE conversation_id = :convId AND sent = 1 AND timestamp IN (:timestamps)")
    suspend fun markRead(convId: Long, timestamps: List<Long>)

    /** Mark received messages as acked (we sent ack). */
    @Query("UPDATE messages SET acked = 1 WHERE conversation_id = :convId AND sent = 0 AND acked = 0 AND timestamp IN (:timestamps)")
    suspend fun markAcked(convId: Long, timestamps: List<Long>)

    /** Get unacked received message timestamps for sending read receipts. */
    @Query("SELECT timestamp FROM messages WHERE conversation_id = :convId AND sent = 0 AND acked = 0")
    suspend fun getUnackedTimestamps(convId: Long): List<Long>

    /** Get ALL received message timestamps — for catch-all read receipt on chat open. */
    @Query("SELECT timestamp FROM messages WHERE conversation_id = :convId AND sent = 0")
    suspend fun getAllReceivedTimestamps(convId: Long): List<Long>

    /** Mark as deleted (delete for everyone) — by timestamp+sender (for SBBS protocol). */
    @Query("UPDATE messages SET deleted = 1 WHERE conversation_id = :convId AND timestamp = :ts AND sender_handle = :senderHandle")
    suspend fun markDeleted(convId: Long, ts: Long, senderHandle: String)

    /** Mark as deleted by entity ID (for local multi-select delete). */
    @Query("UPDATE messages SET deleted = 1 WHERE id = :messageId")
    suspend fun markDeletedById(messageId: Long)

    /** Mark send failed. */
    @Query("UPDATE messages SET failed = 1 WHERE id = :messageId")
    suspend fun markFailed(messageId: Long)

    /** FTS search across all messages. */
    @Query("SELECT m.* FROM messages m INNER JOIN messages_fts f ON m.rowid = f.rowid WHERE messages_fts MATCH :query AND m.deleted = 0 ORDER BY m.timestamp DESC LIMIT 100")
    suspend fun search(query: String): List<MessageEntity>

    /** Count unread received messages in a conversation. */
    @Query("SELECT COUNT(*) FROM messages WHERE conversation_id = :convId AND sent = 0 AND acked = 0")
    suspend fun countUnread(convId: Long): Int

    /** Soft-delete all messages in a conversation (preserves dedup keys to block SBBS re-delivery). */
    @Query("UPDATE messages SET deleted = 1 WHERE conversation_id = :convId AND deleted = 0")
    suspend fun softDeleteByConversation(convId: Long)

    /** Hard-delete all messages in a conversation (only for DB cleanup, loses dedup keys). */
    @Query("DELETE FROM messages WHERE conversation_id = :convId")
    suspend fun deleteByConversation(convId: Long)

    /** Delete a single message locally (delete for me). */
    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteById(messageId: Long)

    /** Search messages within a specific conversation (LIKE query). */
    @Query("SELECT * FROM messages WHERE conversation_id = :convId AND deleted = 0 AND text LIKE :query ORDER BY timestamp DESC LIMIT 50")
    suspend fun searchInConversation(convId: Long, query: String): List<MessageEntity>

    /** Edit a message — preserves original text on first edit. */
    @Query("""
        UPDATE messages SET text = :newText, edited = 1,
        original_text = CASE WHEN original_text IS NULL THEN text ELSE original_text END
        WHERE conversation_id = :convId AND timestamp = :ts AND sender_handle = :senderHandle
    """)
    suspend fun editMessage(convId: Long, ts: Long, senderHandle: String, newText: String)

    /** Find a sent message by timestamp (for editing). */
    @Query("SELECT * FROM messages WHERE conversation_id = :convId AND timestamp = :ts AND sent = 1 LIMIT 1")
    suspend fun findSentByTimestamp(convId: Long, ts: Long): MessageEntity?

    /** Get conversation IDs that have expiring messages (for preview update after cleanup). */
    @Query("SELECT DISTINCT conversation_id FROM messages WHERE expires_at > 0 AND expires_at < :now AND deleted = 0")
    suspend fun getConversationsWithExpired(now: Long): List<Long>

    /** Get the latest non-deleted message for a conversation (for preview update). */
    @Query("SELECT * FROM messages WHERE conversation_id = :convId AND deleted = 0 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestMessage(convId: Long): MessageEntity?

    /** Soft-delete expired disappearing messages (preserves dedup keys to block SBBS re-delivery). */
    @Query("UPDATE messages SET deleted = 1 WHERE expires_at > 0 AND expires_at < :now AND deleted = 0")
    suspend fun deleteExpired(now: Long): Int
}
