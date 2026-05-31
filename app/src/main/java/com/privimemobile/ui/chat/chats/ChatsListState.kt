package com.privimemobile.ui.chat.chats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import com.privimemobile.chat.db.entities.ContactEntity
import com.privimemobile.chat.db.entities.ConversationEntity
import kotlinx.coroutines.Job

/**
 * Holds UI state for the Chats screen list — tabs, search, on-chain results,
 * context menu targets, and pull-to-refresh.
 *
 * No ChatService calls inside this holder — the orchestrator (ChatsScreen)
 * passes results into the holder via setters.
 */
class ChatsListState(activeTab: Int = 0) {
    // Tabs
    var activeTab by mutableIntStateOf(activeTab) // 0=All, 1=Unread, 2=Groups, 3=DMs, 4=Archived
        private set

    fun setTab(tab: Int) { activeTab = tab }

    // Local search
    var searchQuery by mutableStateOf("")
        private set

    fun setSearch(query: String) { searchQuery = query }

    // On-chain search
    var onChainHandles by mutableStateOf<List<ContactEntity>>(emptyList())
        private set
    var onChainGroups by mutableStateOf<List<Map<String, Any?>>>(emptyList())
        private set
    var isSearchingOnChain by mutableStateOf(false)
        private set
    var searchJob by mutableStateOf<Job?>(null)
        internal set // orchestrator manages coroutine lifecycle

    /** Bumped on each valid query change so stale debounced jobs cannot clear the spinner. */
    private var onChainSearchEpoch by mutableIntStateOf(0)

    fun setOnChainResults(handles: List<ContactEntity>, groups: List<Map<String, Any?>>) {
        onChainHandles = handles
        onChainGroups = groups
    }

    fun updateSearchingOnChain(searching: Boolean) {
        isSearchingOnChain = searching
    }

    /** Clear on-chain results only — keep [isSearchingOnChain] unchanged. */
    fun clearOnChainResults() {
        onChainHandles = emptyList()
        onChainGroups = emptyList()
    }

    /** Full reset: clear results AND set isSearchingOnChain = false.
     *  Use only when the query becomes empty or invalid. */
    fun resetOnChainSearch() {
        onChainHandles = emptyList()
        onChainGroups = emptyList()
        isSearchingOnChain = false
    }

    /** Start a new debounced on-chain search; returns epoch for [finishOnChainSearch]. */
    fun beginOnChainSearch(): Int {
        clearOnChainResults()
        isSearchingOnChain = true
        return ++onChainSearchEpoch
    }

    /** Apply results and hide spinner only if this job is still the latest search. */
    fun finishOnChainSearch(epoch: Int, handles: List<ContactEntity>, groups: List<Map<String, Any?>>) {
        if (epoch != onChainSearchEpoch) return
        onChainHandles = handles
        onChainGroups = groups
        isSearchingOnChain = false
    }

    fun isOnChainSearchEpoch(epoch: Int): Boolean = epoch == onChainSearchEpoch

    // Context menus
    var menuTarget by mutableStateOf<ConversationEntity?>(null)
        internal set // set directly from composables
    var menuTargetGroup by mutableStateOf<com.privimemobile.chat.db.entities.GroupEntity?>(null)
        internal set

    // Pull-to-refresh
    var refreshing by mutableStateOf(false)
        private set

    fun onRefreshStart() { refreshing = true }
    fun onRefreshEnd() { refreshing = false }

    companion object {
        /**
         * Saver that persists only the tab selection across process death.
         * Transient state (search, on-chain results, menus) resets on restore — same as original behavior.
         */
        val Saver: Saver<ChatsListState, *> = listSaver(
            save = { listOf(it.activeTab) },
            restore = { ChatsListState(activeTab = it[0] as Int) },
        )
    }
}