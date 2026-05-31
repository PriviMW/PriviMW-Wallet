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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.R
import com.privimemobile.chat.ChatPinOrder
import com.privimemobile.chat.ChatService
import com.privimemobile.chat.db.entities.GroupEntity
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.launch

/** Context menu shown when long-pressing a group chat row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatsGroupContextMenu(
    target: GroupEntity,
    pinnedCount: Int,
    onDismiss: () -> Unit,
    onReorderPinned: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = C.card,
        dragHandle = {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.width(36.dp).height(4.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)).background(C.textMuted.copy(alpha = 0.4f)))
            }
        },
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val grpAvatarBmp = remember(target.groupId, target.avatarHash) {
                    try {
                        val f = java.io.File(context.filesDir, "group_avatars/${target.groupId}.webp")
                        if (f.exists()) android.graphics.BitmapFactory.decodeFile(f.absolutePath) else null
                    } catch (_: Exception) { null }
                }
                if (grpAvatarBmp != null) {
                    Image(
                        bitmap = grpAvatarBmp.asImageBitmap(),
                        contentDescription = stringResource(R.string.chat_section_groups),
                        modifier = Modifier.size(40.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(avatarColor(target.groupId)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Group, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(target.name, color = C.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(stringResource(R.string.group_member_count_format, target.memberCount), color = C.textSecondary, fontSize = 12.sp)
                }
            }
            HorizontalDivider(color = C.border.copy(alpha = 0.3f))

            ChatListMenuItem(if (target.pinned) stringResource(R.string.chats_swipe_unpin) else stringResource(R.string.chats_swipe_pin)) {
                val gid = target.groupId
                val newVal = !target.pinned
                scope.launch {
                    val db = ChatService.db ?: return@launch
                    ChatPinOrder.setGroupPinned(db, gid, newVal)
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
                val gid = target.groupId; val newVal = !target.muted
                scope.launch {
                    ChatService.db?.groupDao()?.setMuted(gid, newVal)
                    val check = ChatService.db?.groupDao()?.findByGroupId(gid)
                    android.util.Log.d("ChatsScreen", "setMuted($gid, $newVal) → DB now: muted=${check?.muted}")
                }
                onDismiss()
            }
            ChatListMenuItem(if (target.archived) stringResource(R.string.chats_unarchive) else stringResource(R.string.chats_archive)) {
                scope.launch { ChatService.db?.groupDao()?.setArchived(target.groupId, !target.archived) }
                onDismiss()
            }
            HorizontalDivider(color = C.border.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
            ChatListMenuItem(stringResource(R.string.chats_leave_group), color = C.error) {
                scope.launch {
                    ChatService.groups.leaveGroup(target.groupId)
                }
                onDismiss()
            }
        }
    }
}