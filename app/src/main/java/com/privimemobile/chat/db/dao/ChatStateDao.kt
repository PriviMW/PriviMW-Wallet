package com.privimemobile.chat.db.dao

import androidx.room.*
import com.privimemobile.chat.db.entities.ChatStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatStateDao {

    /** Observe chat state (identity, registration fee, etc). */
    @Query("SELECT * FROM chat_state WHERE id = 1 LIMIT 1")
    fun observe(): Flow<ChatStateEntity?>

    /** Get current state. */
    @Query("SELECT * FROM chat_state WHERE id = 1 LIMIT 1")
    suspend fun get(): ChatStateEntity?

    /** Insert default state if not exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(state: ChatStateEntity)

    @Update
    suspend fun update(state: ChatStateEntity)

    /** Initialize state — insert default row if empty, set first_install_ts on first run. */
    @Transaction
    suspend fun ensureInitialized(): ChatStateEntity {
        val existing = get()
        if (existing != null) {
            // Set first_install_ts on first ever run of this version (column added in v21)
            if (existing.firstInstallTs == 0L) {
                update(existing.copy(firstInstallTs = System.currentTimeMillis() / 1000))
            }
            return existing
        }
        val default = ChatStateEntity(firstInstallTs = System.currentTimeMillis() / 1000)
        insert(default)
        return default
    }

    /** Update identity after registration. */
    @Query("""
        UPDATE chat_state
        SET my_handle = :handle, my_wallet_id = :walletId, my_display_name = :displayName,
            my_registered_height = :height
        WHERE id = 1
    """)
    suspend fun updateIdentity(handle: String, walletId: String, displayName: String?, height: Long)

    /** Set contractStartTs (one-time, on first registration). */
    @Query("UPDATE chat_state SET contract_start_ts = :ts WHERE id = 1 AND contract_start_ts = 0")
    suspend fun setContractStartTs(ts: Long)

    /** Update registration fee. */
    @Query("UPDATE chat_state SET registration_fee = :fee WHERE id = 1")
    suspend fun updateRegistrationFee(fee: Long)

    /** Update avatar hash (stored in my_avatar_cid column). */
    @Query("UPDATE chat_state SET my_avatar_cid = :hash WHERE id = 1")
    suspend fun updateAvatarHash(hash: String?)

    /** Clear identity (handle released). */
    @Query("""
        UPDATE chat_state
        SET my_handle = NULL, my_wallet_id = NULL, my_display_name = NULL,
            my_registered_height = 0, my_avatar_cid = NULL
        WHERE id = 1
    """)
    suspend fun clearIdentity()
}
