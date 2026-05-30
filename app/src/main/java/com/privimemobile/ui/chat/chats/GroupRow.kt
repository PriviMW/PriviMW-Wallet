package com.privimemobile.ui.chat.chats

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.privimemobile.chat.db.entities.GroupEntity
import com.privimemobile.ui.theme.C
import java.io.File

/** Group chat row in the chat list. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GroupRow(
    group: GroupEntity,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    typingHandles: List<String> = emptyList(),
    draftText: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 1000f),
        label = "grpPressScale",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .combinedClickable(onClick = onClick, onLongClick = onLongPress, interactionSource = interactionSource, indication = androidx.compose.foundation.LocalIndication.current)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Group avatar (custom image or default icon)
        val context = LocalContext.current
        val groupAvatarBmp = remember(group.groupId, group.avatarHash) {
            try {
                val f = File(context.filesDir, "group_avatars/${group.groupId}.webp")
                if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
            } catch (_: Exception) { null }
        }
        if (groupAvatarBmp != null) {
            Image(
                bitmap = groupAvatarBmp.asImageBitmap(),
                contentDescription = stringResource(R.string.chat_section_groups),
                modifier = Modifier.size(52.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.size(52.dp).background(avatarColor(group.groupId), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Group, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!group.isPublic) {
                    Icon(Icons.Default.Lock, stringResource(R.string.chats_private_desc), tint = C.textSecondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = group.name,
                    color = C.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (group.pinned) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = stringResource(R.string.chat_pin),
                        tint = C.textSecondary,
                        modifier = Modifier.padding(start = 4.dp).size(14.dp),
                    )
                }
                if (group.muted) {
                    Icon(
                        Icons.Default.NotificationsOff,
                        contentDescription = stringResource(R.string.chat_mute_notifications),
                        tint = C.textSecondary,
                        modifier = Modifier.padding(start = 4.dp).size(14.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    formatTime(group.lastMessageTs),
                    color = C.textSecondary, fontSize = 12.sp,
                )
            }
            // Preview: typing > draft > last message
            val hasDraft = !draftText.isNullOrEmpty()
            if (typingHandles.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val label = if (typingHandles.size == 1) context.getString(R.string.chat_group_typing_singular, "@${typingHandles[0]}")
                        else context.getString(R.string.chat_group_typing_multiple, typingHandles.size)
                    Text(label, color = C.accent, fontSize = 14.sp)
                    val infiniteTransition = rememberInfiniteTransition(label = "gTyping")
                    repeat(3) { i ->
                        val offsetY by infiniteTransition.animateFloat(
                            initialValue = 0f, targetValue = -3f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(400, easing = FastOutSlowInEasing, delayMillis = i * 120),
                                repeatMode = RepeatMode.Reverse,
                            ), label = "gDot$i",
                        )
                        Text(".", color = C.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.offset(y = offsetY.dp))
                    }
                }
            } else if (hasDraft) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = C.error, fontWeight = FontWeight.Medium)) { append(stringResource(R.string.chats_draft_prefix)) }
                        withStyle(SpanStyle(color = C.textSecondary)) { append(draftText!!) }
                    },
                    fontSize = 14.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (group.lastMessagePreview != null) {
                Text(
                    text = group.lastMessagePreview!!,
                    color = C.textSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = if (group.myRole == 2) stringResource(R.string.chat_you_created) else stringResource(R.string.chat_group_chat),
                    color = C.textMuted,
                    fontSize = 14.sp,
                )
            }
        }

        // Unread badge (same style as DM)
        if (group.unreadCount > 0) {
            Spacer(Modifier.width(8.dp))
            val infiniteTransition = rememberInfiniteTransition(label = "gBadgePulse")
            val badgeScale by infiniteTransition.animateFloat(
                initialValue = 1f, targetValue = 1.12f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ), label = "gBadgeScale",
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
                    if (group.unreadCount > 99) stringResource(R.string.chat_unread_overflow) else group.unreadCount.toString(),
                    color = C.textDark,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}