package com.privimemobile.ui.chat.chats

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import com.privimemobile.R
import com.privimemobile.chat.ChatService
import com.privimemobile.chat.db.entities.ConversationEntity
import com.privimemobile.ui.theme.C

/**
 * Telegram-style conversation row — flat with divider, colored avatar, proper icons.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ConversationRow(conv: ConversationEntity, onClick: () -> Unit, onLongPress: () -> Unit = {}, isTyping: Boolean = false) {
    val displayLabel = conv.displayName?.ifEmpty { null } ?: conv.handle?.let { "@$it" } ?: conv.convKey

    // Observe avatar version to trigger refresh when avatars are updated
    val avatarVersion by ChatService.avatarVersion.collectAsState()

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 1000f),
        label = "convPressScale",
    )

    Column(modifier = Modifier.background(C.bg).graphicsLayer { scaleX = pressScale; scaleY = pressScale }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongPress, interactionSource = interactionSource, indication = androidx.compose.foundation.LocalIndication.current)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar (loads cached image or falls back to letter circle)
            com.privimemobile.ui.components.AvatarDisplay(
                handle = conv.convKey.removePrefix("@"),
                displayName = conv.displayName,
                size = 52.dp,
                version = avatarVersion,
            )
            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Top line: name + indicators + time
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        displayLabel,
                        color = C.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (conv.muted) {
                        Icon(
                            Icons.Default.NotificationsOff,
                            contentDescription = stringResource(R.string.chats_cd_muted),
                            tint = C.textSecondary,
                            modifier = Modifier.padding(start = 4.dp).size(14.dp),
                        )
                    }
                    if (conv.pinned) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = stringResource(R.string.chats_cd_pinned),
                            tint = C.textSecondary,
                            modifier = Modifier.padding(start = 4.dp).size(14.dp),
                        )
                    }
                    if (conv.isBlocked) {
                        Icon(
                            Icons.Default.Block,
                            contentDescription = stringResource(R.string.chats_cd_blocked),
                            tint = Color(0xFFEF5350),
                            modifier = Modifier.padding(start = 4.dp).size(14.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formatTime(conv.lastMessageTs),
                        color = if (conv.unreadCount > 0) C.accent else C.textSecondary,
                        fontSize = 12.sp,
                    )
                }

                Spacer(Modifier.height(3.dp))

                // Bottom line: preview + unread badge
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val hasDraft = !conv.draftText.isNullOrEmpty()
                    if (isTyping) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.chats_typing_status), color = C.accent, fontSize = 14.sp)
                            val infiniteTransition = rememberInfiniteTransition(label = "typingDots")
                            repeat(3) { i ->
                                val offsetY by infiniteTransition.animateFloat(
                                    initialValue = 0f, targetValue = -3f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(400, easing = FastOutSlowInEasing, delayMillis = i * 120),
                                        repeatMode = RepeatMode.Reverse,
                                    ), label = "dot$i",
                                )
                                Text(".", color = C.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.offset(y = offsetY.dp))
                            }
                        }
                    } else if (hasDraft) {
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = C.error, fontWeight = FontWeight.Medium)) {
                                    append(stringResource(R.string.chats_draft_prefix))
                                }
                                withStyle(SpanStyle(color = C.textSecondary)) {
                                    append(conv.draftText!!)
                                }
                            },
                            fontSize = 14.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Text(
                            conv.lastMessagePreview ?: "",
                            color = C.textSecondary,
                            fontSize = 14.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (conv.unreadCount > 0) {
                        Spacer(Modifier.width(8.dp))
                        // Pulse animation on badge
                        val infiniteTransition = rememberInfiniteTransition(label = "badgePulse")
                        val badgeScale by infiniteTransition.animateFloat(
                            initialValue = 1f, targetValue = 1.12f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(600, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse,
                            ), label = "badgeScale",
                        )
                        Box(
                            modifier = Modifier
                                .graphicsLayer(scaleX = badgeScale, scaleY = badgeScale)
                                .defaultMinSize(minWidth = 22.dp)
                                .clip(CircleShape)
                                .background(C.accent)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (conv.unreadCount > 99) stringResource(R.string.chat_unread_overflow) else "${conv.unreadCount}",
                                color = C.textDark,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
        // Divider — inset past avatar
        HorizontalDivider(
            color = C.border.copy(alpha = 0.5f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(start = 82.dp),
        )
    }
}