package com.privimemobile.ui.chat.chats

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import android.content.Context
import com.privimemobile.chat.ChatService
import com.privimemobile.chat.db.entities.ContactEntity
import com.privimemobile.chat.db.entities.ConversationEntity
import com.privimemobile.chat.db.entities.GroupEntity
import kotlinx.coroutines.CoroutineScope

/**
 * Tab folder body: filtered list, swipe rows, on-chain search section.
 * Pull-to-refresh is handled by the parent [com.privimemobile.ui.chat.ChatsScreen].
 */
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
