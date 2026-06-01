package com.privimemobile.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Scroll-to-bottom badge floor, new-message tracking, sticky date header visibility. */
class ChatScrollBadgeState {
    var hasScrolledInitially by mutableStateOf(false)
    var lastBottomTimestamp by mutableLongStateOf(0L)
    var newMsgVersion by mutableIntStateOf(0)
    var lastMsgCount by mutableIntStateOf(0)
    var badgeFloor by mutableIntStateOf(Int.MAX_VALUE)
    var badgeFloorVersion by mutableIntStateOf(-1)
    var stickyVisible by mutableStateOf(false)

    companion object {
        fun forConv(convKey: String): ChatScrollBadgeState {
            val stored = ChatSessionStore.chatBadgeFloors[convKey]
            return ChatScrollBadgeState().apply {
                badgeFloor = stored?.first ?: Int.MAX_VALUE
                badgeFloorVersion = stored?.second ?: -1
            }
        }
    }

    fun resetBadgeFloorForNewConv() {
        badgeFloor = Int.MAX_VALUE
        badgeFloorVersion = -1
    }

    fun persistBadgeFloor(convKey: String) {
        if (badgeFloor != Int.MAX_VALUE) {
            ChatSessionStore.chatBadgeFloors[convKey] = badgeFloor to badgeFloorVersion
        }
    }
}
