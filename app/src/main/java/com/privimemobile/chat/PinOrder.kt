package com.privimemobile.chat

import androidx.room.withTransaction
import com.privimemobile.chat.db.ChatDatabase
import com.privimemobile.chat.db.entities.ConversationEntity
import com.privimemobile.chat.db.entities.GroupEntity

/** Unified pinned chat entry (DM or group) for list sort and reorder UI. */
data class PinnedChatItem(
    val isGroup: Boolean,
    val pinOrder: Int,
    val conv: ConversationEntity? = null,
    val group: GroupEntity? = null,
) {
    val sortKey: String
        get() = if (isGroup) "g_${group!!.groupId}" else "c_${conv!!.id}"
}

object ChatPinOrder {

    suspend fun countPinned(db: ChatDatabase): Int {
        val convs = db.conversationDao().countPinnedActive()
        val groups = db.groupDao().countPinnedActive()
        return convs + groups
    }

    suspend fun loadPinnedItems(db: ChatDatabase): List<PinnedChatItem> {
        val items = mutableListOf<PinnedChatItem>()
        for (c in db.conversationDao().getPinnedActive()) {
            if (c.convKey.startsWith("g_")) continue
            items.add(PinnedChatItem(isGroup = false, pinOrder = c.pinOrder, conv = c))
        }
        for (g in db.groupDao().getPinnedActive()) {
            items.add(PinnedChatItem(isGroup = true, pinOrder = g.pinOrder, group = g))
        }
        return items.sortedWith(
            compareBy<PinnedChatItem> { if (it.pinOrder > 0) it.pinOrder else Int.MAX_VALUE }
                .thenByDescending {
                    if (it.isGroup) it.group!!.lastMessageTs else it.conv!!.lastMessageTs
                },
        )
    }

    suspend fun setConversationPinned(db: ChatDatabase, convId: Long, pinned: Boolean) {
        db.withTransaction {
            if (pinned) {
                val order = nextPinOrder(db)
                db.conversationDao().updatePinState(convId, pinned = true, pinOrder = order)
            } else {
                db.conversationDao().updatePinState(convId, pinned = false, pinOrder = 0)
                compactPinOrders(db)
            }
        }
    }

    suspend fun setGroupPinned(db: ChatDatabase, groupId: String, pinned: Boolean) {
        db.withTransaction {
            if (pinned) {
                val order = nextPinOrder(db)
                db.groupDao().updatePinState(groupId, pinned = true, pinOrder = order)
            } else {
                db.groupDao().updatePinState(groupId, pinned = false, pinOrder = 0)
                compactPinOrders(db)
            }
        }
    }

    /** Persist manual order from reorder UI (1-based positions). */
    suspend fun applyReorder(db: ChatDatabase, ordered: List<PinnedChatItem>) {
        db.withTransaction {
            ordered.forEachIndexed { index, item ->
                val order = index + 1
                if (item.isGroup) {
                    db.groupDao().setPinOrder(item.group!!.groupId, order)
                } else {
                    db.conversationDao().setPinOrder(item.conv!!.id, order)
                }
            }
        }
    }

    private suspend fun nextPinOrder(db: ChatDatabase): Int {
        val maxConv = db.conversationDao().maxPinOrder()
        val maxGroup = db.groupDao().maxPinOrder()
        return maxOf(maxConv, maxGroup) + 1
    }

    /** Close gaps after unpin so orders stay 1..N. */
    private suspend fun compactPinOrders(db: ChatDatabase) {
        val items = loadPinnedItems(db)
        applyReorder(db, items)
    }

    fun buildPinnedListFromState(
        conversations: List<ConversationEntity>,
        groups: List<GroupEntity>,
    ): List<PinnedChatItem> {
        val items = mutableListOf<PinnedChatItem>()
        for (c in conversations) {
            if (c.pinned && !c.archived && !c.convKey.startsWith("g_")) {
                items.add(PinnedChatItem(isGroup = false, pinOrder = c.pinOrder, conv = c))
            }
        }
        for (g in groups) {
            if (g.pinned && !g.archived) {
                items.add(PinnedChatItem(isGroup = true, pinOrder = g.pinOrder, group = g))
            }
        }
        return items.sortedWith(
            compareBy<PinnedChatItem> { if (it.pinOrder > 0) it.pinOrder else Int.MAX_VALUE }
                .thenByDescending {
                    if (it.isGroup) it.group!!.lastMessageTs else it.conv!!.lastMessageTs
                },
        )
    }

    fun compareChatListItems(
        aPinned: Boolean,
        aPinOrder: Int,
        aSortTs: Long,
        bPinned: Boolean,
        bPinOrder: Int,
        bSortTs: Long,
    ): Int {
        if (aPinned != bPinned) return if (aPinned) -1 else 1
        if (aPinned && bPinned) {
            val aOrder = if (aPinOrder > 0) aPinOrder else Int.MAX_VALUE
            val bOrder = if (bPinOrder > 0) bPinOrder else Int.MAX_VALUE
            val orderCmp = aOrder.compareTo(bOrder)
            if (orderCmp != 0) return orderCmp
        }
        return bSortTs.compareTo(aSortTs)
    }
}
