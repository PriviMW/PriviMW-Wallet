package com.privimemobile.ui.chat.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.R
import com.privimemobile.protocol.ChatMessage
import com.privimemobile.ui.chat.ChatPinState
import com.privimemobile.ui.chat.format.formatMessageTime
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Scroll-aware pinned message bar + pin list dialog. */
@Composable
fun ChatPinnedBar(
    pinnedByOrder: List<ChatMessage>,
    messages: List<ChatMessage>,
    pinState: ChatPinState,
    listState: LazyListState,
    scope: CoroutineScope,
    isGroupMode: Boolean,
    groupMyRole: Int,
    convId: Long,
) {
    if (pinnedByOrder.isEmpty()) return

    val context = LocalContext.current

    val scrollAwarePinIndex by remember(pinnedByOrder, messages) {
        derivedStateOf {
            val visibleIdx = listState.firstVisibleItemIndex
            val revMsgs = messages.reversed()
            val pinPositions = pinnedByOrder.mapIndexedNotNull { pmIdx, pin ->
                val lcIdx = revMsgs.indexOfFirst { it.timestamp == pin.timestamp && it.id == pin.id }
                if (lcIdx >= 0) pmIdx to lcIdx else null
            }
            if (pinPositions.isEmpty()) {
                0
            } else {
                val nextPin = pinPositions.filter { it.second >= visibleIdx }.minByOrNull { it.second }
                nextPin?.first ?: pinPositions.maxByOrNull { it.second }?.first ?: 0
            }
        }
    }

    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (pinState.manualOverrideIndex >= 0 && pinState.scrollPosAtOverride >= 0) {
            delay(1000)
            if (listState.firstVisibleItemIndex != pinState.scrollPosAtOverride) {
                pinState.clearManualOverride()
            }
        }
    }

    val safeIndex = if (pinState.manualOverrideIndex >= 0) {
        pinState.manualOverrideIndex.coerceIn(0, pinnedByOrder.size - 1)
    } else {
        scrollAwarePinIndex.coerceIn(0, pinnedByOrder.size - 1)
    }
    val currentPin = pinnedByOrder[safeIndex]

    fun scrollToPinMsg(pin: ChatMessage) {
        val revMsgs = messages.reversed()
        val idx = revMsgs.indexOfFirst { it.timestamp == pin.timestamp && it.id == pin.id }
        if (idx >= 0) {
            pinState.pinHighlightTs = pin.timestamp
            scope.launch {
                listState.animateScrollToItem(idx)
                delay(2000)
                pinState.pinHighlightTs = 0L
            }
        }
    }

    Surface(
        color = C.card,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .clickable {
                    scrollToPinMsg(currentPin)
                    val revMsgs = messages.reversed()
                    val landingIdx = revMsgs.indexOfFirst {
                        it.timestamp == currentPin.timestamp && it.id == currentPin.id
                    }
                    pinState.applyBarTapOverride(safeIndex, landingIdx)
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val segments = pinnedByOrder.size.coerceAtMost(5)
                val segH = (26 / segments).coerceAtLeast(3)
                repeat(segments) { i ->
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(segH.dp)
                            .padding(vertical = 0.5.dp)
                            .background(
                                if (i == safeIndex % segments) C.accent else C.accent.copy(alpha = 0.25f),
                            ),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    if (pinnedByOrder.size > 1) {
                        context.getString(R.string.chat_pinned_message_num, safeIndex + 1)
                    } else {
                        stringResource(R.string.chat_pinned_message)
                    },
                    color = C.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                val pinPreview = when {
                    currentPin.text.isNotEmpty() -> currentPin.text
                    currentPin.file != null -> stringResource(R.string.chat_file_label)
                    else -> stringResource(R.string.chat_message_label)
                }
                Text(
                    pinPreview,
                    color = C.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = { pinState.showPinListDialog = true },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Default.PushPin,
                    stringResource(R.string.chat_pinned_messages),
                    tint = C.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }

    if (pinState.showPinListDialog) {
        AlertDialog(
            onDismissRequest = { pinState.dismissPinListDialog() },
            containerColor = C.card,
            title = {
                Text(
                    stringResource(R.string.chat_pinned_messages, pinnedByOrder.size),
                    color = C.text,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Column(modifier = Modifier.heightIn(max = 400.dp)) {
                    LazyColumn {
                        items(pinnedByOrder.size) { idx ->
                            val pin = pinnedByOrder[idx]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(C.accent.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "#${idx + 1}",
                                        color = C.accent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    val listPreview = when {
                                        pin.text.isNotEmpty() -> pin.text
                                        pin.file != null -> stringResource(R.string.chat_pinned_file)
                                        else -> stringResource(R.string.chat_pinned_message)
                                    }
                                    Text(
                                        listPreview,
                                        color = C.text,
                                        fontSize = 14.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        formatMessageTime(pin.timestamp),
                                        color = C.textMuted,
                                        fontSize = 11.sp,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        pinState.dismissPinListDialog()
                                        scrollToPinMsg(pin)
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForward,
                                        context.getString(R.string.chat_jump),
                                        tint = C.accent,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            if (idx < pinnedByOrder.size - 1) {
                                HorizontalDivider(color = C.border.copy(alpha = 0.3f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                val canUnpin = !isGroupMode || groupMyRole >= 1
                if (canUnpin) {
                    TextButton(onClick = {
                        scope.launch {
                            if (convId > 0L) {
                                com.privimemobile.chat.ChatService.db?.messageDao()?.unpinAll(convId)
                            }
                        }
                        pinState.dismissPinListDialog()
                    }) {
                        Text(stringResource(R.string.chat_unpin_all), color = C.error, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pinState.dismissPinListDialog() }) {
                    Text(stringResource(R.string.general_close), color = C.textSecondary)
                }
            },
        )
    }
}
