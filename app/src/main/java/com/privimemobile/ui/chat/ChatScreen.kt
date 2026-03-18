package com.privimemobile.ui.chat

import android.util.Log
import android.content.ContentValues
import android.content.Context
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.privimemobile.protocol.*
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
@Composable
fun ChatScreen(
    handle: String,
    displayName: String = "",
    onBack: () -> Unit,
    onMediaGallery: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val convKey = "@$handle"

    // Observe conversation reactively — updates when MessageProcessor creates it
    val conversations by com.privimemobile.chat.ChatService.db?.conversationDao()?.observeAll()
        ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val conv = conversations.firstOrNull { it.convKey == convKey }
    val convId = conv?.id ?: 0L

    // Messages from Room DB — automatically updates when convId changes
    val roomMessages by remember(convId) {
        if (convId > 0L) {
            com.privimemobile.chat.ChatService.db!!.messageDao().observeAll(convId)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.collectAsState(initial = emptyList())

    // Load attachments for file messages
    var attachmentMap by remember { mutableStateOf<Map<Long, com.privimemobile.chat.db.entities.AttachmentEntity>>(emptyMap()) }
    LaunchedEffect(roomMessages) {
        val map = mutableMapOf<Long, com.privimemobile.chat.db.entities.AttachmentEntity>()
        roomMessages.filter { it.type == "file" }.forEach { msg ->
            val att = com.privimemobile.chat.ChatService.db?.attachmentDao()?.findByMessageId(msg.id)
            if (att != null) map[msg.id] = att
        }
        attachmentMap = map
    }

    val messages = remember(roomMessages, attachmentMap) { roomMessages.map { entity ->
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

    val resolvedName = displayName.ifEmpty {
        contact?.displayName?.ifEmpty { null } ?: "@$handle"
    }
    val resolvedWalletId = contact?.walletId

    // Input state
    var inputText by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }

    // Pending file to send
    var pendingFile by remember { mutableStateOf<PendingFile?>(null) }

    // Reply target — set by swiping left on a message
    var replyingTo by remember { mutableStateOf<ChatMessage?>(null) }

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

    // All contacts for forward picker
    val allContacts by com.privimemobile.chat.ChatService.db?.contactDao()?.observeAll()
        ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }

    // 3-dot overflow menu
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showCommandMenu by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // In-chat search
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<com.privimemobile.chat.db.entities.MessageEntity>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var searchHighlightTs by remember { mutableStateOf<Long?>(null) }

    // File download tracking
    val filePaths = remember { mutableStateMapOf<String, String>() }
    val downloadStatuses = remember { mutableStateMapOf<String, String>() } // idle, downloading, decrypting, done, error

    val listState = rememberLazyListState()

    // Mark chat as active, clear unread, send read receipts
    LaunchedEffect(handle) {
        com.privimemobile.chat.ChatService.setActiveChat(convKey)
        com.privimemobile.chat.ChatService.contacts.reResolveOnChatOpen(handle)
    }
    DisposableEffect(handle) {
        onDispose {
            com.privimemobile.chat.ChatService.setActiveChat(null)
        }
    }

    // Scroll to bottom when messages change
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0) // reversed layout, 0 = bottom
        }
    }

    // Load cached file paths for file messages
    LaunchedEffect(messages) {
        messages.forEach { msg ->
            val fileCid = msg.file?.cid ?: ""
            if (fileCid.isNotEmpty() && !filePaths.containsKey(fileCid)) {
                val path = com.privimemobile.chat.transport.IpfsTransport.getLocalFilePath(fileCid)
                if (path != null) filePaths[fileCid] = path
            }
        }
    }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val info = getFileInfo(context, uri)
        if (info != null) {
            Log.d("ChatScreen", "File picked: ${info.name}, ${info.size} bytes, ${info.mimeType}")
            if (info.size > Config.MAX_FILE_SIZE) {
                Log.w("ChatScreen", "File too large: ${info.size} > ${Config.MAX_FILE_SIZE}")
                android.widget.Toast.makeText(context, "File too large (max ${Config.MAX_FILE_SIZE / 1024 / 1024}MB)", android.widget.Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            pendingFile = PendingFile(
                uri = uri,
                name = info.name,
                size = info.size,
                mimeType = info.mimeType,
            )
        } else {
            Log.w("ChatScreen", "File info is null for uri: $uri")
        }
    }

    // Send cooldown — 1s between sends to prevent spam
    var lastSendTime by remember { mutableStateOf(0L) }

    // Send message
    fun handleSend() {
        val now = System.currentTimeMillis()
        if (now - lastSendTime < 3000) {
            android.widget.Toast.makeText(context, "Slow down! Wait 3s between messages", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        lastSendTime = now

        val trimmed = inputText.trim()

        // /tip command — send BEAM or any asset
        // Usage: /tip <amount> [asset_id] [message]
        // Examples: /tip 5  |  /tip 5 hello  |  /tip 1 7 enjoy!
        if (trimmed.startsWith("/tip ", ignoreCase = true)) {
            val parts = trimmed.removePrefix("/tip ").trimStart()
            val tokens = parts.split("\\s+".toRegex(), limit = 3)
            val amountBeam = tokens.getOrNull(0)?.toDoubleOrNull()
            val walletId = resolvedWalletId
            if (amountBeam == null || amountBeam <= 0) {
                Toast.makeText(context, "Usage: /tip <amount> [asset_id] [message]", Toast.LENGTH_SHORT).show()
                return
            }
            if (walletId.isNullOrEmpty()) {
                Toast.makeText(context, "Resolving address...", Toast.LENGTH_SHORT).show()
                return
            }
            // If second token is a number, it's the asset ID; otherwise it's part of the message
            val secondToken = tokens.getOrNull(1)
            val secondIsAssetId = secondToken?.toIntOrNull() != null
            val assetId = if (secondIsAssetId) secondToken!!.toInt() else 0
            val caption = if (secondIsAssetId) {
                tokens.getOrNull(2)?.trim() ?: ""
            } else {
                tokens.drop(1).joinToString(" ").trim()
            }
            val amountGroth = (amountBeam * 100_000_000).toLong()
            val assetName = com.privimemobile.wallet.assetTicker(assetId)
            val tipLabel = "Tip: ${Helpers.formatBeam(amountGroth)} $assetName"
            val txComment = "Tip to @$handle" + if (caption.isNotEmpty()) " — $caption" else ""
            scope.launch {
                try {
                    // Send via wallet API tx_send (same approach as desktop DApp)
                    val txResult = com.privimemobile.protocol.WalletApi.callAsync("tx_send", mapOf(
                        "value" to amountGroth,
                        "address" to walletId,
                        "asset_id" to assetId,
                        "comment" to txComment,
                    ))
                    if (txResult.containsKey("error")) {
                        val errMsg = (txResult["error"] as? Map<*, *>)?.get("message") as? String
                            ?: txResult["error"]?.toString() ?: "Send failed"
                        withContext(Dispatchers.Main) { Toast.makeText(context, "Tip failed: $errMsg", Toast.LENGTH_LONG).show() }
                        return@launch
                    }

                    // Send tip SBBS message
                    val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
                    if (state?.myHandle != null) {
                        val ts = System.currentTimeMillis() / 1000
                        val payload = mutableMapOf<String, Any?>(
                            "v" to 1, "t" to "tip", "ts" to ts,
                            "from" to state.myHandle!!, "to" to handle,
                            "dn" to (state.myDisplayName ?: ""),
                            "amount" to amountGroth,
                        )
                        if (assetId != 0) payload["asset_id"] = assetId
                        if (caption.isNotEmpty()) payload["msg"] = caption
                        // Optimistic insert
                        val tipConv = com.privimemobile.chat.ChatService.db!!.conversationDao().getOrCreate(convKey, handle)
                        val dedupKey = "$ts:tip:$amountGroth:$assetId:true"
                        val entity = com.privimemobile.chat.db.entities.MessageEntity(
                            conversationId = tipConv.id,
                            text = caption.ifEmpty { null },
                            timestamp = ts,
                            sent = true,
                            type = "tip",
                            tipAmount = amountGroth,
                            tipAssetId = assetId,
                            senderHandle = state.myHandle,
                            sbbsDedupKey = dedupKey,
                        )
                        com.privimemobile.chat.ChatService.db!!.messageDao().insert(entity)
                        com.privimemobile.chat.ChatService.db!!.conversationDao().updateLastMessage(tipConv.id, ts, tipLabel)
                        com.privimemobile.chat.ChatService.sbbs.sendWithRetry(walletId, payload)
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
            inputText = ""
            return
        }

        // Send file if pending
        if (pendingFile != null) {
            val file = pendingFile!!
            Log.d("ChatScreen", "Sending file: ${file.name}, ${file.size} bytes, walletId=${resolvedWalletId?.take(16)}")
            if (resolvedWalletId.isNullOrEmpty()) {
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
                            val payload = mutableMapOf<String, Any?>(
                                "v" to 1, "t" to "file", "ts" to ts,
                                "from" to state.myHandle!!, "to" to handle,
                                "dn" to (state.myDisplayName ?: ""),
                                "file" to fileMeta,
                            )
                            if (trimmed.isNotEmpty()) payload["msg"] = trimmed
                            if (fileReplyText != null) payload["reply"] = fileReplyText

                            // Optimistic DB insert
                            val conv = com.privimemobile.chat.ChatService.db!!.conversationDao().getOrCreate(convKey, handle)
                            val dedupKey = "$ts:file:${fileMeta["cid"]}:true"
                            val entity = com.privimemobile.chat.db.entities.MessageEntity(
                                conversationId = conv.id, text = trimmed.ifEmpty { null },
                                timestamp = ts, sent = true, type = "file",
                                senderHandle = state.myHandle, sbbsDedupKey = dedupKey,
                            )
                            val msgId = com.privimemobile.chat.ChatService.db!!.messageDao().insert(entity)

                            // Insert attachment
                            if (msgId > 0) {
                                com.privimemobile.chat.ChatService.db!!.attachmentDao().insert(
                                    com.privimemobile.chat.db.entities.AttachmentEntity(
                                        messageId = msgId, conversationId = conv.id,
                                        ipfsCid = fileMeta["cid"] as? String,
                                        encryptionKey = fileMeta["key"] as? String ?: "",
                                        encryptionIv = fileMeta["iv"] as? String ?: "",
                                        fileName = fileMeta["name"] as? String ?: "file",
                                        fileSize = (fileMeta["size"] as? Number)?.toLong() ?: 0,
                                        mimeType = fileMeta["mime"] as? String ?: "",
                                        inlineData = fileMeta["data"] as? String,
                                        downloadStatus = "done", // we already have the file locally
                                    )
                                )
                            }

                            val preview = "\uD83D\uDCCE ${fileMeta["name"]}"
                            com.privimemobile.chat.ChatService.db!!.conversationDao().updateLastMessage(conv.id, ts, preview)

                            // Send via SBBS
                            com.privimemobile.chat.ChatService.sbbs.sendWithRetry(resolvedWalletId!!, payload)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ChatScreen", "File send error: ${e.message}")
                    android.widget.Toast.makeText(context, e.message ?: "File send failed", android.widget.Toast.LENGTH_LONG).show()
                } finally {
                    uploading = false
                    pendingFile = null
                    inputText = ""
                    replyingTo = null
                }
            }
            return
        }

        // Regular text message
        val walletId = resolvedWalletId
        if (trimmed.isEmpty() || walletId.isNullOrEmpty()) return

        // Capture reply text BEFORE coroutine (state may be cleared by then)
        val replyText = replyingTo?.text?.take(200)?.ifEmpty { null }

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
                // Optimistic insert into DB
                val conv = com.privimemobile.chat.ChatService.db!!.conversationDao().getOrCreate(convKey, handle)
                val dedupKey = "$ts:${trimmed.hashCode().toString(16)}:true"
                val entity = com.privimemobile.chat.db.entities.MessageEntity(
                    conversationId = conv.id,
                    text = trimmed,
                    timestamp = ts,
                    sent = true,
                    type = "dm",
                    senderHandle = state.myHandle,
                    sbbsDedupKey = dedupKey,
                    replyText = replyText,
                )
                com.privimemobile.chat.ChatService.db!!.messageDao().insert(entity)
                com.privimemobile.chat.ChatService.db!!.conversationDao().updateLastMessage(conv.id, ts, trimmed.take(100))

                // Send via SBBS
                com.privimemobile.chat.ChatService.sbbs.sendWithRetry(walletId, payload)
            }
        }

        inputText = ""
        replyingTo = null
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

    val canSend = (inputText.isNotBlank() || pendingFile != null) && !resolvedWalletId.isNullOrEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg),
    ) {
        // Header
        Surface(
            color = C.card,
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) {
                    Text("<", color = C.textSecondary, fontSize = 18.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        resolvedName,
                        color = C.text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val typingVer by com.privimemobile.chat.ChatService.typingVersion.collectAsState()
                    val peerTyping = typingVer >= 0 && com.privimemobile.chat.ChatService.isTyping(convKey)
                    if (peerTyping) {
                        Text("typing...", color = C.accent, fontSize = 12.sp)
                    } else {
                        Text("@$handle", color = C.textSecondary, fontSize = 12.sp)
                    }
                }
                // 3-dot overflow menu
                Box {
                    TextButton(onClick = { showOverflowMenu = true }) {
                        Text("\u22EE", color = C.textSecondary, fontSize = 22.sp, fontWeight = FontWeight.Bold)  // ⋮
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                        modifier = Modifier.background(C.card),
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (showSearch) "Close Search" else "Search", color = C.text) },
                            onClick = {
                                showSearch = !showSearch
                                if (!showSearch) { searchQuery = ""; searchResults = emptyList(); searchHighlightTs = null }
                                showOverflowMenu = false
                            },
                            leadingIcon = { Text("\uD83D\uDD0D", fontSize = 16.sp) },
                        )
                        DropdownMenuItem(
                            text = { Text("Media", color = C.text) },
                            onClick = { showOverflowMenu = false; onMediaGallery() },
                            leadingIcon = { Text("\uD83D\uDDBC", fontSize = 16.sp) },
                        )
                        DropdownMenuItem(
                            text = { Text("View profile", color = C.text) },
                            onClick = { showOverflowMenu = false; showProfileDialog = true },
                            leadingIcon = { Text("\uD83D\uDC64", fontSize = 16.sp) },
                        )
                        DropdownMenuItem(
                            text = { Text("Export chat", color = C.text) },
                            onClick = {
                                showOverflowMenu = false
                                // Export chat as text
                                scope.launch {
                                    val sb = StringBuilder()
                                    sb.appendLine("PriviMe Chat — @$handle")
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
                                            putExtra(android.content.Intent.EXTRA_SUBJECT, "PriviMe Chat — @$handle")
                                        }
                                        context.startActivity(android.content.Intent.createChooser(intent, "Export chat"))
                                    }
                                }
                            },
                            leadingIcon = { Text("\uD83D\uDCE4", fontSize = 16.sp) },
                        )
                        val isMuted = conv?.muted == true
                        DropdownMenuItem(
                            text = { Text(if (isMuted) "Unmute" else "Mute", color = C.text) },
                            onClick = {
                                if (convId > 0L) {
                                    scope.launch {
                                        com.privimemobile.chat.ChatService.db?.conversationDao()?.setMuted(convId, !isMuted)
                                    }
                                }
                                showOverflowMenu = false
                            },
                            leadingIcon = { Text(if (isMuted) "\uD83D\uDD14" else "\uD83D\uDD07", fontSize = 16.sp) },
                        )
                        val isBlocked = conv?.isBlocked == true
                        DropdownMenuItem(
                            text = { Text(if (isBlocked) "Unblock" else "Block", color = if (isBlocked) C.text else C.error) },
                            onClick = {
                                if (convId > 0L) {
                                    scope.launch {
                                        com.privimemobile.chat.ChatService.db?.conversationDao()?.setBlocked(convId, !isBlocked)
                                    }
                                }
                                showOverflowMenu = false
                            },
                            leadingIcon = { Text(if (isBlocked) "\u2705" else "\uD83D\uDEAB", fontSize = 16.sp) },
                        )
                        HorizontalDivider(color = C.border)
                        DropdownMenuItem(
                            text = { Text("Clear history", color = C.error) },
                            onClick = { showOverflowMenu = false; showClearConfirm = true },
                            leadingIcon = { Text("\uD83D\uDDD1", fontSize = 16.sp) },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete chat", color = C.error) },
                            onClick = { showOverflowMenu = false; showDeleteConfirm = true },
                            leadingIcon = { Text("\u274C", fontSize = 16.sp) },
                        )
                    }
                }
            }
        }

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

        // Messages list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            state = listState,
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            val reversedMessages = messages.reversed()
            items(reversedMessages, key = { it.id }) { msg ->
                val index = reversedMessages.indexOf(msg)
                val prevMsg = if (index < reversedMessages.size - 1) reversedMessages[index + 1] else null
                val showDateSep = prevMsg == null ||
                        formatDateSeparator(msg.timestamp) != formatDateSeparator(prevMsg.timestamp)

                Column {
                    if (showDateSep) {
                        Text(
                            formatDateSeparator(msg.timestamp),
                            color = C.textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                    MessageBubble(
                        msg = msg,
                        filePath = filePaths[msg.file?.cid ?: ""],
                        downloadStatus = downloadStatuses[msg.file?.cid ?: ""] ?: "idle",
                        onDownload = { cid, key, iv, mime, data ->
                            handleDownload(cid, key, iv, mime, data)
                        },
                        onReply = { replyingTo = msg },
                        onLongPress = { contextMenuMsg = msg },
                        reactions = reactionMap[msg.timestamp] ?: emptyList(),
                        onRemoveReaction = { emoji, msgTs ->
                            scope.launch {
                                if (myHandle != null) {
                                    // Soft-delete locally (stamp removal time for re-delivery detection)
                                    val nowTs = System.currentTimeMillis() / 1000
                                    com.privimemobile.chat.ChatService.db!!.reactionDao().remove(msgTs, myHandle, emoji, nowTs)
                                    // Send unreact SBBS to remove on other side
                                    val walletId = resolvedWalletId
                                    if (!walletId.isNullOrEmpty()) {
                                        val payload = mapOf(
                                            "v" to 1,
                                            "t" to "unreact",
                                            "ts" to System.currentTimeMillis() / 1000,
                                            "from" to myHandle,
                                            "to" to handle,
                                            "msg_ts" to msgTs,
                                            "emoji" to emoji,
                                        )
                                        com.privimemobile.chat.ChatService.sbbs.sendWithRetry(walletId, payload)
                                    }
                                }
                            }
                        },
                        isHighlighted = searchHighlightTs == msg.timestamp,
                    )
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
                        Triple("/tip", "<amount> [asset_id] [message]", "Send BEAM (default) or any asset"),
                    ).forEach { (cmd, args, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    inputText = "$cmd "
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

        // Input bar
        Surface(color = C.card) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Attach button
                IconButton(
                    onClick = { filePickerLauncher.launch("*/*") },
                    enabled = !uploading && !com.privimemobile.chat.transport.IpfsTransport.uploadInProgress,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = "Attach file",
                        tint = C.accent,
                    )
                }
                // Slash command button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { showCommandMenu = !showCommandMenu }
                        .background(if (showCommandMenu) C.accent.copy(alpha = 0.15f) else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("/", color = C.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        if (showCommandMenu && !it.startsWith("/")) showCommandMenu = false
                        if (it.isNotEmpty()) {
                            com.privimemobile.chat.ChatService.sbbs.sendTyping(convKey)
                        }
                    },
                    placeholder = {
                        Text(
                            when {
                                pendingFile != null -> "Add a caption..."
                                resolvedWalletId.isNullOrEmpty() -> "Resolving address..."
                                else -> "Type a message..."
                            },
                            color = C.textSecondary,
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = C.accent,
                        unfocusedBorderColor = C.border,
                        cursorColor = C.accent,
                    ),
                    maxLines = 4,
                    enabled = !resolvedWalletId.isNullOrEmpty() && !uploading,
                )

                Spacer(Modifier.width(8.dp))

                if (uploading) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(C.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = C.textDark,
                            strokeWidth = 2.dp,
                        )
                    }
                } else {
                    IconButton(
                        onClick = { handleSend() },
                        enabled = canSend,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (canSend) C.accent else C.textSecondary,
                        )
                    }
                }
            }
        }

        // ── Profile dialog ──
        if (showProfileDialog) {
            AlertDialog(
                onDismissRequest = { showProfileDialog = false },
                containerColor = C.card,
                title = { Text(resolvedName, color = C.text, fontWeight = FontWeight.SemiBold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Handle", color = C.textSecondary, fontSize = 12.sp)
                        Text("@$handle", color = C.text, fontSize = 15.sp)
                        if (!resolvedWalletId.isNullOrEmpty()) {
                            Text("Wallet ID", color = C.textSecondary, fontSize = 12.sp)
                            Text(
                                resolvedWalletId!!,
                                color = C.text, fontSize = 12.sp,
                                modifier = Modifier.clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("wallet_id", resolvedWalletId))
                                    Toast.makeText(context, "Copied wallet ID", Toast.LENGTH_SHORT).show()
                                },
                            )
                        }
                        if (contact?.displayName?.isNotEmpty() == true) {
                            Text("Display Name", color = C.textSecondary, fontSize = 12.sp)
                            Text(contact!!.displayName!!, color = C.text, fontSize = 15.sp)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showProfileDialog = false }) {
                        Text("Close", color = C.accent)
                    }
                },
            )
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
                                com.privimemobile.chat.ChatService.db?.messageDao()?.deleteByConversation(convId)
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
                title = { Text("Delete chat", color = C.text, fontWeight = FontWeight.SemiBold) },
                text = { Text("Delete this entire conversation? New messages from this contact will start a fresh chat.", color = C.textSecondary) },
                confirmButton = {
                    TextButton(onClick = {
                        if (convId > 0L) {
                            scope.launch {
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

        // ── Context menu (long-press on message) ──
        if (contextMenuMsg != null) {
            val targetMsg = contextMenuMsg!!
            AlertDialog(
                onDismissRequest = { contextMenuMsg = null },
                containerColor = C.card,
                title = null,
                text = {
                    Column {
                        // Quick reaction row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            listOf("\uD83D\uDC4D", "\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83D\uDC4E").forEach { emoji ->
                                Text(
                                    emoji,
                                    fontSize = 28.sp,
                                    modifier = Modifier
                                        .clickable {
                                            // Send reaction
                                            scope.launch {
                                                val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
                                                if (state?.myHandle != null) {
                                                    // Insert locally (reactivate if soft-deleted)
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
                                                    // Send SBBS
                                                    val walletId = resolvedWalletId
                                                    if (!walletId.isNullOrEmpty()) {
                                                        val payload = mapOf(
                                                            "v" to 1,
                                                            "t" to "react",
                                                            "ts" to System.currentTimeMillis() / 1000,
                                                            "from" to state.myHandle!!,
                                                            "to" to handle,
                                                            "msg_ts" to targetMsg.timestamp,
                                                            "emoji" to emoji,
                                                        )
                                                        com.privimemobile.chat.ChatService.sbbs.sendWithRetry(walletId, payload)
                                                    }
                                                }
                                            }
                                            contextMenuMsg = null
                                        }
                                        .padding(4.dp),
                                )
                            }
                        }

                        HorizontalDivider(color = C.border)

                        // Copy
                        if (targetMsg.text.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("message", targetMsg.text))
                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                    contextMenuMsg = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Copy", color = C.text, modifier = Modifier.fillMaxWidth())
                            }
                        }

                        // Reply
                        TextButton(
                            onClick = {
                                replyingTo = targetMsg
                                contextMenuMsg = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Reply", color = C.text, modifier = Modifier.fillMaxWidth())
                        }

                        // Forward (text or file messages)
                        if (targetMsg.text.isNotEmpty() || targetMsg.file != null) {
                            TextButton(
                                onClick = {
                                    forwardingMsg = targetMsg
                                    contextMenuMsg = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Forward", color = C.text, modifier = Modifier.fillMaxWidth())
                            }
                        }

                        // Delete for me
                        TextButton(
                            onClick = {
                                scope.launch {
                                    com.privimemobile.chat.ChatService.db?.messageDao()?.deleteById(targetMsg.id.toLong())
                                }
                                contextMenuMsg = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Delete for me", color = C.error, modifier = Modifier.fillMaxWidth())
                        }

                        // Delete for everyone (only own messages)
                        if (targetMsg.sent) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
                                        if (state?.myHandle != null) {
                                            // Mark local as deleted
                                            com.privimemobile.chat.ChatService.db?.messageDao()?.markDeleted(
                                                convId, targetMsg.timestamp, state.myHandle!!
                                            )
                                            // Send SBBS delete
                                            val walletId = resolvedWalletId
                                            if (!walletId.isNullOrEmpty()) {
                                                val payload = mapOf(
                                                    "v" to 1,
                                                    "t" to "delete",
                                                    "ts" to System.currentTimeMillis() / 1000,
                                                    "from" to state.myHandle!!,
                                                    "to" to handle,
                                                    "msg_ts" to targetMsg.timestamp,
                                                )
                                                com.privimemobile.chat.ChatService.sbbs.sendWithRetry(walletId, payload)
                                            }
                                        }
                                    }
                                    contextMenuMsg = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Delete for everyone", color = C.error, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                },
                confirmButton = {},
            )
        }

        // ── Forward contact picker dialog ──
        if (forwardingMsg != null) {
            val fwdMsg = forwardingMsg!!
            val chatState by com.privimemobile.chat.ChatService.observeState().collectAsState(initial = null)
            val myHandle = chatState?.myHandle
            val forwardContacts = remember(allContacts, myHandle) {
                allContacts.filter { it.handle != myHandle && !it.walletId.isNullOrEmpty() }
            }

            AlertDialog(
                onDismissRequest = { forwardingMsg = null },
                containerColor = C.card,
                title = { Text("Forward to", color = C.text, fontWeight = FontWeight.SemiBold) },
                text = {
                    Column(modifier = Modifier.heightIn(max = 400.dp)) {
                        // Preview of message being forwarded
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = C.bg.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
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

                        if (forwardContacts.isEmpty()) {
                            Text("No contacts", color = C.textSecondary, fontSize = 14.sp)
                        } else {
                            LazyColumn {
                                items(forwardContacts, key = { it.handle }) { contact ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                // Send forwarded message
                                                scope.launch {
                                                    val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
                                                    if (state?.myHandle != null && !contact.walletId.isNullOrEmpty()) {
                                                        val ts = System.currentTimeMillis() / 1000
                                                        val toHandle = contact.handle
                                                        val toConvKey = "@$toHandle"
                                                        val isFile = fwdMsg.file != null
                                                        val msgType = if (isFile) "file" else "dm"
                                                        val fwdFrom = if (fwdMsg.sent) state.myHandle!! else fwdMsg.from
                                                        val payload = mutableMapOf<String, Any?>(
                                                            "v" to 1, "t" to msgType, "ts" to ts,
                                                            "from" to state.myHandle!!, "to" to toHandle,
                                                            "dn" to (state.myDisplayName ?: ""),
                                                            "fwd_from" to fwdFrom,
                                                            "fwd_ts" to fwdMsg.timestamp,
                                                        )
                                                        if (fwdMsg.text.isNotEmpty()) payload["msg"] = fwdMsg.text
                                                        // Include file attachment in payload
                                                        if (isFile) {
                                                            val f = fwdMsg.file!!
                                                            val fileMap = mutableMapOf<String, Any?>(
                                                                "name" to f.name,
                                                                "size" to f.size,
                                                                "mime" to f.mime,
                                                                "key" to f.key,
                                                                "iv" to f.iv,
                                                            )
                                                            if (f.cid.isNotEmpty() && !f.cid.startsWith("inline-")) fileMap["cid"] = f.cid
                                                            if (f.data != null) fileMap["data"] = f.data
                                                            payload["file"] = fileMap
                                                        }
                                                        // Optimistic insert
                                                        val fwdConv = com.privimemobile.chat.ChatService.db!!.conversationDao().getOrCreate(toConvKey, toHandle)
                                                        val dedupKey = "$ts:fwd:${fwdMsg.timestamp}:true"
                                                        val entity = com.privimemobile.chat.db.entities.MessageEntity(
                                                            conversationId = fwdConv.id,
                                                            text = fwdMsg.text.ifEmpty { null },
                                                            timestamp = ts,
                                                            sent = true,
                                                            type = msgType,
                                                            senderHandle = state.myHandle,
                                                            sbbsDedupKey = dedupKey,
                                                            fwdFrom = fwdFrom,
                                                            fwdTs = fwdMsg.timestamp,
                                                        )
                                                        val msgId = com.privimemobile.chat.ChatService.db!!.messageDao().insert(entity)
                                                        // Insert attachment row for file forwards
                                                        if (isFile && msgId != -1L) {
                                                            val f = fwdMsg.file!!
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
                                                        val preview = if (isFile) "\uD83D\uDCCE ${fwdMsg.file!!.name.ifEmpty { "File" }}" else fwdMsg.text.take(100)
                                                        com.privimemobile.chat.ChatService.db!!.conversationDao().updateLastMessage(fwdConv.id, ts, preview)
                                                        com.privimemobile.chat.ChatService.sbbs.sendWithRetry(contact.walletId!!, payload)
                                                        Toast.makeText(context, "Forwarded to @${toHandle}", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                                forwardingMsg = null
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
    }
}

@Composable
private fun MessageBubble(
    msg: ChatMessage,
    filePath: String?,
    downloadStatus: String,
    onDownload: (cid: String, key: String, iv: String, mime: String, inlineData: String?) -> Unit,
    onReply: () -> Unit = {},
    onLongPress: () -> Unit = {},
    reactions: List<Triple<String, Int, Boolean>> = emptyList(),
    onRemoveReaction: (emoji: String, msgTs: Long) -> Unit = { _, _ -> },
    isHighlighted: Boolean = false,
) {
    val context = LocalContext.current
    val isMine = msg.sent
    val isFileMsg = (msg.file?.cid ?: "").isNotEmpty()
    val fileMime = msg.file?.mime ?: ""
    val isImage = msg.type == "file" && Helpers.isImageMime(fileMime)

    // Swipe-left to reply
    var offsetX by remember { mutableStateOf(0f) }
    var swiped by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongPress() },
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX < -80f && !swiped) {
                            swiped = true
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
                        offsetX = (offsetX + dragAmount).coerceIn(-150f, 0f)
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
            Card(
                shape = RoundedCornerShape(
                    topStart = 12.dp,
                    topEnd = 12.dp,
                    bottomStart = if (isMine) 12.dp else 4.dp,
                    bottomEnd = if (isMine) 4.dp else 12.dp,
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isHighlighted) C.accent.copy(alpha = 0.3f)
                        else if (isMine) Color(0x33DA68F5) else C.card,
                ),
                modifier = Modifier.widthIn(max = 280.dp),
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

                // Tip label
                if (msg.isTip) {
                    Text(
                        "Tip: ${Helpers.formatBeam(msg.tipAmount)} ${com.privimemobile.wallet.assetTicker(msg.tipAssetId)}",
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
                    )
                }

                // Text content
                if (msg.text.isNotEmpty()) {
                    Text(msg.text, color = C.text, fontSize = 15.sp, lineHeight = 20.sp)
                }

                // Meta row (time + read status)
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        formatMessageTime(msg.timestamp),
                        color = C.textSecondary,
                        fontSize = 10.sp,
                    )
                    // Status ticks for sent messages: ✓ sent, ✓✓ delivered, ✓✓ read (blue)
                    if (isMine) {
                        Spacer(Modifier.width(6.dp))
                        when {
                            msg.read -> Text("\u2713\u2713", color = C.accent, fontSize = 10.sp)
                            msg.delivered -> Text("\u2713\u2713", color = C.textSecondary, fontSize = 10.sp)
                            else -> Text("\u2713", color = C.textSecondary, fontSize = 10.sp)
                        }
                    }
                }
            }

        }
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
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (mine) C.accent.copy(alpha = 0.2f) else C.border,
                        modifier = if (mine) Modifier.clickable {
                            onRemoveReaction(emoji, msg.timestamp)
                        } else Modifier,
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
) {
    Column(modifier = Modifier.padding(bottom = 2.dp)) {
        if (isImage && filePath != null) {
            // Show cached image
            AsyncImage(
                model = "file://$filePath",
                contentDescription = fileName,
                modifier = Modifier
                    .width(220.dp)
                    .heightIn(max = 180.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
            // Save button for downloaded images
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    "Save",
                    color = C.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { onSave() }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        } else if (isImage && (downloadStatus == "downloading" || downloadStatus == "decrypting")) {
            // Loading placeholder
            Surface(
                modifier = Modifier
                    .width(220.dp)
                    .height(120.dp),
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
                    .width(220.dp)
                    .height(120.dp)
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

// Date formatting
private val msgTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private fun formatMessageTime(ts: Long): String {
    if (ts <= 0) return ""
    return msgTimeFormat.format(Date(ts * 1000))
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
