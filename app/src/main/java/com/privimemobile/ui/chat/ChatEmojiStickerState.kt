package com.privimemobile.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Emoji/sticker picker panel and view-pack dialog flags. */
class ChatEmojiStickerState {
    var showEmojiPicker by mutableStateOf(false)
    var emojiMainTab by mutableStateOf(0)
    var viewPackId by mutableStateOf<String?>(null)
    var showCreateStickerPack by mutableStateOf(false)
}
