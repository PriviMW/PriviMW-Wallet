package com.privimemobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.protocol.Conversation
import com.privimemobile.protocol.ProtocolStartup
import com.privimemobile.ui.theme.C
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatsScreen(
    onOpenChat: (String) -> Unit = {},
    onNewChat: () -> Unit = {},
) {
    // Derive conversation list from protocol state
    val rawConversations by ProtocolStartup.conversations.collectAsState()
    val rawContacts by ProtocolStartup.contacts.collectAsState()
    val rawUnread by ProtocolStartup.unreadCounts.collectAsState()

    val conversations = remember(rawConversations, rawContacts, rawUnread) {
        rawConversations.entries.mapNotNull { (key, msgs) ->
            if (msgs.isEmpty()) return@mapNotNull null
            val last = msgs.last()
            val contact = rawContacts[key]
            Conversation(
                handle = key.removePrefix("@"),
                displayName = contact?.displayName ?: key.removePrefix("@"),
                lastMessage = last.text.ifEmpty { if (last.fileHash.isNotEmpty()) "[File]" else "" },
                lastTimestamp = last.timestamp,
                unreadCount = rawUnread[key] ?: 0,
                walletId = contact?.walletId ?: "",
            )
        }.sortedByDescending { it.lastTimestamp }
    }

    Box(modifier = Modifier.fillMaxSize().background(C.bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Text("Chats", color = C.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "End-to-end encrypted messaging on Beam",
                color = C.textSecondary,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(16.dp))

            if (conversations.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No conversations yet", color = C.textSecondary, fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Start a new chat by tapping +",
                            color = C.textSecondary,
                            fontSize = 13.sp,
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(conversations, key = { it.handle }) { conv ->
                        ConversationCard(conv, onClick = { onOpenChat(conv.handle) })
                    }
                }
            }
        }

        // FAB - New Chat
        FloatingActionButton(
            onClick = onNewChat,
            containerColor = C.accent,
            contentColor = C.textDark,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "New Chat")
        }
    }
}

@Composable
private fun ConversationCard(conv: Conversation, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = C.card),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(C.border),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    conv.displayName.firstOrNull()?.uppercase() ?: conv.handle.first().uppercase(),
                    color = C.accent,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        conv.displayName.ifEmpty { "@${conv.handle}" },
                        color = C.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        formatTime(conv.lastTimestamp),
                        color = C.textSecondary,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        conv.lastMessage,
                        color = C.textSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (conv.unreadCount > 0) {
                        Spacer(Modifier.width(8.dp))
                        Badge(
                            containerColor = C.accent,
                            contentColor = C.textDark,
                        ) {
                            Text("${conv.unreadCount}")
                        }
                    }
                }
            }
        }
    }
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

private fun formatTime(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val date = Date(timestamp * 1000)
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { time = date }
    return if (now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR) &&
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    ) {
        timeFormat.format(date)
    } else {
        dateFormat.format(date)
    }
}
