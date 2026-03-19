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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import kotlin.math.abs

/** Deterministic avatar colors from handle/name hash. */
private val avatarColors = listOf(
    Color(0xFF5C6BC0), // indigo
    Color(0xFF26A69A), // teal
    Color(0xFFEF5350), // red
    Color(0xFFAB47BC), // purple
    Color(0xFF42A5F5), // blue
    Color(0xFFFF7043), // deep orange
    Color(0xFF66BB6A), // green
    Color(0xFFEC407A), // pink
    Color(0xFFFFA726), // orange
    Color(0xFF78909C), // blue grey
)

private fun avatarColor(key: String): Color {
    val hash = abs(key.lowercase().hashCode())
    return avatarColors[hash % avatarColors.size]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    onOpenChat: (String) -> Unit = {},
    onNewChat: () -> Unit = {},
    onRegister: () -> Unit = {},
    onSearch: () -> Unit = {},
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

    var menuTarget by remember { mutableStateOf<ConversationEntity?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // Filter conversations by search query
    val filteredConversations = remember(conversations, searchQuery) {
        if (searchQuery.isBlank()) conversations
        else {
            val q = searchQuery.trim().lowercase()
            conversations.filter { conv ->
                (conv.displayName ?: "").lowercase().contains(q) ||
                        (conv.handle ?: "").lowercase().contains(q)
            }
        }
    }

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
            Column(modifier = Modifier.fillMaxSize()) {
                // ── Top bar ──
                Surface(color = C.bg) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Chats", color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            IconButton(onClick = onSearch, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Search messages",
                                    tint = C.textSecondary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }

                        // Search/filter bar
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search conversations...", color = C.textMuted, fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = C.textSecondary, modifier = Modifier.size(18.dp))
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = C.card,
                                unfocusedContainerColor = C.card,
                                cursorColor = C.accent,
                                focusedTextColor = C.text,
                                unfocusedTextColor = C.text,
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                        )
                    }
                }

                // ── Conversation list ──
                if (filteredConversations.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (searchQuery.isNotBlank()) {
                                Text("No results for \"$searchQuery\"", color = C.textSecondary, fontSize = 15.sp)
                            } else {
                                Text("No conversations yet", color = C.textSecondary, fontSize = 16.sp)
                                Spacer(Modifier.height(6.dp))
                                Text("Tap the pencil to start a chat", color = C.textMuted, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    // Observe typing state for all conversations
                    val typingVer by ChatService.typingVersion.collectAsState()

                    LazyColumn {
                        items(filteredConversations, key = { it.id }) { conv ->
                            val peerTyping = typingVer >= 0 && ChatService.isTyping(conv.convKey)
                            Box(modifier = Modifier.animateItem()) {
                                ConversationRow(
                                    conv = conv,
                                    onClick = { onOpenChat(conv.convKey.removePrefix("@")) },
                                    onLongPress = { menuTarget = conv },
                                    isTyping = peerTyping,
                                )
                            }
                        }
                    }
                }
            }

            // FAB - New Chat (pencil icon like Telegram)
            FloatingActionButton(
                onClick = onNewChat,
                containerColor = C.accent,
                contentColor = C.textDark,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                shape = CircleShape,
            ) {
                Icon(Icons.Filled.Edit, contentDescription = "New Chat")
            }
        }
    }

    // Conversation context menu
    if (menuTarget != null) {
        val target = menuTarget!!
        AlertDialog(
            onDismissRequest = { menuTarget = null },
            containerColor = C.card,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val avatarKey = target.handle ?: target.convKey
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(avatarColor(avatarKey)),
                        contentAlignment = Alignment.Center,
                    ) {
                        val initial = (target.displayName ?: target.handle ?: target.convKey)
                            .removePrefix("@").firstOrNull()?.uppercase() ?: "?"
                        Text(initial, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            target.displayName?.ifEmpty { null } ?: target.handle?.let { "@$it" } ?: target.convKey,
                            color = C.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                        )
                        if (target.handle != null && target.displayName?.isNotEmpty() == true) {
                            Text("@${target.handle}", color = C.textSecondary, fontSize = 12.sp)
                        }
                    }
                }
            },
            text = {
                Column {
                    // Pin / Unpin
                    TextButton(
                        onClick = {
                            scope.launch {
                                ChatService.db?.conversationDao()?.setPinned(target.id, !target.pinned)
                            }
                            menuTarget = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.PushPin, contentDescription = null,
                                tint = C.textSecondary, modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(if (target.pinned) "Unpin" else "Pin", color = C.text)
                        }
                    }

                    // Mute / Unmute
                    TextButton(
                        onClick = {
                            scope.launch {
                                ChatService.db?.conversationDao()?.setMuted(target.id, !target.muted)
                            }
                            menuTarget = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.NotificationsOff, contentDescription = null,
                                tint = C.textSecondary, modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(if (target.muted) "Unmute" else "Mute", color = C.text)
                        }
                    }

                    // Block / Unblock
                    TextButton(
                        onClick = {
                            scope.launch {
                                ChatService.db?.conversationDao()?.setBlocked(target.id, !target.isBlocked)
                            }
                            menuTarget = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Block, contentDescription = null,
                                tint = if (target.isBlocked) C.textSecondary else C.error,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (target.isBlocked) "Unblock" else "Block",
                                color = if (target.isBlocked) C.text else C.error,
                            )
                        }
                    }

                    HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 4.dp))

                    // Delete
                    TextButton(
                        onClick = {
                            scope.launch {
                                ChatService.db?.messageDao()?.softDeleteByConversation(target.id)
                                ChatService.db?.conversationDao()?.softDelete(target.id)
                            }
                            menuTarget = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Delete, contentDescription = null,
                                tint = C.error, modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("Delete", color = C.error)
                        }
                    }
                }
            },
            confirmButton = {},
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
    var txStatus by remember { mutableStateOf("pending") } // pending, confirmed, failed
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var newAddress by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // Poll to detect TX confirmation — check if SBBS address is now ours
    if (updating && txStatus == "pending" && newAddress.isNotEmpty()) {
        LaunchedEffect(newAddress) {
            while (true) {
                delay(5000)
                try {
                    val result = com.privimemobile.protocol.WalletApi.callAsync(
                        "validate_address", mapOf("address" to newAddress)
                    )
                    // Check contract for updated wallet_id
                    val contractResult = com.privimemobile.protocol.ShaderInvoker.invokeAsync(
                        "user", "my_handle"
                    )
                    val onChainWalletId = Helpers.normalizeWalletId(
                        contractResult["wallet_id"] as? String ?: ""
                    )
                    if (onChainWalletId == newAddress) {
                        txStatus = "confirmed"
                        ChatService.identity.clearSbbsNeedsUpdate()
                        ChatService.identity.refreshIdentity()
                        break
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // Auto-dismiss after confirmation
    LaunchedEffect(txStatus) {
        if (txStatus == "confirmed") {
            delay(1500)
        }
    }

    if (updating) {
        Column(
            modifier = Modifier.fillMaxSize().background(C.bg).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (txStatus == "confirmed") {
                Text("\u2705", fontSize = 48.sp)
                Spacer(Modifier.height(16.dp))
                Text("Address Updated!", color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("@${chatState.myHandle} is ready", color = C.accent, fontSize = 16.sp)
            } else if (txStatus == "failed") {
                Text("\u274C", fontSize = 48.sp)
                Spacer(Modifier.height(16.dp))
                Text("Update Failed", color = C.error, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(errorMsg ?: "Transaction failed", color = C.textSecondary, fontSize = 14.sp)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { updating = false; txStatus = "pending"; errorMsg = null },
                    colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                ) { Text("Try Again", color = C.textDark, fontWeight = FontWeight.Bold) }
            } else {
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
                txStatus = "pending"
                val newDn = if (useExistingName) currentDisplayName else displayName.trim()
                com.privimemobile.protocol.WalletApi.call("create_address", mapOf(
                    "type" to "regular",
                    "expiration" to "never",
                    "comment" to "PriviMe messaging",
                )) { result ->
                    val addr = result["address"] as? String
                    if (addr != null) {
                        val normalized = Helpers.normalizeWalletId(addr) ?: addr
                        newAddress = normalized
                        ChatService.identity.updateMessagingAddress(normalized, newDn) { success, error ->
                            if (!success) {
                                errorMsg = error
                                txStatus = "failed"
                            }
                        }
                    } else {
                        errorMsg = "Failed to create SBBS address"
                        txStatus = "failed"
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

/**
 * Telegram-style conversation row — flat with divider, colored avatar, proper icons.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(conv: ConversationEntity, onClick: () -> Unit, onLongPress: () -> Unit = {}, isTyping: Boolean = false) {
    val avatarKey = conv.handle ?: conv.convKey
    val displayLabel = conv.displayName?.ifEmpty { null } ?: conv.handle?.let { "@$it" } ?: conv.convKey
    val initial = (conv.displayName ?: conv.handle ?: conv.convKey)
        .removePrefix("@").firstOrNull()?.uppercase() ?: "?"

    Column(modifier = Modifier.background(C.bg)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongPress)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Circular avatar with deterministic color
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape).background(avatarColor(avatarKey)),
                contentAlignment = Alignment.Center,
            ) {
                Text(initial, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Top line: name + indicators + time
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        displayLabel,
                        color = C.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (conv.muted) {
                        Icon(
                            Icons.Default.NotificationsOff,
                            contentDescription = "Muted",
                            tint = C.textSecondary,
                            modifier = Modifier.padding(start = 4.dp).size(14.dp),
                        )
                    }
                    if (conv.pinned) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = C.textSecondary,
                            modifier = Modifier.padding(start = 4.dp).size(14.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formatTime(conv.lastMessageTs),
                        color = if (conv.unreadCount > 0) C.accent else C.textSecondary,
                        fontSize = 12.sp,
                    )
                }

                Spacer(Modifier.height(3.dp))

                // Bottom line: preview + unread badge
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val hasDraft = !conv.draftText.isNullOrEmpty()
                    if (isTyping) {
                        Text(
                            "typing...",
                            color = C.accent,
                            fontSize = 14.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    } else if (hasDraft) {
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = C.error, fontWeight = FontWeight.Medium)) {
                                    append("Draft: ")
                                }
                                withStyle(SpanStyle(color = C.textSecondary)) {
                                    append(conv.draftText!!)
                                }
                            },
                            fontSize = 14.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Text(
                            conv.lastMessagePreview ?: "",
                            color = C.textSecondary,
                            fontSize = 14.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (conv.unreadCount > 0) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minWidth = 22.dp)
                                .clip(CircleShape)
                                .background(C.accent)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (conv.unreadCount > 99) "99+" else "${conv.unreadCount}",
                                color = C.textDark,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
        // Divider — inset past avatar
        HorizontalDivider(
            color = C.border.copy(alpha = 0.5f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(start = 82.dp),
        )
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
