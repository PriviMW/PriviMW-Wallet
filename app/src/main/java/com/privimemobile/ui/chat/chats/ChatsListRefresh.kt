package com.privimemobile.ui.chat.chats

import com.privimemobile.chat.ChatService
import kotlinx.coroutines.delay

/** Combined pull-to-refresh work for the chats list (outer PTR — replaces nested inner box). */
internal suspend fun runChatsListRefresh() {
    val contacts = ChatService.db?.contactDao()?.getAll() ?: emptyList()
    for (c in contacts) {
        try {
            val resolved = ChatService.contacts.resolveHandle(c.handle)
            if (resolved?.displayName != null && resolved.displayName != c.displayName) {
                ChatService.db?.contactDao()?.updateDisplayName(c.handle, resolved.displayName)
                ChatService.db?.conversationDao()?.updateDisplayName("@${c.handle}", resolved.displayName)
            }
        } catch (_: Exception) {
        }
    }
    ChatService.sbbs.pollNow()
    ChatService.groups.refreshMyGroups()
    ChatService.groups.cleanupDeletedGroups()
    ChatService.identity.refreshIdentity(forceRefresh = true)
    delay(2000)
}
