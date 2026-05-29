package com.privimemobile.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.privimemobile.chat.db.entities.MessageEntity

/**
 * Immediate poll_data UI patches when Room Flow lags after UPDATE.
 * Kept out of [ChatScreen] to avoid release VerifyError on oversized composables.
 */
class ChatPollUiState(
    private val overrides: SnapshotStateMap<Long, String>,
    val revision: Int,
    private val bumpRevision: () -> Unit,
) {
    fun resolve(entityId: Long, entityPollData: String?): String? =
        overrides[entityId] ?: entityPollData

    fun patch(messageId: Long, pollData: String) {
        overrides[messageId] = pollData
        bumpRevision()
    }
}

@Composable
fun rememberChatPollUiState(roomMessages: List<MessageEntity>): ChatPollUiState {
    val overrides = remember { mutableStateMapOf<Long, String>() }
    var revision by remember { mutableIntStateOf(0) }
    LaunchedEffect(roomMessages) {
        var pruned = false
        roomMessages.forEach { entity ->
            val override = overrides[entity.id]
            if (override != null && entity.pollData == override) {
                overrides.remove(entity.id)
                pruned = true
            }
        }
        if (pruned) revision++
    }
    return ChatPollUiState(
        overrides = overrides,
        revision = revision,
        bumpRevision = { revision++ },
    )
}
