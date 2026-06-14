package com.privimemobile.ui.chat.message

import android.widget.Toast
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.privimemobile.R
import com.privimemobile.chat.db.entities.AttachmentEntity
import com.privimemobile.protocol.ChatMessage
import com.privimemobile.protocol.Helpers
import com.privimemobile.ui.chat.ChatSearchState
import com.privimemobile.ui.chat.FullscreenImageData
import com.privimemobile.ui.chat.FullscreenImageItem
import com.privimemobile.ui.chat.ChatChromeState
import com.privimemobile.ui.chat.ChatContextMenuState
import com.privimemobile.ui.chat.ChatEmojiStickerState
import com.privimemobile.ui.chat.ChatImagePreviewState
import com.privimemobile.ui.chat.ChatInputState
import com.privimemobile.ui.chat.ChatMessageListState
import com.privimemobile.ui.chat.ChatPinState
import com.privimemobile.ui.chat.ChatPollUiState
import com.privimemobile.ui.chat.ChatScrollBadgeState
import com.privimemobile.ui.chat.ChatSelectionState
import com.privimemobile.ui.chat.albumBubbleWidth
import com.privimemobile.ui.chat.format.formatDateSeparator
import com.privimemobile.ui.chat.format.formatMessageTime
import com.privimemobile.ui.chat.message.MessageBubble
import com.privimemobile.ui.chat.message.TickIndicator
import com.privimemobile.ui.chat.scroll.ChatListDerive
import com.privimemobile.ui.chat.scroll.ChatScrollMath
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatMessageList(
    modifier: Modifier = Modifier,
    listState: LazyListState,
    messages: List<ChatMessage>,
    roomMessageCount: Int,
    chrome: ChatChromeState,
    files: ChatMessageListState,
    media: ChatImagePreviewState,
    input: ChatInputState,
    selection: ChatSelectionState,
    menu: ChatContextMenuState,
    emoji: ChatEmojiStickerState,
    search: ChatSearchState,
    pinState: ChatPinState,
    scrollBadge: ChatScrollBadgeState,
    pollUi: ChatPollUiState,
    attachmentMap: Map<Long, AttachmentEntity>,
    reactionMap: Map<Long, List<Triple<String, Int, Boolean>>>,
    initialUnreadCount: Int?,
    unreadBoundaryIndex: Int,
    isGroupMode: Boolean,
    groupId: String?,
    handle: String,
    resolvedSbbsAddress: String?,
    myHandle: String?,
    groupMemberNames: Map<String, String>,
    searchHighlightFromNav: Long,
    lastSendTimeMs: Long,
    onLastSendTimeMsChange: (Long) -> Unit,
    scope: CoroutineScope,
    onDownload: (cid: String, keyHex: String, ivHex: String, mime: String, inlineData: String?) -> Unit,
    onViewContact: (String) -> Unit,
) {
    val context = LocalContext.current

// Messages list + scroll-to-bottom button
val wallpaperBg = when {
    chrome.chatWallpaper.startsWith("custom:") -> {
        val path = chrome.chatWallpaper.removePrefix("custom:").substringBefore("#")
        val file = java.io.File(path)
        if (file.exists()) {
            // Cache decoded bitmap to avoid re-decoding on every recomposition
            val cachedBmp = remember(chrome.chatWallpaper) { android.graphics.BitmapFactory.decodeFile(path) }
            if (cachedBmp != null) {
                Modifier.drawBehind {
                    // Center-crop: scale to fill, crop overflow
                    val bw = cachedBmp.width.toFloat()
                    val bh = cachedBmp.height.toFloat()
                    val cw = size.width
                    val ch = size.height
                    val scale = maxOf(cw / bw, ch / bh)
                    val sw = (cw / scale).toInt()
                    val sh = (ch / scale).toInt()
                    val sx = ((bw - sw) / 2).toInt().coerceAtLeast(0)
                    val sy = ((bh - sh) / 2).toInt().coerceAtLeast(0)
                    val srcRect = android.graphics.Rect(sx, sy, sx + sw, sy + sh)
                    val dstRect = android.graphics.Rect(0, 0, cw.toInt(), ch.toInt())
                    val paint = android.graphics.Paint().apply { isFilterBitmap = true }
                    drawContext.canvas.nativeCanvas.drawBitmap(cachedBmp, srcRect, dstRect, paint)
                }
            } else Modifier
        } else Modifier
    }
    chrome.chatWallpaper == "dark_blue" -> Modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF0D1B2A), Color(0xFF1B2838))))
    chrome.chatWallpaper == "teal" -> Modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF004D40), Color(0xFF00695C))))
    chrome.chatWallpaper == "purple" -> Modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF1A0033), Color(0xFF2D1B69))))
    chrome.chatWallpaper == "midnight" -> Modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF0F0F23), Color(0xFF1A1A3E))))
    chrome.chatWallpaper == "forest" -> Modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF1B3A2D), Color(0xFF2D5016))))
    chrome.chatWallpaper == "sunset" -> Modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF2D1B00), Color(0xFF4A2600))))
    else -> Modifier  // default uses C.bg from parent
}
// Pre-compute album groups outside LazyColumn (composable context)
val reversedMessages = remember(messages) { messages.reversed() }
val albumLayout = remember(reversedMessages) { ChatListDerive.computeAlbumGroups(reversedMessages) }
val albumGroups = albumLayout.groups
val albumSkipIds = albumLayout.skipIds

