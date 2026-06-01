package com.privimemobile.ui.chat.chrome

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.R
import com.privimemobile.protocol.ChatMessage
import com.privimemobile.ui.chat.ChatSelectionState
import com.privimemobile.ui.theme.C

@Composable
fun ChatSelectionHeader(
    selection: ChatSelectionState,
    messages: List<ChatMessage>,
    onForwardSelected: (List<ChatMessage>) -> Unit,
) {
    val context = LocalContext.current
    Surface(color = C.card, shadowElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { selection.exitSelection() },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    stringResource(R.string.chat_cancel_scheduled),
                    tint = C.text,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                context.getString(R.string.chat_x_selected, selection.selectedIds.size),
                color = C.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            IconButton(
                onClick = { selection.openBulkDeleteConfirm() },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Default.Delete,
                    stringResource(R.string.chat_label_delete),
                    tint = C.error,
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(
                onClick = {
                    val msgsToForward = messages.filter {
                        it.id in selection.selectedIds && (it.text.isNotEmpty() || it.file != null)
                    }
                    if (msgsToForward.isNotEmpty()) {
                        onForwardSelected(msgsToForward)
                    }
                    selection.exitSelection()
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    stringResource(R.string.chat_forward),
                    tint = C.accent,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
