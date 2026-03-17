package com.privimemobile.chat.contacts

import android.util.Log
import com.privimemobile.chat.db.ChatDatabase
import com.privimemobile.chat.db.entities.ContactEntity
import com.privimemobile.protocol.Helpers
import com.privimemobile.protocol.ShaderInvoker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * ContactManager — resolves handles ↔ wallet_ids, caches in DB.
 *
 * Features:
 * - resolve_handle: @handle → wallet_id + display_name
 * - resolve_walletid: wallet_id → @handle + display_name
 * - Re-resolve on chat open (5min cooldown)
 * - Batch resolution for unresolved contacts
 */
class ContactManager(
    private val db: ChatDatabase,
    private val scope: CoroutineScope,
) {
    private val TAG = "ContactManager"
    private val RESOLVE_COOLDOWN_MS = 5 * 60 * 1000L  // 5 minutes

    /**
     * Ensure a contact exists in DB. If new, queue for async resolution.
     * Called by MessageProcessor when a new sender is encountered.
     */
    fun ensureContact(handle: String, displayName: String?, walletId: String?) {
        scope.launch {
            val existing = db.contactDao().findByHandle(handle)
            if (existing != null) {
                // Update display name if we have a newer one
                if (displayName != null && displayName != existing.displayName) {
                    db.contactDao().update(existing.copy(displayName = displayName))
                }
                // Re-resolve if wallet_id is missing
                if (existing.walletId == null && walletId != null) {
                    db.contactDao().update(existing.copy(walletId = Helpers.normalizeWalletId(walletId)))
                }
                return@launch
            }

            // Insert new contact
            db.contactDao().insert(ContactEntity(
                handle = handle,
                walletId = Helpers.normalizeWalletId(walletId ?: ""),
                displayName = displayName,
            ))

            // If no wallet_id, resolve from contract
            if (walletId == null) {
                resolveHandle(handle)
            }
        }
    }

    /**
     * Resolve a handle from the contract.
     * Updates contact DB + conversation display info.
     */
    suspend fun resolveHandle(handle: String): ContactEntity? {
        try {
            val result = ShaderInvoker.invokeAsync("user", "resolve_handle", mapOf("handle" to handle))
            if (result.containsKey("error")) {
                Log.w(TAG, "resolve_handle($handle) error: ${result["error"]}")
                return null
            }

            val walletId = Helpers.normalizeWalletId(result["wallet_id"] as? String ?: "")
            val displayName = Helpers.fixBvmUtf8(result["display_name"] as? String)
            val avatarCid = result["avatar_cid"] as? String
            val height = (result["registered_height"] as? Number)?.toLong() ?: 0

            if (walletId == null) return null

            // Update contact in DB
            db.contactDao().updateResolved(handle, walletId, displayName, avatarCid, height)

            // Also update conversation display info
            db.conversationDao().updateContactInfo("@$handle", displayName, walletId, avatarCid)

            return db.contactDao().findByHandle(handle)
        } catch (e: Exception) {
            Log.e(TAG, "resolveHandle($handle) failed: ${e.message}")
            return null
        }
    }

    /**
     * Re-resolve on chat open — ensures latest wallet_id (recipient may have updated).
     * Respects 5-minute cooldown.
     */
    fun reResolveOnChatOpen(handle: String) {
        scope.launch {
            val contact = db.contactDao().findByHandle(handle) ?: return@launch
            val now = System.currentTimeMillis() / 1000
            if (now - contact.lastResolvedAt < RESOLVE_COOLDOWN_MS / 1000) return@launch
            resolveHandle(handle)
        }
    }

    /**
     * Resolve a wallet_id to a contact.
     */
    suspend fun resolveWalletId(walletId: String): ContactEntity? {
        try {
            val normalized = Helpers.normalizeWalletId(walletId) ?: return null
            val result = ShaderInvoker.invokeAsync("user", "resolve_walletid", mapOf("walletid" to normalized))
            if (result.containsKey("error")) return null

            val handle = result["handle"] as? String ?: return null
            val displayName = Helpers.fixBvmUtf8(result["display_name"] as? String)
            val avatarCid = result["avatar_cid"] as? String
            val height = (result["registered_height"] as? Number)?.toLong() ?: 0

            db.contactDao().upsert(ContactEntity(
                handle = handle,
                walletId = normalized,
                displayName = displayName,
                avatarCid = avatarCid,
                registeredHeight = height,
                lastResolvedAt = System.currentTimeMillis() / 1000,
            ))

            return db.contactDao().findByHandle(handle)
        } catch (e: Exception) {
            Log.e(TAG, "resolveWalletId failed: ${e.message}")
            return null
        }
    }

    /**
     * Search contacts locally (for NewChatScreen).
     * Filters by handle or display_name prefix from 1st character.
     */
    suspend fun searchLocal(query: String): List<ContactEntity> {
        return db.contactDao().search(query.lowercase())
    }

    /**
     * Batch-resolve all contacts with missing wallet_ids.
     */
    fun resolveUnresolved() {
        scope.launch {
            val staleThreshold = (System.currentTimeMillis() / 1000) - (RESOLVE_COOLDOWN_MS / 1000)
            val unresolved = db.contactDao().getUnresolved(staleThreshold)
            for (contact in unresolved) {
                resolveHandle(contact.handle)
            }
        }
    }
}
