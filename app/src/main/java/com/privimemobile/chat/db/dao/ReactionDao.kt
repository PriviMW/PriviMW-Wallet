package com.privimemobile.chat.db.dao

import androidx.room.*
import com.privimemobile.chat.db.entities.ReactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReactionDao {

    /** Observe reactions for a specific message. */
    @Query("SELECT * FROM reactions WHERE message_ts = :messageTs ORDER BY timestamp ASC")
    fun observeForMessage(messageTs: Long): Flow<List<ReactionEntity>>

    /** Insert — ignore duplicate (same message_ts + sender + emoji). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(reaction: ReactionEntity): Long

    /** Remove a reaction. */
    @Query("DELETE FROM reactions WHERE message_ts = :messageTs AND sender_handle = :senderHandle AND emoji = :emoji")
    suspend fun remove(messageTs: Long, senderHandle: String, emoji: String)
}
