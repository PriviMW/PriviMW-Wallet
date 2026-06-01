package com.privimemobile.ui.chat.chrome

import android.content.Intent
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.R
import com.privimemobile.chat.db.entities.ConversationEntity
import com.privimemobile.chat.db.entities.GroupEntity
import com.privimemobile.ui.chat.ChatChromeState
import com.privimemobile.ui.chat.ChatInputState
import com.privimemobile.protocol.ChatMessage
import com.privimemobile.ui.chat.ChatSearchState
import com.privimemobile.ui.theme.C
import com.privimemobile.protocol.Helpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Telegram-style chat header: back, avatar, title/subtitle, overflow menu. */
@Composable
fun ChatHeader(
    view: View,
    scope: CoroutineScope,
    chrome: ChatChromeState,
    search: ChatSearchState,
    input: ChatInputState,
    messages: List<ChatMessage>,
    handle: String,
    resolvedName: String,
    convKey: String,
    convId: Long,
    conv: ConversationEntity?,
    isGroupMode: Boolean,
    groupId: String?,
    group: GroupEntity?,
    groupMemberCount: Int,
    chatPrefs: android.content.SharedPreferences,
    groupSoundPicker: ActivityResultLauncher<Intent>,
    onBack: () -> Unit,
    onContactInfo: () -> Unit,
    onGroupSettings: () -> Unit,
    onMediaGallery: () -> Unit,
) {
    val context = LocalContext.current

    Surface(color = C.card, shadowElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.general_back),
                    tint = C.text,
                    modifier = Modifier.size(22.dp),
                )
            }
            if (isGroupMode) {
                val gid = groupId ?: ""
                val groupAvatarBmp = remember(gid, group?.avatarHash) {
                    try {
                        val f = java.io.File(context.filesDir, "group_avatars/$gid.webp")
                        if (f.exists()) android.graphics.BitmapFactory.decodeFile(f.absolutePath) else null
                    } catch (_: Exception) {
                        null
                    }
                }
                if (groupAvatarBmp != null) {
                    androidx.compose.foundation.Image(
                        bitmap = groupAvatarBmp.asImageBitmap(),
                        contentDescription = stringResource(R.string.chat_section_groups),
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable { onGroupSettings() },
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(C.accent, CircleShape)
                            .clickable { onGroupSettings() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Group, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            } else {
                Box(modifier = Modifier.clickable { onContactInfo() }) {
                    com.privimemobile.ui.components.AvatarDisplay(
                        handle = handle,
                        displayName = resolvedName,
                        size = 38.dp,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        if (isGroupMode) onGroupSettings() else onContactInfo()
                    },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isGroupMode && group?.isPublic == false) {
                        Icon(
                            Icons.Default.Lock,
                            stringResource(R.string.chat_send_message),
                            tint = C.textSecondary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        resolvedName,
                        color = C.text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isGroupMode) {
                    val typingVer2 by com.privimemobile.chat.ChatService.typingVersion.collectAsState()
                    val groupTypers = if (typingVer2 >= 0) {
                        com.privimemobile.chat.ChatService.getGroupTyping(convKey)
                    } else {
                        emptyList()
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (groupTypers.isNotEmpty()) {
                            val typingText = when (groupTypers.size) {
                                1 -> context.getString(R.string.chat_group_typing_singular, "@${groupTypers[0]}")
                                2 -> context.getString(
                                    R.string.chat_group_typing_two,
                                    "@${groupTypers[0]}",
                                    "@${groupTypers[1]}",
                                )
                                else -> context.getString(R.string.chat_group_typing_multiple, groupTypers.size)
                            }
                            Text(typingText, color = C.accent, fontSize = 12.sp)
                            val infiniteTransition = rememberInfiniteTransition(label = "grpTypingDots")
                            repeat(3) { i ->
                                val offsetY by infiniteTransition.animateFloat(
                                    initialValue = 0f,
                                    targetValue = -3f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(400, easing = FastOutSlowInEasing, delayMillis = i * 120),
                                        repeatMode = RepeatMode.Reverse,
                                    ),
                                    label = "grpDot$i",
                                )
                                Text(
                                    ".",
                                    color = C.accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.offset(y = offsetY.dp),
                                )
                            }
                        } else {
                            Text(
                                stringResource(R.string.group_member_count_format, groupMemberCount),
                                color = C.textSecondary,
                                fontSize = 12.sp,
                            )
                        }
                        if (group?.muted == true) {
                            Icon(
                                Icons.Default.NotificationsOff,
                                stringResource(R.string.chat_overflow_mute),
                                tint = C.textSecondary,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(13.dp),
                            )
                        }
                    }
                } else {
                    val typingVer by com.privimemobile.chat.ChatService.typingVersion.collectAsState()
                    val peerTyping = typingVer >= 0 && com.privimemobile.chat.ChatService.isTyping(convKey)
                    if (peerTyping) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.chat_typing), color = C.accent, fontSize = 12.sp)
                            val infiniteTransition = rememberInfiniteTransition(label = "typingDots")
                            repeat(3) { i ->
                                val offsetY by infiniteTransition.animateFloat(
                                    initialValue = 0f,
                                    targetValue = -3f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(400, easing = FastOutSlowInEasing, delayMillis = i * 120),
                                        repeatMode = RepeatMode.Reverse,
                                    ),
                                    label = "dot$i",
                                )
                                Text(
                                    ".",
                                    color = C.accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.offset(y = offsetY.dp),
                                )
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("@$handle", color = C.textSecondary, fontSize = 12.sp)
                            if (conv?.muted == true) {
                                Icon(
                                    Icons.Default.NotificationsOff,
                                    stringResource(R.string.chat_overflow_mute),
                                    tint = C.textSecondary,
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .size(13.dp),
                                )
                            }
                            if (conv?.isBlocked == true) {
                                Icon(
                                    Icons.Default.Block,
                                    stringResource(R.string.chat_overflow_block),
                                    tint = Color(0xFFEF5350),
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .size(13.dp),
                                )
                            }
                        }
                    }
                }
            }
            Box {
                val menuRotation by animateFloatAsState(
                    targetValue = if (chrome.showOverflowMenu) 90f else 0f,
                    animationSpec = tween(200),
                    label = "menuRotation",
                )
                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        chrome.showOverflowMenu = !chrome.showOverflowMenu
                    },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.chat_overflow_search),
                        tint = if (chrome.showOverflowMenu) C.accent else C.textSecondary,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer { rotationZ = menuRotation },
                    )
                }
                DropdownMenu(
                    expanded = chrome.showOverflowMenu,
                    onDismissRequest = { chrome.showOverflowMenu = false },
                    modifier = Modifier
                        .background(C.card)
                        .widthIn(min = 200.dp),
                ) {
                    ChatHeaderOverflowMenu(
                        chrome = chrome,
                        search = search,
                        input = input,
                        messages = messages,
                        scope = scope,
                        context = context,
                        convKey = convKey,
                        convId = convId,
                        conv = conv,
                        handle = handle,
                        isGroupMode = isGroupMode,
                        groupId = groupId,
                        group = group,
                        chatPrefs = chatPrefs,
                        groupSoundPicker = groupSoundPicker,
                        onContactInfo = onContactInfo,
                        onGroupSettings = onGroupSettings,
                        onMediaGallery = onMediaGallery,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatHeaderOverflowMenu(
    chrome: ChatChromeState,
    search: ChatSearchState,
    input: ChatInputState,
    messages: List<ChatMessage>,
    scope: CoroutineScope,
    context: android.content.Context,
    convKey: String,
    convId: Long,
    conv: ConversationEntity?,
    handle: String,
    isGroupMode: Boolean,
    groupId: String?,
    group: GroupEntity?,
    chatPrefs: android.content.SharedPreferences,
    groupSoundPicker: ActivityResultLauncher<Intent>,
    onContactInfo: () -> Unit,
    onGroupSettings: () -> Unit,
    onMediaGallery: () -> Unit,
) {
    @Composable
    fun OverflowItem(icon: String, label: String, color: Color = C.text, action: () -> Unit) {
        var pressed by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (pressed) C.accent.copy(alpha = 0.15f) else Color.Transparent)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                        onTap = { action() },
                    )
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(icon, fontSize = 16.sp)
            Spacer(Modifier.width(14.dp))
            Text(label, color = color, fontSize = 15.sp)
        }
    }

    fun launchNotifSoundPicker() {
        val intent = android.content.Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_NOTIFICATION)
            putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(R.string.chat_notification_sound))
            putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            val current = chatPrefs.getString("notif_sound_$convKey", null)
            if (current != null && current != "silent") {
                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, android.net.Uri.parse(current))
            }
        }
        groupSoundPicker.launch(intent)
    }

    OverflowItem(
        "\uD83D\uDD0D",
        if (search.showSearch) {
            stringResource(R.string.chat_overflow_close_search)
        } else {
            stringResource(R.string.chat_overflow_search)
        },
    ) {
        search.toggle()
        chrome.showOverflowMenu = false
    }
    OverflowItem("\uD83D\uDDBC", stringResource(R.string.chat_overflow_media)) {
        chrome.showOverflowMenu = false
        onMediaGallery()
    }
    if (isGroupMode) {
        OverflowItem("\u2699\uFE0F", stringResource(R.string.chat_overflow_group_info)) {
            chrome.showOverflowMenu = false
            onGroupSettings()
        }
        OverflowItem("\uD83D\uDD14", context.getString(R.string.chat_overflow_sound, chrome.groupNotifSoundName)) {
            chrome.showOverflowMenu = false
            launchNotifSoundPicker()
        }
    } else {
        OverflowItem("\uD83D\uDC64", stringResource(R.string.chat_overflow_view_profile)) {
            chrome.showOverflowMenu = false
            onContactInfo()
        }
        OverflowItem("\uD83D\uDD14", context.getString(R.string.chat_overflow_sound, chrome.groupNotifSoundName)) {
            chrome.showOverflowMenu = false
            launchNotifSoundPicker()
        }
    }
    OverflowItem("\uD83C\uDFA8", stringResource(R.string.chat_overflow_wallpaper)) {
        chrome.showOverflowMenu = false
        chrome.showWallpaperPicker = true
    }
    OverflowItem(
        "\u23F1",
        if (input.oneShotTimer == 0) {
            stringResource(R.string.chat_overflow_timer_off)
        } else {
            context.getString(R.string.chat_overflow_timer_on, Helpers.formatDuration(context, input.oneShotTimer))
        },
    ) {
        chrome.showOverflowMenu = false
        input.showOneShotTimerPicker = true
    }
    OverflowItem("\uD83D\uDCE4", stringResource(R.string.chat_overflow_export_chat)) {
        chrome.showOverflowMenu = false
        val exportTitle = if (isGroupMode) {
            context.getString(
                R.string.chat_export_group_label,
                group?.name ?: context.getString(R.string.chat_group_name_fallback),
            )
        } else {
            context.getString(R.string.chat_export_dm_label, "@$handle")
        }
        scope.launch {
            val sb = StringBuilder()
            sb.appendLine(exportTitle)
            sb.appendLine(
                context.getString(
                    R.string.chat_export_date,
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date()),
                ),
            )
            sb.appendLine("---")
            messages.forEach { msg ->
                val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date(msg.timestamp * 1000))
                val sender = if (msg.sent) context.getString(R.string.chat_you_label) else "@${msg.from}"
                val text = when {
                    msg.isTip -> "[Tip: ${Helpers.formatBeam(msg.tipAmount)} ${com.privimemobile.wallet.assetTicker(msg.tipAssetId)}] ${msg.text}"
                    msg.type == "file" -> "[File: ${msg.file?.name ?: "file"}] ${msg.text}"
                    else -> msg.text
                }
                sb.appendLine("[$time] $sender: $text")
            }
            withContext(Dispatchers.Main) {
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, sb.toString())
                    putExtra(android.content.Intent.EXTRA_SUBJECT, exportTitle)
                }
                context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.chat_overflow_export_chat)))
            }
        }
    }
    val isMuted = if (isGroupMode) group?.muted == true else conv?.muted == true
    OverflowItem(
        if (isMuted) "\uD83D\uDD14" else "\uD83D\uDD07",
        if (isMuted) stringResource(R.string.chat_overflow_unmute) else stringResource(R.string.chat_overflow_mute),
    ) {
        scope.launch {
            if (isGroupMode && groupId != null) {
                com.privimemobile.chat.ChatService.db?.groupDao()?.setMuted(groupId, !isMuted)
            } else if (convId > 0L) {
                com.privimemobile.chat.ChatService.db?.conversationDao()?.setMuted(convId, !isMuted)
            }
        }
        chrome.showOverflowMenu = false
    }
    if (!isGroupMode) {
        val isBlocked = conv?.isBlocked == true
        OverflowItem(
            if (isBlocked) "\u2705" else "\uD83D\uDEAB",
            if (isBlocked) stringResource(R.string.chat_overflow_unblock) else stringResource(R.string.chat_overflow_block),
            color = if (isBlocked) C.text else C.error,
        ) {
            if (convId > 0L) {
                scope.launch {
                    com.privimemobile.chat.ChatService.db?.conversationDao()?.setBlocked(convId, !isBlocked)
                }
            }
            chrome.showOverflowMenu = false
        }
    }
    HorizontalDivider(color = C.border)
    OverflowItem("\uD83D\uDDD1", stringResource(R.string.chat_clear_history), color = C.error) {
        chrome.showOverflowMenu = false
        chrome.showClearConfirm = true
    }
    OverflowItem(
        "\u274C",
        if (isGroupMode) stringResource(R.string.chat_leave_group) else stringResource(R.string.chat_delete_chat),
        color = C.error,
    ) {
        chrome.showOverflowMenu = false
        chrome.showDeleteConfirm = true
    }
}
