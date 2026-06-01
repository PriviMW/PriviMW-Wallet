package com.privimemobile.ui.chat.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.R
import com.privimemobile.chat.db.entities.GroupEntity
import com.privimemobile.ui.chat.ChatContextMenuState
import com.privimemobile.ui.chat.ChatEmojiStickerState
import com.privimemobile.ui.chat.ChatForwardState
import com.privimemobile.ui.chat.ChatInputState
import com.privimemobile.ui.chat.ChatMessageListState
import com.privimemobile.ui.chat.ChatPollUiState
import com.privimemobile.ui.chat.ChatSelectionState
import com.privimemobile.ui.chat.media.saveFileToDownloads
import com.privimemobile.ui.chat.menu.MenuItemRow
import com.privimemobile.ui.theme.C
import com.privimemobile.protocol.Helpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatContextMenuSheet(
    menu: ChatContextMenuState,
    input: ChatInputState,
    forward: ChatForwardState,
    selection: ChatSelectionState,
    emoji: ChatEmojiStickerState,
    files: ChatMessageListState,
    pollUi: ChatPollUiState,
    context: Context,
    scope: CoroutineScope,
    convId: Long,
    handle: String,
    isGroupMode: Boolean,
    groupId: String?,
    group: GroupEntity?,
    resolvedSbbsAddress: String?,
    myHandle: String?,
    groupMemberNames: Map<String, String>,
    onRefreshConversationPreview: suspend (Long) -> Unit,
) {
    if (menu.contextMenuMsg != null) {
        val targetMsg = menu.contextMenuMsg!!
        val menuMsgId = targetMsg.id.toLong()
        val menuPollData = pollUi.resolve(menuMsgId, targetMsg.pollData)
        ModalBottomSheet(
            onDismissRequest = { menu.contextMenuMsg = null },
            containerColor = C.card,
            dragHandle = {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp).height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(C.textMuted.copy(alpha = 0.4f)),
                    )
                }
            },
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                    // Quick reaction emojis — first row + expandable grid
                    val allEmojis = listOf(
                        "\uD83D\uDC4D", "\uD83D\uDC4E", "\u2764\uFE0F", "\uD83D\uDD25", "\uD83E\uDD70", "\uD83D\uDC4F", "\uD83D\uDE02",
                        "\uD83E\uDD14", "\uD83E\uDD2F", "\uD83D\uDE22", "\uD83C\uDF89", "\uD83D\uDE31", "\uD83D\uDE4F", "\uD83D\uDC40",
                        "\uD83D\uDE0D", "\uD83D\uDE0E", "\uD83E\uDD23", "\u26A1", "\uD83C\uDFC6", "\uD83D\uDC94", "\uD83E\uDD28",
                        "\uD83D\uDE10", "\uD83D\uDE34", "\uD83D\uDE2D", "\uD83E\uDD13", "\uD83D\uDC7B", "\uD83D\uDE08", "\uD83E\uDD21",
                        "\uD83D\uDC4C", "\uD83E\uDD1D", "\uD83E\uDD17", "\uD83E\uDEE1", "\uD83D\uDC8B", "\uD83D\uDCA5", "\uD83D\uDCAF",
                    )
                    val quickEmojis = allEmojis.take(7)
                    var emojiExpanded by remember { mutableStateOf(false) }
                    // Staggered bounce-in for quick emojis
                    val emojiAppeared = remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { emojiAppeared.value = true }

                    fun sendReaction(emoji: String) {
                        scope.launch {
                            val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
                            if (state?.myHandle != null) {
                                val ts = System.currentTimeMillis() / 1000
                                val insertId = com.privimemobile.chat.ChatService.db!!.reactionDao().insert(
                                    com.privimemobile.chat.db.entities.ReactionEntity(
                                        messageTs = targetMsg.timestamp,
                                        senderHandle = state.myHandle!!,
                                        emoji = emoji,
                                        timestamp = ts,
                                    )
                                )
                                if (insertId == -1L) {
                                    com.privimemobile.chat.ChatService.db!!.reactionDao().reactivate(
                                        targetMsg.timestamp, state.myHandle!!, emoji, ts
                                    )
                                }
                                val reactPayload = mapOf(
                                    "v" to 1, "t" to "react",
                                    "ts" to System.currentTimeMillis() / 1000,
                                    "from" to state.myHandle!!, "to" to (if (isGroupMode) groupId!! else handle),
                                    "msg_ts" to targetMsg.timestamp, "emoji" to emoji,
                                )
                                if (isGroupMode && groupId != null) {
                                    com.privimemobile.chat.ChatService.groups.sendGroupPayload(groupId, reactPayload)
                                } else {
                                    val walletId = resolvedSbbsAddress
                                    if (!walletId.isNullOrEmpty()) {
                                        com.privimemobile.chat.ChatService.sbbs.sendWithRetry(walletId, reactPayload)
                                    }
                                }
                            }
                        }
                        menu.contextMenuMsg = null
                    }

                    // First row: 7 quick emojis + expand button
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = if (emojiExpanded) 4.dp else 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        quickEmojis.forEachIndexed { eidx, emoji ->
                            val emojiScale by animateFloatAsState(
                                targetValue = if (emojiAppeared.value) 1f else 0f,
                                animationSpec = spring(
                                    dampingRatio = 0.5f, stiffness = 600f,
                                    visibilityThreshold = 0.01f,
                                ),
                                label = "emojiScale$eidx",
                            )
                            Text(
                                emoji, fontSize = 28.sp,
                                modifier = Modifier
                                    .graphicsLayer { scaleX = emojiScale; scaleY = emojiScale }
                                    .clickable { sendReaction(emoji) }
                                    .padding(4.dp),
                            )
                        }
                        // Expand/collapse button
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(C.bg.copy(alpha = 0.5f))
                                .clickable { emojiExpanded = !emojiExpanded },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (emojiExpanded) "\u25B2" else "\u25BC",
                                color = C.textSecondary, fontSize = 14.sp,
                            )
                        }
                    }

                    // Expanded emoji grid
                    if (emojiExpanded) {
                        val gridEmojis = allEmojis.drop(7)
                        val columns = 7
                        gridEmojis.chunked(columns).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                row.forEach { emoji ->
                                    Text(
                                        emoji, fontSize = 28.sp,
                                        modifier = Modifier.clickable { sendReaction(emoji) }.padding(4.dp),
                                    )
                                }
                                // Pad incomplete rows
                                repeat(columns - row.size) {
                                    Spacer(Modifier.size(36.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    HorizontalDivider(color = C.border)

                    // Menu items with touch highlight
                    // View Pack for sticker messages
                    if (targetMsg.type == "sticker" && targetMsg.stickerPackId != null) {
                        MenuItemRow(context.getString(R.string.chat_view_pack) + " \u2014 ${targetMsg.stickerPackName ?: stringResource(R.string.chat_sticker_pack_label)}") {
                            emoji.viewPackId = targetMsg.stickerPackId
                            menu.contextMenuMsg = null
                        }
                    }

                    if (targetMsg.text.isNotEmpty()) {
                        MenuItemRow(stringResource(R.string.chat_copy)) {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.chat_clip_label), targetMsg.text))
                            Toast.makeText(context, R.string.general_copied, Toast.LENGTH_SHORT).show()
                            menu.contextMenuMsg = null
                        }
                    }

                    if (targetMsg.file != null) {
                        val targetPath = files.filePaths[targetMsg.file.cid]
                        if (targetPath != null) {
                            MenuItemRow(stringResource(R.string.chat_save_to_downloads)) {
                                saveFileToDownloads(context, targetPath, targetMsg.file.name, targetMsg.file.mime)
                                menu.contextMenuMsg = null
                            }
                            // Save to stickers for image files
                            if (Helpers.isImageMime(targetMsg.file.mime)) {
                                var showSaveToPackPicker by remember { mutableStateOf(false) }
                                MenuItemRow(stringResource(R.string.chat_save_to_stickers)) {
                                    showSaveToPackPicker = true
                                }
                                if (showSaveToPackPicker) {
                                    val stickersRoot = java.io.File(context.filesDir, "stickers")
                                    val packDirs = stickersRoot.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()
                                    AlertDialog(
                                        onDismissRequest = { showSaveToPackPicker = false },
                                        containerColor = C.card,
                                        title = { Text(stringResource(R.string.chat_save_to_pack), color = C.text) },
                                        text = {
                                            Column {
                                                if (packDirs.isEmpty()) {
                                                    Text(stringResource(R.string.chat_no_packs_yet), color = C.textSecondary, fontSize = 13.sp)
                                                }
                                                packDirs.forEach { dir ->
                                                    val count = dir.listFiles()?.size ?: 0
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                                            .clickable {
                                                                try {
                                                                    val src = java.io.File(targetPath)
                                                                    val bmp = android.graphics.BitmapFactory.decodeFile(src.absolutePath)
                                                                    if (bmp != null) {
                                                                        val maxSz = 512
                                                                        val scale = minOf(maxSz.toFloat() / bmp.width, maxSz.toFloat() / bmp.height, 1f)
                                                                        val w = (bmp.width * scale).toInt()
                                                                        val h = (bmp.height * scale).toInt()
                                                                        val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, w, h, true)
                                                                        val dest = java.io.File(dir, "sticker_${System.currentTimeMillis()}.webp")
                                                                        dest.outputStream().use { scaled.compress(android.graphics.Bitmap.CompressFormat.WEBP, 80, it) }
                                                                        Toast.makeText(context, context.getString(R.string.toast_saved_to_dir, dir.name), Toast.LENGTH_SHORT).show()
                                                                    }
                                                                } catch (_: Exception) {}
                                                                showSaveToPackPicker = false
                                                                menu.contextMenuMsg = null
                                                            }
                                                            .padding(vertical = 10.dp, horizontal = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Text("\uD83C\uDFAD", fontSize = 20.sp)
                                                        Spacer(Modifier.width(12.dp))
                                                        Text(dir.name, color = C.text, fontSize = 15.sp)
                                                        Spacer(Modifier.weight(1f))
                                                        Text("$count", color = C.textMuted, fontSize = 12.sp)
                                                    }
                                                }
                                            }
                                        },
                                        confirmButton = {},
                                        dismissButton = {
                                            TextButton(onClick = { showSaveToPackPicker = false }) {
                                                Text(stringResource(R.string.general_cancel), color = C.textSecondary)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    MenuItemRow(stringResource(R.string.chat_reply)) { input.replyingTo = targetMsg; menu.contextMenuMsg = null }

                    // Poll creator: close or reopen voting
                    if (targetMsg.type == "poll" && menuPollData != null &&
                        myHandle != null && targetMsg.from == myHandle
                    ) {
                        val pollClosed = com.privimemobile.chat.poll.PollLogic.isClosed(menuPollData)
                        MenuItemRow(
                            stringResource(
                                if (pollClosed) R.string.chat_poll_reopen else R.string.chat_poll_close,
                            ),
                        ) {
                            scope.launch {
                                val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
                                val creator = state?.myHandle ?: return@launch
                                // Always read live poll_data — snapshots can be stale and would wipe votes.
                                val freshPollData = com.privimemobile.chat.ChatService.db
                                    ?.messageDao()?.findById(menuMsgId)?.pollData
                                    ?: menuPollData
                                val isClosedNow = com.privimemobile.chat.poll.PollLogic.isClosed(freshPollData)
                                val ts = System.currentTimeMillis() / 1000
                                val updated = if (isClosedNow) {
                                    com.privimemobile.chat.poll.PollLogic.applyReopen(freshPollData)
                                } else {
                                    com.privimemobile.chat.poll.PollLogic.applyClose(freshPollData, ts)
                                }
                                if (updated != null) {
                                    com.privimemobile.chat.ChatService.db?.messageDao()?.updatePollData(
                                        menuMsgId, updated,
                                    )
                                    pollUi.patch(menuMsgId, updated)
                                }
                                val payload = mapOf(
                                    "v" to 1,
                                    "t" to if (isClosedNow) "poll_reopen" else "poll_close",
                                    "ts" to ts,
                                    "from" to creator,
                                    "to" to (if (isGroupMode) groupId!! else handle),
                                    "msg_ts" to targetMsg.timestamp,
                                )
                                if (isGroupMode && groupId != null) {
                                    com.privimemobile.chat.ChatService.groups.sendGroupPayload(groupId, payload)
                                } else {
                                    val walletId = resolvedSbbsAddress
                                    if (!walletId.isNullOrEmpty()) {
                                        com.privimemobile.chat.ChatService.sbbs.sendWithRetry(walletId, payload)
                                    }
                                }
                            }
                            menu.contextMenuMsg = null
                        }
                    }

                    // Pin/Unpin — in group mode, only admin (role>=1) or creator (role==2) can pin
                    val canPin = !isGroupMode || (group?.myRole ?: 0) >= 1
                    if (canPin) {
                        MenuItemRow(if (targetMsg.pinned) stringResource(R.string.chat_unpin) else stringResource(R.string.chat_pin)) {
                            val isPinning = !targetMsg.pinned
                            scope.launch {
                                if (isPinning) com.privimemobile.chat.ChatService.db?.messageDao()?.pinMessage(targetMsg.id.toLong())
                                else com.privimemobile.chat.ChatService.db?.messageDao()?.unpinMessage(targetMsg.id.toLong())
                                // Broadcast pin/unpin to group members
                                if (isGroupMode && groupId != null) {
                                    val pinPayload = mapOf(
                                        "v" to 1, "t" to "group_pin",
                                        "ts" to System.currentTimeMillis() / 1000,
                                        "msg_ts" to targetMsg.timestamp,
                                        "msg" to (targetMsg.text.take(100)),
                                        "pin" to isPinning,
                                    )
                                    com.privimemobile.chat.ChatService.groups.sendGroupPayload(groupId, pinPayload)
                                }
                            }
                            menu.contextMenuMsg = null
                        }
                    }

                    if (targetMsg.sent && targetMsg.text.isNotEmpty() && targetMsg.type != "tip") {
                        MenuItemRow(stringResource(R.string.chat_edit)) {
                            input.editingMsg = targetMsg; input.replyingTo = null
                            input.setInputText(targetMsg.text); menu.contextMenuMsg = null
                        }
                    }

                    if (targetMsg.text.isNotEmpty() || targetMsg.file != null) {
                        MenuItemRow(stringResource(R.string.chat_forward)) {
                            forward.openSingle(targetMsg); menu.contextMenuMsg = null
                        }
                    }

                    // Resend option for sent messages not yet delivered (text, file, voice, sticker, sticker_pack, poll)
                    if (targetMsg.sent && !targetMsg.delivered && targetMsg.scheduledAt == 0L) {
                        MenuItemRow(stringResource(R.string.chat_resend)) {
                            scope.launch {
                                val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
                                if (state?.myHandle != null) {
                                    val msgId = targetMsg.id.toLong()
                                    val attachment = com.privimemobile.chat.ChatService.db?.attachmentDao()?.findByMessageId(msgId)
                                    val isNewTs = System.currentTimeMillis() / 1000
                                    if (isGroupMode && groupId != null) {
                                        // GROUP: rebuild payload based on original type
                                        val payload = when {
                                            targetMsg.type == "tip" -> mutableMapOf<String, Any?>(
                                                "v" to 1, "t" to "tip", "ts" to isNewTs,
                                                "from" to state.myHandle!!, "to" to groupId,
                                                "dn" to (state.myDisplayName ?: ""),
                                                "msg" to (targetMsg.text ?: ""),
                                                "amount" to targetMsg.tipAmount,
                                                "asset_id" to targetMsg.tipAssetId,
                                                "reply" to (targetMsg.reply ?: ""), "reply_ts" to targetMsg.replyTs,
                                            )
                                            targetMsg.type == "poll" -> mutableMapOf<String, Any?>(
                                                "v" to 1, "t" to "poll", "ts" to isNewTs,
                                                "from" to state.myHandle!!, "to" to groupId,
                                                "dn" to (state.myDisplayName ?: ""),
                                                "poll" to (targetMsg.pollData ?: targetMsg.text ?: ""),
                                                "reply" to (targetMsg.reply ?: ""), "reply_ts" to targetMsg.replyTs,
                                            )
                                            attachment != null && attachment.mimeType == "application/x-tgsticker" -> mutableMapOf<String, Any?>(
                                                "v" to 1, "t" to targetMsg.type, "ts" to isNewTs,
                                                "from" to state.myHandle!!, "to" to groupId,
                                                "dn" to (state.myDisplayName ?: ""),
                                                "file" to mapOf<String, Any?>(
                                                    "name" to attachment.fileName, "size" to attachment.fileSize,
                                                    "mime" to attachment.mimeType, "data" to attachment.inlineData,
                                                    "key" to attachment.encryptionKey, "iv" to attachment.encryptionIv,
                                                    "cid" to attachment.ipfsCid,
                                                ),
                                                "extras" to attachment.extras,
                                            )
                                            attachment != null -> mutableMapOf<String, Any?>(
                                                "v" to 1, "t" to targetMsg.type, "ts" to isNewTs,
                                                "from" to state.myHandle!!, "to" to groupId,
                                                "dn" to (state.myDisplayName ?: ""),
                                                "file" to mapOf<String, Any?>(
                                                    "name" to attachment.fileName, "size" to attachment.fileSize,
                                                    "mime" to attachment.mimeType, "data" to attachment.inlineData,
                                                    "key" to attachment.encryptionKey, "iv" to attachment.encryptionIv,
                                                    "cid" to attachment.ipfsCid,
                                                ),
                                                "extras" to attachment.extras,
                                            )
                                            else -> mutableMapOf<String, Any?>(
                                                "v" to 1, "t" to targetMsg.type, "ts" to isNewTs,
                                                "from" to state.myHandle!!, "to" to groupId,
                                                "dn" to (state.myDisplayName ?: ""),
                                                "msg" to (targetMsg.text ?: targetMsg.type),
                                                "reply" to (targetMsg.reply ?: ""), "reply_ts" to targetMsg.replyTs,
                                            )
                                        }
                                        com.privimemobile.chat.ChatService.groups.sendGroupPayload(groupId, payload)
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, R.string.toast_message_resent, Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        // DM: rebuild payload based on original type
                                        val wid = resolvedSbbsAddress
                                        if (!wid.isNullOrEmpty()) {
                                            val payload = when {
                                                targetMsg.type == "tip" -> mapOf<String, Any?>(
                                                    "v" to 1, "t" to "tip", "ts" to isNewTs,
                                                    "from" to state.myHandle!!, "to" to handle,
                                                    "dn" to (state.myDisplayName ?: ""),
                                                    "msg" to (targetMsg.text ?: ""),
                                                    "amount" to targetMsg.tipAmount,
                                                    "asset_id" to targetMsg.tipAssetId,
                                                )
                                                attachment != null && attachment.mimeType == "application/x-tgsticker" -> mapOf<String, Any?>(
                                                    "v" to 1, "t" to "file", "ts" to isNewTs,
                                                    "from" to state.myHandle!!, "to" to handle,
                                                    "dn" to (state.myDisplayName ?: ""),
                                                    "file" to mapOf<String, Any?>(
                                                        "name" to attachment.fileName, "size" to attachment.fileSize,
                                                        "mime" to attachment.mimeType,
                                                        "data" to attachment.inlineData,
                                                        "key" to attachment.encryptionKey, "iv" to attachment.encryptionIv,
                                                        "cid" to attachment.ipfsCid,
                                                    ),
                                                    "extras" to attachment.extras,
                                                )
                                                attachment != null -> mapOf<String, Any?>(
                                                    "v" to 1, "t" to "file", "ts" to isNewTs,
                                                    "from" to state.myHandle!!, "to" to handle,
                                                    "dn" to (state.myDisplayName ?: ""),
                                                    "file" to mapOf<String, Any?>(
                                                        "name" to attachment.fileName, "size" to attachment.fileSize,
                                                        "mime" to attachment.mimeType,
                                                        "data" to attachment.inlineData,
                                                        "key" to attachment.encryptionKey, "iv" to attachment.encryptionIv,
                                                        "cid" to attachment.ipfsCid,
                                                    ),
                                                    "extras" to attachment.extras,
                                                )
                                                else -> mapOf<String, Any?>(
                                                    "v" to 1, "t" to "dm", "ts" to isNewTs,
                                                    "from" to state.myHandle!!, "to" to handle,
                                                    "dn" to (state.myDisplayName ?: ""),
                                                    "msg" to (targetMsg.text ?: ""),
                                                )
                                            }
                                            com.privimemobile.chat.ChatService.sbbs.sendWithRetry(wid, payload)
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, R.string.toast_message_resent, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            }
                            menu.contextMenuMsg = null
                        }
                    }

                    MenuItemRow(stringResource(R.string.chat_select)) {
                        selection.enterSelectionWith(targetMsg.id); menu.contextMenuMsg = null
                    }

                    // Cancel scheduled message option
                    if (targetMsg.scheduledAt > 0) {
                        MenuItemRow(stringResource(R.string.chat_cancel_scheduled), color = C.error) {
                            val cid = convId
                            scope.launch {
                                com.privimemobile.chat.ChatService.db?.messageDao()?.cancelScheduled(targetMsg.id.toLong())
                                onRefreshConversationPreview(cid)
                            }
                            menu.contextMenuMsg = null
                            Toast.makeText(context, R.string.toast_scheduled_cancelled, Toast.LENGTH_SHORT).show()
                        }
                    }

                    MenuItemRow(stringResource(R.string.chat_delete_for_me), color = C.error) {
                        val cid = convId
                        scope.launch {
                            com.privimemobile.chat.ChatService.db?.messageDao()?.markDeletedById(targetMsg.id.toLong())
                            onRefreshConversationPreview(cid)
                        }
                        menu.contextMenuMsg = null
                    }

                    if (targetMsg.sent) {
                        MenuItemRow(stringResource(R.string.chat_delete_for_everyone), color = C.error) {
                            scope.launch {
                                val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
                                if (state?.myHandle != null) {
                                    com.privimemobile.chat.ChatService.db?.messageDao()?.markDeletedById(targetMsg.id.toLong())
                                    // Update preview BEFORE network send — survives early navigation
                                    onRefreshConversationPreview(convId)
                                    val delPayload = com.privimemobile.chat.DeleteForEveryone.payload(
                                        myHandle = state.myHandle!!,
                                        to = if (isGroupMode) groupId!! else handle,
                                        msgTimestamp = targetMsg.timestamp,
                                    )
                                    if (isGroupMode && groupId != null) {
                                        com.privimemobile.chat.ChatService.groups.sendGroupPayload(groupId, delPayload)
                                    } else {
                                        val walletId = resolvedSbbsAddress
                                        if (!walletId.isNullOrEmpty()) {
                                            com.privimemobile.chat.ChatService.sbbs.sendWithRetry(walletId, delPayload)
                                        }
                                    }
                                }
                            }
                            menu.contextMenuMsg = null
                        }
                    }
                }
            }
        }

    if (menu.reactionDetailMsg != null) {
        val detailMsg = menu.reactionDetailMsg!!
        // Load ALL reactions for this message (for "All" tab + tab list)
        val allReactorsFlow = remember(detailMsg.timestamp) {
            com.privimemobile.chat.ChatService.db!!.reactionDao()
                .observeForMessage(detailMsg.timestamp)
        }
        val allReactors by allReactorsFlow.collectAsState(initial = emptyList())

        // Build tab list: first = "All", then each unique emoji sorted by count
        val emojiTabs = remember(allReactors) {
            val active = allReactors.filter { r -> !r.removed }
            val grouped: Map<String, List<com.privimemobile.chat.db.entities.ReactionEntity>> = active.groupBy { r -> r.emoji }
            val sorted = grouped.entries.sortedByDescending { e -> e.value.size }
            val tabs = mutableListOf<Pair<String, String?>>()
            tabs.add(Pair("\uD83D\uDCAC", null)) // "All" tab with icon
            for (entry in sorted) {
                tabs.add(Pair(entry.key, entry.key))
            }
            tabs
        }
        var selectedTabIdx by remember { mutableIntStateOf(0) }

        ModalBottomSheet(
            onDismissRequest = { menu.reactionDetailMsg = null },
            containerColor = C.card,
            dragHandle = {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp).height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(C.textMuted.copy(alpha = 0.4f)),
                    )
                }
            },
        ) {
            Column(modifier = Modifier.padding(horizontal = 0.dp).padding(bottom = 24.dp)) {
                // Header: emoji + total count
                Text(
                    "${emojiTabs.size - 1} reaction${if (emojiTabs.size - 1 != 1) "s" else ""}",
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )

                // Scrollable tab row
                if (emojiTabs.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        emojiTabs.forEachIndexed { idx, (label, _) ->
                            val isAllTab = idx == 0
                            val isSelected = idx == selectedTabIdx
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) C.accent.copy(alpha = 0.2f) else C.bg,
                                modifier = Modifier
                                    .clickable { selectedTabIdx = idx }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(label, fontSize = 14.sp)
                                    if (!isAllTab) {
                                        val count = allReactors.count { r -> !r.removed && r.emoji == label }
                                        if (count > 1) {
                                            Text(" $count", fontSize = 12.sp, color = if (isSelected) C.accent else C.textMuted)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 8.dp))

                // Content based on selected tab
                val selectedEmoji = if (selectedTabIdx == 0) null else emojiTabs.getOrNull(selectedTabIdx)?.second
                val filteredReactors = remember(allReactors, selectedEmoji) {
                    if (selectedEmoji == null) {
                        // "All" tab — show in emoji-grouped order
                        allReactors.filter { !it.removed }
                            .sortedByDescending { it.timestamp }
                    } else {
                        allReactors.filter { !it.removed && it.emoji == selectedEmoji }
                            .sortedByDescending { it.timestamp }
                    }
                }

                if (filteredReactors.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.chat_no_reactions), color = C.textSecondary, fontSize = 14.sp)
                    }
                } else {
                    // Per-reactor date formatting (reactor.timestamp = when they reacted)
                    val reactedDateFormat = remember { java.text.SimpleDateFormat("E, MMM d 'at' HH:mm", java.util.Locale.getDefault()) }

                    // Pre-load contacts once for avatar display names
                    var contactsMap by remember { mutableStateOf<Map<String, com.privimemobile.chat.db.entities.ContactEntity>>(emptyMap()) }
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        val db = com.privimemobile.chat.ChatService.db ?: return@LaunchedEffect
                        val allContacts = db.contactDao().observeAll()
                        allContacts.collect { contacts ->
                            contactsMap = contacts.associateBy { it.handle }
                        }
                    }

                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        filteredReactors.forEach { reactor ->
                            val isMe = reactor.senderHandle == myHandle
                            val contact = contactsMap[reactor.senderHandle]
                            val displayName = if (isMe) stringResource(R.string.chat_you_label) else contact?.displayName ?: groupMemberNames[reactor.senderHandle] ?: "@${reactor.senderHandle}"
                            val reactedDate = reactedDateFormat.format(java.util.Date(reactor.timestamp * 1000))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Avatar
                                com.privimemobile.ui.components.AvatarDisplay(
                                    handle = reactor.senderHandle,
                                    displayName = contact?.displayName,
                                    size = 44.dp,
                                    isMe = isMe,
                                )
                                Spacer(Modifier.width(12.dp))

                                // Name + "reacted on" timestamp
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        displayName,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        context.getString(R.string.chat_reacted_on, reactedDate),
                                        fontSize = 12.sp,
                                        color = C.textSecondary,
                                    )
                                }

                                // Reaction emoji (only on "All" tab)
                                if (selectedEmoji == null) {
                                    Text(reactor.emoji, fontSize = 20.sp)
                                }
                            }

                            if (reactor != filteredReactors.last()) {
                                HorizontalDivider(color = C.border.copy(alpha = 0.5f), modifier = Modifier.padding(start = 72.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
