package com.privimemobile.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.material.icons.filled.Group
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
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
    onCreateGroup: () -> Unit = {},
    onOpenGroup: (String) -> Unit = {},
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

    // Group list — observe from Room DAO
    val groups by ChatService.db?.groupDao()?.observeAll()
        ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }

    // Refresh groups on mount
    LaunchedEffect(Unit) {
        ChatService.groups.refreshMyGroups()
    }

    var menuTarget by remember { mutableStateOf<ConversationEntity?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // Chat folder tabs
    var activeTab by remember { mutableStateOf(0) } // 0=All, 1=Unread, 2=Archived

    // Filter conversations by tab + search query
    val filteredConversations = remember(conversations, searchQuery, activeTab) {
        val tabFiltered = when (activeTab) {
            1 -> conversations.filter { !it.archived && it.unreadCount > 0 }
            2 -> conversations.filter { it.archived }
            else -> conversations.filter { !it.archived }
        }
        if (searchQuery.isBlank()) tabFiltered
        else {
            val q = searchQuery.trim().lowercase()
            tabFiltered.filter { conv ->
                (conv.displayName ?: "").lowercase().contains(q) ||
                        (conv.handle ?: "").lowercase().contains(q)
            }
        }
    }

    val filteredGroups = remember(groups, searchQuery, activeTab) {
        val tabFiltered = when (activeTab) {
            1 -> groups.filter { !it.archived && it.unreadCount > 0 }
            2 -> groups.filter { it.archived }
            else -> groups.filter { !it.archived }
        }
        if (searchQuery.isBlank()) tabFiltered
        else {
            val q = searchQuery.trim().lowercase()
            tabFiltered.filter { it.name.lowercase().contains(q) }
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

                // ── Tab bar (All / Unread / Archived) ──
                val tabLabels = listOf("All", "Unread", "Archived")
                val unreadTotal = conversations.count { !it.archived && it.unreadCount > 0 }
                val archivedTotal = conversations.count { it.archived }
                ScrollableTabRow(
                    selectedTabIndex = activeTab,
                    containerColor = Color.Transparent,
                    contentColor = C.accent,
                    edgePadding = 12.dp,
                    divider = {},
                    indicator = {},
                ) {
                    tabLabels.forEachIndexed { idx, label ->
                        val badge = when (idx) {
                            1 -> if (unreadTotal > 0) " ($unreadTotal)" else ""
                            2 -> if (archivedTotal > 0) " ($archivedTotal)" else ""
                            else -> ""
                        }
                        Tab(
                            selected = activeTab == idx,
                            onClick = { activeTab = idx },
                            text = {
                                Text(
                                    "$label$badge",
                                    color = if (activeTab == idx) C.accent else C.textSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = if (activeTab == idx) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            },
                        )
                    }
                }

                // ── Conversation list ──
                if (filteredConversations.isEmpty() && filteredGroups.isEmpty()) {
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
                        // Group items
                        items(filteredGroups, key = { "g_${it.groupId}" }) { group ->
                            Box(modifier = Modifier.animateItem()) {
                                GroupRow(
                                    group = group,
                                    onClick = { onOpenGroup(group.groupId) },
                                )
                            }
                        }
                        // Conversation items
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

            // FABs - New Chat + Create Group
            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End,
            ) {
                // Small FAB - Create Group
                SmallFloatingActionButton(
                    onClick = onCreateGroup,
                    containerColor = C.surface,
                    contentColor = C.accent,
                    shape = CircleShape,
                ) {
                    Icon(Icons.Filled.Group, contentDescription = "Create Group", modifier = Modifier.size(20.dp))
                }
                // Main FAB - New Chat
                FloatingActionButton(
                    onClick = onNewChat,
                    containerColor = C.accent,
                    contentColor = C.textDark,
                    shape = CircleShape,
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "New Chat")
                }
            }
        }
    }

    // Conversation context menu
    if (menuTarget != null) {
        val target = menuTarget!!
        ModalBottomSheet(
            onDismissRequest = { menuTarget = null },
            containerColor = C.card,
            dragHandle = {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.textMuted.copy(alpha = 0.4f)))
                }
            },
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                // Header with avatar
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val avatarKey = target.handle ?: target.convKey
                    Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(avatarColor(avatarKey)), contentAlignment = Alignment.Center) {
                        val initial = (target.displayName ?: target.handle ?: target.convKey).removePrefix("@").firstOrNull()?.uppercase() ?: "?"
                        Text(initial, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(target.displayName?.ifEmpty { null } ?: target.handle?.let { "@$it" } ?: target.convKey, color = C.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        if (target.handle != null && target.displayName?.isNotEmpty() == true) {
                            Text("@${target.handle}", color = C.textSecondary, fontSize = 12.sp)
                        }
                    }
                }
                HorizontalDivider(color = C.border.copy(alpha = 0.3f))

                // Menu items with touch highlight
                ChatListMenuItem(if (target.pinned) "Unpin" else "Pin") {
                    scope.launch { ChatService.db?.conversationDao()?.setPinned(target.id, !target.pinned) }; menuTarget = null
                }
                ChatListMenuItem(if (target.muted) "Unmute" else "Mute") {
                    scope.launch { ChatService.db?.conversationDao()?.setMuted(target.id, !target.muted) }; menuTarget = null
                }
                ChatListMenuItem(if (target.archived) "Unarchive" else "Archive") {
                    scope.launch { ChatService.db?.conversationDao()?.setArchived(target.id, !target.archived) }; menuTarget = null
                }
                ChatListMenuItem(if (target.isBlocked) "Unblock" else "Block", color = if (target.isBlocked) C.text else C.error) {
                    scope.launch { ChatService.db?.conversationDao()?.setBlocked(target.id, !target.isBlocked) }; menuTarget = null
                }
                HorizontalDivider(color = C.border.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                ChatListMenuItem("Delete", color = C.error) {
                    scope.launch {
                        ChatService.db?.messageDao()?.softDeleteByConversation(target.id)
                        ChatService.db?.conversationDao()?.softDelete(target.id)
                    }; menuTarget = null
                }
            }
        }
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
            // Avatar (loads cached image or falls back to letter circle)
            com.privimemobile.ui.components.AvatarDisplay(
                handle = conv.convKey.removePrefix("@"),
                displayName = conv.displayName,
                size = 52.dp,
            )
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
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text("typing", color = C.accent, fontSize = 14.sp)
                            val infiniteTransition = rememberInfiniteTransition(label = "typingDots")
                            repeat(3) { i ->
                                val offsetY by infiniteTransition.animateFloat(
                                    initialValue = 0f, targetValue = -3f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(400, easing = FastOutSlowInEasing, delayMillis = i * 120),
                                        repeatMode = RepeatMode.Reverse,
                                    ), label = "dot$i",
                                )
                                Text(".", color = C.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.offset(y = offsetY.dp))
                            }
                        }
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
                        // Pulse animation on badge
                        val infiniteTransition = rememberInfiniteTransition(label = "badgePulse")
                        val badgeScale by infiniteTransition.animateFloat(
                            initialValue = 1f, targetValue = 1.12f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(600, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse,
                            ), label = "badgeScale",
                        )
                        Box(
                            modifier = Modifier
                                .graphicsLayer(scaleX = badgeScale, scaleY = badgeScale)
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

/** Chat list menu item with Telegram-style touch highlight. */
@Composable
private fun ChatListMenuItem(text: String, color: Color = C.text, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (pressed) C.accent.copy(alpha = 0.12f) else Color.Transparent)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { onClick() },
                )
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = color, fontSize = 15.sp)
    }
}

/** Group chat row in the chat list. */
@Composable
private fun GroupRow(
    group: com.privimemobile.chat.db.entities.GroupEntity,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Group avatar (icon)
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(avatarColor(group.groupId), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Group, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = group.name,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(4.dp))
                Text("${group.memberCount} members", color = C.textSecondary, fontSize = 12.sp)
            }
            if (group.lastMessagePreview != null) {
                Text(
                    text = group.lastMessagePreview!!,
                    color = C.textSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = if (group.myRole == 2) "You created this group" else "Group chat",
                    color = C.textMuted,
                    fontSize = 14.sp,
                )
            }
        }

        // Unread badge
        if (group.unreadCount > 0) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(C.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (group.unreadCount > 99) "99+" else group.unreadCount.toString(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
            }
        }
    }
}
