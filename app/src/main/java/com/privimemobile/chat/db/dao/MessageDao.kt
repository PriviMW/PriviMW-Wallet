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

    /** Mark as deleted (delete for everyone). */
    @Query("UPDATE messages SET deleted = 1 WHERE conversation_id = :convId AND timestamp = :ts AND sender_handle = :senderHandle")
    suspend fun markDeleted(convId: Long, ts: Long, senderHandle: String)

    /** Mark send failed. */
    @Query("UPDATE messages SET failed = 1 WHERE id = :messageId")
    suspend fun markFailed(messageId: Long)

    /** FTS search across all messages. */
    @Query("SELECT m.* FROM messages m INNER JOIN messages_fts f ON m.rowid = f.rowid WHERE messages_fts MATCH :query AND m.deleted = 0 ORDER BY m.timestamp DESC LIMIT 100")
    suspend fun search(query: String): List<MessageEntity>

    /** Count unread received messages in a conversation. */
    @Query("SELECT COUNT(*) FROM messages WHERE conversation_id = :convId AND sent = 0 AND acked = 0")
    suspend fun countUnread(convId: Long): Int

    /** Delete all messages in a conversation (local delete). */
    @Query("DELETE FROM messages WHERE conversation_id = :convId")
    suspend fun deleteByConversation(convId: Long)
}
