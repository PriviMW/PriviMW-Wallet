package com.privimemobile.ui.chat

/**
 * Process-wide chat session state (survives navigation within the app process).
 * Must stay separate from per-composable state — see ChatScreen unread / scroll invariants.
 */
object ChatSessionStore {
    /** Chats opened this process session — first open scrolls to bottom; re-entry restores position. */
    val openedChatSessions = mutableSetOf<String>()

    /** LazyColumn index + offset per convKey on leave. */
    val chatScrollPositions = mutableMapOf<String, Pair<Int, Int>>()

    /** Scroll-to-bottom badge floor + version per convKey. */
    val chatBadgeFloors = mutableMapOf<String, Pair<Int, Int>>()

    /**
     * Unread count captured before [com.privimemobile.chat.ChatService.setActiveChat] clears DB unread.
     */
    val chatInitialUnread = mutableMapOf<String, Int>()
}
