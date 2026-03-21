package com.privimemobile.chat.group

import android.util.Log
import com.privimemobile.chat.ChatService
import com.privimemobile.chat.db.ChatDatabase
import com.privimemobile.chat.db.entities.PendingTxEntity
import com.privimemobile.protocol.WalletApi
import kotlinx.coroutines.*
import kotlinx.coroutines.delay

/**
 * PendingTxManager — tracks on-chain TXs and gates features until confirmed.
 *
 * Called on ev_txs_changed events to check pending TX statuses.
 * When confirmed: executes post-confirm actions (refresh groups, enable features).
 * When failed/cancelled: reverts optimistic state.
 */
class PendingTxManager(
    private val db: ChatDatabase,
    private val scope: CoroutineScope,
) {
    private val TAG = "PendingTxManager"

    /**
     * Record a new pending TX.
     * Called after process_invoke_data returns a txId.
     */
    suspend fun trackTx(txId: String, action: String, targetId: String, extraData: String? = null) {
        db.pendingTxDao().insert(PendingTxEntity(
            txId = txId,
            action = action,
            targetId = targetId,
            extraData = extraData,
        ))
        Log.d(TAG, "Tracking TX: $txId action=$action target=$targetId")
    }

    /**
     * Check if a specific action+target is pending.
     */
    suspend fun isPending(action: String, targetId: String): Boolean {
        return db.pendingTxDao().isPending(action, targetId) > 0
    }

    /**
     * Called on ev_txs_changed — checks all pending TXs.
     */
    fun onTxsChanged() {
        scope.launch {
            checkPendingTxs()
        }
    }

    /**
     * Check all pending TXs against wallet TX status.
     */
    suspend fun checkPendingTxs() {
        val pending = db.pendingTxDao().getAllPending()
        if (pending.isEmpty()) return

        Log.d(TAG, "Checking ${pending.size} pending TXs")

        for (tx in pending) {
            try {
                val result = WalletApi.callAsync("tx_status", mapOf("txId" to tx.txId))
                val status = (result["status"] as? Number)?.toInt()
                val statusString = result["status_string"] as? String ?: ""

                when {
                    // Completed (status 3 in Beam = Completed)
                    status == 3 || statusString.contains("completed", ignoreCase = true) -> {
                        Log.d(TAG, "TX confirmed: ${tx.txId} action=${tx.action}")
                        onTxConfirmed(tx)
                        db.pendingTxDao().deleteByTxId(tx.txId)
                    }
                    // Failed (status 4)
                    status == 4 || statusString.contains("failed", ignoreCase = true) -> {
                        Log.w(TAG, "TX failed: ${tx.txId} action=${tx.action}")
                        onTxFailed(tx)
                        db.pendingTxDao().deleteByTxId(tx.txId)
                    }
                    // Cancelled (status 2)
                    status == 2 || statusString.contains("cancel", ignoreCase = true) -> {
                        Log.w(TAG, "TX cancelled: ${tx.txId} action=${tx.action}")
                        onTxFailed(tx)
                        db.pendingTxDao().deleteByTxId(tx.txId)
                    }
                    // Still pending — check if too old (>1 hour)
                    else -> {
                        val age = System.currentTimeMillis() / 1000 - tx.createdAt
                        if (age > 3600) {
                            Log.w(TAG, "TX expired (>1h): ${tx.txId}")
                            onTxFailed(tx)
                            db.pendingTxDao().deleteByTxId(tx.txId)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking TX ${tx.txId}: ${e.message}")
            }
        }
    }

    /**
     * Post-confirm actions — execute when TX is mined on-chain.
     */
    private suspend fun onTxConfirmed(tx: PendingTxEntity) {
        when (tx.action) {
            PendingTxEntity.ACTION_REGISTER_HANDLE -> {
                // Refresh identity from contract
                ChatService.identity.refreshIdentity()
            }
            PendingTxEntity.ACTION_UPDATE_PROFILE -> {
                // Refresh identity to confirm profile update
                ChatService.identity.refreshIdentity()
            }
            PendingTxEntity.ACTION_CREATE_GROUP -> {
                // Refresh groups to get the new group
                ChatService.groups.refreshMyGroups()
                // Mark group as not pending
                db.groupDao().findByGroupId(tx.targetId)?.let { group ->
                    db.groupDao().updateGroup(group.copy(/* pending would be cleared */))
                }
            }
            PendingTxEntity.ACTION_JOIN_GROUP -> {
                // Refresh groups + members
                ChatService.groups.refreshMyGroups()
                ChatService.groups.refreshGroupMembers(tx.targetId)
                // Notify other members
                scope.launch { ChatService.groups.sendGroupService(tx.targetId, "joined") }
            }
            PendingTxEntity.ACTION_LEAVE_GROUP -> {
                // Notify other members before removing
                scope.launch { ChatService.groups.sendGroupService(tx.targetId, "left") }
                delay(1000)
                // Remove from local DB
                db.groupDao().deleteByGroupId(tx.targetId)
                db.groupDao().removeAllMembers(tx.targetId)
            }
            PendingTxEntity.ACTION_REMOVE_MEMBER,
            PendingTxEntity.ACTION_SET_ROLE,
            PendingTxEntity.ACTION_TRANSFER_OWNERSHIP -> {
                // Refresh members
                ChatService.groups.refreshGroupMembers(tx.targetId)
                ChatService.groups.refreshGroupInfo(tx.targetId)
            }
            PendingTxEntity.ACTION_DELETE_GROUP -> {
                // Remove from local DB
                db.groupDao().deleteByGroupId(tx.targetId)
                db.groupDao().removeAllMembers(tx.targetId)
            }
        }
    }

    /**
     * Revert actions — execute when TX fails or is cancelled.
     */
    private suspend fun onTxFailed(tx: PendingTxEntity) {
        when (tx.action) {
            PendingTxEntity.ACTION_CREATE_GROUP -> {
                // Remove the optimistically-added group
                db.groupDao().deleteByGroupId(tx.targetId)
            }
            PendingTxEntity.ACTION_JOIN_GROUP -> {
                // Remove from local DB (never actually joined)
                db.groupDao().deleteByGroupId(tx.targetId)
            }
            // Other failures: just refresh to get correct state
            else -> {
                if (tx.targetId.length == 64) { // group_id
                    ChatService.groups.refreshGroupInfo(tx.targetId)
                    ChatService.groups.refreshGroupMembers(tx.targetId)
                }
            }
        }
    }
}
