package com.privimemobile.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.privimemobile.protocol.ChatMessage

/** Composer: text, reply/edit targets, pending attachments, timers, schedule picker flag. */
class ChatInputState(initialDraft: String = "") {
    var inputText by mutableStateOf(TextFieldValue(initialDraft))
    var replyingTo by mutableStateOf<ChatMessage?>(null)
    var editingMsg by mutableStateOf<ChatMessage?>(null)
    var pendingFile by mutableStateOf<PendingFile?>(null)
    var pendingStickerMeta by mutableStateOf<StickerMeta?>(null)
    var oneShotTimer by mutableStateOf(0)
    var showOneShotTimerPicker by mutableStateOf(false)
    var showDatePicker by mutableStateOf(false)
    var showSchedulePicker by mutableStateOf(false)

    fun setInputText(text: String) {
        inputText = TextFieldValue(text, TextRange(text.length))
    }

    fun clearReplyAndEdit() {
        replyingTo = null
        editingMsg = null
    }
}
