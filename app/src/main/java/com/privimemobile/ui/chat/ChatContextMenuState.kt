package com.privimemobile.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.privimemobile.protocol.ChatMessage

/** Long-press message menu and reaction-detail sheet state. */
class ChatContextMenuState {
    var contextMenuMsg by mutableStateOf<ChatMessage?>(null)
    var reactionDetailMsg by mutableStateOf<ChatMessage?>(null)
    var reactionDetailEmoji by mutableStateOf("")
    var replyHighlightTs by mutableStateOf<Long?>(null)

    fun dismissContextMenu() {
        contextMenuMsg = null
    }

    fun showReactionDetail(msg: ChatMessage, emoji: String) {
        reactionDetailMsg = msg
        reactionDetailEmoji = emoji
    }

    fun dismissReactionDetail() {
        reactionDetailMsg = null
        reactionDetailEmoji = ""
    }
}
