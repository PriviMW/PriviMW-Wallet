package com.privimemobile.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Header overflow, wallpaper, clear/delete chat confirmations, notification sound label. */
class ChatChromeState(initialWallpaper: String) {
    var showOverflowMenu by mutableStateOf(false)
    var showWallpaperPicker by mutableStateOf(false)
    var chatWallpaper by mutableStateOf(initialWallpaper)
    var showClearConfirm by mutableStateOf(false)
    var showDeleteConfirm by mutableStateOf(false)
    var groupNotifSoundName by mutableStateOf("")

    fun setWallpaper(path: String) {
        chatWallpaper = path
    }
}
