package com.privimemobile.ui.chat.chats

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.privimemobile.R
import com.privimemobile.ui.theme.C

/** Delete/leave confirmation dialog shown after swiping left on a chat item. */
@Composable
internal fun ChatsDeleteConfirmDialog(
    item: ChatListItem,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val name = if (item.isGroup) item.group!!.name
    else (item.conv!!.displayName?.ifEmpty { null } ?: "@${item.conv!!.convKey.removePrefix("@")}")
    val isGrp = item.isGroup
    val deleteMsg = stringResource(if (isGrp) R.string.chats_leave_group_confirm else R.string.chats_delete_chat_confirm, name)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.card,
        title = { Text(stringResource(if (isGrp) R.string.chats_leave_group_title else R.string.chats_delete_chat_title), color = C.text, fontWeight = FontWeight.SemiBold) },
        text = { HighlightedNameText(message = deleteMsg, name = name) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(if (isGrp) R.string.chats_leave else R.string.general_delete), color = C.error, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.general_cancel), color = C.textSecondary)
            }
        },
    )
}