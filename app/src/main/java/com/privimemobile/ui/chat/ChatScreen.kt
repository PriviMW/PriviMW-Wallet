package com.privimemobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.protocol.ChatMessage
import com.privimemobile.protocol.ProtocolStartup
import com.privimemobile.ui.theme.C
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatScreen(
    handle: String,
    displayName: String = "",
    onBack: () -> Unit,
) {
    // Collect messages for this conversation from protocol state
    val allConversations by ProtocolStartup.conversations.collectAsState()
    val messages = remember(allConversations, handle) {
        allConversations["@$handle"] ?: emptyList()
    }

    // Clear unread when opening chat
    LaunchedEffect(handle) {
        ProtocolStartup.activeChat = "@$handle"
        ProtocolStartup.clearUnread("@$handle")
    }
    DisposableEffect(handle) {
        onDispose { ProtocolStartup.activeChat = null }
    }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg),
    ) {
        // Header
        Surface(
            color = C.card,
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) {
                    Text("<", color = C.textSecondary, fontSize = 18.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        displayName.ifEmpty { "@$handle" },
                        color = C.text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("@$handle", color = C.textSecondary, fontSize = 12.sp)
                }
            }
        }

        // Messages
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            state = listState,
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(messages.reversed(), key = { it.id }) { msg ->
                MessageBubble(msg)
            }
        }

        // Input bar
        Surface(color = C.card) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Message...", color = C.textSecondary) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = C.accent,
                        unfocusedBorderColor = C.border,
                        cursorColor = C.accent,
                    ),
                    maxLines = 4,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            ProtocolStartup.sendMessage(handle, inputText.trim())
                            inputText = ""
                        }
                    },
                    enabled = inputText.isNotBlank(),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (inputText.isNotBlank()) C.accent else C.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val isMine = msg.sent

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMine) 16.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 16.dp,
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isMine) C.outgoing.copy(alpha = 0.2f) else C.card,
            ),
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(msg.text, color = C.text, fontSize = 15.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    formatMessageTime(msg.timestamp),
                    color = C.textSecondary,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

private val msgTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private fun formatMessageTime(ts: Long): String {
    if (ts <= 0) return ""
    return msgTimeFormat.format(Date(ts * 1000))
}
