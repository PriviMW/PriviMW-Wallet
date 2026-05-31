package com.privimemobile.ui.chat.chats

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.privimemobile.chat.ChatService
import com.privimemobile.chat.db.entities.ConversationEntity
import com.privimemobile.ui.theme.C

/**
 * One chat list row with swipe-to-delete/archive and per-item confirmation dialogs.
 * [onDeleteConfirm] / [onArchiveConfirm] run orchestrator-side ChatService / DB work.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatListSwipeRow(
    item: ChatListItem,
    typingVer: Int,
    conversations: List<ConversationEntity>,
    onOpenChat: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onLongPressDm: (ConversationEntity) -> Unit,
    onLongPressGroup: (com.privimemobile.chat.db.entities.GroupEntity) -> Unit,
    onDeleteConfirm: (ChatListItem) -> Unit,
    onArchiveConfirm: (ChatListItem) -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showArchiveConfirm by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    showDeleteConfirm = true
                    false
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    showArchiveConfirm = true
                    false
                }
                else -> false
            }
        },
        positionalThreshold = { it * 0.5f },
    )

    if (showDeleteConfirm) {
        ChatsDeleteConfirmDialog(
            item = item,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                onDeleteConfirm(item)
            },
        )
    }
    if (showArchiveConfirm) {
        ChatsArchiveConfirmDialog(
            item = item,
            onDismiss = { showArchiveConfirm = false },
            onConfirm = {
                showArchiveConfirm = false
                onArchiveConfirm(item)
            },
        )
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            SwipeDismissBackground(
                progress = dismissState.progress,
                direction = dismissState.dismissDirection,
            )
        },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
    ) {
        Surface(color = C.bg) {
            if (item.isGroup) {
                val group = item.group!!
                val gConvKey = "g_${group.groupId.take(16)}"
                val gTypingHandles = if (typingVer >= 0) ChatService.getGroupTyping(gConvKey) else emptyList()
                val gDraft = conversations.firstOrNull { it.convKey == gConvKey }?.draftText
                GroupRow(
                    group = group,
                    onClick = { onOpenGroup(group.groupId) },
                    onLongPress = { onLongPressGroup(group) },
                    typingHandles = gTypingHandles,
                    draftText = gDraft,
                )
            } else {
                val conv = item.conv!!
                val peerTyping = typingVer >= 0 && ChatService.isTyping(conv.convKey)
                ConversationRow(
                    conv = conv,
                    onClick = { onOpenChat(conv.convKey.removePrefix("@")) },
                    onLongPress = { onLongPressDm(conv) },
                    isTyping = peerTyping,
                )
            }
        }
    }
}
