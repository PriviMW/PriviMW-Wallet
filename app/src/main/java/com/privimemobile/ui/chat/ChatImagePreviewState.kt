package com.privimemobile.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.privimemobile.ui.chat.media.ImagePreviewData

/** Fullscreen image viewer, attachment picker, and gallery preview-before-send. */
class ChatImagePreviewState {
    var fullscreenImage by mutableStateOf<FullscreenImageData?>(null)
    var showAttachPicker by mutableStateOf(false)
    var attachPickerTab by mutableStateOf(0)
    var imagePreview by mutableStateOf<ImagePreviewData?>(null)
    var previewCaption by mutableStateOf("")
    var sendingFromPreview by mutableStateOf(false)

    fun dismissFullscreen() {
        fullscreenImage = null
    }

    fun dismissImagePreview() {
        imagePreview = null
        previewCaption = ""
        sendingFromPreview = false
    }
}
