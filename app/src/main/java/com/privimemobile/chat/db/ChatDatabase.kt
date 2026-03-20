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
    version = 11,
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

        /** V5→V6: Pinned message timestamp on conversations. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN pinned_message_ts INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** V6→V7: Pinned flag on messages (multi-pin support). */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** V7→V8: Pin ordering (pinned_at timestamp). */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN pinned_at INTEGER NOT NULL DEFAULT 0")
                // Backfill: existing pinned messages get pinned_at = their message timestamp
                db.execSQL("UPDATE messages SET pinned_at = timestamp WHERE pinned = 1")
            }
        }

        /** V8→V9: Archived conversations, poll data. */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN poll_data TEXT DEFAULT NULL")
            }
        }

        /** V9→V10: Message scheduling. */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN scheduled_at INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** V10→V11: Sticker pack metadata on messages. */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN sticker_pack_name TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE messages ADD COLUMN sticker_pack_id TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE messages ADD COLUMN sticker_emoji TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE messages ADD COLUMN sticker_pack_total INTEGER NOT NULL DEFAULT 0")
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
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
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
