package com.privimemobile.ui.chat.chats

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import android.content.Context
import com.privimemobile.chat.ChatService
import com.privimemobile.chat.db.entities.ContactEntity
import com.privimemobile.chat.db.entities.ConversationEntity
import com.privimemobile.chat.db.entities.GroupEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tab folder body: filtered list, inner pull-to-refresh, swipe rows, on-chain search section.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatsTabFolderContent(
    tab: Int,
    conversations: List<ConversationEntity>,
    groups: List<GroupEntity>,
    dms: List<ConversationEntity>,
    searchQuery: String,
    hasOnChain: Boolean,
    chatListState: LazyListState,
    state: ChatsListState,
    scope: CoroutineScope,
    context: Context,
    onChainHandles: List<ContactEntity>,
    onChainGroups: List<Map<String, Any?>>,
    onOpenChat: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
) {
    val tabConversations = remember(conversations, searchQuery, tab, dms) {
        filterConversationsForTab(tab, conversations, dms, searchQuery)
    }
    val tabGroups = remember(groups, searchQuery, tab) {
        filterGroupsForTab(tab, groups, searchQuery)
    }
    val tabUnifiedList = remember(tabConversations, tabGroups) {
        buildUnifiedChatList(tabConversations, tabGroups)
    }

    if (tabUnifiedList.isEmpty() && !hasOnChain) {
        ChatsEmptyState(searchQuery = searchQuery)
    } else {
        val typingVer by ChatService.typingVersion.collectAsState()

        var refreshing by remember { mutableStateOf(false) }
        val onRefresh: () -> Unit = {
            refreshing = true
            scope.launch {
                val contacts = ChatService.db?.contactDao()?.getAll() ?: emptyList()
                for (c in contacts) {
                    try {
                        val resolved = ChatService.contacts.resolveHandle(c.handle)
                        if (resolved?.displayName != null && resolved.displayName != c.displayName) {
                            ChatService.db?.contactDao()?.updateDisplayName(c.handle, resolved.displayName)
                            ChatService.db?.conversationDao()?.updateDisplayName("@${c.handle}", resolved.displayName)
                        }
                    } catch (_: Exception) {
                    }
                }
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
                    ChatListSwipeRow(
                        item = item,
                        typingVer = typingVer,
                        conversations = conversations,
                        onOpenChat = onOpenChat,
                        onOpenGroup = onOpenGroup,
                        onLongPressDm = { state.menuTarget = it },
                        onLongPressGroup = { state.menuTargetGroup = it },
                        onDeleteConfirm = { performChatsSwipeDelete(context, scope, it) },
                        onArchiveConfirm = { performChatsSwipeArchive(scope, it) },
                    )
                }

                onChainSearchResults(
                    isSearching = state.isSearchingOnChain,
                    searchQuery = searchQuery,
                    onChainHandles = onChainHandles,
                    onChainGroups = onChainGroups,
                    scope = scope,
                    onOpenChat = onOpenChat,
                    onOpenGroup = onOpenGroup,
                    onSearchCleared = { state.setSearch("") },
                )
            }
        }
    }
}
