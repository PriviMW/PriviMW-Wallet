package com.privimemobile.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import androidx.compose.ui.res.stringResource
import com.privimemobile.R
import com.privimemobile.chat.ChatService
import com.privimemobile.chat.db.entities.ChatStateEntity
import com.privimemobile.chat.db.entities.ContactEntity
import com.privimemobile.chat.db.entities.ConversationEntity
import com.privimemobile.protocol.Helpers
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.Job
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
    val context = androidx.compose.ui.platform.LocalContext.current
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

    // Observe pending TXs for landing page gating
    val allPendingTxs by ChatService.db?.pendingTxDao()?.observePending()
        ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }

    // Landing page 1: Not registered (or pending registration TX)
    if (!isRegistered) {
        val pendingRegTx = allPendingTxs.any {
            it.action == com.privimemobile.chat.db.entities.PendingTxEntity.ACTION_REGISTER_HANDLE
        }
        if (pendingRegTx) {
            // Show pending screen instead of registration form
            Column(
                modifier = Modifier.fillMaxSize().background(C.bg).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(color = C.accent, modifier = Modifier.size(48.dp), strokeWidth = 4.dp)
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.register_registering), color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.register_pending_message),
                    color = C.textSecondary, fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 20.sp,
                )
            }
        } else {
            NotRegisteredLanding(onRegister)
        }
        return
    }

    // Landing page 2: SBBS needs re-registration (restored wallet) or pending update TX
    val pendingUpdateTx = allPendingTxs.any {
        it.action == com.privimemobile.chat.db.entities.PendingTxEntity.ACTION_UPDATE_PROFILE
    }
    if (sbbsNeedsUpdate || pendingUpdateTx) {
        ReRegisterLanding(chatState!!)
        return
    }

    // Main conversation list — observe from Room DAO
    val conversations by ChatService.db?.conversationDao()?.observeAll()
        ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }

    // Group list — pre-load from DB, then observe reactive updates
    var groups by remember { mutableStateOf<List<com.privimemobile.chat.db.entities.GroupEntity>>(emptyList()) }
    LaunchedEffect(Unit) {
        // Pre-load from DB first so the list always has existing groups on first render
        val existingGroups = ChatService.db?.groupDao()?.getAllGroups() ?: emptyList()
        groups = existingGroups
        // Then observe reactive updates (Room may emit synchronously on start)
        ChatService.db?.groupDao()?.observeAll()?.collect { updatedGroups ->
            groups = updatedGroups
        }
    }

    // Refresh groups on mount and when identity becomes available
    LaunchedEffect(isRegistered) {
        if (isRegistered) {
            ChatService.groups.refreshMyGroups()
        }
    }

    var menuTarget by remember { mutableStateOf<ConversationEntity?>(null) }
    var menuTargetGroup by remember { mutableStateOf<com.privimemobile.chat.db.entities.GroupEntity?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    // On-chain search state
    var onChainHandles by remember { mutableStateOf<List<ContactEntity>>(emptyList()) }
    var onChainGroups by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var isSearchingOnChain by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val myHandle = chatState?.myHandle
    val myGroupIds = remember(groups) { groups.map { it.groupId }.toSet() }

    // On-chain results that aren't already in local conversations
    val onChainNew = remember(onChainHandles, conversations, myHandle) {
        val localHandles = conversations.mapNotNull { it.handle }.toSet()
        onChainHandles.filter { it.handle !in localHandles && it.handle != myHandle }
    }
    val onChainGroupsNew = remember(onChainGroups, myGroupIds) {
        onChainGroups.filter { (it["group_id"] as? String) !in myGroupIds }
    }

    // Debounced on-chain search when user types in the search bar
    LaunchedEffect(searchQuery) {
        searchJob?.cancel()
        onChainHandles = emptyList()
        onChainGroups = emptyList()
        isSearchingOnChain = false

        val trimmed = searchQuery.trim().removePrefix("@").lowercase()
        if (trimmed.isEmpty() || !Regex("^[a-z0-9_]+$").matches(trimmed)) return@LaunchedEffect

        isSearchingOnChain = true
        searchJob = scope.launch {
            delay(300) // debounce
            val handles = try { ChatService.contacts.searchOnChain(trimmed) } catch (_: Exception) { emptyList() }
            val chainGroups = try { ChatService.groups.searchGroups(trimmed) } catch (_: Exception) { emptyList() }
            onChainHandles = handles.filter { it.handle != myHandle }
            onChainGroups = chainGroups
            isSearchingOnChain = false
        }
    }

    // Chat folder tabs
    var activeTab by remember { mutableStateOf(0) } // 0=All, 1=Unread, 2=Groups, 3=DMs, 4=Archived

    // Filter conversations by tab + search query
    // Note: group conversations stored in ConversationEntity have convKey starting with "g_"
    // but isGroup is not set, so we filter by convKey instead
    val dms = remember(conversations) { conversations.filter { !it.convKey.startsWith("g_") } }
    val groupConvs = remember(conversations) { conversations.filter { it.convKey.startsWith("g_") } }

    val filteredConversations = remember(conversations, searchQuery, activeTab, dms, groupConvs) {
        val tabFiltered = when (activeTab) {
            0 -> conversations.filter { !it.archived }  // All — DMs + group convs + standalone groups
            1 -> conversations.filter { !it.archived && it.unreadCount > 0 }
            2 -> emptyList()  // Groups tab — handled by filteredGroups
            3 -> dms.filter { !it.archived }  // DMs tab — only non-group conversations
            4 -> conversations.filter { it.archived }
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
            0 -> groups.filter { !it.archived }  // All — standalone group entries
            1 -> groups.filter { !it.archived && it.unreadCount > 0 }
            2 -> groups.filter { !it.archived }  // Groups tab — all non-archived groups
            3 -> emptyList()  // DMs tab — no groups here
            4 -> groups.filter { it.archived }
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
                ChatService.groups.refreshMyGroups()
                delay(2000)
                refreshing = false
            }
        },
        modifier = Modifier.fillMaxSize().background(C.bg),
    ) {
        // Non-saveable LazyListState — always starts at top, no flash on back navigation
        val chatListState = remember { androidx.compose.foundation.lazy.LazyListState(0, 0) }
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.chat_title), color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.chats_encrypted_notice), color = C.textSecondary, fontSize = 10.sp)
                            }
                            IconButton(onClick = onSearch, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Search messages",
                                    tint = C.textSecondary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }

                        // Search/filter bar — always visible (fixed above scrollable list)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(stringResource(R.string.chats_search_placeholder), color = C.textMuted, fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = C.textSecondary, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                if (isSearchingOnChain) {
                                    CircularProgressIndicator(Modifier.size(16.dp), color = C.accent, strokeWidth = 2.dp)
                                } else if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = stringResource(R.string.chats_cd_clear_search),
                                            tint = C.textSecondary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
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

                // ── Tab bar (All / Unread / Groups / DMs / Archived) ──
                val tabLabelRes = listOf(R.string.chats_tab_all, R.string.chats_tab_unread, R.string.chats_tab_groups, R.string.chats_tab_dms, R.string.chats_tab_archived)
                val unreadTotal = conversations.count { !it.archived && it.unreadCount > 0 } + groups.count { !it.archived && it.unreadCount > 0 }
                val groupsTotal = groups.count { !it.archived }
                val dmsTotal = dms.count { !it.archived }
                val archivedTotal = conversations.count { it.archived } + groups.count { it.archived }
                // Telegram-style tab selector with animated pill indicator — horizontally scrollable
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tabLabelRes.forEachIndexed { idx, labelRes ->
                        val label = stringResource(labelRes)
                        val badge = when (idx) {
                            1 -> if (unreadTotal > 0) " ($unreadTotal)" else ""
                            2 -> if (groupsTotal > 0) " ($groupsTotal)" else ""
                            3 -> if (dmsTotal > 0) " ($dmsTotal)" else ""
                            4 -> if (archivedTotal > 0) " ($archivedTotal)" else ""
                            else -> ""
                        }
                        val selected = activeTab == idx
                        val bgColor by animateColorAsState(
                            if (selected) C.accent.copy(alpha = 0.15f) else Color.Transparent,
                            animationSpec = tween(250), label = "tabBg$idx",
                        )
                        val textColor by animateColorAsState(
                            if (selected) C.accent else C.textSecondary,
                            animationSpec = tween(250), label = "tabTxt$idx",
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(bgColor)
                                .clickable { activeTab = idx }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        ) {
                            Text(
                                "$label$badge",
                                color = textColor,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                            )
                        }
                    }
                }

                // ── Conversation list ──
                val hasOnChain = onChainNew.isNotEmpty() || onChainGroupsNew.isNotEmpty() || (isSearchingOnChain && searchQuery.isNotBlank())

                if (filteredConversations.isEmpty() && filteredGroups.isEmpty() && !hasOnChain) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (searchQuery.isNotBlank()) {
                                Text(stringResource(R.string.chat_no_results, searchQuery), color = C.textSecondary, fontSize = 15.sp)
                            } else {
                                Text(stringResource(R.string.chats_empty), color = C.textSecondary, fontSize = 16.sp)
                                Spacer(Modifier.height(6.dp))
                                Text(stringResource(R.string.chats_tap_to_chat), color = C.textMuted, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    // Observe typing state for all conversations
                    val typingVer by ChatService.typingVersion.collectAsState()

                    // Pull-to-refresh
                    var refreshing by remember { mutableStateOf(false) }
                    val onRefresh: () -> Unit = {
                        refreshing = true
                        scope.launch {
                            // Re-resolve all contact display names from contract
                            val contacts = ChatService.db?.contactDao()?.getAll() ?: emptyList()
                            for (c in contacts) {
                                try {
                                    val resolved = ChatService.contacts.resolveHandle(c.handle)
                                    if (resolved?.displayName != null && resolved.displayName != c.displayName) {
                                        ChatService.db?.contactDao()?.updateDisplayName(c.handle, resolved.displayName)
                                        ChatService.db?.conversationDao()?.updateDisplayName("@${c.handle}", resolved.displayName)
                                    }
                                } catch (_: Exception) {}
                            }
                            // Refresh groups + cleanup deleted
                            ChatService.groups.refreshMyGroups()
                            ChatService.groups.cleanupDeletedGroups()
                            ChatService.identity.refreshIdentity(forceRefresh = true)
                            delay(500)
                            refreshing = false
                        }
                    }

                    // Unified list: conversations + groups sorted by last message time
                    val unifiedList = remember(filteredConversations, filteredGroups) {
                        data class ChatListItem(val isGroup: Boolean, val sortTs: Long, val pinned: Boolean, val conv: ConversationEntity? = null, val group: com.privimemobile.chat.db.entities.GroupEntity? = null)
                        val items = mutableListOf<ChatListItem>()
                        for (c in filteredConversations) {
                            if (c.convKey.startsWith("g_")) continue // skip group conversation entries
                            items.add(ChatListItem(false, c.lastMessageTs, c.pinned, conv = c))
                        }
                        for (g in filteredGroups) {
                            items.add(ChatListItem(true, g.lastMessageTs, g.pinned, group = g))
                        }
                        // Pinned items first, then by timestamp descending
                        items.sortedWith(compareByDescending<ChatListItem> { it.pinned }.thenByDescending { it.sortTs })
                    }

                    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh) {
                    LazyColumn(state = chatListState) {
                        items(unifiedList.size, key = { i ->
                            val item = unifiedList[i]
                            if (item.isGroup) "g_${item.group!!.groupId}" else "c_${item.conv!!.id}"
                        }) { i ->
                            val item = unifiedList[i]
                            var showDeleteConfirmItem by remember { mutableStateOf(false) }
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    when (value) {
                                        SwipeToDismissBoxValue.EndToStart -> {
                                            // Swipe left → show confirmation
                                            showDeleteConfirmItem = true
                                            false // don't dismiss yet
                                        }
                                        SwipeToDismissBoxValue.StartToEnd -> {
                                            // Swipe right → Archive
                                            scope.launch {
                                                if (item.isGroup) {
                                                    ChatService.db?.groupDao()?.setArchived(item.group!!.groupId, !item.group.archived)
                                                } else {
                                                    ChatService.db?.conversationDao()?.setArchived(item.conv!!.id, !(item.conv.archived))
                                                }
                                            }
                                            true
                                        }
                                        else -> false
                                    }
                                },
                                positionalThreshold = { it * 0.35f },
                            )
                            if (showDeleteConfirmItem) {
                                val name = if (item.isGroup) item.group!!.name else "@${item.conv!!.convKey.removePrefix("@")}"
                                val isGrp = item.isGroup
                                AlertDialog(
                                    onDismissRequest = { showDeleteConfirmItem = false },
                                    containerColor = C.card,
                                    title = { Text(stringResource(if (isGrp) R.string.chats_leave_group_title else R.string.chats_delete_chat_title), color = C.text, fontWeight = FontWeight.SemiBold) },
                                    text = { Text(stringResource(if (isGrp) R.string.chats_leave_group_confirm else R.string.chats_delete_chat_confirm, name), color = C.textSecondary) },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            showDeleteConfirmItem = false
                                            if (isGrp) {
                                                val gid = item.group!!.groupId
                                                ChatService.groups.leaveGroup(gid) { success, error ->
                                                    scope.launch {
                                                        if (!success) {
                                                            android.widget.Toast.makeText(
                                                                context,
                                                                context.getString(R.string.chats_leave_failed, error ?: context.getString(R.string.register_transaction_failed)),
                                                                android.widget.Toast.LENGTH_LONG
                                                            ).show()
                                                        } else {
                                                            // Wallet accepted TX data — soft-delete local conv
                                                            val conv = ChatService.db?.conversationDao()?.findByKey("g_${gid.take(16)}")
                                                            conv?.let {
                                                                ChatService.db?.messageDao()?.softDeleteByConversation(it.id)
                                                                ChatService.db?.conversationDao()?.softDelete(it.id)
                                                            }
                                                        }
                                                    }
                                                }
                                            } else {
                                                scope.launch {
                                                    val cid = item.conv!!.id
                                                    val handle = item.conv!!.convKey.removePrefix("@")
                                                    ChatService.db?.messageDao()?.softDeleteByConversation(cid)
                                                    ChatService.db?.conversationDao()?.softDelete(cid)
                                                    ChatService.db?.contactDao()?.deleteByHandle(handle)
                                                }
                                            }
                                        }) { Text(stringResource(if (isGrp) R.string.chats_leave else R.string.general_delete), color = C.error, fontWeight = FontWeight.Bold) }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showDeleteConfirmItem = false }) {
                                            Text(stringResource(R.string.general_cancel), color = C.textSecondary)
                                        }
                                    },
                                )
                            }
                            SwipeToDismissBox(
                                state = dismissState,
                                modifier = Modifier.animateItem(),
                                backgroundContent = {
                                    val progress = dismissState.progress
                                    val direction = dismissState.dismissDirection
                                    // Show color immediately as user starts swiping
                                    val bgColor = when (direction) {
                                        SwipeToDismissBoxValue.EndToStart -> C.error.copy(alpha = (progress * 2.5f).coerceIn(0f, 1f))
                                        SwipeToDismissBoxValue.StartToEnd -> C.accent.copy(alpha = (progress * 2.5f).coerceIn(0f, 1f))
                                        else -> Color.Transparent
                                    }
                                    val iconAlpha = (progress * 3f).coerceIn(0f, 1f)
                                    val iconAlignment = when (direction) {
                                        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                        else -> Alignment.CenterEnd
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(bgColor)
                                            .padding(horizontal = 24.dp),
                                        contentAlignment = iconAlignment,
                                    ) {
                                        if (direction == SwipeToDismissBoxValue.EndToStart) {
                                            Icon(Icons.Default.Delete, stringResource(R.string.chats_cd_delete), tint = Color.White.copy(alpha = iconAlpha))
                                        } else if (direction == SwipeToDismissBoxValue.StartToEnd) {
                                            Icon(Icons.Default.Archive, stringResource(R.string.chats_cd_archive), tint = Color.White.copy(alpha = iconAlpha))
                                        }
                                    }
                                },
                                enableDismissFromStartToEnd = true,
                                enableDismissFromEndToStart = true,
                            ) {
                                Surface(color = C.bg) {
                                if (item.isGroup) {
                                    val gConvKey = "g_${item.group!!.groupId.take(16)}"
                                    val gTypingHandles = if (typingVer >= 0) ChatService.getGroupTyping(gConvKey) else emptyList()
                                    val gConv = conversations.firstOrNull { it.convKey == gConvKey }
                                    val gDraft = gConv?.draftText
                                    GroupRow(group = item.group!!, onClick = { onOpenGroup(item.group.groupId) }, onLongPress = { menuTargetGroup = item.group }, typingHandles = gTypingHandles, draftText = gDraft)
                                } else {
                                    val peerTyping = typingVer >= 0 && ChatService.isTyping(item.conv!!.convKey)
                                    ConversationRow(conv = item.conv!!, onClick = { onOpenChat(item.conv!!.convKey.removePrefix("@")) }, onLongPress = { menuTarget = item.conv }, isTyping = peerTyping)
                                }
                                }
                            }
                        }


                    // ── On-chain search results (Telegram-style global search) ──

                    // Show spinner while searching
                    if (isSearchingOnChain && searchQuery.isNotBlank()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(Modifier.size(18.dp), color = C.accent, strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(stringResource(R.string.search_searching), color = C.textSecondary, fontSize = 13.sp)
                            }
                        }
                    }

                    // On-chain handle results
                    if (onChainNew.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.search_section_global),
                                color = C.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                            )
                        }
                        items(onChainNew.size, key = { "oc_${onChainNew[it].handle}" }) { idx ->
                            val contact = onChainNew[idx]
                            Column(modifier = Modifier.background(C.bg)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launch {
                                                ChatService.contacts.ensureContact(contact.handle, contact.displayName, contact.walletId)
                                            }
                                            // Navigate to chat (clears search)
                                            searchQuery = ""
                                            onOpenChat(contact.handle)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    com.privimemobile.ui.components.AvatarDisplay(
                                        handle = contact.handle,
                                        displayName = contact.displayName,
                                        size = 44.dp,
                                    )
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            contact.displayName?.ifEmpty { null } ?: "@${contact.handle}",
                                            color = C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                        )
                                        if (!contact.displayName.isNullOrEmpty()) {
                                            Text("@${contact.handle}", color = C.textSecondary, fontSize = 13.sp)
                                        }
                                    }
                                    Surface(shape = RoundedCornerShape(20.dp), color = C.accent) {
                                        Text(
                                            stringResource(R.string.search_button_chat), color = C.textDark, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                        )
                                    }
                                }
                                HorizontalDivider(
                                    color = C.border.copy(alpha = 0.5f),
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(start = 74.dp),
                                )
                            }
                        }
                    }

                    // On-chain group results
                    if (onChainGroupsNew.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.search_section_groups),
                                color = C.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                            )
                        }
                        items(onChainGroupsNew.size, key = { "ocg_${onChainGroupsNew[it]["group_id"]}" }) { idx ->
                            val g = onChainGroupsNew[idx]
                            val groupId = g["group_id"] as? String ?: return@items
                            val name = g["name"] as? String ?: ""
                            val creator = g["creator"] as? String ?: ""
                            val memberCount = g["member_count"] as? Int ?: 0
                            val needsApproval = (g["require_approval"] as? Int ?: 0) == 1
                            var joining by remember { mutableStateOf(false) }

                            Column(modifier = Modifier.background(C.bg)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF5C6BC0)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Filled.Group, null, tint = Color.White, modifier = Modifier.size(22.dp))
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(name, color = C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "$memberCount members · by @$creator",
                                            color = C.textSecondary, fontSize = 13.sp,
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            joining = true
                                            ChatService.groups.joinGroup(groupId) { success, error ->
                                                joining = false
                                                if (success) {
                                                    searchQuery = ""
                                                    scope.launch {
                                                        ChatService.groups.refreshMyGroups()
                                                    }
                                                    onOpenGroup(groupId)
                                                }
                                            }
                                        },
                                        enabled = !joining,
                                        colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                                        shape = RoundedCornerShape(20.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                        modifier = Modifier.height(34.dp),
                                    ) {
                                        if (joining) {
                                            CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                        } else {
                                            Text(
                                                if (needsApproval) stringResource(R.string.search_button_request) else stringResource(R.string.search_button_join),
                                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(
                                    color = C.border.copy(alpha = 0.5f),
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(start = 74.dp),
                                )
                            }
                        }
                    }
                    } // close LazyColumn
                    } // close PullToRefreshBox
                }
            }

            // FABs - New Chat + Create Group (hide on scroll down, show on scroll up)
            val fabVisible = remember { mutableStateOf(true) }
            val prevFirstVisible = remember { mutableIntStateOf(0) }
            val prevScrollOffset = remember { mutableIntStateOf(0) }
            LaunchedEffect(chatListState.firstVisibleItemIndex, chatListState.firstVisibleItemScrollOffset) {
                val currentFirst = chatListState.firstVisibleItemIndex
                val currentOffset = chatListState.firstVisibleItemScrollOffset
                if (currentFirst > prevFirstVisible.intValue || (currentFirst == prevFirstVisible.intValue && currentOffset > prevScrollOffset.intValue + 20)) {
                    fabVisible.value = false // scrolling down
                } else if (currentFirst < prevFirstVisible.intValue || (currentFirst == prevFirstVisible.intValue && currentOffset < prevScrollOffset.intValue - 20)) {
                    fabVisible.value = true // scrolling up
                }
                prevFirstVisible.intValue = currentFirst
                prevScrollOffset.intValue = currentOffset
            }
            val fabScale by animateFloatAsState(
                targetValue = if (fabVisible.value) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
                label = "fabScale",
            )
            val fabAlpha by animateFloatAsState(
                targetValue = if (fabVisible.value) 1f else 0f,
                animationSpec = tween(200),
                label = "fabAlpha",
            )
            // Hide FABs while searching — they block the join button on search results
            if (searchQuery.isBlank()) {
                Column(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                        .graphicsLayer { scaleX = fabScale; scaleY = fabScale; alpha = fabAlpha },
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    // Small FAB - Create Group
                    SmallFloatingActionButton(
                        onClick = onCreateGroup,
                        containerColor = C.card,
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
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.chats_fab_new_chat))
                    }
                }
            }
        }
    }

    // Group context menu
    if (menuTargetGroup != null) {
        val target = menuTargetGroup!!
        ModalBottomSheet(
            onDismissRequest = { menuTargetGroup = null },
            containerColor = C.card,
            dragHandle = {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.textMuted.copy(alpha = 0.4f)))
                }
            },
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val grpAvatarBmp = remember(target.groupId, target.avatarHash) {
                        try {
                            val f = java.io.File(context.filesDir, "group_avatars/${target.groupId}.webp")
                            if (f.exists()) android.graphics.BitmapFactory.decodeFile(f.absolutePath) else null
                        } catch (_: Exception) { null }
                    }
                    if (grpAvatarBmp != null) {
                        Image(
                            bitmap = grpAvatarBmp.asImageBitmap(),
                            contentDescription = "Group",
                            modifier = Modifier.size(40.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(avatarColor(target.groupId)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Group, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(target.name, color = C.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(stringResource(R.string.group_member_count_format, target.memberCount), color = C.textSecondary, fontSize = 12.sp)
                    }
                }
                HorizontalDivider(color = C.border.copy(alpha = 0.3f))

                ChatListMenuItem(if (target.pinned) stringResource(R.string.chats_swipe_unpin) else stringResource(R.string.chats_swipe_pin)) {
                    val gid = target.groupId; val newVal = !target.pinned
                    scope.launch {
                        ChatService.db?.groupDao()?.setPinned(gid, newVal)
                        val check = ChatService.db?.groupDao()?.findByGroupId(gid)
                        android.util.Log.d("ChatsScreen", "setPinned($gid, $newVal) → DB now: pinned=${check?.pinned}")
                    }
                    menuTargetGroup = null
                }
                ChatListMenuItem(if (target.muted) stringResource(R.string.chats_swipe_unmute) else stringResource(R.string.chats_swipe_mute)) {
                    val gid = target.groupId; val newVal = !target.muted
                    scope.launch {
                        ChatService.db?.groupDao()?.setMuted(gid, newVal)
                        val check = ChatService.db?.groupDao()?.findByGroupId(gid)
                        android.util.Log.d("ChatsScreen", "setMuted($gid, $newVal) → DB now: muted=${check?.muted}")
                    }
                    menuTargetGroup = null
                }
                ChatListMenuItem(if (target.archived) stringResource(R.string.chats_unarchive) else stringResource(R.string.chats_archive)) {
                    scope.launch { ChatService.db?.groupDao()?.setArchived(target.groupId, !target.archived) }
                    menuTargetGroup = null
                }
                HorizontalDivider(color = C.border.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                ChatListMenuItem(stringResource(R.string.chats_leave_group), color = C.error) {
                    scope.launch {
                        ChatService.groups.leaveGroup(target.groupId)
                    }
                    menuTargetGroup = null
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
                    val dmAvatarBmp = remember(avatarKey) {
                        try {
                            val f = java.io.File(context.filesDir, "avatars/${avatarKey.removePrefix("@")}.webp")
                            if (f.exists()) android.graphics.BitmapFactory.decodeFile(f.absolutePath) else null
                        } catch (_: Exception) { null }
                    }
                    if (dmAvatarBmp != null) {
                        Image(
                            bitmap = dmAvatarBmp.asImageBitmap(),
                            contentDescription = "Avatar",
                            modifier = Modifier.size(40.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(avatarColor(avatarKey)), contentAlignment = Alignment.Center) {
                            val initial = (target.displayName ?: target.handle ?: target.convKey).removePrefix("@").firstOrNull()?.uppercase() ?: "?"
                            Text(initial, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
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
                ChatListMenuItem(if (target.pinned) stringResource(R.string.chats_swipe_unpin) else stringResource(R.string.chats_swipe_pin)) {
                    scope.launch { ChatService.db?.conversationDao()?.setPinned(target.id, !target.pinned) }; menuTarget = null
                }
                ChatListMenuItem(if (target.muted) stringResource(R.string.chats_swipe_unmute) else stringResource(R.string.chats_swipe_mute)) {
                    scope.launch { ChatService.db?.conversationDao()?.setMuted(target.id, !target.muted) }; menuTarget = null
                }
                ChatListMenuItem(if (target.archived) stringResource(R.string.chats_unarchive) else stringResource(R.string.chats_archive)) {
                    scope.launch { ChatService.db?.conversationDao()?.setArchived(target.id, !target.archived) }; menuTarget = null
                }
                ChatListMenuItem(if (target.isBlocked) stringResource(R.string.chat_unblock_user) else stringResource(R.string.chat_block_user), color = if (target.isBlocked) C.text else C.error) {
                    scope.launch { ChatService.db?.conversationDao()?.setBlocked(target.id, !target.isBlocked) }; menuTarget = null
                }
                HorizontalDivider(color = C.border.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                ChatListMenuItem(stringResource(R.string.general_delete), color = C.error) {
                    scope.launch {
                        val db = ChatService.db ?: return@launch
                        // Delete attachment files from disk
                        val attachments = db.attachmentDao().getAllByConversation(target.id)
                        for (att in attachments) {
                            if (att.localPath != null) {
                                try { java.io.File(att.localPath).delete() } catch (_: Exception) {}
                            }
                        }
                        // Delete attachment DB records
                        db.attachmentDao().deleteByConversation(target.id)
                        // Delete wallpaper file + prefs
                        val handle = target.convKey.removePrefix("@")
                        try { java.io.File(context.filesDir, "wallpaper_$handle.jpg").delete() } catch (_: Exception) {}
                        context.getSharedPreferences("privime_prefs", 0).edit()
                            .remove("wallpaper_${target.convKey}").apply()
                        // Clear draft
                        db.conversationDao().setDraft(target.id, null)
                        // Soft-delete messages (keep dedup keys to prevent SBBS re-delivery)
                        db.messageDao().softDeleteByConversation(target.id)
                        // Soft-delete conversation
                        db.conversationDao().softDelete(target.id)
                        // Remove from contacts list if this was a DM
                        if (!target.isGroup) {
                            db.contactDao().deleteByHandle(handle)
                        }
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
        Text(stringResource(R.string.register_not_registered_title), color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.register_not_registered_subtitle),
            color = C.textSecondary, fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.chats_encrypted_notice),
            color = C.textSecondary.copy(alpha = 0.6f), fontSize = 11.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRegister,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = C.accent),
        ) {
            Text(stringResource(R.string.register_not_registered_button), color = C.textDark, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ReRegisterLanding(chatState: ChatStateEntity) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentDisplayName = chatState.myDisplayName ?: ""
    var displayName by remember { mutableStateOf(currentDisplayName) }
    var useExistingName by remember { mutableStateOf(currentDisplayName.isNotEmpty()) }
    var updating by remember { mutableStateOf(false) }
    var txStatus by remember { mutableStateOf("pending") } // pending, confirmed, failed
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var newAddress by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // Check for existing pending update_profile TX (survives navigation)
    val pendingTxs by ChatService.db?.pendingTxDao()?.observePending()
        ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val existingPendingUpdate = pendingTxs.firstOrNull {
        it.action == com.privimemobile.chat.db.entities.PendingTxEntity.ACTION_UPDATE_PROFILE
    }
    LaunchedEffect(existingPendingUpdate) {
        if (existingPendingUpdate != null && !updating) {
            updating = true
            txStatus = "pending"
        }
        if (existingPendingUpdate == null && updating && txStatus == "pending") {
            // TX processed — check if SBBS update flag is clear
            if (!ChatService.identity.sbbsNeedsUpdate.value) {
                txStatus = "confirmed"
                ChatService.identity.refreshIdentity(forceRefresh = true)
            }
        }
    }

    // Block back navigation while TX is pending
    if (updating && txStatus == "pending") {
        androidx.activity.compose.BackHandler {}
    }

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
                        ChatService.identity.refreshIdentity(forceRefresh = true)
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
                Text(stringResource(R.string.chats_address_updated), color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.chats_handle_ready, chatState.myHandle ?: ""), color = C.accent, fontSize = 16.sp)
            } else if (txStatus == "failed") {
                Text("\u274C", fontSize = 48.sp)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.chats_update_failed), color = C.error, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(errorMsg ?: stringResource(R.string.register_transaction_failed), color = C.textSecondary, fontSize = 14.sp)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { updating = false; txStatus = "pending"; errorMsg = null },
                    colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                ) { Text(stringResource(R.string.register_try_again), color = C.textDark, fontWeight = FontWeight.Bold) }
            } else {
                CircularProgressIndicator(color = C.accent, modifier = Modifier.size(48.dp), strokeWidth = 4.dp)
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.chats_updating_address), color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("@${chatState.myHandle}", color = C.accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.chats_updating_address_msg),
                    color = C.textSecondary, fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 20.sp,
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.chats_welcome_back), color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("@${chatState.myHandle}", color = C.accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.chats_restored_notice),
            color = C.textSecondary, fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 20.sp,
        )

        // Display name option
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.register_display_name_label), color = C.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        if (currentDisplayName.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = useExistingName,
                    onClick = { useExistingName = true; displayName = currentDisplayName },
                    colors = RadioButtonDefaults.colors(selectedColor = C.accent, unselectedColor = C.textSecondary),
                )
                Text(stringResource(R.string.chats_reregister_keep_name, currentDisplayName), color = C.text, fontSize = 14.sp,
                    modifier = Modifier.clickable { useExistingName = true; displayName = currentDisplayName })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = !useExistingName,
                    onClick = { useExistingName = false; displayName = "" },
                    colors = RadioButtonDefaults.colors(selectedColor = C.accent, unselectedColor = C.textSecondary),
                )
                Text(stringResource(R.string.chats_reregister_change_name), color = C.text, fontSize = 14.sp,
                    modifier = Modifier.clickable { useExistingName = false; displayName = "" })
            }
        }
        if (!useExistingName || currentDisplayName.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = displayName,
                onValueChange = { if (it.length <= 32) displayName = it },
                placeholder = { Text(stringResource(R.string.chats_reregister_name_placeholder), color = C.textSecondary) },
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
                stringResource(R.string.chats_reregister_info),
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
                    stringResource(R.string.chats_reregister_no_beam),
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
                        errorMsg = context.getString(R.string.register_sbbs_failed)
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
            Text(stringResource(R.string.chats_reregister_button), color = C.textDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 1000f),
        label = "convPressScale",
    )

    Column(modifier = Modifier.background(C.bg).graphicsLayer { scaleX = pressScale; scaleY = pressScale }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongPress, interactionSource = interactionSource, indication = LocalIndication.current)
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
                            contentDescription = stringResource(R.string.chats_cd_muted),
                            tint = C.textSecondary,
                            modifier = Modifier.padding(start = 4.dp).size(14.dp),
                        )
                    }
                    if (conv.pinned) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = stringResource(R.string.chats_cd_pinned),
                            tint = C.textSecondary,
                            modifier = Modifier.padding(start = 4.dp).size(14.dp),
                        )
                    }
                    if (conv.isBlocked) {
                        Icon(
                            Icons.Default.Block,
                            contentDescription = stringResource(R.string.chats_cd_blocked),
                            tint = Color(0xFFEF5350),
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupRow(
    group: com.privimemobile.chat.db.entities.GroupEntity,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
    typingHandles: List<String> = emptyList(),
    draftText: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 1000f),
        label = "grpPressScale",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .combinedClickable(onClick = onClick, onLongClick = onLongPress, interactionSource = interactionSource, indication = LocalIndication.current)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Group avatar (custom image or default icon)
        val context = LocalContext.current
        val groupAvatarBmp = remember(group.groupId, group.avatarHash) {
            try {
                val f = java.io.File(context.filesDir, "group_avatars/${group.groupId}.webp")
                if (f.exists()) android.graphics.BitmapFactory.decodeFile(f.absolutePath) else null
            } catch (_: Exception) { null }
        }
        if (groupAvatarBmp != null) {
            androidx.compose.foundation.Image(
                bitmap = groupAvatarBmp.asImageBitmap(),
                contentDescription = "Group",
                modifier = Modifier.size(52.dp).clip(CircleShape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.size(52.dp).background(avatarColor(group.groupId), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Group, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!group.isPublic) {
                    Icon(Icons.Default.Lock, "Private", tint = C.textSecondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = group.name,
                    color = C.text,
                    fontSize = 16.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (group.pinned) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        tint = C.textSecondary,
                        modifier = Modifier.padding(start = 4.dp).size(14.dp),
                    )
                }
                if (group.muted) {
                    Icon(
                        Icons.Default.NotificationsOff,
                        contentDescription = "Muted",
                        tint = C.textSecondary,
                        modifier = Modifier.padding(start = 4.dp).size(14.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    formatTime(group.lastMessageTs),
                    color = C.textSecondary, fontSize = 12.sp,
                )
            }
            // Preview: typing > draft > last message
            val hasDraft = !draftText.isNullOrEmpty()
            if (typingHandles.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val label = if (typingHandles.size == 1) "@${typingHandles[0]} typing"
                        else "${typingHandles.size} people typing"
                    Text(label, color = C.accent, fontSize = 14.sp)
                    val infiniteTransition = rememberInfiniteTransition(label = "gTyping")
                    repeat(3) { i ->
                        val offsetY by infiniteTransition.animateFloat(
                            initialValue = 0f, targetValue = -3f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(400, easing = FastOutSlowInEasing, delayMillis = i * 120),
                                repeatMode = RepeatMode.Reverse,
                            ), label = "gDot$i",
                        )
                        Text(".", color = C.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.offset(y = offsetY.dp))
                    }
                }
            } else if (hasDraft) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = C.error, fontWeight = FontWeight.Medium)) { append("Draft: ") }
                        withStyle(SpanStyle(color = C.textSecondary)) { append(draftText!!) }
                    },
                    fontSize = 14.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (group.lastMessagePreview != null) {
                Text(
                    text = group.lastMessagePreview!!,
                    color = C.textSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = if (group.myRole == 2) "You created this group" else "Group chat",
                    color = C.textMuted,
                    fontSize = 14.sp,
                )
            }
        }

        // Unread badge (same style as DM)
        if (group.unreadCount > 0) {
            Spacer(Modifier.width(8.dp))
            val infiniteTransition = rememberInfiniteTransition(label = "gBadgePulse")
            val badgeScale by infiniteTransition.animateFloat(
                initialValue = 1f, targetValue = 1.12f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ), label = "gBadgeScale",
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
                    if (group.unreadCount > 99) "99+" else group.unreadCount.toString(),
                    color = C.textDark,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
