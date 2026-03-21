package com.privimemobile.chat.db.entities

import androidx.room.*

/**
 * Tracks on-chain transactions that must confirm before a feature is usable.
 *
 * Flow:
 * 1. TX submitted → insert PendingTxEntity (status = PENDING)
 * 2. ev_txs_changed → check TX status via wallet API
 * 3. Confirmed → execute post-confirm action, delete entry
 * 4. Failed/Cancelled → revert optimistic state, delete entry
 */
@Entity(
    tableName = "pending_txs",
    indices = [
        Index(value = ["tx_id"], unique = true),
        Index(value = ["action"]),
    ]
)
data class PendingTxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "tx_id") val txId: String,           // Beam TX ID from process_invoke_data
    val action: String,                                      // e.g. "create_group", "join_group", "register_handle"
    @ColumnInfo(name = "target_id") val targetId: String,    // group_id, handle, etc.
    @ColumnInfo(name = "extra_data") val extraData: String? = null, // JSON for additional context
    val status: Int = STATUS_PENDING,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis() / 1000,
) {
    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_CONFIRMED = 1
        const val STATUS_FAILED = 2
        const val STATUS_CANCELLED = 3

        // Action constants
        const val ACTION_REGISTER_HANDLE = "register_handle"
        const val ACTION_UPDATE_PROFILE = "update_profile"
        const val ACTION_CREATE_GROUP = "create_group"
        const val ACTION_JOIN_GROUP = "join_group"
        const val ACTION_LEAVE_GROUP = "leave_group"
        const val ACTION_REMOVE_MEMBER = "remove_member"
        const val ACTION_SET_ROLE = "set_role"
        const val ACTION_TRANSFER_OWNERSHIP = "transfer_ownership"
        const val ACTION_UPDATE_GROUP_INFO = "update_group_info"
        const val ACTION_DELETE_GROUP = "delete_group"
        const val ACTION_RELEASE_HANDLE = "release_handle"
    }
}
