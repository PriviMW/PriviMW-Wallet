package com.privimemobile.ui.chat

import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.privimemobile.protocol.*
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.compose.BackHandler
import java.text.SimpleDateFormat
import java.util.*

/**
 * Chat screen with full feature parity to ChatScreen.tsx:
 * - File attachment button + document picker
 * - Image display for file messages
 * - File download/save
 * - Scroll to bottom on new message
 * - Message grouping by date separator
 * - Read receipts
 * - Tip labels
 * - Reply display + swipe-left to reply
 * - Pending file preview bar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    handle: String,
    displayName: String = "",
    onBack: () -> Unit,
    onMediaGallery: () -> Unit = {},
    onContactInfo: () -> Unit = {},
    onNavigateToChat: (String) -> Unit = {},
    scrollToTimestamp: Long = 0L,
    groupId: String? = null,
    onGroupSettings: () -> Unit = {},
    onViewContact: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = androidx.compose.ui.platform.LocalView.current  // for haptic feedback
    val isGroupMode = groupId != null

    // --- Group mode state (reactive — observes DB changes) ---
    val allGroups by com.privimemobile.chat.ChatService.db?.groupDao()?.observeAll()
        ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val group = if (isGroupMode) allGroups.firstOrNull { it.groupId == groupId } else null

    var groupConvId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(groupId) {
        if (groupId != null) {
            // Initial load if not in DB yet
            if (allGroups.none { it.groupId == groupId }) {
                com.privimemobile.chat.ChatService.groups.refreshGroupInfo(groupId)
            }
            com.privimemobile.chat.ChatService.groups.refreshGroupMembers(groupId)
            com.privimemobile.chat.ChatService.db?.groupDao()?.clearUnread(groupId)
            val grp = com.privimemobile.chat.ChatService.db?.groupDao()?.findByGroupId(groupId)
            groupConvId = com.privimemobile.chat.ChatService.groups.getOrCreateGroupConversation(groupId, grp?.name ?: "Group")

            // Request group avatar + description if missing locally
            com.privimemobile.chat.ChatService.groups.requestGroupInfoIfNeeded(groupId)
            // Request member profile pictures if missing
            com.privimemobile.chat.ChatService.groups.requestMemberAvatars(groupId)
        }
    }

    // Observe group members reactively for accurate count
    val groupMembers by if (isGroupMode) {
        com.privimemobile.chat.ChatService.db?.groupDao()?.observeMembers(groupId!!)
            ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    } else {
        remember { mutableStateOf(emptyList<com.privimemobile.chat.db.entities.GroupMemberEntity>()) }
    }
    val groupMemberCount = groupMembers.size
    // Map handle → display name for sender labels in chat bubbles
    val groupMemberNames = remember(groupMembers) {
        groupMembers.filter { !it.displayName.isNullOrEmpty() }
            .associate { it.handle to it.displayName!! }
    }

    val convKey = if (isGroupMode) "g_${groupId!!.take(16)}" else "@$handle"

    // Observe conversation reactively — updates when MessageProcessor creates it
    val conversations by com.privimemobile.chat.ChatService.db?.conversationDao()?.observeAll()
        ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val conv = conversations.firstOrNull { it.convKey == convKey }
    val convId = if (isGroupMode) (groupConvId ?: conv?.id ?: 0L) else (conv?.id ?: 0L)

    // Messages from Room DB — automatically updates when convId changes
    val roomMessages by remember(convId) {
        if (convId > 0L) {
            com.privimemobile.chat.ChatService.db!!.messageDao().observeAll(convId)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.collectAsState(initial = emptyList())

    // File download tracking (declared early so attachment loading can pre-populate)
    val filePaths = remember { mutableStateMapOf<String, String>() }
    val downloadStatuses = remember { mutableStateMapOf<String, String>() } // idle, downloading, decrypting, done, error

    // Load attachments for file messages + pre-populate cached file paths
    var attachmentMap by remember { mutableStateOf<Map<Long, com.privimemobile.chat.db.entities.AttachmentEntity>>(emptyMap()) }
    LaunchedEffect(roomMessages) {
        val map = mutableMapOf<Long, com.privimemobile.chat.db.entities.AttachmentEntity>()
        roomMessages.filter { it.type == "file" || it.type == "sticker" || it.type == "sticker_pack" }.forEach { msg ->
            val att = com.privimemobile.chat.ChatService.db?.attachmentDao()?.findByMessageId(msg.id)
            if (att != null) {
                map[msg.id] = att
                // Pre-load cached path so images show instantly (no placeholder flash)
                val cid = att.ipfsCid ?: ""
                if (cid.isNotEmpty() && !filePaths.containsKey(cid)) {
                    val path = com.privimemobile.chat.transport.IpfsTransport.getLocalFilePath(cid)
                    if (path != null) filePaths[cid] = path
                }
            }
        }
        attachmentMap = map
    }

    val nowSecs = System.currentTimeMillis() / 1000
    val messages = remember(roomMessages, attachmentMap) { roomMessages.filter { entity ->
        // Skip expired disappearing messages (cleanup coroutine handles DB deletion)
        entity.expiresAt == 0L || entity.expiresAt > nowSecs
    }.map { entity ->
        val att = attachmentMap[entity.id]
        ChatMessage(
            id = entity.id.toString(),
            from = entity.senderHandle ?: "",
            to = handle,
            text = entity.text ?: "",
            timestamp = entity.timestamp,
            sent = entity.sent,
            read = entity.read,
            delivered = entity.delivered,
            type = entity.type,
            isTip = entity.type == "tip",
            tipAmount = entity.tipAmount,
            tipAssetId = entity.tipAssetId,
            reply = entity.replyText,
            fwdFrom = entity.fwdFrom,
            edited = entity.edited,
            expiresAt = entity.expiresAt,
            pinned = entity.pinned,
            pinnedAt = entity.pinnedAt,
            pollData = entity.pollData,
            scheduledAt = entity.scheduledAt,
            stickerPackName = entity.stickerPackName,
            stickerPackId = entity.stickerPackId,
            stickerEmoji = entity.stickerEmoji,
            stickerPackTotal = entity.stickerPackTotal,
            file = if (att != null) FileAttachment(
                cid = att.ipfsCid ?: "",
                name = att.fileName,
                size = att.fileSize,
                mime = att.mimeType,
                key = att.encryptionKey,
                iv = att.encryptionIv,
                data = att.inlineData,
            ) else null,
        )
    } }

    // Contact from Room DB
    val roomContact by com.privimemobile.chat.ChatService.db?.contactDao()?.observeAll()
        ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val contact = roomContact.firstOrNull { it.handle == handle }

    val resolvedName = if (isGroupMode) {
        group?.name ?: "Group"
    } else {
        displayName.ifEmpty { contact?.displayName?.ifEmpty { null } ?: "@$handle" }
    }
    val resolvedWalletId = contact?.walletId

    // Input state — use TextFieldValue for cursor control
    var inputText by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
    fun setInputText(text: String) {
        inputText = androidx.compose.ui.text.input.TextFieldValue(
            text = text,
            selection = androidx.compose.ui.text.TextRange(text.length), // cursor at end
        )
    }
    var uploading by remember { mutableStateOf(false) }

    // Pending file to send
    var pendingFile by remember { mutableStateOf<PendingFile?>(null) }
    // Sticker metadata for the pending file (null = regular file, non-null = sticker)
    data class StickerMeta(val packName: String, val packId: String, val packTotal: Int, val emoji: String? = null)
    var pendingStickerMeta by remember { mutableStateOf<StickerMeta?>(null) }

    // Reply target — set by swiping left on a message
    var replyingTo by remember { mutableStateOf<ChatMessage?>(null) }

    // Edit target — set from context menu on own messages
    var editingMsg by remember { mutableStateOf<ChatMessage?>(null) }

    // Per-message self-destruct timer (one-shot, independent of conversation-level timer)
    var oneShotTimer by remember { mutableStateOf(0) }  // 0=off, seconds
    var showOneShotTimerPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }

    // Multi-select mode
    var selectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var viewPackId by remember { mutableStateOf<String?>(null) }  // pack_id to show in View Pack dialog
    var pendingDeleteIds by remember { mutableStateOf<List<String>>(emptyList()) }

    // Long-press context menu target
    var contextMenuMsg by remember { mutableStateOf<ChatMessage?>(null) }

    // Reactions — observe all reactions for this conversation
    val reactions by remember(convId) {
        if (convId > 0L) {
            com.privimemobile.chat.ChatService.db!!.reactionDao().observeForConversation(convId)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.collectAsState(initial = emptyList())

    // Group reactions by message timestamp → Map<Long, List<Triple<emoji, count, isMine>>>
    val chatState by com.privimemobile.chat.ChatService.observeState().collectAsState(initial = null)
    val myHandle = chatState?.myHandle

    val reactionMap = remember(reactions, myHandle) {
        reactions.groupBy { it.messageTs }
            .mapValues { (_, reacts) ->
                reacts.groupBy { it.emoji }
                    .map { (emoji, list) ->
                        Triple(emoji, list.size, list.any { it.senderHandle == myHandle })
                    }
                    .sortedByDescending { it.second }
            }
    }

    // Forward message — contact picker dialog
    var forwardingMsg by remember { mutableStateOf<ChatMessage?>(null) }
    var forwardingMsgs by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }

    // All contacts for forward picker
    val allContacts by com.privimemobile.chat.ChatService.db?.contactDao()?.observeAll()
        ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }

    // 3-dot overflow menu
    var showOverflowMenu by remember { mutableStateOf(false) }
    // Chat wallpaper
    var showWallpaperPicker by remember { mutableStateOf(false) }
    val prefs = context.getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)
    var chatWallpaper by remember { mutableStateOf(prefs.getString("wallpaper_$convKey", "default") ?: "default") }
    val wallpaperImagePicker = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val file = java.io.File(context.filesDir, "wallpaper_${convKey.replace("@", "")}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                chatWallpaper = "custom:${file.absolutePath}#${System.currentTimeMillis()}"
                prefs.edit().putString("wallpaper_$convKey", chatWallpaper).apply()
                showWallpaperPicker = false
            } catch (_: Exception) {}
        }
    }
    var showCommandMenu by remember { mutableStateOf(false) }
    var showSchedulePicker by remember { mutableStateOf(false) }
    var showCreateStickerPack by remember { mutableStateOf(false) }
    var emojiMainTab by remember { mutableStateOf(0) }  // 0=Emoji, 1=Stickers
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var showClearConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // showDisappearPicker removed — per-message self-destruct replaces conversation-level timer

    // In-chat search
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<com.privimemobile.chat.db.entities.MessageEntity>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var searchHighlightTs by remember { mutableStateOf<Long?>(null) }

    // Attachment picker
    var showAttachPicker by remember { mutableStateOf(false) }
    var attachPickerTab by remember { mutableStateOf(0) } // 0 = Gallery, 1 = Files

    // Fullscreen image viewer
    var fullscreenImage by remember { mutableStateOf<Pair<String, String>?>(null) } // (filePath, fileName)

    val listState = rememberLazyListState()

    // Capture unread count before setActiveChat clears it (for "X new messages" divider)
    // Read directly from DB to avoid race with reactive Flow clearing
    var initialUnreadCount by remember { mutableStateOf(0) }
    LaunchedEffect(convId) {
        if (convId > 0L && initialUnreadCount == 0) {
            initialUnreadCount = com.privimemobile.chat.ChatService.db?.messageDao()?.countUnread(convId) ?: 0
        }
    }

    // Load draft on mount — read directly from DB to avoid reactive state race
    LaunchedEffect(convId) {
        if (convId > 0L) {
            val draft = com.privimemobile.chat.ChatService.db?.conversationDao()?.findById(convId)?.draftText
            if (!draft.isNullOrEmpty()) {
                setInputText(draft)
            }
        }
    }

    // Keep latest convId available for DisposableEffect (plain val isn't captured correctly)
    val currentConvId by rememberUpdatedState(convId)

    // Mark chat as active, clear unread, send read receipts
    LaunchedEffect(handle, groupId) {
        com.privimemobile.chat.ChatService.setActiveChat(convKey)
        if (!isGroupMode) {
            com.privimemobile.chat.ChatService.contacts.reResolveOnChatOpen(handle)
        }
    }
    DisposableEffect(handle, groupId) {
        onDispose {
            com.privimemobile.chat.ChatService.setActiveChat(null)
            // Save draft on dispose
            val draftText = inputText.text.trim().ifEmpty { null }
            if (currentConvId > 0L) {
                kotlinx.coroutines.GlobalScope.launch {
                    com.privimemobile.chat.ChatService.db?.conversationDao()?.setDraft(currentConvId, draftText)
                }
            }
        }
    }

    // Scroll to bottom when messages change
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && scrollToTimestamp == 0L) {
            listState.animateScrollToItem(0) // reversed layout, 0 = bottom
        }
    }

    // Scroll to specific message (from search results)
    var searchHighlightFromNav by remember { mutableStateOf(scrollToTimestamp) }
    LaunchedEffect(scrollToTimestamp, messages.size) {
        if (scrollToTimestamp > 0L && messages.isNotEmpty()) {
            val reversedMessages = messages.reversed()
            val idx = reversedMessages.indexOfFirst { it.timestamp == scrollToTimestamp }
            if (idx >= 0) {
                searchHighlightFromNav = scrollToTimestamp
                listState.animateScrollToItem(idx)
                // Auto-clear highlight after 3s
                delay(3000)
                searchHighlightFromNav = 0L
            }
        }
    }

    // Load cached file paths + auto-download images/GIFs
    LaunchedEffect(messages) {
        messages.forEach { msg ->
            val fileCid = msg.file?.cid ?: ""
            if (fileCid.isNotEmpty() && !filePaths.containsKey(fileCid)) {
                val path = com.privimemobile.chat.transport.IpfsTransport.getLocalFilePath(fileCid)
                if (path != null) {
                    filePaths[fileCid] = path
                } else if ((Helpers.isImageMime(msg.file?.mime ?: "") || msg.type == "sticker_pack" || msg.type == "sticker")
                    && (msg.file?.size ?: 0) <= Config.AUTO_DL_MAX_SIZE
                    && downloadStatuses[fileCid] == null
                ) {
                    // Auto-download images, GIFs, and sticker packs
                    downloadStatuses[fileCid] = "downloading"
                    scope.launch {
                        try {
                            val attachment = com.privimemobile.chat.ChatService.db?.attachmentDao()?.findByCid(fileCid)
                            if (attachment != null) {
                                val dlPath = com.privimemobile.chat.transport.IpfsTransport.downloadFile(
                                    attachmentId = attachment.id,
                                    ipfsCid = fileCid,
                                    keyHex = msg.file?.key ?: "",
                                    ivHex = msg.file?.iv ?: "",
                                    inlineData = attachment.inlineData ?: msg.file?.data,
                                )
                                filePaths[fileCid] = dlPath
                                downloadStatuses[fileCid] = "done"
                            } else {
                                downloadStatuses[fileCid] = "error"
                            }
                        } catch (_: Exception) {
                            downloadStatuses[fileCid] = "error"
                        }
                    }
                }
            }
        }
    }

    // Common handler for picked files (from any source)
    fun handlePickedUri(uri: Uri) {
        val info = getFileInfo(context, uri)
        if (info != null) {
            Log.d("ChatScreen", "File picked: ${info.name}, ${info.size} bytes, ${info.mimeType}")
            val isImage = Helpers.isImageMime(info.mimeType)
            // Images: allow up to MAX_FILE_SIZE (compression will shrink them)
            // Non-images: cap at MAX_INLINE_SIZE (no compression, must fit inline)
            val limit = if (isImage) Config.MAX_FILE_SIZE else Config.MAX_INLINE_SIZE
            if (info.size > limit) {
                Log.w("ChatScreen", "File too large: ${info.size} > $limit (isImage=$isImage)")
                val msg = if (isImage) {
                    "Image too large (max ${Config.MAX_FILE_SIZE / 1024 / 1024}MB)"
                } else {
                    "File too large (max ${Config.MAX_INLINE_SIZE / 1024}KB). Only images are compressed."
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                return
            }
            pendingFile = PendingFile(uri = uri, name = info.name, size = info.size, mimeType = info.mimeType)
            showAttachPicker = false
        } else {
            Log.w("ChatScreen", "File info is null for uri: $uri")
        }
    }

    // File picker launcher (for Files tab)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) handlePickedUri(uri)
    }

    // Gallery image picker launcher (for Gallery tab — picks from system gallery)
    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) handlePickedUri(uri)
    }

    // Send cooldown — 1s between sends to prevent spam
    var lastSendTime by remember { mutableStateOf(0L) }

    // Schedule a message for later sending
    fun scheduleMessage(text: String, scheduledAt: Long) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get() ?: return@launch
            val myHandle = state.myHandle ?: return@launch
            val ts = System.currentTimeMillis() / 1000
            val schedConvId = if (isGroupMode && groupId != null) {
                com.privimemobile.chat.ChatService.groups.getOrCreateGroupConversation(groupId, group?.name ?: "Group")
            } else {
                val conv = com.privimemobile.chat.ChatService.db!!.conversationDao().getOrCreate(convKey, handle)
                if (conv.deletedAtTs > 0) com.privimemobile.chat.ChatService.db!!.conversationDao().undelete(conv.id)
                conv.id
            }
            val dedupKey = "$ts:${trimmed.hashCode().toString(16)}:scheduled:true"
            val entity = com.privimemobile.chat.db.entities.MessageEntity(
                conversationId = schedConvId,
                text = trimmed,
                timestamp = ts,
                sent = true,
                type = if (isGroupMode) "group_msg" else "dm",
                senderHandle = myHandle,
                sbbsDedupKey = dedupKey,
                scheduledAt = scheduledAt,
            )
            com.privimemobile.chat.ChatService.db!!.messageDao().insert(entity)
            val sdf = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
            val timeStr = sdf.format(java.util.Date(scheduledAt * 1000))
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Message scheduled for $timeStr", Toast.LENGTH_LONG).show()
            }
        }
        setInputText("")
    }

    // Send message
    fun handleSend() {
        val now = System.currentTimeMillis()
        if (now - lastSendTime < 3000) {
            android.widget.Toast.makeText(context, "Slow down! Wait 3s between messages", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        lastSendTime = now

        // Clear draft on send and dismiss unread divider
        initialUnreadCount = 0
        if (convId > 0L) {
            scope.launch { com.privimemobile.chat.ChatService.db?.conversationDao()?.setDraft(convId, null) }
        }

        val trimmed = inputText.text.trim()

        // Edit mode — update existing message instead of creating new one
        if (editingMsg != null) {
            val editTarget = editingMsg!!
            if (trimmed.isNotEmpty() && trimmed != editTarget.text) {
                scope.launch {
                    val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
                    if (state?.myHandle != null && convId > 0L) {
                        // Update local DB
                        com.privimemobile.chat.ChatService.db?.messageDao()?.editMessage(
                            convId, editTarget.timestamp, state.myHandle!!, trimmed
                        )
                        // Update chat list preview if this was the latest message
                        val convEntity = com.privimemobile.chat.ChatService.db?.conversationDao()?.findById(convId)
                        if (convEntity != null && convEntity.lastMessageTs == editTarget.timestamp) {
                            com.privimemobile.chat.ChatService.db?.conversationDao()?.updateLastMessage(convId, editTarget.timestamp, trimmed.take(100))
                        }
                        // Send SBBS edit to recipient(s)
                        val editPayload = mapOf(
                            "v" to 1,
                            "t" to "edit",
                            "ts" to System.currentTimeMillis() / 1000,
                            "from" to state.myHandle!!,
                            "to" to (if (isGroupMode) groupId!! else handle),
                            "msg_ts" to editTarget.timestamp,
                            "msg" to trimmed,
                        )
                        if (isGroupMode && groupId != null) {
                            com.privimemobile.chat.ChatService.groups.sendGroupPayload(groupId, editPayload)
                        } else {
                            val walletId = resolvedWalletId
                            if (!walletId.isNullOrEmpty()) {
                                com.privimemobile.chat.ChatService.sbbs.sendWithRetry(walletId, editPayload)
                            }
                        }
                    }
                }
            }
            editingMsg = null
            setInputText("")
            return
        }

        // /tip command — send BEAM or any asset
        // DM:    /tip <amount> [asset_id] [message]
        // Group: /tip @handle <amount> [asset_id] [message]
        // Group with reply: /tip <amount> [asset_id] [message] (tips the quoted message sender)
        if (trimmed.startsWith("/tip ", ignoreCase = true)) {
            val parts = trimmed.removePrefix("/tip ").trimStart()
            val tokens = parts.split("\\s+".toRegex(), limit = 5)

            // In group mode: first token is @handle, OR use quoted reply sender
            val tipTargetHandle: String
            val tipTokens: List<String>
            if (isGroupMode) {
                val firstToken = tokens.getOrNull(0) ?: ""
                if (firstToken.startsWith("@") && firstToken.length >= 2) {
                    // Explicit @handle
                    tipTargetHandle = firstToken.removePrefix("@")
                    tipTokens = tokens.drop(1)
                } else if (replyingTo != null && !replyingTo!!.from.isNullOrEmpty() && !replyingTo!!.sent) {
                    // Quoted reply — tip the sender of the quoted message
                    tipTargetHandle = replyingTo!!.from
                    tipTokens = tokens // all tokens are amount/asset/msg
                } else {
                    Toast.makeText(context, "Reply to a message or use: /tip @handle <amount>", Toast.LENGTH_LONG).show()
                    setInputText(""); return
                }
            } else {
                tipTargetHandle = handle
                tipTokens = tokens
            }

            val amountBeam = tipTokens.getOrNull(0)?.toDoubleOrNull()
            if (amountBeam == null || amountBeam <= 0) {
                val usage = if (isGroupMode) "Usage: /tip @handle <amount> [asset_id] [message]"
                    else "Usage: /tip <amount> [asset_id] [message]"
                Toast.makeText(context, usage, Toast.LENGTH_SHORT).show()
                return
            }

            // Resolve wallet ID for tip target
            scope.launch {
                val tipWalletId = if (isGroupMode) {
                    // Look up target handle's wallet ID from group members or contacts
                    val member = com.privimemobile.chat.ChatService.db?.groupDao()?.findMember(groupId!!, tipTargetHandle)
                    member?.walletId ?: com.privimemobile.chat.ChatService.db?.contactDao()?.findByHandle(tipTargetHandle)?.walletId
                } else {
                    resolvedWalletId
                }

                if (tipWalletId.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Cannot resolve @$tipTargetHandle address", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Parse asset ID and caption
                val secondToken = tipTokens.getOrNull(1)
                val secondIsAssetId = secondToken?.toIntOrNull() != null
                val assetId = if (secondIsAssetId) secondToken!!.toInt() else 0
                val caption = if (secondIsAssetId) {
                    tipTokens.getOrNull(2)?.trim() ?: ""
                } else {
                    tipTokens.drop(1).joinToString(" ").trim()
                }
                val amountGroth = (amountBeam * 100_000_000).toLong()
                val assetName = com.privimemobile.wallet.assetTicker(assetId)
                val tipLabel = "Tip to @$tipTargetHandle: ${Helpers.formatBeam(amountGroth)} $assetName"

                // Balance check
                val bal = com.privimemobile.wallet.WalletEventBus.assetBalances[assetId]
                val spendable = (bal?.available ?: 0L) + (bal?.shielded ?: 0L)
                if (spendable < amountGroth) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Insufficient $assetName balance (have ${Helpers.formatBeam(spendable)}, need ${Helpers.formatBeam(amountGroth)})", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                if (assetId != 0) {
                    val beamBal = com.privimemobile.wallet.WalletEventBus.assetBalances[0]
                    val beamSpendable = (beamBal?.available ?: 0L) + (beamBal?.shielded ?: 0L)
                    if (beamSpendable <= 0) {
                        withContext(Dispatchers.Main) { Toast.makeText(context, "Insufficient BEAM for transaction fee", Toast.LENGTH_LONG).show() }
                        return@launch
                    }
                }

                try {
                    val txComment = "Tip to @$tipTargetHandle" + if (caption.isNotEmpty()) " — $caption" else ""
                    val txResult = com.privimemobile.protocol.WalletApi.callAsync("tx_send", mapOf(
                        "value" to amountGroth,
                        "address" to tipWalletId,
                        "asset_id" to assetId,
                        "comment" to txComment,
                    ))
                    if (txResult.containsKey("error")) {
                        val errMsg = (txResult["error"] as? Map<*, *>)?.get("message") as? String
                            ?: txResult["error"]?.toString() ?: "Send failed"
                        withContext(Dispatchers.Main) { Toast.makeText(context, "Tip failed: $errMsg", Toast.LENGTH_LONG).show() }
                        return@launch
                    }

                    // Insert tip message + send SBBS notification
                    val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
                    if (state?.myHandle != null) {
                        val ts = System.currentTimeMillis() / 1000
                        val dedupKey = "$ts:tip:$amountGroth:$assetId:true"
                        val entity = com.privimemobile.chat.db.entities.MessageEntity(
                            conversationId = convId,
                            text = "\u2192@$tipTargetHandle" + if (caption.isNotEmpty()) "\n$caption" else "",
                            timestamp = ts,
                            sent = true,
                            type = "tip",
                            tipAmount = amountGroth,
                            tipAssetId = assetId,
                            senderHandle = state.myHandle,
                            sbbsDedupKey = dedupKey,
                        )
                        com.privimemobile.chat.ChatService.db!!.messageDao().insert(entity)

                        val payload = mutableMapOf<String, Any?>(
                            "v" to 1, "t" to "tip", "ts" to ts,
                            "from" to state.myHandle!!, "to" to tipTargetHandle,
                            "dn" to (state.myDisplayName ?: ""),
                            "amount" to amountGroth,
                        )
                        if (assetId != 0) payload["asset_id"] = assetId
                        if (caption.isNotEmpty()) payload["msg"] = caption

                        if (isGroupMode && groupId != null) {
                            // Broadcast tip to group + update group preview
                            com.privimemobile.chat.ChatService.groups.sendGroupPayload(groupId, payload)
                            com.privimemobile.chat.ChatService.db?.groupDao()?.updateLastMessage(groupId, ts, tipLabel)
                        } else {
                            val tipConv = com.privimemobile.chat.ChatService.db!!.conversationDao().getOrCreate(convKey, tipTargetHandle)
                            if (tipConv.deletedAtTs > 0) com.privimemobile.chat.ChatService.db!!.conversationDao().undelete(tipConv.id)
                            com.privimemobile.chat.ChatService.db!!.conversationDao().updateLastMessage(tipConv.id, ts, tipLabel)
                            com.privimemobile.chat.ChatService.sbbs.sendWithRetry(tipWalletId, payload)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Tip sent! $tipLabel", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        val msg = e.message ?: "Send failed"
                        if (msg.contains("cancel", ignoreCase = true) || msg.contains("rejected", ignoreCase = true)) {
                            Toast.makeText(context, "Tip cancelled", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Tip failed: $msg", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            setInputText("")
            replyingTo = null
            return
        }

        // /poll command — create a poll
        // Usage: /poll Question? | Option 1 | Option 2 | Option 3
        if (trimmed.startsWith("/poll ", ignoreCase = true)) {
            val parts = trimmed.removePrefix("/poll ").split("|").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size >= 3) { // question + at least 2 options
                val question = parts[0]
                val options = parts.drop(1).map { mapOf("text" to it, "voters" to emptyList<String>()) }
                val pollJson = org.json.JSONObject().apply {
                    put("question", question)
                    put("options", org.json.JSONArray(options.map { org.json.JSONObject(it) }))
                }.toString()

                scope.launch {
                    val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
                    if (state?.myHandle != null) {
                        val ts = System.currentTimeMillis() / 1000
                        val dedupKey = "$ts:poll:${question.hashCode().toString(16)}:true"
                        val entity = com.privimemobile.chat.db.entities.MessageEntity(
                            conversationId = convId,
                            text = question,
                            timestamp = ts,
                            sent = true,
                            type = "poll",
                            senderHandle = state.myHandle,
                            sbbsDedupKey = dedupKey,
                            pollData = pollJson,
                        )
                        com.privimemobile.chat.ChatService.db!!.messageDao().insert(entity)
                        val payload = mapOf(
                            "v" to 1, "t" to "poll", "ts" to ts,
                            "from" to state.myHandle!!, "to" to (if (isGroupMode) groupId!! else handle),
                            "dn" to (state.myDisplayName ?: ""),
                            "msg" to question, "poll" to pollJson,
                        )
                        if (isGroupMode && groupId != null) {
                            com.privimemobile.chat.ChatService.groups.sendGroupPayload(groupId, payload)
                            com.privimemobile.chat.ChatService.db?.groupDao()?.updateLastMessage(groupId, ts, "\uD83D\uDCCA $question")
                        } else {
                            val convDb = com.privimemobile.chat.ChatService.db!!.conversationDao().getOrCreate(convKey, handle)
                            if (convDb.deletedAtTs > 0) com.privimemobile.chat.ChatService.db!!.conversationDao().undelete(convDb.id)
                            com.privimemobile.chat.ChatService.db!!.conversationDao().updateLastMessage(convDb.id, ts, "\uD83D\uDCCA $question")
                            val walletId = resolvedWalletId
                            if (!walletId.isNullOrEmpty()) {
                                com.privimemobile.chat.ChatService.sbbs.sendWithRetry(walletId, payload)
                            }
                        }
                    }
                }
            } else {
                Toast.makeText(context, "Usage: /poll Question | Option 1 | Option 2", Toast.LENGTH_LONG).show()
            }
            setInputText("")
            return
        }

        // Send file if pending
        if (pendingFile != null) {
            val file = pendingFile!!
            Log.d("ChatScreen", "Sending file: ${file.name}, ${file.size} bytes, isGroup=$isGroupMode")
            if (!isGroupMode && resolvedWalletId.isNullOrEmpty()) {
                Log.w("ChatScreen", "Cannot send file — no resolved wallet ID")
                return
            }
            // Capture reply text BEFORE coroutine
            val fileReplyText = replyingTo?.text?.take(200)?.ifEmpty { null }
            uploading = true
            scope.launch {
                try {
                    // Prepare file: compress → encrypt → inline or IPFS
                    Log.d("ChatScreen", "Calling IpfsTransport.prepareFile...")
                    val fileMeta = com.privimemobile.chat.transport.IpfsTransport.prepareFile(
                        context, file.uri, file.name, file.size, file.mimeType,
                    )
                    if (fileMeta != null) {
                        val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
                        if (state?.myHandle != null) {
                            val ts = System.currentTimeMillis() / 1000
                            val stickerMeta = pendingStickerMeta
                            val msgType = if (stickerMeta != null) "sticker" else "file"
                            val payload = mutableMapOf<String, Any?>(
                                "v" to 1, "t" to msgType, "ts" to ts,
                                "from" to state.myHandle!!, "to" to (if (isGroupMode) groupId!! else handle),
                                "dn" to (state.myDisplayName ?: ""),
                                "file" to fileMeta,
                            )
                            if (trimmed.isNotEmpty()) payload["msg"] = trimmed
                            if (fileReplyText != null) payload["reply"] = fileReplyText
                            // Add sticker pack metadata
                            if (stickerMeta != null) {
                                payload["pack_name"] = stickerMeta.packName
                                payload["pack_id"] = stickerMeta.packId
                                payload["pack_total"] = stickerMeta.packTotal
                                if (stickerMeta.emoji != null) payload["sticker_emoji"] = stickerMeta.emoji
                            }

                            // Optimistic DB insert
                            val fileConvId = if (isGroupMode) convId else {
                                val conv = com.privimemobile.chat.ChatService.db!!.conversationDao().getOrCreate(convKey, handle)
                                if (conv.deletedAtTs > 0) com.privimemobile.chat.ChatService.db!!.conversationDao().undelete(conv.id)
                                conv.id
                            }
                            val dedupKey = "$ts:$msgType:${fileMeta["cid"]}:true"
                            val entity = com.privimemobile.chat.db.entities.MessageEntity(
                                conversationId = fileConvId, text = trimmed.ifEmpty { null },
                                timestamp = ts, sent = true, type = msgType,
                                senderHandle = state.myHandle, sbbsDedupKey = dedupKey,
                                replyText = fileReplyText,
                                stickerPackName = stickerMeta?.packName,
                                stickerPackId = stickerMeta?.packId,
                                stickerEmoji = stickerMeta?.emoji,
                                stickerPackTotal = stickerMeta?.packTotal ?: 0,
                            )
                            val msgId = com.privimemobile.chat.ChatService.db!!.messageDao().insert(entity)

                            // Insert attachment
                            val cid = fileMeta["cid"] as? String ?: ""
                            // Pre-cache decrypted file BEFORE message insert (prevents blank bubble flicker)
                            if (cid.isNotEmpty()) {
                                try {
                                    val dlPath = com.privimemobile.chat.transport.IpfsTransport.downloadFile(
                                        0L, cid,
                                        fileMeta["key"] as? String ?: "",
                                        fileMeta["iv"] as? String ?: "",
                                        fileMeta["data"] as? String,
                                    )
                                    filePaths[cid] = dlPath
                                } catch (_: Exception) {}
                            }
                            if (msgId > 0) {
                                com.privimemobile.chat.ChatService.db!!.attachmentDao().insert(
                                    com.privimemobile.chat.db.entities.AttachmentEntity(
                                        messageId = msgId, conversationId = fileConvId,
                                        ipfsCid = cid,
                                        encryptionKey = fileMeta["key"] as? String ?: "",
                                        encryptionIv = fileMeta["iv"] as? String ?: "",
                                        fileName = fileMeta["name"] as? String ?: "file",
                                        fileSize = (fileMeta["size"] as? Number)?.toLong() ?: 0,
                                        mimeType = fileMeta["mime"] as? String ?: "",
                                        inlineData = fileMeta["data"] as? String,
                                        downloadStatus = "done",
                                    )
                                )
                            }

                            val preview = if (msgType == "sticker") {
                                "${stickerMeta?.emoji ?: "\uD83C\uDFAD"} Sticker"
                            } else {
                                "\uD83D\uDCCE ${fileMeta["name"]}"
                            }
                            if (isGroupMode && groupId != null) {
                                com.privimemobile.chat.ChatService.groups.sendGroupPayload(groupId, payload)
                                com.privimemobile.chat.ChatService.db?.groupDao()?.updateLastMessage(groupId, ts, preview)
                            } else {
                                com.privimemobile.chat.ChatService.db!!.conversationDao().updateLastMessage(fileConvId, ts, preview)
                                com.privimemobile.chat.ChatService.sbbs.sendWithRetry(resolvedWalletId!!, payload)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ChatScreen", "File send error: ${e.message}")
                    android.widget.Toast.makeText(context, e.message ?: "File send failed", android.widget.Toast.LENGTH_LONG).show()
                } finally {
                    uploading = false
                    pendingFile = null
                    pendingStickerMeta = null
                    setInputText("")
                    replyingTo = null
                }
            }
            return
        }

        // Regular text message
        if (trimmed.isEmpty()) return

        // Group mode — send via GroupManager
        if (isGroupMode && groupId != null) {
            val grpReplyText = replyingTo?.text?.take(200)?.ifEmpty { null }
            val grpTimer = oneShotTimer
            scope.launch {
                com.privimemobile.chat.ChatService.groups.sendGroupMessage(
                    groupId, trimmed, replyText = grpReplyText, ttl = grpTimer,
                )
            }
            setInputText("")
            replyingTo = null
            oneShotTimer = 0
            // Clear draft
            if (convId > 0L) {
                scope.launch { com.privimemobile.chat.ChatService.db?.conversationDao()?.setDraft(convId, null) }
            }
            return
        }

        val walletId = resolvedWalletId
        if (walletId.isNullOrEmpty()) return

        // Capture state BEFORE coroutine (state may be cleared by main thread before coroutine runs)
        val replyText = replyingTo?.text?.take(200)?.ifEmpty { null }
        val capturedTimer = oneShotTimer

        // Send via new chat system
        scope.launch {
            val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
            if (state?.myHandle != null) {
                val ts = System.currentTimeMillis() / 1000
                val payload = mutableMapOf<String, Any?>(
                    "v" to 1,
                    "t" to "dm",
                    "ts" to ts,
                    "from" to state.myHandle!!,
                    "to" to handle,
                    "dn" to (state.myDisplayName ?: ""),
                    "msg" to trimmed,
                )
                if (replyText != null) payload["reply"] = replyText
                // Disappearing message TTL — per-message one-shot takes priority over conversation-level
                if (capturedTimer > 0) payload["ttl"] = capturedTimer
                val expiresAt = if (capturedTimer > 0) ts + capturedTimer else 0L
                // Optimistic insert into DB — un-delete if conversation was tombstoned
                val convDb = com.privimemobile.chat.ChatService.db!!.conversationDao().getOrCreate(convKey, handle)
                if (convDb.deletedAtTs > 0) {
                    com.privimemobile.chat.ChatService.db!!.conversationDao().undelete(convDb.id)
                }
                val dedupKey = "$ts:${trimmed.hashCode().toString(16)}:true"
                val entity = com.privimemobile.chat.db.entities.MessageEntity(
                    conversationId = convDb.id,
                    text = trimmed,
                    timestamp = ts,
                    sent = true,
                    type = "dm",
                    senderHandle = state.myHandle,
                    sbbsDedupKey = dedupKey,
                    replyText = replyText,
                    expiresAt = expiresAt,
                )
                com.privimemobile.chat.ChatService.db!!.messageDao().insert(entity)
                com.privimemobile.chat.ChatService.db!!.conversationDao().updateLastMessage(convDb.id, ts, trimmed.take(100))

                // Send via SBBS
                com.privimemobile.chat.ChatService.sbbs.sendWithRetry(walletId, payload)
            }
        }

        setInputText("")
        replyingTo = null
        oneShotTimer = 0  // clear per-message timer after send
    }

    // Update conversation preview after message deletion
    suspend fun refreshConversationPreview(cid: Long) {
        val latest = com.privimemobile.chat.ChatService.db?.messageDao()?.getLatestMessage(cid)
        if (latest != null) {
            val preview = when (latest.type) {
                "tip" -> "Tip"
                "file" -> "\uD83D\uDCCE File"
                else -> latest.text?.take(100)
            }
            com.privimemobile.chat.ChatService.db?.conversationDao()?.updateLastMessage(cid, latest.timestamp, preview)
        } else {
            com.privimemobile.chat.ChatService.db?.conversationDao()?.updateLastMessage(cid, 0, null)
        }
    }

    // Download a file via IpfsTransport
    fun handleDownload(cid: String, keyHex: String, ivHex: String, mime: String, inlineData: String? = null) {
        downloadStatuses[cid] = "downloading"
        scope.launch {
            try {
                // Find attachment by CID
                val attachment = com.privimemobile.chat.ChatService.db?.attachmentDao()?.findByCid(cid)
                if (attachment != null) {
                    val path = com.privimemobile.chat.transport.IpfsTransport.downloadFile(
                        attachmentId = attachment.id,
                        ipfsCid = cid,
                        keyHex = keyHex,
                        ivHex = ivHex,
                        inlineData = attachment.inlineData ?: inlineData,
                    )
                    filePaths[cid] = path
                    downloadStatuses[cid] = "done"
                } else {
                    downloadStatuses[cid] = "error"
                }
            } catch (e: Exception) {
                downloadStatuses[cid] = "error"
            }
        }
    }

    val canSend = (inputText.text.isNotBlank() || pendingFile != null) && (isGroupMode || !resolvedWalletId.isNullOrEmpty())

    // BackHandler: intercept system back when overlays are visible
    BackHandler(enabled = selectionMode) { selectionMode = false; selectedIds.clear() }
    BackHandler(enabled = fullscreenImage != null) { fullscreenImage = null }
    BackHandler(enabled = showAttachPicker) { showAttachPicker = false }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg),
    ) {
        // Selection mode header bar
        if (selectionMode) {
            Surface(color = C.card, shadowElevation = 2.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { selectionMode = false; selectedIds.clear() }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel", tint = C.text, modifier = Modifier.size(22.dp))
                    }
                    Text(
                        "${selectedIds.size} selected",
                        color = C.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    // Delete selected — show confirmation dialog
                    IconButton(onClick = {
                        pendingDeleteIds = selectedIds.toList()
                        showDeleteConfirmDialog = true
                    }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Delete, "Delete", tint = C.error, modifier = Modifier.size(22.dp))
                    }
                    // Forward selected — forward ALL selected messages (not just first)
                    IconButton(onClick = {
                        val msgsToForward = messages.filter { it.id in selectedIds && (it.text.isNotEmpty() || it.file != null) }
                        if (msgsToForward.isNotEmpty()) {
                            // Use first message to trigger forward dialog; store all in forwardingMsgs
                            forwardingMsgs = msgsToForward
                            forwardingMsg = msgsToForward.first()
                        }
                        selectionMode = false
                        selectedIds.clear()
                    }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Forward", tint = C.accent, modifier = Modifier.size(22.dp))
                    }
                }
            }
        } else {
        // Header — Telegram-style with avatar + back arrow
        Surface(
            color = C.card,
            shadowElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = C.text, modifier = Modifier.size(22.dp))
                }
                if (isGroupMode) {
                    // Group avatar — custom image or default icon
                    val groupAvatarBmp = remember(groupId, group?.avatarHash) {
                        try {
                            val f = java.io.File(context.filesDir, "group_avatars/$groupId.webp")
                            if (f.exists()) android.graphics.BitmapFactory.decodeFile(f.absolutePath) else null
                        } catch (_: Exception) { null }
                    }
                    if (groupAvatarBmp != null) {
                        androidx.compose.foundation.Image(
                            bitmap = groupAvatarBmp.asImageBitmap(),
                            contentDescription = "Group",
                            modifier = Modifier.size(38.dp).clip(CircleShape).clickable { onGroupSettings() },
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(38.dp).background(C.accent, CircleShape).clickable { onGroupSettings() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Group, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                } else {
                // Avatar
                val avatarKey = handle
                val avatarColors = listOf(
                    Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFEF5350), Color(0xFFAB47BC),
                    Color(0xFF42A5F5), Color(0xFFFF7043), Color(0xFF66BB6A), Color(0xFFEC407A),
                )
                val avatarBg = avatarColors[kotlin.math.abs(avatarKey.hashCode()) % avatarColors.size]
                val initial = (resolvedName.removePrefix("@")).firstOrNull()?.uppercase() ?: "?"
                Box(
                    modifier = Modifier.clickable { onContactInfo() },
                ) {
                    com.privimemobile.ui.components.AvatarDisplay(
                        handle = handle,
                        displayName = resolvedName,
                        size = 38.dp,
                    )
                }
                }
                Spacer(Modifier.width(10.dp))
                Column(
                    modifier = Modifier.weight(1f).clickable {
                        if (isGroupMode) onGroupSettings() else onContactInfo()
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isGroupMode && group?.isPublic == false) {
                            Icon(Icons.Default.Lock, "Private", tint = C.textSecondary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            resolvedName,
                            color = C.text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (isGroupMode) {
                        val typingVer2 by com.privimemobile.chat.ChatService.typingVersion.collectAsState()
                        val groupTypers = if (typingVer2 >= 0) com.privimemobile.chat.ChatService.getGroupTyping(convKey) else emptyList()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (groupTypers.isNotEmpty()) {
                                val typingText = if (groupTypers.size == 1) "@${groupTypers[0]} is typing"
                                    else if (groupTypers.size == 2) "@${groupTypers[0]} and @${groupTypers[1]} are typing"
                                    else "${groupTypers.size} people are typing"
                                Text(typingText, color = C.accent, fontSize = 12.sp)
                                val infiniteTransition = rememberInfiniteTransition(label = "grpTypingDots")
                                repeat(3) { i ->
                                    val offsetY by infiniteTransition.animateFloat(
                                        initialValue = 0f, targetValue = -3f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(400, easing = FastOutSlowInEasing, delayMillis = i * 120),
                                            repeatMode = RepeatMode.Reverse,
                                        ), label = "grpDot$i",
                                    )
                                    Text(".", color = C.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.offset(y = offsetY.dp))
                                }
                            } else {
                                Text("$groupMemberCount members", color = C.textSecondary, fontSize = 12.sp)
                            }
                            if (group?.muted == true) {
                                Icon(Icons.Default.NotificationsOff, "Muted", tint = C.textSecondary,
                                    modifier = Modifier.padding(start = 4.dp).size(13.dp))
                            }
                        }
                    } else {
                    val typingVer by com.privimemobile.chat.ChatService.typingVersion.collectAsState()
                    val peerTyping = typingVer >= 0 && com.privimemobile.chat.ChatService.isTyping(convKey)
                    if (peerTyping) {
                        // Bouncing dots animation (Telegram-style)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("typing", color = C.accent, fontSize = 12.sp)
                            val infiniteTransition = rememberInfiniteTransition(label = "typingDots")
                            repeat(3) { i ->
                                val offsetY by infiniteTransition.animateFloat(
                                    initialValue = 0f, targetValue = -3f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(400, easing = FastOutSlowInEasing, delayMillis = i * 120),
                                        repeatMode = RepeatMode.Reverse,
                                    ), label = "dot$i",
                                )
                                Text(
                                    ".",
                                    color = C.accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.offset(y = offsetY.dp),
                                )
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("@$handle", color = C.textSecondary, fontSize = 12.sp)
                            if (conv?.muted == true) {
                                Icon(Icons.Default.NotificationsOff, "Muted", tint = C.textSecondary,
                                    modifier = Modifier.padding(start = 4.dp).size(13.dp))
                            }
                            if (conv?.isBlocked == true) {
                                Icon(Icons.Default.Block, "Blocked", tint = Color(0xFFEF5350),
                                    modifier = Modifier.padding(start = 4.dp).size(13.dp))
                            }
                        }
                    }
                    } // end !isGroupMode else
                }
                // 3-dot overflow menu (animated rotation + smooth dropdown)
                Box {
                    val menuRotation by animateFloatAsState(
                        targetValue = if (showOverflowMenu) 90f else 0f,
                        animationSpec = tween(200),
                        label = "menuRotation",
                    )
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            showOverflowMenu = !showOverflowMenu
                        },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Menu",
                            tint = if (showOverflowMenu) C.accent else C.textSecondary,
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer { rotationZ = menuRotation },
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                        modifier = Modifier.background(C.card).widthIn(min = 200.dp),
                    ) {
                        @Composable fun OverflowItem(icon: String, label: String, color: Color = C.text, action: () -> Unit) {
                            var pressed by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (pressed) C.accent.copy(alpha = 0.15f) else Color.Transparent)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                                            onTap = { action() },
                                        )
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(icon, fontSize = 16.sp)
                                Spacer(Modifier.width(14.dp))
                                Text(label, color = color, fontSize = 15.sp)
                            }
                        }
                        OverflowItem("\uD83D\uDD0D", if (showSearch) "Close Search" else "Search") {
                            showSearch = !showSearch
                            if (!showSearch) { searchQuery = ""; searchResults = emptyList(); searchHighlightTs = null }
                            showOverflowMenu = false
                        }
                        if (!isGroupMode) {
                            OverflowItem("\uD83D\uDDBC", "Media") { showOverflowMenu = false; onMediaGallery() }
                        }
                        if (isGroupMode) {
                            OverflowItem("\u2699\uFE0F", "Group info") { showOverflowMenu = false; onGroupSettings() }
                        } else {
                            OverflowItem("\uD83D\uDC64", "View profile") { showOverflowMenu = false; onContactInfo() }
                        }
                        OverflowItem("\uD83C\uDFA8", "Wallpaper") { showOverflowMenu = false; showWallpaperPicker = true }
                        OverflowItem("\uD83D\uDCE4", "Export chat") {
                            showOverflowMenu = false
                            val exportTitle = if (isGroupMode) "PriviMe Group — ${group?.name ?: "Group"}" else "PriviMe Chat — @$handle"
                            scope.launch {
                                val sb = StringBuilder()
                                sb.appendLine(exportTitle)
                                sb.appendLine("Exported: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}")
                                sb.appendLine("---")
                                messages.forEach { msg ->
                                    val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                                        .format(java.util.Date(msg.timestamp * 1000))
                                    val sender = if (msg.sent) "You" else "@${msg.from}"
                                    val text = when {
                                        msg.isTip -> "[Tip: ${Helpers.formatBeam(msg.tipAmount)} ${com.privimemobile.wallet.assetTicker(msg.tipAssetId)}] ${msg.text}"
                                        msg.type == "file" -> "[File: ${msg.file?.name ?: "file"}] ${msg.text}"
                                        else -> msg.text
                                    }
                                    sb.appendLine("[$time] $sender: $text")
                                }
                                withContext(Dispatchers.Main) {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, sb.toString())
                                        putExtra(android.content.Intent.EXTRA_SUBJECT, exportTitle)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(intent, "Export chat"))
                                }
                            }
                        }
                        val isMuted = if (isGroupMode) group?.muted == true else conv?.muted == true
                        OverflowItem(if (isMuted) "\uD83D\uDD14" else "\uD83D\uDD07", if (isMuted) "Unmute" else "Mute") {
                            scope.launch {
                                if (isGroupMode && groupId != null) {
                                    com.privimemobile.chat.ChatService.db?.groupDao()?.setMuted(groupId, !isMuted)
                                } else if (convId > 0L) {
                                    com.privimemobile.chat.ChatService.db?.conversationDao()?.setMuted(convId, !isMuted)
                                }
                            }
                            showOverflowMenu = false
                        }
                        if (!isGroupMode) {
                            val isBlocked = conv?.isBlocked == true
                            OverflowItem(if (isBlocked) "\u2705" else "\uD83D\uDEAB", if (isBlocked) "Unblock" else "Block", color = if (isBlocked) C.text else C.error) {
                                if (convId > 0L) { scope.launch { com.privimemobile.chat.ChatService.db?.conversationDao()?.setBlocked(convId, !isBlocked) } }
                                showOverflowMenu = false
                            }
                        }
                        HorizontalDivider(color = C.border)
                        OverflowItem("\uD83D\uDDD1", "Clear history", color = C.error) { showOverflowMenu = false; showClearConfirm = true }
                        OverflowItem("\u274C", if (isGroupMode) "Leave group" else "Delete chat", color = C.error) { showOverflowMenu = false; showDeleteConfirm = true }
                    }
                }
            }
        }
        } // close else (normal header vs selection header)

        // In-chat search bar
        if (showSearch) {
            Surface(color = C.card) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { query ->
                            searchQuery = query
                            searchJob?.cancel()
                            if (query.isBlank()) {
                                searchResults = emptyList()
                                searchHighlightTs = null
                                return@OutlinedTextField
                            }
                            searchJob = scope.launch {
                                delay(300) // debounce
                                val results = com.privimemobile.chat.ChatService.db?.messageDao()
                                    ?.searchInConversation(convId, "%${query.trim()}%") ?: emptyList()
                                searchResults = results
                            }
                        },
                        placeholder = { Text("Search in chat...", color = C.textMuted) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = C.accent, unfocusedBorderColor = C.border,
                            focusedContainerColor = C.bg, unfocusedContainerColor = C.bg,
                            cursorColor = C.accent, focusedTextColor = C.text, unfocusedTextColor = C.text,
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                    )
                    if (searchResults.isNotEmpty()) {
                        Text(
                            "${searchResults.size}",
                            color = C.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
            // Search results list
            if (searchResults.isNotEmpty()) {
                Surface(color = C.card.copy(alpha = 0.95f)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                    ) {
                        items(searchResults, key = { it.id }) { result ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // Find index in reversed messages and scroll to it
                                        val reversedMessages = messages.reversed()
                                        val idx = reversedMessages.indexOfFirst { it.timestamp == result.timestamp }
                                        if (idx >= 0) {
                                            searchHighlightTs = result.timestamp
                                            scope.launch {
                                                listState.animateScrollToItem(idx)
                                                // Auto-clear highlight after 2s
                                                delay(2000)
                                                searchHighlightTs = null
                                            }
                                        }
                                        showSearch = false
                                        searchQuery = ""
                                        searchResults = emptyList()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        result.text?.take(80) ?: "",
                                        color = C.text, fontSize = 13.sp, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        formatMessageTime(result.timestamp),
                                        color = C.textSecondary, fontSize = 11.sp,
                                    )
                                }
                                if (result.sent) {
                                    Text("You", color = C.textSecondary, fontSize = 11.sp)
                                }
                            }
                            HorizontalDivider(color = C.border, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        // Pinned messages bar — scroll-aware, ordered by pin time
        val pinnedByOrder = messages.filter { it.pinned }.sortedBy { it.pinnedAt }  // #1 = first pinned
        var pinHighlightTs by remember { mutableStateOf(0L) }
        var showPinListDialog by remember { mutableStateOf(false) }
        if (pinnedByOrder.isNotEmpty()) {
            // Scroll-aware pin index (default)
            val scrollAwarePinIndex by remember(pinnedByOrder) {
                derivedStateOf {
                    val visibleIdx = listState.firstVisibleItemIndex
                    val revMsgs = messages.reversed()
                    val pinPositions = pinnedByOrder.mapIndexedNotNull { pmIdx, pin ->
                        val lcIdx = revMsgs.indexOfFirst { it.timestamp == pin.timestamp && it.id == pin.id }
                        if (lcIdx >= 0) pmIdx to lcIdx else null
                    }
                    if (pinPositions.isEmpty()) 0
                    else {
                        val nextPin = pinPositions.filter { it.second >= visibleIdx }.minByOrNull { it.second }
                        nextPin?.first ?: pinPositions.maxByOrNull { it.second }?.first ?: 0
                    }
                }
            }

            // Manual override from tap — cleared only on user-initiated scroll
            var manualOverrideIndex by remember { mutableStateOf(-1) }
            var scrollPosAtOverride by remember { mutableStateOf(-1) }
            // Clear override when user scrolls AWAY from where the tap-scroll landed
            LaunchedEffect(listState.firstVisibleItemIndex) {
                if (manualOverrideIndex >= 0 && scrollPosAtOverride >= 0) {
                    // Wait for the programmatic scroll to finish first
                    delay(1000)
                    // Only clear if user has actually scrolled from the landing position
                    if (listState.firstVisibleItemIndex != scrollPosAtOverride) {
                        manualOverrideIndex = -1
                        scrollPosAtOverride = -1
                    }
                }
            }

            val safeIndex = if (manualOverrideIndex >= 0)
                manualOverrideIndex.coerceIn(0, pinnedByOrder.size - 1)
            else
                scrollAwarePinIndex.coerceIn(0, pinnedByOrder.size - 1)
            val currentPin = pinnedByOrder[safeIndex]

            fun scrollToPinMsg(pin: ChatMessage) {
                val revMsgs = messages.reversed()
                val idx = revMsgs.indexOfFirst { it.timestamp == pin.timestamp && it.id == pin.id }
                if (idx >= 0) {
                    pinHighlightTs = pin.timestamp
                    scope.launch {
                        listState.animateScrollToItem(idx)
                        delay(2000)
                        pinHighlightTs = 0L
                    }
                }
            }

            Surface(
                color = C.card,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .clickable {
                            scrollToPinMsg(currentPin)
                            // Set manual override to previous pin (stop at #1, don't cycle)
                            manualOverrideIndex = if (safeIndex > 0) safeIndex - 1 else 0
                            // Record where scroll will land so we only clear on USER scroll
                            val revMsgs = messages.reversed()
                            scrollPosAtOverride = revMsgs.indexOfFirst { it.timestamp == currentPin.timestamp && it.id == currentPin.id }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Accent segment bar
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val segments = pinnedByOrder.size.coerceAtMost(5)
                        val segH = (26 / segments).coerceAtLeast(3)
                        repeat(segments) { i ->
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(segH.dp)
                                    .padding(vertical = 0.5.dp)
                                    .background(if (i == safeIndex % segments) C.accent else C.accent.copy(alpha = 0.25f))
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(
                            if (pinnedByOrder.size > 1) "Pinned Message #${safeIndex + 1}" else "Pinned Message",
                            color = C.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            currentPin.text.ifEmpty { if (currentPin.file != null) "\uD83D\uDCCE File" else "Message" },
                            color = C.textSecondary, fontSize = 13.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // Pin list icon — opens full pin list screen
                    IconButton(
                        onClick = { showPinListDialog = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Default.PushPin, "All pins", tint = C.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Pin list dialog (full screen style)
            if (showPinListDialog) {
                AlertDialog(
                    onDismissRequest = { showPinListDialog = false },
                    containerColor = C.card,
                    title = {
                        Text("Pinned Messages (${pinnedByOrder.size})", color = C.text, fontWeight = FontWeight.SemiBold)
                    },
                    text = {
                        Column(modifier = Modifier.heightIn(max = 400.dp)) {
                            LazyColumn {
                                items(pinnedByOrder.size) { idx ->
                                    val pin = pinnedByOrder[idx]
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // Pin number
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(C.accent.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text("#${idx + 1}", color = C.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        // Message preview
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                pin.text.ifEmpty { if (pin.file != null) "\uD83D\uDCCE File" else "Message" },
                                                color = C.text, fontSize = 14.sp,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                formatMessageTime(pin.timestamp),
                                                color = C.textMuted, fontSize = 11.sp,
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        // Navigate to message button (chat bubble with arrow)
                                        IconButton(
                                            onClick = {
                                                showPinListDialog = false
                                                scrollToPinMsg(pin)
                                            },
                                            modifier = Modifier.size(32.dp),
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowForward,
                                                "Go to message",
                                                tint = C.accent,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                    if (idx < pinnedByOrder.size - 1) {
                                        HorizontalDivider(color = C.border.copy(alpha = 0.3f))
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        // Unpin all — only admin/creator in group mode, always in DM
                        val canUnpin = !isGroupMode || (group?.myRole ?: 0) >= 1
                        if (canUnpin) {
                            TextButton(onClick = {
                                scope.launch {
                                    if (convId > 0L) {
                                        com.privimemobile.chat.ChatService.db?.messageDao()?.unpinAll(convId)
                                    }
                                }
                                showPinListDialog = false
                            }) {
                                Text("Unpin All", color = C.error, fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPinListDialog = false }) {
                            Text("Close", color = C.textSecondary)
                        }
                    },
                )
            }
        }

        // Messages list + scroll-to-bottom button
        val wallpaperBg = when {
            chatWallpaper.startsWith("custom:") -> {
                val path = chatWallpaper.removePrefix("custom:").substringBefore("#")
                val file = java.io.File(path)
                if (file.exists()) {
                    // Cache decoded bitmap to avoid re-decoding on every recomposition
                    val cachedBmp = remember(chatWallpaper) { android.graphics.BitmapFactory.decodeFile(path) }
                    if (cachedBmp != null) {
                        Modifier.drawBehind {
                            // Center-crop: scale to fill, crop overflow
                            val bw = cachedBmp.width.toFloat()
                            val bh = cachedBmp.height.toFloat()
                            val cw = size.width
                            val ch = size.height
                            val scale = maxOf(cw / bw, ch / bh)
                            val sw = (cw / scale).toInt()
                            val sh = (ch / scale).toInt()
                            val sx = ((bw - sw) / 2).toInt().coerceAtLeast(0)
                            val sy = ((bh - sh) / 2).toInt().coerceAtLeast(0)
                            val srcRect = android.graphics.Rect(sx, sy, sx + sw, sy + sh)
                            val dstRect = android.graphics.Rect(0, 0, cw.toInt(), ch.toInt())
                            val paint = android.graphics.Paint().apply { isFilterBitmap = true }
                            drawContext.canvas.nativeCanvas.drawBitmap(cachedBmp, srcRect, dstRect, paint)
                        }
                    } else Modifier
                } else Modifier
            }
            chatWallpaper == "dark_blue" -> Modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF0D1B2A), Color(0xFF1B2838))))
            chatWallpaper == "teal" -> Modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF004D40), Color(0xFF00695C))))
            chatWallpaper == "purple" -> Modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF1A0033), Color(0xFF2D1B69))))
            chatWallpaper == "midnight" -> Modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF0F0F23), Color(0xFF1A1A3E))))
            chatWallpaper == "forest" -> Modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF1B3A2D), Color(0xFF2D5016))))
            chatWallpaper == "sunset" -> Modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF2D1B00), Color(0xFF4A2600))))
            else -> Modifier  // default uses C.bg from parent
        }
        // Pre-compute album groups outside LazyColumn (composable context)
        val reversedMessages = remember(messages) { messages.reversed() }
        val albumGroups = remember(reversedMessages) {
                val groups = mutableMapOf<String, List<String>>() // first msg id → list of all msg ids in album
                val skipIds = mutableSetOf<String>()
                for (i in reversedMessages.indices) {
                    if (reversedMessages[i].id in skipIds) continue
                    val msg = reversedMessages[i]
                    if (msg.type != "file" || msg.file == null || !com.privimemobile.protocol.Helpers.isImageMime(msg.file.mime)) continue
                    // Collect consecutive images from same sender
                    val albumIds = mutableListOf(msg.id)
                    var j = i + 1
                    while (j < reversedMessages.size) {
                        val next = reversedMessages[j]
                        if (next.type == "file" && next.file != null &&
                            com.privimemobile.protocol.Helpers.isImageMime(next.file.mime) &&
                            next.sent == msg.sent &&
                            kotlin.math.abs(next.timestamp - msg.timestamp) < 60
                        ) {
                            albumIds.add(next.id)
                            skipIds.add(next.id)
                            j++
                        } else break
                    }
                    if (albumIds.size > 1) {
                        groups[msg.id] = albumIds
                    }
                }
                groups
            }
        val albumSkipIds = remember(albumGroups) {
            albumGroups.values.flatMap { it.drop(1) }.toSet()
        }

        Box(modifier = Modifier.weight(1f).then(wallpaperBg)) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            state = listState,
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(reversedMessages, key = { it.id }) { msg ->
                // Skip non-first album images (rendered as grid in the first item)
                if (msg.id in albumSkipIds) return@items
                // Message appear animation
                var appeared by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { appeared = true }
                val alpha by animateFloatAsState(
                    targetValue = if (appeared) 1f else 0f,
                    animationSpec = tween(200),
                    label = "msgAlpha",
                )
                val offsetY by animateFloatAsState(
                    targetValue = if (appeared) 0f else 20f,
                    animationSpec = tween(200),
                    label = "msgOffset",
                )

                val index = reversedMessages.indexOf(msg)
                val prevMsg = if (index < reversedMessages.size - 1) reversedMessages[index + 1] else null
                val showDateSep = prevMsg == null ||
                        formatDateSeparator(msg.timestamp) != formatDateSeparator(prevMsg.timestamp)

                Column(
                    modifier = Modifier
                        .animateItem()
                        .graphicsLayer {
                            this.alpha = alpha
                            translationY = offsetY
                        },
                ) {
                    if (showDateSep) {
                        // Centered pill date separator — tap to jump to date
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = C.card.copy(alpha = 0.8f),
                                modifier = Modifier.clickable { showDatePicker = true },
                            ) {
                                Text(
                                    formatDateSeparator(msg.timestamp),
                                    color = C.textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }

                    // "X new messages" unread divider
                    if (initialUnreadCount > 0 && index == initialUnreadCount - 1 && !msg.sent) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = C.accent.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    "$initialUnreadCount new message${if (initialUnreadCount > 1) "s" else ""}",
                                    color = C.accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }

                    // Selection mode: checkbox + tap toggles selection
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (selectionMode) {
                            Checkbox(
                                checked = msg.id in selectedIds,
                                onCheckedChange = {
                                    if (msg.id in selectedIds) selectedIds.remove(msg.id)
                                    else selectedIds.add(msg.id)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = C.accent,
                                    uncheckedColor = C.textSecondary,
                                    checkmarkColor = C.textDark,
                                ),
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    // Album grid for grouped consecutive images
                    val albumIds = albumGroups[msg.id]
                    if (albumIds != null && albumIds.size > 1) {
                        val albumMsgs = albumIds.mapNotNull { id -> reversedMessages.firstOrNull { it.id == id } }
                        val isMine = msg.sent
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
                        ) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = if (isMine) Color(0xFF1B3A4B) else C.card),
                                modifier = Modifier.widthIn(max = 280.dp),
                            ) {
                                // 2-column grid
                                val columns = 2
                                albumMsgs.chunked(columns).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        row.forEach { albumMsg ->
                                            val fp = filePaths[albumMsg.file?.cid ?: ""]
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1f)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .clickable {
                                                        if (fp != null) fullscreenImage = Pair(fp, albumMsg.file?.name ?: "Image")
                                                    },
                                            ) {
                                                if (fp != null) {
                                                    AsyncImage(
                                                        model = java.io.File(fp),
                                                        contentDescription = "Photo",
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize(),
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize().background(C.bg),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(20.dp),
                                                            color = C.accent, strokeWidth = 2.dp,
                                                        )
                                                    }
                                                    // Trigger download
                                                    val cid = albumMsg.file?.cid ?: ""
                                                    if (cid.isNotEmpty() && downloadStatuses[cid] != "downloading") {
                                                        LaunchedEffect(cid) {
                                                            handleDownload(
                                                                cid,
                                                                albumMsg.file?.key ?: "",
                                                                albumMsg.file?.iv ?: "",
                                                                albumMsg.file?.mime ?: "image/jpeg",
                                                                albumMsg.file?.data,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        // Pad incomplete rows
                                        if (row.size < columns) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                                // Time + status row
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("${albumMsgs.size} photos", color = C.textMuted, fontSize = 10.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(formatMessageTime(msg.timestamp), color = C.textSecondary, fontSize = 10.sp)
                                    if (isMine) {
                                        Spacer(Modifier.width(6.dp))
                                        val lastMsg = albumMsgs.last()
                                        TickIndicator(read = lastMsg.read, delivered = lastMsg.delivered)
                                    }
                                }
                            }
                        }
                    } else
                    MessageBubble(
                        msg = msg,
                        filePath = filePaths[msg.file?.cid ?: ""],
                        downloadStatus = downloadStatuses[msg.file?.cid ?: ""] ?: "idle",
                        onDownload = { cid, key, iv, mime, data ->
                            handleDownload(cid, key, iv, mime, data)
                        },
                        onReply = if (selectionMode) {{ /* no-op in selection mode */ }} else {{ replyingTo = msg }},
                        onTap = {
                            if (selectionMode) {
                                if (msg.id in selectedIds) selectedIds.remove(msg.id)
                                else selectedIds.add(msg.id)
                            } else if (msg.type == "sticker" && msg.stickerPackId != null) {
                                // Sticker tap → view pack
                                viewPackId = msg.stickerPackId
                            } else {
                                contextMenuMsg = msg
                            }
                        },
                        onLongPress = {
                            if (!selectionMode) {
                                if (msg.type == "sticker") {
                                    contextMenuMsg = msg
                                } else {
                                    selectionMode = true
                                    selectedIds.clear()
                                    selectedIds.add(msg.id)
                                }
                            } else {
                                if (msg.id in selectedIds) selectedIds.remove(msg.id)
                                else selectedIds.add(msg.id)
                            }
                        },
                        onFullscreenImage = {
                            val fp = filePaths[msg.file?.cid ?: ""]
                            if (fp != null) fullscreenImage = Pair(fp, msg.file?.name ?: "Image")
                        },
                        isSelected = selectionMode && msg.id in selectedIds,
                        onPollVote = { optIdx ->
                            val now = System.currentTimeMillis()
                            if (now - lastSendTime < 3000) {
                                Toast.makeText(context, "Wait a moment...", Toast.LENGTH_SHORT).show()
                                return@MessageBubble
                            }
                            lastSendTime = now
                            scope.launch {
                                val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
                                if (state?.myHandle != null && msg.pollData != null) {
                                    val pollObj = org.json.JSONObject(msg.pollData)
                                    val options = pollObj.optJSONArray("options")
                                    if (options != null && optIdx < options.length()) {
                                        val myHandle = state.myHandle!!
                                        // Check if tapping the same option (unvote)
                                        val tappedOpt = options.getJSONObject(optIdx)
                                        val tappedVoters = tappedOpt.optJSONArray("voters") ?: org.json.JSONArray()
                                        var isUnvote = false
                                        for (i in 0 until tappedVoters.length()) {
                                            if (tappedVoters.getString(i) == myHandle) { isUnvote = true; break }
                                        }

                                        if (isUnvote) {
                                            // Remove vote from this option
                                            val newVoters = org.json.JSONArray()
                                            for (i in 0 until tappedVoters.length()) {
                                                if (tappedVoters.getString(i) != myHandle) newVoters.put(tappedVoters.getString(i))
                                            }
                                            tappedOpt.put("voters", newVoters)
                                            options.put(optIdx, tappedOpt)
                                        } else {
                                            // Single choice: remove vote from ALL other options first
                                            for (j in 0 until options.length()) {
                                                val o = options.getJSONObject(j)
                                                val v = o.optJSONArray("voters") ?: org.json.JSONArray()
                                                val cleaned = org.json.JSONArray()
                                                for (i in 0 until v.length()) {
                                                    if (v.getString(i) != myHandle) cleaned.put(v.getString(i))
                                                }
                                                o.put("voters", cleaned)
                                                options.put(j, o)
                                            }
                                            // Add vote to tapped option
                                            val voters = options.getJSONObject(optIdx).optJSONArray("voters") ?: org.json.JSONArray()
                                            voters.put(myHandle)
                                            options.getJSONObject(optIdx).put("voters", voters)
                                        }

                                        pollObj.put("options", options)
                                        com.privimemobile.chat.ChatService.db?.messageDao()?.updatePollData(
                                            msg.id.toLong(), pollObj.toString()
                                        )
                                        // Send vote/unvote via SBBS
                                        val votePayload = mapOf(
                                            "v" to 1, "t" to if (isUnvote) "poll_unvote" else "poll_vote",
                                            "ts" to System.currentTimeMillis() / 1000,
                                            "from" to myHandle, "to" to (if (isGroupMode) groupId!! else handle),
                                            "msg_ts" to msg.timestamp,
                                            "option" to optIdx,
                                        )
                                        if (isGroupMode && groupId != null) {
                                            com.privimemobile.chat.ChatService.groups.sendGroupPayload(groupId, votePayload)
                                        } else {
                                            val walletId = resolvedWalletId
                                            if (!walletId.isNullOrEmpty()) {
                                                com.privimemobile.chat.ChatService.sbbs.sendWithRetry(walletId, votePayload)
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        myHandle = myHandle,
                        reactions = reactionMap[msg.timestamp] ?: emptyList(),
                        onRemoveReaction = { emoji, msgTs ->
                            scope.launch {
                                if (myHandle != null) {
                                    // Soft-delete locally (stamp removal time for re-delivery detection)
                                    val nowTs = System.currentTimeMillis() / 1000
                                    com.privimemobile.chat.ChatService.db!!.reactionDao().remove(msgTs, myHandle, emoji, nowTs)
                                    // Send unreact SBBS to remove on other side
                                    val unreactPayload = mapOf(
                                        "v" to 1,
                                        "t" to "unreact",
                                        "ts" to System.currentTimeMillis() / 1000,
                                        "from" to myHandle,
                                        "to" to (if (isGroupMode) groupId!! else handle),
                                        "msg_ts" to msgTs,
                                        "emoji" to emoji,
                                    )
                                    if (isGroupMode && groupId != null) {
                                        com.privimemobile.chat.ChatService.groups.sendGroupPayload(groupId, unreactPayload)
                                    } else {
                                        val walletId = resolvedWalletId
                                        if (!walletId.isNullOrEmpty()) {
                                            com.privimemobile.chat.ChatService.sbbs.sendWithRetry(walletId, unreactPayload)
                                        }
                                    }
                                }
                            }
                        },
                        isHighlighted = searchHighlightTs == msg.timestamp || searchHighlightFromNav == msg.timestamp || pinHighlightTs == msg.timestamp,
                        isGroupMode = isGroupMode,
                        onSenderTap = { senderHandle ->
                            if (senderHandle.isNotEmpty()) onViewContact(senderHandle)
                        },
                        groupMemberNames = groupMemberNames,
                    )
                    } // close Row (selection mode wrapper)
                }
            }
        }

        // Pending file preview bar
        if (pendingFile != null) {
            Surface(
                color = C.card,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (Helpers.isImageMime(pendingFile!!.mimeType)) "\uD83D\uDDBC" else "\uD83D\uDCCE",
                        fontSize = 20.sp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${pendingFile!!.name} (${Helpers.formatFileSize(pendingFile!!.size)})",
                        color = C.text,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { pendingFile = null }) {
                        Text("X", color = C.error, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Scroll-to-bottom FAB
        val showScrollButton by remember {
            derivedStateOf { listState.firstVisibleItemIndex > 3 }
        }
        if (showScrollButton) {
            SmallFloatingActionButton(
                onClick = {
                    scope.launch { listState.animateScrollToItem(0) }
                },
                containerColor = C.card,
                contentColor = C.text,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 12.dp),
            ) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Scroll to bottom",
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        } // end Box wrapper

        // Emoji picker panel (Telegram-style: recent + scrollable categories, 9 columns)
        if (showEmojiPicker) {
            // Track recent emojis in SharedPreferences
            val recentPrefs = context.getSharedPreferences("emoji_recent", Context.MODE_PRIVATE)
            val recentEmojis = remember {
                mutableStateListOf<String>().apply {
                    addAll(recentPrefs.getString("recent", "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList())
                }
            }
            fun addRecent(emoji: String) {
                recentEmojis.remove(emoji)
                recentEmojis.add(0, emoji)
                if (recentEmojis.size > 32) recentEmojis.removeRange(32, recentEmojis.size)
                recentPrefs.edit().putString("recent", recentEmojis.joinToString(",")).apply()
            }
            fun insertEmoji(emoji: String) {
                val current = inputText.text
                val sel = inputText.selection.start
                setInputText(current.substring(0, sel) + emoji + current.substring(sel))
                addRecent(emoji)
            }

            // All emoji categories
            val allCategories = listOf(
                "Emoji & People" to listOf(
                    "\uD83D\uDE00", "\uD83D\uDE03", "\uD83D\uDE04", "\uD83D\uDE01", "\uD83D\uDE06", "\uD83D\uDE05", "\uD83D\uDE02", "\uD83E\uDD23", "\uD83D\uDE0A",
                    "\uD83D\uDE07", "\uD83D\uDE42", "\uD83D\uDE43", "\uD83D\uDE09", "\uD83D\uDE0C", "\uD83D\uDE0D", "\uD83E\uDD70", "\uD83D\uDE18", "\uD83D\uDE17",
                    "\uD83D\uDE19", "\uD83D\uDE1A", "\uD83D\uDE0B", "\uD83D\uDE1B", "\uD83D\uDE1C", "\uD83E\uDD2A", "\uD83D\uDE1D", "\uD83E\uDD11", "\uD83E\uDD17",
                    "\uD83E\uDD2D", "\uD83E\uDD2B", "\uD83E\uDD14", "\uD83E\uDD10", "\uD83E\uDD28", "\uD83D\uDE10", "\uD83D\uDE11", "\uD83D\uDE36", "\uD83D\uDE0F",
                    "\uD83D\uDE12", "\uD83D\uDE44", "\uD83D\uDE2C", "\uD83E\uDD25", "\uD83D\uDE0E", "\uD83E\uDD13", "\uD83E\uDD78", "\uD83E\uDD21", "\uD83D\uDE34",
                    "\uD83D\uDE2A", "\uD83D\uDE31", "\uD83D\uDE28", "\uD83D\uDE30", "\uD83D\uDE25", "\uD83D\uDE22", "\uD83D\uDE2D", "\uD83D\uDE24", "\uD83D\uDE21",
                    "\uD83D\uDE20", "\uD83E\uDD2F", "\uD83D\uDE33", "\uD83E\uDD75", "\uD83E\uDD76", "\uD83D\uDE31", "\uD83D\uDE28", "\uD83E\uDD2E", "\uD83E\uDD27",
                    "\uD83D\uDE37", "\uD83E\uDD12", "\uD83E\uDD15", "\uD83D\uDE35", "\uD83E\uDD74", "\uD83E\uDD22", "\uD83D\uDC7F", "\uD83D\uDC79", "\uD83D\uDC7A",
                    "\uD83D\uDC80", "\uD83D\uDC7B", "\uD83D\uDC7D", "\uD83E\uDD16", "\uD83D\uDCA9", "\uD83D\uDE3A", "\uD83D\uDE38", "\uD83D\uDE39", "\uD83D\uDE3B",
                ),
                "Hands & Gestures" to listOf(
                    "\uD83D\uDC4D", "\uD83D\uDC4E", "\u270A", "\uD83D\uDC4A", "\uD83E\uDD1B", "\uD83E\uDD1C", "\uD83D\uDC4F", "\uD83D\uDE4C", "\uD83D\uDC50",
                    "\uD83E\uDD32", "\uD83E\uDD1D", "\uD83D\uDE4F", "\u270D\uFE0F", "\uD83D\uDC85", "\uD83E\uDD33", "\uD83D\uDCAA", "\uD83D\uDC4B", "\uD83E\uDD1A",
                    "\u270B", "\uD83D\uDC4C", "\uD83E\uDD0F", "\u270C\uFE0F", "\uD83E\uDD1E", "\uD83E\uDD1F", "\uD83E\uDD18", "\uD83E\uDD19", "\uD83D\uDC46",
                    "\uD83D\uDC47", "\uD83D\uDC48", "\uD83D\uDC49", "\uD83D\uDD95", "\uD83D\uDC4B", "\uD83E\uDEF6", "\uD83E\uDEF1", "\uD83E\uDEF2", "\uD83E\uDEF3",
                ),
                "Hearts & Symbols" to listOf(
                    "\u2764\uFE0F", "\uD83E\uDDE1", "\uD83D\uDC9B", "\uD83D\uDC9A", "\uD83D\uDC99", "\uD83D\uDC9C", "\uD83D\uDDA4", "\uD83D\uDC94",
                    "\uD83D\uDC95", "\uD83D\uDC9E", "\uD83D\uDC93", "\uD83D\uDC97", "\uD83D\uDC96", "\uD83D\uDC98", "\uD83D\uDC9D", "\u2B50", "\uD83C\uDF1F",
                    "\uD83D\uDCAB", "\u26A1", "\uD83D\uDD25", "\uD83D\uDCA5", "\uD83C\uDF89", "\uD83C\uDF8A", "\uD83C\uDFC6", "\uD83E\uDD47", "\uD83E\uDD48",
                    "\uD83E\uDD49", "\uD83D\uDCAF", "\uD83D\uDC8B", "\uD83D\uDCA4", "\uD83D\uDCA8", "\uD83C\uDF08", "\u2600\uFE0F", "\uD83C\uDF19", "\u2744\uFE0F",
                ),
                "Animals & Nature" to listOf(
                    "\uD83D\uDC36", "\uD83D\uDC31", "\uD83D\uDC2D", "\uD83D\uDC39", "\uD83D\uDC30", "\uD83E\uDD8A", "\uD83D\uDC3B", "\uD83D\uDC28",
                    "\uD83D\uDC2F", "\uD83E\uDD81", "\uD83D\uDC2E", "\uD83D\uDC37", "\uD83D\uDC38", "\uD83D\uDC35", "\uD83D\uDE48", "\uD83D\uDE49",
                    "\uD83D\uDE4A", "\uD83D\uDC27", "\uD83D\uDC26", "\uD83E\uDD85", "\uD83E\uDD86", "\uD83E\uDD89", "\uD83D\uDC3C", "\uD83D\uDC22",
                    "\uD83D\uDC0D", "\uD83E\uDD96", "\uD83D\uDC33", "\uD83D\uDC2C", "\uD83E\uDD8B", "\uD83C\uDF3A", "\uD83C\uDF39", "\uD83C\uDF3B",
                    "\uD83C\uDF3C", "\uD83C\uDF37", "\uD83C\uDF34", "\uD83C\uDF35", "\uD83C\uDF32", "\uD83C\uDF33", "\uD83C\uDF40", "\uD83C\uDF3F",
                ),
                "Food & Drink" to listOf(
                    "\uD83C\uDF4E", "\uD83C\uDF4A", "\uD83C\uDF4B", "\uD83C\uDF4C", "\uD83C\uDF49", "\uD83C\uDF47", "\uD83C\uDF53", "\uD83C\uDF48",
                    "\uD83C\uDF55", "\uD83C\uDF54", "\uD83C\uDF5F", "\uD83C\uDF2D", "\uD83C\uDF2E", "\uD83C\uDF2F", "\uD83C\uDF73", "\uD83C\uDF5E",
                    "\u2615", "\uD83C\uDF75", "\uD83C\uDF7A", "\uD83C\uDF77", "\uD83E\uDD42", "\uD83C\uDF78", "\uD83E\uDD64", "\uD83C\uDF70",
                    "\uD83C\uDF82", "\uD83C\uDF6B", "\uD83C\uDF6C", "\uD83C\uDF6D", "\uD83C\uDF6E", "\uD83C\uDF6F", "\uD83C\uDF7E", "\uD83E\uDD43",
                ),
                "Objects & Flags" to listOf(
                    "\uD83D\uDCF1", "\uD83D\uDCBB", "\uD83D\uDCF7", "\uD83C\uDFB5", "\uD83C\uDFB6", "\uD83C\uDFA4", "\uD83C\uDFAC", "\uD83D\uDCDA",
                    "\uD83D\uDD13", "\uD83D\uDD12", "\uD83D\uDD11", "\uD83D\uDCA1", "\uD83D\uDD0B", "\uD83D\uDCE7", "\uD83D\uDCE6", "\uD83D\uDCB0",
                    "\uD83D\uDCB3", "\uD83D\uDE97", "\uD83D\uDE95", "\uD83D\uDE8C", "\u2708\uFE0F", "\uD83D\uDE80", "\uD83C\uDFE0", "\uD83C\uDFEB",
                    "\uD83C\uDFE5", "\u26BD", "\uD83C\uDFC0", "\uD83C\uDFBE", "\uD83C\uDFAF", "\uD83C\uDFF3\uFE0F", "\uD83C\uDFF4", "\uD83C\uDDE6\uD83C\uDDFA",
                ),
            )

            // Category tab icons (matching Telegram)
            val categoryIcons = listOf("\uD83D\uDD53", "\uD83D\uDE00", "\uD83D\uDC4B", "\u2764\uFE0F", "\uD83D\uDC3B", "\uD83C\uDF54", "\uD83D\uDCF1", "\uD83C\uDFAD")
            // emojiMainTab declared at screen level for IME detection access
            val emojiGridState = rememberLazyGridState()

            // Pre-compute grid indices for each category header (for tab scrolling)
            val categoryGridIndices = remember(recentEmojis.size, allCategories) {
                val indices = mutableListOf<Int>()
                var idx = 0
                // Recent
                idx++ // "Recent" header
                idx += recentEmojis.size
                // Each category
                allCategories.forEachIndexed { catIdx, (_, emojis) ->
                    indices.add(idx) // category header position
                    idx++ // header
                    idx += emojis.size
                }
                indices
            }

            val screenHeight = LocalContext.current.resources.displayMetrics.heightPixels
            val panelHeight = (screenHeight * 0.40f / LocalContext.current.resources.displayMetrics.density).dp

            Surface(color = C.card) {
                Column(modifier = Modifier.height(panelHeight)) {
                    // Main tabs: Emoji | Stickers
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        // Category icon tabs (Telegram-style)
                        var activeTabIdx by remember { mutableStateOf(0) }
                        categoryIcons.forEachIndexed { idx, icon ->
                            val isActive = if (idx == categoryIcons.size - 1) emojiMainTab == 1 else emojiMainTab == 0 && activeTabIdx == idx
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (isActive) C.accent.copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable {
                                        if (idx == categoryIcons.size - 1) {
                                            emojiMainTab = 1 // Stickers tab
                                        } else {
                                            emojiMainTab = 0
                                            activeTabIdx = idx
                                            if (idx == 0) {
                                                // Recent — scroll to top
                                                scope.launch { emojiGridState.animateScrollToItem(0) }
                                            } else if (idx - 1 < categoryGridIndices.size) {
                                                // Scroll to category header
                                                scope.launch { emojiGridState.animateScrollToItem(categoryGridIndices[idx - 1]) }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(icon, fontSize = 20.sp)
                            }
                        }
                    }

                    HorizontalDivider(color = C.border.copy(alpha = 0.3f))

                    if (emojiMainTab == 0) {
                        // Emoji tab — single scrollable list with recent + all categories
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(9),
                            state = emojiGridState,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        ) {
                            // Recent emojis
                            if (recentEmojis.isNotEmpty()) {
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(9) }) {
                                    Text("Recent", color = C.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp))
                                }
                                items(recentEmojis.size) { idx ->
                                    Text(
                                        recentEmojis[idx], fontSize = 28.sp,
                                        modifier = Modifier.clickable { insertEmoji(recentEmojis[idx]) }.padding(4.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    )
                                }
                            }
                            // All categories with headers
                            allCategories.forEach { (categoryName, emojis) ->
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(9) }) {
                                    Text(categoryName, color = C.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp))
                                }
                                items(emojis.size) { idx ->
                                    Text(
                                        emojis[idx], fontSize = 28.sp,
                                        modifier = Modifier.clickable { insertEmoji(emojis[idx]) }.padding(4.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    )
                                }
                            }
                        }
                    } else {
                        // Stickers tab — pack-based system
                        val stickersRoot = remember { java.io.File(context.filesDir, "stickers").also { it.mkdirs() } }
                        fun loadPacks(): List<Pair<String, List<java.io.File>>> {
                            val dirs = stickersRoot.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()
                            return dirs.map { dir ->
                                dir.name to (dir.listFiles()?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList())
                            }
                        }
                        var packs by remember { mutableStateOf(loadPacks()) }
                        var activePackIdx by remember { mutableStateOf(0) }
                        var newPackName by remember { mutableStateOf("") }

                        // Helper to save a bitmap as sticker WebP
                        fun saveStickerBitmap(bmp: android.graphics.Bitmap, packDir: java.io.File, suffix: String = "") {
                            val maxSz = 512
                            val scale = minOf(maxSz.toFloat() / bmp.width, maxSz.toFloat() / bmp.height, 1f)
                            val w = (bmp.width * scale).toInt()
                            val h = (bmp.height * scale).toInt()
                            val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, w, h, true)
                            val file = java.io.File(packDir, "sticker_${System.currentTimeMillis()}$suffix.webp")
                            file.outputStream().use { scaled.compress(android.graphics.Bitmap.CompressFormat.WEBP, 80, it) }
                        }

                        // Multi-image picker for adding stickers to a pack
                        val addToPackLauncher = rememberLauncherForActivityResult(
                            contract = androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()
                        ) { uris ->
                            if (uris.isNotEmpty() && packs.isNotEmpty() && activePackIdx < packs.size) {
                                val packDir = java.io.File(stickersRoot, packs[activePackIdx].first)
                                for ((i, uri) in uris.withIndex()) {
                                    try {
                                        val input = context.contentResolver.openInputStream(uri)
                                        val bmp = android.graphics.BitmapFactory.decodeStream(input)
                                        input?.close()
                                        if (bmp != null) saveStickerBitmap(bmp, packDir, "_$i")
                                    } catch (_: Exception) {}
                                }
                                packs = loadPacks()
                                Toast.makeText(context, "${uris.size} sticker(s) added", Toast.LENGTH_SHORT).show()
                            }
                        }

                        // ZIP file picker for importing sticker packs
                        val zipImportLauncher = rememberLauncherForActivityResult(
                            contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
                        ) { uri ->
                            if (uri != null && packs.isNotEmpty() && activePackIdx < packs.size) {
                                val packDir = java.io.File(stickersRoot, packs[activePackIdx].first)
                                var count = 0
                                try {
                                    val input = context.contentResolver.openInputStream(uri) ?: return@rememberLauncherForActivityResult
                                    val zip = java.util.zip.ZipInputStream(input)
                                    var entry = zip.nextEntry
                                    while (entry != null) {
                                        val name = entry.name.lowercase()
                                        if (!entry.isDirectory && (name.endsWith(".webp") || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".tgs") || name.endsWith(".json"))) {
                                            val bytes = zip.readBytes()
                                            if (name.endsWith(".tgs")) {
                                                // TGS animated sticker — save directly
                                                val dest = java.io.File(packDir, "sticker_${System.currentTimeMillis()}_z$count.tgs")
                                                dest.writeBytes(bytes)
                                                count++
                                            } else if (name.endsWith(".json")) {
                                                // Lottie JSON — compress to TGS (gzip)
                                                val dest = java.io.File(packDir, "sticker_${System.currentTimeMillis()}_z$count.tgs")
                                                java.util.zip.GZIPOutputStream(dest.outputStream()).use { it.write(bytes) }
                                                count++
                                            } else {
                                                // Static image — decode and save as WebP
                                                val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                                if (bmp != null) {
                                                    saveStickerBitmap(bmp, packDir, "_z$count")
                                                    count++
                                                }
                                            }
                                        }
                                        zip.closeEntry()
                                        entry = zip.nextEntry
                                    }
                                    zip.close()
                                    input.close()
                                } catch (_: Exception) {}
                                packs = loadPacks()
                                if (count > 0) {
                                    Toast.makeText(context, "$count sticker(s) imported from ZIP", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "No images found in ZIP", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }

                        Column(modifier = Modifier.fillMaxSize()) {
                            // Pack tabs row
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // + button to create new pack
                                Box(
                                    modifier = Modifier.size(36.dp).clip(CircleShape)
                                        .background(C.accent.copy(alpha = 0.15f))
                                        .clickable { showCreateStickerPack = true },
                                    contentAlignment = Alignment.Center,
                                ) { Text("+", color = C.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                                Spacer(Modifier.width(4.dp))

                                // Pack tabs (scrollable) — only render when packs exist
                                // Pack tabs as simple scrollable Row (avoids ScrollableTabRow index crash)
                                Row(
                                    modifier = Modifier.weight(1f)
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    packs.forEachIndexed { idx, (name, _) ->
                                        val selected = idx == activePackIdx.coerceIn(0, maxOf(packs.size - 1, 0))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(if (selected) C.accent.copy(alpha = 0.15f) else Color.Transparent)
                                                .clickable { activePackIdx = idx }
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                        ) {
                                            Text(
                                                name, fontSize = 12.sp, maxLines = 1,
                                                color = if (selected) C.accent else C.textSecondary,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                            )
                                        }
                                    }
                                }
                            }

                            if (packs.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("\uD83C\uDFAD", fontSize = 48.sp)
                                        Spacer(Modifier.height(8.dp))
                                        Text("No sticker packs", color = C.textSecondary, fontSize = 14.sp)
                                        Text("Tap + to create a pack", color = C.textMuted, fontSize = 12.sp)
                                    }
                                }
                            } else {
                                val currentPack = packs.getOrNull(activePackIdx)
                                if (currentPack != null) {
                                    // Pack header with add + delete buttons
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(currentPack.first, color = C.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text(" (${currentPack.second.size})", color = C.textMuted, fontSize = 12.sp)
                                        Spacer(Modifier.weight(1f))
                                        TextButton(onClick = { zipImportLauncher.launch("application/zip") }) {
                                            Text("ZIP", color = C.accent, fontSize = 12.sp)
                                        }
                                        TextButton(onClick = { addToPackLauncher.launch("image/*") }) {
                                            Text("+ Add", color = C.accent, fontSize = 12.sp)
                                        }
                                        TextButton(onClick = {
                                            // Share entire pack as ZIP
                                            val packFiles = currentPack?.second ?: emptyList()
                                            if (packFiles.isEmpty()) {
                                                Toast.makeText(context, "Pack is empty", Toast.LENGTH_SHORT).show()
                                            } else if (!isGroupMode && resolvedWalletId.isNullOrEmpty()) {
                                                Toast.makeText(context, "Resolving address...", Toast.LENGTH_SHORT).show()
                                            } else {
                                                val pName = currentPack!!.first
                                                val pId = pName.hashCode().toString(16)
                                                val pTotal = packFiles.size
                                                showEmojiPicker = false
                                                Toast.makeText(context, "Packaging $pTotal stickers...", Toast.LENGTH_SHORT).show()
                                                com.privimemobile.chat.ChatService.scope.launch {
                                                    try {
                                                        // Build ZIP of all stickers (try 512px first, then 256px if too large)
                                                        fun buildZip(maxPx: Int, quality: Int): java.io.File {
                                                            val zipFile = java.io.File(context.cacheDir, "pack_${pId}_${System.currentTimeMillis()}.zip")
                                                            java.util.zip.ZipOutputStream(zipFile.outputStream()).use { zos ->
                                                                packFiles.forEachIndexed { idx, file ->
                                                                    if (file.name.endsWith(".tgs", ignoreCase = true)) {
                                                                        // TGS animated sticker — include as-is
                                                                        val entry = java.util.zip.ZipEntry("sticker_${idx}.tgs")
                                                                        zos.putNextEntry(entry)
                                                                        file.inputStream().use { it.copyTo(zos) }
                                                                        zos.closeEntry()
                                                                    } else {
                                                                        // Static sticker — resize and compress
                                                                        val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: return@forEachIndexed
                                                                        val scale = minOf(maxPx.toFloat() / bmp.width, maxPx.toFloat() / bmp.height, 1f)
                                                                        val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
                                                                        val entry = java.util.zip.ZipEntry("sticker_${idx}.webp")
                                                                        zos.putNextEntry(entry)
                                                                        scaled.compress(android.graphics.Bitmap.CompressFormat.WEBP, quality, zos)
                                                                        zos.closeEntry()
                                                                    }
                                                                }
                                                            }
                                                            return zipFile
                                                        }

                                                        var zipFile = buildZip(512, 80)
                                                        if (zipFile.length() > 500_000) {
                                                            zipFile.delete()
                                                            zipFile = buildZip(256, 50)  // More aggressive compression
                                                        }

                                                        if (zipFile.length() > 700_000) {
                                                            withContext(Dispatchers.Main) {
                                                                Toast.makeText(context, "Pack too large to share (${zipFile.length() / 1024}KB)", Toast.LENGTH_LONG).show()
                                                            }
                                                            zipFile.delete()
                                                            return@launch
                                                        }

                                                        // Send ZIP as sticker_pack message
                                                        val uri = android.net.Uri.fromFile(zipFile)
                                                        val fileMeta = com.privimemobile.chat.transport.IpfsTransport.prepareFile(
                                                            context, uri, "${pName}.zip", zipFile.length(), "application/zip"
                                                        )
                                                        if (fileMeta != null) {
                                                            val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get() ?: return@launch
                                                            val myHandle = state.myHandle ?: return@launch
                                                            val ts = System.currentTimeMillis() / 1000
                                                            val payload = mutableMapOf<String, Any?>(
                                                                "v" to 1, "t" to "sticker_pack", "ts" to ts,
                                                                "from" to myHandle, "to" to (if (isGroupMode) groupId!! else handle),
                                                                "dn" to (state.myDisplayName ?: ""),
                                                                "file" to fileMeta,
                                                                "pack_name" to pName,
                                                                "pack_id" to pId,
                                                                "pack_total" to pTotal,
                                                            )
                                                            val cid = fileMeta["cid"] as? String ?: ""
                                                            val stkConvId = if (isGroupMode) convId else {
                                                                val conv = com.privimemobile.chat.ChatService.db!!.conversationDao().getOrCreate(convKey, handle)
                                                                if (conv.deletedAtTs > 0) com.privimemobile.chat.ChatService.db!!.conversationDao().undelete(conv.id)
                                                                conv.id
                                                            }
                                                            val dedupKey = "$ts:sticker_pack:$cid:true"
                                                            val entity = com.privimemobile.chat.db.entities.MessageEntity(
                                                                conversationId = stkConvId, text = "\uD83D\uDCE6 Sticker pack: $pName ($pTotal stickers)",
                                                                timestamp = ts, sent = true, type = "sticker_pack",
                                                                senderHandle = myHandle, sbbsDedupKey = dedupKey,
                                                                stickerPackName = pName, stickerPackId = pId, stickerPackTotal = pTotal,
                                                            )
                                                            val msgId = com.privimemobile.chat.ChatService.db!!.messageDao().insert(entity)
                                                            if (msgId > 0 && cid.isNotEmpty()) {
                                                                com.privimemobile.chat.ChatService.db!!.attachmentDao().insert(
                                                                    com.privimemobile.chat.db.entities.AttachmentEntity(
                                                                        messageId = msgId, conversationId = stkConvId,
                                                                        ipfsCid = cid, encryptionKey = fileMeta["key"] as? String ?: "",
                                                                        encryptionIv = fileMeta["iv"] as? String ?: "",
                                                                        fileName = "${pName}.zip", fileSize = zipFile.length(),
                                                                        mimeType = "application/zip", inlineData = fileMeta["data"] as? String,
                                                                        downloadStatus = "done",
                                                                    )
                                                                )
                                                            }
                                                            if (isGroupMode && groupId != null) {
                                                                com.privimemobile.chat.ChatService.groups.sendGroupPayload(groupId, payload)
                                                                com.privimemobile.chat.ChatService.db?.groupDao()?.updateLastMessage(groupId, ts, "\uD83D\uDCE6 Sticker pack: $pName")
                                                            } else {
                                                                com.privimemobile.chat.ChatService.db!!.conversationDao().updateLastMessage(stkConvId, ts, "\uD83D\uDCE6 Sticker pack: $pName")
                                                                com.privimemobile.chat.ChatService.sbbs.sendWithRetry(resolvedWalletId!!, payload)
                                                            }
                                                            withContext(Dispatchers.Main) {
                                                                Toast.makeText(context, "Pack shared! ($pTotal stickers, ${zipFile.length() / 1024}KB)", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                        zipFile.delete()
                                                    } catch (e: Exception) {
                                                        Log.e("ChatScreen", "Share pack error: ${e.message}")
                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                }
                                            }
                                        }) {
                                            Text("Share", color = C.accent, fontSize = 12.sp)
                                        }
                                        TextButton(onClick = {
                                            val dir = java.io.File(stickersRoot, currentPack.first)
                                            dir.deleteRecursively()
                                            packs = loadPacks()
                                            activePackIdx = 0
                                            Toast.makeText(context, "Pack deleted", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Text("Delete", color = C.error, fontSize = 12.sp)
                                        }
                                    }

                                    // Sticker grid
                                    if (currentPack.second.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("Empty pack — tap + Add", color = C.textMuted, fontSize = 13.sp)
                                        }
                                    } else {
                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(4),
                                            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                                            contentPadding = PaddingValues(4.dp),
                                        ) {
                                            items(currentPack.second.size) { idx ->
                                                val file = currentPack.second[idx]
                                                val isTgs = file.name.endsWith(".tgs", ignoreCase = true)
                                                val stickerMod = Modifier
                                                    .aspectRatio(1f)
                                                    .padding(4.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .pointerInput(file.absolutePath) {
                                                        detectTapGestures(
                                                            onTap = {
                                                                val mime = if (isTgs) "application/x-tgsticker" else "image/webp"
                                                                val ext = if (isTgs) "tgs" else "webp"
                                                                val cached = java.io.File(context.cacheDir, "sticker_send_${System.currentTimeMillis()}.$ext")
                                                                file.copyTo(cached, overwrite = true)
                                                                pendingFile = PendingFile(uri = android.net.Uri.fromFile(cached), name = file.name, size = cached.length(), mimeType = mime)
                                                                val pName = currentPack?.first ?: "Stickers"
                                                                pendingStickerMeta = StickerMeta(pName, pName.hashCode().toString(16), currentPack?.second?.size ?: 0)
                                                                showEmojiPicker = false
                                                                handleSend()
                                                            },
                                                            onLongPress = {
                                                                file.delete()
                                                                packs = loadPacks()
                                                                Toast.makeText(context, "Sticker removed", Toast.LENGTH_SHORT).show()
                                                            },
                                                        )
                                                    }

                                                if (isTgs) {
                                                    // Animated TGS sticker — decompress and render with Lottie
                                                    val lottieJson = remember(file.absolutePath, file.lastModified()) {
                                                        try {
                                                            java.util.zip.GZIPInputStream(file.inputStream()).bufferedReader().readText()
                                                        } catch (_: Exception) { null }
                                                    }
                                                    if (lottieJson != null) {
                                                        val composition by com.airbnb.lottie.compose.rememberLottieComposition(
                                                            com.airbnb.lottie.compose.LottieCompositionSpec.JsonString(lottieJson)
                                                        )
                                                        com.airbnb.lottie.compose.LottieAnimation(
                                                            composition = composition,
                                                            iterations = com.airbnb.lottie.compose.LottieConstants.IterateForever,
                                                            modifier = stickerMod,
                                                        )
                                                    }
                                                } else {
                                                    // Static sticker (WebP/PNG)
                                                    val bmp = remember(file.absolutePath, file.lastModified()) {
                                                        android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                                                    }
                                                    if (bmp != null) {
                                                        Image(
                                                            bitmap = bmp.asImageBitmap(),
                                                            contentDescription = "Sticker",
                                                            modifier = stickerMod,
                                                            contentScale = ContentScale.Fit,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Create pack dialog
                        if (showCreateStickerPack) {
                            AlertDialog(
                                onDismissRequest = { showCreateStickerPack = false; newPackName = ""; focusManager.clearFocus(); keyboardController?.hide() },
                                containerColor = C.card,
                                title = { Text("New Sticker Pack", color = C.text) },
                                text = {
                                    OutlinedTextField(
                                        value = newPackName,
                                        onValueChange = { newPackName = it.take(20) },
                                        placeholder = { Text("Pack name", color = C.textMuted) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = C.text, unfocusedTextColor = C.text,
                                            focusedBorderColor = C.accent, cursorColor = C.accent,
                                        ),
                                    )
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        val name = newPackName.trim()
                                        if (name.isNotEmpty()) {
                                            java.io.File(stickersRoot, name).mkdirs()
                                            packs = loadPacks()
                                            activePackIdx = packs.indexOfFirst { it.first == name }.coerceAtLeast(0)
                                            showCreateStickerPack = false; newPackName = ""
                                            focusManager.clearFocus(); keyboardController?.hide()
                                        }
                                    }) { Text("Create", color = C.accent) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showCreateStickerPack = false; newPackName = ""; focusManager.clearFocus(); keyboardController?.hide() }) {
                                        Text("Cancel", color = C.textSecondary)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        // Self-destruct timer indicator bar
        if (oneShotTimer > 0) {
            Surface(
                color = C.card,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("\u23F3", fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Self-destruct: ${formatTimerLabel(oneShotTimer)}",
                        color = C.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { oneShotTimer = 0 }) {
                        Text("X", color = C.error, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Edit preview bar
        if (editingMsg != null) {
            Surface(
                color = C.card,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(32.dp)
                            .background(Color(0xFFFFA726)) // orange accent for edit
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                    ) {
                        Text(
                            "Editing message",
                            color = Color(0xFFFFA726),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            editingMsg!!.text.take(80),
                            color = C.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(onClick = {
                        editingMsg = null
                        setInputText("")
                    }) {
                        Text("X", color = C.error, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Reply preview bar
        if (replyingTo != null) {
            Surface(
                color = C.card,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(32.dp)
                            .background(C.accent)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                    ) {
                        Text(
                            if (replyingTo!!.sent) "You" else "@${replyingTo!!.from}",
                            color = C.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            replyingTo!!.text.ifEmpty {
                                when (replyingTo!!.type) {
                                    "file" -> replyingTo!!.file?.name ?: "File"
                                    "tip" -> "Tip: ${Helpers.formatBeam(replyingTo!!.tipAmount)} ${com.privimemobile.wallet.assetTicker(replyingTo!!.tipAssetId)}"
                                    else -> "Message"
                                }
                            }.take(80),
                            color = C.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(onClick = { replyingTo = null }) {
                        Text("X", color = C.error, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Command menu popup (Telegram-style)
        if (showCommandMenu) {
            Surface(
                color = C.card,
                shadowElevation = 4.dp,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("Commands", color = C.textSecondary, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
                    listOf(
                        Triple("/tip",
                            if (isGroupMode) "<amount> [asset_id] [msg] (reply to tip)" else "<amount> [asset_id] [message]",
                            if (isGroupMode) "Reply to a message, or /tip @handle amount" else "Send BEAM (default) or any asset"),
                        Triple("/poll", "Question | Option 1 | Option 2", "Create a poll"),
                    ).forEach { (cmd, args, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    setInputText("$cmd ")
                                    showCommandMenu = false
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(cmd, color = C.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(6.dp))
                            Text(args, color = C.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        }
                        Text(desc, color = C.textMuted, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
                    }
                }
            }
        }

        // Input bar — Telegram-X style (48dp bar, 180ms animations)
        // keyboardController declared at screen level
        val hasText = inputText.text.isNotBlank() || pendingFile != null
        // Close emoji picker when system keyboard appears
        val imeVisible = androidx.compose.foundation.layout.WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
        LaunchedEffect(imeVisible) {
            if (imeVisible && showEmojiPicker && emojiMainTab == 0) showEmojiPicker = false
            // Only auto-close on emoji tab (emojiMainTab=0). Sticker tab (emojiMainTab=1) uses dialogs/pickers that open keyboard.
        }
        Surface(
            color = C.card,
            shadowElevation = 2.dp,
            modifier = Modifier.then(if (!showEmojiPicker) Modifier.navigationBarsPadding() else Modifier),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                // Emoji toggle (left — 48dp like Telegram)
                IconButton(
                    onClick = {
                        if (showEmojiPicker) {
                            showEmojiPicker = false
                            keyboardController?.show()
                        } else {
                            showEmojiPicker = true
                            keyboardController?.hide()
                        }
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    AnimatedContent(
                        targetState = showEmojiPicker,
                        transitionSpec = { (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.6f)).togetherWith(fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.6f)) },
                        label = "emojiIcon",
                    ) { isEmoji ->
                        if (isEmoji) {
                            Icon(Icons.Default.KeyboardArrowDown, "Keyboard", tint = C.textSecondary, modifier = Modifier.size(24.dp))
                        } else {
                            Text("\uD83D\uDE00", fontSize = 24.sp)
                        }
                    }
                }

                // Text field (center — fills available space)
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { newValue ->
                        inputText = newValue
                        val text = newValue.text
                        if (text == "/") showCommandMenu = true
                        else if (showCommandMenu && !text.startsWith("/")) showCommandMenu = false
                        if (text.isNotEmpty()) {
                            if (isGroupMode) com.privimemobile.chat.ChatService.groups.sendGroupTyping(groupId!!)
                            else com.privimemobile.chat.ChatService.sbbs.sendTyping(convKey)
                        }
                        // Close emoji picker when user starts typing via keyboard
                        if (showEmojiPicker && text.length > inputText.text.length) {
                            // Only if keyboard added chars (not emoji picker)
                        }
                    },
                    placeholder = {
                        Text(
                            when {
                                pendingFile != null -> "Add a caption..."
                                !isGroupMode && resolvedWalletId.isNullOrEmpty() -> "Resolving address..."
                                else -> "Message"
                            },
                            color = C.textMuted, fontSize = 15.sp,
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = C.bg, unfocusedContainerColor = C.bg,
                        cursorColor = C.accent, focusedTextColor = C.text, unfocusedTextColor = C.text,
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp),
                    maxLines = 4,
                    enabled = (isGroupMode || !resolvedWalletId.isNullOrEmpty()) && !uploading,
                )

                // Right side: animated swap icons ↔ send (180ms, scale to 62.5% like Telegram)
                AnimatedContent(
                    targetState = hasText || uploading,
                    transitionSpec = {
                        (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.625f))
                            .togetherWith(fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.625f))
                    },
                    label = "rightIcons",
                ) { showSend ->
                    if (showSend) {
                        if (uploading) {
                            Box(
                                modifier = Modifier.size(48.dp).clip(CircleShape).background(C.accent),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = C.textDark, strokeWidth = 2.dp)
                            }
                        } else {
                            Box(
                                modifier = Modifier.size(48.dp).clip(CircleShape).background(C.accent)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = { handleSend() },
                                            onLongPress = { showSchedulePicker = true },
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = C.textDark, modifier = Modifier.size(22.dp))
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.height(48.dp),
                        ) {
                            IconButton(onClick = { showCommandMenu = !showCommandMenu }, modifier = Modifier.size(42.dp)) {
                                Box(
                                    modifier = Modifier.size(32.dp).clip(CircleShape)
                                        .background(if (showCommandMenu) C.accent.copy(alpha = 0.15f) else Color.Transparent),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("/", color = C.textSecondary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            IconButton(
                                onClick = { showAttachPicker = true },
                                enabled = !uploading && !com.privimemobile.chat.transport.IpfsTransport.uploadInProgress,
                                modifier = Modifier.size(42.dp),
                            ) {
                                Icon(Icons.Default.AttachFile, "Attach", tint = C.textSecondary, modifier = Modifier.size(22.dp))
                            }
                            Box(modifier = Modifier.size(42.dp)) {
                                IconButton(onClick = { showOneShotTimerPicker = true }, modifier = Modifier.size(42.dp)) {
                                    Icon(Icons.Default.Timer, "Timer", tint = if (oneShotTimer > 0) C.accent else C.textMuted, modifier = Modifier.size(20.dp))
                                }
                                if (oneShotTimer > 0) {
                                    Box(modifier = Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = 4.dp).size(8.dp).clip(CircleShape).background(C.accent))
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Clear history confirmation ──
        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                containerColor = C.card,
                title = { Text("Clear history", color = C.text, fontWeight = FontWeight.SemiBold) },
                text = { Text("Delete all messages in this chat? This cannot be undone.", color = C.textSecondary) },
                confirmButton = {
                    TextButton(onClick = {
                        if (convId > 0L) {
                            scope.launch {
                                com.privimemobile.chat.ChatService.db?.messageDao()?.softDeleteByConversation(convId)
                                com.privimemobile.chat.ChatService.db?.conversationDao()?.updateLastMessage(convId, 0, null)
                            }
                        }
                        showClearConfirm = false
                    }) {
                        Text("Clear", color = C.error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) {
                        Text("Cancel", color = C.textSecondary)
                    }
                },
            )
        }

        // ── Delete chat confirmation ──
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                containerColor = C.card,
                title = { Text(if (isGroupMode) "Leave group" else "Delete chat", color = C.text, fontWeight = FontWeight.SemiBold) },
                text = { Text(
                    if (isGroupMode) "Leave this group and delete all messages? You can rejoin later."
                    else "Delete this entire conversation? New messages from this contact will start a fresh chat.",
                    color = C.textSecondary,
                ) },
                confirmButton = {
                    TextButton(onClick = {
                        if (convId > 0L) {
                            scope.launch {
                                com.privimemobile.chat.ChatService.db?.messageDao()?.softDeleteByConversation(convId)
                                com.privimemobile.chat.ChatService.db?.conversationDao()?.softDelete(convId)
                            }
                        }
                        showDeleteConfirm = false
                        onBack()
                    }) {
                        Text("Delete", color = C.error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("Cancel", color = C.textSecondary)
                    }
                },
            )
        }

        // ── Multi-select delete confirmation ──
        if (showDeleteConfirmDialog && pendingDeleteIds.isNotEmpty()) {
            val count = pendingDeleteIds.size
            // Check if any selected message is ours (for "delete for everyone" option)
            val hasOwnMessages = messages.any { it.id in pendingDeleteIds && it.sent }
            AlertDialog(
                onDismissRequest = {
                    showDeleteConfirmDialog = false
                    pendingDeleteIds = emptyList()
                },
                containerColor = C.card,
                title = { Text("Delete $count message${if (count > 1) "s" else ""}?", color = C.text, fontWeight = FontWeight.SemiBold) },
                text = {
                    Column {
                        // Delete for me
                        TextButton(
                            onClick = {
                                val ids = pendingDeleteIds.toList()
                                val cid = convId
                                scope.launch {
                                    ids.forEach { id ->
                                        com.privimemobile.chat.ChatService.db?.messageDao()?.markDeletedById(id.toLong())
                                    }
                                    // Update chat list preview
                                    refreshConversationPreview(cid)
                                }
                                showDeleteConfirmDialog = false
                                pendingDeleteIds = emptyList()
                                selectionMode = false
                                selectedIds.clear()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Delete for me", color = C.text, fontSize = 15.sp, modifier = Modifier.fillMaxWidth())
                        }
                        // Delete for everyone (only if we have own messages selected)
                        if (hasOwnMessages) {
                            TextButton(
                                onClick = {
                                    // Capture message data BEFORE coroutine — messages state changes after each delete
                                    val msgsToDelete = messages.filter { it.id in pendingDeleteIds }
                                    val capturedConvId = convId
                                    showDeleteConfirmDialog = false
                                    pendingDeleteIds = emptyList()
                                    selectionMode = false
                                    selectedIds.clear()
                                    scope.launch {
                                        val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
                                        if (state?.myHandle != null) {
                                            for ((i, msg) in msgsToDelete.withIndex()) {
                                                com.privimemobile.chat.ChatService.db?.messageDao()?.markDeletedById(msg.id.toLong())
                                                if (msg.sent) {
                                                    val delPayload = mapOf(
                                                        "v" to 1, "t" to "delete",
                                                        "ts" to System.currentTimeMillis() / 1000,
                                                        "from" to state.myHandle!!,
                                                        "to" to (if (isGroupMode) groupId!! else handle),
                                                        "msg_ts" to msg.timestamp,
                                                    )
                                                    if (isGroupMode && groupId != null) {
                                                        com.privimemobile.chat.ChatService.groups.sendGroupPayload(groupId, delPayload)
                                                    } else {
                                                        val walletId = resolvedWalletId
                                                        if (!walletId.isNullOrEmpty()) {
                                                            com.privimemobile.chat.ChatService.sbbs.sendWithRetry(walletId, delPayload)
                                                        }
                                                    }
                                                    if (i < msgsToDelete.size - 1) delay(1000)
                                                }
                                            }
                                        }
                                        // Update chat list preview
                                        refreshConversationPreview(capturedConvId)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Delete for everyone", color = C.error, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = {
                        showDeleteConfirmDialog = false
                        pendingDeleteIds = emptyList()
                    }) {
                        Text("Cancel", color = C.textSecondary)
                    }
                },
            )
        }

        // ── Wallpaper picker ──
        if (showWallpaperPicker) {
            val wallpaperOptions = listOf(
                "default" to "Default",
                "dark_blue" to "Dark Blue",
                "teal" to "Teal",
                "purple" to "Purple",
                "midnight" to "Midnight",
                "forest" to "Forest",
                "sunset" to "Sunset",
            )
            AlertDialog(
                onDismissRequest = { showWallpaperPicker = false },
                containerColor = C.card,
                title = { Text("Chat Wallpaper", color = C.text, fontWeight = FontWeight.SemiBold) },
                text = {
                    Column {
                        // Custom image option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { wallpaperImagePicker.launch("image/*") }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier.size(32.dp).clip(CircleShape)
                                    .background(C.accent.copy(alpha = 0.3f))
                                    .then(if (chatWallpaper.startsWith("custom:")) Modifier.border(2.dp, C.accent, CircleShape) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) { Text("\uD83D\uDDBC", fontSize = 16.sp) }
                            Spacer(Modifier.width(12.dp))
                            Text("Custom Image", color = C.text, fontSize = 15.sp)
                            if (chatWallpaper.startsWith("custom:")) {
                                Spacer(Modifier.weight(1f))
                                Text("\u2713", color = C.accent, fontSize = 16.sp)
                            }
                        }
                        HorizontalDivider(color = C.textSecondary.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                        wallpaperOptions.forEach { (key, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        chatWallpaper = key
                                        prefs.edit().putString("wallpaper_$convKey", key).apply()
                                        showWallpaperPicker = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Color preview
                                val previewColor = when (key) {
                                    "dark_blue" -> Color(0xFF0D1B2A)
                                    "teal" -> Color(0xFF004D40)
                                    "purple" -> Color(0xFF1A0033)
                                    "midnight" -> Color(0xFF0F0F23)
                                    "forest" -> Color(0xFF1B3A2D)
                                    "sunset" -> Color(0xFF2D1B00)
                                    else -> C.bg
                                }
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(previewColor)
                                        .then(if (chatWallpaper == key) Modifier.border(2.dp, C.accent, CircleShape) else Modifier),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(label, color = C.text, fontSize = 15.sp)
                                if (chatWallpaper == key) {
                                    Spacer(Modifier.weight(1f))
                                    Text("\u2713", color = C.accent, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
            )
        }

        // ── Schedule message picker ──
        if (showSchedulePicker) {
            val scheduleOptions = listOf(
                "10min" to "In 10 minutes",
                "30min" to "In 30 minutes",
                "1h" to "In 1 hour",
                "3h" to "In 3 hours",
                "tomorrow" to "Tomorrow morning (9:00 AM)",
                "custom" to "Pick date & time...",
            )
            var showCustomDateTime by remember { mutableStateOf(false) }

            if (!showCustomDateTime) {
                AlertDialog(
                    onDismissRequest = { showSchedulePicker = false },
                    containerColor = C.card,
                    title = { Text("Schedule Message", color = C.text, fontWeight = FontWeight.SemiBold) },
                    text = {
                        Column {
                            Text("Long-press send to schedule", color = C.textSecondary, fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 8.dp))
                            scheduleOptions.forEach { (key, label) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (key == "custom") {
                                                showCustomDateTime = true
                                            } else {
                                                val now = System.currentTimeMillis() / 1000
                                                val scheduledAt = when (key) {
                                                    "10min" -> now + 600
                                                    "30min" -> now + 1800
                                                    "1h" -> now + 3600
                                                    "3h" -> now + 10800
                                                    "tomorrow" -> {
                                                        val cal = java.util.Calendar.getInstance()
                                                        cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                                                        cal.set(java.util.Calendar.HOUR_OF_DAY, 9)
                                                        cal.set(java.util.Calendar.MINUTE, 0)
                                                        cal.set(java.util.Calendar.SECOND, 0)
                                                        cal.timeInMillis / 1000
                                                    }
                                                    else -> now + 600
                                                }
                                                scheduleMessage(inputText.text, scheduledAt)
                                                showSchedulePicker = false
                                            }
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(if (key == "custom") "\uD83D\uDCC5" else "\u23F0", fontSize = 18.sp)
                                    Spacer(Modifier.width(12.dp))
                                    Text(label, color = C.text, fontSize = 15.sp)
                                }
                            }
                        }
                    },
                    confirmButton = {},
                )
            } else {
                // Two-step: date picker → time picker
                var selectedDateMillis by remember { mutableStateOf<Long?>(null) }

                if (selectedDateMillis == null) {
                    // Step 1: Date picker
                    val dateState = rememberDatePickerState()
                    DatePickerDialog(
                        onDismissRequest = { showCustomDateTime = false; showSchedulePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                val millis = dateState.selectedDateMillis
                                if (millis != null) selectedDateMillis = millis
                            }) { Text("Next", color = C.accent) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showCustomDateTime = false; showSchedulePicker = false }) {
                                Text("Cancel", color = C.textSecondary)
                            }
                        },
                    ) {
                        DatePicker(state = dateState, colors = DatePickerDefaults.colors(containerColor = C.card))
                    }
                } else {
                    // Step 2: Time picker
                    val cal = java.util.Calendar.getInstance()
                    val initMin = cal.get(java.util.Calendar.MINUTE) + 5
                    val timeState = rememberTimePickerState(
                        initialHour = cal.get(java.util.Calendar.HOUR_OF_DAY) + initMin / 60,
                        initialMinute = initMin % 60,
                    )
                    AlertDialog(
                        onDismissRequest = { showCustomDateTime = false; showSchedulePicker = false },
                        containerColor = C.card,
                        title = { Text("Pick time", color = C.text, fontWeight = FontWeight.SemiBold) },
                        text = {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                TimePicker(state = timeState)
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val dateCal = java.util.Calendar.getInstance()
                                dateCal.timeInMillis = selectedDateMillis!!
                                dateCal.set(java.util.Calendar.HOUR_OF_DAY, timeState.hour)
                                dateCal.set(java.util.Calendar.MINUTE, timeState.minute)
                                dateCal.set(java.util.Calendar.SECOND, 0)
                                val scheduledAt = dateCal.timeInMillis / 1000
                                val now = System.currentTimeMillis() / 1000
                                if (scheduledAt > now) {
                                    scheduleMessage(inputText.text, scheduledAt)
                                } else {
                                    Toast.makeText(context, "Time must be in the future", Toast.LENGTH_SHORT).show()
                                }
                                showCustomDateTime = false
                                showSchedulePicker = false
                            }) { Text("Schedule", color = C.accent) }
                        },
                        dismissButton = {
                            TextButton(onClick = { selectedDateMillis = null }) {
                                Text("Back", color = C.textSecondary)
                            }
                        },
                    )
                }
            }
        }

        // ── View Sticker Pack dialog (local only) ──
        if (viewPackId != null) {
            // Get pack name from the sticker message that triggered this
            val packName = messages.firstOrNull { it.stickerPackId == viewPackId }?.stickerPackName ?: "Stickers"
            val stickersRoot = java.io.File(context.filesDir, "stickers")
            val localPackDir = java.io.File(stickersRoot, packName)
            val localFiles = remember(viewPackId) {
                if (localPackDir.exists()) localPackDir.listFiles()?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
                else emptyList()
            }

            AlertDialog(
                onDismissRequest = { viewPackId = null },
                containerColor = C.card,
                title = {
                    Column {
                        Text(packName, color = C.text, fontWeight = FontWeight.SemiBold)
                        if (localFiles.isNotEmpty()) {
                            Text("${localFiles.size} stickers", color = C.accent, fontSize = 12.sp)
                        } else {
                            Text("Pack not saved yet. Ask sender to share it.", color = C.textSecondary, fontSize = 12.sp)
                        }
                    }
                },
                text = {
                    if (localFiles.isEmpty()) {
                        Text("You don't have this pack. The sender can share it using the Share button in their sticker picker.", color = C.textMuted, fontSize = 13.sp)
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.heightIn(max = 300.dp),
                            contentPadding = PaddingValues(4.dp),
                        ) {
                            items(localFiles.size) { idx ->
                                val file = localFiles[idx]
                                val isTgs = file.name.endsWith(".tgs", ignoreCase = true)
                                if (isTgs) {
                                    val lottieJson = remember(file.absolutePath) {
                                        try { java.util.zip.GZIPInputStream(file.inputStream()).bufferedReader().readText() }
                                        catch (_: Exception) { null }
                                    }
                                    if (lottieJson != null) {
                                        val composition by com.airbnb.lottie.compose.rememberLottieComposition(
                                            com.airbnb.lottie.compose.LottieCompositionSpec.JsonString(lottieJson)
                                        )
                                        com.airbnb.lottie.compose.LottieAnimation(
                                            composition = composition,
                                            iterations = com.airbnb.lottie.compose.LottieConstants.IterateForever,
                                            modifier = Modifier.aspectRatio(1f).padding(4.dp).clip(RoundedCornerShape(8.dp)),
                                        )
                                    }
                                } else {
                                    val bmp = remember(file.absolutePath, file.lastModified()) {
                                        android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                                    }
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Sticker",
                                            modifier = Modifier.aspectRatio(1f).padding(4.dp).clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Fit,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewPackId = null }) { Text("OK", color = C.accent) }
                },
                dismissButton = {},
            )
        }

        // ── Date jump picker ──
        if (showDatePicker) {
            val datePickerState = rememberDatePickerState()
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis
                        if (selectedMillis != null) {
                            val targetTs = selectedMillis / 1000
                            // Find the closest message to this date
                            val reversedMessages = messages.reversed()
                            val idx = reversedMessages.indexOfFirst { it.timestamp >= targetTs }
                            if (idx >= 0) {
                                scope.launch { listState.animateScrollToItem(idx) }
                            } else if (reversedMessages.isNotEmpty()) {
                                // Date is after all messages — scroll to newest
                                scope.launch { listState.animateScrollToItem(0) }
                            }
                        }
                        showDatePicker = false
                    }) {
                        Text("Jump", color = C.accent)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text("Cancel", color = C.textSecondary)
                    }
                },
                colors = DatePickerDefaults.colors(containerColor = C.card),
            ) {
                DatePicker(state = datePickerState)
            }
        }

        // ── Per-message self-destruct timer picker ──
        if (showOneShotTimerPicker) {
            val timerOptions = listOf(
                0 to "Off",
                30 to "30 seconds",
                300 to "5 minutes",
                3600 to "1 hour",
                86400 to "1 day",
            )
            AlertDialog(
                onDismissRequest = { showOneShotTimerPicker = false },
                containerColor = C.card,
                shape = RoundedCornerShape(16.dp),
                title = { Text("Self-destruct timer", color = C.text, fontWeight = FontWeight.SemiBold) },
                text = {
                    Column {
                        Text(
                            "Next message will auto-delete after this time. Does not change the other person's settings.",
                            color = C.textSecondary, fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        timerOptions.forEach { (seconds, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        oneShotTimer = seconds
                                        showOneShotTimerPicker = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = oneShotTimer == seconds,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = C.accent,
                                        unselectedColor = C.textSecondary,
                                    ),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(label, color = C.text, fontSize = 15.sp)
                            }
                        }
                    }
                },
                confirmButton = {},
            )
        }

        // ── Context menu (long-press on message) — Telegram-style bottom sheet ──
        if (contextMenuMsg != null) {
            val targetMsg = contextMenuMsg!!
            ModalBottomSheet(
                onDismissRequest = { contextMenuMsg = null },
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
                                        val walletId = resolvedWalletId
                                        if (!walletId.isNullOrEmpty()) {
                                            com.privimemobile.chat.ChatService.sbbs.sendWithRetry(walletId, reactPayload)
                                        }
                                    }
                                }
                            }
                            contextMenuMsg = null
                        }

                        // First row: 7 quick emojis + expand button
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = if (emojiExpanded) 4.dp else 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            quickEmojis.forEach { emoji ->
                                Text(
                                    emoji, fontSize = 28.sp,
                                    modifier = Modifier.clickable { sendReaction(emoji) }.padding(4.dp),
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
                            MenuItemRow("View Pack \u2014 ${targetMsg.stickerPackName ?: "Stickers"}") {
                                viewPackId = targetMsg.stickerPackId
                                contextMenuMsg = null
                            }
                        }

                        if (targetMsg.text.isNotEmpty()) {
                            MenuItemRow("Copy") {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("message", targetMsg.text))
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                contextMenuMsg = null
                            }
                        }

                        if (targetMsg.file != null) {
                            val targetPath = filePaths[targetMsg.file.cid]
                            if (targetPath != null) {
                                MenuItemRow("Save to Downloads") {
                                    saveFileToDownloads(context, targetPath, targetMsg.file.name, targetMsg.file.mime)
                                    contextMenuMsg = null
                                }
                                // Save to stickers for image files
                                if (Helpers.isImageMime(targetMsg.file.mime)) {
                                    var showSaveToPackPicker by remember { mutableStateOf(false) }
                                    MenuItemRow("Save to stickers") {
                                        showSaveToPackPicker = true
                                    }
                                    if (showSaveToPackPicker) {
                                        val stickersRoot = java.io.File(context.filesDir, "stickers")
                                        val packDirs = stickersRoot.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()
                                        AlertDialog(
                                            onDismissRequest = { showSaveToPackPicker = false },
                                            containerColor = C.card,
                                            title = { Text("Save to pack", color = C.text) },
                                            text = {
                                                Column {
                                                    if (packDirs.isEmpty()) {
                                                        Text("No packs yet. Create one in the sticker tab first.", color = C.textSecondary, fontSize = 13.sp)
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
                                                                            Toast.makeText(context, "Saved to ${dir.name}", Toast.LENGTH_SHORT).show()
                                                                        }
                                                                    } catch (_: Exception) {}
                                                                    showSaveToPackPicker = false
                                                                    contextMenuMsg = null
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
                                                    Text("Cancel", color = C.textSecondary)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        MenuItemRow("Reply") { replyingTo = targetMsg; contextMenuMsg = null }

                        // Pin/Unpin — in group mode, only admin (role>=1) or creator (role==2) can pin
                        val canPin = !isGroupMode || (group?.myRole ?: 0) >= 1
                        if (canPin) {
                            MenuItemRow(if (targetMsg.pinned) "Unpin" else "Pin") {
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
                                contextMenuMsg = null
                            }
                        }

                        if (targetMsg.sent && targetMsg.text.isNotEmpty() && targetMsg.type != "tip") {
                            MenuItemRow("Edit") {
                                editingMsg = targetMsg; replyingTo = null
                                setInputText(targetMsg.text); contextMenuMsg = null
                            }
                        }

                        if (targetMsg.text.isNotEmpty() || targetMsg.file != null) {
                            MenuItemRow("Forward") {
                                forwardingMsgs = emptyList()
                                forwardingMsg = targetMsg; contextMenuMsg = null
                            }
                        }

                        // Resend option for sent messages not yet delivered
                        if (targetMsg.sent && !targetMsg.delivered && targetMsg.scheduledAt == 0L && (targetMsg.type == "dm" || targetMsg.type == "group_msg")) {
                            MenuItemRow("Resend") {
                                scope.launch {
                                    val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
                                    if (state?.myHandle != null) {
                                        if (isGroupMode && groupId != null) {
                                            com.privimemobile.chat.ChatService.groups.sendGroupMessage(
                                                groupId, targetMsg.text, replyText = targetMsg.reply,
                                            )
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Message resent to group", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            val wid = resolvedWalletId
                                            if (!wid.isNullOrEmpty()) {
                                                val payload = mapOf(
                                                    "v" to 1, "t" to "dm",
                                                    "ts" to targetMsg.timestamp,
                                                    "from" to state.myHandle!!, "to" to handle,
                                                    "dn" to (state.myDisplayName ?: ""),
                                                    "msg" to targetMsg.text,
                                                )
                                                com.privimemobile.chat.ChatService.sbbs.sendWithRetry(wid, payload)
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Message resent", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                }
                                contextMenuMsg = null
                            }
                        }

                        MenuItemRow("Select") {
                            selectionMode = true; selectedIds.clear()
                            selectedIds.add(targetMsg.id); contextMenuMsg = null
                        }

                        // Cancel scheduled message option
                        if (targetMsg.scheduledAt > 0) {
                            MenuItemRow("Cancel scheduled", color = C.error) {
                                val cid = convId
                                scope.launch {
                                    com.privimemobile.chat.ChatService.db?.messageDao()?.cancelScheduled(targetMsg.id.toLong())
                                    refreshConversationPreview(cid)
                                }
                                contextMenuMsg = null
                                Toast.makeText(context, "Scheduled message cancelled", Toast.LENGTH_SHORT).show()
                            }
                        }

                        MenuItemRow("Delete for me", color = C.error) {
                            val cid = convId
                            scope.launch {
                                com.privimemobile.chat.ChatService.db?.messageDao()?.markDeletedById(targetMsg.id.toLong())
                                refreshConversationPreview(cid)
                            }
                            contextMenuMsg = null
                        }

                        if (targetMsg.sent) {
                            MenuItemRow("Delete for everyone", color = C.error) {
                                scope.launch {
                                    val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
                                    if (state?.myHandle != null) {
                                        com.privimemobile.chat.ChatService.db?.messageDao()?.markDeletedById(targetMsg.id.toLong())
                                        val delPayload = mapOf(
                                            "v" to 1, "t" to "delete",
                                            "ts" to System.currentTimeMillis() / 1000,
                                            "from" to state.myHandle!!, "to" to (if (isGroupMode) groupId!! else handle),
                                            "msg_ts" to targetMsg.timestamp,
                                        )
                                        if (isGroupMode && groupId != null) {
                                            com.privimemobile.chat.ChatService.groups.sendGroupPayload(groupId, delPayload)
                                        } else {
                                            val walletId = resolvedWalletId
                                            if (!walletId.isNullOrEmpty()) {
                                                com.privimemobile.chat.ChatService.sbbs.sendWithRetry(walletId, delPayload)
                                            }
                                        }
                                        refreshConversationPreview(convId)
                                    }
                                }
                                contextMenuMsg = null
                            }
                        }
                    }
                }
            }
        }

        // ── Forward contact picker dialog ──
        if (forwardingMsg != null) {
            val fwdMsg = forwardingMsg!!
            val allFwdMsgs = forwardingMsgs.ifEmpty { listOf(fwdMsg) }
            val chatState by com.privimemobile.chat.ChatService.observeState().collectAsState(initial = null)
            val myHandle = chatState?.myHandle
            val forwardContacts = remember(allContacts, myHandle) {
                allContacts.filter { it.handle != myHandle && !it.walletId.isNullOrEmpty() }
            }
            val forwardGroups by com.privimemobile.chat.ChatService.db?.groupDao()?.observeAll()
                ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }

            AlertDialog(
                onDismissRequest = { forwardingMsg = null; forwardingMsgs = emptyList() },
                containerColor = C.card,
                title = { Text("Forward ${if (allFwdMsgs.size > 1) "${allFwdMsgs.size} messages" else ""} to", color = C.text, fontWeight = FontWeight.SemiBold) },
                text = {
                    Column(modifier = Modifier.heightIn(max = 400.dp)) {
                        // Preview of message(s) being forwarded
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = C.bg.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                if (allFwdMsgs.size > 1) {
                                    Text(
                                        "${allFwdMsgs.size} messages selected",
                                        color = C.textSecondary,
                                        fontSize = 12.sp,
                                    )
                                } else {
                                    if (fwdMsg.file != null) {
                                        Text(
                                            "\uD83D\uDCCE ${fwdMsg.file.name.ifEmpty { "File" }}",
                                            color = C.accent,
                                            fontSize = 12.sp,
                                        )
                                    }
                                    if (fwdMsg.text.isNotEmpty()) {
                                        Text(
                                            fwdMsg.text.take(100),
                                            color = C.textSecondary,
                                            fontSize = 12.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }

                        if (forwardContacts.isEmpty() && forwardGroups.isEmpty()) {
                            Text("No contacts or groups", color = C.textSecondary, fontSize = 14.sp)
                        } else {
                            LazyColumn {
                                // Groups section
                                if (forwardGroups.isNotEmpty()) {
                                    item { Text("Groups", color = C.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp)) }
                                    items(forwardGroups, key = { "g_${it.groupId}" }) { grp ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    com.privimemobile.chat.ChatService.scope.launch {
                                                        val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
                                                        if (state?.myHandle != null) {
                                                            for ((i, m) in allFwdMsgs.withIndex()) {
                                                                val ts = System.currentTimeMillis() / 1000 + i
                                                                val fwdFrom = if (m.sent) state.myHandle!! else m.from
                                                                val isFile = m.file != null
                                                                val msgType = if (isFile) "file" else "dm"
                                                                val payload = mutableMapOf<String, Any?>(
                                                                    "v" to 1, "t" to msgType, "ts" to ts,
                                                                    "from" to state.myHandle!!,
                                                                    "dn" to (state.myDisplayName ?: ""),
                                                                    "fwd_from" to fwdFrom, "fwd_ts" to m.timestamp,
                                                                )
                                                                if (m.text.isNotEmpty()) payload["msg"] = m.text
                                                                if (isFile) {
                                                                    val f = m.file!!
                                                                    val fileMap = mutableMapOf<String, Any?>(
                                                                        "name" to f.name, "size" to f.size,
                                                                        "mime" to f.mime, "key" to f.key, "iv" to f.iv,
                                                                    )
                                                                    if (f.cid.isNotEmpty() && !f.cid.startsWith("inline-")) fileMap["cid"] = f.cid
                                                                    if (f.data != null) fileMap["data"] = f.data
                                                                    payload["file"] = fileMap
                                                                }
                                                                com.privimemobile.chat.ChatService.groups.sendGroupMessage(grp.groupId, m.text, fwdFrom = fwdFrom, fwdTs = m.timestamp)
                                                                if (i < allFwdMsgs.size - 1) delay(1000)
                                                            }
                                                            val count = allFwdMsgs.size
                                                            Toast.makeText(context, "Forwarded $count message${if (count > 1) "s" else ""} to ${grp.name}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                    forwardingMsg = null; forwardingMsgs = emptyList()
                                                }
                                                .padding(vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Box(
                                                Modifier.size(36.dp).clip(CircleShape).background(C.accent),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(Icons.Default.Group, null, tint = C.textDark, modifier = Modifier.size(20.dp))
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            Column {
                                                Text(grp.name, color = C.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                                Text("${grp.memberCount} members", color = C.textSecondary, fontSize = 12.sp)
                                            }
                                        }
                                        HorizontalDivider(color = C.border, thickness = 0.5.dp)
                                    }
                                    item { Spacer(Modifier.height(8.dp)) }
                                }
                                // Contacts section
                                if (forwardContacts.isNotEmpty()) {
                                    item { Text("Contacts", color = C.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp)) }
                                }
                                items(forwardContacts, key = { it.handle }) { contact ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                // Send forwarded message(s) — use ChatService scope so it survives navigation
                                                com.privimemobile.chat.ChatService.scope.launch {
                                                    val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
                                                    if (state?.myHandle != null && !contact.walletId.isNullOrEmpty()) {
                                                        val toHandle = contact.handle
                                                        val toConvKey = "@$toHandle"
                                                        val fwdConv = com.privimemobile.chat.ChatService.db!!.conversationDao().getOrCreate(toConvKey, toHandle)
                                                        var lastPreview: String? = null
                                                        var lastTs = 0L
                                                        for ((i, m) in allFwdMsgs.withIndex()) {
                                                            val ts = System.currentTimeMillis() / 1000 + i  // offset to avoid dedup collision
                                                            val isFile = m.file != null
                                                            val msgType = if (isFile) "file" else "dm"
                                                            val fwdFrom = if (m.sent) state.myHandle!! else m.from
                                                            val payload = mutableMapOf<String, Any?>(
                                                                "v" to 1, "t" to msgType, "ts" to ts,
                                                                "from" to state.myHandle!!, "to" to toHandle,
                                                                "dn" to (state.myDisplayName ?: ""),
                                                                "fwd_from" to fwdFrom,
                                                                "fwd_ts" to m.timestamp,
                                                            )
                                                            if (m.text.isNotEmpty()) payload["msg"] = m.text
                                                            if (isFile) {
                                                                val f = m.file!!
                                                                val fileMap = mutableMapOf<String, Any?>(
                                                                    "name" to f.name, "size" to f.size,
                                                                    "mime" to f.mime, "key" to f.key, "iv" to f.iv,
                                                                )
                                                                if (f.cid.isNotEmpty() && !f.cid.startsWith("inline-")) fileMap["cid"] = f.cid
                                                                if (f.data != null) fileMap["data"] = f.data
                                                                payload["file"] = fileMap
                                                            }
                                                            val dedupKey = "$ts:fwd:${m.timestamp}:true"
                                                            val entity = com.privimemobile.chat.db.entities.MessageEntity(
                                                                conversationId = fwdConv.id,
                                                                text = m.text.ifEmpty { null },
                                                                timestamp = ts,
                                                                sent = true,
                                                                type = msgType,
                                                                senderHandle = state.myHandle,
                                                                sbbsDedupKey = dedupKey,
                                                                fwdFrom = fwdFrom,
                                                                fwdTs = m.timestamp,
                                                            )
                                                            val msgId = com.privimemobile.chat.ChatService.db!!.messageDao().insert(entity)
                                                            if (isFile && msgId != -1L) {
                                                                val f = m.file!!
                                                                com.privimemobile.chat.ChatService.db!!.attachmentDao().insert(
                                                                    com.privimemobile.chat.db.entities.AttachmentEntity(
                                                                        messageId = msgId,
                                                                        conversationId = fwdConv.id,
                                                                        ipfsCid = f.cid.ifEmpty { "inline-${System.currentTimeMillis().toString(36)}" },
                                                                        encryptionKey = f.key,
                                                                        encryptionIv = f.iv,
                                                                        fileName = f.name,
                                                                        fileSize = f.size,
                                                                        mimeType = f.mime,
                                                                        inlineData = f.data,
                                                                    )
                                                                )
                                                            }
                                                            lastPreview = if (isFile) "\uD83D\uDCCE ${m.file!!.name.ifEmpty { "File" }}" else m.text.take(100)
                                                            lastTs = ts
                                                            com.privimemobile.chat.ChatService.sbbs.sendWithRetry(contact.walletId!!, payload)
                                                            // Delay between forwards to avoid SBBS rate-limiting
                                                            if (i < allFwdMsgs.size - 1) delay(2000)
                                                        }
                                                        if (lastTs > 0) {
                                                            com.privimemobile.chat.ChatService.db!!.conversationDao().updateLastMessage(fwdConv.id, lastTs, lastPreview)
                                                        }
                                                        val count = allFwdMsgs.size
                                                        Toast.makeText(context, "Forwarded $count message${if (count > 1) "s" else ""} to @${toHandle}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                forwardingMsg = null
                                                forwardingMsgs = emptyList()
                                                // Navigate to the forwarded-to chat
                                                onNavigateToChat(contact.handle)
                                            }
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // Avatar circle
                                        Box(
                                            Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(C.accent),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                (contact.displayName?.ifEmpty { null } ?: contact.handle).first().uppercase(),
                                                color = C.textDark, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                            )
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text("@${contact.handle}", color = C.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                            if (!contact.displayName.isNullOrEmpty()) {
                                                Text(contact.displayName!!, color = C.textSecondary, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                    HorizontalDivider(color = C.border, thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
            )
        }
    } // end Column

    // ── Overlays rendered OUTSIDE Column, filling entire screen ──

    // Attachment picker bottom sheet (Telegram-style)
    if (showAttachPicker) {
        AttachmentPickerSheet(
            onDismiss = { showAttachPicker = false },
            onPickGallery = {
                showAttachPicker = false
                galleryPickerLauncher.launch("image/*")
            },
            onPickFile = {
                showAttachPicker = false
                filePickerLauncher.launch("*/*")
            },
            onImageSelected = { uri -> handlePickedUri(uri) },
            onMultiImageSelected = { uris ->
                showAttachPicker = false
                // Send images one by one with buffer
                scope.launch {
                    uris.forEach { uri ->
                        handlePickedUri(uri)
                        // Wait for upload to complete + buffer between sends
                        delay(2000)
                        // Auto-send each pending file
                        handleSend()
                        delay(1000)
                    }
                }
            },
        )
    }

    // Fullscreen image viewer
    if (fullscreenImage != null) {
        FullscreenImageViewer(
            filePath = fullscreenImage!!.first,
            fileName = fullscreenImage!!.second,
            onDismiss = { fullscreenImage = null },
            onSave = {
                val mime = when {
                    fullscreenImage!!.second.endsWith(".png", true) -> "image/png"
                    fullscreenImage!!.second.endsWith(".gif", true) -> "image/gif"
                    fullscreenImage!!.second.endsWith(".webp", true) -> "image/webp"
                    else -> "image/jpeg"
                }
                saveFileToDownloads(context, fullscreenImage!!.first, fullscreenImage!!.second, mime)
            },
        )
    }
    } // end Box

@Composable
private fun TickIndicator(read: Boolean, delivered: Boolean) {
    val tickText = if (read || delivered) "\u2713\u2713" else "\u2713"
    val tickColor = if (read) C.accent else C.textSecondary
    // Animate scale when status upgrades
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 800f),
        label = "tickScale",
    )
    Text(tickText, color = tickColor, fontSize = 10.sp,
        modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale))
}

@Composable
private fun MessageBubble(
    msg: ChatMessage,
    filePath: String?,
    downloadStatus: String,
    onDownload: (cid: String, key: String, iv: String, mime: String, inlineData: String?) -> Unit,
    onReply: () -> Unit = {},
    onTap: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onFullscreenImage: () -> Unit = {},
    reactions: List<Triple<String, Int, Boolean>> = emptyList(),
    onRemoveReaction: (emoji: String, msgTs: Long) -> Unit = { _, _ -> },
    isHighlighted: Boolean = false,
    isSelected: Boolean = false,
    onPollVote: (optionIndex: Int) -> Unit = {},
    myHandle: String? = null,
    isGroupMode: Boolean = false,
    onSenderTap: (String) -> Unit = {},
    groupMemberNames: Map<String, String> = emptyMap(),
) {
    val context = LocalContext.current
    val bubbleView = androidx.compose.ui.platform.LocalView.current
    val isMine = msg.sent
    val isSticker = msg.type == "sticker"
    val isFileMsg = (msg.file?.cid ?: "").isNotEmpty()
    val fileMime = msg.file?.mime ?: ""
    val isImage = (msg.type == "file" || msg.type == "sticker") && Helpers.isImageMime(fileMime)

    // Group service messages (join/leave/kick/ban) — centered pill
    if (msg.type == "group_service") {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = msg.text,
                color = C.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier
                    .background(C.card, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        return
    }

    // Group invite message — card with Accept/Decline buttons
    if (msg.type == "group_invite" && msg.pollData != null) {
        val inviteData = remember(msg.pollData) {
            try { org.json.JSONObject(msg.pollData) } catch (_: Exception) { null }
        }
        val inviteGroupId = inviteData?.optString("group_id") ?: ""
        val inviteGroupName = inviteData?.optString("group_name") ?: "Group"
        val invitedBy = inviteData?.optString("invited_by") ?: msg.from
        val inviteMemberCount = inviteData?.optInt("member_count", 0) ?: 0
        val invitePassword = inviteData?.optString("join_password")
        var joining by remember { mutableStateOf(false) }
        val inviteScope = rememberCoroutineScope()
        // Use msg.edited as persistent "responded" flag (survives navigation)
        val alreadyResponded = msg.edited

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = C.card),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 16.dp),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("\uD83D\uDC65 Group Invite", color = C.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(inviteGroupName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("$inviteMemberCount members \u2022 Invited by @$invitedBy", color = C.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                if (!alreadyResponded && !joining) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                joining = true
                                // Mark as responded in DB immediately
                                inviteScope.launch {
                                    com.privimemobile.chat.ChatService.db?.messageDao()?.markEdited(msg.id.toLong())
                                }
                                com.privimemobile.chat.ChatService.groups.joinGroup(inviteGroupId, joinPassword = invitePassword) { s, e ->
                                    if (s) {
                                        android.widget.Toast.makeText(context, "Joining $inviteGroupName...", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        joining = false
                                        android.widget.Toast.makeText(context, e ?: "Join failed", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = inviteGroupId.isNotEmpty(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                        ) {
                            Text("Accept", color = C.textDark, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                // Mark as responded in DB
                                inviteScope.launch {
                                    com.privimemobile.chat.ChatService.db?.messageDao()?.markEdited(msg.id.toLong())
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text("Decline", color = C.textSecondary)
                        }
                    }
                } else if (joining) {
                    Text("Joining...", color = C.accent, fontSize = 13.sp)
                } else {
                    Text("\u2705 Responded", color = C.textMuted, fontSize = 13.sp)
                }
            }
        }
        return
    }

    // Check if message is emoji-only (1-3 emojis, no other text) for large display
    val isEmojiOnly = remember(msg.text) {
        val t = msg.text.trim()
        if (t.isEmpty() || (msg.type != "dm" && msg.type != "group_msg") || msg.file != null || msg.isTip || msg.reply != null || msg.fwdFrom != null) false
        else {
            // Count emoji codepoints
            val codePoints = t.codePoints().toArray()
            codePoints.size in 1..3 && codePoints.all { cp ->
                Character.getType(cp).let { type ->
                    type == Character.OTHER_SYMBOL.toInt() ||
                    type == Character.SURROGATE.toInt() ||
                    type == Character.NON_SPACING_MARK.toInt() ||
                    type == Character.FORMAT.toInt() ||
                    (cp in 0x1F000..0x1FFFF) || // emoticons, symbols
                    (cp in 0x2600..0x27BF) ||    // misc symbols
                    (cp in 0xFE00..0xFE0F) ||    // variation selectors
                    (cp in 0x200D..0x200D) ||     // ZWJ
                    (cp in 0xE0020..0xE007F)      // tags
                }
            }
        }
    }

    // Swipe-left to reply
    var offsetX by remember { mutableStateOf(0f) }
    var swiped by remember { mutableStateOf(false) }
    var swipeHapticFired by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = {
                        bubbleView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onLongPress()
                    },
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX < -80f && !swiped) {
                            swiped = true
                            bubbleView.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            onReply()
                        }
                        offsetX = 0f
                        swiped = false
                    },
                    onDragCancel = {
                        offsetX = 0f
                        swiped = false
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        val prev = offsetX
                        offsetX = (offsetX + dragAmount).coerceIn(-150f, 0f)
                        // Haptic buzz when crossing swipe threshold
                        if (prev > -80f && offsetX <= -80f && !swipeHapticFired) {
                            swipeHapticFired = true
                            bubbleView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        } else if (offsetX > -80f) {
                            swipeHapticFired = false
                        }
                    },
                )
            },
    ) {
        // Reply arrow hint (visible when swiping) — always on the right side
        if (offsetX < -20f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(horizontal = 8.dp),
            ) {
                Text(
                    "\u21A9",  // ↩ reply arrow
                    color = C.accent.copy(alpha = (-offsetX / 100f).coerceIn(0f, 1f)),
                    fontSize = 18.sp,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = (offsetX / 3f).dp),
            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        ) {
            // Group mode: show sender avatar for non-self messages (tap → contact info)
            if (isGroupMode && !isMine) {
                Box(modifier = Modifier.clickable { onSenderTap(msg.from) }) {
                    com.privimemobile.ui.components.AvatarDisplay(
                        handle = msg.from,
                        size = 32.dp,
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
            // Wrap group sender name + bubble content
            Column(
                horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
                modifier = Modifier.widthIn(max = 300.dp),
            ) {
            // Group mode: sender name (display name or @handle, colored, tap → contact info)
            if (isGroupMode && !isMine && msg.from.isNotEmpty()) {
                val senderColors = listOf(
                    Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFEF5350), Color(0xFFAB47BC),
                    Color(0xFF42A5F5), Color(0xFFFF7043), Color(0xFF66BB6A), Color(0xFFEC407A),
                    Color(0xFFFFA726), Color(0xFF78909C),
                )
                val senderColor = senderColors[kotlin.math.abs(msg.from.hashCode()) % senderColors.size]
                val senderDisplayName = groupMemberNames[msg.from]
                Text(
                    senderDisplayName ?: "@${msg.from}",
                    color = senderColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 1.dp).clickable { onSenderTap(msg.from) },
                )
            }
            // Sticker display (no bubble, larger image — show placeholder even before attachment loads)
            // Sticker pack message — show card with Save button
            if (msg.type == "sticker_pack") {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isMine) Color(0xFF1B3A4B) else C.card),
                    modifier = Modifier.widthIn(max = 260.dp).clickable { onTap() },
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("\uD83D\uDCE6 Sticker Pack", color = C.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(msg.stickerPackName ?: "Stickers", color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("${msg.stickerPackTotal} stickers", color = C.textSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        if (!isMine && filePath != null) {
                            val packSaved = remember(msg.stickerPackName) {
                                val dir = java.io.File(context.filesDir, "stickers/${msg.stickerPackName}")
                                dir.exists() && (dir.listFiles()?.isNotEmpty() == true)
                            }
                            Button(
                                onClick = {
                                    // Extract ZIP and save stickers
                                    try {
                                        val packDir = java.io.File(context.filesDir, "stickers/${msg.stickerPackName}").also { it.mkdirs() }
                                        val src = java.io.File(filePath!!)
                                        var count = 0
                                        java.util.zip.ZipInputStream(src.inputStream()).use { zis ->
                                            var entry = zis.nextEntry
                                            while (entry != null) {
                                                val name = entry.name.lowercase()
                                                if (!entry.isDirectory && (name.endsWith(".webp") || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".tgs"))) {
                                                    val bytes = zis.readBytes()
                                                    val dest = java.io.File(packDir, entry.name)
                                                    if (!dest.exists()) {
                                                        dest.writeBytes(bytes)
                                                        count++
                                                    }
                                                }
                                                zis.closeEntry()
                                                entry = zis.nextEntry
                                            }
                                        }
                                        Toast.makeText(context, "Saved $count stickers to \"${msg.stickerPackName}\"", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (packSaved) "Update Pack" else "Save Pack", color = C.textDark)
                            }
                        } else if (!isMine) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = C.accent, strokeWidth = 2.dp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Text(formatMessageTime(msg.timestamp), color = C.textSecondary, fontSize = 10.sp)
                            if (isMine) { Spacer(Modifier.width(6.dp)); TickIndicator(read = msg.read, delivered = msg.delivered) }
                        }
                    }
                }
            } else
            if (isSticker) {
                val isTgsSticker = filePath != null && (filePath.endsWith(".tgs", ignoreCase = true)
                    || msg.file?.mime == "application/x-tgsticker"
                    || msg.file?.name?.endsWith(".tgs", ignoreCase = true) == true
                    || run {
                        // Detect GZIP magic bytes (TGS is gzipped Lottie)
                        try {
                            val f = java.io.File(filePath)
                            if (f.exists() && f.length() > 2) {
                                val header = ByteArray(2)
                                f.inputStream().use { it.read(header) }
                                header[0] == 0x1f.toByte() && header[1] == 0x8b.toByte()
                            } else false
                        } catch (_: Exception) { false }
                    })
                Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
                    if (filePath != null && isTgsSticker) {
                        // Animated TGS sticker
                        val lottieJson = remember(filePath) {
                            try { java.util.zip.GZIPInputStream(java.io.File(filePath).inputStream()).bufferedReader().readText() }
                            catch (_: Exception) { null }
                        }
                        if (lottieJson != null) {
                            val composition by com.airbnb.lottie.compose.rememberLottieComposition(
                                com.airbnb.lottie.compose.LottieCompositionSpec.JsonString(lottieJson)
                            )
                            com.airbnb.lottie.compose.LottieAnimation(
                                composition = composition,
                                iterations = com.airbnb.lottie.compose.LottieConstants.IterateForever,
                                modifier = Modifier.size(180.dp).clip(RoundedCornerShape(8.dp))
                                    .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }, onLongPress = { onLongPress() }) },
                            )
                        }
                    } else if (filePath != null) {
                        // Static sticker (WebP/PNG)
                        AsyncImage(
                            model = filePath,
                            contentDescription = "Sticker",
                            modifier = Modifier
                                .size(180.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }, onLongPress = { onLongPress() }) },
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        // Loading placeholder
                        Box(
                            modifier = Modifier.size(180.dp).clip(RoundedCornerShape(8.dp))
                                .background(C.card.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = C.accent, strokeWidth = 2.dp)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        if (msg.stickerEmoji != null) { Text(msg.stickerEmoji, fontSize = 10.sp); Spacer(Modifier.width(3.dp)) }
                        if (msg.pinned) { Text("\uD83D\uDCCC", fontSize = 10.sp); Spacer(Modifier.width(3.dp)) }
                        Text(formatMessageTime(msg.timestamp), color = C.textSecondary, fontSize = 10.sp)
                        if (isMine) {
                            Spacer(Modifier.width(6.dp))
                            TickIndicator(read = msg.read, delivered = msg.delivered)
                        }
                    }
                    if (msg.stickerPackName != null) {
                        Text(msg.stickerPackName, color = C.textMuted, fontSize = 9.sp)
                    }
                }
            } else
            // Large emoji display (no bubble)
            if (isEmojiOnly) {
                Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
                    Text(msg.text, fontSize = 48.sp, lineHeight = 56.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (msg.pinned) { Text("\uD83D\uDCCC", fontSize = 10.sp); Spacer(Modifier.width(3.dp)) }
                        Text(formatMessageTime(msg.timestamp), color = C.textSecondary, fontSize = 10.sp)
                        if (isMine) {
                            Spacer(Modifier.width(6.dp))
                            TickIndicator(read = msg.read, delivered = msg.delivered)
                        }
                    }
                }
            } else
            Card(
                shape = RoundedCornerShape(
                    topStart = 12.dp,
                    topEnd = 12.dp,
                    bottomStart = if (isMine) 12.dp else 4.dp,
                    bottomEnd = if (isMine) 4.dp else 12.dp,
                ),
                colors = CardDefaults.cardColors(
                    containerColor = androidx.compose.animation.animateColorAsState(
                        targetValue = when {
                            isSelected -> C.accent.copy(alpha = 0.25f)
                            isHighlighted -> C.accent.copy(alpha = 0.3f)
                            isMine -> Color(0xFF1B3A4B)
                            else -> C.card
                        },
                        animationSpec = tween(180),
                        label = "bubbleColor",
                    ).value,
                ),
                modifier = Modifier.widthIn(max = if (isImage) 300.dp else 280.dp),
            ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // Forwarded label
                if (msg.fwdFrom != null) {
                    Text(
                        "Forwarded from @${msg.fwdFrom}",
                        color = C.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }

                // Tip label — parse →@handle prefix for group tips
                if (msg.isTip) {
                    val assetLabel = "${Helpers.formatBeam(msg.tipAmount)} ${com.privimemobile.wallet.assetTicker(msg.tipAssetId)}"
                    val tipTarget = if (msg.text.startsWith("\u2192@")) {
                        msg.text.lineSequence().first().removePrefix("\u2192")
                    } else null
                    val tipLabel = if (tipTarget != null) "Tip to $tipTarget: $assetLabel" else "Tip: $assetLabel"
                    Text(
                        tipLabel,
                        color = C.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }

                // Reply display
                if (msg.reply != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = C.bg.copy(alpha = 0.5f),
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(8.dp)
                                .height(IntrinsicSize.Min),
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .fillMaxHeight()
                                    .background(C.accent)
                            )
                            Text(
                                msg.reply,
                                color = C.textSecondary,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }

                // File content
                if (isFileMsg) {
                    val fName = msg.file?.name ?: ""
                    val fMime = msg.file?.mime ?: "application/octet-stream"
                    FileContent(
                        cid = msg.file?.cid ?: "",
                        fileName = fName,
                        fileSize = msg.file?.size ?: 0L,
                        filePath = filePath,
                        downloadStatus = downloadStatus,
                        isImage = isImage,
                        onDownload = {
                            onDownload(
                                msg.file?.cid ?: "",
                                msg.file?.key ?: "",
                                msg.file?.iv ?: "",
                                fMime,
                                msg.file?.data,
                            )
                        },
                        onSave = {
                            if (filePath != null) {
                                saveFileToDownloads(context, filePath, fName, fMime)
                            }
                        },
                        onFullscreen = onFullscreenImage,
                    )
                }

                // Poll display
                if (msg.type == "poll" && msg.pollData != null) {
                    val pollQuestion = remember(msg.pollData) {
                        try { org.json.JSONObject(msg.pollData).optString("question", msg.text) } catch (_: Exception) { msg.text }
                    }
                    data class PollOpt(val text: String, val voteCount: Int, val voters: List<String>)
                    val pollOptions = remember(msg.pollData) {
                        try {
                            val opts = org.json.JSONObject(msg.pollData).optJSONArray("options")
                            val list = mutableListOf<PollOpt>()
                            if (opts != null) {
                                for (i in 0 until opts.length()) {
                                    val opt = opts.getJSONObject(i)
                                    val v = opt.optJSONArray("voters")
                                    val voterList = mutableListOf<String>()
                                    if (v != null) { for (j in 0 until v.length()) voterList.add(v.getString(j)) }
                                    list.add(PollOpt(opt.optString("text", ""), voterList.size, voterList))
                                }
                            }
                            list.toList()
                        } catch (_: Exception) { emptyList<PollOpt>() }
                    }
                    val totalVotes = pollOptions.sumOf { it.voteCount }
                    Text("\uD83D\uDCCA Poll", color = C.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(pollQuestion, color = C.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                    pollOptions.forEachIndexed { optIdx, opt ->
                        val myVote = myHandle != null && myHandle in opt.voters
                        val pct = if (totalVotes > 0) (opt.voteCount * 100 / totalVotes) else 0
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (myVote) C.accent.copy(alpha = 0.2f) else C.bg.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                .clickable { onPollVote(optIdx) },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (myVote) {
                                    Text("\u2713 ", color = C.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(opt.text, color = C.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                if (totalVotes > 0) {
                                    Text("$pct%", color = if (myVote) C.accent else C.textSecondary, fontSize = 12.sp)
                                    Spacer(Modifier.width(4.dp))
                                }
                                if (opt.voteCount > 0) {
                                    Text("${opt.voteCount}", color = C.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                // Text content — strip →@handle prefix from tip messages
                val displayText = if (msg.isTip && msg.text.startsWith("\u2192@")) {
                    val lines = msg.text.lines()
                    lines.drop(1).joinToString("\n").trim() // skip "→@handle" line, show caption only
                } else msg.text
                if (displayText.isNotEmpty()) {
                    Text(
                        text = parseMarkdown(displayText),
                        color = C.text, fontSize = 15.sp, lineHeight = 20.sp,
                    )
                }
                }

                // Meta row (time + read status)
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (msg.pinned) {
                        Text("\uD83D\uDCCC", fontSize = 10.sp)  // 📌
                        Spacer(Modifier.width(3.dp))
                    }
                    if (msg.expiresAt > 0) {
                        Text("\u23F3", fontSize = 10.sp)  // ⏳
                        Spacer(Modifier.width(3.dp))
                    }
                    if (msg.edited) {
                        Text(
                            "edited",
                            color = C.textMuted,
                            fontSize = 10.sp,
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        formatMessageTime(msg.timestamp),
                        color = C.textSecondary,
                        fontSize = 10.sp,
                    )
                    // Status ticks or schedule indicator
                    if (isMine) {
                        Spacer(Modifier.width(6.dp))
                        if (msg.scheduledAt > 0) {
                            val sdf = remember { java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault()) }
                            Text(
                                "\uD83D\uDD52 ${sdf.format(java.util.Date(msg.scheduledAt * 1000))}",
                                color = C.accent,
                                fontSize = 9.sp,
                            )
                        } else {
                            TickIndicator(read = msg.read, delivered = msg.delivered)
                        }
                    }
                }
            }

        }
        }  // close group sender name/bubble wrapper Column
        }  // close Card Row
    }  // close Box (swipe container)

    // Reaction pills OUTSIDE gesture area so taps work
    if (reactions.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, start = if (isMine) 0.dp else 4.dp, end = if (isMine) 4.dp else 0.dp),
            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                reactions.forEach { (emoji, count, mine) ->
                    // Scale-in animation for each reaction pill
                    var reactionVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { reactionVisible = true }
                    val reactionScale by animateFloatAsState(
                        targetValue = if (reactionVisible) 1f else 0f,
                        animationSpec = spring(dampingRatio = 0.4f, stiffness = 600f),
                        label = "rxScale",
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (mine) C.accent.copy(alpha = 0.2f) else C.border,
                        modifier = Modifier
                            .graphicsLayer { scaleX = reactionScale; scaleY = reactionScale }
                            .then(if (mine) Modifier.clickable {
                                onRemoveReaction(emoji, msg.timestamp)
                            } else Modifier),
                    ) {
                        Text(
                            if (count > 1) "$emoji $count" else emoji,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
    }  // close Column wrapper
}

@Composable
private fun FileContent(
    cid: String,
    fileName: String,
    fileSize: Long,
    filePath: String?,
    downloadStatus: String,
    isImage: Boolean,
    onDownload: () -> Unit,
    onSave: () -> Unit,
    onFullscreen: () -> Unit = {},
) {
    Column(modifier = Modifier.padding(bottom = 2.dp)) {
        if (isImage && filePath != null) {
            // Show cached image — tap to fullscreen
            AsyncImage(
                model = "file://$filePath",
                contentDescription = fileName,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 260.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(C.border)
                    .clickable { onFullscreen() },
                contentScale = ContentScale.Crop,
            )
        } else if (isImage && (downloadStatus == "downloading" || downloadStatus == "decrypting")) {
            // Loading placeholder
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                shape = RoundedCornerShape(8.dp),
                color = C.border,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = C.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (downloadStatus == "downloading") "Downloading..." else "Decrypting...",
                        color = C.textSecondary,
                        fontSize = 11.sp,
                    )
                }
            }
        } else if (isImage && downloadStatus == "error") {
            // Error — tap to retry
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clickable { onDownload() },
                shape = RoundedCornerShape(8.dp),
                color = C.border,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("\uD83D\uDD04", fontSize = 24.sp)
                    Text("Tap to retry", color = C.error, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Sender may be offline", color = C.textSecondary, fontSize = 10.sp)
                }
            }
        } else {
            // Generic file card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { if (filePath != null) onSave() else onDownload() },
                shape = RoundedCornerShape(8.dp),
                color = C.border,
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (isImage) "\uD83D\uDDBC" else "\uD83D\uDCCE",
                        fontSize = 24.sp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            fileName,
                            color = C.text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            buildString {
                                append(Helpers.formatFileSize(fileSize))
                                if (downloadStatus == "error") append(" \u2014 tap to retry")
                            },
                            color = C.textSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    when (downloadStatus) {
                        "downloading", "decrypting" -> {
                            CircularProgressIndicator(
                                color = C.accent,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        "error" -> {
                            Text("Retry", color = C.error, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        else -> {
                            if (filePath != null) {
                                Text("Save", color = C.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            } else {
                                Text("Download", color = C.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class PendingFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String,
)

private fun getFileInfo(context: Context, uri: Uri): FileInfo? {
    return try {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIndex >= 0) it.getString(nameIndex) else "file"
                val size = if (sizeIndex >= 0) it.getLong(sizeIndex) else 0L
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                FileInfo(name, size, mimeType)
            } else null
        }
    } catch (_: Exception) {
        null
    }
}

private data class FileInfo(val name: String, val size: Long, val mimeType: String)

/**
 * Save a cached file to the public Downloads folder using MediaStore.
 * Works on API 29+ (scoped storage) and falls back for older APIs.
 */
private fun saveFileToDownloads(context: Context, srcPath: String, fileName: String, mimeType: String = "application/octet-stream") {
    try {
        val srcFile = java.io.File(srcPath)
        if (!srcFile.exists()) {
            Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Scoped storage (API 29+)
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    srcFile.inputStream().use { input -> input.copyTo(out) }
                }
                Toast.makeText(context, "Saved to Downloads/$fileName", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Legacy storage (API < 29)
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val destFile = java.io.File(downloadsDir, fileName)
            srcFile.inputStream().use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            Toast.makeText(context, "Saved to Downloads/$fileName", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

// ── Fullscreen image viewer (Telegram-style: black bg, tap to dismiss) ──

@Composable
private fun FullscreenImageViewer(
    filePath: String,
    fileName: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    var showBars by remember { mutableStateOf(true) }
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        if (scale > 1f) {
            offsetX += panChange.x
            offsetY += panChange.y
        } else {
            offsetX = 0f
            offsetY = 0f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = "file://$filePath",
            contentDescription = fileName,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                )
                .transformable(state = transformState)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { showBars = !showBars },
                        onDoubleTap = {
                            if (scale > 1.1f) {
                                scale = 1f; offsetX = 0f; offsetY = 0f
                            } else {
                                scale = 2.5f
                            }
                        },
                    )
                },
            contentScale = ContentScale.Fit,
        )

        // Top bar with close + save — toggled by tap
        androidx.compose.animation.AnimatedVisibility(
            visible = showBars,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close", tint = Color.White)
            }
            Text(
                fileName,
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSave) {
                Icon(Icons.Default.Download, contentDescription = "Save", tint = Color.White)
            }
        }
        } // end AnimatedVisibility
    }
}

// ── Attachment picker bottom sheet (Telegram-style Gallery/Files tabs) ──

@Composable
private fun AttachmentPickerSheet(
    onDismiss: () -> Unit,
    onPickGallery: () -> Unit,
    onPickFile: () -> Unit,
    onImageSelected: (Uri) -> Unit,
    onMultiImageSelected: (List<Uri>) -> Unit = {},
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0=Gallery, 1=Files

    // Check gallery permission
    val hasPermission = remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission.value = granted }

    // Load gallery images from MediaStore
    val galleryImages = remember { mutableStateOf<List<Uri>>(emptyList()) }
    LaunchedEffect(hasPermission.value) {
        if (hasPermission.value) {
            withContext(Dispatchers.IO) {
                galleryImages.value = loadGalleryImages(context, limit = 60)
            }
        }
    }

    // Swipe-to-dismiss state
    var sheetOffsetY by remember { mutableStateOf(0f) }

    // Full-screen overlay that acts as a bottom sheet
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = (0.4f * (1f - (sheetOffsetY / 600f).coerceIn(0f, 1f)))))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .align(Alignment.BottomCenter)
                .offset(y = sheetOffsetY.coerceAtLeast(0f).dp)
                .clickable(enabled = false) {}, // block click-through
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = C.card,
            shadowElevation = 8.dp,
        ) {
            Column {
                // Drag handle — swipe down to dismiss
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (sheetOffsetY > 100f) {
                                        onDismiss()
                                    }
                                    sheetOffsetY = 0f
                                },
                                onDragCancel = { sheetOffsetY = 0f },
                                onVerticalDrag = { _, dragAmount ->
                                    sheetOffsetY = (sheetOffsetY + dragAmount).coerceAtLeast(0f)
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(C.textMuted),
                    )
                }

                // Tab row — Gallery | Files
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                ) {
                    listOf(
                        Triple(0, "Gallery", Icons.Default.PhotoLibrary),
                        Triple(1, "Files", Icons.Default.Description),
                    ).forEach { (index, label, icon) ->
                        val isSelected = selectedTab == index
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedTab = index },
                            color = if (isSelected) C.accent.copy(alpha = 0.15f) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    icon,
                                    contentDescription = label,
                                    tint = if (isSelected) C.accent else C.textSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    label,
                                    color = if (isSelected) C.accent else C.textSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                when (selectedTab) {
                    0 -> {
                        // Gallery tab
                        if (!hasPermission.value) {
                            // Request permission
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("Gallery access needed", color = C.textSecondary, fontSize = 14.sp)
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            Manifest.permission.READ_MEDIA_IMAGES
                                        } else {
                                            Manifest.permission.READ_EXTERNAL_STORAGE
                                        }
                                        permissionLauncher.launch(perm)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                                ) {
                                    Text("Grant Access", color = C.textDark)
                                }
                                Spacer(Modifier.height(8.dp))
                                TextButton(onClick = onPickGallery) {
                                    Text("Or pick from system gallery", color = C.accent, fontSize = 13.sp)
                                }
                            }
                        } else if (galleryImages.value.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("No images found", color = C.textMuted, fontSize = 14.sp)
                            }
                        } else {
                            val selectedUris = remember { mutableStateListOf<Uri>() }
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Send button when multiple selected
                                if (selectedUris.size > 0) {
                                    Surface(
                                        color = C.accent,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (selectedUris.size == 1) {
                                                    onImageSelected(selectedUris.first())
                                                } else {
                                                    onMultiImageSelected(selectedUris.toList())
                                                }
                                            },
                                    ) {
                                        Text(
                                            "Send ${selectedUris.size} photo${if (selectedUris.size > 1) "s" else ""}",
                                            color = C.textDark,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        )
                                    }
                                }
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    items(galleryImages.value) { uri ->
                                        val isSelected = uri in selectedUris
                                        Box(
                                            modifier = Modifier
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(4.dp))
                                                .clickable {
                                                    if (isSelected) selectedUris.remove(uri)
                                                    else selectedUris.add(uri)
                                                },
                                        ) {
                                            AsyncImage(
                                                model = uri,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop,
                                            )
                                            // Selection overlay + number badge
                                            if (isSelected) {
                                                Box(modifier = Modifier.fillMaxSize().background(C.accent.copy(alpha = 0.3f)))
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(4.dp)
                                                        .size(22.dp)
                                                        .clip(CircleShape)
                                                        .background(C.accent),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Text(
                                                        "${selectedUris.indexOf(uri) + 1}",
                                                        color = C.textDark, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // Files tab — action buttons
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // Document picker
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onPickFile() },
                                color = C.bg,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.Description,
                                        contentDescription = null,
                                        tint = C.accent,
                                        modifier = Modifier.size(28.dp),
                                    )
                                    Spacer(Modifier.width(14.dp))
                                    Column {
                                        Text("Browse Files", color = C.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                        Text("Documents, PDFs, and more", color = C.textSecondary, fontSize = 12.sp)
                                    }
                                }
                            }

                            // Image from gallery via system picker
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onPickGallery() },
                                color = C.bg,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Default.PhotoLibrary,
                                        contentDescription = null,
                                        tint = C.accent,
                                        modifier = Modifier.size(28.dp),
                                    )
                                    Spacer(Modifier.width(14.dp))
                                    Column {
                                        Text("Photo / Video", color = C.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                        Text("Pick from gallery", color = C.textSecondary, fontSize = 12.sp)
                                    }
                                }
                            }

                            Spacer(Modifier.weight(1f))

                            Text(
                                "Max file size: ${Config.MAX_FILE_SIZE / 1024 / 1024}MB",
                                color = C.textMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Query MediaStore for recent images, sorted newest first. */
private fun loadGalleryImages(context: Context, limit: Int = 60): List<Uri> {
    val images = mutableListOf<Uri>()
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    val projection = arrayOf(MediaStore.Images.Media._ID)
    val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

    try {
        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            var count = 0
            while (cursor.moveToNext() && count < limit) {
                val id = cursor.getLong(idCol)
                val uri = android.content.ContentUris.withAppendedId(collection, id)
                images.add(uri)
                count++
            }
        }
    } catch (e: Exception) {
        Log.w("AttachmentPicker", "Failed to load gallery: ${e.message}")
    }
    return images
}

// Date formatting
private val msgTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
/** Telegram-style menu item with prominent touch highlight. */
@Composable
private fun MenuItemRow(
    text: String,
    color: Color = C.text,
    onClick: () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (pressed) C.accent.copy(alpha = 0.12f) else Color.Transparent)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onClick() },
                )
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = color, fontSize = 15.sp)
    }
}

private fun formatMessageTime(ts: Long): String {
    if (ts <= 0) return ""
    return msgTimeFormat.format(Date(ts * 1000))
}

/** Parse simple markdown: **bold**, *italic*, `code`, ~~strikethrough~~ */
private fun parseMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                // **bold**
                i + 1 < text.length && text[i] == '*' && text[i + 1] == '*' -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i + 2) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else { append(text[i]); i++ }
                }
                // *italic*
                text[i] == '*' -> {
                    val end = text.indexOf('*', i + 1)
                    if (end > i + 1) {
                        withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else { append(text[i]); i++ }
                }
                // ~~strikethrough~~
                i + 1 < text.length && text[i] == '~' && text[i + 1] == '~' -> {
                    val end = text.indexOf("~~", i + 2)
                    if (end > i + 2) {
                        withStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else { append(text[i]); i++ }
                }
                // `code`
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i + 1) {
                        withStyle(SpanStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            background = Color(0x20FFFFFF),
                        )) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else { append(text[i]); i++ }
                }
                // @mention highlighting
                text[i] == '@' && (i == 0 || text[i - 1] == ' ' || text[i - 1] == '\n') -> {
                    var j = i + 1
                    while (j < text.length && (text[j].isLetterOrDigit() || text[j] == '_')) j++
                    if (j > i + 1) {
                        withStyle(SpanStyle(color = C.accent, fontWeight = FontWeight.SemiBold)) {
                            append(text.substring(i, j))
                        }
                        i = j
                    } else { append(text[i]); i++ }
                }
                else -> { append(text[i]); i++ }
            }
        }
    }
}

private val dateSepFormat = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
private fun formatDateSeparator(ts: Long): String {
    if (ts <= 0) return ""
    val date = Date(ts * 1000)
    val cal = Calendar.getInstance()
    val today = Calendar.getInstance()

    cal.time = date
    return when {
        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Today"
        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) - 1 -> "Yesterday"
        else -> dateSepFormat.format(date)
    }
}

/** Format disappearing timer value to human-readable label. */
internal fun formatTimerLabel(seconds: Int): String = when {
    seconds <= 0 -> "Off"
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60}m"
    seconds < 86400 -> "${seconds / 3600}h"
    else -> "${seconds / 86400}d"
}
