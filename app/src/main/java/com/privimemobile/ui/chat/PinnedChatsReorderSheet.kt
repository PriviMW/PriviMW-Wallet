package com.privimemobile.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import com.privimemobile.R
import com.privimemobile.chat.PinnedChatItem
import com.privimemobile.ui.components.AvatarDisplay
import com.privimemobile.ui.theme.C

@Composable
fun PinnedChatsReorderOverlay(
    initialItems: List<PinnedChatItem>,
    onDismiss: () -> Unit,
    onSave: (List<PinnedChatItem>) -> Unit,
) {
    var orderedItems by remember(initialItems) { mutableStateOf(initialItems) }
    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val itemHeightPx = with(LocalDensity.current) { 72.dp.toPx() }
    val hapticView = LocalView.current
    fun hapticTick() {
        hapticView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.chats_reorder_pinned_title),
                    color = C.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = {
                    onSave(orderedItems)
                    onDismiss()
                }) {
                    Text(stringResource(R.string.general_done), color = C.accent, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                stringResource(R.string.chats_reorder_pinned_hint),
                color = C.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            orderedItems.forEachIndexed { index, item ->
                val isDragging = dragIndex == index
                val infiniteTransition = rememberInfiniteTransition(label = "pinJiggle$index")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = -0.6f,
                    targetValue = 0.6f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(100 + (index % 3) * 30, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "pinRot$index",
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer(
                            rotationZ = if (isDragging) 0f else rotation,
                            translationY = if (isDragging) dragOffset else 0f,
                            scaleX = if (isDragging) 1.03f else 1f,
                            scaleY = if (isDragging) 1.03f else 1f,
                            shadowElevation = if (isDragging) 12f else 0f,
                        )
                        .zIndex(if (isDragging) 10f else 0f)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PinnedReorderRowContent(item = item, modifier = Modifier.weight(1f))
                    DragHandle(
                        isDragging = isDragging,
                        onDragStart = {
                            dragIndex = index
                            dragOffset = 0f
                            hapticTick()
                        },
                        onDragEnd = {
                            dragIndex = -1
                            dragOffset = 0f
                        },
                        onDragCancel = {
                            dragIndex = -1
                            dragOffset = 0f
                        },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount
                            if (dragIndex < 0) return@DragHandle
                            val threshold = itemHeightPx * 0.5f
                            if (dragOffset > threshold && dragIndex < orderedItems.size - 1) {
                                val list = orderedItems.toMutableList()
                                val temp = list[dragIndex]
                                list[dragIndex] = list[dragIndex + 1]
                                list[dragIndex + 1] = temp
                                orderedItems = list
                                dragIndex += 1
                                dragOffset -= itemHeightPx
                                hapticTick()
                            } else if (dragOffset < -threshold && dragIndex > 0) {
                                val list = orderedItems.toMutableList()
                                val temp = list[dragIndex]
                                list[dragIndex] = list[dragIndex - 1]
                                list[dragIndex - 1] = temp
                                orderedItems = list
                                dragIndex -= 1
                                dragOffset += itemHeightPx
                                hapticTick()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PinnedReorderRowContent(item: PinnedChatItem, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(C.card)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.isGroup) {
            val group = item.group!!
            val context = LocalContext.current
            val groupAvatarBmp = remember(group.groupId, group.avatarHash) {
                try {
                    val f = java.io.File(context.filesDir, "group_avatars/${group.groupId}.webp")
                    if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
                } catch (_: Exception) { null }
            }
            if (groupAvatarBmp != null) {
                Image(
                    bitmap = groupAvatarBmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier.size(44.dp).background(C.accent.copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Group, contentDescription = null, tint = C.accent, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                group.name,
                color = C.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            val conv = item.conv!!
            AvatarDisplay(
                handle = conv.convKey.removePrefix("@"),
                displayName = conv.displayName,
                size = 44.dp,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                conv.displayName?.ifEmpty { null } ?: conv.handle?.let { "@$it" } ?: conv.convKey,
                color = C.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DragHandle(
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onVerticalDrag: (change: androidx.compose.ui.input.pointer.PointerInputChange, Float) -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(start = 8.dp)
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isDragging) C.accent.copy(alpha = 0.2f) else C.card)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragCancel() },
                    onVerticalDrag = onVerticalDrag,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) {
                Box(
                    Modifier
                        .width(18.dp)
                        .height(2.dp)
                        .background(
                            if (isDragging) C.accent else C.textSecondary,
                            RoundedCornerShape(1.dp),
                        ),
                )
            }
        }
    }
}
