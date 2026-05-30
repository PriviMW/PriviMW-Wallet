package com.privimemobile.ui.chat.chats

import androidx.compose.ui.graphics.Color
import com.privimemobile.ui.theme.C
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

/** Deterministic avatar colors from handle/name hash. */
internal val avatarColors = listOf(
    Color(0xFF5C6BC0), // indigo
    Color(0xFF26A69A), // teal
    Color(0xFFEF5350), // red
    Color(0xFFAB47BC), // purple
    Color(0xFF42A5F5), // blue
    Color(0xFFFF7043), // deep orange
    Color(0xFF66BB6A), // green
    Color(0xFFEC407A), // pink
    Color(0xFFFFA726), // orange
    Color(0xFF78909C), // blue grey
)

internal fun avatarColor(key: String): Color {
    val hash = abs(key.lowercase().hashCode())
    return avatarColors[hash % avatarColors.size]
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

internal fun formatTime(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val date = Date(timestamp * 1000)
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { time = date }
    return if (now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR) &&
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    ) timeFormat.format(date) else dateFormat.format(date)
}