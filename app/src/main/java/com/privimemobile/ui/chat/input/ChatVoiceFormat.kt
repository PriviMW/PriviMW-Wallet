package com.privimemobile.ui.chat.input

/** Format voice duration as m:ss */
fun formatVoiceDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
