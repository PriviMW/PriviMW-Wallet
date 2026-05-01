package com.privimemobile.ui.components

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.*
import com.privimemobile.chat.voice.VoicePlaybackController
import com.privimemobile.chat.voice.VoiceRecorder
import com.privimemobile.chat.voice.VoiceWaveformView
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Voice message bubble for chat messages.
 *
 * Layout (Telegram style):
 * [Play/Pause] [Waveform ─────────────] [0:00 / 1:23] [2x]
 *
 * - Tap waveform to seek
 * - Tap play/pause to toggle playback
 * - Long-press for context menu (passed via callback)
 * - Speed badge (mine only, shown after duration)
 */
@Composable
fun VoiceMessageBubble(
    id: String,
    durationSecs: Int,
    waveform: ByteArray?,
    filePath: String?,
    isMine: Boolean,
    onLongPress: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val playbackController = remember { VoicePlaybackController }

    val isPlaying = playbackController.isPlaying(id)
    // Only read controller values for the ACTUALLY PLAYING bubble — each bubble owns its own duration
    val progress = if (isPlaying) playbackController.progress.value else 0f
    val positionMs = if (isPlaying) playbackController.positionMs.value else 0L
    val durationMs = if (isPlaying) playbackController.durationMs.value else durationSecs * 1000L
    val speed = if (isPlaying) playbackController.speed.value else 1f

    // Resolve file path
    val file = remember(filePath) {
        filePath?.let { File(it) }
    }

    // Check if file exists
    val fileExists = remember(file) { file?.exists() == true }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { _ -> onLongPress() })
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Play/Pause button
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isMine) C.textDark else C.accent)
                .clickable(enabled = fileExists) {
                    file?.let {
                        playbackController.toggle(id, it, waveform)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(if (isPlaying) R.string.voice_pause_button else R.string.voice_play_button),
                tint = if (isMine) C.accent else C.textDark,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Duration label (left for mine, right for theirs)
        if (isMine) {
            DurationLabel(
                positionMs = if (isPlaying) positionMs else 0L,
                durationMs = if (durationMs > 0) durationMs else durationSecs * 1000L,
                isPlaying = isPlaying,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        // Waveform
        VoiceWaveformView(
            waveform = waveform,
            progress = if (isPlaying) progress else 0f,
            isPlaying = isPlaying,
            isMine = isMine,
            modifier = Modifier
                .weight(1f)
                .height(32.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        // Seek on waveform tap
                        if (fileExists && file != null) {
                            val width = size.width.toFloat()
                            val seekProgress = (offset.x / width).coerceIn(0f, 1f)
                            playbackController.play(id, file, waveform, seekProgress)
                        }
                    }
                }
        )

        // Duration label (theirs on right)
        if (!isMine) {
            Spacer(modifier = Modifier.width(8.dp))
            DurationLabel(
                positionMs = if (isPlaying) positionMs else 0L,
                durationMs = if (durationMs > 0) durationMs else durationSecs * 1000L,
                isPlaying = isPlaying,
            )
        }

        // Speed badge (mine only, when playing)
        if (isMine && isPlaying && speed != 1f) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${speed.toInt()}x",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = C.textSecondary,
            )
        }
    }
}

@Composable
private fun DurationLabel(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
) {
    val pos = if (isPlaying) formatDuration(positionMs) else "0:00"
    val dur = formatDuration(durationMs)
    Text(
        text = "$pos / $dur",
        fontSize = 12.sp,
        color = C.textMuted,
    )
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

/**
 * Telegram-style microphone button for voice messages.
 *
 * Behavior:
 * - **Tap (< 200ms):** Shows tooltip "Hold to record audio." that fades after 2.5s
 * - **Hold (≥ 200ms):** Starts recording immediately while finger is held
 * - **Release after recording:** Stops and sends (via onSend callback)
 * - **Slide left while recording:** Cancels (discard)
 * - **Max duration reached:** Auto-stops and sends
 */
@Composable
fun MicButton(
    isRecordingVisual: Boolean,
    slideOffset: Float,
    hasRecordPermission: Boolean,
    onRecordPermissionRequest: () -> Unit,
    scope: CoroutineScope,
    view: View,
    context: Context,
    onStartRecording: () -> Unit,
    onSendRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onShowHint: (Boolean) -> Unit,
    onLockSwipe: (() -> Unit)? = null,
) {
    var longPressJob by remember { mutableStateOf<Job?>(null) }
    var hintJob by remember { mutableStateOf<Job?>(null) }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
                when {
                    isRecordingVisual && slideOffset < -120f -> C.error.copy(alpha = 0.25f)
                    isRecordingVisual -> C.error.copy(alpha = 0.15f)
                    else -> Color.Transparent
                }
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown()
                        val startX = down.position.x
                        val startY = down.position.y

                        // State for this gesture session
                        var recordingStarted = false
                        var cancelled = false
                        var locked = false

                        // Schedule recording trigger after 200ms
                        longPressJob = scope.launch {
                            delay(200L)
                            recordingStarted = true
                            onShowHint(false)
                            if (!hasRecordPermission) {
                                onRecordPermissionRequest()
                            } else {
                                onStartRecording()
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            }
                        }

                        // Track finger movement
                        var dragX = 0f
                        var dragY = 0f
                        loop@ while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break

                            if (!change.pressed) {
                                // Finger released
                                longPressJob?.cancel()
                                if (cancelled || locked) {
                                    // Already cancelled or locked — do nothing on release
                                } else if (recordingStarted) {
                                    onSendRecording()
                                } else {
                                    // Tap — show hint
                                    longPressJob?.cancel()
                                    onShowHint(true)
                                    hintJob?.cancel()
                                    hintJob = scope.launch {
                                        delay(2500)
                                        onShowHint(false)
                                    }
                                }
                                break@loop
                            }

                            dragX = change.position.x - startX
                            dragY = change.position.y - startY

                            // Swipe left to cancel
                            if (recordingStarted && dragX < -120f && !cancelled && !locked) {
                                cancelled = true
                                longPressJob?.cancel()
                                onCancelRecording()
                                break@loop
                            }

                            // Swipe up to lock (Telegram-style: release finger, recording continues)
                            if (recordingStarted && dragY < -60f && !cancelled && !locked) {
                                locked = true
                                onLockSwipe?.invoke()
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                break@loop
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Mic,
            "Voice",
            tint = if (isRecordingVisual) C.error else C.textSecondary,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Telegram-style tooltip: dark bubble with small arrow pointing DOWN toward mic button.
 */
@Composable
fun RecordHintTooltip(
    text: String = "Hold to record · Max 2 min",
) {
    Box(
        contentAlignment = Alignment.BottomEnd,
    ) {
        // Bubble body
        Surface(
            color = Color(0xFF1C1E1E),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        // Arrow — overlaps bubble bottom via negative Y offset (seamless merge)
        Canvas(
            modifier = Modifier
                .size(width = 10.dp, height = 6.dp)
                .offset(y = (-2).dp),
        ) {
            drawPath(
                path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                },
                color = Color(0xFF1C1E1E),
            )
        }
    }
}
