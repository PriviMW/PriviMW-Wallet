package com.privimemobile.ui.chat.format

import android.content.Context
import com.privimemobile.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val msgTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dateSepFormat = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())

fun formatMessageTime(ts: Long): String {
    if (ts <= 0) return ""
    return msgTimeFormat.format(Date(ts * 1000))
}

fun formatDateSeparator(ts: Long, ctx: Context): String {
    if (ts <= 0) return ""
    val date = Date(ts * 1000)
    val cal = Calendar.getInstance()
    val today = Calendar.getInstance()

    cal.time = date
    return when {
        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) ->
            ctx.getString(R.string.chat_date_today)
        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) - 1 ->
            ctx.getString(R.string.chat_date_yesterday)
        else -> dateSepFormat.format(date)
    }
}

/** Format disappearing timer value to human-readable label. */
internal fun formatTimerLabel(ctx: Context, seconds: Int): String = when {
    seconds <= 0 -> ctx.getString(R.string.chat_timer_off)
    seconds < 60 -> ctx.getString(R.string.chat_timer_seconds_dynamic, seconds)
    seconds < 3600 -> ctx.getString(R.string.chat_timer_minutes_dynamic, seconds / 60)
    seconds < 86400 -> ctx.getString(R.string.chat_timer_hours_dynamic, seconds / 3600)
    else -> ctx.getString(R.string.chat_timer_days_dynamic, seconds / 86400)
}
