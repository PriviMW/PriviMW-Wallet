package com.privimemobile.chat.db.dao

import androidx.room.*
import com.privimemobile.chat.db.entities.PendingTxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingTxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tx: PendingTxEntity): Long

    @Query("SELECT * FROM pending_txs WHERE tx_id = :txId LIMIT 1")
    suspend fun findByTxId(txId: String): PendingTxEntity?

    @Query("SELECT * FROM pending_txs WHERE action = :action AND target_id = :targetId LIMIT 1")
    suspend fun findByActionAndTarget(action: String, targetId: String): PendingTxEntity?

    @Query("SELECT * FROM pending_txs WHERE status = 0")
    suspend fun getAllPending(): List<PendingTxEntity>

    @Query("SELECT * FROM pending_txs WHERE status = 0")
    fun observePending(): Flow<List<PendingTxEntity>>

    @Query("SELECT COUNT(*) FROM pending_txs WHERE status = 0 AND action = :action AND target_id = :targetId")
    suspend fun isPending(action: String, targetId: String): Int

    @Query("UPDATE pending_txs SET status = :status WHERE tx_id = :txId")
    suspend fun updateStatus(txId: String, status: Int)

    @Query("DELETE FROM pending_txs WHERE tx_id = :txId")
    suspend fun deleteByTxId(txId: String)

    @Query("DELETE FROM pending_txs WHERE status != 0")
    suspend fun deleteCompleted()

    @Query("DELETE FROM pending_txs WHERE created_at < :before")
    suspend fun deleteOlderThan(before: Long)
}
