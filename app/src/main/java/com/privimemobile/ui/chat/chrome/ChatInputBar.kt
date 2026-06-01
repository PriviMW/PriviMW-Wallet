package com.privimemobile.ui.chat.chrome

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.R
import com.privimemobile.chat.db.entities.GroupMemberEntity
import com.privimemobile.ui.chat.ChatCommandMenu
import com.privimemobile.ui.chat.ChatEmojiStickerState
import com.privimemobile.ui.chat.ChatImagePreviewState
import com.privimemobile.ui.chat.ChatInputState
import com.privimemobile.ui.chat.ChatVoiceState
import com.privimemobile.ui.chat.MentionAutocompleteMenu
import com.privimemobile.ui.chat.input.VoiceLockIndicator
import com.privimemobile.ui.chat.input.VoicePreviewBar
import com.privimemobile.ui.chat.input.VoiceRecordingBar
import com.privimemobile.ui.components.MicButton
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Bottom input bar: text field, voice record UI, command/mention popups, mic overlays. */
@Composable
fun ChatInputBar(
    view: View,
    scope: CoroutineScope,
    input: ChatInputState,
    voice: ChatVoiceState,
    emoji: ChatEmojiStickerState,
    media: ChatImagePreviewState,
    isGroupMode: Boolean,
    groupId: String?,
    convKey: String,
    resolvedSbbsAddress: String?,
    uploading: Boolean,
    isDeletedAccount: Boolean,
    showCommandMenu: Boolean,
    onShowCommandMenuChange: (Boolean) -> Unit,
    showMentionMenu: Boolean,
    onShowMentionMenuChange: (Boolean) -> Unit,
    mentionStartIdx: Int,
    onMentionStartIdxChange: (Int) -> Unit,
    onMentionFilterChange: (String) -> Unit,
    filteredMembers: List<GroupMemberEntity>,
    onRequestRecordPermission: () -> Unit,
    onSend: () -> Unit,
    onSendVoice: suspend (com.privimemobile.chat.voice.VoiceRecorder.RecordingResult) -> Unit,
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    // Close emoji picker when system keyboard appears
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(imeVisible) {
        if (imeVisible && emoji.showEmojiPicker && emoji.emojiMainTab == 0) emoji.showEmojiPicker = false
        // Only auto-close on emoji tab (emoji.emojiMainTab=0). Sticker tab (emoji.emojiMainTab=1) uses dialogs/pickers that open keyboard.
    }

    // Live duration timer for voice recording
    LaunchedEffect(voice.voiceRecording) {
        while (voice.voiceRecording) {
            voice.voiceRecordDuration = voice.voiceRecorder?.getDurationMs() ?: 0L
            delay(100)
        }
    }

    // ── Input bar & overlays — outer Box with clip=false for floating elements ──
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { clip = false },
    ) {
        Surface(
            color = C.card,
            shadowElevation = 2.dp,
            modifier = Modifier
                .then(if (!emoji.showEmojiPicker) Modifier.navigationBarsPadding() else Modifier),
        ) {
            // ── Input bar: left bar content animates, MicButton stays always stable ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left: AnimatedContent for bar content only (normal ↔ recording ↔ preview)
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedContent(
                        targetState = when { voice.voicePaused -> 2; voice.voiceRecording -> 1; else -> 0 },
                        transitionSpec = {
                            fadeIn(tween(200)).togetherWith(fadeOut(tween(100)))
                        },
                        label = "inputBarMode",
                    ) { state ->
                        if (state == 2) {
                            VoicePreviewBar(
                                waveform = voice.voicePreviewWaveform,
                                durationMs = voice.voicePauseDuration,
                                onDelete = {
                                    voice.voiceRecorder?.cancel()
                                    voice.voicePreviewFile = null
                                    voice.voicePreviewWaveform = null
                                    voice.voicePreviewDuration = 0L
                                    voice.voicePauseDuration = 0L
                                    voice.voicePaused = false
                                    voice.voiceRecording = false
                                    voice.voiceLocked = false
                                    voice.voiceRecorder = null
                                    voice.micIsRecordingVisual = false
                                    voice.micSlideOffset = 0f
                                },
                                onSend = {
                                    val result = voice.voiceRecorder?.stop()
                                    if (result != null) {
                                        scope.launch { onSendVoice(result) }
                                    }
                                    voice.voicePreviewFile = null
                                    voice.voicePreviewWaveform = null
                                    voice.voicePreviewDuration = 0L
                                    voice.voicePauseDuration = 0L
                                    voice.voicePaused = false
                                    voice.voiceRecording = false
                                    voice.voiceLocked = false
                                    voice.voiceRecorder = null
                                    voice.micIsRecordingVisual = false
                                    voice.micSlideOffset = 0f
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                            )
                        } else if (state == 1) {
                            VoiceRecordingBar(
                                durationMs = voice.voiceRecordDuration,
                                isLocked = voice.voiceLocked,
                                onCancel = {
                                    voice.voiceRecorder?.cancel()
                                    voice.voiceRecording = false
                                    voice.voiceLocked = false
                                    voice.voiceRecorder = null
                                    voice.micIsRecordingVisual = false
                                },
                                onLock = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    voice.voiceLocked = true
                                },
                                onSend = {
                                    val result = voice.voiceRecorder?.stop()
                                    if (result != null) {
                                        scope.launch { onSendVoice(result) }
                                    }
                                    voice.voiceRecording = false
                                    voice.voiceLocked = false
                                    voice.voiceRecorder = null
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                            )
                        } else {
                            // Normal input bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                                    .heightIn(min = 52.dp)
                                    .padding(horizontal = 2.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Emoji toggle (left — 48dp like Telegram)
                                IconButton(
                                    onClick = {
                                        if (emoji.showEmojiPicker) {
                                            emoji.showEmojiPicker = false
                                            keyboardController?.show()
                                        } else {
                                            emoji.showEmojiPicker = true
                                            keyboardController?.hide()
                                        }
                                    },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    AnimatedContent(
                                        targetState = emoji.showEmojiPicker,
                                        transitionSpec = { (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.6f)).togetherWith(fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.6f)) },
                                        label = "emojiIcon",
                                    ) { isEmoji ->
                                        if (isEmoji) {
                                            Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.chat_send_message), tint = C.textSecondary, modifier = Modifier.size(24.dp))
                                        } else {
                                            Text("\uD83D\uDE00", fontSize = 24.sp)
                                        }
                                    }
                                }

                                // Text field (center — fills available space)
                                OutlinedTextField(
                                    value = input.inputText,
                                    onValueChange = { newValue ->
                                        input.inputText = newValue
                                        val text = newValue.text
                                        val cursor = newValue.selection.start
                                        if (text == "/") onShowCommandMenuChange(true)
                                        else if (showCommandMenu && (!text.startsWith("/") || text.contains(" "))) onShowCommandMenuChange(false)
                                        // @mention autocomplete detection (group mode only)
                                        if (isGroupMode && cursor > 0) {
                                            var atIdx = -1
                                            for (k in cursor - 1 downTo 0) {
                                                val c = text[k]
                                                if (c == '@') { atIdx = k; break }
                                                if (c == ' ' || c == '\n') break
                                            }
                                            if (atIdx >= 0 && (atIdx == 0 || text[atIdx - 1] == ' ' || text[atIdx - 1] == '\n' ||
                                                        text.substring(0, atIdx).trimEnd().endsWith("/tip"))) {
                                                onMentionStartIdxChange(atIdx)
                                                onMentionFilterChange(text.substring(atIdx + 1, cursor))
                                                onShowMentionMenuChange(true)
                                            } else {
                                                onShowMentionMenuChange(false)
                                            }
                                        } else {
                                            onShowMentionMenuChange(false)
                                        }
                                        if (text.isNotEmpty()) {
                                            if (isGroupMode) com.privimemobile.chat.ChatService.groups.sendGroupTyping(groupId!!)
                                            else com.privimemobile.chat.ChatService.sbbs.sendTyping(convKey)
                                        }
                                    },
                                    placeholder = {
                                        Text(
                                            when {
                                                input.pendingFile != null -> stringResource(R.string.chat_add_caption)
                                                isDeletedAccount -> stringResource(R.string.chat_account_deleted_notice)
                                                !isGroupMode && resolvedSbbsAddress.isNullOrEmpty() -> stringResource(R.string.chat_resolving_address)
                                                else -> stringResource(R.string.chat_message_placeholder)
                                            },
                                            color = C.textMuted, fontSize = 15.sp,
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                                        focusedContainerColor = C.bg, unfocusedContainerColor = C.bg,
                                        cursorColor = C.accent, focusedTextColor = C.text, unfocusedTextColor = C.text,
                                    ),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp),
                                    maxLines = 4,
                                    enabled = (isGroupMode || !resolvedSbbsAddress.isNullOrEmpty()) && !uploading && !isDeletedAccount,
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                )

                                // Right side: animated morph icons ↔ send
                                AnimatedContent(
                                    targetState = input.inputText.text.isNotBlank() || input.pendingFile != null || uploading,
                                    transitionSpec = {
                                        (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.4f) +
                                            slideInVertically(tween(180)) { it / 4 })
                                            .togetherWith(fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.4f) +
                                                slideOutVertically(tween(150)) { -it / 4 })
                                    },
                                    label = "rightIcons",
                                ) { showSend ->
                                    if (showSend) {
                                        if (uploading) {
                                            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(C.accent), contentAlignment = Alignment.Center) {
                                                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = C.textDark, strokeWidth = 2.dp)
                                            }
                                        } else {
                                            Box(
                                                modifier = Modifier.size(48.dp).clip(CircleShape).background(C.accent)
                                                    .pointerInput(Unit) {
                                                        detectTapGestures(onTap = { onSend() }, onLongPress = { input.showSchedulePicker = true })
                                                    },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.chat_send_message), tint = C.textDark, modifier = Modifier.size(22.dp))
                                            }
                                        }
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(48.dp)) {
                                            IconButton(onClick = { onShowCommandMenuChange(!showCommandMenu) }, modifier = Modifier.size(42.dp)) {
                                                Box(
                                                    modifier = Modifier.size(32.dp).clip(CircleShape)
                                                        .background(if (showCommandMenu) C.accent.copy(alpha = 0.15f) else Color.Transparent),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Text("/", color = C.textSecondary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            IconButton(
                                                onClick = { media.showAttachPicker = true },
                                                enabled = !uploading && !com.privimemobile.chat.transport.IpfsTransport.uploadInProgress,
                                                modifier = Modifier.size(42.dp),
                                            ) {
                                                Icon(Icons.Default.AttachFile, stringResource(R.string.chat_attach_file), tint = C.textSecondary, modifier = Modifier.size(22.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Right: MicButton / SendButton — NEVER animates, always stable
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.padding(end = 2.dp, bottom = 2.dp),
                ) {
                    if (voice.voiceRecording && voice.voiceLocked) {
                        // ── Locked mode: large send button (Telegram-style) ──
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(C.accent)
                                .clickable {
                                    val result = voice.voiceRecorder?.stop()
                                    if (result != null) {
                                        scope.launch { onSendVoice(result) }
                                    }
                                    voice.voiceRecording = false
                                    voice.voiceLocked = false
                                    voice.voiceRecorder = null
                                    voice.micIsRecordingVisual = false
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                stringResource(R.string.chat_send_message),
                                tint = Color.White,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    } else if (input.inputText.text.isEmpty() && input.pendingFile == null && !voice.voicePaused) {
                        // ── Normal + recording (unlocked): mic button with swipe-to-lock ──
                        MicButton(
                            isRecordingVisual = voice.micIsRecordingVisual,
                            slideOffset = voice.micSlideOffset,
                            hasRecordPermission = voice.hasRecordPermission,
                            onRecordPermissionRequest = {
                                onRequestRecordPermission()
                            },
                            scope = scope,
                            view = view,
                            context = context,
                            onStartRecording = {
                                voice.voiceRecorder = com.privimemobile.chat.voice.VoiceRecorder(
                                    context = context,
                                    amplitudeCallback = { },
                                    onMaxDurationReached = {
                                        val result = voice.voiceRecorder?.stop()
                                        if (result != null && result.durationMs >= 700L) {
                                            scope.launch { onSendVoice(result) }
                                        } else if (result != null) {
                                            result.file.delete()
                                        }
                                        voice.micIsRecordingVisual = false
                                        voice.voiceRecording = false
                                        voice.voiceLocked = false
                                        voice.voiceRecorder = null
                                    }
                                ).also { recorder ->
                                    if (recorder.start() != null) {
                                        voice.voiceRecording = true
                                        voice.micIsRecordingVisual = true
                                    }
                                }
                            },
                            onSendRecording = {
                                voice.voiceRecorder?.stop()?.let { result ->
                                    if (result.durationMs >= 700L) {
                                        scope.launch { onSendVoice(result) }
                                    } else {
                                        result.file.delete()
                                    }
                                }
                                voice.voiceRecording = false
                                voice.voiceRecorder = null
                                voice.micIsRecordingVisual = false
                                voice.micSlideOffset = 0f
                            },
                            onCancelRecording = {
                                voice.voiceRecorder?.cancel()
                                voice.micIsRecordingVisual = false
                                voice.voiceRecording = false
                                voice.voiceRecorder = null
                            },
                            onShowHint = { show ->
                                voice.micShowRecordHint = show
                            },
                            onLockSwipe = {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                voice.voiceLocked = true
                            },
                        )
                    }
                }
            }
        } // close Surface (input bar)

        // ── Command menu popup (floats above input bar, overlaying messages) ──
        if (showCommandMenu) {
            ChatCommandMenu(
                isGroupMode = isGroupMode,
                onCommandSelect = { cmd ->
                    input.setInputText(cmd)
                    onShowCommandMenuChange(false)
                },
            )
        }

        // ── @mention autocomplete popup (floats above input bar, overlaying messages) ──
        if (showMentionMenu && filteredMembers.isNotEmpty()) {
            MentionAutocompleteMenu(
                members = filteredMembers,
                onSelect = { member ->
                    val text = input.inputText.text
                    val before = text.substring(0, mentionStartIdx)
                    val after = if (input.inputText.selection.start < text.length) text.substring(input.inputText.selection.start) else ""
                    val newText = "$before@${member.handle} $after"
                    input.inputText = androidx.compose.ui.text.input.TextFieldValue(
                        text = newText,
                        selection = androidx.compose.ui.text.TextRange(before.length + member.handle.length + 2),
                    )
                    onShowMentionMenuChange(false)
                },
            )
        }

        // ── Record hint tooltip (anchored to input bar top-right, floats above into chat) ──
        if (voice.micShowRecordHint) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-12).dp, y = (-28).dp),
            ) {
                com.privimemobile.ui.components.RecordHintTooltip(text = stringResource(R.string.chat_voice_hold_to_record))
            }
        }

        // ── Floating indicator: lock pill (recording), pause circle (locked), mic circle (paused) ──
        if (voice.voiceRecording && !voice.voicePaused) {
            if (voice.voiceLocked) {
                // Pause circle (locked — tap to pause, see waveform preview, then resume or send)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-10).dp, y = (-100).dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2A2D2E).copy(alpha = 0.9f))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                val ok = voice.voiceRecorder?.pause()
                                if (ok == true) {
                                    // Populate preview data from live recorder
                                    val amps = voice.voiceRecorder?.getAmplitudes() ?: emptyList()
                                    voice.voicePreviewWaveform = com.privimemobile.chat.voice.VoiceRecorder.Companion.packWaveform(amps)
                                    voice.voicePauseDuration = voice.voiceRecorder?.getDurationMs() ?: 0L
                                    voice.voicePreviewDuration = voice.voicePauseDuration
                                    voice.voiceRecordDuration = voice.voicePauseDuration
                                    voice.voiceLocked = false
                                    voice.voicePaused = true
                                    voice.micIsRecordingVisual = false
                                    voice.micSlideOffset = 0f
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White.copy(alpha = 0.9f)),
                            )
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White.copy(alpha = 0.9f)),
                            )
                        }
                    }
                }
            } else {
                // Lock pill (recording — swipe up to lock)
                VoiceLockIndicator(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-8).dp, y = (-100).dp),
                )
            }
        }
        if (voice.voicePaused) {
            // Mic circle (paused — tap to resume recording in the same session)
            // Trash is in VoicePreviewBar (left side of the bar), so no floating trash needed
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-10).dp, y = (-100).dp),
            ) {
                // Mic resume button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2A2D2E).copy(alpha = 0.9f))
                        .clickable {
                                // Resume the existing recording session — return to locked state
                                voice.voicePaused = false
                                voice.voiceRecording = true
                                voice.voiceLocked = true
                                voice.voiceRecorder?.resume()
                                voice.micIsRecordingVisual = true
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            stringResource(R.string.chat_mic_desc),
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
            }
        }
    } // close Box (input bar + tooltip overlay)
}
