package com.privimemobile.chat.db.dao

import androidx.room.*
import com.privimemobile.chat.db.entities.ReactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReactionDao {

    /** Observe reactions for a specific message (excludes soft-deleted). */
    @Query("SELECT * FROM reactions WHERE message_ts = :messageTs AND removed = 0 ORDER BY timestamp ASC")
    fun observeForMessage(messageTs: Long): Flow<List<ReactionEntity>>

    /** Insert — ignore duplicate (same message_ts + sender + emoji). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(reaction: ReactionEntity): Long

    /** Re-activate a soft-deleted reaction ONLY if incoming ts is newer than removal time.
     *  Prevents SBBS re-delivery of old "react" from un-removing, while allowing genuine re-reacts. */
    @Query("UPDATE reactions SET removed = 0, timestamp = :newTs WHERE message_ts = :messageTs AND sender_handle = :senderHandle AND emoji = :emoji AND removed = 1 AND timestamp < :newTs")
    suspend fun reactivate(messageTs: Long, senderHandle: String, emoji: String, newTs: Long): Int

    /** Soft-delete a reaction. Sets timestamp to removal time so re-delivery detection works. */
    @Query("UPDATE reactions SET removed = 1, timestamp = :removedAt WHERE message_ts = :messageTs AND sender_handle = :senderHandle AND emoji = :emoji")
    suspend fun remove(messageTs: Long, senderHandle: String, emoji: String, removedAt: Long)

    /** Get all reactions for a list of message timestamps (batch load, excludes soft-deleted). */
    @Query("SELECT * FROM reactions WHERE message_ts IN (:timestamps) AND removed = 0 ORDER BY timestamp ASC")
    suspend fun getForMessages(timestamps: List<Long>): List<ReactionEntity>

    /** Observe all reactions for a conversation's messages (excludes soft-deleted). */
    @Query("SELECT r.* FROM reactions r INNER JOIN messages m ON r.message_ts = m.timestamp WHERE m.conversation_id = :convId AND r.removed = 0 ORDER BY r.timestamp ASC")
    fun observeForConversation(convId: Long): Flow<List<ReactionEntity>>
}
