package com.privimemobile.ui.chat.chats

import com.privimemobile.chat.ChatService
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Pull-to-refresh for the chats tab — matches pre-merge **outer** PTR:
 * SBBS poll + [GroupManager.refreshMyGroups], then a short minimum spinner time.
 *
 * Not tied to DM/chat count: slowness usually means many **groups**, where each new
 * group can trigger extra on-chain `view_group` / member fetches inside refreshMyGroups.
 * cleanup + force identity refresh are not run here (those were inner-only and added seconds).
 */
internal suspend fun runChatsListRefresh() {
    ChatService.sbbs.pollNow()
    val started = System.currentTimeMillis()
    try {
        // Cap wait so the indicator does not spin for unbounded contract work.
        withTimeout(PTR_GROUP_REFRESH_MS) {
            ChatService.groups.refreshMyGroups()
        }
    } catch (_: TimeoutCancellationException) {
        ChatService.scope.launch { ChatService.groups.refreshMyGroups() }
    }
    val elapsed = System.currentTimeMillis() - started
    val minVisible = PTR_MIN_SPINNER_MS - elapsed
    if (minVisible > 0) delay(minVisible)
    // Optional hygiene — do not block the spinner (second list_my_groups call).
    ChatService.scope.launch { ChatService.groups.cleanupDeletedGroups() }
}

private const val PTR_GROUP_REFRESH_MS = 3_000L
private const val PTR_MIN_SPINNER_MS = 500L
