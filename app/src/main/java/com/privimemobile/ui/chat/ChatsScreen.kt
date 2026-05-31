package com.privimemobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.privimemobile.R
import com.privimemobile.chat.ChatPinOrder
import com.privimemobile.chat.ChatService
import com.privimemobile.ui.chat.chats.*
import com.privimemobile.chat.db.entities.ConversationEntity
import com.privimemobile.ui.theme.C

/**
 * In-memory snapshot while ChatsScreen is off-screen (e.g. inside a chat).
 * Avoids re-reading Room and replaying list animations when popping back.
 */
private object ChatsListSnapshot {
    var conversations: List<ConversationEntity> = emptyList()
    var groups: List<com.privimemobile.chat.db.entities.GroupEntity> = emptyList()
    var dbSeeded: Boolean = false
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

    // observeState() seeds the initial value via onStart, so collectAsState gets it immediately
    val chatState by ChatService.observeState().collectAsState(initial = null)
    val isRegistered = chatState?.myHandle != null
    val sbbsNeedsUpdate by ChatService.identity.sbbsNeedsUpdate.collectAsState()

    // Observe pending TXs for landing page gating
    val allPendingTxs by ChatService.db?.pendingTxDao()?.observePending()
        ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }

    // Landing page 1: Not registered (or pending registration TX)
    if (!isRegistered) {
        if (chatState == null) {
            Box(Modifier.fillMaxSize().background(C.bg), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = C.accent)
            }
            return
        }
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

    // Restore snapshot when returning from ChatScreen; observe for live updates
    var conversations by remember { mutableStateOf(ChatsListSnapshot.conversations) }
    var groups by remember { mutableStateOf(ChatsListSnapshot.groups) }
    LaunchedEffect(Unit) {
        if (!ChatsListSnapshot.dbSeeded) {
            withContext(Dispatchers.IO) {
                ChatsListSnapshot.conversations =
                    ChatService.db?.conversationDao()?.getAllActive() ?: emptyList()
                ChatsListSnapshot.groups =
                    ChatService.db?.groupDao()?.getAllGroups() ?: emptyList()
                ChatsListSnapshot.dbSeeded = true
            }
            conversations = ChatsListSnapshot.conversations
            groups = ChatsListSnapshot.groups
        }
        coroutineScope {
            launch {
                ChatService.db?.conversationDao()?.observeAll()?.collect { updated ->
                    conversations = updated
                    ChatsListSnapshot.conversations = updated
                }
            }
            launch {
                ChatService.db?.groupDao()?.observeAll()?.collect { updated ->
                    groups = updated
                    ChatsListSnapshot.groups = updated
                }
            }
        }
    }

    // Refresh groups when identity is ready — ChatService scope survives tab switches.
    LaunchedEffect(isRegistered) {
        if (!isRegistered || !ChatService.initialized.value) return@LaunchedEffect
        ChatService.scope.launch {
            try {
                ChatService.groups.refreshMyGroups()
            } catch (e: Exception) {
                android.util.Log.w("ChatsScreen", "refreshMyGroups failed: ${e.message}")
            }
        }
    }

    var showReorderPinned by remember { mutableStateOf(false) }
    val state = rememberSaveable(saver = ChatsListState.Saver) { ChatsListState() }
    val scope = rememberCoroutineScope()
    val myHandle = chatState?.myHandle
    val myGroupIds = remember(groups) { groups.map { it.groupId }.toSet() }

    // On-chain results that aren't already in local conversations
    val onChainNew = remember(state.onChainHandles, conversations, myHandle) {
        val localHandles = conversations.mapNotNull { it.handle }.toSet()
        state.onChainHandles.filter { it.handle !in localHandles && it.handle != myHandle }
    }
    val onChainGroupsNew = remember(state.onChainGroups, myGroupIds) {
        state.onChainGroups.filter { (it["group_id"] as? String) !in myGroupIds }
    }

    // Debounced on-chain search when user types in the search bar
    LaunchedEffect(state.searchQuery) {
        state.searchJob?.cancel()
        state.resetOnChainSearch()

        val trimmed = state.searchQuery.trim().removePrefix("@").lowercase()
        if (trimmed.isEmpty() || !Regex("^[a-z0-9_]+$").matches(trimmed)) return@LaunchedEffect

        state.updateSearchingOnChain(true)
        state.searchJob = scope.launch {
            delay(300) // debounce
            val handles = try { ChatService.contacts.searchOnChain(trimmed) } catch (_: Exception) { emptyList() }
            val chainGroups = try { ChatService.groups.searchGroups(trimmed) } catch (_: Exception) { emptyList() }
            state.setOnChainResults(handles.filter { it.handle != myHandle }, chainGroups)
            state.updateSearchingOnChain(false)
        }
    }

    // Chat folder tabs (saved across recompositions)

    // Filter conversations by tab + search query
    // Note: group conversations stored in ConversationEntity have convKey starting with "g_"
    // but isGroup is not set, so we filter by convKey instead
    val dms = remember(conversations) { conversations.filter { !it.convKey.startsWith("g_") } }
    val groupConvs = remember(conversations) { conversations.filter { it.convKey.startsWith("g_") } }

    val pinnedCount = remember(conversations, groups) {
        conversations.count { it.pinned && !it.archived && !it.convKey.startsWith("g_") } +
            groups.count { it.pinned && !it.archived }
    }

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = {
            state.onRefreshStart()
            ChatService.sbbs.pollNow()
            scope.launch {
                ChatService.groups.refreshMyGroups()
                delay(2000)
                state.onRefreshEnd()
            }
        },
        modifier = Modifier.fillMaxSize().background(C.bg),
    ) {
        // Saved across navigation to ChatScreen (scroll position preserved on back)
        val chatListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
        val listFadeEasing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
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
                                    contentDescription = stringResource(R.string.chat_search),
                                    tint = C.textSecondary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }

                        // Search/filter bar — always visible (fixed above scrollable list)
                        Spacer(Modifier.height(8.dp))
                        ChatsSearchBar(
                            searchQuery = state.searchQuery,
                            isSearchingOnChain = state.isSearchingOnChain,
                            onSearchQueryChange = { state.setSearch(it) },
                        )
                    }
                }

                // ── Tab bar (All / Unread / Groups / DMs / Archived) ──
                ChatsTabBar(
                    activeTab = state.activeTab,
                    onTabSelected = { state.setTab(it) },
                    conversations = conversations,
                    groups = groups,
                )

                // ── Conversation list ──
                val hasOnChain = onChainNew.isNotEmpty() || onChainGroupsNew.isNotEmpty() ||
                    (state.isSearchingOnChain && state.searchQuery.isNotBlank())

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    AnimatedContent(
                        targetState = state.activeTab,
                        modifier = Modifier.fillMaxSize(),
                        transitionSpec = {
                            if (initialState == targetState) {
                                EnterTransition.None togetherWith ExitTransition.None
                            } else {
                                fadeIn(
                                    animationSpec = tween(200, easing = listFadeEasing),
                                ) togetherWith fadeOut(
                                    animationSpec = tween(150, easing = FastOutSlowInEasing),
                                )
                            }
                        },
                        label = "chatsFolderList",
                    ) { tab ->
                        val tabConversations = remember(conversations, state.searchQuery, tab, dms) {
                            filterConversationsForTab(tab, conversations, dms, state.searchQuery)
                        }
                        val tabGroups = remember(groups, state.searchQuery, tab) {
                            filterGroupsForTab(tab, groups, state.searchQuery)
                        }
                        val tabUnifiedList = remember(tabConversations, tabGroups) {
                            buildUnifiedChatList(tabConversations, tabGroups)
                        }

                        if (tabUnifiedList.isEmpty() && !hasOnChain) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (state.searchQuery.isNotBlank()) {
                                        Text(stringResource(R.string.chat_no_results, state.searchQuery), color = C.textSecondary, fontSize = 15.sp)
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

                    PullToRefreshBox(
                        isRefreshing = refreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = chatListState,
                    ) {
                        items(tabUnifiedList.size, key = { i ->
                            val listItem = tabUnifiedList[i]
                            if (listItem.isGroup) "g_${listItem.group!!.groupId}" else "c_${listItem.conv!!.id}"
                        }) { i ->
                            val item = tabUnifiedList[i]
                            var showDeleteConfirmItem by remember { mutableStateOf(false) }
                            var showArchiveConfirmItem by remember { mutableStateOf(false) }
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    when (value) {
                                        SwipeToDismissBoxValue.EndToStart -> {
                                            // Swipe left → show delete confirmation
                                            showDeleteConfirmItem = true
                                            false // don't dismiss yet
                                        }
                                        SwipeToDismissBoxValue.StartToEnd -> {
                                            // Swipe right → show archive confirmation
                                            showArchiveConfirmItem = true
                                            false // don't dismiss yet
                                        }
                                        else -> false
                                    }
                                },
                                positionalThreshold = { it * 0.5f },
                            )
                            if (showDeleteConfirmItem) {
                                val name = if (item.isGroup) item.group!!.name
                                else (item.conv!!.displayName?.ifEmpty { null } ?: "@${item.conv!!.convKey.removePrefix("@")}")
                                val isGrp = item.isGroup
                                val deleteMsg = stringResource(if (isGrp) R.string.chats_leave_group_confirm else R.string.chats_delete_chat_confirm, name)
                                AlertDialog(
                                    onDismissRequest = { showDeleteConfirmItem = false },
                                    containerColor = C.card,
                                    title = { Text(stringResource(if (isGrp) R.string.chats_leave_group_title else R.string.chats_delete_chat_title), color = C.text, fontWeight = FontWeight.SemiBold) },
                                    text = {
                                        val annotated = buildAnnotatedString {
                                            val idx = deleteMsg.indexOf(name)
                                            if (idx >= 0) {
                                                val before = deleteMsg.substring(0, idx)
                                                val after = deleteMsg.substring(idx + name.length)
                                                if (before.isNotEmpty()) {
                                                    withStyle(SpanStyle(color = C.textSecondary)) { append(before) }
                                                }
                                                withStyle(SpanStyle(color = C.accent, fontWeight = FontWeight.Bold)) { append(name) }
                                                if (after.isNotEmpty()) {
                                                    withStyle(SpanStyle(color = C.textSecondary)) { append(after) }
                                                }
                                            } else {
                                                withStyle(SpanStyle(color = C.textSecondary)) { append(deleteMsg) }
                                            }
                                        }
                                        Text(text = annotated)
                                    },
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
                            if (showArchiveConfirmItem) {
                                val isGrp = item.isGroup
                                val name = if (item.isGroup) item.group!!.name else (item.conv!!.displayName?.ifEmpty { null } ?: "@${item.conv!!.convKey.removePrefix("@")}")
                                val isCurrentlyArchived = if (isGrp) item.group!!.archived else item.conv!!.archived
                                val archiveMsg = stringResource(if (isCurrentlyArchived) R.string.chats_unarchive_confirm else R.string.chats_archive_confirm, name)
                                AlertDialog(
                                    onDismissRequest = { showArchiveConfirmItem = false },
                                    containerColor = C.card,
                                    title = { Text(stringResource(if (isCurrentlyArchived) R.string.chats_unarchive else R.string.chats_archive), color = C.text, fontWeight = FontWeight.SemiBold) },
                                    text = {
                                        val annotated = buildAnnotatedString {
                                            val idx = archiveMsg.indexOf(name)
                                            if (idx >= 0) {
                                                val before = archiveMsg.substring(0, idx)
                                                val after = archiveMsg.substring(idx + name.length)
                                                if (before.isNotEmpty()) {
                                                    withStyle(SpanStyle(color = C.textSecondary)) { append(before) }
                                                }
                                                withStyle(SpanStyle(color = C.accent, fontWeight = FontWeight.Bold)) { append(name) }
                                                if (after.isNotEmpty()) {
                                                    withStyle(SpanStyle(color = C.textSecondary)) { append(after) }
                                                }
                                            } else {
                                                withStyle(SpanStyle(color = C.textSecondary)) { append(archiveMsg) }
                                            }
                                        }
                                        Text(text = annotated)
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            showArchiveConfirmItem = false
                                            scope.launch {
                                                if (isGrp) {
                                                    ChatService.db?.groupDao()?.setArchived(item.group!!.groupId, !item.group!!.archived)
                                                } else {
                                                    ChatService.db?.conversationDao()?.setArchived(item.conv!!.id, !item.conv!!.archived)
                                                }
                                            }
                                        }) { Text(stringResource(if (isCurrentlyArchived) R.string.chats_unarchive else R.string.chats_archive), color = C.accent, fontWeight = FontWeight.Bold) }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showArchiveConfirmItem = false }) {
                                            Text(stringResource(R.string.general_cancel), color = C.textSecondary)
                                        }
                                    },
                                )
                            }
                            SwipeToDismissBox(
                                state = dismissState,
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
                                    GroupRow(group = item.group!!, onClick = { onOpenGroup(item.group.groupId) }, onLongPress = { state.menuTargetGroup = item.group }, typingHandles = gTypingHandles, draftText = gDraft)
                                } else {
                                    val peerTyping = typingVer >= 0 && ChatService.isTyping(item.conv!!.convKey)
                                    ConversationRow(conv = item.conv!!, onClick = { onOpenChat(item.conv!!.convKey.removePrefix("@")) }, onLongPress = { state.menuTarget = item.conv }, isTyping = peerTyping)
                                }
                                }
                            }
                        }

                    // ── On-chain search results (Telegram-style global search) ──

                    // Show spinner while searching
                    if (state.isSearchingOnChain && state.searchQuery.isNotBlank()) {
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
                                            state.setSearch("")
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
                                            context.getString(R.string.chat_join_group_subtitle, memberCount, creator),
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
                                                    state.setSearch("")
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
                    } // AnimatedContent folder list
                } // Box list area
            }

            // FABs - New Chat + Create Group (hide on scroll down, show on scroll up)
            // Hide FABs while searching — they block the join button on search results
            if (state.searchQuery.isBlank()) {
                ChatsFabs(
                    chatListState = chatListState,
                    onNewChat = onNewChat,
                    onCreateGroup = onCreateGroup,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
    }

    // Group context menu
    if (state.menuTargetGroup != null) {
        ChatsGroupContextMenu(
            target = state.menuTargetGroup!!,
            pinnedCount = pinnedCount,
            onDismiss = { state.menuTargetGroup = null },
            onReorderPinned = { showReorderPinned = true },
        )
    }

    // Conversation context menu
    if (state.menuTarget != null) {
        ChatsConversationContextMenu(
            target = state.menuTarget!!,
            pinnedCount = pinnedCount,
            onDismiss = { state.menuTarget = null },
            onReorderPinned = { showReorderPinned = true },
        )
    }

    if (showReorderPinned) {
        androidx.activity.compose.BackHandler { showReorderPinned = false }
        val pinnedItems = remember(conversations, groups) {
            ChatPinOrder.buildPinnedListFromState(conversations, groups)
        }
        PinnedChatsReorderOverlay(
            initialItems = pinnedItems,
            onDismiss = { showReorderPinned = false },
            onSave = { ordered ->
                scope.launch {
                    val db = ChatService.db ?: return@launch
                    ChatPinOrder.applyReorder(db, ordered)
                }
            },
        )
    }
}

