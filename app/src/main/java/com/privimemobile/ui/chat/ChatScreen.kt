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

    // Send cooldown — 3s between sends to prevent spam
    var lastSendTime by remember { mutableStateOf(0L) }

    fun buildSendDeps(): ChatSendDeps = ChatSendDeps(
        context = context,
        scope = scope,
        convId = convId,
        convKey = convKey,
        handle = handle,
        isGroupMode = isGroupMode,
        groupId = groupId,
        groupName = group?.name ?: context.getString(R.string.chat_group_name_fallback),
        resolvedSbbsAddress = resolvedSbbsAddress,
        resolvedWalletId = resolvedWalletId,
        input = input,
        voice = voice,
        files = files,
        media = media,
        getLastSendTime = { lastSendTime },
        setLastSendTime = { lastSendTime = it },
        clearInitialUnreadDivider = { initialUnreadCount = 0 },
        clearDraft = {
            if (convId > 0L) {
                scope.launch {
                    com.privimemobile.chat.ChatService.db?.conversationDao()?.setDraft(convId, null)
                }
            }
        },
        onUploadingChange = { uploading = it },
    )

    fun handlePickedUri(uri: Uri) = ChatSendPipeline.handlePickedUri(buildSendDeps(), uri)

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

    fun scheduleMessage(text: String, scheduledAt: Long) =
        ChatSendPipeline.scheduleMessage(buildSendDeps(), text, scheduledAt)

    suspend fun sendVoiceMessage() = ChatSendPipeline.sendVoiceMessage(buildSendDeps())

    suspend fun sendVoiceMessage(result: com.privimemobile.chat.voice.VoiceRecorder.RecordingResult) =
        ChatSendPipeline.sendVoiceMessage(buildSendDeps(), result)

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

    fun handleSend() = ChatSendPipeline.handleSend(buildSendDeps())

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
        if (selection.selectionMode) {
            com.privimemobile.ui.chat.chrome.ChatSelectionHeader(
                selection = selection,
                messages = messages,
                onForwardSelected = { msgs -> forward.openMultiple(msgs) },
            )
        } else {
            com.privimemobile.ui.chat.chrome.ChatHeader(
                view = view,
                scope = scope,
                chrome = chrome,
                search = search,
                input = input,
                messages = messages,
                handle = handle,
                resolvedName = resolvedName,
                convKey = convKey,
                convId = convId,
                conv = conv,
                isGroupMode = isGroupMode,
                groupId = groupId,
                group = group,
                groupMemberCount = groupMemberCount,
                chatPrefs = chatPrefs,
                groupSoundPicker = groupSoundPicker,
                onBack = onBack,
                onContactInfo = onContactInfo,
                onGroupSettings = onGroupSettings,
                onMediaGallery = onMediaGallery,
            )
        }

        com.privimemobile.ui.chat.chrome.ChatSearchOverlay(
            search = search,
            scope = scope,
            onQuerySearch = { query ->
                search.searchResults = com.privimemobile.chat.ChatService.db?.messageDao()
                    ?.searchInConversation(convId, "%$query%") ?: emptyList()
            },
            onResultClick = { ts ->
                val reversedMessages = messages.reversed()
                val idx = reversedMessages.indexOfFirst { it.timestamp == ts }
                if (idx >= 0) {
                    search.searchHighlightTs = ts
                    scope.launch {
                        listState.animateScrollToItem(idx)
                        delay(2000)
                        search.searchHighlightTs = null
                    }
                }
            },
        )

        val pinnedByOrder = messages.filter { it.pinned }.sortedBy { it.pinnedAt }
        com.privimemobile.ui.chat.chrome.ChatPinnedBar(
            pinnedByOrder = pinnedByOrder,
            messages = messages,
            pinState = pinState,
            listState = listState,
            scope = scope,
            isGroupMode = isGroupMode,
            groupMyRole = group?.myRole ?: 0,
            convId = convId,
        )

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

        com.privimemobile.ui.chat.chrome.ChatReplyEditBars(input = input)

        com.privimemobile.ui.chat.chrome.ChatInputBar(
            view = view,
            scope = scope,
            input = input,
            voice = voice,
            emoji = emoji,
            media = media,
            isGroupMode = isGroupMode,
            groupId = groupId,
            convKey = convKey,
            resolvedSbbsAddress = resolvedSbbsAddress,
            uploading = uploading,
            isDeletedAccount = isDeletedAccount,
            showCommandMenu = showCommandMenu,
            onShowCommandMenuChange = { showCommandMenu = it },
            showMentionMenu = showMentionMenu,
            onShowMentionMenuChange = { showMentionMenu = it },
            mentionStartIdx = mentionStartIdx,
            onMentionStartIdxChange = { mentionStartIdx = it },
            onMentionFilterChange = { mentionFilter = it },
            filteredMembers = filteredMembers,
            onRequestRecordPermission = { voicePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            onSend = { handleSend() },
            onSendVoice = { result -> sendVoiceMessage(result) },
        )


        com.privimemobile.ui.chat.dialogs.ChatClearDeleteDialogs(
            chrome = chrome,
            selection = selection,
            convId = convId,
            handle = handle,
            isGroupMode = isGroupMode,
            groupId = groupId,
            messages = messages,
            resolvedSbbsAddress = resolvedSbbsAddress,
            context = context,
            scope = scope,
            onBack = onBack,
            onRefreshConversationPreview = { cid -> refreshConversationPreview(cid) },
        )

        com.privimemobile.ui.chat.dialogs.ChatWallpaperScheduleDialogs(
            chrome = chrome,
            input = input,
            chatPrefs = prefs,
            convKey = convKey,
            context = context,
            scope = scope,
            messages = messages,
            listState = listState,
            wallpaperImagePicker = wallpaperImagePicker,
            onScheduleMessage = { text, scheduledAt -> scheduleMessage(text, scheduledAt) },
        )


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
