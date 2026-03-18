package com.privimemobile.chat.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 5,
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

        // --- Migrations (preserve chat history across version bumps) ---

        /** V2→V3: Add tip_asset_id column to messages. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN tip_asset_id INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** V3→V4: Add removed column to reactions (soft-delete for SBBS dedup). */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reactions ADD COLUMN removed INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** V4→V5: Phase B2 — drafts, disappearing messages, message editing. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN draft_text TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE conversations ADD COLUMN disappear_timer INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN edited INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN original_text TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE messages ADD COLUMN expires_at INTEGER NOT NULL DEFAULT 0")
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
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
