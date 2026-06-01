package com.privimemobile.ui.chat.dialogs

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.privimemobile.R
import com.privimemobile.chat.DeleteAuthorization
import com.privimemobile.protocol.ChatMessage
import com.privimemobile.ui.chat.ChatChromeState
import com.privimemobile.ui.chat.ChatSelectionState
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun ChatClearDeleteDialogs(
    chrome: ChatChromeState,
    selection: ChatSelectionState,
    convId: Long,
    handle: String,
    isGroupMode: Boolean,
    groupId: String?,
    groupMyRole: Int,
    messages: List<ChatMessage>,
    resolvedSbbsAddress: String?,
    context: Context,
    scope: CoroutineScope,
    onBack: () -> Unit,
    onRefreshConversationPreview: suspend (Long) -> Unit,
) {
    if (chrome.showClearConfirm) {
        AlertDialog(
            onDismissRequest = { chrome.showClearConfirm = false },
            containerColor = C.card,
            title = { Text(stringResource(R.string.chat_clear_history), color = C.text, fontWeight = FontWeight.SemiBold) },
            text = { Text(stringResource(R.string.chat_clear_history_body), color = C.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    if (convId > 0L) {
                        scope.launch {
                            com.privimemobile.chat.ChatService.db?.messageDao()?.softDeleteByConversation(convId)
                            com.privimemobile.chat.ChatService.db?.conversationDao()?.updateLastMessage(convId, 0, null)
                        }
                    }
                    chrome.showClearConfirm = false
                }) {
                    Text(stringResource(R.string.chat_clear), color = C.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { chrome.showClearConfirm = false }) {
                    Text(stringResource(R.string.general_cancel), color = C.textSecondary)
                }
            },
        )
    }

    if (chrome.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { chrome.showDeleteConfirm = false },
            containerColor = C.card,
            title = {
                Text(
                    if (isGroupMode) stringResource(R.string.chat_leave_group) else stringResource(R.string.chat_delete_chat),
                    color = C.text,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(
                    if (isGroupMode) stringResource(R.string.chat_leave_confirm) else stringResource(R.string.chat_delete_confirm),
                    color = C.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    chrome.showDeleteConfirm = false
                    if (isGroupMode && groupId != null) {
                        com.privimemobile.chat.ChatService.groups.leaveGroup(groupId) { success, error ->
                            if (!success) {
                                scope.launch {
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(R.string.chat_leave_failed, error ?: context.getString(R.string.chat_tx_failed)),
                                        android.widget.Toast.LENGTH_LONG,
                                    ).show()
                                }
                            } else {
                                scope.launch {
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(R.string.chat_leave_tx_submitted),
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                onBack()
                            }
                        }
                    } else if (convId > 0L) {
                        scope.launch {
                            com.privimemobile.chat.ChatService.db?.messageDao()?.softDeleteByConversation(convId)
                            com.privimemobile.chat.ChatService.db?.conversationDao()?.softDelete(convId)
                            com.privimemobile.chat.ChatService.db?.contactDao()?.deleteByHandle(handle)
                        }
                        onBack()
                    }
                }) {
                    Text(stringResource(R.string.general_delete), color = C.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { chrome.showDeleteConfirm = false }) {
                    Text(stringResource(R.string.general_cancel), color = C.textSecondary)
                }
            },
        )
    }

    if (selection.showDeleteConfirmDialog && selection.pendingDeleteIds.isNotEmpty()) {
        val count = selection.pendingDeleteIds.size
        val msgsToDelete = messages.filter { it.id in selection.pendingDeleteIds }
        val broadcastMsgs = DeleteAuthorization.filterForDeleteForEveryoneBroadcast(
            isGroup = isGroupMode,
            myGroupRole = groupMyRole,
            selected = msgsToDelete,
            isSentByMe = { it.sent },
        )
        val broadcastCount = broadcastMsgs.size
        val isGroupModerator = DeleteAuthorization.isGroupModerator(isGroupMode, groupMyRole)
        val hasOwnMessages = broadcastCount > 0
        val hasOthersMessages = !isGroupModerator && broadcastMsgs.size < msgsToDelete.size
        AlertDialog(
            onDismissRequest = { selection.dismissDeleteConfirm() },
            containerColor = C.card,
            title = {
                Text(
                    context.getString(R.string.chat_delete_messages_title, count, if (count > 1) "s" else ""),
                    color = C.text,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            val ids = selection.pendingDeleteIds.toList()
                            val cid = convId
                            scope.launch {
                                ids.forEach { id ->
                                    com.privimemobile.chat.ChatService.db?.messageDao()?.markDeletedById(id.toLong())
                                }
                                onRefreshConversationPreview(cid)
                            }
                            selection.clearAfterBulkAction()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.chat_delete_for_me),
                            color = C.text,
                            fontSize = 15.sp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (hasOwnMessages) {
                        if (hasOthersMessages) {
                            Text(
                                stringResource(R.string.chat_bulk_delete_everyone_hint),
                                color = C.textSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        TextButton(
                            onClick = {
                                val capturedConvId = convId
                                val toBroadcast = broadcastMsgs
                                selection.clearAfterBulkAction()
                                com.privimemobile.chat.ChatService.scope.launch {
                                    val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
                                    if (state?.myHandle != null) {
                                        for (msg in msgsToDelete) {
                                            com.privimemobile.chat.ChatService.db?.messageDao()?.markDeletedById(msg.id.toLong())
                                        }
                                        onRefreshConversationPreview(capturedConvId)
                                        val toPeer = if (isGroupMode) groupId!! else handle
                                        val delPayloads = com.privimemobile.chat.DeleteForEveryone.payloads(
                                            myHandle = state.myHandle!!,
                                            to = toPeer,
                                            msgTimestamps = toBroadcast.map { it.timestamp },
                                        )
                                        if (isGroupMode && groupId != null) {
                                            com.privimemobile.chat.ChatService.groups
                                                .deliverGroupPayloadsSequentially(groupId, delPayloads)
                                        } else {
                                            val walletId = resolvedSbbsAddress
                                            if (!walletId.isNullOrEmpty()) {
                                                com.privimemobile.chat.ChatService.sbbs
                                                    .sendPayloadsWithRetrySpaced(walletId, delPayloads)
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (hasOthersMessages || (isGroupModerator && broadcastCount > 1)) {
                                    stringResource(R.string.chat_bulk_delete_for_everyone_count, broadcastCount)
                                } else {
                                    stringResource(R.string.chat_delete_for_everyone)
                                },
                                color = C.error,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selection.dismissDeleteConfirm() }) {
                    Text(stringResource(R.string.general_cancel), color = C.textSecondary)
                }
            },
        )
    }
}
