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

        com.privimemobile.ui.chat.chrome.ChatEmojiStickerPanel(
            emoji = emoji,
            input = input,
            scope = scope,
            messages = messages,
            isGroupMode = isGroupMode,
            groupId = groupId,
            handle = handle,
            convId = convId,
            convKey = convKey,
            resolvedSbbsAddress = resolvedSbbsAddress,
            onSend = { handleSend() },
        )

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

        com.privimemobile.ui.chat.chrome.ChatViewStickerPackDialog(
            emoji = emoji,
            messages = messages,
        )

        com.privimemobile.ui.chat.dialogs.ChatContextMenuSheet(
            menu = menu,
            input = input,
            forward = forward,
            selection = selection,
            emoji = emoji,
            files = files,
            pollUi = pollUi,
            context = context,
            scope = scope,
            convId = convId,
            handle = handle,
            isGroupMode = isGroupMode,
            groupId = groupId,
            group = group,
            resolvedSbbsAddress = resolvedSbbsAddress,
            myHandle = myHandle,
            groupMemberNames = groupMemberNames,
            onRefreshConversationPreview = { refreshConversationPreview(it) },
        )

        }

        com.privimemobile.ui.chat.dialogs.ChatForwardPickerDialog(
            forward = forward,
            allContacts = allContacts,
            onNavigateToChat = onNavigateToChat,
        )

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
