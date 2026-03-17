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
import com.privimemobile.chat.ChatService
import com.privimemobile.chat.db.entities.ChatStateEntity
import com.privimemobile.chat.db.entities.ConversationEntity
import com.privimemobile.protocol.Helpers
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
    onRegister: () -> Unit = {},
) {
    // Wait for ChatService to initialize
    val isInitialized by ChatService.initialized.collectAsState()
    if (!isInitialized) {
        Box(Modifier.fillMaxSize().background(C.bg), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = C.accent)
        }
        return
    }

    // Observe chat state from Room DB
    val chatState by ChatService.observeState().collectAsState(initial = null)
    val isRegistered = chatState?.myHandle != null
    val sbbsNeedsUpdate by ChatService.identity.sbbsNeedsUpdate.collectAsState()

    // Landing page 1: Not registered
    if (!isRegistered) {
        NotRegisteredLanding(onRegister)
        return
    }

    // Landing page 2: SBBS needs re-registration (restored wallet)
    if (sbbsNeedsUpdate) {
        ReRegisterLanding(chatState!!)
        return
    }

    // Main conversation list — observe from Room DAO
    val conversations by ChatService.db?.conversationDao()?.observeAll()
        ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }

    var deleteTarget by remember { mutableStateOf<ConversationEntity?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            ChatService.sbbs.pollNow()
            scope.launch {
                delay(2000)
                refreshing = false
            }
        },
        modifier = Modifier.fillMaxSize().background(C.bg),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
            ) {
                Text("Chats", color = C.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "End-to-end encrypted messaging on Beam",
                    color = C.textSecondary, fontSize = 14.sp,
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
                            Text("Start a new chat by tapping +", color = C.textSecondary, fontSize = 13.sp)
                        }
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(conversations, key = { it.id }) { conv ->
                            ConversationCard(
                                conv = conv,
                                onClick = { onOpenChat(conv.convKey.removePrefix("@")) },
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
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New Chat")
            }
        }
    }

    // Delete dialog
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Conversation", color = C.text) },
            text = { Text("Delete conversation with ${deleteTarget!!.convKey}? This cannot be undone.", color = C.textSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            ChatService.db?.conversationDao()?.softDelete(deleteTarget!!.id)
                        }
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

@Composable
private fun NotRegisteredLanding(onRegister: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(C.bg).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Encrypted Messaging", color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "Register a @handle to start sending and receiving encrypted messages on Beam.",
            color = C.textSecondary, fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRegister,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = C.accent),
        ) {
            Text("Register Handle", color = C.textDark, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ReRegisterLanding(chatState: ChatStateEntity) {
    val currentDisplayName = chatState.myDisplayName ?: ""
    var displayName by remember { mutableStateOf(currentDisplayName) }
    var useExistingName by remember { mutableStateOf(currentDisplayName.isNotEmpty()) }
    var updating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (updating) {
        Column(
            modifier = Modifier.fillMaxSize().background(C.bg).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(color = C.accent, modifier = Modifier.size(48.dp), strokeWidth = 4.dp)
            Spacer(Modifier.height(24.dp))
            Text("Updating Address", color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("@${chatState.myHandle}", color = C.accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            Text(
                "Your messaging address is being updated on the Beam blockchain. This usually takes about 1 minute.",
                color = C.textSecondary, fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 20.sp,
            )
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().background(C.bg).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Welcome Back", color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("@${chatState.myHandle}", color = C.accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Text(
            "Your wallet was restored on a new device. Your messaging address needs to be updated so others can reach you.",
            color = C.textSecondary, fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 20.sp,
        )

        // Display name option
        Spacer(Modifier.height(16.dp))
        Text("Display Name", color = C.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        if (currentDisplayName.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = useExistingName,
                    onClick = { useExistingName = true; displayName = currentDisplayName },
                    colors = RadioButtonDefaults.colors(selectedColor = C.accent, unselectedColor = C.textSecondary),
                )
                Text("Keep \"$currentDisplayName\"", color = C.text, fontSize = 14.sp,
                    modifier = Modifier.clickable { useExistingName = true; displayName = currentDisplayName })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = !useExistingName,
                    onClick = { useExistingName = false; displayName = "" },
                    colors = RadioButtonDefaults.colors(selectedColor = C.accent, unselectedColor = C.textSecondary),
                )
                Text("Change display name", color = C.text, fontSize = 14.sp,
                    modifier = Modifier.clickable { useExistingName = false; displayName = "" })
            }
        }
        if (!useExistingName || currentDisplayName.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = displayName,
                onValueChange = { if (it.length <= 32) displayName = it },
                placeholder = { Text("Display name (optional)", color = C.textSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = C.text, unfocusedTextColor = C.text,
                    cursorColor = C.accent, focusedBorderColor = C.accent, unfocusedBorderColor = C.border,
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = Color(0x1AFFC107),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x4DFFC107)),
        ) {
            Text(
                "Your @handle is safe on-chain. Previous conversations cannot be restored as messages are stored locally. A small fee applies.",
                color = Color(0xFFFFC107), fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 17.sp,
                modifier = Modifier.padding(12.dp),
            )
        }

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
                    "You need BEAM to pay the transaction fee.",
                    color = C.error, fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 17.sp,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                updating = true
                val newDn = if (useExistingName) currentDisplayName else displayName.trim()
                // Create new SBBS address → update on-chain
                com.privimemobile.protocol.WalletApi.call("create_address", mapOf(
                    "type" to "regular",
                    "expiration" to "never",
                    "comment" to "PriviMe messaging",
                )) { result ->
                    val addr = result["address"] as? String
                    if (addr != null) {
                        val normalized = Helpers.normalizeWalletId(addr) ?: addr
                        ChatService.identity.updateMessagingAddress(normalized, newDn) { success, error ->
                            if (!success) updating = false
                        }
                    } else {
                        updating = false
                    }
                }
            },
            enabled = !updating && hasBalance,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = C.accent,
                disabledContainerColor = C.accent.copy(alpha = 0.3f),
            ),
        ) {
            Text("Update Messaging Address", color = C.textDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationCard(conv: ConversationEntity, onClick: () -> Unit, onLongPress: () -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = C.card),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Avatar
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(C.border),
                contentAlignment = Alignment.Center,
            ) {
                val initial = (conv.displayName ?: conv.handle ?: conv.convKey)
                    .removePrefix("@").firstOrNull()?.uppercase() ?: "?"
                Text(initial, color = C.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        conv.displayName?.ifEmpty { null } ?: conv.handle?.let { "@$it" } ?: conv.convKey,
                        color = C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        formatTime(conv.lastMessageTs),
                        color = C.textSecondary, fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        conv.lastMessagePreview ?: "",
                        color = C.textSecondary, fontSize = 13.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    if (conv.unreadCount > 0) {
                        Spacer(Modifier.width(8.dp))
                        Badge(containerColor = C.accent, contentColor = C.textDark) {
                            Text(if (conv.unreadCount > 99) "99+" else "${conv.unreadCount}")
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
    ) timeFormat.format(date) else dateFormat.format(date)
}
