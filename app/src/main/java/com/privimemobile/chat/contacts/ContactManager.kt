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
     *
     * Writes sbbsAddress (SBBS channel) from the envelope sender.
     * The walletId (on-chain) is resolved separately by resolveHandle.
     */
    fun ensureContact(handle: String, displayName: String?, walletId: String?) {
        scope.launch {
            val normalized = Helpers.normalizeWalletId(walletId ?: "")
            val existing = db.contactDao().findByHandle(handle)
            if (existing != null) {
                // Update display name if we have a newer one
                if (displayName != null && displayName != existing.displayName) {
                    db.contactDao().update(existing.copy(displayName = displayName))
                    // Also update conversation display info
                    db.conversationDao().updateContactInfo("@$handle", displayName, null, null)
                }
                // Update sbbs_address from SBBS envelope (per-conversation channel)
                if (normalized != null && existing.sbbsAddress != normalized) {
                    db.contactDao().updateSbbsAddress(handle, normalized)
                }
            } else {
                // Insert new contact — sbbsAddress from envelope, walletId from contract (resolved later)
                db.contactDao().insert(ContactEntity(
                    handle = handle,
                    sbbsAddress = normalized,
                    displayName = displayName,
                ))
            }

            // Request avatar if no cached file exists
            if (normalized != null) {
                val filesDir = com.privimemobile.chat.transport.IpfsTransport.filesDir
                if (filesDir != null) {
                    val avatarFile = java.io.File(filesDir, "avatars/$handle.webp")
                    if (!avatarFile.exists()) {
                        requestAvatar(handle, normalized)
                    }
                }
            }
        }
    }

    /**
     * Resolve a handle from the contract.
     * Updates contact DB + conversation display info.
     */
    suspend fun resolveHandle(handle: String, requestAvatarIfMissing: Boolean = true): ContactEntity? {
        try {
            val result = ShaderInvoker.invokeAsync("user", "resolve_handle", mapOf("handle" to handle))
            if (result.containsKey("error")) {
                Log.w(TAG, "resolve_handle($handle) error: ${result["error"]}")
                // Check if this is a known contact — if so, their handle was deleted
                val existing = db.contactDao().findByHandle(handle)
                if (existing != null && !existing.isDeleted) {
                    db.contactDao().markDeleted(handle)
                    db.conversationDao().updateDisplayName("@$handle", "Deleted Account")
                    Log.d(TAG, "Marked @$handle as deleted account")
                } else if (existing == null) {
                    // Contact never existed locally — insert as deleted
                    db.contactDao().insert(ContactEntity(
                        handle = handle, displayName = "Deleted Account", isDeleted = true,
                    ))
                    Log.d(TAG, "Inserted @$handle as deleted account (never existed locally)")
                }
                return null
            }

            val walletId = Helpers.normalizeWalletId(result["wallet_id"] as? String ?: "")
            val displayName = Helpers.fixBvmUtf8(result["display_name"] as? String)
            val avatarHash: String? = null // Avatar is SBBS-only, not from contract
            val height = (result["registered_height"] as? Number)?.toLong() ?: 0

            if (walletId == null) return null

            // Upsert contact in DB (insert if not exists, update if exists)
            val existing = db.contactDao().findByHandle(handle)
            if (existing != null) {
                db.contactDao().updateResolved(handle, walletId, displayName, avatarHash, height)
            } else {
                db.contactDao().insert(ContactEntity(
                    handle = handle,
                    walletId = walletId,
                    displayName = displayName,
                    avatarCid = avatarHash,
                    registeredHeight = height,
                    lastResolvedAt = System.currentTimeMillis() / 1000,
                ))
            }

            // Also update conversation display info
            db.conversationDao().updateContactInfo("@$handle", displayName, walletId, avatarHash)

            // Request avatar if no cached file exists (avatar is SBBS-only, no on-chain hash)
            if (requestAvatarIfMissing) {
                val filesDir = com.privimemobile.chat.transport.IpfsTransport.filesDir
                if (filesDir != null) {
                    val avatarFile = java.io.File(filesDir, "avatars/$handle.webp")
                    if (!avatarFile.exists()) {
                        requestAvatar(handle, walletId)
                    }
                }
            }

            return db.contactDao().findByHandle(handle)
        } catch (e: Exception) {
            Log.e(TAG, "resolveHandle($handle) failed: ${e.message}")
            return null
        }
    }

   /**
     * Re-resolve on chat open — ensures latest wallet_id (recipient may have updated).
     * Respects 5-minute cooldown for refresh, but ALWAYS resolves if wallet_id is missing.
     */
    fun reResolveOnChatOpen(handle: String) {
        scope.launch {
            val contact = db.contactDao().findByHandle(handle) ?: return@launch

            // Always check for missing avatar on chat open (no cooldown)
            val filesDir = com.privimemobile.chat.transport.IpfsTransport.filesDir
            val avatarAddr = contact.sbbsAddress ?: contact.walletId
            if (filesDir != null && !avatarAddr.isNullOrEmpty()) {
                val avatarFile = java.io.File(filesDir, "avatars/$handle.webp")
                if (!avatarFile.exists()) {
                    requestAvatar(handle, avatarAddr)
                }
            }

            // If wallet_id is missing, resolve immediately (no cooldown) — needed for /tip
            if (contact.walletId.isNullOrEmpty()) {
                resolveHandle(handle)
                return@launch
            }

            // If sbbs_address is missing, resolve to get the on-chain wallet_id as initial send address
            if (contact.sbbsAddress.isNullOrEmpty()) {
                resolveHandle(handle)
                return@launch
            }

            // Resolve handle (with cooldown) to refresh wallet_id/display name
            val now = System.currentTimeMillis() / 1000
            if (now - contact.lastResolvedAt < RESOLVE_COOLDOWN_MS / 1000) return@launch
            resolveHandle(handle)
        }
    }

    /**
     * Resolve a wallet_id to a contact.
     * Writes walletId (on-chain) only. sbbsAddress is filled by ensureContact from SBBS envelope.
     */
    suspend fun resolveWalletId(walletId: String): ContactEntity? {
        try {
            val normalized = Helpers.normalizeWalletId(walletId) ?: return null
            val result = ShaderInvoker.invokeAsync("user", "resolve_walletid", mapOf("walletid" to normalized))
            if (result.containsKey("error")) return null

            val handle = result["handle"] as? String ?: return null
            val displayName = Helpers.fixBvmUtf8(result["display_name"] as? String)
            val avatarHash: String? = null // Avatar is SBBS-only, not from contract
            val height = (result["registered_height"] as? Number)?.toLong() ?: 0

            db.contactDao().upsert(ContactEntity(
                handle = handle,
                walletId = normalized,
                displayName = displayName,
                avatarCid = avatarHash,
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
     * Search handles on-chain by prefix (1+ chars).
     * Returns up to 20 results from the contract's search_handles method.
     */
    suspend fun searchOnChain(prefix: String): List<ContactEntity> {
        try {
            val result = ShaderInvoker.invokeAsync("user", "search_handles", mapOf("prefix" to prefix))
            val results = result["results"] as? List<*> ?: return emptyList()
            val contacts = mutableListOf<ContactEntity>()
            for (item in results) {
                val map = item as? Map<*, *> ?: continue
                val handle = map["handle"] as? String ?: continue
                val walletId = Helpers.normalizeWalletId(map["wallet_id"] as? String ?: "") ?: continue
                val displayName = Helpers.fixBvmUtf8(map["display_name"] as? String)
                val height = (map["registered_height"] as? Number)?.toLong() ?: 0

                // Upsert into local DB (walletId only — sbbsAddress from SBBS envelope)
                db.contactDao().updateResolved(handle, walletId, displayName, null, height)
                contacts.add(ContactEntity(
                    handle = handle,
                    walletId = walletId,
                    displayName = displayName,
                    registeredHeight = height,
                ))
            }
            Log.d(TAG, "searchOnChain('$prefix') → ${contacts.size} results")
            return contacts
        } catch (e: Exception) {
            Log.e(TAG, "searchOnChain($prefix) failed: ${e.message}")
            return emptyList()
        }
    }

   /**
     * Batch-resolve all contacts with missing sbbs_address (need contract for sending).
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

    /** Send avatar_request to a contact. Uses sbbsAddress (SBBS channel) for sending. */
    suspend fun requestAvatar(handle: String, sbbsAddress: String) {
        try {
            val state = db.chatStateDao().get() ?: return
            val myHandle = state.myHandle ?: return
            val payload = mapOf(
                "v" to 1, "t" to "avatar_request",
                "ts" to System.currentTimeMillis() / 1000,
                "from" to myHandle, "to" to handle,
            )
            com.privimemobile.chat.ChatService.sbbs.sendWithRetry(sbbsAddress, payload)
            Log.d(TAG, "Sent avatar_request to @$handle")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request avatar from @$handle: ${e.message}")
        }
    }
}
