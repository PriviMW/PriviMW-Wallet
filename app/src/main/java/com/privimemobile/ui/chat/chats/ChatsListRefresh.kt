package com.privimemobile.ui.chat.chats

import com.privimemobile.chat.ChatService
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pull-to-refresh for the chats tab (matches pre-merge **outer** PTR only).
 *
 * Do not loop [ContactManager.resolveHandle] for every contact here — each call hits
 * the contract and with many chats refresh can take 10+ seconds. Names refresh on chat open.
 */
internal suspend fun runChatsListRefresh() {
    ChatService.sbbs.pollNow()
    coroutineScope {
        launch { ChatService.groups.refreshMyGroups() }
        launch { ChatService.groups.cleanupDeletedGroups() }
        launch { ChatService.identity.refreshIdentity(forceRefresh = true) }
    }
    // Brief minimum so the indicator is visible; outer PTR used 2s before merge.
    delay(500)
}
