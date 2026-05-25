package com.privimemobile.chat

import com.privimemobile.chat.db.entities.ContactEntity
import com.privimemobile.chat.db.entities.ConversationEntity

/**
 * Resolves the SBBS address to use when sending a DM.
 *
 * Beam uses per-conversation SBBS channel addresses (envelope sender) that may differ from
 * on-chain wallet_id. Using stale contact.sbbsAddress alone routes messages to the wrong channel.
 */
object DmAddressResolver {
    fun resolve(contact: ContactEntity?, conv: ConversationEntity?): String? {
        val walletId = contact?.walletId?.takeIf { it.isNotEmpty() }
        val convSbbs = conv?.sbbsAddress?.takeIf { it.isNotEmpty() }
        val contactSbbs = contact?.sbbsAddress?.takeIf { it.isNotEmpty() }

        // Same rule as GroupManager.sendGroupInvite — envelope addr ≠ wallet_id → use wallet_id
        return if (!walletId.isNullOrEmpty() && !convSbbs.isNullOrEmpty() && convSbbs != walletId) {
            walletId
        } else {
            convSbbs ?: walletId ?: contactSbbs
        }
    }

    /** Align contact.sbbs_address with resolved send address so avatar_request etc. use the same channel. */
    suspend fun syncContactSbbs(db: com.privimemobile.chat.db.ChatDatabase, handle: String) {
        val contact = db.contactDao().findByHandle(handle) ?: return
        val conv = db.conversationDao().findByKey("@$handle")
        val resolved = resolve(contact, conv) ?: return
        if (contact.sbbsAddress != resolved) {
            db.contactDao().updateSbbsAddress(handle, resolved)
        }
    }
}
