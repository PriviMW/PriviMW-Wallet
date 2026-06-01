package com.privimemobile.ui.chat

import androidx.compose.runtime.mutableStateMapOf

/** Per-CID local file paths and download status for message attachments. */
class ChatMessageListState {
    val filePaths = mutableStateMapOf<String, String>()
    val downloadStatuses = mutableStateMapOf<String, String>()
}
