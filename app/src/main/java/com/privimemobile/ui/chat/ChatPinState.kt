package com.privimemobile.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Pinned-message bar UI: highlight timestamp, list dialog, manual bar index override.
 * Scroll-aware index derivation and [LazyListState] animation stay in [ChatScreen].
 */
class ChatPinState {
    var pinHighlightTs by mutableStateOf(0L)
    var showPinListDialog by mutableStateOf(false)
    /** User tapped pin bar; -1 = use scroll-aware index. */
    var manualOverrideIndex by mutableStateOf(-1)
    /** List index after bar tap-scroll; used to detect user scroll-away. */
    var scrollPosAtOverride by mutableStateOf(-1)

    fun clearManualOverride() {
        manualOverrideIndex = -1
        scrollPosAtOverride = -1
    }

    fun applyBarTapOverride(safeIndex: Int, landingListIndex: Int) {
        manualOverrideIndex = if (safeIndex > 0) safeIndex - 1 else 0
        scrollPosAtOverride = landingListIndex
    }

    fun dismissPinListDialog() {
        showPinListDialog = false
    }
}
