package com.privimemobile.ui.chat.chats

import androidx.compose.ui.graphics.Color
import com.privimemobile.chat.ChatPinOrder
import com.privimemobile.chat.db.entities.ConversationEntity
import com.privimemobile.chat.db.entities.GroupEntity
import com.privimemobile.ui.theme.C
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

/** Deterministic avatar colors from handle/name hash. */
internal val avatarColors = listOf(
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

internal fun avatarColor(key: String): Color {
    val hash = abs(key.lowercase().hashCode())
    return avatarColors[hash % avatarColors.size]
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

internal fun formatTime(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val date = Date(timestamp * 1000)
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { time = date }
    return if (now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR) &&
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    ) timeFormat.format(date) else dateFormat.format(date)
}

/** Unified list item merging DMs and groups for sorted display. */
internal data class ChatListItem(
    val isGroup: Boolean,
    val sortTs: Long,
    val pinned: Boolean,
    val pinOrder: Int,
    val conv: ConversationEntity? = null,
    val group: GroupEntity? = null,
)

/** Merge DMs and groups into a single list sorted by pinned order then timestamp. */
internal fun buildUnifiedChatList(
    conversations: List<ConversationEntity>,
    groups: List<GroupEntity>,
): List<ChatListItem> {
    val items = mutableListOf<ChatListItem>()
    for (c in conversations) {
        if (c.convKey.startsWith("g_")) continue
        items.add(ChatListItem(false, c.lastMessageTs, c.pinned, c.pinOrder, conv = c))
    }
    for (g in groups) {
        items.add(ChatListItem(true, g.lastMessageTs, g.pinned, g.pinOrder, group = g))
    }
    return items.sortedWith { a, b ->
        ChatPinOrder.compareChatListItems(
            a.pinned, a.pinOrder, a.sortTs,
            b.pinned, b.pinOrder, b.sortTs,
        )
    }
}

/** Filter conversations for a given tab index and search query. */
internal fun filterConversationsForTab(
    tab: Int,
    conversations: List<ConversationEntity>,
    dms: List<ConversationEntity>,
    searchQuery: String,
): List<ConversationEntity> {
    val tabFiltered = when (tab) {
        0 -> conversations.filter { !it.archived }
        1 -> conversations.filter { !it.archived && it.unreadCount > 0 }
        2 -> emptyList()
        3 -> dms.filter { !it.archived }
        4 -> conversations.filter { it.archived }
        else -> conversations.filter { !it.archived }
    }
    if (searchQuery.isBlank()) return tabFiltered
    val q = searchQuery.trim().lowercase()
    return tabFiltered.filter { conv ->
        (conv.displayName ?: "").lowercase().contains(q) ||
            (conv.handle ?: "").lowercase().contains(q)
    }
}

/** Filter groups for a given tab index and search query. */
internal fun filterGroupsForTab(
    tab: Int,
    groups: List<GroupEntity>,
    searchQuery: String,
): List<GroupEntity> {
    val tabFiltered = when (tab) {
        0 -> groups.filter { !it.archived }
        1 -> groups.filter { !it.archived && it.unreadCount > 0 }
        2 -> groups.filter { !it.archived }
        3 -> emptyList()
        4 -> groups.filter { it.archived }
        else -> groups.filter { !it.archived }
    }
    if (searchQuery.isBlank()) return tabFiltered
    val q = searchQuery.trim().lowercase()
    return tabFiltered.filter { it.name.lowercase().contains(q) }
}