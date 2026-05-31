package com.privimemobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import com.privimemobile.chat.ChatPinOrder
import com.privimemobile.chat.ChatService
import com.privimemobile.chat.db.entities.ConversationEntity
import com.privimemobile.chat.db.entities.PendingTxEntity
import com.privimemobile.ui.chat.chats.ChatsFabs
import com.privimemobile.ui.chat.chats.ChatsGroupContextMenu
import com.privimemobile.ui.chat.chats.ChatsConversationContextMenu
import com.privimemobile.ui.chat.chats.ChatsListState
import com.privimemobile.ui.chat.chats.ChatsLoadingPlaceholder
import com.privimemobile.ui.chat.chats.ChatsPendingRegistrationScreen
import com.privimemobile.ui.chat.chats.ChatsSearchBar
import com.privimemobile.ui.chat.chats.ChatsTabBar
import com.privimemobile.ui.chat.chats.ChatsTabFolderContent
import com.privimemobile.ui.chat.chats.ChatsTopBar
import com.privimemobile.ui.chat.chats.runChatsListRefresh
import com.privimemobile.ui.chat.chats.NotRegisteredLanding
import com.privimemobile.ui.chat.chats.ReRegisterLanding
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val context = LocalContext.current
    val isInitialized by ChatService.initialized.collectAsState()
    if (!isInitialized) {
        ChatsLoadingPlaceholder()
        return
    }

    val chatState by ChatService.observeState().collectAsState(initial = null)
    val isRegistered = chatState?.myHandle != null
    val sbbsNeedsUpdate by ChatService.identity.sbbsNeedsUpdate.collectAsState()

    val allPendingTxs by ChatService.db?.pendingTxDao()?.observePending()
        ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }

    if (!isRegistered) {
        if (chatState == null) {
            ChatsLoadingPlaceholder()
            return
        }
        val pendingRegTx = allPendingTxs.any {
            it.action == PendingTxEntity.ACTION_REGISTER_HANDLE
        }
        if (pendingRegTx) {
            ChatsPendingRegistrationScreen()
        } else {
            NotRegisteredLanding(onRegister)
        }
        return
    }

    val pendingUpdateTx = allPendingTxs.any {
        it.action == PendingTxEntity.ACTION_UPDATE_PROFILE
    }
    if (sbbsNeedsUpdate || pendingUpdateTx) {
        ReRegisterLanding(chatState!!)
        return
    }

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

    val onChainNew = remember(state.onChainHandles, conversations, myHandle) {
        val localHandles = conversations.mapNotNull { it.handle }.toSet()
        state.onChainHandles.filter { it.handle !in localHandles && it.handle != myHandle }
    }
    val onChainGroupsNew = remember(state.onChainGroups, myGroupIds) {
        state.onChainGroups.filter { (it["group_id"] as? String) !in myGroupIds }
    }

    LaunchedEffect(state.searchQuery) {
        state.searchJob?.cancel()

        val trimmed = state.searchQuery.trim().removePrefix("@").lowercase()
        if (trimmed.isEmpty() || !Regex("^[a-z0-9_]+$").matches(trimmed)) {
            state.resetOnChainSearch()
            return@LaunchedEffect
        }

        val epoch = state.beginOnChainSearch()
        val querySnapshot = trimmed
        state.searchJob = scope.launch {
            delay(300)
            if (!state.isOnChainSearchEpoch(epoch)) return@launch
            val handles = try { ChatService.contacts.searchOnChain(querySnapshot) } catch (_: Exception) { emptyList() }
            val chainGroups = try { ChatService.groups.searchGroups(querySnapshot) } catch (_: Exception) { emptyList() }
            state.finishOnChainSearch(
                epoch,
                handles.filter { it.handle != myHandle },
                chainGroups,
            )
        }
    }

    val dms = remember(conversations) { conversations.filter { !it.convKey.startsWith("g_") } }

    val pinnedCount = remember(conversations, groups) {
        conversations.count { it.pinned && !it.archived && !it.convKey.startsWith("g_") } +
            groups.count { it.pinned && !it.archived }
    }

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = {
            state.onRefreshStart()
            scope.launch {
                runChatsListRefresh()
                state.onRefreshEnd()
            }
        },
        modifier = Modifier.fillMaxSize().background(C.bg),
    ) {
        val chatListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
        val listFadeEasing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                ChatsTopBar(onSearch = onSearch) {
                    ChatsSearchBar(
                        searchQuery = state.searchQuery,
                        isSearchingOnChain = state.isSearchingOnChain,
                        onSearchQueryChange = { state.setSearch(it) },
                    )
                }

                ChatsTabBar(
                    activeTab = state.activeTab,
                    onTabSelected = { state.setTab(it) },
                    conversations = conversations,
                    groups = groups,
                )

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
                                fadeIn(animationSpec = tween(200, easing = listFadeEasing)) togetherWith
                                    fadeOut(animationSpec = tween(150, easing = FastOutSlowInEasing))
                            }
                        },
                        label = "chatsFolderList",
                    ) { tab ->
                        ChatsTabFolderContent(
                            tab = tab,
                            conversations = conversations,
                            groups = groups,
                            dms = dms,
                            searchQuery = state.searchQuery,
                            hasOnChain = hasOnChain,
                            chatListState = chatListState,
                            state = state,
                            scope = scope,
                            context = context,
                            onChainHandles = onChainNew,
                            onChainGroups = onChainGroupsNew,
                            onOpenChat = onOpenChat,
                            onOpenGroup = onOpenGroup,
                        )
                    }
                }
            }

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

    if (state.menuTargetGroup != null) {
        ChatsGroupContextMenu(
            target = state.menuTargetGroup!!,
            pinnedCount = pinnedCount,
            onDismiss = { state.menuTargetGroup = null },
            onReorderPinned = { showReorderPinned = true },
        )
    }

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
