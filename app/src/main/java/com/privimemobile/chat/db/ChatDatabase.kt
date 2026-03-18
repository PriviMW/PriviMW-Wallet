package com.privimemobile.chat.db

import android.content.Context
import androidx.room.*
import com.privimemobile.chat.db.dao.*
import com.privimemobile.chat.db.entities.*
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        MessageEntity::class,
        MessageFts::class,
        ConversationEntity::class,
        ContactEntity::class,
        AttachmentEntity::class,
        ReactionEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        ChatStateEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class ChatDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun contactDao(): ContactDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun reactionDao(): ReactionDao
    abstract fun chatStateDao(): ChatStateDao

    companion object {
        @Volatile
        private var INSTANCE: ChatDatabase? = null
        private const val DB_NAME = "privime_chat.db"

        fun getInstance(context: Context, passphrase: ByteArray): ChatDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context, passphrase).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context, passphrase: ByteArray): ChatDatabase {
            val factory = SupportFactory(passphrase)
            return Room.databaseBuilder(
                context.applicationContext,
                ChatDatabase::class.java,
                DB_NAME
            )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
        }

        /** Close and release the singleton (used on wallet deletion). */
        fun close() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
