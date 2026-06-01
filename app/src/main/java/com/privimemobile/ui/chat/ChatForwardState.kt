package com.privimemobile.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.privimemobile.protocol.ChatMessage

/**
 * Forward-to contact/group picker dialog state.
 * SBBS / group send and DB inserts stay in [ChatScreen] orchestrator.
 */
class ChatForwardState {
    var forwardingMsg by mutableStateOf<ChatMessage?>(null)
    var forwardingMsgs by mutableStateOf<List<ChatMessage>>(emptyList())

    val isPickerOpen: Boolean get() = forwardingMsg != null

    fun messagesToForward(): List<ChatMessage> {
        val anchor = forwardingMsg ?: return emptyList()
        return forwardingMsgs.ifEmpty { listOf(anchor) }
    }

    fun openSingle(msg: ChatMessage) {
        forwardingMsgs = emptyList()
        forwardingMsg = msg
    }

    fun openMultiple(msgs: List<ChatMessage>) {
        if (msgs.isEmpty()) return
        forwardingMsgs = msgs
        forwardingMsg = msgs.first()
    }

    fun dismiss() {
        forwardingMsg = null
        forwardingMsgs = emptyList()
    }
}
