package com.privimemobile.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.protocol.Conversation
import com.privimemobile.protocol.Helpers
import com.privimemobile.protocol.ProtocolStartup
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    onOpenChat: (String) -> Unit = {},
    onNewChat: () -> Unit = {},
) {
    val identity by ProtocolStartup.identity.collectAsState()

    // SBBS re-registration state (restored wallet)
    val sbbsNeedsUpdate by ProtocolStartup.sbbsNeedsUpdate.collectAsState()
    val sbbsUpdating by ProtocolStartup.sbbsUpdating.collectAsState()

    // Landing page 1: Not registered — show register prompt
    if (identity == null || identity?.registered != true) {
        Column(
            modifier = Modifier.fillMaxSize().background(C.bg).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Encrypted Messaging", color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(
                "Register a @handle to start sending and receiving encrypted messages on Beam.",
                color = C.textSecondary, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onNewChat, // Navigate to register screen
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.accent),
            ) {
                Text("Register Handle", color = C.textDark, fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    // Landing page 2: Registered but SBBS address belongs to old device — re-register
    if (sbbsNeedsUpdate) {
        Column(
            modifier = Modifier.fillMaxSize().background(C.bg).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Welcome Back", color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "@${identity!!.handle}",
                color = C.accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Your wallet was restored on a new device. Your messaging address needs to be updated so others can reach you.",
                color = C.textSecondary,
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = Color(0x1AFFC107),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x4DFFC107)),
            ) {
                Text(
                    "Your @handle is safe on-chain. Previous conversations cannot be restored as messages are stored locally on each device. A small transaction fee applies to update your address.",
                    color = Color(0xFFFFC107),
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(12.dp),
                )
            }
            // Show balance warning if wallet is empty
            val beamStatus by com.privimemobile.wallet.WalletEventBus.beamStatus.collectAsState()
            val hasBalance = beamStatus.available > 0

            if (!hasBalance) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x1AFF6B6B),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x4DFF6B6B)),
                ) {
                    Text(
                        "You need BEAM in your wallet to pay the transaction fee. Send some BEAM to this wallet first.",
                        color = C.error,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    ProtocolStartup.reRegisterSbbsAddress()
                },
                enabled = !sbbsUpdating && hasBalance,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = C.accent,
                    disabledContainerColor = C.accent.copy(alpha = 0.3f),
                ),
            ) {
                if (sbbsUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = C.textDark,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Updating Address...", color = C.textDark, fontWeight = FontWeight.Bold)
                } else {
                    Text("Update Messaging Address", color = C.textDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
        return
    }

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
                lastMessage = when {
                    last.isTip -> "Tip: ${Helpers.formatBeam(last.tipAmount)} BEAM"
                    (last.file?.cid ?: "").isNotEmpty() -> "\uD83D\uDCCE ${last.file?.name ?: "File"}"
                    else -> last.text
                },
                lastTimestamp = last.timestamp,
                unreadCount = rawUnread[key] ?: 0,
                walletId = contact?.walletId ?: "",
            )
        }.sortedByDescending { it.lastTimestamp }
    }

    var deleteTarget by remember { mutableStateOf<Conversation?>(null) }

    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            ProtocolStartup.loadMessages(true)
            scope.launch {
                delay(2000)
                refreshing = false
            }
        },
        modifier = Modifier.fillMaxSize().background(C.bg),
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
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
                        ConversationCard(
                            conv,
                            onClick = { onOpenChat(conv.handle) },
                            onLongPress = { deleteTarget = conv },
                        )
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
    } // PullToRefreshBox

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Conversation", color = C.text) },
            text = { Text("Delete conversation with @${deleteTarget!!.handle}? This cannot be undone.", color = C.textSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        ProtocolStartup.deleteConversation("@${deleteTarget!!.handle}")
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = C.error),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("Delete", color = C.text, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteTarget = null }, shape = RoundedCornerShape(8.dp)) {
                    Text("Cancel", color = C.textSecondary)
                }
            },
            containerColor = C.card,
            shape = RoundedCornerShape(12.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationCard(conv: Conversation, onClick: () -> Unit, onLongPress: () -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { onClick() }, onLongClick = { onLongPress() }),
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
