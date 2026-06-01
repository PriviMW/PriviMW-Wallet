package com.privimemobile.ui.chat.message

import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.privimemobile.R
import com.privimemobile.chat.poll.PollLogic
import com.privimemobile.protocol.ChatMessage
import com.privimemobile.protocol.Helpers
import com.privimemobile.ui.chat.format.formatMessageTime
import com.privimemobile.ui.chat.format.parseMarkdown
import com.privimemobile.ui.chat.media.saveFileToDownloads
import com.privimemobile.ui.components.VoiceMessageBubble
import com.privimemobile.ui.theme.C
import com.privimemobile.wallet.assetTicker
import org.json.JSONObject

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    msg: ChatMessage,
    filePath: String?,
    downloadStatus: String?,
    onDownload: (cid: String, key: String, iv: String, mime: String, inlineData: String?) -> Unit,
    onReply: () -> Unit = {},
    onTap: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onFullscreenImage: () -> Unit = {},
    reactions: List<Triple<String, Int, Boolean>> = emptyList(),
    onReactionTap: (emoji: String, msgTs: Long, isMine: Boolean) -> Unit = { _, _, _ -> },
    onReactionLongPress: (emoji: String, msgTs: Long) -> Unit = { _, _ -> },
    isHighlighted: Boolean = false,
    isSelected: Boolean = false,
    onPollVote: (optionIndex: Int) -> Unit = {},
    myHandle: String? = null,
    isGroupMode: Boolean = false,
    onSenderTap: (String) -> Unit = {},
    groupMemberNames: Map<String, String> = emptyMap(),
    isFirstInCluster: Boolean = true,
    isLastInCluster: Boolean = true,
    showTimestamp: Boolean = true,
    onScrollToReply: (Long) -> Unit = {},
    attachmentExtras: String? = null, // JSON from AttachmentEntity.extras for voice waveform/duration
) {
    val context = LocalContext.current
    val bubbleView = androidx.compose.ui.platform.LocalView.current
    var pendingOpenUrl by remember { mutableStateOf<String?>(null) }
    val isMine = msg.sent
    val isSticker = msg.type == "sticker"
    val isFileMsg = (msg.file?.cid ?: "").isNotEmpty()
    val fileMime = msg.file?.mime ?: ""
    val isImage = (msg.type == "file" || msg.type == "sticker") && Helpers.isImageMime(fileMime)

    // Group service messages (join/leave/kick/ban) — centered pill
    if (msg.type == "group_service") {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = msg.text,
                color = C.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier
                    .background(C.card, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        return
    }

    // Group invite message — card with Accept/Decline buttons
    if (msg.type == "group_invite" && msg.pollData != null) {
        val inviteData = remember(msg.pollData) {
            try { org.json.JSONObject(msg.pollData) } catch (_: Exception) { null }
        }
        val inviteGroupId = inviteData?.optString("group_id") ?: ""
        val inviteGroupName = inviteData?.optString("group_name") ?: stringResource(R.string.chat_group_name_fallback)
        val invitedBy = inviteData?.optString("invited_by") ?: msg.from
        val inviteMemberCount = inviteData?.optInt("member_count", 0) ?: 0
        val invitePassword = inviteData?.optString("join_password")
        var joining by remember { mutableStateOf(false) }
        val inviteScope = rememberCoroutineScope()
        // Use msg.edited as persistent "responded" flag (survives navigation)
        val alreadyResponded = msg.edited

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = C.card),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 16.dp),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(stringResource(R.string.chat_group_invite), color = C.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(inviteGroupName, color = C.bubbleText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.chat_invite_member_count_format, inviteMemberCount, "@$invitedBy"), color = C.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                if (!alreadyResponded && !joining) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                joining = true
                                // Mark as responded in DB immediately
                                inviteScope.launch {
                                    com.privimemobile.chat.ChatService.db?.messageDao()?.markEdited(msg.id.toLong())
                                }
                                com.privimemobile.chat.ChatService.groups.joinGroup(inviteGroupId, joinPassword = invitePassword) { s, e ->
                                    if (s) {
                                        android.widget.Toast.makeText(context, context.getString(R.string.toast_joining_group, inviteGroupName), android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        joining = false
                                        android.widget.Toast.makeText(context, context.getString(R.string.chat_join_failed) + ": " + e, android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = inviteGroupId.isNotEmpty(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                        ) {
                            Text(stringResource(R.string.chat_accept), color = C.textDark, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                // Mark as responded in DB
                                inviteScope.launch {
                                    com.privimemobile.chat.ChatService.db?.messageDao()?.markEdited(msg.id.toLong())
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(stringResource(R.string.chat_decline), color = C.textSecondary)
                        }
                    }
                } else if (joining) {
                    Text(stringResource(R.string.chat_joining), color = C.accent, fontSize = 13.sp)
                } else {
                    Text(stringResource(R.string.chat_invite_responded), color = C.textMuted, fontSize = 13.sp)
                }
            }
        }
        return
    }

    // Check if message is emoji-only (1-3 emojis, no other text) for large display
    val isEmojiOnly = remember(msg.text) {
        val t = msg.text.trim()
        if (t.isEmpty() || (msg.type != "dm" && msg.type != "group_msg") || msg.file != null || msg.isTip || msg.reply != null || msg.fwdFrom != null) false
        else {
            // Count emoji codepoints
            val codePoints = t.codePoints().toArray()
            codePoints.size in 1..3 && codePoints.all { cp ->
                Character.getType(cp).let { type ->
                    type == Character.OTHER_SYMBOL.toInt() ||
                    type == Character.SURROGATE.toInt() ||
                    type == Character.NON_SPACING_MARK.toInt() ||
                    type == Character.FORMAT.toInt() ||
                    (cp in 0x1F000..0x1FFFF) || // emoticons, symbols
                    (cp in 0x2600..0x27BF) ||    // misc symbols
                    (cp in 0xFE00..0xFE0F) ||    // variation selectors
                    (cp in 0x200D..0x200D) ||     // ZWJ
                    (cp in 0xE0020..0xE007F)      // tags
                }
            }
        }
    }

    // Swipe-left to reply
    var offsetX by remember { mutableStateOf(0f) }
    var swiped by remember { mutableStateOf(false) }
    var swipeHapticFired by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = {
                        bubbleView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onLongPress()
                    },
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX < -80f && !swiped) {
                            swiped = true
                            bubbleView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            onReply()
                        }
                        offsetX = 0f
                        swiped = false
                    },
                    onDragCancel = {
                        offsetX = 0f
                        swiped = false
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        val prev = offsetX
                        offsetX = (offsetX + dragAmount).coerceIn(-150f, 0f)
                        // Haptic buzz when crossing swipe threshold
                        if (prev > -80f && offsetX <= -80f && !swipeHapticFired) {
                            swipeHapticFired = true
                            bubbleView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        } else if (offsetX > -80f) {
                            swipeHapticFired = false
                        }
                    },
                )
            },
    ) {
        // Reply arrow hint (visible when swiping) — always on the right side
        if (offsetX < -20f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(horizontal = 8.dp),
            ) {
                Text(
                    "\u21A9",  // ↩ reply arrow
                    color = C.accent.copy(alpha = (-offsetX / 100f).coerceIn(0f, 1f)),
                    fontSize = 18.sp,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = (offsetX / 3f).dp),
            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        ) {
            // Group mode: show sender avatar for first in cluster, spacer for others (keeps alignment)
            if (isGroupMode && !isMine) {
                if (isFirstInCluster) {
                    Box(modifier = Modifier.clickable { onSenderTap(msg.from) }) {
                        com.privimemobile.ui.components.AvatarDisplay(
                            handle = msg.from,
                            size = 32.dp,
                        )
                    }
                } else {
                    Spacer(Modifier.width(32.dp)) // same width as avatar
                }
                Spacer(Modifier.width(6.dp))
            }
            // Wrap group sender name + bubble content
            Column(
                horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
                modifier = Modifier.widthIn(max = 300.dp),
            ) {
            // Group mode: sender name only for first message in cluster
            if (isGroupMode && !isMine && msg.from.isNotEmpty() && isFirstInCluster) {
                val senderColors = listOf(
                    Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFEF5350), Color(0xFFAB47BC),
                    Color(0xFF42A5F5), Color(0xFFFF7043), Color(0xFF66BB6A), Color(0xFFEC407A),
                    Color(0xFFFFA726), Color(0xFF78909C),
                )
                val senderColor = senderColors[kotlin.math.abs(msg.from.hashCode()) % senderColors.size]
                val senderDisplayName = groupMemberNames[msg.from]
                Text(
                    senderDisplayName ?: "@${msg.from}",
                    color = senderColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 1.dp).clickable { onSenderTap(msg.from) },
                )
            }
            // Sticker display (no bubble, larger image — show placeholder even before attachment loads)
            // Sticker pack message — show card with Save button
            if (msg.type == "sticker_pack") {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isMine) C.bubbleMine else C.bubbleOther),
                    modifier = Modifier.widthIn(max = 260.dp).clickable { onTap() },
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("\uD83D\uDCE6 ${stringResource(R.string.chat_sticker_pack_header)}", color = C.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(msg.stickerPackName ?: stringResource(R.string.chat_sticker_pack_label), color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("${msg.stickerPackTotal} stickers", color = C.textSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        if (!isMine && filePath != null) {
                            val packSaved = remember(msg.stickerPackName) {
                                val dir = java.io.File(context.filesDir, "stickers/${msg.stickerPackName}")
                                dir.exists() && (dir.listFiles()?.isNotEmpty() == true)
                            }
                            Button(
                                onClick = {
                                    // Extract ZIP and save stickers
                                    try {
                                        val packDir = java.io.File(context.filesDir, "stickers/${msg.stickerPackName}").also { it.mkdirs() }
                                        val src = java.io.File(filePath!!)
                                        var count = 0
                                        java.util.zip.ZipInputStream(src.inputStream()).use { zis ->
                                            var entry = zis.nextEntry
                                            while (entry != null) {
                                                val name = entry.name.lowercase()
                                                if (!entry.isDirectory && (name.endsWith(".webp") || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".tgs"))) {
                                                    val bytes = zis.readBytes()
                                                    val dest = java.io.File(packDir, entry.name)
                                                    if (!dest.exists()) {
                                                        dest.writeBytes(bytes)
                                                        count++
                                                    }
                                                }
                                                zis.closeEntry()
                                                entry = zis.nextEntry
                                            }
                                        }
                                        Toast.makeText(context, context.getString(R.string.toast_stickers_saved, count, msg.stickerPackName), Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, context.getString(R.string.media_save_failed) + ": ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (packSaved) stringResource(R.string.chat_update_pack) else stringResource(R.string.chat_save_pack), color = C.textDark)
                            }
                        } else if (!isMine) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = C.accent, strokeWidth = 2.dp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Text(formatMessageTime(msg.timestamp), color = C.textSecondary, fontSize = 10.sp)
                            if (isMine) { Spacer(Modifier.width(6.dp)); TickIndicator(read = msg.read, delivered = msg.delivered) }
                        }
                    }
                }
            } else
            if (isSticker) {
                val isTgsSticker = filePath != null && (filePath.endsWith(".tgs", ignoreCase = true)
                    || msg.file?.mime == "application/x-tgsticker"
                    || msg.file?.name?.endsWith(".tgs", ignoreCase = true) == true
                    || run {
                        // Detect GZIP magic bytes (TGS is gzipped Lottie)
                        try {
                            val f = java.io.File(filePath)
                            if (f.exists() && f.length() > 2) {
                                val header = ByteArray(2)
                                f.inputStream().use { it.read(header) }
                                header[0] == 0x1f.toByte() && header[1] == 0x8b.toByte()
                            } else false
                        } catch (_: Exception) { false }
                    })
                Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
                    if (filePath != null && isTgsSticker) {
                        // Animated TGS sticker
                        val lottieJson = remember(filePath) {
                            try { java.util.zip.GZIPInputStream(java.io.File(filePath).inputStream()).bufferedReader().readText() }
                            catch (_: Exception) { null }
                        }
                        if (lottieJson != null) {
                            val composition by com.airbnb.lottie.compose.rememberLottieComposition(
                                com.airbnb.lottie.compose.LottieCompositionSpec.JsonString(lottieJson)
                            )
                            com.airbnb.lottie.compose.LottieAnimation(
                                composition = composition,
                                iterations = com.airbnb.lottie.compose.LottieConstants.IterateForever,
                                modifier = Modifier.size(180.dp).clip(RoundedCornerShape(8.dp))
                                    .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }, onLongPress = { onLongPress() }) },
                            )
                        }
                    } else if (filePath != null) {
                        // Static sticker (WebP/PNG)
                        AsyncImage(
                            model = filePath,
                            contentDescription = stringResource(R.string.chat_sticker_pack_label),
                            modifier = Modifier
                                .size(180.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }, onLongPress = { onLongPress() }) },
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        // Loading placeholder
                        Box(
                            modifier = Modifier.size(180.dp).clip(RoundedCornerShape(8.dp))
                                .background(C.card.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = C.accent, strokeWidth = 2.dp)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        if (msg.stickerEmoji != null) { Text(msg.stickerEmoji, fontSize = 10.sp); Spacer(Modifier.width(3.dp)) }
                        if (msg.pinned) { Text("\uD83D\uDCCC", fontSize = 10.sp); Spacer(Modifier.width(3.dp)) }
                        Text(formatMessageTime(msg.timestamp), color = C.textSecondary, fontSize = 10.sp)
                        if (isMine) {
                            Spacer(Modifier.width(6.dp))
                            TickIndicator(read = msg.read, delivered = msg.delivered)
                        }
                    }
                    if (msg.stickerPackName != null) {
                        Text(msg.stickerPackName, color = C.textMuted, fontSize = 9.sp)
                    }
                }
            } else
            // Large emoji display (no bubble)
            if (isEmojiOnly) {
                Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
                    Text(msg.text, fontSize = 48.sp, lineHeight = 56.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (msg.pinned) { Text("\uD83D\uDCCC", fontSize = 10.sp); Spacer(Modifier.width(3.dp)) }
                        Text(formatMessageTime(msg.timestamp), color = C.textSecondary, fontSize = 10.sp)
                        if (isMine) {
                            Spacer(Modifier.width(6.dp))
                            TickIndicator(read = msg.read, delivered = msg.delivered)
                        }
                    }
                }
            } else {
            // Telegram-style bubble corners: grouped messages get small inner corners
            val bigR = 16.dp; val smallR = 4.dp
            val bubbleShape = if (isMine) RoundedCornerShape(
                topStart = bigR,
                topEnd = if (isFirstInCluster) bigR else smallR,
                bottomStart = bigR,
                bottomEnd = if (isLastInCluster) bigR else smallR,
            ) else RoundedCornerShape(
                topStart = if (isFirstInCluster) bigR else smallR,
                topEnd = bigR,
                bottomStart = if (isLastInCluster) bigR else smallR,
                bottomEnd = bigR,
            )
            Card(
                shape = bubbleShape,
                colors = CardDefaults.cardColors(
                    containerColor = androidx.compose.animation.animateColorAsState(
                        targetValue = when {
                            isSelected -> C.accent.copy(alpha = 0.25f)
                            isHighlighted -> C.accent.copy(alpha = 0.3f)
                            isMine -> C.bubbleMine
                            else -> C.bubbleOther
                        },
                        animationSpec = tween(180),
                        label = "bubbleColor",
                    ).value,
                ),
                modifier = Modifier.widthIn(max = if (isImage) 300.dp else 280.dp),
            ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // Forwarded label
                if (msg.fwdFrom != null) {
                    Text(
                        context.getString(R.string.chat_forwarded_from, msg.fwdFrom),
                        color = C.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }

                // Tip label — parse →@handle prefix for group tips
                if (msg.isTip) {
                    val assetLabel = "${Helpers.formatBeam(msg.tipAmount)} ${com.privimemobile.wallet.assetTicker(msg.tipAssetId)}"
                    val tipTarget = if (msg.text.startsWith("\u2192@")) {
                        msg.text.lineSequence().first().removePrefix("\u2192")
                    } else null
                    val tipLabel = if (tipTarget != null) context.getString(R.string.chat_tip_to, tipTarget, assetLabel) else context.getString(R.string.chat_tip_simple, assetLabel)
                    Text(
                        tipLabel,
                        color = C.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }

                // Reply display (Telegram-style: accent line + sender name + quoted text, tappable)
                if (msg.reply != null) {
                    val senderColors = listOf(
                        Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFEF5350), Color(0xFFAB47BC),
                        Color(0xFF42A5F5), Color(0xFFFF7043), Color(0xFF66BB6A), Color(0xFFEC407A),
                    )
                    val quoteSender = msg.replySender
                    val quoteColor = if (quoteSender != null) senderColors[kotlin.math.abs(quoteSender.hashCode()) % senderColors.size] else C.accent
                    Surface(
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .clickable { if (msg.replyTs > 0) onScrollToReply(msg.replyTs) },
                        shape = RoundedCornerShape(6.dp),
                        color = C.bg.copy(alpha = 0.5f),
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp).height(IntrinsicSize.Min),
                        ) {
                            Box(
                                modifier = Modifier.width(2.5.dp).fillMaxHeight()
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(quoteColor)
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                if (quoteSender != null) {
                                    val senderName = if (quoteSender == myHandle) stringResource(R.string.chat_you_label)
                                        else groupMemberNames[quoteSender] ?: "@$quoteSender"
                                    Text(
                                        senderName,
                                        color = quoteColor,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                    )
                                }
                                Text(
                                    msg.reply,
                                    color = C.textSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                // File content
                if (isFileMsg) {
                    val fName = msg.file?.name ?: ""
                    val fMime = msg.file?.mime ?: "application/octet-stream"
                    val isVoice = Helpers.isVoiceMime(fMime) && !isImage

                    if (isVoice) {
                        // Voice message — render waveform bubble
                        val voiceData = remember(attachmentExtras) {
                            try {
                                if (attachmentExtras != null) {
                                    val obj = org.json.JSONObject(attachmentExtras)
                                    val waveformB64 = obj.optString("waveform")
                                    val durationMs = obj.optLong("duration_ms", 0)
                                    val waveformBytes = if (waveformB64.isNotEmpty()) {
                                        try { android.util.Base64.decode(waveformB64, android.util.Base64.DEFAULT) } catch (_: Exception) { null }
                                    } else null
                                    Pair(waveformBytes, durationMs)
                                } else null
                            } catch (_: Exception) { null }
                        }
                        val waveform = voiceData?.first
                        val durationSec = (voiceData?.second ?: 0L) / 1000

                        // Parse extras from attachment for waveform/duration
                        com.privimemobile.ui.components.VoiceMessageBubble(
                            id = msg.id,
                            durationSecs = durationSec.toInt(),
                            waveform = waveform,
                            filePath = filePath,
                            isMine = isMine,
                            onLongPress = onLongPress,
                        )
                    } else {
                        // Regular file (image or document)
                        FileContent(
                            cid = msg.file?.cid ?: "",
                            fileName = fName,
                            fileSize = msg.file?.size ?: 0L,
                            filePath = filePath,
                            downloadStatus = downloadStatus,
                            isImage = isImage,
                            onDownload = {
                                onDownload(
                                    msg.file?.cid ?: "",
                                    msg.file?.key ?: "",
                                    msg.file?.iv ?: "",
                                    fMime,
                                    msg.file?.data,
                                )
                            },
                            onSave = {
                                if (filePath != null) {
                                    saveFileToDownloads(context, filePath, fName, fMime)
                                }
                            },
                            onFullscreen = onFullscreenImage,
                        )
                    }
                }

                // Poll display
                if (msg.type == "poll" && msg.pollData != null) {
                    val pollQuestion = remember(msg.pollData) {
                        try { org.json.JSONObject(msg.pollData).optString("question", msg.text) } catch (_: Exception) { msg.text }
                    }
                    val pollClosed = remember(msg.pollData) {
                        com.privimemobile.chat.poll.PollLogic.isClosed(msg.pollData)
                    }
                    val userHasVoted = remember(msg.pollData, myHandle) {
                        myHandle != null && com.privimemobile.chat.poll.PollLogic.hasVoted(msg.pollData, myHandle)
                    }
                    val canVote = !pollClosed && !userHasVoted
                    data class PollOpt(val text: String, val voteCount: Int, val voters: List<String>)
                    val pollOptions = remember(msg.pollData) {
                        try {
                            val opts = org.json.JSONObject(msg.pollData).optJSONArray("options")
                            val list = mutableListOf<PollOpt>()
                            if (opts != null) {
                                for (i in 0 until opts.length()) {
                                    val opt = opts.getJSONObject(i)
                                    val v = opt.optJSONArray("voters")
                                    val voterList = mutableListOf<String>()
                                    if (v != null) { for (j in 0 until v.length()) voterList.add(v.getString(j)) }
                                    list.add(PollOpt(opt.optString("text", ""), voterList.size, voterList))
                                }
                            }
                            list.toList()
                        } catch (_: Exception) { emptyList<PollOpt>() }
                    }
                    val totalVotes = pollOptions.sumOf { it.voteCount }
                    Text("\uD83D\uDCCA ${stringResource(R.string.chat_poll_label)}", color = C.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    if (pollClosed) {
                        Text(
                            stringResource(R.string.chat_poll_closed_label),
                            color = C.textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                    Text(pollQuestion, color = C.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                    pollOptions.forEachIndexed { optIdx, opt ->
                        val myVote = myHandle != null && myHandle in opt.voters
                        val pct = if (totalVotes > 0) (opt.voteCount * 100 / totalVotes) else 0
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (myVote) C.accent.copy(alpha = 0.2f) else C.bg.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                .clickable(enabled = canVote) { onPollVote(optIdx) },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (myVote) {
                                    Text("\u2713 ", color = C.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(opt.text, color = C.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                if (totalVotes > 0) {
                                    Text("$pct%", color = if (myVote) C.accent else C.textSecondary, fontSize = 12.sp)
                                    Spacer(Modifier.width(4.dp))
                                }
                                if (opt.voteCount > 0) {
                                    Text("${opt.voteCount}", color = C.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                // Text content — strip →@handle prefix from tip messages
                val displayText = if (msg.isTip && msg.text.startsWith("\u2192@")) {
                    val lines = msg.text.lines()
                    lines.drop(1).joinToString("\n").trim() // skip "→@handle" line, show caption only
                } else msg.text
                if (displayText.isNotEmpty()) {
                    val annotated = parseMarkdown(displayText)
                    androidx.compose.foundation.text.ClickableText(
                        text = annotated,
                        style = androidx.compose.ui.text.TextStyle(color = C.text, fontSize = 15.sp, lineHeight = 20.sp),
                        onClick = { offset ->
                            val urlAnnotation = annotated.getStringAnnotations(tag = "url", start = offset, end = offset).firstOrNull()
                            if (urlAnnotation != null) {
                                val url = urlAnnotation.item
                                // Show confirmation dialog
                                pendingOpenUrl = url
                            } else {
                                onTap()
                            }
                        },
                    )
                }
                }

                // Meta row — time hidden for non-last in cluster, ticks always shown for sent, edited always shown
                if (showTimestamp || isMine || msg.edited) {
                Spacer(Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (msg.edited) {
                        Text(stringResource(R.string.chat_edited_label), color = C.textMuted, fontSize = 10.sp)
                        Spacer(Modifier.width(4.dp))
                    }
                    if (showTimestamp) {
                        if (msg.pinned) {
                            Text("\uD83D\uDCCC", fontSize = 10.sp)
                            Spacer(Modifier.width(3.dp))
                        }
                        if (msg.expiresAt > 0) {
                            Text("\u23F3", fontSize = 10.sp)
                            Spacer(Modifier.width(3.dp))
                        }
                        Text(formatMessageTime(msg.timestamp), color = C.textSecondary, fontSize = 10.sp)
                    }
                    // Ticks ALWAYS shown for sent messages (user needs delivery status)
                    if (isMine) {
                        Spacer(Modifier.width(6.dp))
                        if (msg.scheduledAt > 0) {
                            val sdf = remember { java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault()) }
                            Text(
                                "\uD83D\uDD52 ${sdf.format(java.util.Date(msg.scheduledAt * 1000))}",
                                color = C.accent, fontSize = 9.sp,
                            )
                        } else {
                            TickIndicator(read = msg.read, delivered = msg.delivered)
                        }
                    }
                }
                } // end if (showTimestamp || isMine || msg.edited)
            }

        }
        } // close else (bubble Card block)
        }  // close group sender name/bubble wrapper Column
        }  // close Card Row
    }  // close Box (swipe container)

    // Reaction pills OUTSIDE gesture area so taps work
    if (reactions.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, start = if (isMine) 0.dp else if (isGroupMode && !isMine) 44.dp else 4.dp, end = if (isMine) 4.dp else 0.dp),
            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                reactions.forEach { (emoji, count, mine) ->
                    // Scale-in animation for each reaction pill
                    var reactionVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { reactionVisible = true }
                    val reactionScale by animateFloatAsState(
                        targetValue = if (reactionVisible) 1f else 0f,
                        animationSpec = spring(dampingRatio = 0.4f, stiffness = 600f),
                        label = "rxScale",
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (mine) C.accent.copy(alpha = 0.2f) else C.border,
                        modifier = Modifier
                            .graphicsLayer { scaleX = reactionScale; scaleY = reactionScale }
                            .combinedClickable(
                                onClick = { onReactionTap(emoji, msg.timestamp, mine) },
                                onLongClick = { onReactionLongPress(emoji, msg.timestamp) },
                            ),
                    ) {
                        Text(
                            if (count > 1) "$emoji $count" else emoji,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    } // close Column
    } // close Box

    // ── URL open confirmation dialog ──
    if (pendingOpenUrl != null) {
        AlertDialog(
            onDismissRequest = { pendingOpenUrl = null },
            containerColor = C.card,
            title = { Text(stringResource(R.string.chat_open_link_title), color = C.text, fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.chat_url_open_warning),
                        color = C.textSecondary,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        pendingOpenUrl!!,
                        color = C.accent,
                        fontSize = 12.sp,
                        modifier = androidx.compose.ui.Modifier
                            .background(Color(0x10FFFFFF), RoundedCornerShape(4.dp))
                            .padding(8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            data = android.net.Uri.parse(pendingOpenUrl)
                            addCategory(android.content.Intent.CATEGORY_BROWSABLE)
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {}
                    pendingOpenUrl = null
                }) {
                    Text(stringResource(R.string.chat_open), color = C.accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingOpenUrl = null }) {
                    Text(stringResource(R.string.general_cancel), color = C.textSecondary)
                }
            },
        )
    }
} // close MessageBubble
