package com.privimemobile.chat.db.dao

import androidx.room.*
import com.privimemobile.chat.db.entities.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    /** All contacts sorted by handle. */
    @Query("SELECT * FROM contacts ORDER BY handle ASC")
    fun observeAll(): Flow<List<ContactEntity>>

    /** Find by handle. */
    @Query("SELECT * FROM contacts WHERE handle = :handle LIMIT 1")
    suspend fun findByHandle(handle: String): ContactEntity?

    /** Find by wallet_id. */
    @Query("SELECT * FROM contacts WHERE wallet_id = :walletId LIMIT 1")
    suspend fun findByWalletId(walletId: String): ContactEntity?

    /** Search contacts by handle or display_name prefix (for NewChatScreen). */
    @Query("""
        SELECT * FROM contacts
        WHERE handle LIKE :query || '%' OR display_name LIKE :query || '%'
        ORDER BY handle ASC
        LIMIT 50
    """)
    suspend fun search(query: String): List<ContactEntity>

    /** Insert or ignore. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(contact: ContactEntity): Long

    @Update
    suspend fun update(contact: ContactEntity)

    /** Upsert: insert or update on handle conflict. */
    @Transaction
    suspend fun upsert(contact: ContactEntity) {
        val existing = findByHandle(contact.handle)
        if (existing != null) {
            update(contact.copy(id = existing.id))
        } else {
            insert(contact)
        }
    }

    /** Update resolved wallet_id + display_name for a handle. */
    @Query("""
        UPDATE contacts
        SET wallet_id = :walletId, display_name = :displayName, avatar_cid = :avatarCid,
            registered_height = :registeredHeight, last_resolved_at = :resolvedAt
        WHERE handle = :handle
    """)
    suspend fun updateResolved(
        handle: String,
        walletId: String?,
        displayName: String?,
        avatarCid: String?,
        registeredHeight: Long,
        resolvedAt: Long = System.currentTimeMillis() / 1000,
    )

    /** Get contacts that need resolution (no wallet_id or stale). */
    @Query("SELECT * FROM contacts WHERE wallet_id IS NULL OR last_resolved_at < :staleThreshold")
    suspend fun getUnresolved(staleThreshold: Long): List<ContactEntity>
}