Box(modifier = modifier.then(wallpaperBg)) {
LazyColumn(
    modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 12.dp),
    state = listState,
    reverseLayout = true,
    verticalArrangement = Arrangement.spacedBy(4.dp),
    contentPadding = PaddingValues(vertical = 8.dp),
    flingBehavior = remember { TelegramFlingBehavior() },
) {
    items(
        reversedMessages,
        key = { msg -> ChatListDerive.messageItemKey(msg) },
    ) { msg ->
        // Skip non-first album images (rendered as grid in the first item)
        if (msg.id in albumSkipIds) return@items
        // Telegram-style message appear animation
        var appeared by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { appeared = true }
        val easeOutQuint = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
        val msgAlpha by animateFloatAsState(
            targetValue = if (appeared) 1f else 0f,
            animationSpec = tween(400, easing = easeOutQuint),
            label = "msgAlpha",
        )
        val msgScale by animateFloatAsState(
            targetValue = if (appeared) 1f else if (msg.sent) 0.6f else 0.85f,
            animationSpec = if (msg.sent) spring(dampingRatio = 0.6f, stiffness = 400f)
                else tween(400, easing = easeOutQuint),
            label = "msgScale",
        )
        val msgOffsetY by animateFloatAsState(
            targetValue = if (appeared) 0f else if (msg.sent) 40f else 15f,
            animationSpec = tween(if (msg.sent) 500 else 350, easing = easeOutQuint),
            label = "msgOffset",
        )

        val index = ChatListDerive.indexInReversedList(reversedMessages, msg)
        val prevMsg = if (index < reversedMessages.size - 1) reversedMessages[index + 1] else null // older
        val nextMsg = if (index > 0) reversedMessages[index - 1] else null // newer
        val curDateLabel = formatDateSeparator(msg.timestamp, context)
        val cluster = ChatListDerive.computeClusterFlags(
            index,
            reversedMessages,
            prevMsg?.let { formatDateSeparator(it.timestamp, context) },
            curDateLabel,
            nextMsg?.let { formatDateSeparator(it.timestamp, context) },
        )
        val showDateSep = cluster.showDateSep
        val isFirstInCluster = cluster.isFirstInCluster
        val isLastInCluster = cluster.isLastInCluster
        val showTimestamp = cluster.showTimestamp

        Column(
            modifier = Modifier
                .animateItem(
                    fadeInSpec = tween(300, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)),
                    fadeOutSpec = tween(300, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)),
                    placementSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
                )
                .graphicsLayer {
                    this.alpha = msgAlpha
                    scaleX = msgScale
                    scaleY = msgScale
                    translationY = msgOffsetY
                    // Sent messages scale from bottom-right, received from bottom-left
                    transformOrigin = if (msg.sent)
                        androidx.compose.ui.graphics.TransformOrigin(1f, 1f)
                    else
                        androidx.compose.ui.graphics.TransformOrigin(0f, 1f)
                },
        ) {
            if (showDateSep) {
                // Centered pill date separator — tap to jump to date
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = C.card.copy(alpha = 0.8f),
                        modifier = Modifier.clickable { input.showDatePicker = true },
                    ) {
                        Text(
                            formatDateSeparator(msg.timestamp, context),
                            color = C.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // "X new messages" unread divider — shown at the boundary between read and unread
            val unreadCount = initialUnreadCount ?: 0
            if (unreadBoundaryIndex >= 0 && index == unreadBoundaryIndex) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = C.accent.copy(alpha = 0.15f),
                    ) {
                        Text(
                            context.resources.getQuantityString(R.plurals.chat_new_messages, unreadCount, unreadCount),
                            color = C.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // Selection mode: checkbox + tap toggles selection
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selection.selectionMode) {
                    Checkbox(
                        checked = msg.id in selection.selectedIds,
                        onCheckedChange = { selection.toggleSelected(msg.id) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = C.accent,
                            uncheckedColor = C.textSecondary,
                            checkmarkColor = C.textDark,
                        ),
                        modifier = Modifier.size(32.dp),
                    )
                }
            // Album grid for grouped consecutive images — Telegram-style adaptive layouts
            val albumIds = albumGroups[msg.id]
            if (albumIds != null && albumIds.size > 1) {
                val albumMsgs = albumIds.mapNotNull { id -> reversedMessages.firstOrNull { it.id == id } }
                val isMine = msg.sent
                val screenWidthDp = LocalConfiguration.current.screenWidthDp
                val albumMaxWidth = albumBubbleWidth(screenWidthDp)

                // Helper: open fullscreen viewer starting from a specific album image
                fun openAlbumViewer(startMsg: ChatMessage) {
                    val items = albumMsgs.mapNotNull { m ->
                        val path = files.filePaths[m.file?.cid ?: ""] ?: return@mapNotNull null
                        FullscreenImageItem(
                            filePath = path,
                            fileName = m.file?.name ?: context.getString(R.string.chat_pinned_file),
                            msgId = m.id.toLong(),
                            msgTs = m.timestamp,
                            isMine = m.sent,
                        )
                    }
                    if (items.isEmpty()) return
                    val startIndex = items.indexOfFirst { it.msgId == startMsg.id.toLong() }
                    if (startIndex < 0) {
                        // Tapped image is still downloading (was filtered out of items).
                        // Don't open the viewer on a different image — wait for the
                        // download to finish, or surface a retry toast.
                        val cid = startMsg.file?.cid ?: ""
                        if (cid.isNotEmpty() && files.downloadStatuses[cid] != "downloading") {
                            onDownload(
                                cid,
                                startMsg.file?.key ?: "",
                                startMsg.file?.iv ?: "",
                                startMsg.file?.mime ?: "image/jpeg",
                                startMsg.file?.data,
                            )
                            android.widget.Toast.makeText(
                                context, R.string.chat_image_downloading, android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            android.widget.Toast.makeText(
                                context, R.string.chat_image_downloading, android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                        return
                    }
                    media.fullscreenImage = FullscreenImageData(items, startIndex)
                }

                // Reusable cell composable for each album image
                @Composable
                fun AlbumCell(albumMsg: ChatMessage, modifier: Modifier = Modifier) {
                    val fp = files.filePaths[albumMsg.file?.cid ?: ""]
                    Box(
                        modifier = modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(enabled = fp != null) { openAlbumViewer(albumMsg) },
                    ) {
                        if (fp != null) {
                            AsyncImage(
                                model = java.io.File(fp),
                                contentDescription = stringResource(R.string.chat_media_section),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize().background(C.bg),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = C.accent, strokeWidth = 2.dp,
                                )
                            }
                            // Trigger download
                            val cid = albumMsg.file?.cid ?: ""
                            if (cid.isNotEmpty() && files.downloadStatuses[cid] != "downloading") {
                                LaunchedEffect(cid) {
                                    onDownload(
                                        cid,
                                        albumMsg.file?.key ?: "",
                                        albumMsg.file?.iv ?: "",
                                        albumMsg.file?.mime ?: "image/jpeg",
                                        albumMsg.file?.data,
                                    )
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
                    ) {
                        if (isGroupMode && !isMine) {
                            if (isFirstInCluster) {
                                Box(modifier = Modifier.clickable { onViewContact(msg.from) }) {
                                    com.privimemobile.ui.components.AvatarDisplay(
                                        handle = msg.from,
                                        size = 32.dp,
                                    )
                                }
                            } else {
                                Spacer(Modifier.width(32.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                        }
                        Column(
                            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
                            modifier = Modifier.widthIn(max = albumMaxWidth),
                        ) {
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
                                    modifier = Modifier.padding(start = 4.dp, bottom = 1.dp).clickable { onViewContact(msg.from) },
                                )
                            }
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isMine) C.bubbleMine else C.bubbleOther),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            when (albumMsgs.size) {
                                2 -> {
                                    // 2 photos: side by side, nearly square
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        AlbumCell(albumMsgs[0], modifier = Modifier.weight(1f).aspectRatio(1f))
                                        AlbumCell(albumMsgs[1], modifier = Modifier.weight(1f).aspectRatio(1f))
                                    }
                                }
                                3 -> {
                                    // 3 photos: 1 large left (2 rows) + 2 stacked right — Telegram signature
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        AlbumCell(albumMsgs[0], modifier = Modifier.weight(1f).aspectRatio(0.667f))
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            AlbumCell(albumMsgs[1], modifier = Modifier.fillMaxWidth().aspectRatio(1.334f))
                                            AlbumCell(albumMsgs[2], modifier = Modifier.fillMaxWidth().aspectRatio(1.334f))
                                        }
                                    }
                                }
                                else -> {
                                    // 4+ photos: 2-column grid, square cells, "+N" overlay on last
                                    val maxVisible = if (albumMsgs.size > 6) 6 else albumMsgs.size
                                    val visibleMsgs = albumMsgs.take(maxVisible)
                                    val remaining = albumMsgs.size - maxVisible
                                    visibleMsgs.chunked(2).forEachIndexed { rowIndex, row ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            row.forEachIndexed { cellIndex, albumMsg ->
                                                val isLastCell = rowIndex == (maxVisible - 1) / 2
                                                    && cellIndex == row.size - 1
                                                    && remaining > 0
                                                Box(
                                                    modifier = Modifier.weight(1f).aspectRatio(1f),
                                                ) {
                                                    AlbumCell(albumMsg)
                                                    // "+N" overlay on last visible cell
                                                    if (isLastCell) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
                                                                .clip(RoundedCornerShape(4.dp)),
                                                            contentAlignment = Alignment.Center,
                                                        ) {
                                                            Text(
                                                                "+$remaining",
                                                                color = androidx.compose.ui.graphics.Color.White,
                                                                fontSize = 22.sp,
                                                                fontWeight = FontWeight.Bold,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            // Pad incomplete rows
                                            if (row.size < 2) {
                                                Spacer(Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                            // Time + status row
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(context.getString(R.string.chat_album_photos, albumMsgs.size), color = C.textMuted, fontSize = 10.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(formatMessageTime(msg.timestamp), color = C.textSecondary, fontSize = 10.sp)
                                if (isMine) {
                                    Spacer(Modifier.width(6.dp))
                                    val lastMsg = albumMsgs.last()
                                    TickIndicator(read = lastMsg.read, delivered = lastMsg.delivered)
                                }
                            }
                        }
                    }
                        }
                    }
                }
            } else
            MessageBubble(
                msg = msg,
                filePath = files.filePaths[msg.file?.cid ?: ""],
                downloadStatus = files.downloadStatuses[msg.file?.cid ?: ""],
                attachmentExtras = attachmentMap[msg.id.toLong()]?.extras,
                isFirstInCluster = isFirstInCluster,
                isLastInCluster = isLastInCluster,
                showTimestamp = showTimestamp,
                onDownload = { cid, key, iv, mime, data ->
                    onDownload(cid, key, iv, mime, data)
                },
                onReply = if (selection.selectionMode) {{ /* no-op in selection mode */ }} else {{ input.replyingTo = msg }},
                onTap = {
                    if (selection.selectionMode) {
                        selection.toggleSelected(msg.id)
                    } else if (msg.type == "sticker" && msg.stickerPackId != null) {
                        // Sticker tap → view pack
                        emoji.viewPackId = msg.stickerPackId
                    } else {
                        menu.contextMenuMsg = msg
                    }
                },
                onLongPress = {
                    if (!selection.selectionMode) {
                        if (msg.type == "sticker") {
                            menu.contextMenuMsg = msg
                        } else {
                            selection.enterSelectionWith(msg.id)
                        }
                    } else {
                        selection.toggleSelected(msg.id)
                    }
                },
                onFullscreenImage = {
                    val fp = files.filePaths[msg.file?.cid ?: ""]
                    if (fp != null) media.fullscreenImage = FullscreenImageData(fp, msg.file?.name ?: context.getString(R.string.chat_pinned_file), msg.id.toLong(), msg.timestamp, msg.sent)
                },
                isSelected = selection.selectionMode && msg.id in selection.selectedIds,
                onPollVote = { optIdx ->
                    val now = System.currentTimeMillis()
                    if (now - lastSendTimeMs < 3000) {
                        Toast.makeText(context, R.string.toast_wait_moment, Toast.LENGTH_SHORT).show()
                        return@MessageBubble
                    }
                    val pollData = msg.pollData
                    if (pollData == null) return@MessageBubble
                    scope.launch {
                        val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
                        val voteHandle = state?.myHandle ?: return@launch
                        val freshPollData = com.privimemobile.chat.ChatService.db
                            ?.messageDao()?.findById(msg.id.toLong())?.pollData
                            ?: pollData
                        when (val result = com.privimemobile.chat.poll.PollLogic.applyVote(
                            freshPollData, voteHandle, optIdx,
                        )) {
                            is com.privimemobile.chat.poll.PollLogic.VoteResult.Rejected -> {
                                val toastRes = when (result.reason) {
                                    com.privimemobile.chat.poll.PollLogic.VoteRejectReason.CLOSED ->
                                        R.string.toast_poll_closed
                                    com.privimemobile.chat.poll.PollLogic.VoteRejectReason.ALREADY_VOTED ->
                                        R.string.toast_poll_already_voted
                                    com.privimemobile.chat.poll.PollLogic.VoteRejectReason.INVALID_OPTION ->
                                        return@launch
                                }
                                Toast.makeText(context, toastRes, Toast.LENGTH_SHORT).show()
                            }
                            is com.privimemobile.chat.poll.PollLogic.VoteResult.Applied -> {
                                onLastSendTimeMsChange(now)
                                val voteMsgId = msg.id.toLong()
                                com.privimemobile.chat.ChatService.db?.messageDao()?.updatePollData(
                                    voteMsgId, result.pollData,
                                )
                                pollUi.patch(voteMsgId, result.pollData)
                                val votePayload = mapOf(
                                    "v" to 1, "t" to "poll_vote",
                                    "ts" to System.currentTimeMillis() / 1000,
                                    "from" to voteHandle,
                                    "to" to (if (isGroupMode) groupId!! else handle),
                                    "msg_ts" to msg.timestamp,
                                    "option" to optIdx,
                                )
                                if (isGroupMode && groupId != null) {
                                    com.privimemobile.chat.ChatService.groups.sendGroupPayload(
                                        groupId, votePayload,
                                    )
                                } else {
                                    val walletId = resolvedSbbsAddress
                                    if (!walletId.isNullOrEmpty()) {
                                        com.privimemobile.chat.ChatService.sbbs.sendWithRetry(
                                            walletId, votePayload,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                myHandle = myHandle,
                reactions = reactionMap[msg.timestamp] ?: emptyList(),
                onReactionTap = { emoji, msgTs, isMine ->
                    // Tap: mine → remove, not-mine → send
                    scope.launch {
                        val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
                        if (state?.myHandle != null) {
                            if (isMine) {
                                // Remove reaction
                                val nowTs = System.currentTimeMillis() / 1000
                                com.privimemobile.chat.ChatService.db!!.reactionDao().remove(msgTs, state.myHandle!!, emoji, nowTs)
                                val unreactPayload = mapOf(
                                    "v" to 1, "t" to "unreact",
                                    "ts" to System.currentTimeMillis() / 1000,
                                    "from" to state.myHandle!!,
                                    "to" to (if (isGroupMode) groupId!! else handle),
                                    "msg_ts" to msgTs, "emoji" to emoji,
                                )
                                if (isGroupMode && groupId != null) {
                                    com.privimemobile.chat.ChatService.groups.sendGroupPayload(groupId, unreactPayload)
                                } else {
                                    val walletId = resolvedSbbsAddress
                                    if (!walletId.isNullOrEmpty()) {
                                        com.privimemobile.chat.ChatService.sbbs.sendWithRetry(walletId, unreactPayload)
                                    }
                                }
                            } else {
                                // Send reaction
                                val ts = System.currentTimeMillis() / 1000
                                val insertId = com.privimemobile.chat.ChatService.db!!.reactionDao().insert(
                                    com.privimemobile.chat.db.entities.ReactionEntity(
                                        messageTs = msgTs,
                                        senderHandle = state.myHandle!!,
                                        emoji = emoji,
                                        timestamp = ts,
                                    )
                                )
                                if (insertId == -1L) {
                                    com.privimemobile.chat.ChatService.db!!.reactionDao().reactivate(msgTs, state.myHandle!!, emoji, ts)
                                }
                                val reactPayload = mapOf(
                                    "v" to 1, "t" to "react",
                                    "ts" to System.currentTimeMillis() / 1000,
                                    "from" to state.myHandle!!,
                                    "to" to (if (isGroupMode) groupId!! else handle),
                                    "msg_ts" to msgTs, "emoji" to emoji,
                                )
                                if (isGroupMode && groupId != null) {
                                    com.privimemobile.chat.ChatService.groups.sendGroupPayload(groupId, reactPayload)
                                } else {
                                    val walletId = resolvedSbbsAddress
                                    if (!walletId.isNullOrEmpty()) {
                                        com.privimemobile.chat.ChatService.sbbs.sendWithRetry(walletId, reactPayload)
                                    }
                                }
                            }
                        }
                    }
                },
                onReactionLongPress = { emoji, msgTs ->
                    // Long-press any pill → show who reacted with this emoji
                    menu.reactionDetailMsg = msg
                    menu.reactionDetailEmoji = emoji
                },
                isHighlighted = search.searchHighlightTs == msg.timestamp || searchHighlightFromNav == msg.timestamp || pinState.pinHighlightTs == msg.timestamp || menu.replyHighlightTs == msg.timestamp,
                isGroupMode = isGroupMode,
                onSenderTap = { senderHandle ->
                    if (senderHandle.isNotEmpty()) onViewContact(senderHandle)
                },
                groupMemberNames = groupMemberNames,
                onScrollToReply = { replyTs ->
                    val targetIdx = reversedMessages.indexOfFirst { it.timestamp == replyTs }
                    if (targetIdx >= 0) {
                        scope.launch {
                            listState.animateScrollToItem(targetIdx)
                            menu.replyHighlightTs = replyTs
                            kotlinx.coroutines.delay(2000)
                            menu.replyHighlightTs = null
                        }
                    }
                },
            )
            } // close Row (selection mode wrapper)
        }
    }
}

// Pending file preview bar with thumbnail
if (input.pendingFile != null) {
    Surface(
        color = C.card,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (Helpers.isImageMime(input.pendingFile!!.mimeType)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp)),
                ) {
                    coil.compose.AsyncImage(
                        model = input.pendingFile!!.uri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    // Remove button overlaid on thumbnail
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable { input.pendingFile = null },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.chat_label_delete),
                            tint = Color.White,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "${input.pendingFile!!.name} (${Helpers.formatFileSize(input.pendingFile!!.size)})",
                    color = C.text,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text("\uD83D\uDCCE", fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "${input.pendingFile!!.name} (${Helpers.formatFileSize(input.pendingFile!!.size)})",
                    color = C.text,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { input.pendingFile = null }) {
                    Text(stringResource(R.string.chat_close), color = C.error, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// Scroll-to-bottom FAB with unread count
val showScrollButton by remember {
    derivedStateOf { listState.firstVisibleItemIndex > 3 }
}
// Badge: counts unseen messages below current scroll position.
// scrollBadge.badgeFloor prevents re-increment on scroll-up: it only decreases as the user
// scrolls down. Resets when scrollBadge.newMsgVersion changes (new messages arrived) or
// when user reaches the bottom.
val unreadBelow by remember(messages, reversedMessages) {
    derivedStateOf {
        @Suppress("UNUSED_EXPRESSION")
        roomMessageCount // subscribe to new message arrivals
        val current = listState.firstVisibleItemIndex
        val raw = ChatScrollMath.computeUnreadBelowRaw(
            reversedMessages,
            current,
            initialUnreadCount,
            scrollBadge.lastBottomTimestamp,
        )
        val (newFloorState, display) = ChatScrollMath.applyBadgeFloor(
            raw,
            ChatScrollMath.BadgeFloorState(scrollBadge.badgeFloor, scrollBadge.badgeFloorVersion),
            scrollBadge.newMsgVersion,
        )
        scrollBadge.badgeFloor = newFloorState.floor
        scrollBadge.badgeFloorVersion = newFloorState.floorVersion
        display
    }
}
val scrollBtnScale by animateFloatAsState(
    targetValue = if (showScrollButton) 1f else 0f,
    animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
    label = "scrollBtnScale",
)
if (scrollBtnScale > 0f) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 12.dp, bottom = 12.dp)
            .graphicsLayer { scaleX = scrollBtnScale; scaleY = scrollBtnScale },
    ) {
        SmallFloatingActionButton(
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            containerColor = C.card,
            contentColor = C.text,
            shape = CircleShape,
        ) {
            Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.chat_jump), modifier = Modifier.size(24.dp))
        }
        if (unreadBelow > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .defaultMinSize(minWidth = 18.dp)
                    .clip(CircleShape)
                    .background(C.accent)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (unreadBelow > 99) stringResource(R.string.chat_unread_overflow) else "$unreadBelow",
                    color = C.textDark, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
// Sticky date header — floats at top when scrolling
val stickyDateText = remember(listState.firstVisibleItemIndex, reversedMessages) {
    val idx = listState.firstVisibleItemIndex
    if (idx in reversedMessages.indices) {
        formatDateSeparator(reversedMessages[idx].timestamp, context)
    } else null
}
val isScrolling = listState.isScrollInProgress

LaunchedEffect(isScrolling) {
    if (isScrolling) scrollBadge.stickyVisible = true
    else { kotlinx.coroutines.delay(1500); scrollBadge.stickyVisible = false }
}
val stickyAlpha by animateFloatAsState(
    targetValue = if (scrollBadge.stickyVisible && stickyDateText != null) 1f else 0f,
    animationSpec = tween(if (scrollBadge.stickyVisible) 200 else 400),
    label = "stickyAlpha",
)
if (stickyAlpha > 0f) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = C.card.copy(alpha = 0.9f),
        shadowElevation = 4.dp,
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
            .graphicsLayer { alpha = stickyAlpha },
    ) {
        Text(
            stickyDateText ?: "",
            color = C.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}
} // end Box wrapper
}


private class TelegramFlingBehavior : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val dampened = initialVelocity * 0.90f
        if (kotlin.math.abs(dampened) < 50f) return dampened
        var velocityLeft = dampened
        var lastValue = 0f
        AnimationState(
            initialValue = 0f,
            initialVelocity = dampened,
        ).animateDecay(
            exponentialDecay(frictionMultiplier = 1.05f)
        ) {
            val delta = value - lastValue
            lastValue = value
            val consumed = scrollBy(delta)
            if (kotlin.math.abs(consumed) < kotlin.math.abs(delta) * 0.5f) {
                cancelAnimation()
            }
            velocityLeft = velocity
        }
        return velocityLeft
    }
}
