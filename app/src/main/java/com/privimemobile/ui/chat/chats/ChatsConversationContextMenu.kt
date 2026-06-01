package com.privimemobile.ui.chat.chats

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.R
import com.privimemobile.chat.ChatPinOrder
import com.privimemobile.chat.ChatService
import com.privimemobile.chat.db.entities.ConversationEntity
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.launch

/** Context menu shown when long-pressing a DM conversation row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatsConversationContextMenu(
    target: ConversationEntity,
    pinnedCount: Int,
    onDismiss: () -> Unit,
    onReorderPinned: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = C.card,
        dragHandle = {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.textMuted.copy(alpha = 0.4f)))
            }
        },
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            // Header with avatar
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val avatarKey = target.handle ?: target.convKey
                val dmAvatarBmp = remember(avatarKey) {
                    try {
                        val f = java.io.File(context.filesDir, "avatars/${avatarKey.removePrefix("@")}.webp")
                        if (f.exists()) android.graphics.BitmapFactory.decodeFile(f.absolutePath) else null
                    } catch (_: Exception) { null }
                }
                if (dmAvatarBmp != null) {
                    Image(
                        bitmap = dmAvatarBmp.asImageBitmap(),
                        contentDescription = stringResource(R.string.contact_info_title),
                        modifier = Modifier.size(40.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(avatarColor(avatarKey)), contentAlignment = Alignment.Center) {
                        val initial = (target.displayName ?: target.handle ?: target.convKey).removePrefix("@").firstOrNull()?.uppercase() ?: "?"
                        Text(initial, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(target.displayName?.ifEmpty { null } ?: target.handle?.let { "@$it" } ?: target.convKey, color = C.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    if (target.handle != null && target.displayName?.isNotEmpty() == true) {
                        Text("@${target.handle}", color = C.textSecondary, fontSize = 12.sp)
                    }
                }
            }
            HorizontalDivider(color = C.border.copy(alpha = 0.3f))

            // Menu items with touch highlight
            ChatListMenuItem(if (target.pinned) stringResource(R.string.chats_swipe_unpin) else stringResource(R.string.chats_swipe_pin)) {
                val newVal = !target.pinned
                scope.launch {
                    val db = ChatService.db ?: return@launch
                    ChatPinOrder.setConversationPinned(db, target.id, newVal)
                }
                onDismiss()
            }
            if (target.pinned && pinnedCount >= 2) {
                ChatListMenuItem(stringResource(R.string.chats_change_pin_order)) {
                    onDismiss()
                    onReorderPinned()
                }
            }
            ChatListMenuItem(if (target.muted) stringResource(R.string.chats_swipe_unmute) else stringResource(R.string.chats_swipe_mute)) {
                scope.launch { ChatService.db?.conversationDao()?.setMuted(target.id, !target.muted) }; onDismiss()
            }
            ChatListMenuItem(if (target.archived) stringResource(R.string.chats_unarchive) else stringResource(R.string.chats_archive)) {
                scope.launch { ChatService.db?.conversationDao()?.setArchived(target.id, !target.archived) }; onDismiss()
            }
            ChatListMenuItem(if (target.isBlocked) stringResource(R.string.chat_unblock_user) else stringResource(R.string.chat_block_user), color = if (target.isBlocked) C.text else C.error) {
                scope.launch { ChatService.db?.conversationDao()?.setBlocked(target.id, !target.isBlocked) }; onDismiss()
            }
            HorizontalDivider(color = C.border.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
            ChatListMenuItem(stringResource(R.string.general_delete), color = C.error) {
                scope.launch {
                    val db = ChatService.db ?: return@launch
                    // Delete attachment files from disk
                    val attachments = db.attachmentDao().getAllByConversation(target.id)
                    for (att in attachments) {
                        if (att.localPath != null) {
                            try { java.io.File(att.localPath).delete() } catch (_: Exception) {}
                        }
                    }
                    // Delete attachment DB records
                    db.attachmentDao().deleteByConversation(target.id)
                    // Delete wallpaper file + prefs
                    val handle = target.convKey.removePrefix("@")
                    try { java.io.File(context.filesDir, "wallpaper_$handle.jpg").delete() } catch (_: Exception) {}
                    context.getSharedPreferences("chat_prefs", android.content.Context.MODE_PRIVATE).edit()
                        .remove("wallpaper_${target.convKey}").apply()
                    // Clear draft
                    db.conversationDao().setDraft(target.id, null)
                    // Soft-delete messages (keep dedup keys to prevent SBBS re-delivery)
                    db.messageDao().softDeleteByConversation(target.id)
                    // Soft-delete conversation
                    db.conversationDao().softDelete(target.id)
                    // Remove from contacts list if this was a DM
                    if (!target.isGroup) {
                        db.contactDao().deleteByHandle(handle)
                    }
                }; onDismiss()
            }
        }
    }
}