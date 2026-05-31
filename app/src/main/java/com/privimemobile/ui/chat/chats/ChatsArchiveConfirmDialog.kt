package com.privimemobile.ui.chat.chats

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.privimemobile.R
import com.privimemobile.ui.theme.C

/** Archive/unarchive confirmation dialog shown after swiping right on a chat item. */
@Composable
internal fun ChatsArchiveConfirmDialog(
    item: ChatListItem,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val isGrp = item.isGroup
    val name = if (item.isGroup) item.group!!.name else (item.conv!!.displayName?.ifEmpty { null } ?: "@${item.conv!!.convKey.removePrefix("@")}")
    val isCurrentlyArchived = if (isGrp) item.group!!.archived else item.conv!!.archived
    val archiveMsg = stringResource(if (isCurrentlyArchived) R.string.chats_unarchive_confirm else R.string.chats_archive_confirm, name)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.card,
        title = { Text(stringResource(if (isCurrentlyArchived) R.string.chats_unarchive else R.string.chats_archive), color = C.text, fontWeight = FontWeight.SemiBold) },
        text = { HighlightedNameText(message = archiveMsg, name = name) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(if (isCurrentlyArchived) R.string.chats_unarchive else R.string.chats_archive), color = C.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.general_cancel), color = C.textSecondary)
            }
        },
    )
}