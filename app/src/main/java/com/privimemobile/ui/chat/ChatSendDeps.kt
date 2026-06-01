package com.privimemobile.ui.chat

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope

/**
 * Dependencies for [ChatSendPipeline] — assembled by [ChatScreen] orchestrator per send.
 */
class ChatSendDeps(
    val context: Context,
    val scope: CoroutineScope,
    val convId: Long,
    val convKey: String,
    val handle: String,
    val isGroupMode: Boolean,
    val groupId: String?,
    val groupName: String,
    val resolvedSbbsAddress: String?,
    val resolvedWalletId: String?,
    val input: ChatInputState,
    val voice: ChatVoiceState,
    val files: ChatMessageListState,
    val media: ChatImagePreviewState,
    val getLastSendTime: () -> Long,
    val setLastSendTime: (Long) -> Unit,
    val clearInitialUnreadDivider: () -> Unit,
    val clearDraft: () -> Unit,
    val onUploadingChange: (Boolean) -> Unit,
)
