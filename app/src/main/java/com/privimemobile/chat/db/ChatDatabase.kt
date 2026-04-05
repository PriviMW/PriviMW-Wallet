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
        PendingTxEntity::class,
    ],
    version = 22,
    exportSchema = false,
)
abstract class ChatDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun contactDao(): ContactDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun reactionDao(): ReactionDao
    abstract fun chatStateDao(): ChatStateDao
    abstract fun groupDao(): GroupDao
    abstract fun pendingTxDao(): PendingTxDao

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

        /** V11→V12: Group chat — rebuild groups/group_members tables with new schema. */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Drop old group tables (schema changed significantly)
                db.execSQL("DROP TABLE IF EXISTS group_members")
                db.execSQL("DROP TABLE IF EXISTS groups")

                // Create new groups table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS groups (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        group_id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT DEFAULT NULL,
                        creator_handle TEXT NOT NULL,
                        is_public INTEGER NOT NULL DEFAULT 0,
                        require_approval INTEGER NOT NULL DEFAULT 0,
                        max_members INTEGER NOT NULL DEFAULT 200,
                        member_count INTEGER NOT NULL DEFAULT 0,
                        default_permissions INTEGER NOT NULL DEFAULT 3,
                        avatar_hash TEXT DEFAULT NULL,
                        my_role INTEGER NOT NULL DEFAULT 0,
                        my_permissions INTEGER NOT NULL DEFAULT 3,
                        created_height INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL DEFAULT 0,
                        last_message_ts INTEGER NOT NULL DEFAULT 0,
                        last_message_preview TEXT DEFAULT NULL,
                        unread_count INTEGER NOT NULL DEFAULT 0,
                        muted INTEGER NOT NULL DEFAULT 0,
                        archived INTEGER NOT NULL DEFAULT 0
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_groups_group_id ON groups (group_id)")

                // Create new group_members table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS group_members (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        group_id TEXT NOT NULL,
                        handle TEXT NOT NULL,
                        display_name TEXT DEFAULT NULL,
                        role INTEGER NOT NULL DEFAULT 0,
                        permissions INTEGER NOT NULL DEFAULT 3,
                        wallet_id TEXT DEFAULT NULL,
                        joined_height INTEGER NOT NULL DEFAULT 0
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_group_members_group_id_handle ON group_members (group_id, handle)")
            }
        }

        /** V12→V13: Pending TX tracking table. */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pending_txs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        tx_id TEXT NOT NULL,
                        action TEXT NOT NULL,
                        target_id TEXT NOT NULL,
                        extra_data TEXT DEFAULT NULL,
                        status INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL DEFAULT 0
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pending_txs_tx_id ON pending_txs (tx_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_txs_action ON pending_txs (action)")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE groups ADD COLUMN join_password TEXT DEFAULT NULL")
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE groups ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contacts ADD COLUMN is_deleted INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN reply_sender TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE messages ADD COLUMN reply_ts INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE contacts ADD COLUMN last_profile_update_ts INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE groups ADD COLUMN last_info_update_ts INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reactions ADD COLUMN notified_at INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** V20→V21: Add first_install_ts to chat_state for reaction notification reinstall dedup. */
        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_state ADD COLUMN first_install_ts INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** V21→V22: Add extras column to attachments for voice message waveform + duration. */
        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE attachments ADD COLUMN extras TEXT DEFAULT NULL")
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
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22)
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
