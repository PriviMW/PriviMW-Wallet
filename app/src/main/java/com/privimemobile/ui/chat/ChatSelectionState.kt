package com.privimemobile.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Multi-select mode: selected message ids and bulk-delete confirmation dialog state.
 * DB / network delete work stays in [ChatScreen] orchestrator.
 */
class ChatSelectionState {
    var selectionMode by mutableStateOf(false)
    val selectedIds = mutableStateListOf<String>()
    var showDeleteConfirmDialog by mutableStateOf(false)
    var pendingDeleteIds by mutableStateOf<List<String>>(emptyList())

    fun exitSelection() {
        selectionMode = false
        selectedIds.clear()
    }

    fun toggleSelected(id: String) {
        if (id in selectedIds) selectedIds.remove(id) else selectedIds.add(id)
    }

    fun enterSelectionWith(id: String) {
        selectionMode = true
        selectedIds.clear()
        selectedIds.add(id)
    }

    fun openBulkDeleteConfirm() {
        pendingDeleteIds = selectedIds.toList()
        showDeleteConfirmDialog = true
    }

    fun dismissDeleteConfirm() {
        showDeleteConfirmDialog = false
        pendingDeleteIds = emptyList()
    }

    fun clearAfterBulkAction() {
        dismissDeleteConfirm()
        exitSelection()
    }
}
