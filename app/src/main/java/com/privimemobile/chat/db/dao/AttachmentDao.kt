package com.privimemobile.chat.db.dao

import androidx.room.*
import com.privimemobile.chat.db.entities.AttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {

    /** Get attachment for a message. */
    @Query("SELECT * FROM attachments WHERE message_id = :messageId LIMIT 1")
    suspend fun findByMessageId(messageId: Long): AttachmentEntity?

    /** Observe attachment for a message (download status changes). */
    @Query("SELECT * FROM attachments WHERE message_id = :messageId LIMIT 1")
    fun observeByMessageId(messageId: Long): Flow<AttachmentEntity?>

    /** Media gallery: all image attachments in a conversation. */
    @Query("""
        SELECT * FROM attachments
        WHERE conversation_id = :convId AND mime_type LIKE 'image/%'
        ORDER BY rowid DESC
    """)
    fun observeImages(convId: Long): Flow<List<AttachmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: AttachmentEntity): Long

    @Update
    suspend fun update(attachment: AttachmentEntity)

    /** Update download status. */
    @Query("UPDATE attachments SET download_status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    /** Update local path after download. */
    @Query("UPDATE attachments SET local_path = :path, download_status = 'done' WHERE id = :id")
    suspend fun setDownloaded(id: Long, path: String)

    /** Find by IPFS CID (dedup check). */
    @Query("SELECT * FROM attachments WHERE ipfs_cid = :cid LIMIT 1")
    suspend fun findByCid(cid: String): AttachmentEntity?

    /** Count cached files (for FIFO eviction). */
    @Query("SELECT COUNT(*) FROM attachments WHERE local_path IS NOT NULL")
    suspend fun countCached(): Int

    /** Get oldest cached attachment (for eviction). */
    @Query("SELECT * FROM attachments WHERE local_path IS NOT NULL ORDER BY rowid ASC LIMIT 1")
    suspend fun getOldestCached(): AttachmentEntity?

    /** Count all attachments in a conversation (for contact info). */
    @Query("SELECT COUNT(*) FROM attachments WHERE conversation_id = :convId")
    suspend fun countByConversation(convId: Long): Int

    /** Count image attachments in a conversation (for contact info). */
    @Query("SELECT COUNT(*) FROM attachments WHERE conversation_id = :convId AND mime_type LIKE 'image/%'")
    suspend fun countImagesByConversation(convId: Long): Int
}
