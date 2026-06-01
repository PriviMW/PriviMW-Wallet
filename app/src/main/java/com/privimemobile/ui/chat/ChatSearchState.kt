package com.privimemobile.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.privimemobile.chat.db.entities.MessageEntity
import kotlinx.coroutines.Job

/**
 * In-chat search overlay: query, debounced results, tap-to-scroll highlight timestamp.
 * DB search and [LazyListState] scroll stay in [ChatScreen] orchestrator.
 */
class ChatSearchState {
    var showSearch by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<MessageEntity>>(emptyList())
    var searchHighlightTs by mutableStateOf<Long?>(null)
    var searchJob by mutableStateOf<Job?>(null)

    fun cancelSearchJob() {
        searchJob?.cancel()
        searchJob = null
    }

    fun clearResultsAndHighlight() {
        searchResults = emptyList()
        searchHighlightTs = null
    }

    fun clearQueryAndResults() {
        searchQuery = ""
        clearResultsAndHighlight()
        cancelSearchJob()
    }

    fun close() {
        showSearch = false
        clearQueryAndResults()
    }

    fun toggle() {
        showSearch = !showSearch
        if (!showSearch) clearQueryAndResults()
    }

    fun onQueryChanged(query: String) {
        searchQuery = query
        cancelSearchJob()
        if (query.isBlank()) clearResultsAndHighlight()
    }

    fun closeAfterResultPick() {
        showSearch = false
        searchQuery = ""
        searchResults = emptyList()
        cancelSearchJob()
        // searchHighlightTs kept until orchestrator clears after scroll animation
    }
}
