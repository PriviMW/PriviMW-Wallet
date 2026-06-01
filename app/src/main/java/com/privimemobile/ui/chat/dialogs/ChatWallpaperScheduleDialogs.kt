package com.privimemobile.ui.chat.dialogs

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.R
import com.privimemobile.protocol.ChatMessage
import com.privimemobile.ui.chat.ChatChromeState
import com.privimemobile.ui.chat.ChatInputState
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatWallpaperScheduleDialogs(
    chrome: ChatChromeState,
    input: ChatInputState,
    chatPrefs: SharedPreferences,
    convKey: String,
    context: Context,
    scope: CoroutineScope,
    messages: List<ChatMessage>,
    listState: LazyListState,
    wallpaperImagePicker: ActivityResultLauncher<String>,
    onScheduleMessage: (text: String, scheduledAt: Long) -> Unit,
) {
    if (chrome.showWallpaperPicker) {
        val wallpaperOptions = listOf(
            "default" to stringResource(R.string.chat_wallpaper_default),
            "dark_blue" to stringResource(R.string.chat_wallpaper_dark_blue),
            "teal" to stringResource(R.string.chat_wallpaper_teal),
            "purple" to stringResource(R.string.chat_wallpaper_purple),
            "midnight" to stringResource(R.string.chat_wallpaper_midnight),
            "forest" to stringResource(R.string.chat_wallpaper_forest),
            "sunset" to stringResource(R.string.chat_wallpaper_sunset),
        )
        AlertDialog(
            onDismissRequest = { chrome.showWallpaperPicker = false },
            containerColor = C.card,
            title = { Text(stringResource(R.string.chat_wallpaper), color = C.text, fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    // Custom image option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { wallpaperImagePicker.launch("image/*") }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape)
                                .background(C.accent.copy(alpha = 0.3f))
                                .then(if (chrome.chatWallpaper.startsWith("custom:")) Modifier.border(2.dp, C.accent, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) { Text("\uD83D\uDDBC", fontSize = 16.sp) }
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.chat_custom_image), color = C.text, fontSize = 15.sp)
                        if (chrome.chatWallpaper.startsWith("custom:")) {
                            Spacer(Modifier.weight(1f))
                            Text("\u2713", color = C.accent, fontSize = 16.sp)
                        }
                    }
                    HorizontalDivider(color = C.textSecondary.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                    wallpaperOptions.forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    chrome.chatWallpaper = key
                                    chatPrefs.edit().putString("wallpaper_$convKey", key).apply()
                                    chrome.showWallpaperPicker = false
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Color preview
                            val previewColor = when (key) {
                                "dark_blue" -> Color(0xFF0D1B2A)
                                "teal" -> Color(0xFF004D40)
                                "purple" -> Color(0xFF1A0033)
                                "midnight" -> Color(0xFF0F0F23)
                                "forest" -> Color(0xFF1B3A2D)
                                "sunset" -> Color(0xFF2D1B00)
                                else -> C.bg
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(previewColor)
                                    .then(if (chrome.chatWallpaper == key) Modifier.border(2.dp, C.accent, CircleShape) else Modifier),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(label, color = C.text, fontSize = 15.sp)
                            if (chrome.chatWallpaper == key) {
                                Spacer(Modifier.weight(1f))
                                Text("\u2713", color = C.accent, fontSize = 16.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }

    if (input.showSchedulePicker) {
        val scheduleOptions = listOf(
            "10min" to stringResource(R.string.chat_schedule_in_10min),
            "30min" to stringResource(R.string.chat_schedule_in_30min),
            "1h" to stringResource(R.string.chat_schedule_in_1h),
            "3h" to stringResource(R.string.chat_schedule_in_3h),
            "tomorrow" to stringResource(R.string.chat_schedule_tomorrow),
            "custom" to stringResource(R.string.chat_schedule_custom),
        )
        var showCustomDateTime by remember { mutableStateOf(false) }

        if (!showCustomDateTime) {
            AlertDialog(
                onDismissRequest = { input.showSchedulePicker = false },
                containerColor = C.card,
                title = { Text(stringResource(R.string.chat_schedule_message), color = C.text, fontWeight = FontWeight.SemiBold) },
                text = {
                    Column {
                        Text(stringResource(R.string.chat_longpress_schedule_hint), color = C.textSecondary, fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp))
                        scheduleOptions.forEach { (key, label) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (key == "custom") {
                                            showCustomDateTime = true
                                        } else {
                                            val now = System.currentTimeMillis() / 1000
                                            val scheduledAt = when (key) {
                                                "10min" -> now + 600
                                                "30min" -> now + 1800
                                                "1h" -> now + 3600
                                                "3h" -> now + 10800
                                                "tomorrow" -> {
                                                    val cal = java.util.Calendar.getInstance()
                                                    cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                                                    cal.set(java.util.Calendar.HOUR_OF_DAY, 9)
                                                    cal.set(java.util.Calendar.MINUTE, 0)
                                                    cal.set(java.util.Calendar.SECOND, 0)
                                                    cal.timeInMillis / 1000
                                                }
                                                else -> now + 600
                                            }
                                            onScheduleMessage(input.inputText.text, scheduledAt)
                                            input.showSchedulePicker = false
                                        }
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(if (key == "custom") "\uD83D\uDCC5" else "\u23F0", fontSize = 18.sp)
                                Spacer(Modifier.width(12.dp))
                                Text(label, color = C.text, fontSize = 15.sp)
                            }
                        }
                    }
                },
                confirmButton = {},
            )
        } else {
            // Two-step: date picker → time picker
            var selectedDateMillis by remember { mutableStateOf<Long?>(null) }

            if (selectedDateMillis == null) {
                // Step 1: Date picker
                val dateState = rememberDatePickerState()
                DatePickerDialog(
                    onDismissRequest = { showCustomDateTime = false; input.showSchedulePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            val millis = dateState.selectedDateMillis
                            if (millis != null) selectedDateMillis = millis
                        }) { Text(stringResource(R.string.general_next), color = C.accent) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCustomDateTime = false; input.showSchedulePicker = false }) {
                            Text(stringResource(R.string.general_cancel), color = C.textSecondary)
                        }
                    },
                ) {
                    DatePicker(state = dateState, colors = DatePickerDefaults.colors(containerColor = C.card))
                }
            } else {
                // Step 2: Time picker
                val cal = java.util.Calendar.getInstance()
                val initMin = cal.get(java.util.Calendar.MINUTE) + 5
                val timeState = rememberTimePickerState(
                    initialHour = cal.get(java.util.Calendar.HOUR_OF_DAY) + initMin / 60,
                    initialMinute = initMin % 60,
                )
                AlertDialog(
                    onDismissRequest = { showCustomDateTime = false; input.showSchedulePicker = false },
                    containerColor = C.card,
                    title = { Text(stringResource(R.string.chat_pick_time), color = C.text, fontWeight = FontWeight.SemiBold) },
                    text = {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            TimePicker(state = timeState)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val dateCal = java.util.Calendar.getInstance()
                            dateCal.timeInMillis = selectedDateMillis!!
                            dateCal.set(java.util.Calendar.HOUR_OF_DAY, timeState.hour)
                            dateCal.set(java.util.Calendar.MINUTE, timeState.minute)
                            dateCal.set(java.util.Calendar.SECOND, 0)
                            val scheduledAt = dateCal.timeInMillis / 1000
                            val now = System.currentTimeMillis() / 1000
                            if (scheduledAt > now) {
                                onScheduleMessage(input.inputText.text, scheduledAt)
                            } else {
                                Toast.makeText(context, R.string.toast_time_future, Toast.LENGTH_SHORT).show()
                            }
                            showCustomDateTime = false
                            input.showSchedulePicker = false
                        }) { Text(stringResource(R.string.chat_schedule), color = C.accent) }
                    },
                    dismissButton = {
                        TextButton(onClick = { selectedDateMillis = null }) {
                            Text(stringResource(R.string.general_back), color = C.textSecondary)
                        }
                    },
                )
            }
        }
    }
    if (input.showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { input.showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val selectedMillis = datePickerState.selectedDateMillis
                    if (selectedMillis != null) {
                        val targetTs = selectedMillis / 1000
                        // Find the closest message to this date
                        val reversedMessages = messages.reversed()
                        val idx = reversedMessages.indexOfFirst { it.timestamp >= targetTs }
                        if (idx >= 0) {
                            scope.launch { listState.animateScrollToItem(idx) }
                        } else if (reversedMessages.isNotEmpty()) {
                            // Date is after all messages — scroll to newest
                            scope.launch { listState.animateScrollToItem(0) }
                        }
                    }
                    input.showDatePicker = false
                }) {
                    Text(stringResource(R.string.chat_jump), color = C.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { input.showDatePicker = false }) {
                    Text(stringResource(R.string.general_cancel), color = C.textSecondary)
                }
            },
            colors = DatePickerDefaults.colors(containerColor = C.card),
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (input.showOneShotTimerPicker) {
        val timerOptions = listOf(
            0 to stringResource(R.string.chat_overflow_timer_off),
            30 to stringResource(R.string.chat_timer_30s),
            300 to stringResource(R.string.chat_timer_5min),
            3600 to stringResource(R.string.chat_timer_1h),
            86400 to stringResource(R.string.chat_timer_1day),
        )
        AlertDialog(
            onDismissRequest = { input.showOneShotTimerPicker = false },
            containerColor = C.card,
            shape = RoundedCornerShape(16.dp),
            title = { Text(stringResource(R.string.chat_self_destruct_timer), color = C.text, fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.chat_next_message_hint),
                        color = C.textSecondary, fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    timerOptions.forEach { (seconds, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    input.oneShotTimer = seconds
                                    input.showOneShotTimerPicker = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = input.oneShotTimer == seconds,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = C.accent,
                                    unselectedColor = C.textSecondary,
                                ),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(label, color = C.text, fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }
}
