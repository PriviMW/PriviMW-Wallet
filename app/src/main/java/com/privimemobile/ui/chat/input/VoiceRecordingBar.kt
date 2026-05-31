package com.privimemobile.ui.chat.input

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.R
import com.privimemobile.ui.theme.C

/**
 * Telegram-style voice recording bar.
 * Shows recording duration, slide-to-cancel and swipe-up-to-lock gestures.
 */
@Composable
fun VoiceRecordingBar(
    durationMs: Long,
    isLocked: Boolean,
    onCancel: () -> Unit,
    onLock: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragOffsetX by remember { mutableStateOf(0f) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val cancelThreshold = 120f
    val lockThreshold = 80f

    val shouldCancel = dragOffsetX < -cancelThreshold
    val shouldLock = dragOffsetY < -lockThreshold

    LaunchedEffect(shouldLock) {
        if (shouldLock && !isLocked) {
            onLock()
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(start = 12.dp, end = 8.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (shouldCancel) onCancel()
                        dragOffsetX = 0f
                    },
                    onHorizontalDrag = { _, drag ->
                        dragOffsetX = (dragOffsetX + drag).coerceIn(-200f, 0f)
                    },
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { dragOffsetY = 0f },
                    onVerticalDrag = { _, drag ->
                        if (!isLocked) {
                            dragOffsetY = (dragOffsetY + drag).coerceIn(-150f, 0f)
                        }
                    },
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (shouldCancel) C.textMuted else Color(0xFFE53935)),
            )
        }

        Spacer(Modifier.width(12.dp))

        Text(
            text = formatVoiceDuration(durationMs),
            color = if (shouldCancel) C.textMuted else C.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            when {
                shouldCancel -> {
                    Text(
                        text = stringResource(R.string.chat_voice_release_cancel),
                        color = C.error,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                isLocked -> {
                    Text(
                        text = stringResource(R.string.chat_voice_cancel),
                        color = if (shouldLock) C.accent else C.accent.copy(alpha = 0.7f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onCancel() },
                    )
                }

                else -> {
                    Text(
                        text = "\u27F5 " + stringResource(R.string.chat_voice_slide_to_cancel),
                        color = C.textSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
