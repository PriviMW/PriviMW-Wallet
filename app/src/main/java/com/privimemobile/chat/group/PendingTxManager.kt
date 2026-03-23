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
                Log.d(TAG, "TX ${tx.txId.take(8)}... action=${tx.action} status=$status ($statusString)")

                when {
                    // Completed (status 3 in Beam = Completed)
                    status == 3 || statusString.contains("completed", ignoreCase = true) -> {
                        Log.d(TAG, "TX confirmed: ${tx.txId} action=${tx.action}")
                        db.pendingTxDao().deleteByTxId(tx.txId) // delete FIRST to prevent double-processing
                        onTxConfirmed(tx)
                    }
                    // Failed (status 4)
                    status == 4 || statusString.contains("failed", ignoreCase = true) -> {
                        Log.w(TAG, "TX failed: ${tx.txId} action=${tx.action}")
                        db.pendingTxDao().deleteByTxId(tx.txId)
                        onTxFailed(tx)
                    }
                    // Cancelled (status 2)
                    status == 2 || statusString.contains("cancel", ignoreCase = true) -> {
                        Log.w(TAG, "TX cancelled: ${tx.txId} action=${tx.action}")
                        db.pendingTxDao().deleteByTxId(tx.txId)
                        onTxFailed(tx)
                    }
                    // Still pending — check if too old (>1 hour)
                    else -> {
                        val age = System.currentTimeMillis() / 1000 - tx.createdAt
                        if (age > 3600) {
                            Log.w(TAG, "TX expired (>1h): ${tx.txId}")
                            db.pendingTxDao().deleteByTxId(tx.txId)
                            onTxFailed(tx)
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
                // Refresh identity from contract (force — need to get real height)
                ChatService.identity.refreshIdentity(forceRefresh = true)
            }
            PendingTxEntity.ACTION_UPDATE_PROFILE -> {
                // Refresh identity to get updated display name / wallet_id from contract
                ChatService.identity.refreshIdentity(forceRefresh = true)
                // Broadcast profile update to all contacts so they see the new display name
                scope.launch {
                    delay(1000) // wait for refreshIdentity to complete
                    val state = db.chatStateDao().get() ?: return@launch
                    if (state.myHandle != null) {
                        val payload = mapOf(
                            "v" to 1, "t" to "profile_update",
                            "from" to state.myHandle,
                            "display_name" to (state.myDisplayName ?: ""),
                        )
                        // Send to all DM contacts
                        val contacts = db.contactDao().getAll()
                        val sentWalletIds = mutableSetOf<String>()
                        for (contact in contacts) {
                            if (contact.walletId.isNullOrEmpty()) continue
                            try { ChatService.sbbs.sendOnce(contact.walletId, payload) } catch (_: Exception) {}
                            sentWalletIds.add(contact.walletId)
                            delay(100)
                        }
                        // Also send to all group members (so they update cached wallet_id)
                        val groups = db.groupDao().getAllGroups()
                        for (group in groups) {
                            val members = db.groupDao().getMemberWalletIds(group.groupId, state.myHandle)
                            for (wid in members) {
                                if (wid.isNullOrEmpty() || wid in sentWalletIds) continue
                                try { ChatService.sbbs.sendOnce(wid, payload) } catch (_: Exception) {}
                                sentWalletIds.add(wid)
                                delay(100)
                            }
                        }
                    }
                }
            }
            PendingTxEntity.ACTION_CREATE_GROUP -> {
                // Refresh groups to get the new group
                ChatService.groups.refreshMyGroups()
                // Save join password + description (tx.extraData = JSON, tx.targetId = group name)
                if (tx.extraData != null) {
                    try {
                        val extra = org.json.JSONObject(tx.extraData)
                        val password = extra.optString("password").ifEmpty { null }
                        val description = extra.optString("description").ifEmpty { null }
                        val allGroups = db.groupDao().getAll()
                        val newGroup = allGroups.firstOrNull { it.name == tx.targetId }
                        if (newGroup != null) {
                            db.groupDao().updateGroup(newGroup.copy(
                                joinPassword = password ?: newGroup.joinPassword,
                                description = description ?: newGroup.description,
                            ))
                        }
                    } catch (_: Exception) {}
                }
            }
            PendingTxEntity.ACTION_JOIN_GROUP -> {
                // extraData = target handle (for admin-add), null (for self-join)
                val addedHandle = tx.extraData
                ChatService.groups.refreshMyGroups()
                ChatService.groups.refreshGroupMembers(tx.targetId)
                // Notify other members — only AFTER TX confirms
                if (addedHandle != null) {
                    // Admin added someone else
                    scope.launch { ChatService.groups.sendGroupService(tx.targetId, "joined", addedHandle) }
                } else {
                    // Self-join
                    scope.launch { ChatService.groups.sendGroupService(tx.targetId, "joined") }
                }
            }
            PendingTxEntity.ACTION_LEAVE_GROUP -> {
                // Notify other members before removing
                scope.launch { ChatService.groups.sendGroupService(tx.targetId, "left") }
                delay(1000)
                // Remove from local DB
                db.groupDao().deleteByGroupId(tx.targetId)
                db.groupDao().removeAllMembers(tx.targetId)
            }
            PendingTxEntity.ACTION_REMOVE_MEMBER -> {
                ChatService.groups.refreshGroupMembers(tx.targetId)
                ChatService.groups.refreshGroupInfo(tx.targetId)
                // tx.extraData = "ban:handle", "unban:handle", or just "handle"
                if (tx.extraData != null) {
                    val isBan = tx.extraData.startsWith("ban:")
                    val isUnban = tx.extraData.startsWith("unban:")
                    val targetHandle = when {
                        isBan -> tx.extraData.removePrefix("ban:")
                        isUnban -> tx.extraData.removePrefix("unban:")
                        else -> tx.extraData
                    }
                    val action = when {
                        isBan -> "banned"
                        isUnban -> "unbanned"
                        else -> "kicked"
                    }
                    scope.launch { ChatService.groups.sendGroupService(tx.targetId, action, targetHandle) }
                }
            }
            PendingTxEntity.ACTION_SET_ROLE -> {
                ChatService.groups.refreshGroupMembers(tx.targetId)
                ChatService.groups.refreshGroupInfo(tx.targetId)
                // tx.extraData = target handle. Determine promote vs demote from refreshed data.
                if (tx.extraData != null) {
                    val member = db.groupDao().findMember(tx.targetId, tx.extraData)
                    val action = if (member?.role == 1) "promoted" else "demoted"
                    scope.launch { ChatService.groups.sendGroupService(tx.targetId, action, tx.extraData) }
                }
            }
            PendingTxEntity.ACTION_TRANSFER_OWNERSHIP -> {
                ChatService.groups.refreshGroupMembers(tx.targetId)
                ChatService.groups.refreshGroupInfo(tx.targetId)
                if (tx.extraData != null) {
                    scope.launch { ChatService.groups.sendGroupService(tx.targetId, "ownership_transferred", tx.extraData) }
                }
            }
            PendingTxEntity.ACTION_UPDATE_GROUP_INFO -> {
                ChatService.groups.refreshGroupInfo(tx.targetId)
                // Broadcast name change to members if name was updated
                if (tx.extraData?.startsWith("name:") == true) {
                    val newName = tx.extraData.removePrefix("name:")
                    val state = db.chatStateDao().get()
                    val myHandle = state?.myHandle ?: "admin"
                    // Update local DB
                    val group = db.groupDao().findByGroupId(tx.targetId)
                    if (group != null) {
                        db.groupDao().updateGroup(group.copy(name = newName))
                    }
                    // Insert local service message
                    val ts = System.currentTimeMillis() / 1000
                    val convId = ChatService.groups.getOrCreateGroupConversation(tx.targetId, newName)
                    val dedupKey = "$ts:info_update:name:${tx.targetId}".hashCode().toString(16)
                    db.messageDao().insert(com.privimemobile.chat.db.entities.MessageEntity(
                        conversationId = convId, timestamp = ts,
                        senderHandle = myHandle, text = "You changed the group name to \"$newName\"",
                        type = "group_service", sent = true, sbbsDedupKey = dedupKey,
                    ))
                    db.groupDao().updateLastMessage(tx.targetId, ts, "Group name changed to \"$newName\"")
                    // Broadcast to members
                    scope.launch {
                        ChatService.groups.sendGroupPayload(tx.targetId, mapOf(
                            "v" to 1, "t" to "group_info_update",
                            "ts" to ts, "name" to newName,
                        ))
                    }
                }
            }
            PendingTxEntity.ACTION_RELEASE_HANDLE -> {
                // Handle was deleted on-chain — clear local identity
                Log.d(TAG, "ACTION_RELEASE_HANDLE confirmed — clearing identity...")
                db.chatStateDao().clearIdentity()
                // Also clear SBBS flag so we don't show re-register landing
                ChatService.identity.clearSbbsNeedsUpdate()
                Log.d(TAG, "Handle released — identity cleared, UI should show 'Not Registered'")
            }
            PendingTxEntity.ACTION_DELETE_GROUP -> {
                // Notify all members before deleting
                scope.launch { ChatService.groups.sendGroupService(tx.targetId, "group_deleted") }
                kotlinx.coroutines.delay(2000) // wait for SBBS to send
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
                if (tx.extraData == null) {
                    // Self-join failed — remove the group we optimistically added
                    db.groupDao().deleteByGroupId(tx.targetId)
                }
                // Admin-add failed (extraData = target handle) — don't touch the group,
                // just refresh to get correct member list
                ChatService.groups.refreshGroupMembers(tx.targetId)
            }
            PendingTxEntity.ACTION_UPDATE_PROFILE -> {
                // Revert optimistic wallet_id update if we stored the old address
                if (tx.extraData?.startsWith("addr:") == true) {
                    val oldAddr = tx.extraData.removePrefix("addr:")
                    val state = db.chatStateDao().get()
                    if (state != null) {
                        db.chatStateDao().update(state.copy(myWalletId = oldAddr))
                        Log.d(TAG, "Reverted messaging address to $oldAddr")
                    }
                }
                // Refresh identity from contract to get correct state
                ChatService.identity.refreshIdentity(forceRefresh = true)
            }
            PendingTxEntity.ACTION_RELEASE_HANDLE -> {
                // TX failed — identity was NOT cleared (we wait for confirm now), nothing to revert
                Log.d(TAG, "Release handle TX failed — identity unchanged")
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
