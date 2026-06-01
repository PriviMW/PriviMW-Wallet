package com.privimemobile.ui.chat

import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.withFrameNanos
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import com.privimemobile.R
import com.privimemobile.protocol.*
import com.privimemobile.ui.chat.format.formatDateSeparator
import com.privimemobile.ui.chat.format.formatMessageTime
import com.privimemobile.ui.chat.format.formatTimerLabel
import com.privimemobile.ui.chat.format.parseMarkdown
import com.privimemobile.ui.chat.media.AttachmentPickerSheet
import com.privimemobile.ui.chat.media.FullscreenImageViewer
import com.privimemobile.ui.chat.media.ImagePreviewData
import com.privimemobile.ui.chat.media.ImagePreviewSheet
import com.privimemobile.ui.chat.media.saveFileToDownloads
import com.privimemobile.ui.chat.input.VoiceLockIndicator
import com.privimemobile.ui.chat.input.VoicePreviewBar
import com.privimemobile.ui.chat.input.VoiceRecordingBar
import com.privimemobile.ui.chat.input.formatVoiceDuration
import com.privimemobile.ui.chat.menu.MenuItemRow
import com.privimemobile.ui.chat.message.FileContent
import com.privimemobile.ui.chat.message.MessageBubble
import com.privimemobile.ui.chat.message.TickIndicator
import com.privimemobile.ui.chat.scroll.ChatListDerive
import com.privimemobile.ui.chat.scroll.ChatScrollMath
import com.privimemobile.ui.components.VoiceMessageBubble
import com.privimemobile.ui.components.MicButton
import com.privimemobile.chat.voice.VoiceWaveformView
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.compose.BackHandler
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
            groupConvId = com.privimemobile.chat.ChatService.groups.getOrCreateGroupConversation(groupId, grp?.name ?: context.getString(R.string.chat_group_name_fallback))

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
    // Resolve display names: group members first, then contacts DB
    val contacts by com.privimemobile.chat.ChatService.db?.contactDao()?.observeAll()
        ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val groupMemberNames = remember(groupMembers, contacts) {
        val names = mutableMapOf<String, String>()
        // From contacts DB
        contacts.forEach { c -> if (!c.displayName.isNullOrEmpty()) names[c.handle] = c.displayName!! }
        // From group members (overrides contacts if set)
        groupMembers.forEach { m -> if (!m.displayName.isNullOrEmpty()) names[m.handle] = m.displayName!! }
        names
    }

    val convKey = if (isGroupMode) "g_${groupId!!.take(16)}" else "@$handle"

    // Synchronous IO seed on first composition — LaunchedEffect left one frame without
    // contact/address/draft and caused DM input bar flash (disabled field + icon morph).
    val dmOpenSeed = remember(handle, isGroupMode) {
        if (isGroupMode) null
        else kotlinx.coroutines.runBlocking(Dispatchers.IO) {
            val db = com.privimemobile.chat.ChatService.db ?: return@runBlocking null
            val contactRow = db.contactDao().findByHandle(handle)
            val convRow = db.conversationDao().findByKey("@$handle")
            DmOpenSeed(
                contact = contactRow,
                conv = convRow,
                resolvedAddress = com.privimemobile.chat.DmAddressResolver.resolve(contactRow, convRow),
                draftText = convRow?.draftText?.takeIf { it.isNotEmpty() },
            )
        }
    }

    // Track first open per session
    val isFirstOpen = remember(convKey) { !ChatSessionStore.openedChatSessions.contains(convKey) }
    if (isFirstOpen) ChatSessionStore.openedChatSessions.add(convKey)

    // Observe conversation reactively
    val conversations by com.privimemobile.chat.ChatService.db?.conversationDao()?.observeAll()
        ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val conv = conversations.firstOrNull { it.convKey == convKey } ?: dmOpenSeed?.conv
    val convId = if (isGroupMode) (groupConvId ?: conv?.id ?: 0L) else (conv?.id ?: 0L)

    // Messages from Room DB
    val roomMessages by remember(convId) {
        if (convId > 0L) {
            com.privimemobile.chat.ChatService.db!!.messageDao().observeAll(convId)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.collectAsState(initial = emptyList())

    val pollUi = rememberChatPollUiState(roomMessages)

    // File download tracking (declared early so attachment loading can pre-populate)
    val files = remember(convKey) { ChatMessageListState() }

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
                if (cid.isNotEmpty() && !files.filePaths.containsKey(cid)) {
                    val path = com.privimemobile.chat.transport.IpfsTransport.getLocalFilePath(cid)
                    if (path != null) files.filePaths[cid] = path
                }
            }
        }
        attachmentMap = map
    }

    val nowSecs = System.currentTimeMillis() / 1000
    val messages = remember(roomMessages, attachmentMap, pollUi.revision) { roomMessages.filter { entity ->
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
            replySender = entity.replySender,
            replyTs = entity.replyTs,
            fwdFrom = entity.fwdFrom,
            edited = entity.edited,
            expiresAt = entity.expiresAt,
            pinned = entity.pinned,
            pinnedAt = entity.pinnedAt,
            pollData = pollUi.resolve(entity.id, entity.pollData),
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
    val contact = roomContact.firstOrNull { it.handle == handle } ?: dmOpenSeed?.contact

    val resolvedName = if (isGroupMode) {
        group?.name ?: stringResource(R.string.chat_group_name_fallback)
    } else {
        displayName.ifEmpty {
            contact?.displayName?.ifEmpty { null }
                ?: conv?.displayName?.ifEmpty { null }
                ?: "@$handle"
        }
    }
    val resolvedWalletId = contact?.walletId
    val resolvedSbbsAddress = remember(contact, conv, dmOpenSeed, isGroupMode) {
        if (isGroupMode) null
        else com.privimemobile.chat.DmAddressResolver.resolve(contact, conv) ?: dmOpenSeed?.resolvedAddress
    }
    val isDeletedAccount = contact?.isDeleted == true

    val prefs = context.getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)
    val chatPrefs = context.getSharedPreferences("chat_prefs", android.content.Context.MODE_PRIVATE)

    // State holders (plain Kotlin — orchestrator wires side effects)
    val input = remember(handle, isGroupMode) {
        ChatInputState(if (isGroupMode) "" else dmOpenSeed?.draftText ?: "")
    }
    val voice = remember(convKey) { ChatVoiceState() }
    val emoji = remember(convKey) { ChatEmojiStickerState() }
    val menu = remember(convKey) { ChatContextMenuState() }
    val media = remember(convKey) { ChatImagePreviewState() }
    val scrollBadge = remember(convKey) { ChatScrollBadgeState.forConv(convKey) }
    val chrome = remember(convKey) {
        ChatChromeState(prefs.getString("wallpaper_$convKey", "default") ?: "default").also { state ->
            state.groupNotifSoundName = chatPrefs.getString(
                "notif_sound_name_$convKey",
                context.getString(R.string.contact_notif_sound_default),
            ) ?: context.getString(R.string.contact_notif_sound_default)
        }
    }
    val selection = remember(convKey) { ChatSelectionState() }
    val forward = remember(convKey) { ChatForwardState() }
    val search = remember(convKey) { ChatSearchState() }
    val pinState = remember(convKey) { ChatPinState() }

    LaunchedEffect(convKey) {
        voice.hasRecordPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    var uploading by remember { mutableStateOf(false) }
    val voicePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        voice.hasRecordPermission = granted
        if (granted) voice.startRecordingAfterPermission = true
    }

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

    // All contacts for forward picker
    val allContacts by com.privimemobile.chat.ChatService.db?.contactDao()?.observeDmContacts()
        ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }

    val wallpaperImagePicker = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val file = java.io.File(context.filesDir, "wallpaper_${convKey.replace("@", "")}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                chrome.setWallpaper("custom:${file.absolutePath}#${System.currentTimeMillis()}")
                prefs.edit().putString("wallpaper_$convKey", chrome.chatWallpaper).apply()
                chrome.showWallpaperPicker = false
            } catch (_: Exception) {}
        }
    }
    var showCommandMenu by remember { mutableStateOf(false) }
    // @mention autocomplete state
    var showMentionMenu by remember { mutableStateOf(false) }
    var mentionFilter by remember { mutableStateOf("") }
    var mentionStartIdx by remember { mutableStateOf(-1) } // cursor position of '@'
    val filteredMembers = remember(groupMembers, mentionFilter, myHandle) {
        if (!isGroupMode) emptyList()
        else groupMembers.filter { m ->
            m.handle != myHandle && (mentionFilter.isEmpty() ||
                m.handle.contains(mentionFilter, ignoreCase = true) ||
                m.displayName?.contains(mentionFilter, ignoreCase = true) == true)
        }.take(5)
    }
    val groupSoundPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.getParcelableExtra<android.net.Uri>(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        val ver = chatPrefs.getInt("notif_channel_ver_$convKey", 0) + 1
        if (uri != null) {
            val ringtone = android.media.RingtoneManager.getRingtone(context, uri)
            val name = ringtone?.getTitle(context) ?: context.getString(R.string.contact_notif_sound_custom)
            chatPrefs.edit().putString("notif_sound_$convKey", uri.toString())
                .putString("notif_sound_name_$convKey", name)
                .putInt("notif_channel_ver_$convKey", ver).apply()
            chrome.groupNotifSoundName = name
        } else {
            chatPrefs.edit().putString("notif_sound_$convKey", "silent")
                .putString("notif_sound_name_$convKey", context.getString(R.string.contact_notif_sound_silent))
                .putInt("notif_channel_ver_$convKey", ver).apply()
            chrome.groupNotifSoundName = context.getString(R.string.contact_notif_sound_silent)
        }
    }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    // Restore saved scroll position on re-entry; first open starts at bottom (index 0)
    val savedScroll = if (isFirstOpen) null else ChatSessionStore.chatScrollPositions[convKey]
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedScroll?.first ?: 0,
        initialFirstVisibleItemScrollOffset = savedScroll?.second ?: 0,
    )

    // Save scroll position when leaving the chat so re-entry preserves it
    DisposableEffect(convKey) {
        onDispose {
            ChatSessionStore.chatScrollPositions[convKey] = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
    }

    // Capture unread count before setActiveChat clears it (for "X new messages" divider)
    // Read directly from DB to avoid race with reactive Flow clearing
    // Use nullable to distinguish "not loaded yet" from "loaded, count = 0"
    // Restore from persisted state on re-entry (DB acks are cleared by first visit's setActiveChat)
    var initialUnreadCount by remember {
        mutableStateOf<Int?>(ChatSessionStore.chatInitialUnread[convKey])
    }
    // Keep latest convId available for DisposableEffect (plain val isn't captured correctly)
    val currentConvId by rememberUpdatedState(convId)

    // Capture unread count first, THEN mark chat as active.
    // Sequencing is critical: setActiveChat sends acks which would make countUnread return 0.
    LaunchedEffect(convId, handle, groupId) {
        if (convId > 0L) {
            // Capture unread count BEFORE setActiveChat sends acks
            if (ChatSessionStore.chatInitialUnread[convKey] == null) {
                initialUnreadCount = null
                // Group chats track unread on GroupEntity, DMs track on ConversationEntity
                initialUnreadCount = if (isGroupMode && groupId != null) {
                    com.privimemobile.chat.ChatService.db?.groupDao()?.findByGroupId(groupId!!)?.unreadCount ?: 0
                } else {
                    conv?.unreadCount ?:
                        com.privimemobile.chat.ChatService.db?.conversationDao()?.findById(convId)?.unreadCount ?: 0
                }
            }
            // Load draft when not already seeded (DM open uses dmOpenSeed; groups may get convId late)
            val draft = com.privimemobile.chat.ChatService.db?.conversationDao()?.findById(convId)?.draftText
            if (!draft.isNullOrEmpty() && input.inputText.text.isEmpty()) {
                input.setInputText(draft)
            }
            // Set active chat (sends acks via internal scope.launch — async)
            com.privimemobile.chat.ChatService.setActiveChat(convKey)
            if (!isGroupMode) {
                com.privimemobile.chat.ChatService.db?.let {
                    com.privimemobile.chat.DmAddressResolver.syncContactSbbs(it, handle)
                }
                com.privimemobile.chat.ChatService.contacts.reResolveOnChatOpen(handle)
            }
        }
    }
    DisposableEffect(handle, groupId) {
        onDispose {
            com.privimemobile.chat.ChatService.setActiveChat(null)
            // Save draft on dispose
            val draftText = input.inputText.text.trim().ifEmpty { null }
            if (currentConvId > 0L) {
                kotlinx.coroutines.GlobalScope.launch {
                    com.privimemobile.chat.ChatService.db?.conversationDao()?.setDraft(currentConvId, draftText)
                }
            }
        }
    }

    LaunchedEffect(convId) {
        scrollBadge.hasScrolledInitially = false
    }

    LaunchedEffect(messages.size) {
        if (messages.size != scrollBadge.lastMsgCount) {
            scrollBadge.lastMsgCount = messages.size
            scrollBadge.newMsgVersion++
        }
    }

    LaunchedEffect(convId) {
        if (convId > 0L && ChatSessionStore.chatBadgeFloors[convKey] == null) {
            scrollBadge.resetBadgeFloorForNewConv()
        }
    }

    // Persist badge floor and initial unread count when leaving, restore on re-entry
    DisposableEffect(convKey) {
        onDispose {
            scrollBadge.persistBadgeFloor(convKey)
            val unread = initialUnreadCount
            if (unread != null && unread > 0) {
                ChatSessionStore.chatInitialUnread[convKey] = unread
            }
        }
    }

    // Initialize baseline when messages first load (covers first open and re-entry)
    LaunchedEffect(messages.isNotEmpty()) {
        if (messages.isNotEmpty() && scrollBadge.lastBottomTimestamp == 0L) {
            scrollBadge.lastBottomTimestamp = messages.maxOfOrNull { it.timestamp } ?: 0L
        }
    }

    // Clear unread divider when user is at the bottom and can see all messages.
    // Use scrollBadge.hasScrolledInitially to avoid clearing during the initial scroll animation
    // (which would cause a flash before the list settles at its target position).
    LaunchedEffect(listState.firstVisibleItemIndex, messages) {
        val current = listState.firstVisibleItemIndex
        if (ChatScrollMath.shouldClearUnreadWhenAtBottom(
                current,
                scrollBadge.hasScrolledInitially,
                messages.isNotEmpty(),
                initialUnreadCount,
            )
        ) {
            scrollBadge.lastBottomTimestamp = messages.maxOfOrNull { it.timestamp } ?: 0L
            ChatSessionStore.chatBadgeFloors.remove(convKey)
            ChatSessionStore.chatInitialUnread.remove(convKey)
            scrollBadge.badgeFloor = Int.MAX_VALUE
            if (initialUnreadCount != null && initialUnreadCount!! > 0) {
                initialUnreadCount = 0
            }
        }
    }

    // Find the index in the reversed list of the last (oldest) unread received message.
    // Scans from newest to oldest counting received messages until we hit initialUnreadCount.
    // This correctly handles interleaved sent messages that would throw off a simple index.
    val unreadBoundaryIndex = remember(messages, initialUnreadCount) {
        ChatScrollMath.computeUnreadBoundaryIndex(messages, initialUnreadCount)
    }

    // Scroll behavior:
    // - First open + unread > 0: scroll to divider
    // - First open + unread == 0: scroll to bottom
    // - Re-entry + was at bottom + unread > 0: scroll to divider
    // - Re-entry + was scrolled up + unread > 0: stay at saved position, badge shows
    // - Re-entry + no unread: stay at saved position
    // - New message while at bottom: auto-scroll to bottom
    // - New message while scrolled up: don't auto-scroll, badge counts it
    LaunchedEffect(messages.size, initialUnreadCount) {
        if (messages.isNotEmpty() && scrollToTimestamp == 0L && initialUnreadCount != null) {
            if (!scrollBadge.hasScrolledInitially) {
                val unread = initialUnreadCount!!
                val wasAtBottom = savedScroll == null || savedScroll.first <= 2
                if (unread == 0) {
                    scrollBadge.hasScrolledInitially = true
                    if (savedScroll == null) {
                        listState.animateScrollToItem(0) // first open, no unread → bottom
                    }
                    // re-entry, no unread → stay at saved position
                } else if (unreadBoundaryIndex >= 0 && wasAtBottom) {
                    scrollBadge.hasScrolledInitially = true
                    listState.scrollToItem(unreadBoundaryIndex)
                } else if (unreadBoundaryIndex >= 0) {
                    // unread > 0, wasAtBottom = false: stay at saved position
                    scrollBadge.hasScrolledInitially = true
                }
                // else: boundary not found, messages not loaded yet — wait
            } else {
                // Chat is open, new message arrived — scroll if user is at bottom
                if (listState.firstVisibleItemIndex <= 2) {
                    listState.animateScrollToItem(0)
                }
            }
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
            if (fileCid.isNotEmpty() && !files.filePaths.containsKey(fileCid)) {
                val path = com.privimemobile.chat.transport.IpfsTransport.getLocalFilePath(fileCid)
                if (path != null) {
                    files.filePaths[fileCid] = path
                } else if ((Helpers.isImageMime(msg.file?.mime ?: "") || msg.type == "sticker_pack" || msg.type == "sticker" || Helpers.isVoiceMime(msg.file?.mime))
                    && (msg.file?.size ?: 0) <= Config.AUTO_DL_MAX_SIZE
                    && files.downloadStatuses[fileCid] == null
                ) {
                    // Auto-download images, GIFs, stickers, and voice messages
                    files.downloadStatuses[fileCid] = "downloading"
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
                                files.filePaths[fileCid] = dlPath
                                files.downloadStatuses[fileCid] = "done"
                            } else {
                                files.downloadStatuses[fileCid] = "error"
                            }
                        } catch (_: Exception) {
                            files.downloadStatuses[fileCid] = "error"
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
                    context.getString(R.string.chat_image_too_large, Config.MAX_FILE_SIZE / 1024 / 1024)
                } else {
                    context.getString(R.string.chat_file_too_large, Config.MAX_INLINE_SIZE / 1024)
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                return
            }
            input.pendingFile = PendingFile(uri = uri, name = info.name, size = info.size, mimeType = info.mimeType)
            media.showAttachPicker = false
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
                com.privimemobile.chat.ChatService.groups.getOrCreateGroupConversation(groupId, group?.name ?: context.getString(R.string.chat_group_name_fallback))
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
                Toast.makeText(context, context.getString(R.string.toast_message_scheduled, timeStr), Toast.LENGTH_LONG).show()
            }
        }
        input.setInputText("")
    }

    // Send voice message (from preview)
    suspend fun sendVoiceMessage() {
        val file = voice.voicePreviewFile ?: return
        val waveform = voice.voicePreviewWaveform
        val durationMs = voice.voicePreviewDuration

        if (!isGroupMode && resolvedSbbsAddress.isNullOrEmpty()) {
            Toast.makeText(context, R.string.toast_cannot_send_no_address, Toast.LENGTH_SHORT).show()
            return
        }

        val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
        if (state?.myHandle == null) return

        val ts = System.currentTimeMillis() / 1000

        // Read audio file and encrypt for inline delivery
        val audioBytes = withContext(Dispatchers.IO) { file.readBytes() }

        val (key, iv) = com.privimemobile.protocol.FileCrypto.generateFileKey()
        val ciphertext = com.privimemobile.protocol.FileCrypto.encrypt(audioBytes, key, iv)
        val inlineData = android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP)

        // Check actual size limit (SBBS max inline size ~750KB after base64)
        if (inlineData.length > com.privimemobile.protocol.Config.MAX_INLINE_SIZE) {
            val sizeKB = inlineData.length / 1024
            val limitKB = com.privimemobile.protocol.Config.MAX_INLINE_SIZE / 1024
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.toast_voice_too_large, sizeKB, limitKB), Toast.LENGTH_LONG).show()
            }
            file.delete()
            voice.voicePreviewFile = null
            voice.voicePreviewWaveform = null
            voice.voicePreviewDuration = 0L
            return
        }

        // Encode waveform as base64
        val waveformB64 = if (waveform != null) android.util.Base64.encodeToString(waveform, android.util.Base64.NO_WRAP) else null
        val extras = org.json.JSONObject().apply {
            if (waveformB64 != null) put("waveform", waveformB64)
            put("duration_ms", durationMs)
        }.toString()

        // Build file metadata (OGG/Opus format from native encoder)
        val fileName = "voice_${ts}.ogg"
        val fileMeta = mapOf<String, Any?>(
            "cid" to "inline-${java.util.UUID.randomUUID()}",
            "key" to key,
            "iv" to iv,
            "name" to fileName,
            "size" to audioBytes.size,
            "mime" to "audio/ogg",
            "data" to inlineData,
        )

        val payload = mutableMapOf<String, Any?>(
            "v" to 1, "t" to "file", "ts" to ts,
            "from" to state.myHandle, "to" to (if (isGroupMode) groupId!! else handle),
            "dn" to (state.myDisplayName ?: ""),
            "file" to fileMeta,
            "extras" to extras,
        )

        // Optimistic DB insert
        val voiceConvId = if (isGroupMode) convId else {
            val conv = com.privimemobile.chat.ChatService.db!!.conversationDao().getOrCreate(convKey, handle)
            if (conv.deletedAtTs > 0) com.privimemobile.chat.ChatService.db!!.conversationDao().undelete(conv.id)
            conv.id
        }
        val dedupKey = "$ts:file:${fileMeta["cid"]}:true"
        val entity = com.privimemobile.chat.db.entities.MessageEntity(
            conversationId = voiceConvId, text = null,
            timestamp = ts, sent = true, type = "file",
            senderHandle = state.myHandle, sbbsDedupKey = dedupKey,
        )
        val msgId = com.privimemobile.chat.ChatService.db!!.messageDao().insert(entity)

        // Insert attachment with extras
        if (msgId > 0) {
            com.privimemobile.chat.ChatService.db!!.attachmentDao().insert(
                com.privimemobile.chat.db.entities.AttachmentEntity(
                    messageId = msgId, conversationId = voiceConvId,
                    ipfsCid = fileMeta["cid"] as? String ?: "",
                    encryptionKey = key, encryptionIv = iv,
                    fileName = fileName, fileSize = audioBytes.size.toLong(),
                    mimeType = "audio/ogg",
                    inlineData = inlineData,
                    downloadStatus = "done",
                    extras = extras,
                )
            )
        }

        val preview = "\uD83C\uDFA4 Voice ${formatVoiceDuration(durationMs)}"

        // Delete temp recording file and clear preview state
        file.delete()
        voice.voicePreviewFile = null
        voice.voicePreviewWaveform = null
        voice.voicePreviewDuration = 0L

        // Send via SBBS
        // Update preview BEFORE network send — survives early navigation
        if (isGroupMode && groupId != null) {
            val youLabel = context.getString(R.string.chat_sender_you)
            com.privimemobile.chat.ChatService.db?.groupDao()?.updateLastMessage(groupId, ts, "$youLabel: $preview")
            com.privimemobile.chat.ChatService.groups.sendGroupPayload(groupId, payload)
        } else {
            com.privimemobile.chat.ChatService.db!!.conversationDao().updateLastMessage(voiceConvId, ts, preview)
            com.privimemobile.chat.ChatService.sbbs.sendWithRetry(resolvedSbbsAddress!!, payload)
        }

        // Clean up temp file
        withContext(Dispatchers.IO) { file.delete() }
    }

    // Send voice message (direct from RecordingResult - Telegram-style release to send)
    suspend fun sendVoiceMessage(result: com.privimemobile.chat.voice.VoiceRecorder.RecordingResult) {
        if (!isGroupMode && resolvedSbbsAddress.isNullOrEmpty()) {
            Toast.makeText(context, R.string.toast_cannot_send_no_address, Toast.LENGTH_SHORT).show()
            result.file.delete()
            return
        }

        val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
        if (state?.myHandle == null) {
            result.file.delete()
            return
        }

        val ts = System.currentTimeMillis() / 1000

        // Read audio file and encrypt for inline delivery
        val audioBytes = withContext(Dispatchers.IO) { result.file.readBytes() }

        val (key, iv) = com.privimemobile.protocol.FileCrypto.generateFileKey()
        val ciphertext = com.privimemobile.protocol.FileCrypto.encrypt(audioBytes, key, iv)
        val inlineData = android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP)

        // Check actual size limit (SBBS max inline size ~750KB after base64)
        if (inlineData.length > com.privimemobile.protocol.Config.MAX_INLINE_SIZE) {
            val sizeKB = inlineData.length / 1024
            val limitKB = com.privimemobile.protocol.Config.MAX_INLINE_SIZE / 1024
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.toast_voice_too_large, sizeKB, limitKB), Toast.LENGTH_LONG).show()
            }
            result.file.delete()
            return
        }

        // Encode waveform as base64
        val waveformB64 = android.util.Base64.encodeToString(result.waveform, android.util.Base64.NO_WRAP)
        val extras = org.json.JSONObject().apply {
            put("waveform", waveformB64)
            put("duration_ms", result.durationMs)
        }.toString()

        // Build file metadata (OGG/Opus format from native encoder)
        val fileName = "voice_${ts}.ogg"
        val fileMeta = mapOf<String, Any?>(
            "cid" to "inline-${java.util.UUID.randomUUID()}",
            "key" to key,
            "iv" to iv,
            "name" to fileName,
            "size" to audioBytes.size,
            "mime" to "audio/ogg",
            "data" to inlineData,
        )

        val payload = mutableMapOf<String, Any?>(
            "v" to 1, "t" to "file", "ts" to ts,
            "from" to state.myHandle, "to" to (if (isGroupMode) groupId!! else handle),
            "dn" to (state.myDisplayName ?: ""),
            "file" to fileMeta,
            "extras" to extras,
        )

        // Optimistic DB insert
        val voiceConvId = if (isGroupMode) convId else {
            val conv = com.privimemobile.chat.ChatService.db!!.conversationDao().getOrCreate(convKey, handle)
            if (conv.deletedAtTs > 0) com.privimemobile.chat.ChatService.db!!.conversationDao().undelete(conv.id)
            conv.id
        }
        val dedupKey = "$ts:file:${fileMeta["cid"]}:true"
        val entity = com.privimemobile.chat.db.entities.MessageEntity(
            conversationId = voiceConvId, text = null,
            timestamp = ts, sent = true, type = "file",
            senderHandle = state.myHandle, sbbsDedupKey = dedupKey,
        )
        val msgId = com.privimemobile.chat.ChatService.db!!.messageDao().insert(entity)

        // Insert attachment with extras
        if (msgId > 0) {
            com.privimemobile.chat.ChatService.db!!.attachmentDao().insert(
                com.privimemobile.chat.db.entities.AttachmentEntity(
                    messageId = msgId, conversationId = voiceConvId,
                    ipfsCid = fileMeta["cid"] as? String ?: "",
                    encryptionKey = key, encryptionIv = iv,
                    fileName = fileName, fileSize = audioBytes.size.toLong(),
                    mimeType = "audio/ogg",
                    inlineData = inlineData,
                    downloadStatus = "done",
                    extras = extras,
                )
            )
        }

        val preview = "\uD83C\uDFA4 Voice ${formatVoiceDuration(result.durationMs)}"

        // Delete temp recording file
        result.file.delete()

        // Update preview BEFORE network send — survives early navigation
        if (isGroupMode && groupId != null) {
            val youLabel = context.getString(R.string.chat_sender_you)
            com.privimemobile.chat.ChatService.db?.groupDao()?.updateLastMessage(groupId, ts, "$youLabel: $preview")
            com.privimemobile.chat.ChatService.groups.sendGroupPayload(groupId, payload)
        } else {
            com.privimemobile.chat.ChatService.db!!.conversationDao().updateLastMessage(voiceConvId, ts, preview)
            com.privimemobile.chat.ChatService.sbbs.sendWithRetry(resolvedSbbsAddress!!, payload)
        }
    }

    // After permission granted, auto-start recording in locked mode (hands-free)
    // Must be AFTER sendVoiceMessage() definition so the capture sees it
    LaunchedEffect(voice.startRecordingAfterPermission) {
        if (!voice.startRecordingAfterPermission) return@LaunchedEffect
        val recorder = com.privimemobile.chat.voice.VoiceRecorder(
            context,
            amplitudeCallback = { },
            onMaxDurationReached = {
                val result = voice.voiceRecorder?.stop()
                if (result != null && result.durationMs >= 700L) {
                    scope.launch { sendVoiceMessage(result) }
                } else if (result != null) {
                    result.file.delete()
                }
                voice.voiceRecording = false
                voice.voiceLocked = false
                voice.voicePaused = false
                voice.voiceRecorder = null
                voice.micIsRecordingVisual = false
                voice.startRecordingAfterPermission = false
            }
        )
        if (recorder.start() != null) {
            voice.voiceRecorder = recorder
            voice.voiceRecording = true
            voice.voiceLocked = true        // show locked UI: CANCEL + pause circle + send button
            voice.micIsRecordingVisual = false
        }
        voice.startRecordingAfterPermission = false
    }

    // Send message
    fun handleSend() {
        val now = System.currentTimeMillis()
        if (now - lastSendTime < 3000) {
            android.widget.Toast.makeText(context, R.string.toast_slow_down, android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        lastSendTime = now

        // Clear draft on send and dismiss unread divider
        initialUnreadCount = 0 // explicitly set to 0 (not null) to hide divider
        if (convId > 0L) {
            scope.launch { com.privimemobile.chat.ChatService.db?.conversationDao()?.setDraft(convId, null) }
        }

        val trimmed = input.inputText.text.trim()

        // Edit mode — update existing message instead of creating new one
        if (input.editingMsg != null) {
            val editTarget = input.editingMsg!!
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
                        // Update group preview if this is a group conversation
                        if (isGroupMode && groupId != null) {
                            val group = com.privimemobile.chat.ChatService.db?.groupDao()?.findByGroupId(groupId)
                            if (group != null && group.lastMessageTs == editTarget.timestamp) {
                                val senderLabel = context.getString(com.privimemobile.R.string.chat_sender_you)
                                com.privimemobile.chat.ChatService.db?.groupDao()?.updateLastMessage(groupId, editTarget.timestamp, "${senderLabel}: ${trimmed.take(40)}")
                            }
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
                            val sendAddr = resolvedSbbsAddress
                            if (!sendAddr.isNullOrEmpty()) {
                                com.privimemobile.chat.ChatService.sbbs.sendWithRetry(sendAddr, editPayload)
                            }
                        }
                    }
                }
            }
            input.editingMsg = null
            input.setInputText("")
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
                } else if (input.replyingTo != null && !input.replyingTo!!.from.isNullOrEmpty() && !input.replyingTo!!.sent) {
                    // Quoted reply — tip the sender of the quoted message
                    tipTargetHandle = input.replyingTo!!.from
                    tipTokens = tokens // all tokens are amount/asset/msg
                } else {
                    Toast.makeText(context, R.string.toast_reply_or_tip_hint, Toast.LENGTH_LONG).show()
                    input.setInputText(""); return
                }
            } else {
                tipTargetHandle = handle
                tipTokens = tokens
            }

            val amountBeam = tipTokens.getOrNull(0)?.replace(',', '.')?.toDoubleOrNull()
            if (amountBeam == null || amountBeam <= 0) {
                val usage = if (isGroupMode) context.getString(R.string.toast_tip_usage_group)
                    else context.getString(R.string.toast_tip_usage_dm)
                Toast.makeText(context, usage, Toast.LENGTH_SHORT).show()
                return
            }

            // Resolve wallet ID (on-chain for money) and sbbs_address (SBBS channel for notification)
            scope.launch {
                val tipWalletId = if (isGroupMode) {
                    // Look up target handle's wallet ID from group members or contacts
                    val member = com.privimemobile.chat.ChatService.db?.groupDao()?.findMember(groupId!!, tipTargetHandle)
                    member?.walletId ?: com.privimemobile.chat.ChatService.db?.contactDao()?.findByHandle(tipTargetHandle)?.walletId
                } else {
                    resolvedWalletId
                }
                val tipSbbsAddress = if (isGroupMode) {
                    val member = com.privimemobile.chat.ChatService.db?.groupDao()?.findMember(groupId!!, tipTargetHandle)
                    member?.sbbsAddress ?: member?.walletId
                        ?: com.privimemobile.chat.ChatService.db?.contactDao()?.findByHandle(tipTargetHandle)?.sbbsAddress
                        ?: com.privimemobile.chat.ChatService.db?.contactDao()?.findByHandle(tipTargetHandle)?.walletId
                } else {
                    resolvedSbbsAddress
                }

                if (tipWalletId.isNullOrEmpty() || tipSbbsAddress.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.toast_cannot_resolve, tipTargetHandle), Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Parse asset ID and caption
                val secondToken = tipTokens.getOrNull(1)
                val secondIsAssetId = secondToken?.toIntOrNull() != null
                val assetId = if (secondIsAssetId) secondToken!!.toInt() else 0
                val caption = if (secondIsAssetId) {
                    tipTokens.drop(2).joinToString(" ").trim()
                } else {
                    tipTokens.drop(1).joinToString(" ").trim()
                }
                val amountGroth = (amountBeam * 100_000_000).toLong()
                val assetName = com.privimemobile.wallet.assetTicker(assetId)
                val tipLabel = context.getString(R.string.chat_tip_to, "@$tipTargetHandle", "${Helpers.formatBeam(amountGroth)} $assetName")

                // Balance check
                val bal = com.privimemobile.wallet.WalletEventBus.assetBalances[assetId]
                val spendable = (bal?.available ?: 0L) + (bal?.shielded ?: 0L)
                if (spendable < amountGroth) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.toast_insufficient_balance, assetName, Helpers.formatBeam(spendable), Helpers.formatBeam(amountGroth)), Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                if (assetId != 0) {
                    val beamBal = com.privimemobile.wallet.WalletEventBus.assetBalances[0]
                    val beamSpendable = (beamBal?.available ?: 0L) + (beamBal?.shielded ?: 0L)
                    if (beamSpendable <= 0) {
                        withContext(Dispatchers.Main) { Toast.makeText(context, R.string.toast_insufficient_beam_fee, Toast.LENGTH_LONG).show() }
                        return@launch
                    }
                }

                try {
                    val txComment = context.getString(R.string.chat_tip_tx_comment, tipTargetHandle) + if (caption.isNotEmpty()) " — $caption" else ""
                    val txResult = com.privimemobile.protocol.WalletApi.callAsync("tx_send", mapOf(
                        "value" to amountGroth,
                        "address" to tipWalletId,
                        "asset_id" to assetId,
                        "comment" to txComment,
                    ))
                    if (txResult.containsKey("error")) {
                        val errMsg = Helpers.extractError(txResult, context)
                        val tipCancelled = errMsg == context.getString(R.string.tx_cancelled)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context,
                                if (tipCancelled) context.getString(R.string.tip_cancelled) else context.getString(R.string.tip_failed, errMsg),
                                if (tipCancelled) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                            ).show()
                        }
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
                            // Update preview BEFORE network send — survives early navigation
                            val youPrefix = context.getString(R.string.chat_sender_you)
                            com.privimemobile.chat.ChatService.db?.groupDao()?.updateLastMessage(groupId, ts, "$youPrefix: $tipLabel")
                            com.privimemobile.chat.ChatService.groups.sendGroupPayload(groupId, payload)
                        } else {
                            val tipConv = com.privimemobile.chat.ChatService.db!!.conversationDao().getOrCreate(convKey, tipTargetHandle)
                            if (tipConv.deletedAtTs > 0) com.privimemobile.chat.ChatService.db!!.conversationDao().undelete(tipConv.id)
                            com.privimemobile.chat.ChatService.db!!.conversationDao().updateLastMessage(tipConv.id, ts, tipLabel)
                            com.privimemobile.chat.ChatService.sbbs.sendWithRetry(tipSbbsAddress, payload)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.chat_tip_sent, tipLabel), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        val errorMap = mapOf("error" to mapOf("message" to (e.message ?: context.getString(R.string.chat_send_failed))))
                        val errMsg = Helpers.extractError(errorMap, context)
                        val tipCancelled = errMsg == context.getString(R.string.tx_cancelled)
                        Toast.makeText(context,
                            if (tipCancelled) context.getString(R.string.tip_cancelled) else context.getString(R.string.tip_failed, errMsg),
                            if (tipCancelled) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            input.setInputText("")
            input.replyingTo = null
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
                            // Update preview BEFORE network send \u2014 survives early navigation
                            val youLabel = context.getString(R.string.chat_sender_you)
                            com.privimemobile.chat.ChatService.db?.groupDao()?.updateLastMessage(groupId, ts, "$youLabel: \uD83D\uDCCA $question")
                            com.privimemobile.chat.ChatService.groups.sendGroupPayload(groupId, payload)
                        } else {
                            val convDb = com.privimemobile.chat.ChatService.db!!.conversationDao().getOrCreate(convKey, handle)
                            if (convDb.deletedAtTs > 0) com.privimemobile.chat.ChatService.db!!.conversationDao().undelete(convDb.id)
                            com.privimemobile.chat.ChatService.db!!.conversationDao().updateLastMessage(convDb.id, ts, "\uD83D\uDCCA $question")
                            val walletId = resolvedSbbsAddress
                            if (!walletId.isNullOrEmpty()) {
                                com.privimemobile.chat.ChatService.sbbs.sendWithRetry(walletId, payload)
                            }
                        }
                    }
                }
            } else {
                Toast.makeText(context, R.string.toast_poll_usage, Toast.LENGTH_LONG).show()
            }
            input.setInputText("")
            return
        }

        // Send file if pending
        if (input.pendingFile != null) {
            val file = input.pendingFile!!
            Log.d("ChatScreen", "Sending file: ${file.name}, ${file.size} bytes, isGroup=$isGroupMode")
            if (!isGroupMode && resolvedSbbsAddress.isNullOrEmpty()) {
                Log.w("ChatScreen", "Cannot send file — no resolved wallet ID")
                return
            }
            // Capture reply text BEFORE coroutine
            val fileReplyText = input.replyingTo?.text?.take(200)?.ifEmpty { null }
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
                            val stickerMeta = input.pendingStickerMeta
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
                                    files.filePaths[cid] = dlPath
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

                            // Clear UI state BEFORE SBBS send (message already in DB and visible)
                            uploading = false
                            input.pendingFile = null
                            input.pendingStickerMeta = null
                            input.setInputText("")
                            input.replyingTo = null

                            if (isGroupMode && groupId != null) {
                                // Update preview BEFORE network send — survives early navigation
                                val youLabel = context.getString(R.string.chat_sender_you)
                                com.privimemobile.chat.ChatService.db?.groupDao()?.updateLastMessage(groupId, ts, "$youLabel: $preview")
                                com.privimemobile.chat.ChatService.groups.sendGroupPayload(groupId, payload)
                            } else {
                                com.privimemobile.chat.ChatService.db!!.conversationDao().updateLastMessage(fileConvId, ts, preview)
                                com.privimemobile.chat.ChatService.sbbs.sendWithRetry(resolvedSbbsAddress!!, payload)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ChatScreen", "File send error: ${e.message}")
                    android.widget.Toast.makeText(context, context.getString(R.string.chat_file_send_failed) + ": " + (e.message ?: ""), android.widget.Toast.LENGTH_LONG).show()
                } finally {
                    uploading = false
                    input.pendingFile = null
                    input.pendingStickerMeta = null
                    input.setInputText("")
                    input.replyingTo = null
                }
            }
            return
        }

        // Regular text message
        if (trimmed.isEmpty()) return

        // Group mode — send via GroupManager
        if (isGroupMode && groupId != null) {
            val grpReplyText = input.replyingTo?.text?.take(200)?.ifEmpty { null }
            val grpReplySender = input.replyingTo?.from
            val grpReplyTs = input.replyingTo?.timestamp ?: 0L
            val grpTimer = input.oneShotTimer
            scope.launch {
                com.privimemobile.chat.ChatService.groups.sendGroupMessage(
                    groupId, trimmed, replyText = grpReplyText,
                    replySender = grpReplySender, replyMsgTs = grpReplyTs,
                    ttl = grpTimer,
                )
            }
            input.setInputText("")
            input.replyingTo = null
            input.oneShotTimer = 0
            // Clear draft
            if (convId > 0L) {
                scope.launch { com.privimemobile.chat.ChatService.db?.conversationDao()?.setDraft(convId, null) }
            }
            return
        }

        val sendAddress = resolvedSbbsAddress
        if (sendAddress.isNullOrEmpty()) return

        // Capture state BEFORE coroutine (state may be cleared by main thread before coroutine runs)
        val replyText = input.replyingTo?.text?.take(200)?.ifEmpty { null }
        val replySenderHandle = input.replyingTo?.from
        val replyMsgTs = input.replyingTo?.timestamp ?: 0L
        val capturedTimer = input.oneShotTimer

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
                if (replyText != null) {
                    payload["reply"] = replyText
                    if (replySenderHandle != null) payload["reply_from"] = replySenderHandle
                    if (replyMsgTs > 0) payload["reply_ts"] = replyMsgTs
                }
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
                    replySender = replySenderHandle,
                    replyTs = replyMsgTs,
                    expiresAt = expiresAt,
                )
                com.privimemobile.chat.ChatService.db!!.messageDao().insert(entity)
                com.privimemobile.chat.ChatService.db!!.conversationDao().updateLastMessage(convDb.id, ts, trimmed.take(100))

                // Send via SBBS
                com.privimemobile.chat.ChatService.sbbs.sendWithRetry(sendAddress, payload)
            }
        }

        input.setInputText("")
        input.replyingTo = null
        input.oneShotTimer = 0  // clear per-message timer after send
    }

    // Update conversation preview after message deletion
    suspend fun refreshConversationPreview(cid: Long) {
        val latest = com.privimemobile.chat.ChatService.db?.messageDao()?.getLatestMessage(cid)
        if (latest != null) {
            val preview = when (latest.type) {
                "tip" -> context.getString(R.string.chat_tip_preview)
                "file" -> "\uD83D\uDCCE " + context.getString(R.string.chat_file_label)
                else -> latest.text?.take(100)
            }
            com.privimemobile.chat.ChatService.db?.conversationDao()?.updateLastMessage(cid, latest.timestamp, preview)
        } else {
            com.privimemobile.chat.ChatService.db?.conversationDao()?.updateLastMessage(cid, 0, null)
        }
        // Also update group preview if this is a group conversation
        if (isGroupMode && groupId != null) {
            val group = com.privimemobile.chat.ChatService.db?.groupDao()?.findByGroupId(groupId!!)
            if (group != null) {
                if (latest != null) {
                    val senderLabel = if (latest.sent) context.getString(R.string.chat_sender_you) else "@${latest.senderHandle}"
                    val grpPreview = when (latest.type) {
                        "tip" -> "$senderLabel: ${context.getString(R.string.chat_preview_tip)}"
                        "file" -> "$senderLabel: \uD83D\uDCCE ${context.getString(R.string.chat_preview_file)}"
                        else -> "$senderLabel: ${latest.text?.take(40) ?: context.getString(R.string.chat_delete_preview)}"
                    }
                    com.privimemobile.chat.ChatService.db?.groupDao()?.updateLastMessage(groupId!!, latest.timestamp, grpPreview)
                } else {
                    com.privimemobile.chat.ChatService.db?.groupDao()?.updateLastMessage(groupId!!, 0, null)
                }
            }
        }
    }

    // Download a file via IpfsTransport
    fun handleDownload(cid: String, keyHex: String, ivHex: String, mime: String, inlineData: String? = null) {
        files.downloadStatuses[cid] = "downloading"
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
                    files.filePaths[cid] = path
                    files.downloadStatuses[cid] = "done"
                } else {
                    files.downloadStatuses[cid] = "error"
                }
            } catch (e: Exception) {
                files.downloadStatuses[cid] = "error"
            }
        }
    }

    val canSend = (input.inputText.text.isNotBlank() || input.pendingFile != null) && (isGroupMode || !resolvedSbbsAddress.isNullOrEmpty())

    // BackHandler: intercept system back when overlays are visible
    BackHandler(enabled = selection.selectionMode) { selection.exitSelection() }
    BackHandler(enabled = media.fullscreenImage != null) { media.fullscreenImage = null }
    BackHandler(enabled = media.showAttachPicker) { media.showAttachPicker = false }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg),
    ) {
        // Selection mode header bar
        if (selection.selectionMode) {
            Surface(color = C.card, shadowElevation = 2.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { selection.exitSelection() }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.chat_cancel_scheduled), tint = C.text, modifier = Modifier.size(22.dp))
                    }
                    Text(
                        context.getString(R.string.chat_x_selected, selection.selectedIds.size),
                        color = C.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    // Delete selected — show confirmation dialog
                    IconButton(onClick = { selection.openBulkDeleteConfirm() }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Delete, stringResource(R.string.chat_label_delete), tint = C.error, modifier = Modifier.size(22.dp))
                    }
                    // Forward selected — forward ALL selected messages (not just first)
                    IconButton(onClick = {
                        val msgsToForward = messages.filter { it.id in selection.selectedIds && (it.text.isNotEmpty() || it.file != null) }
                        if (msgsToForward.isNotEmpty()) {
                            forward.openMultiple(msgsToForward)
                        }
                        selection.exitSelection()
                    }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.chat_forward), tint = C.accent, modifier = Modifier.size(22.dp))
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.general_back), tint = C.text, modifier = Modifier.size(22.dp))
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
                            contentDescription = stringResource(R.string.chat_section_groups),
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
                            Icon(Icons.Default.Lock, stringResource(R.string.chat_send_message), tint = C.textSecondary, modifier = Modifier.size(14.dp))
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
                                val typingText = if (groupTypers.size == 1) context.getString(R.string.chat_group_typing_singular, "@${groupTypers[0]}")
                                    else if (groupTypers.size == 2) context.getString(R.string.chat_group_typing_two, "@${groupTypers[0]}", "@${groupTypers[1]}")
                                    else context.getString(R.string.chat_group_typing_multiple, groupTypers.size)
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
                                Text(stringResource(R.string.group_member_count_format, groupMemberCount), color = C.textSecondary, fontSize = 12.sp)
                            }
                            if (group?.muted == true) {
                                Icon(Icons.Default.NotificationsOff, stringResource(R.string.chat_overflow_mute), tint = C.textSecondary,
                                    modifier = Modifier.padding(start = 4.dp).size(13.dp))
                            }
                        }
                    } else {
                    val typingVer by com.privimemobile.chat.ChatService.typingVersion.collectAsState()
                    val peerTyping = typingVer >= 0 && com.privimemobile.chat.ChatService.isTyping(convKey)
                    if (peerTyping) {
                        // Bouncing dots animation (Telegram-style)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.chat_typing), color = C.accent, fontSize = 12.sp)
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
                                Icon(Icons.Default.NotificationsOff, stringResource(R.string.chat_overflow_mute), tint = C.textSecondary,
                                    modifier = Modifier.padding(start = 4.dp).size(13.dp))
                            }
                            if (conv?.isBlocked == true) {
                                Icon(Icons.Default.Block, stringResource(R.string.chat_overflow_block), tint = Color(0xFFEF5350),
                                    modifier = Modifier.padding(start = 4.dp).size(13.dp))
                            }
                        }
                    }
                    } // end !isGroupMode else
                }
                // 3-dot overflow menu (animated rotation + smooth dropdown)
                Box {
                    val menuRotation by animateFloatAsState(
                        targetValue = if (chrome.showOverflowMenu) 90f else 0f,
                        animationSpec = tween(200),
                        label = "menuRotation",
                    )
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            chrome.showOverflowMenu = !chrome.showOverflowMenu
                        },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.chat_overflow_search),
                            tint = if (chrome.showOverflowMenu) C.accent else C.textSecondary,
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer { rotationZ = menuRotation },
                        )
                    }
                    DropdownMenu(
                        expanded = chrome.showOverflowMenu,
                        onDismissRequest = { chrome.showOverflowMenu = false },
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
                        OverflowItem("\uD83D\uDD0D", if (search.showSearch) stringResource(R.string.chat_overflow_close_search) else stringResource(R.string.chat_overflow_search)) {
                            search.toggle()
                            chrome.showOverflowMenu = false
                        }
                        OverflowItem("\uD83D\uDDBC", stringResource(R.string.chat_overflow_media)) { chrome.showOverflowMenu = false; onMediaGallery() }
                        if (isGroupMode) {
                            OverflowItem("\u2699\uFE0F", stringResource(R.string.chat_overflow_group_info)) { chrome.showOverflowMenu = false; onGroupSettings() }
                            OverflowItem("\uD83D\uDD14", context.getString(R.string.chat_overflow_sound, chrome.groupNotifSoundName)) {
                                chrome.showOverflowMenu = false
                                val intent = android.content.Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_NOTIFICATION)
                                    putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(R.string.chat_notification_sound))
                                    putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                    putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    val current = chatPrefs.getString("notif_sound_$convKey", null)
                                    if (current != null && current != "silent") {
                                        putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, android.net.Uri.parse(current))
                                    }
                                }
                                groupSoundPicker.launch(intent)
                            }
                        } else {
                            OverflowItem("\uD83D\uDC64", stringResource(R.string.chat_overflow_view_profile)) { chrome.showOverflowMenu = false; onContactInfo() }
                            OverflowItem("\uD83D\uDD14", context.getString(R.string.chat_overflow_sound, chrome.groupNotifSoundName)) {
                                chrome.showOverflowMenu = false
                                val intent = android.content.Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_NOTIFICATION)
                                    putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(R.string.chat_notification_sound))
                                    putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                    putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    val current = chatPrefs.getString("notif_sound_$convKey", null)
                                    if (current != null && current != "silent") {
                                        putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, android.net.Uri.parse(current))
                                    }
                                }
                                groupSoundPicker.launch(intent)
                            }
                        }
                        OverflowItem("\uD83C\uDFA8", stringResource(R.string.chat_overflow_wallpaper)) { chrome.showOverflowMenu = false; chrome.showWallpaperPicker = true }
                        // Per-message self-destruct timer
                        OverflowItem("\u23F1", if (input.oneShotTimer == 0) stringResource(R.string.chat_overflow_timer_off) else context.getString(R.string.chat_overflow_timer_on, Helpers.formatDuration(context, input.oneShotTimer))) {
                            chrome.showOverflowMenu = false
                            input.showOneShotTimerPicker = true
                        }
                        OverflowItem("\uD83D\uDCE4", stringResource(R.string.chat_overflow_export_chat)) {
                            chrome.showOverflowMenu = false
                            val exportTitle = if (isGroupMode) context.getString(R.string.chat_export_group_label, group?.name ?: context.getString(R.string.chat_group_name_fallback)) else context.getString(R.string.chat_export_dm_label, "@$handle")
                            scope.launch {
                                val sb = StringBuilder()
                                sb.appendLine(exportTitle)
                                sb.appendLine(context.getString(R.string.chat_export_date, java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())))
                                sb.appendLine("---")
                                messages.forEach { msg ->
                                    val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                                        .format(java.util.Date(msg.timestamp * 1000))
                                    val sender = if (msg.sent) context.getString(R.string.chat_you_label) else "@${msg.from}"
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
                                    context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.chat_overflow_export_chat)))
                                }
                            }
                        }
                        val isMuted = if (isGroupMode) group?.muted == true else conv?.muted == true
                        OverflowItem(if (isMuted) "\uD83D\uDD14" else "\uD83D\uDD07", if (isMuted) stringResource(R.string.chat_overflow_unmute) else stringResource(R.string.chat_overflow_mute)) {
                            scope.launch {
                                if (isGroupMode && groupId != null) {
                                    com.privimemobile.chat.ChatService.db?.groupDao()?.setMuted(groupId, !isMuted)
                                } else if (convId > 0L) {
                                    com.privimemobile.chat.ChatService.db?.conversationDao()?.setMuted(convId, !isMuted)
                                }
                            }
                            chrome.showOverflowMenu = false
                        }
                        if (!isGroupMode) {
                            val isBlocked = conv?.isBlocked == true
                            OverflowItem(if (isBlocked) "\u2705" else "\uD83D\uDEAB", if (isBlocked) stringResource(R.string.chat_overflow_unblock) else stringResource(R.string.chat_overflow_block), color = if (isBlocked) C.text else C.error) {
                                if (convId > 0L) { scope.launch { com.privimemobile.chat.ChatService.db?.conversationDao()?.setBlocked(convId, !isBlocked) } }
                                chrome.showOverflowMenu = false
                            }
                        }
                        HorizontalDivider(color = C.border)
                        OverflowItem("\uD83D\uDDD1", stringResource(R.string.chat_clear_history), color = C.error) { chrome.showOverflowMenu = false; chrome.showClearConfirm = true }
                        OverflowItem("\u274C", if (isGroupMode) stringResource(R.string.chat_leave_group) else stringResource(R.string.chat_delete_chat), color = C.error) { chrome.showOverflowMenu = false; chrome.showDeleteConfirm = true }
                    }
                }
            }
        }
        } // close else (normal header vs selection header)

        // In-chat search bar (Telegram-style: back arrow + field + X to clear)
        androidx.compose.animation.AnimatedVisibility(
            visible = search.showSearch,
            enter = androidx.compose.animation.expandVertically(tween(200)) + androidx.compose.animation.fadeIn(tween(200)),
            exit = androidx.compose.animation.shrinkVertically(tween(200)) + androidx.compose.animation.fadeOut(tween(200)),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
            Surface(color = C.card) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Back arrow — closes search
                    IconButton(onClick = { search.close() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.chat_overflow_close_search), tint = C.textSecondary)
                    }
                    OutlinedTextField(
                        value = search.searchQuery,
                        onValueChange = { query: String ->
                            search.onQueryChanged(query)
                            if (query.isNotBlank()) {
                                search.searchJob = scope.launch {
                                    delay(300) // debounce
                                    val results = com.privimemobile.chat.ChatService.db?.messageDao()
                                        ?.searchInConversation(convId, "%${query.trim()}%") ?: emptyList()
                                    search.searchResults = results
                                }
                            }
                        },
                        placeholder = { Text(stringResource(R.string.chat_search_in_chat), color = C.textMuted, fontSize = 14.sp) },
                        singleLine = true,
                        trailingIcon = if (search.searchQuery.isNotEmpty()) { {
                            IconButton(onClick = { search.clearQueryAndResults() }) {
                                Text("✕", color = C.textSecondary, fontSize = 18.sp)
                            }
                        } } else null,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = C.accent, unfocusedBorderColor = C.border,
                            focusedContainerColor = C.bg, unfocusedContainerColor = C.bg,
                            cursorColor = C.accent, focusedTextColor = C.text, unfocusedTextColor = C.text,
                        ),
                    )
                    if (search.searchResults.isNotEmpty()) {
                        Text(
                            "${search.searchResults.size}",
                            color = C.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
            // Search results list
            if (search.searchResults.isNotEmpty()) {
                Surface(color = C.card.copy(alpha = 0.95f)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                    ) {
                        items(search.searchResults, key = { it.id }) { result ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // Find index in reversed messages and scroll to it
                                        val reversedMessages = messages.reversed()
                                        val idx = reversedMessages.indexOfFirst { it.timestamp == result.timestamp }
                                        if (idx >= 0) {
                                            search.searchHighlightTs = result.timestamp
                                            scope.launch {
                                                listState.animateScrollToItem(idx)
                                                // Auto-clear highlight after 2s
                                                delay(2000)
                                                search.searchHighlightTs = null
                                            }
                                        }
                                        search.closeAfterResultPick()
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
                                    Text(stringResource(R.string.chat_you_label), color = C.textSecondary, fontSize = 11.sp)
                                }
                            }
                            HorizontalDivider(color = C.border, thickness = 0.5.dp)
                        }
                    }
                }
            }
            } // Column: search bar above results (no overlap)
        }

        // Pinned messages bar — scroll-aware, ordered by pin time
        val pinnedByOrder = messages.filter { it.pinned }.sortedBy { it.pinnedAt }  // #1 = first pinned
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

            // Clear override when user scrolls AWAY from where the tap-scroll landed
            LaunchedEffect(listState.firstVisibleItemIndex) {
                if (pinState.manualOverrideIndex >= 0 && pinState.scrollPosAtOverride >= 0) {
                    // Wait for the programmatic scroll to finish first
                    delay(1000)
                    // Only clear if user has actually scrolled from the landing position
                    if (listState.firstVisibleItemIndex != pinState.scrollPosAtOverride) {
                        pinState.clearManualOverride()
                    }
                }
            }

            val safeIndex = if (pinState.manualOverrideIndex >= 0)
                pinState.manualOverrideIndex.coerceIn(0, pinnedByOrder.size - 1)
            else
                scrollAwarePinIndex.coerceIn(0, pinnedByOrder.size - 1)
            val currentPin = pinnedByOrder[safeIndex]

            fun scrollToPinMsg(pin: ChatMessage) {
                val revMsgs = messages.reversed()
                val idx = revMsgs.indexOfFirst { it.timestamp == pin.timestamp && it.id == pin.id }
                if (idx >= 0) {
                    pinState.pinHighlightTs = pin.timestamp
                    scope.launch {
                        listState.animateScrollToItem(idx)
                        delay(2000)
                        pinState.pinHighlightTs = 0L
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
                            val revMsgs = messages.reversed()
                            val landingIdx = revMsgs.indexOfFirst { it.timestamp == currentPin.timestamp && it.id == currentPin.id }
                            pinState.applyBarTapOverride(safeIndex, landingIdx)
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
                            if (pinnedByOrder.size > 1) context.getString(R.string.chat_pinned_message_num, safeIndex + 1) else stringResource(R.string.chat_pinned_message),
                            color = C.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            currentPin.text.ifEmpty { if (currentPin.file != null) stringResource(R.string.chat_file_label) else stringResource(R.string.chat_message_label) },
                            color = C.textSecondary, fontSize = 13.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // Pin list icon — opens full pin list screen
                    IconButton(
                        onClick = { pinState.showPinListDialog = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Default.PushPin, stringResource(R.string.chat_pinned_messages), tint = C.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Pin list dialog (full screen style)
            if (pinState.showPinListDialog) {
                AlertDialog(
                    onDismissRequest = { pinState.dismissPinListDialog() },
                    containerColor = C.card,
                    title = {
                        Text(stringResource(R.string.chat_pinned_messages, pinnedByOrder.size), color = C.text, fontWeight = FontWeight.SemiBold)
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
                                                pin.text.ifEmpty { if (pin.file != null) stringResource(R.string.chat_pinned_file) else stringResource(R.string.chat_pinned_message) },
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
                                                pinState.dismissPinListDialog()
                                                scrollToPinMsg(pin)
                                            },
                                            modifier = Modifier.size(32.dp),
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowForward,
                                                context.getString(R.string.chat_jump),
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
                                pinState.dismissPinListDialog()
                            }) {
                                Text(stringResource(R.string.chat_unpin_all), color = C.error, fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pinState.dismissPinListDialog() }) {
                            Text(stringResource(R.string.general_close), color = C.textSecondary)
                        }
                    },
                )
            }
        }

        // Messages list + scroll-to-bottom button
        val wallpaperBg = when {
            chrome.chatWallpaper.startsWith("custom:") -> {
                val path = chrome.chatWallpaper.removePrefix("custom:").substringBefore("#")
                val file = java.io.File(path)
                if (file.exists()) {
                    // Cache decoded bitmap to avoid re-decoding on every recomposition
                    val cachedBmp = remember(chrome.chatWallpaper) { android.graphics.BitmapFactory.decodeFile(path) }
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
            chrome.chatWallpaper == "dark_blue" -> Modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF0D1B2A), Color(0xFF1B2838))))
            chrome.chatWallpaper == "teal" -> Modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF004D40), Color(0xFF00695C))))
            chrome.chatWallpaper == "purple" -> Modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF1A0033), Color(0xFF2D1B69))))
            chrome.chatWallpaper == "midnight" -> Modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF0F0F23), Color(0xFF1A1A3E))))
            chrome.chatWallpaper == "forest" -> Modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF1B3A2D), Color(0xFF2D5016))))
            chrome.chatWallpaper == "sunset" -> Modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF2D1B00), Color(0xFF4A2600))))
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
                    // Don't group images that have captions — captions need individual bubbles
                    if (msg.text.isNotEmpty()) continue
                    // Collect consecutive images from same sender
                    val albumIds = mutableListOf(msg.id)
                    var j = i + 1
                    while (j < reversedMessages.size) {
                        val next = reversedMessages[j]
                        if (next.type == "file" && next.file != null &&
                            com.privimemobile.protocol.Helpers.isImageMime(next.file.mime) &&
                            next.sent == msg.sent &&
                            kotlin.math.abs(next.timestamp - msg.timestamp) < 60 &&
                            next.text.isEmpty()
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
            flingBehavior = remember { TelegramFlingBehavior() },
        ) {
            items(
                reversedMessages,
                key = { msg -> ChatListDerive.messageItemKey(msg) },
            ) { msg ->
                // Skip non-first album images (rendered as grid in the first item)
                if (msg.id in albumSkipIds) return@items
                // Telegram-style message appear animation
                var appeared by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { appeared = true }
                val easeOutQuint = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
                val msgAlpha by animateFloatAsState(
                    targetValue = if (appeared) 1f else 0f,
                    animationSpec = tween(400, easing = easeOutQuint),
                    label = "msgAlpha",
                )
                val msgScale by animateFloatAsState(
                    targetValue = if (appeared) 1f else if (msg.sent) 0.6f else 0.85f,
                    animationSpec = if (msg.sent) spring(dampingRatio = 0.6f, stiffness = 400f)
                        else tween(400, easing = easeOutQuint),
                    label = "msgScale",
                )
                val msgOffsetY by animateFloatAsState(
                    targetValue = if (appeared) 0f else if (msg.sent) 40f else 15f,
                    animationSpec = tween(if (msg.sent) 500 else 350, easing = easeOutQuint),
                    label = "msgOffset",
                )

                val index = ChatListDerive.indexInReversedList(reversedMessages, msg)
                val prevMsg = if (index < reversedMessages.size - 1) reversedMessages[index + 1] else null // older
                val nextMsg = if (index > 0) reversedMessages[index - 1] else null // newer
                val curDateLabel = formatDateSeparator(msg.timestamp, context)
                val cluster = ChatListDerive.computeClusterFlags(
                    index,
                    reversedMessages,
                    prevMsg?.let { formatDateSeparator(it.timestamp, context) },
                    curDateLabel,
                    nextMsg?.let { formatDateSeparator(it.timestamp, context) },
                )
                val showDateSep = cluster.showDateSep
                val isFirstInCluster = cluster.isFirstInCluster
                val isLastInCluster = cluster.isLastInCluster
                val showTimestamp = cluster.showTimestamp

                Column(
                    modifier = Modifier
                        .animateItem(
                            fadeInSpec = tween(300, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)),
                            fadeOutSpec = tween(300, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)),
                            placementSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
                        )
                        .graphicsLayer {
                            this.alpha = msgAlpha
                            scaleX = msgScale
                            scaleY = msgScale
                            translationY = msgOffsetY
                            // Sent messages scale from bottom-right, received from bottom-left
                            transformOrigin = if (msg.sent)
                                androidx.compose.ui.graphics.TransformOrigin(1f, 1f)
                            else
                                androidx.compose.ui.graphics.TransformOrigin(0f, 1f)
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
                                modifier = Modifier.clickable { input.showDatePicker = true },
                            ) {
                                Text(
                                    formatDateSeparator(msg.timestamp, context),
                                    color = C.textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }

                    // "X new messages" unread divider — shown at the boundary between read and unread
                    val unreadCount = initialUnreadCount ?: 0
                    if (unreadBoundaryIndex >= 0 && index == unreadBoundaryIndex) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = C.accent.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    context.resources.getQuantityString(R.plurals.chat_new_messages, unreadCount, unreadCount),
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
                        if (selection.selectionMode) {
                            Checkbox(
                                checked = msg.id in selection.selectedIds,
                                onCheckedChange = { selection.toggleSelected(msg.id) },
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
                                colors = CardDefaults.cardColors(containerColor = if (isMine) C.bubbleMine else C.bubbleOther),
                                modifier = Modifier.widthIn(max = 280.dp),
                            ) {
                                // 2-column grid
                                val columns = 2
                                albumMsgs.chunked(columns).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        row.forEach { albumMsg ->
                                            val fp = files.filePaths[albumMsg.file?.cid ?: ""]
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1f)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .clickable {
                                                        if (fp != null) media.fullscreenImage = FullscreenImageData(fp, albumMsg.file?.name ?: context.getString(R.string.chat_pinned_file), albumMsg.id.toLong(), albumMsg.timestamp, albumMsg.sent)
                                                    },
                                            ) {
                                                if (fp != null) {
                                                    AsyncImage(
                                                        model = java.io.File(fp),
                                                        contentDescription = stringResource(R.string.chat_media_section),
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
                                                    if (cid.isNotEmpty() && files.downloadStatuses[cid] != "downloading") {
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
                                    Text(context.getString(R.string.chat_album_photos, albumMsgs.size), color = C.textMuted, fontSize = 10.sp)
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
                        filePath = files.filePaths[msg.file?.cid ?: ""],
                        downloadStatus = files.downloadStatuses[msg.file?.cid ?: ""],
                        attachmentExtras = attachmentMap[msg.id.toLong()]?.extras,
                        isFirstInCluster = isFirstInCluster,
                        isLastInCluster = isLastInCluster,
                        showTimestamp = showTimestamp,
                        onDownload = { cid, key, iv, mime, data ->
                            handleDownload(cid, key, iv, mime, data)
                        },
                        onReply = if (selection.selectionMode) {{ /* no-op in selection mode */ }} else {{ input.replyingTo = msg }},
                        onTap = {
                            if (selection.selectionMode) {
                                selection.toggleSelected(msg.id)
                            } else if (msg.type == "sticker" && msg.stickerPackId != null) {
                                // Sticker tap → view pack
                                emoji.viewPackId = msg.stickerPackId
                            } else {
                                menu.contextMenuMsg = msg
                            }
                        },
                        onLongPress = {
                            if (!selection.selectionMode) {
                                if (msg.type == "sticker") {
                                    menu.contextMenuMsg = msg
                                } else {
                                    selection.enterSelectionWith(msg.id)
                                }
                            } else {
                                selection.toggleSelected(msg.id)
                            }
                        },
                        onFullscreenImage = {
                            val fp = files.filePaths[msg.file?.cid ?: ""]
                            if (fp != null) media.fullscreenImage = FullscreenImageData(fp, msg.file?.name ?: context.getString(R.string.chat_pinned_file), msg.id.toLong(), msg.timestamp, msg.sent)
                        },
                        isSelected = selection.selectionMode && msg.id in selection.selectedIds,
                        onPollVote = { optIdx ->
                            val now = System.currentTimeMillis()
                            if (now - lastSendTime < 3000) {
                                Toast.makeText(context, R.string.toast_wait_moment, Toast.LENGTH_SHORT).show()
                                return@MessageBubble
                            }
                            val pollData = msg.pollData
                            if (pollData == null) return@MessageBubble
                            scope.launch {
                                val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
                                val voteHandle = state?.myHandle ?: return@launch
                                val freshPollData = com.privimemobile.chat.ChatService.db
                                    ?.messageDao()?.findById(msg.id.toLong())?.pollData
                                    ?: pollData
                                when (val result = com.privimemobile.chat.poll.PollLogic.applyVote(
                                    freshPollData, voteHandle, optIdx,
                                )) {
                                    is com.privimemobile.chat.poll.PollLogic.VoteResult.Rejected -> {
                                        val toastRes = when (result.reason) {
                                            com.privimemobile.chat.poll.PollLogic.VoteRejectReason.CLOSED ->
                                                R.string.toast_poll_closed
                                            com.privimemobile.chat.poll.PollLogic.VoteRejectReason.ALREADY_VOTED ->
                                                R.string.toast_poll_already_voted
                                            com.privimemobile.chat.poll.PollLogic.VoteRejectReason.INVALID_OPTION ->
                                                return@launch
                                        }
                                        Toast.makeText(context, toastRes, Toast.LENGTH_SHORT).show()
                                    }
                                    is com.privimemobile.chat.poll.PollLogic.VoteResult.Applied -> {
                                        lastSendTime = now
                                        val voteMsgId = msg.id.toLong()
                                        com.privimemobile.chat.ChatService.db?.messageDao()?.updatePollData(
                                            voteMsgId, result.pollData,
                                        )
                                        pollUi.patch(voteMsgId, result.pollData)
                                        val votePayload = mapOf(
                                            "v" to 1, "t" to "poll_vote",
                                            "ts" to System.currentTimeMillis() / 1000,
                                            "from" to voteHandle,
                                            "to" to (if (isGroupMode) groupId!! else handle),
                                            "msg_ts" to msg.timestamp,
                                            "option" to optIdx,
                                        )
                                        if (isGroupMode && groupId != null) {
                                            com.privimemobile.chat.ChatService.groups.sendGroupPayload(
                                                groupId, votePayload,
                                            )
                                        } else {
                                            val walletId = resolvedSbbsAddress
                                            if (!walletId.isNullOrEmpty()) {
                                                com.privimemobile.chat.ChatService.sbbs.sendWithRetry(
                                                    walletId, votePayload,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        myHandle = myHandle,
                        reactions = reactionMap[msg.timestamp] ?: emptyList(),
                        onReactionTap = { emoji, msgTs, isMine ->
                            // Tap: mine → remove, not-mine → send
                            scope.launch {
                                val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
                                if (state?.myHandle != null) {
                                    if (isMine) {
                                        // Remove reaction
                                        val nowTs = System.currentTimeMillis() / 1000
                                        com.privimemobile.chat.ChatService.db!!.reactionDao().remove(msgTs, state.myHandle!!, emoji, nowTs)
                                        val unreactPayload = mapOf(
                                            "v" to 1, "t" to "unreact",
                                            "ts" to System.currentTimeMillis() / 1000,
                                            "from" to state.myHandle!!,
                                            "to" to (if (isGroupMode) groupId!! else handle),
                                            "msg_ts" to msgTs, "emoji" to emoji,
                                        )
                                        if (isGroupMode && groupId != null) {
                                            com.privimemobile.chat.ChatService.groups.sendGroupPayload(groupId, unreactPayload)
                                        } else {
                                            val walletId = resolvedSbbsAddress
                                            if (!walletId.isNullOrEmpty()) {
                                                com.privimemobile.chat.ChatService.sbbs.sendWithRetry(walletId, unreactPayload)
                                            }
                                        }
                                    } else {
                                        // Send reaction
                                        val ts = System.currentTimeMillis() / 1000
                                        val insertId = com.privimemobile.chat.ChatService.db!!.reactionDao().insert(
                                            com.privimemobile.chat.db.entities.ReactionEntity(
                                                messageTs = msgTs,
                                                senderHandle = state.myHandle!!,
                                                emoji = emoji,
                                                timestamp = ts,
                                            )
                                        )
                                        if (insertId == -1L) {
                                            com.privimemobile.chat.ChatService.db!!.reactionDao().reactivate(msgTs, state.myHandle!!, emoji, ts)
                                        }
                                        val reactPayload = mapOf(
                                            "v" to 1, "t" to "react",
                                            "ts" to System.currentTimeMillis() / 1000,
                                            "from" to state.myHandle!!,
                                            "to" to (if (isGroupMode) groupId!! else handle),
                                            "msg_ts" to msgTs, "emoji" to emoji,
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
                            }
                        },
                        onReactionLongPress = { emoji, msgTs ->
                            // Long-press any pill → show who reacted with this emoji
                            menu.reactionDetailMsg = msg
                            menu.reactionDetailEmoji = emoji
                        },
                        isHighlighted = search.searchHighlightTs == msg.timestamp || searchHighlightFromNav == msg.timestamp || pinState.pinHighlightTs == msg.timestamp || menu.replyHighlightTs == msg.timestamp,
                        isGroupMode = isGroupMode,
                        onSenderTap = { senderHandle ->
                            if (senderHandle.isNotEmpty()) onViewContact(senderHandle)
                        },
                        groupMemberNames = groupMemberNames,
                        onScrollToReply = { replyTs ->
                            val targetIdx = reversedMessages.indexOfFirst { it.timestamp == replyTs }
                            if (targetIdx >= 0) {
                                scope.launch {
                                    listState.animateScrollToItem(targetIdx)
                                    menu.replyHighlightTs = replyTs
                                    kotlinx.coroutines.delay(2000)
                                    menu.replyHighlightTs = null
                                }
                            }
                        },
                    )
                    } // close Row (selection mode wrapper)
                }
            }
        }

        // Pending file preview bar with thumbnail
        if (input.pendingFile != null) {
            Surface(
                color = C.card,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (Helpers.isImageMime(input.pendingFile!!.mimeType)) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(6.dp)),
                        ) {
                            coil.compose.AsyncImage(
                                model = input.pendingFile!!.uri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                            // Remove button overlaid on thumbnail
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .clickable { input.pendingFile = null },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.chat_label_delete),
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "${input.pendingFile!!.name} (${Helpers.formatFileSize(input.pendingFile!!.size)})",
                            color = C.text,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Text("\uD83D\uDCCE", fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${input.pendingFile!!.name} (${Helpers.formatFileSize(input.pendingFile!!.size)})",
                            color = C.text,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { input.pendingFile = null }) {
                            Text(stringResource(R.string.chat_close), color = C.error, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Scroll-to-bottom FAB with unread count
        val showScrollButton by remember {
            derivedStateOf { listState.firstVisibleItemIndex > 3 }
        }
        // Badge: counts unseen messages below current scroll position.
        // scrollBadge.badgeFloor prevents re-increment on scroll-up: it only decreases as the user
        // scrolls down. Resets when scrollBadge.newMsgVersion changes (new messages arrived) or
        // when user reaches the bottom.
        val unreadBelow by remember(messages, reversedMessages) {
            derivedStateOf {
                @Suppress("UNUSED_EXPRESSION")
                roomMessages.size // subscribe to new message arrivals
                val current = listState.firstVisibleItemIndex
                val raw = ChatScrollMath.computeUnreadBelowRaw(
                    reversedMessages,
                    current,
                    initialUnreadCount,
                    scrollBadge.lastBottomTimestamp,
                )
                val (newFloorState, display) = ChatScrollMath.applyBadgeFloor(
                    raw,
                    ChatScrollMath.BadgeFloorState(scrollBadge.badgeFloor, scrollBadge.badgeFloorVersion),
                    scrollBadge.newMsgVersion,
                )
                scrollBadge.badgeFloor = newFloorState.floor
                scrollBadge.badgeFloorVersion = newFloorState.floorVersion
                display
            }
        }
        val scrollBtnScale by animateFloatAsState(
            targetValue = if (showScrollButton) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
            label = "scrollBtnScale",
        )
        if (scrollBtnScale > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 12.dp)
                    .graphicsLayer { scaleX = scrollBtnScale; scaleY = scrollBtnScale },
            ) {
                SmallFloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    containerColor = C.card,
                    contentColor = C.text,
                    shape = CircleShape,
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.chat_jump), modifier = Modifier.size(24.dp))
                }
                if (unreadBelow > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .defaultMinSize(minWidth = 18.dp)
                            .clip(CircleShape)
                            .background(C.accent)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (unreadBelow > 99) stringResource(R.string.chat_unread_overflow) else "$unreadBelow",
                            color = C.textDark, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        // Sticky date header — floats at top when scrolling
        val stickyDateText = remember(listState.firstVisibleItemIndex, reversedMessages) {
            val idx = listState.firstVisibleItemIndex
            if (idx in reversedMessages.indices) {
                formatDateSeparator(reversedMessages[idx].timestamp, context)
            } else null
        }
        val isScrolling = listState.isScrollInProgress

        LaunchedEffect(isScrolling) {
            if (isScrolling) scrollBadge.stickyVisible = true
            else { kotlinx.coroutines.delay(1500); scrollBadge.stickyVisible = false }
        }
        val stickyAlpha by animateFloatAsState(
            targetValue = if (scrollBadge.stickyVisible && stickyDateText != null) 1f else 0f,
            animationSpec = tween(if (scrollBadge.stickyVisible) 200 else 400),
            label = "stickyAlpha",
        )
        if (stickyAlpha > 0f) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = C.card.copy(alpha = 0.9f),
                shadowElevation = 4.dp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                    .graphicsLayer { alpha = stickyAlpha },
            ) {
                Text(
                    stickyDateText ?: "",
                    color = C.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
        } // end Box wrapper

        // Emoji picker panel (Telegram-style: recent + scrollable categories, 9 columns)
        if (emoji.showEmojiPicker) {
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
                val current = input.inputText.text
                val sel = input.inputText.selection.start
                input.setInputText(current.substring(0, sel) + emoji + current.substring(sel))
                addRecent(emoji)
            }

            // All emoji categories
            val allCategories = listOf(
                stringResource(R.string.chat_emoji_cat_people) to listOf(
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
                stringResource(R.string.chat_emoji_cat_gestures) to listOf(
                    "\uD83D\uDC4D", "\uD83D\uDC4E", "\u270A", "\uD83D\uDC4A", "\uD83E\uDD1B", "\uD83E\uDD1C", "\uD83D\uDC4F", "\uD83D\uDE4C", "\uD83D\uDC50",
                    "\uD83E\uDD32", "\uD83E\uDD1D", "\uD83D\uDE4F", "\u270D\uFE0F", "\uD83D\uDC85", "\uD83E\uDD33", "\uD83D\uDCAA", "\uD83D\uDC4B", "\uD83E\uDD1A",
                    "\u270B", "\uD83D\uDC4C", "\uD83E\uDD0F", "\u270C\uFE0F", "\uD83E\uDD1E", "\uD83E\uDD1F", "\uD83E\uDD18", "\uD83E\uDD19", "\uD83D\uDC46",
                    "\uD83D\uDC47", "\uD83D\uDC48", "\uD83D\uDC49", "\uD83D\uDD95", "\uD83D\uDC4B", "\uD83E\uDEF6", "\uD83E\uDEF1", "\uD83E\uDEF2", "\uD83E\uDEF3",
                ),
                stringResource(R.string.chat_emoji_cat_hearts) to listOf(
                    "\u2764\uFE0F", "\uD83E\uDDE1", "\uD83D\uDC9B", "\uD83D\uDC9A", "\uD83D\uDC99", "\uD83D\uDC9C", "\uD83D\uDDA4", "\uD83D\uDC94",
                    "\uD83D\uDC95", "\uD83D\uDC9E", "\uD83D\uDC93", "\uD83D\uDC97", "\uD83D\uDC96", "\uD83D\uDC98", "\uD83D\uDC9D", "\u2B50", "\uD83C\uDF1F",
                    "\uD83D\uDCAB", "\u26A1", "\uD83D\uDD25", "\uD83D\uDCA5", "\uD83C\uDF89", "\uD83C\uDF8A", "\uD83C\uDFC6", "\uD83E\uDD47", "\uD83E\uDD48",
                    "\uD83E\uDD49", "\uD83D\uDCAF", "\uD83D\uDC8B", "\uD83D\uDCA4", "\uD83D\uDCA8", "\uD83C\uDF08", "\u2600\uFE0F", "\uD83C\uDF19", "\u2744\uFE0F",
                ),
                stringResource(R.string.chat_emoji_cat_animals) to listOf(
                    "\uD83D\uDC36", "\uD83D\uDC31", "\uD83D\uDC2D", "\uD83D\uDC39", "\uD83D\uDC30", "\uD83E\uDD8A", "\uD83D\uDC3B", "\uD83D\uDC28",
                    "\uD83D\uDC2F", "\uD83E\uDD81", "\uD83D\uDC2E", "\uD83D\uDC37", "\uD83D\uDC38", "\uD83D\uDC35", "\uD83D\uDE48", "\uD83D\uDE49",
                    "\uD83D\uDE4A", "\uD83D\uDC27", "\uD83D\uDC26", "\uD83E\uDD85", "\uD83E\uDD86", "\uD83E\uDD89", "\uD83D\uDC3C", "\uD83D\uDC22",
                    "\uD83D\uDC0D", "\uD83E\uDD96", "\uD83D\uDC33", "\uD83D\uDC2C", "\uD83E\uDD8B", "\uD83C\uDF3A", "\uD83C\uDF39", "\uD83C\uDF3B",
                    "\uD83C\uDF3C", "\uD83C\uDF37", "\uD83C\uDF34", "\uD83C\uDF35", "\uD83C\uDF32", "\uD83C\uDF33", "\uD83C\uDF40", "\uD83C\uDF3F",
                ),
                stringResource(R.string.chat_emoji_cat_food) to listOf(
                    "\uD83C\uDF4E", "\uD83C\uDF4A", "\uD83C\uDF4B", "\uD83C\uDF4C", "\uD83C\uDF49", "\uD83C\uDF47", "\uD83C\uDF53", "\uD83C\uDF48",
                    "\uD83C\uDF55", "\uD83C\uDF54", "\uD83C\uDF5F", "\uD83C\uDF2D", "\uD83C\uDF2E", "\uD83C\uDF2F", "\uD83C\uDF73", "\uD83C\uDF5E",
                    "\u2615", "\uD83C\uDF75", "\uD83C\uDF7A", "\uD83C\uDF77", "\uD83E\uDD42", "\uD83C\uDF78", "\uD83E\uDD64", "\uD83C\uDF70",
                    "\uD83C\uDF82", "\uD83C\uDF6B", "\uD83C\uDF6C", "\uD83C\uDF6D", "\uD83C\uDF6E", "\uD83C\uDF6F", "\uD83C\uDF7E", "\uD83E\uDD43",
                ),
                stringResource(R.string.chat_emoji_cat_objects) to listOf(
                    "\uD83D\uDCF1", "\uD83D\uDCBB", "\uD83D\uDCF7", "\uD83C\uDFB5", "\uD83C\uDFB6", "\uD83C\uDFA4", "\uD83C\uDFAC", "\uD83D\uDCDA",
                    "\uD83D\uDD13", "\uD83D\uDD12", "\uD83D\uDD11", "\uD83D\uDCA1", "\uD83D\uDD0B", "\uD83D\uDCE7", "\uD83D\uDCE6", "\uD83D\uDCB0",
                    "\uD83D\uDCB3", "\uD83D\uDE97", "\uD83D\uDE95", "\uD83D\uDE8C", "\u2708\uFE0F", "\uD83D\uDE80", "\uD83C\uDFE0", "\uD83C\uDFEB",
                    "\uD83C\uDFE5", "\u26BD", "\uD83C\uDFC0", "\uD83C\uDFBE", "\uD83C\uDFAF", "\uD83C\uDFF3\uFE0F", "\uD83C\uDFF4", "\uD83C\uDDE6\uD83C\uDDFA",
                ),
            )

            // Category tab icons (matching Telegram)
            val categoryIcons = listOf("\uD83D\uDD53", "\uD83D\uDE00", "\uD83D\uDC4B", "\u2764\uFE0F", "\uD83D\uDC3B", "\uD83C\uDF54", "\uD83D\uDCF1", "\uD83C\uDFAD")
            // emoji.emojiMainTab declared at screen level for IME detection access
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
                            val isActive = if (idx == categoryIcons.size - 1) emoji.emojiMainTab == 1 else emoji.emojiMainTab == 0 && activeTabIdx == idx
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (isActive) C.accent.copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable {
                                        if (idx == categoryIcons.size - 1) {
                                            emoji.emojiMainTab = 1 // Stickers tab
                                        } else {
                                            emoji.emojiMainTab = 0
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

                    if (emoji.emojiMainTab == 0) {
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
                                    Text(stringResource(R.string.chat_recent), color = C.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp))
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
                                Toast.makeText(context, context.getString(R.string.toast_stickers_added, uris.size), Toast.LENGTH_SHORT).show()
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
                                    Toast.makeText(context, context.getString(R.string.toast_stickers_imported, count), Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, R.string.toast_no_images_in_zip, Toast.LENGTH_SHORT).show()
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
                                        .clickable { emoji.showCreateStickerPack = true },
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
                                        Text(stringResource(R.string.chat_no_sticker_packs), color = C.textSecondary, fontSize = 14.sp)
                                        Text(stringResource(R.string.chat_tap_create_pack), color = C.textMuted, fontSize = 12.sp)
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
                                            Text(stringResource(R.string.chat_zip_label), color = C.accent, fontSize = 12.sp)
                                        }
                                        TextButton(onClick = { addToPackLauncher.launch("image/*") }) {
                                            Text(stringResource(R.string.chat_add_sticker), color = C.accent, fontSize = 12.sp)
                                        }
                                        TextButton(onClick = {
                                            // Share entire pack as ZIP
                                            val packFiles = currentPack?.second ?: emptyList()
                                            if (packFiles.isEmpty()) {
                                                Toast.makeText(context, R.string.toast_pack_empty, Toast.LENGTH_SHORT).show()
                                            } else if (!isGroupMode && resolvedSbbsAddress.isNullOrEmpty()) {
                                                Toast.makeText(context, R.string.toast_resolving_address, Toast.LENGTH_SHORT).show()
                                            } else {
                                                val pName = currentPack!!.first
                                                val pId = pName.hashCode().toString(16)
                                                val pTotal = packFiles.size
                                                emoji.showEmojiPicker = false
                                                Toast.makeText(context, context.getString(R.string.toast_packaging_stickers, pTotal), Toast.LENGTH_SHORT).show()
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
                                                                Toast.makeText(context, context.getString(R.string.toast_pack_too_large, zipFile.length() / 1024), Toast.LENGTH_LONG).show()
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
                                                                // Update preview BEFORE network send \u2014 survives early navigation
                                                                val youLabel = context.getString(R.string.chat_sender_you)
                                                                com.privimemobile.chat.ChatService.db?.groupDao()?.updateLastMessage(groupId, ts, "$youLabel: \uD83D\uDCE6 Sticker pack: $pName")
                                                                com.privimemobile.chat.ChatService.groups.sendGroupPayload(groupId, payload)
                                                            } else {
                                                                com.privimemobile.chat.ChatService.db!!.conversationDao().updateLastMessage(stkConvId, ts, "\uD83D\uDCE6 Sticker pack: $pName")
                                                                com.privimemobile.chat.ChatService.sbbs.sendWithRetry(resolvedSbbsAddress!!, payload)
                                                            }
                                                            withContext(Dispatchers.Main) {
                                                                Toast.makeText(context, context.getString(R.string.toast_pack_shared, pTotal, zipFile.length() / 1024), Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                        zipFile.delete()
                                                    } catch (e: Exception) {
                                                        Log.e("ChatScreen", "Share pack error: ${e.message}")
                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(context, context.getString(R.string.toast_share_failed, e.message), Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                }
                                            }
                                        }) {
                                            Text(stringResource(R.string.chat_label_share), color = C.accent, fontSize = 12.sp)
                                        }
                                        TextButton(onClick = {
                                            val dir = java.io.File(stickersRoot, currentPack.first)
                                            dir.deleteRecursively()
                                            packs = loadPacks()
                                            activePackIdx = 0
                                            Toast.makeText(context, R.string.toast_pack_deleted, Toast.LENGTH_SHORT).show()
                                        }) {
                                            Text(stringResource(R.string.chat_label_delete), color = C.error, fontSize = 12.sp)
                                        }
                                    }

                                    // Sticker grid
                                    if (currentPack.second.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(stringResource(R.string.chat_empty_pack_hint), color = C.textMuted, fontSize = 13.sp)
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
                                                                input.pendingFile = PendingFile(uri = android.net.Uri.fromFile(cached), name = file.name, size = cached.length(), mimeType = mime)
                                                                val pName = currentPack?.first ?: context.getString(R.string.chat_sticker_pack_label)
                                                                input.pendingStickerMeta = StickerMeta(pName, pName.hashCode().toString(16), currentPack?.second?.size ?: 0)
                                                                emoji.showEmojiPicker = false
                                                                handleSend()
                                                            },
                                                            onLongPress = {
                                                                file.delete()
                                                                packs = loadPacks()
                                                                Toast.makeText(context, R.string.toast_sticker_removed, Toast.LENGTH_SHORT).show()
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
                                                            contentDescription = stringResource(R.string.chat_sticker_pack_label),
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
                        if (emoji.showCreateStickerPack) {
                            AlertDialog(
                                onDismissRequest = { emoji.showCreateStickerPack = false; newPackName = ""; focusManager.clearFocus(); keyboardController?.hide() },
                                containerColor = C.card,
                                title = { Text(stringResource(R.string.chat_new_sticker_pack), color = C.text) },
                                text = {
                                    OutlinedTextField(
                                        value = newPackName,
                                        onValueChange = { newPackName = it.take(20) },
                                        placeholder = { Text(stringResource(R.string.chat_pack_name), color = C.textMuted) },
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
                                            emoji.showCreateStickerPack = false; newPackName = ""
                                            focusManager.clearFocus(); keyboardController?.hide()
                                        }
                                    }) { Text(stringResource(R.string.chat_create), color = C.accent) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { emoji.showCreateStickerPack = false; newPackName = ""; focusManager.clearFocus(); keyboardController?.hide() }) {
                                        Text(stringResource(R.string.general_cancel), color = C.textSecondary)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        // Self-destruct timer indicator bar
        if (input.oneShotTimer > 0) {
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
                        context.getString(R.string.chat_self_destruct_timer) + ": ${formatTimerLabel(context, input.oneShotTimer)}",
                        color = C.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { input.oneShotTimer = 0 }) {
                        Text(stringResource(R.string.chat_close), color = C.error, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Edit preview bar
        if (input.editingMsg != null) {
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
                            stringResource(R.string.chat_editing_message),
                            color = Color(0xFFFFA726),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            input.editingMsg!!.text.take(80),
                            color = C.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(onClick = {
                        input.editingMsg = null
                        input.setInputText("")
                    }) {
                        Text(stringResource(R.string.chat_close), color = C.error, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Reply preview bar
        if (input.replyingTo != null) {
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
                            if (input.replyingTo!!.sent) stringResource(R.string.chat_you_label) else "@${input.replyingTo!!.from}",
                            color = C.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            input.replyingTo!!.text.ifEmpty {
                                when (input.replyingTo!!.type) {
                                    "file" -> stringResource(R.string.chat_reply_file)
                                    "tip" -> context.getString(R.string.chat_tip_simple, "${Helpers.formatBeam(input.replyingTo!!.tipAmount)} ${com.privimemobile.wallet.assetTicker(input.replyingTo!!.tipAssetId)}")
                                    else -> context.getString(R.string.chat_message_label)
                                }
                            }.take(80),
                            color = C.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(onClick = { input.replyingTo = null }) {
                        Text(stringResource(R.string.chat_close), color = C.error, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Input bar — Telegram-X style (48dp bar, 180ms animations)
        // keyboardController declared at screen level
        val hasText = input.inputText.text.isNotBlank() || input.pendingFile != null
        // Close emoji picker when system keyboard appears
        val imeVisible = androidx.compose.foundation.layout.WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
        LaunchedEffect(imeVisible) {
            if (imeVisible && emoji.showEmojiPicker && emoji.emojiMainTab == 0) emoji.showEmojiPicker = false
            // Only auto-close on emoji tab (emoji.emojiMainTab=0). Sticker tab (emoji.emojiMainTab=1) uses dialogs/pickers that open keyboard.
        }

        // Live duration timer for voice recording
        LaunchedEffect(voice.voiceRecording) {
            while (voice.voiceRecording) {
                voice.voiceRecordDuration = voice.voiceRecorder?.getDurationMs() ?: 0L
                delay(100)
            }
        }

        // ── Input bar & overlays — outer Box with clip=false for floating elements ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { clip = false },
        ) {
            Surface(
                color = C.card,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .then(if (!emoji.showEmojiPicker) Modifier.navigationBarsPadding() else Modifier),
            ) {
                // ── Input bar: left bar content animates, MicButton stays always stable ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Left: AnimatedContent for bar content only (normal ↔ recording ↔ preview)
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedContent(
                            targetState = when { voice.voicePaused -> 2; voice.voiceRecording -> 1; else -> 0 },
                            transitionSpec = {
                                fadeIn(tween(200)).togetherWith(fadeOut(tween(100)))
                            },
                            label = "inputBarMode",
                        ) { state ->
                            if (state == 2) {
                                VoicePreviewBar(
                                    waveform = voice.voicePreviewWaveform,
                                    durationMs = voice.voicePauseDuration,
                                    onDelete = {
                                        voice.voiceRecorder?.cancel()
                                        voice.voicePreviewFile = null
                                        voice.voicePreviewWaveform = null
                                        voice.voicePreviewDuration = 0L
                                        voice.voicePauseDuration = 0L
                                        voice.voicePaused = false
                                        voice.voiceRecording = false
                                        voice.voiceLocked = false
                                        voice.voiceRecorder = null
                                        voice.micIsRecordingVisual = false
                                        voice.micSlideOffset = 0f
                                    },
                                    onSend = {
                                        val result = voice.voiceRecorder?.stop()
                                        if (result != null) {
                                            scope.launch { sendVoiceMessage(result) }
                                        }
                                        voice.voicePreviewFile = null
                                        voice.voicePreviewWaveform = null
                                        voice.voicePreviewDuration = 0L
                                        voice.voicePauseDuration = 0L
                                        voice.voicePaused = false
                                        voice.voiceRecording = false
                                        voice.voiceLocked = false
                                        voice.voiceRecorder = null
                                        voice.micIsRecordingVisual = false
                                        voice.micSlideOffset = 0f
                                    },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                )
                            } else if (state == 1) {
                                VoiceRecordingBar(
                                    durationMs = voice.voiceRecordDuration,
                                    isLocked = voice.voiceLocked,
                                    onCancel = {
                                        voice.voiceRecorder?.cancel()
                                        voice.voiceRecording = false
                                        voice.voiceLocked = false
                                        voice.voiceRecorder = null
                                        voice.micIsRecordingVisual = false
                                    },
                                    onLock = {
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                        voice.voiceLocked = true
                                    },
                                    onSend = {
                                        val result = voice.voiceRecorder?.stop()
                                        if (result != null) {
                                            scope.launch { sendVoiceMessage(result) }
                                        }
                                        voice.voiceRecording = false
                                        voice.voiceLocked = false
                                        voice.voiceRecorder = null
                                    },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                )
                            } else {
                                // Normal input bar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min)
                                        .heightIn(min = 52.dp)
                                        .padding(horizontal = 2.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // Emoji toggle (left — 48dp like Telegram)
                                    IconButton(
                                        onClick = {
                                            if (emoji.showEmojiPicker) {
                                                emoji.showEmojiPicker = false
                                                keyboardController?.show()
                                            } else {
                                                emoji.showEmojiPicker = true
                                                keyboardController?.hide()
                                            }
                                        },
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        AnimatedContent(
                                            targetState = emoji.showEmojiPicker,
                                            transitionSpec = { (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.6f)).togetherWith(fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.6f)) },
                                            label = "emojiIcon",
                                        ) { isEmoji ->
                                            if (isEmoji) {
                                                Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.chat_send_message), tint = C.textSecondary, modifier = Modifier.size(24.dp))
                                            } else {
                                                Text("\uD83D\uDE00", fontSize = 24.sp)
                                            }
                                        }
                                    }

                                    // Text field (center — fills available space)
                                    OutlinedTextField(
                                        value = input.inputText,
                                        onValueChange = { newValue ->
                                            input.inputText = newValue
                                            val text = newValue.text
                                            val cursor = newValue.selection.start
                                            if (text == "/") showCommandMenu = true
                                            else if (showCommandMenu && (!text.startsWith("/") || text.contains(" "))) showCommandMenu = false
                                            // @mention autocomplete detection (group mode only)
                                            if (isGroupMode && cursor > 0) {
                                                var atIdx = -1
                                                for (k in cursor - 1 downTo 0) {
                                                    val c = text[k]
                                                    if (c == '@') { atIdx = k; break }
                                                    if (c == ' ' || c == '\n') break
                                                }
                                                if (atIdx >= 0 && (atIdx == 0 || text[atIdx - 1] == ' ' || text[atIdx - 1] == '\n' ||
                                                            text.substring(0, atIdx).trimEnd().endsWith("/tip"))) {
                                                    mentionStartIdx = atIdx
                                                    mentionFilter = text.substring(atIdx + 1, cursor)
                                                    showMentionMenu = true
                                                } else {
                                                    showMentionMenu = false
                                                }
                                            } else {
                                                showMentionMenu = false
                                            }
                                            if (text.isNotEmpty()) {
                                                if (isGroupMode) com.privimemobile.chat.ChatService.groups.sendGroupTyping(groupId!!)
                                                else com.privimemobile.chat.ChatService.sbbs.sendTyping(convKey)
                                            }
                                        },
                                        placeholder = {
                                            Text(
                                                when {
                                                    input.pendingFile != null -> stringResource(R.string.chat_add_caption)
                                                    isDeletedAccount -> stringResource(R.string.chat_account_deleted_notice)
                                                    !isGroupMode && resolvedSbbsAddress.isNullOrEmpty() -> stringResource(R.string.chat_resolving_address)
                                                    else -> stringResource(R.string.chat_message_placeholder)
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
                                        enabled = (isGroupMode || !resolvedSbbsAddress.isNullOrEmpty()) && !uploading && !isDeletedAccount,
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                    )

                                    // Right side: animated morph icons ↔ send
                                    AnimatedContent(
                                        targetState = input.inputText.text.isNotBlank() || input.pendingFile != null || uploading,
                                        transitionSpec = {
                                            (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.4f) +
                                                slideInVertically(tween(180)) { it / 4 })
                                                .togetherWith(fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.4f) +
                                                    slideOutVertically(tween(150)) { -it / 4 })
                                        },
                                        label = "rightIcons",
                                    ) { showSend ->
                                        if (showSend) {
                                            if (uploading) {
                                                Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(C.accent), contentAlignment = Alignment.Center) {
                                                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = C.textDark, strokeWidth = 2.dp)
                                                }
                                            } else {
                                                Box(
                                                    modifier = Modifier.size(48.dp).clip(CircleShape).background(C.accent)
                                                        .pointerInput(Unit) {
                                                            detectTapGestures(onTap = { handleSend() }, onLongPress = { input.showSchedulePicker = true })
                                                        },
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.chat_send_message), tint = C.textDark, modifier = Modifier.size(22.dp))
                                                }
                                            }
                                        } else {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(48.dp)) {
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
                                                    onClick = { media.showAttachPicker = true },
                                                    enabled = !uploading && !com.privimemobile.chat.transport.IpfsTransport.uploadInProgress,
                                                    modifier = Modifier.size(42.dp),
                                                ) {
                                                    Icon(Icons.Default.AttachFile, stringResource(R.string.chat_attach_file), tint = C.textSecondary, modifier = Modifier.size(22.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Right: MicButton / SendButton — NEVER animates, always stable
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.padding(end = 2.dp, bottom = 2.dp),
                    ) {
                        if (voice.voiceRecording && voice.voiceLocked) {
                            // ── Locked mode: large send button (Telegram-style) ──
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(C.accent)
                                    .clickable {
                                        val result = voice.voiceRecorder?.stop()
                                        if (result != null) {
                                            scope.launch { sendVoiceMessage(result) }
                                        }
                                        voice.voiceRecording = false
                                        voice.voiceLocked = false
                                        voice.voiceRecorder = null
                                        voice.micIsRecordingVisual = false
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    stringResource(R.string.chat_send_message),
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                        } else if (input.inputText.text.isEmpty() && input.pendingFile == null && !voice.voicePaused) {
                            // ── Normal + recording (unlocked): mic button with swipe-to-lock ──
                            MicButton(
                                isRecordingVisual = voice.micIsRecordingVisual,
                                slideOffset = voice.micSlideOffset,
                                hasRecordPermission = voice.hasRecordPermission,
                                onRecordPermissionRequest = {
                                    voicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                scope = scope,
                                view = view,
                                context = context,
                                onStartRecording = {
                                    voice.voiceRecorder = com.privimemobile.chat.voice.VoiceRecorder(
                                        context = context,
                                        amplitudeCallback = { },
                                        onMaxDurationReached = {
                                            val result = voice.voiceRecorder?.stop()
                                            if (result != null && result.durationMs >= 700L) {
                                                scope.launch { sendVoiceMessage(result) }
                                            } else if (result != null) {
                                                result.file.delete()
                                            }
                                            voice.micIsRecordingVisual = false
                                            voice.voiceRecording = false
                                            voice.voiceLocked = false
                                            voice.voiceRecorder = null
                                        }
                                    ).also { recorder ->
                                        if (recorder.start() != null) {
                                            voice.voiceRecording = true
                                            voice.micIsRecordingVisual = true
                                        }
                                    }
                                },
                                onSendRecording = {
                                    voice.voiceRecorder?.stop()?.let { result ->
                                        if (result.durationMs >= 700L) {
                                            scope.launch { sendVoiceMessage(result) }
                                        } else {
                                            result.file.delete()
                                        }
                                    }
                                    voice.voiceRecording = false
                                    voice.voiceRecorder = null
                                    voice.micIsRecordingVisual = false
                                    voice.micSlideOffset = 0f
                                },
                                onCancelRecording = {
                                    voice.voiceRecorder?.cancel()
                                    voice.micIsRecordingVisual = false
                                    voice.voiceRecording = false
                                    voice.voiceRecorder = null
                                },
                                onShowHint = { show ->
                                    voice.micShowRecordHint = show
                                },
                                onLockSwipe = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    voice.voiceLocked = true
                                },
                            )
                        }
                    }
                }
            } // close Surface (input bar)

            // ── Command menu popup (floats above input bar, overlaying messages) ──
            if (showCommandMenu) {
                ChatCommandMenu(
                    isGroupMode = isGroupMode,
                    onCommandSelect = { cmd ->
                        input.setInputText(cmd)
                        showCommandMenu = false
                    },
                )
            }

            // ── @mention autocomplete popup (floats above input bar, overlaying messages) ──
            if (showMentionMenu && filteredMembers.isNotEmpty()) {
                MentionAutocompleteMenu(
                    members = filteredMembers,
                    onSelect = { member ->
                        val text = input.inputText.text
                        val before = text.substring(0, mentionStartIdx)
                        val after = if (input.inputText.selection.start < text.length) text.substring(input.inputText.selection.start) else ""
                        val newText = "$before@${member.handle} $after"
                        input.inputText = androidx.compose.ui.text.input.TextFieldValue(
                            text = newText,
                            selection = androidx.compose.ui.text.TextRange(before.length + member.handle.length + 2),
                        )
                        showMentionMenu = false
                    },
                )
            }

            // ── Record hint tooltip (anchored to input bar top-right, floats above into chat) ──
            if (voice.micShowRecordHint) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-12).dp, y = (-28).dp),
                ) {
                    com.privimemobile.ui.components.RecordHintTooltip(text = stringResource(R.string.chat_voice_hold_to_record))
                }
            }

            // ── Floating indicator: lock pill (recording), pause circle (locked), mic circle (paused) ──
            if (voice.voiceRecording && !voice.voicePaused) {
                if (voice.voiceLocked) {
                    // Pause circle (locked — tap to pause, see waveform preview, then resume or send)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = (-10).dp, y = (-100).dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2A2D2E).copy(alpha = 0.9f))
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    val ok = voice.voiceRecorder?.pause()
                                    if (ok == true) {
                                        // Populate preview data from live recorder
                                        val amps = voice.voiceRecorder?.getAmplitudes() ?: emptyList()
                                        voice.voicePreviewWaveform = com.privimemobile.chat.voice.VoiceRecorder.Companion.packWaveform(amps)
                                        voice.voicePauseDuration = voice.voiceRecorder?.getDurationMs() ?: 0L
                                        voice.voicePreviewDuration = voice.voicePauseDuration
                                        voice.voiceRecordDuration = voice.voicePauseDuration
                                        voice.voiceLocked = false
                                        voice.voicePaused = true
                                        voice.micIsRecordingVisual = false
                                        voice.micSlideOffset = 0f
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(16.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color.White.copy(alpha = 0.9f)),
                                )
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(16.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color.White.copy(alpha = 0.9f)),
                                )
                            }
                        }
                    }
                } else {
                    // Lock pill (recording — swipe up to lock)
                    VoiceLockIndicator(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = (-8).dp, y = (-100).dp),
                    )
                }
            }
            if (voice.voicePaused) {
                // Mic circle (paused — tap to resume recording in the same session)
                // Trash is in VoicePreviewBar (left side of the bar), so no floating trash needed
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = (-10).dp, y = (-100).dp),
                ) {
                    // Mic resume button
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2A2D2E).copy(alpha = 0.9f))
                            .clickable {
                                    // Resume the existing recording session — return to locked state
                                    voice.voicePaused = false
                                    voice.voiceRecording = true
                                    voice.voiceLocked = true
                                    voice.voiceRecorder?.resume()
                                    voice.micIsRecordingVisual = true
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                stringResource(R.string.chat_mic_desc),
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                }
            }
        } // close Box (input bar + tooltip overlay)

        // ── Clear history confirmation ──
        if (chrome.showClearConfirm) {
            AlertDialog(
                onDismissRequest = { chrome.showClearConfirm = false },
                containerColor = C.card,
                title = { Text(stringResource(R.string.chat_clear_history), color = C.text, fontWeight = FontWeight.SemiBold) },
                text = { Text(stringResource(R.string.chat_clear_history_body), color = C.textSecondary) },
                confirmButton = {
                    TextButton(onClick = {
                        if (convId > 0L) {
                            scope.launch {
                                com.privimemobile.chat.ChatService.db?.messageDao()?.softDeleteByConversation(convId)
                                com.privimemobile.chat.ChatService.db?.conversationDao()?.updateLastMessage(convId, 0, null)
                            }
                        }
                        chrome.showClearConfirm = false
                    }) {
                        Text(stringResource(R.string.chat_clear), color = C.error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { chrome.showClearConfirm = false }) {
                        Text(stringResource(R.string.general_cancel), color = C.textSecondary)
                    }
                },
            )
        }

        // ── Delete chat confirmation ──
        if (chrome.showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { chrome.showDeleteConfirm = false },
                containerColor = C.card,
                title = { Text(if (isGroupMode) stringResource(R.string.chat_leave_group) else stringResource(R.string.chat_delete_chat), color = C.text, fontWeight = FontWeight.SemiBold) },
                text = { Text(
                    if (isGroupMode) stringResource(R.string.chat_leave_confirm)
                    else stringResource(R.string.chat_delete_confirm),
                    color = C.textSecondary,
                ) },
                confirmButton = {
                    TextButton(onClick = {
                        chrome.showDeleteConfirm = false
                        if (isGroupMode && groupId != null) {
                            // Fire contract TX first — wallet popup must be confirmed by user
                            com.privimemobile.chat.ChatService.groups.leaveGroup(groupId) { success, error ->
                                if (!success) {
                                    scope.launch {
                                        android.widget.Toast.makeText(
                                            context,
                                            context.getString(R.string.chat_leave_failed, error ?: context.getString(R.string.chat_tx_failed)),
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    }
                                } else {
                                    // Wallet accepted the TX data — wait for on-chain confirmation before deleting local data
                                    scope.launch {
                                        android.widget.Toast.makeText(
                                            context,
                                            context.getString(R.string.chat_leave_tx_submitted),
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    onBack()
                                }
                            }
                        } else if (convId > 0L) {
                            // DM: delete immediately (no contract TX for DMs)
                            scope.launch {
                                com.privimemobile.chat.ChatService.db?.messageDao()?.softDeleteByConversation(convId)
                                com.privimemobile.chat.ChatService.db?.conversationDao()?.softDelete(convId)
                                com.privimemobile.chat.ChatService.db?.contactDao()?.deleteByHandle(handle)
                            }
                            onBack()
                        }
                    }) {
                        Text(stringResource(R.string.general_delete), color = C.error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { chrome.showDeleteConfirm = false }) {
                        Text(stringResource(R.string.general_cancel), color = C.textSecondary)
                    }
                },
            )
        }

        // ── Multi-select delete confirmation ──
        if (selection.showDeleteConfirmDialog && selection.pendingDeleteIds.isNotEmpty()) {
            val count = selection.pendingDeleteIds.size
            // Check if any selected message is ours (for "delete for everyone" option)
            val hasOwnMessages = messages.any { it.id in selection.pendingDeleteIds && it.sent }
            AlertDialog(
                onDismissRequest = { selection.dismissDeleteConfirm() },
                containerColor = C.card,
                title = { Text(context.getString(R.string.chat_delete_messages_title, count, if (count > 1) "s" else ""), color = C.text, fontWeight = FontWeight.SemiBold) },
                text = {
                    Column {
                        // Delete for me
                        TextButton(
                            onClick = {
                                val ids = selection.pendingDeleteIds.toList()
                                val cid = convId
                                scope.launch {
                                    ids.forEach { id ->
                                        com.privimemobile.chat.ChatService.db?.messageDao()?.markDeletedById(id.toLong())
                                    }
                                    // Update chat list preview
                                    refreshConversationPreview(cid)
                                }
                                selection.clearAfterBulkAction()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.chat_delete_for_me), color = C.text, fontSize = 15.sp, modifier = Modifier.fillMaxWidth())
                        }
                        // Delete for everyone (only if we have own messages selected)
                        if (hasOwnMessages) {
                            TextButton(
                                onClick = {
                                    // Capture message data BEFORE coroutine — messages state changes after each delete
                                    val msgsToDelete = messages.filter { it.id in selection.pendingDeleteIds }
                                    val capturedConvId = convId
                                    selection.clearAfterBulkAction()
                                    scope.launch {
                                        val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
                                        if (state?.myHandle != null) {
                                            for ((i, msg) in msgsToDelete.withIndex()) {
                                                com.privimemobile.chat.ChatService.db?.messageDao()?.markDeletedById(msg.id.toLong())
                                            }
                                            // Update preview BEFORE network sends — survives early navigation
                                            refreshConversationPreview(capturedConvId)
                                            for (msg in msgsToDelete) {
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
                                                        val walletId = resolvedSbbsAddress
                                                        if (!walletId.isNullOrEmpty()) {
                                                            com.privimemobile.chat.ChatService.sbbs.sendWithRetry(walletId, delPayload)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.chat_delete_for_everyone), color = C.error, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { selection.dismissDeleteConfirm() }) {
                        Text(stringResource(R.string.general_cancel), color = C.textSecondary)
                    }
                },
            )
        }

        // ── Wallpaper picker ──
        if (chrome.showWallpaperPicker) {
            val wallpaperOptions = listOf(
                "default" to stringResource(R.string.chat_wallpaper_default),
                "dark_blue" to stringResource(R.string.chat_wallpaper_dark_blue),
                "teal" to stringResource(R.string.chat_wallpaper_teal),
                "purple" to stringResource(R.string.chat_wallpaper_purple),
                "midnight" to stringResource(R.string.chat_wallpaper_midnight),
                "forest" to stringResource(R.string.chat_wallpaper_forest),
                "sunset" to stringResource(R.string.chat_wallpaper_sunset),
            )
            AlertDialog(
                onDismissRequest = { chrome.showWallpaperPicker = false },
                containerColor = C.card,
                title = { Text(stringResource(R.string.chat_wallpaper), color = C.text, fontWeight = FontWeight.SemiBold) },
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
                                    .then(if (chrome.chatWallpaper.startsWith("custom:")) Modifier.border(2.dp, C.accent, CircleShape) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) { Text("\uD83D\uDDBC", fontSize = 16.sp) }
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.chat_custom_image), color = C.text, fontSize = 15.sp)
                            if (chrome.chatWallpaper.startsWith("custom:")) {
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
                                        chrome.chatWallpaper = key
                                        prefs.edit().putString("wallpaper_$convKey", key).apply()
                                        chrome.showWallpaperPicker = false
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
                                        .then(if (chrome.chatWallpaper == key) Modifier.border(2.dp, C.accent, CircleShape) else Modifier),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(label, color = C.text, fontSize = 15.sp)
                                if (chrome.chatWallpaper == key) {
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
        if (input.showSchedulePicker) {
            val scheduleOptions = listOf(
                "10min" to stringResource(R.string.chat_schedule_in_10min),
                "30min" to stringResource(R.string.chat_schedule_in_30min),
                "1h" to stringResource(R.string.chat_schedule_in_1h),
                "3h" to stringResource(R.string.chat_schedule_in_3h),
                "tomorrow" to stringResource(R.string.chat_schedule_tomorrow),
                "custom" to stringResource(R.string.chat_schedule_custom),
            )
            var showCustomDateTime by remember { mutableStateOf(false) }

            if (!showCustomDateTime) {
                AlertDialog(
                    onDismissRequest = { input.showSchedulePicker = false },
                    containerColor = C.card,
                    title = { Text(stringResource(R.string.chat_schedule_message), color = C.text, fontWeight = FontWeight.SemiBold) },
                    text = {
                        Column {
                            Text(stringResource(R.string.chat_longpress_schedule_hint), color = C.textSecondary, fontSize = 12.sp,
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
                                                scheduleMessage(input.inputText.text, scheduledAt)
                                                input.showSchedulePicker = false
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
                        onDismissRequest = { showCustomDateTime = false; input.showSchedulePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                val millis = dateState.selectedDateMillis
                                if (millis != null) selectedDateMillis = millis
                            }) { Text(stringResource(R.string.general_next), color = C.accent) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showCustomDateTime = false; input.showSchedulePicker = false }) {
                                Text(stringResource(R.string.general_cancel), color = C.textSecondary)
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
                        onDismissRequest = { showCustomDateTime = false; input.showSchedulePicker = false },
                        containerColor = C.card,
                        title = { Text(stringResource(R.string.chat_pick_time), color = C.text, fontWeight = FontWeight.SemiBold) },
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
                                    scheduleMessage(input.inputText.text, scheduledAt)
                                } else {
                                    Toast.makeText(context, R.string.toast_time_future, Toast.LENGTH_SHORT).show()
                                }
                                showCustomDateTime = false
                                input.showSchedulePicker = false
                            }) { Text(stringResource(R.string.chat_schedule), color = C.accent) }
                        },
                        dismissButton = {
                            TextButton(onClick = { selectedDateMillis = null }) {
                                Text(stringResource(R.string.general_back), color = C.textSecondary)
                            }
                        },
                    )
                }
            }
        }

        // ── View Sticker Pack dialog (local only) ──
        if (emoji.viewPackId != null) {
            // Get pack name from the sticker message that triggered this
            val packName = messages.firstOrNull { it.stickerPackId == emoji.viewPackId }?.stickerPackName ?: stringResource(R.string.chat_sticker_pack_label)
            val stickersRoot = java.io.File(context.filesDir, "stickers")
            val localPackDir = java.io.File(stickersRoot, packName)
            val localFiles = remember(emoji.viewPackId) {
                if (localPackDir.exists()) localPackDir.listFiles()?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
                else emptyList()
            }

            AlertDialog(
                onDismissRequest = { emoji.viewPackId = null },
                containerColor = C.card,
                title = {
                    Column {
                        Text(packName, color = C.text, fontWeight = FontWeight.SemiBold)
                        if (localFiles.isNotEmpty()) {
                            Text(stringResource(R.string.chat_stickers_count, localFiles.size), color = C.accent, fontSize = 12.sp)
                        } else {
                            Text(stringResource(R.string.chat_pack_not_saved), color = C.textSecondary, fontSize = 12.sp)
                        }
                    }
                },
                text = {
                    if (localFiles.isEmpty()) {
                        Text(stringResource(R.string.chat_dont_have_pack), color = C.textMuted, fontSize = 13.sp)
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
                                            contentDescription = stringResource(R.string.chat_sticker_pack_label),
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
                    TextButton(onClick = { emoji.viewPackId = null }) { Text(stringResource(R.string.general_ok), color = C.accent) }
                },
                dismissButton = {},
            )
        }

        // ── Date jump picker ──
        if (input.showDatePicker) {
            val datePickerState = rememberDatePickerState()
            DatePickerDialog(
                onDismissRequest = { input.showDatePicker = false },
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
                        input.showDatePicker = false
                    }) {
                        Text(stringResource(R.string.chat_jump), color = C.accent)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { input.showDatePicker = false }) {
                        Text(stringResource(R.string.general_cancel), color = C.textSecondary)
                    }
                },
                colors = DatePickerDefaults.colors(containerColor = C.card),
            ) {
                DatePicker(state = datePickerState)
            }
        }

        // ── Per-message self-destruct timer picker ──
        if (input.showOneShotTimerPicker) {
            val timerOptions = listOf(
                0 to stringResource(R.string.chat_overflow_timer_off),
                30 to stringResource(R.string.chat_timer_30s),
                300 to stringResource(R.string.chat_timer_5min),
                3600 to stringResource(R.string.chat_timer_1h),
                86400 to stringResource(R.string.chat_timer_1day),
            )
            AlertDialog(
                onDismissRequest = { input.showOneShotTimerPicker = false },
                containerColor = C.card,
                shape = RoundedCornerShape(16.dp),
                title = { Text(stringResource(R.string.chat_self_destruct_timer), color = C.text, fontWeight = FontWeight.SemiBold) },
                text = {
                    Column {
                        Text(
                            stringResource(R.string.chat_next_message_hint),
                            color = C.textSecondary, fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        timerOptions.forEach { (seconds, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        input.oneShotTimer = seconds
                                        input.showOneShotTimerPicker = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = input.oneShotTimer == seconds,
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
                                    refreshConversationPreview(cid)
                                }
                                menu.contextMenuMsg = null
                                Toast.makeText(context, R.string.toast_scheduled_cancelled, Toast.LENGTH_SHORT).show()
                            }
                        }

                        MenuItemRow(stringResource(R.string.chat_delete_for_me), color = C.error) {
                            val cid = convId
                            scope.launch {
                                com.privimemobile.chat.ChatService.db?.messageDao()?.markDeletedById(targetMsg.id.toLong())
                                refreshConversationPreview(cid)
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
                                        refreshConversationPreview(convId)
                                        val delPayload = mapOf(
                                            "v" to 1, "t" to "delete",
                                            "ts" to System.currentTimeMillis() / 1000,
                                            "from" to state.myHandle!!, "to" to (if (isGroupMode) groupId!! else handle),
                                            "msg_ts" to targetMsg.timestamp,
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
        }

        // ── Reaction detail — long-press a reaction pill → tabbed reaction info ──
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

        // ── Forward contact picker dialog ──
        if (forward.isPickerOpen) {
            val fwdMsg = forward.forwardingMsg!!
            val allFwdMsgs = forward.messagesToForward()
            val chatState by com.privimemobile.chat.ChatService.observeState().collectAsState(initial = null)
            val myHandle = chatState?.myHandle
            val forwardContacts = remember(allContacts, myHandle) {
                allContacts.filter { it.handle != myHandle && !it.walletId.isNullOrEmpty() && !it.isDeleted }
            }
            val forwardGroups by com.privimemobile.chat.ChatService.db?.groupDao()?.observeAll()
                ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }

            AlertDialog(
                onDismissRequest = { forward.dismiss() },
                containerColor = C.card,
                title = { Text(stringResource(R.string.chat_forward_to, if (allFwdMsgs.size > 1) "${allFwdMsgs.size} messages" else ""), color = C.text, fontWeight = FontWeight.SemiBold) },
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
                                        context.getString(R.string.chat_messages_selected, allFwdMsgs.size),
                                        color = C.textSecondary,
                                        fontSize = 12.sp,
                                    )
                                } else {
                                    if (fwdMsg.file != null) {
                                        Text(
                                            stringResource(R.string.chat_forward_file_msg, fwdMsg.file.name.ifEmpty { "" }),
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
                            Text(stringResource(R.string.chat_no_contacts), color = C.textSecondary, fontSize = 14.sp)
                        } else {
                            LazyColumn {
                                // Groups section (hidden for multi-forward — SBBS reliability)
                                if (forwardGroups.isNotEmpty() && allFwdMsgs.size <= 1) {
                                    item { Text(stringResource(R.string.chat_section_groups), color = C.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp)) }
                                    items(forwardGroups, key = { "g_${it.groupId}" }) { grp ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val targetGid = grp.groupId
                                                    val targetName = grp.name
                                                    val msgs = allFwdMsgs.toList()
                                                    com.privimemobile.chat.ChatService.scope.launch {
                                                        val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
                                                        if (state?.myHandle != null) {
                                                            val m = msgs.first()
                                                            val fwdFrom = if (m.sent) state.myHandle!! else m.from
                                                            val isFile = m.file != null
                                                            val ts = System.currentTimeMillis() / 1000

                                                            if (isFile) {
                                                                val f = m.file!!
                                                                val fileMeta = mutableMapOf<String, Any?>(
                                                                    "name" to f.name, "size" to f.size,
                                                                    "mime" to f.mime, "key" to f.key, "iv" to f.iv,
                                                                )
                                                                if (f.cid.isNotEmpty() && !f.cid.startsWith("inline-")) fileMeta["cid"] = f.cid
                                                                if (f.data != null) fileMeta["data"] = f.data

                                                                val payload = mutableMapOf<String, Any?>(
                                                                    "v" to 1, "t" to "file", "ts" to ts,
                                                                    "from" to state.myHandle, "to" to targetGid,
                                                                    "dn" to (state.myDisplayName ?: ""),
                                                                    "file" to fileMeta,
                                                                    "fwd_from" to fwdFrom,
                                                                    "fwd_ts" to m.timestamp,
                                                                )
                                                                if (m.text.isNotEmpty()) payload["msg"] = m.text

                                                                val convId = com.privimemobile.chat.ChatService.groups.getOrCreateGroupConversation(targetGid, targetName)
                                                                val dedupKey = "$ts:fwd_file:${m.timestamp}:true"
                                                                val entity = com.privimemobile.chat.db.entities.MessageEntity(
                                                                    conversationId = convId,
                                                                    text = if (m.text.isNotEmpty()) m.text else null,
                                                                    timestamp = ts,
                                                                    sent = true,
                                                                    type = "file",
                                                                    senderHandle = state.myHandle,
                                                                    sbbsDedupKey = dedupKey,
                                                                    fwdFrom = fwdFrom,
                                                                    fwdTs = m.timestamp,
                                                                )
                                                                val msgId = com.privimemobile.chat.ChatService.db!!.messageDao().insert(entity)
                                                                if (msgId > 0) {
                                                                    com.privimemobile.chat.ChatService.db!!.attachmentDao().insert(
                                                                        com.privimemobile.chat.db.entities.AttachmentEntity(
                                                                            messageId = msgId,
                                                                            conversationId = convId,
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
                                                                val preview = if (m.text.isNotEmpty()) context.getString(R.string.chat_forward_file_msg, f.name.ifEmpty { "" }) else context.getString(R.string.chat_forward_file_msg, f.name.ifEmpty { "" })
                                                                com.privimemobile.chat.ChatService.db?.groupDao()?.updateLastMessage(targetGid, ts, preview)

                                                                com.privimemobile.chat.ChatService.groups.sendGroupPayload(targetGid, payload)
                                                            } else {
                                                                Log.d("ChatScreen", "Forward to group $targetGid: text=${m.text.take(20)}")
                                                                com.privimemobile.chat.ChatService.groups.sendGroupMessage(targetGid, m.text, fwdFrom = fwdFrom, fwdTs = m.timestamp)
                                                            }

                                                            withContext(Dispatchers.Main) {
                                                                Toast.makeText(context, context.getString(R.string.toast_forwarded_to, targetName), Toast.LENGTH_SHORT).show()
                                                            }
                                                        } else {
                                                            Log.w("ChatScreen", "Forward failed: myHandle is null")
                                                        }
                                                    }
                                                    forward.dismiss()
                                                    onNavigateToChat("group:$targetGid")
                                                }
                                                .padding(vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            val groupAvatarBmp = remember(grp.groupId) {
                                                try {
                                                    val f = java.io.File(context.filesDir, "group_avatars/${grp.groupId}.webp")
                                                    if (f.exists()) android.graphics.BitmapFactory.decodeFile(f.absolutePath) else null
                                                } catch (_: Exception) { null }
                                            }
                                            if (groupAvatarBmp != null) {
                                                androidx.compose.foundation.Image(
                                                    bitmap = groupAvatarBmp.asImageBitmap(),
                                                    contentDescription = grp.name,
                                                    modifier = Modifier.size(36.dp).clip(CircleShape),
                                                    contentScale = ContentScale.Crop,
                                                )
                                            } else {
                                                Box(
                                                    Modifier.size(36.dp).clip(CircleShape).background(C.accent),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Icon(Icons.Default.Group, null, tint = C.textDark, modifier = Modifier.size(20.dp))
                                                }
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
                                    item { Text(stringResource(R.string.chat_section_contacts), color = C.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp)) }
                                }
                                items(forwardContacts, key = { it.handle }) { contact ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                // Send forwarded message(s) — use ChatService scope so it survives navigation
                                                com.privimemobile.chat.ChatService.scope.launch {
                                                    val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
                                                    if (state?.myHandle != null) {
                                                        val toHandle = contact.handle
                                                        val toConvKey = "@$toHandle"
                                                        val fwdConv = com.privimemobile.chat.ChatService.db!!.conversationDao().getOrCreate(toConvKey, toHandle)
                                                        val sendAddr = com.privimemobile.chat.DmAddressResolver.resolve(contact, fwdConv)
                                                            ?: return@launch
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
                                                            lastPreview = if (isFile) context.getString(R.string.chat_forward_file_msg, m.file!!.name.ifEmpty { "" }) else m.text.take(100)
                                                            lastTs = ts
                                                            com.privimemobile.chat.ChatService.sbbs.sendWithRetry(sendAddr, payload)
                                                            // Delay between forwards to avoid SBBS rate-limiting
                                                            if (i < allFwdMsgs.size - 1) delay(2000)
                                                        }
                                                        if (lastTs > 0) {
                                                            com.privimemobile.chat.ChatService.db!!.conversationDao().updateLastMessage(fwdConv.id, lastTs, lastPreview)
                                                        }
                                                        val count = allFwdMsgs.size
                                                        Toast.makeText(context, context.getString(R.string.toast_forwarded_count, count, if (count > 1) "s" else "", toHandle), Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                forward.dismiss()
                                                // Navigate to the forwarded-to chat
                                                onNavigateToChat(contact.handle)
                                            }
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        com.privimemobile.ui.components.AvatarDisplay(
                                            handle = contact.handle,
                                            displayName = contact.displayName,
                                            size = 36.dp,
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                contact.displayName?.ifEmpty { null } ?: contact.handle,
                                                color = C.text,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                            )
                                            Text(
                                                "@${contact.handle}",
                                                color = C.textSecondary,
                                                fontSize = 12.sp,
                                            )
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
    if (media.showAttachPicker) {
        AttachmentPickerSheet(
            onDismiss = { media.showAttachPicker = false },
            onPickGallery = {
                media.showAttachPicker = false
                galleryPickerLauncher.launch("image/*")
            },
            onPickFile = {
                media.showAttachPicker = false
                filePickerLauncher.launch("*/*")
            },
            onPreviewImage = { uri, _, _, _ ->
                media.showAttachPicker = false
                // Open fullscreen preview for single image
                media.imagePreview = ImagePreviewData(uri, name = "", size = 0L, mimeType = "")
                media.previewCaption = ""
            },
            onMultiImageSelected = { uris ->
                media.showAttachPicker = false
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

    // Single-image preview from gallery (Telegram-style: fullscreen + caption + send)
    AnimatedVisibility(
        visible = media.imagePreview != null,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.6f, animationSpec = tween(180)),
        exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.8f, animationSpec = tween(120)),
    ) {
        media.imagePreview?.let { preview ->
            ImagePreviewSheet(
                previewData = preview,
                caption = media.previewCaption,
                onCaptionChange = { media.previewCaption = it },
                onDismiss = { media.imagePreview = null },
                isSending = media.sendingFromPreview,
                onSend = { caption ->
                media.sendingFromPreview = true
                // First validate file size via getFileInfo
                val info = getFileInfo(context, preview.uri)
                if (info != null) {
                    val isImage = Helpers.isImageMime(info.mimeType)
                    val limit = if (isImage) Config.MAX_FILE_SIZE else Config.MAX_INLINE_SIZE
                    if (info.size > limit) {
                        val msg = if (isImage) context.getString(R.string.chat_image_too_large, Config.MAX_FILE_SIZE / 1024 / 1024)
                        else context.getString(R.string.chat_file_too_large, Config.MAX_INLINE_SIZE / 1024)
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        media.imagePreview = null
                        media.sendingFromPreview = false
                        return@ImagePreviewSheet
                    }
                }
                // Set the caption text if provided
                if (caption.trim().isNotEmpty()) {
                    input.inputText = androidx.compose.ui.text.input.TextFieldValue(caption.trim())
                }
                // Pass through handlePickedUri to set input.pendingFile, then send
                handlePickedUri(preview.uri)
                scope.launch {
                    delay(500) // brief wait for input.pendingFile to be set
                    if (input.pendingFile != null) {
                        handleSend()
                    }
                    media.imagePreview = null
                    media.sendingFromPreview = false
                }
            },
        )
        }
    }

    // Fullscreen image viewer
    if (media.fullscreenImage != null) {
        val fImg = media.fullscreenImage!!
        FullscreenImageViewer(
            filePath = fImg.filePath,
            fileName = fImg.fileName,
            isMine = fImg.isMine,
            onDismiss = { media.fullscreenImage = null },
            onSave = {
                val mime = when {
                    fImg.fileName.endsWith(".png", true) -> "image/png"
                    fImg.fileName.endsWith(".gif", true) -> "image/gif"
                    fImg.fileName.endsWith(".webp", true) -> "image/webp"
                    else -> "image/jpeg"
                }
                saveFileToDownloads(context, fImg.filePath, fImg.fileName, mime)
            },
            onDeleteForMe = {
                scope.launch {
                    com.privimemobile.chat.ChatService.db?.messageDao()?.markDeletedById(fImg.msgId)
                    refreshConversationPreview(convId)
                }
                media.fullscreenImage = null
            },
            onDeleteForEveryone = if (fImg.isMine) { {
                scope.launch {
                    val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
                    if (state?.myHandle != null) {
                        com.privimemobile.chat.ChatService.db?.messageDao()?.markDeletedById(fImg.msgId)
                        // Update preview BEFORE network send — survives early navigation
                        refreshConversationPreview(convId)
                        val delPayload = mapOf(
                            "v" to 1, "t" to "delete",
                            "ts" to System.currentTimeMillis() / 1000,
                            "from" to state.myHandle!!, "to" to (if (isGroupMode) groupId!! else handle),
                            "msg_ts" to fImg.msgTs,
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
                media.fullscreenImage = null
            } } else null,
        )
    }
    } // end Box


private class TelegramFlingBehavior : androidx.compose.foundation.gestures.FlingBehavior {
    override suspend fun androidx.compose.foundation.gestures.ScrollScope.performFling(initialVelocity: Float): Float {
        // Dampen velocity by 20% for a heavier feel
        val dampened = initialVelocity * 0.90f
        if (kotlin.math.abs(dampened) < 50f) return dampened
        var velocityLeft = dampened
        var lastValue = 0f
        androidx.compose.animation.core.AnimationState(
            initialValue = 0f,
            initialVelocity = dampened,
        ).animateDecay(
            androidx.compose.animation.core.exponentialDecay(frictionMultiplier = 1.05f)
        ) {
            val delta = value - lastValue
            lastValue = value
            val consumed = scrollBy(delta)
            if (kotlin.math.abs(consumed) < kotlin.math.abs(delta) * 0.5f) {
                cancelAnimation()
            }
            velocityLeft = velocity
        }
        return velocityLeft
    }
}
