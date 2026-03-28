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

    /** Get all contacts (non-Flow, for iteration). */
    @Query("SELECT * FROM contacts")
    suspend fun getAll(): List<ContactEntity>

    /** Update avatar hash for a contact. */
    @Query("UPDATE contacts SET avatar_cid = :avatarHash WHERE handle = :handle")
    suspend fun updateAvatarHash(handle: String, avatarHash: String?)

    /** Update profile update timestamp and avatar hash (for profile_update deduplication). */
    @Query("UPDATE contacts SET avatar_cid = :avatarHash, last_profile_update_ts = :timestamp WHERE handle = :handle")
    suspend fun updateProfileUpdate(handle: String, avatarHash: String?, timestamp: Long)

    /** Update display name for a contact. */
    @Query("UPDATE contacts SET display_name = :displayName WHERE handle = :handle")
    suspend fun updateDisplayName(handle: String, displayName: String?)

    /** Mark a contact as deleted (handle no longer exists on-chain). */
    @Query("UPDATE contacts SET is_deleted = 1, display_name = 'Deleted Account' WHERE handle = :handle")
    suspend fun markDeleted(handle: String)

    /** Get contacts that need resolution (no wallet_id or stale). */
    @Query("SELECT * FROM contacts WHERE wallet_id IS NULL OR last_resolved_at < :staleThreshold")
    suspend fun getUnresolved(staleThreshold: Long): List<ContactEntity>

    /**
     * Contacts that have an active (non-deleted, non-group) DM conversation.
     * This is "Your Contacts" — only people you currently have a DM open with.
     */
    @Query("""
        SELECT c.* FROM contacts c
        INNER JOIN conversations conv ON conv.conv_key = '@' || c.handle
        WHERE conv.deleted_at_ts = 0 AND conv.is_group = 0
        ORDER BY c.handle ASC
    """)
    fun observeDmContacts(): Flow<List<ContactEntity>>

    /** Hard-delete a contact by handle. */
    @Query("DELETE FROM contacts WHERE handle = :handle")
    suspend fun deleteByHandle(handle: String)
}
