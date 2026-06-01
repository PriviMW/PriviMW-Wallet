package com.privimemobile.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.privimemobile.chat.voice.VoiceRecorder
import java.io.File

/** Voice record / lock / preview UI. Encoding and send stay in [ChatScreen]. */
class ChatVoiceState {
    var voiceRecording by mutableStateOf(false)
    var voiceLocked by mutableStateOf(false)
    var voiceCanceling by mutableStateOf(false)
    var voicePaused by mutableStateOf(false)
    var voicePauseDuration by mutableStateOf(0L)
    var voicePreviewFile by mutableStateOf<File?>(null)
    var voicePreviewWaveform by mutableStateOf<ByteArray?>(null)
    var voicePreviewDuration by mutableStateOf(0L)
    var voiceRecorder by mutableStateOf<VoiceRecorder?>(null)
    var voiceRecordDuration by mutableStateOf(0L)
    var micShowRecordHint by mutableStateOf(false)
    var micSlideOffset by mutableStateOf(0f)
    var micIsRecordingVisual by mutableStateOf(false)
    var hasRecordPermission by mutableStateOf(false)
    var startRecordingAfterPermission by mutableStateOf(false)

    fun resetRecordingUi() {
        voiceRecording = false
        voiceLocked = false
        voiceCanceling = false
        voicePaused = false
        voicePauseDuration = 0L
        voiceRecordDuration = 0L
        micShowRecordHint = false
        micSlideOffset = 0f
        micIsRecordingVisual = false
    }

    fun clearPreview() {
        voicePreviewFile = null
        voicePreviewWaveform = null
        voicePreviewDuration = 0L
    }
}
