package com.privimemobile.ui.chat

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.privimemobile.R
import com.privimemobile.chat.ChatService
import com.privimemobile.chat.db.entities.AttachmentEntity
import com.privimemobile.chat.transport.IpfsTransport
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGalleryScreen(
    handle: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isGroup = handle.length == 64 && handle.all { it.isLetterOrDigit() }
    val convKey = if (isGroup) "g_${handle.take(16)}" else "@$handle"
    val displayName = if (isGroup) context.getString(R.string.chat_group_name_fallback) else "@$handle"

    val conversations by ChatService.db?.conversationDao()?.observeAll()
        ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val conv = conversations.firstOrNull { it.convKey == convKey }
    val convId = conv?.id ?: 0L

    // Tab state: 0 = Media, 1 = Files
    var selectedTab by remember { mutableIntStateOf(0) }

    // Observe images
    val images by remember(convId) {
        if (convId > 0L) ChatService.db!!.attachmentDao().observeImages(convId)
        else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    // Observe files
    val files by remember(convId) {
        if (convId > 0L) ChatService.db!!.attachmentDao().observeFiles(convId)
        else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    // Track local file paths
    val filePaths = remember { mutableStateMapOf<String, String>() }
    val currentList = if (selectedTab == 0) images else files
    LaunchedEffect(currentList) {
        currentList.forEach { att ->
            val cid = att.ipfsCid ?: return@forEach
            if (!filePaths.containsKey(cid)) {
                val path = IpfsTransport.getLocalFilePath(cid)
                if (path != null) filePaths[cid] = path
            }
        }
    }

    var fullscreenImage by remember { mutableStateOf<AttachmentEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(C.bg)) {
        // Header — flush with status bar
        TopAppBar(
            title = { Text(stringResource(R.string.chat_media_files_title), color = C.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.general_back), tint = C.text)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = C.card),
            windowInsets = WindowInsets(0),
        )

        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = C.card,
            contentColor = C.accent,
            indicator = {},
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.chat_media_section, images.size), fontSize = 13.sp) },
                selectedContentColor = C.accent,
                unselectedContentColor = C.textSecondary,
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.chat_files_section, files.size), fontSize = 13.sp) },
                selectedContentColor = C.accent,
                unselectedContentColor = C.textSecondary,
            )
        }

        // Content
        if (selectedTab == 0) {
            // Media grid
            if (images.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(context.getString(R.string.chat_no_images_yet), color = C.textMuted, fontSize = 14.sp)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(8.dp),
                ) {
                    items(images, key = { it.id }) { att ->
                        val cid = att.ipfsCid ?: ""
                        val path = filePaths[cid]
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(C.border)
                                .clickable {
                                    if (path != null) {
                                        fullscreenImage = att
                                    } else {
                                        scope.launch {
                                            try {
                                                val downloaded = IpfsTransport.downloadFile(
                                                    attachmentId = att.id, ipfsCid = cid,
                                                    keyHex = att.encryptionKey, ivHex = att.encryptionIv,
                                                    inlineData = att.inlineData,
                                                )
                                                filePaths[cid] = downloaded
                                                fullscreenImage = att
                                            } catch (_: Exception) {
                                                Toast.makeText(context, context.getString(R.string.media_download_failed), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (path != null) {
                                AsyncImage(
                                    model = "file://$path", contentDescription = att.fileName,
                                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                                )
                            } else {
                                CircularProgressIndicator(color = C.accent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        } else {
            // Files list
            if (files.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(context.getString(R.string.chat_no_files_yet), color = C.textMuted, fontSize = 14.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.padding(8.dp)) {
                    items(files, key = { it.id }) { att ->
                        val cid = att.ipfsCid ?: ""
                        val path = filePaths[cid]
                        val sizeText = when {
                            att.fileSize > 1_000_000 -> "%.1f MB".format(att.fileSize / 1_000_000.0)
                            att.fileSize > 1_000 -> "%.1f KB".format(att.fileSize / 1_000.0)
                            else -> "${att.fileSize} B"
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (path != null) {
                                        // Open file
                                        try {
                                            val file = java.io.File(path)
                                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                                context, "${context.packageName}.fileprovider", file
                                            )
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, att.mimeType)
                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            Toast.makeText(context, context.getString(R.string.media_no_app_to_open), Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        scope.launch {
                                            try {
                                                val downloaded = IpfsTransport.downloadFile(
                                                    attachmentId = att.id, ipfsCid = cid,
                                                    keyHex = att.encryptionKey, ivHex = att.encryptionIv,
                                                    inlineData = att.inlineData,
                                                )
                                                filePaths[cid] = downloaded
                                                Toast.makeText(context, context.getString(R.string.media_downloaded), Toast.LENGTH_SHORT).show()
                                            } catch (_: Exception) {
                                                Toast.makeText(context, context.getString(R.string.media_download_failed), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // File icon
                            Box(
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(C.accent.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.InsertDriveFile, null, tint = C.accent, modifier = Modifier.size(24.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    att.fileName, color = C.text, fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "$sizeText · ${att.mimeType}", color = C.textSecondary, fontSize = 12.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            // Download/save button
                            if (path != null) {
                                IconButton(onClick = {
                                    saveFileToDownloads(context, path, att.fileName, att.mimeType)
                                }) {
                                    Icon(Icons.Default.Download, context.getString(R.string.chat_save_file), tint = C.accent)
                                }
                            } else {
                                CircularProgressIndicator(color = C.accent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        }
                        HorizontalDivider(color = C.border.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }

    // Fullscreen image viewer (same style as ChatScreen)
    if (fullscreenImage != null) {
        val att = fullscreenImage!!
        val cid = att.ipfsCid ?: ""
        val path = filePaths[cid]
        var showBars by remember { mutableStateOf(true) }
        var imgScale by remember { mutableFloatStateOf(1f) }
        var imgOffsetX by remember { mutableFloatStateOf(0f) }
        var imgOffsetY by remember { mutableFloatStateOf(0f) }
        val transformState = androidx.compose.foundation.gestures.rememberTransformableState { zoomChange, panChange, _ ->
            imgScale = (imgScale * zoomChange).coerceIn(1f, 5f)
            if (imgScale > 1f) { imgOffsetX += panChange.x; imgOffsetY += panChange.y }
            else { imgOffsetX = 0f; imgOffsetY = 0f }
        }

        androidx.compose.ui.window.Dialog(
            onDismissRequest = { fullscreenImage = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (path != null) {
                    AsyncImage(
                        model = "file://$path", contentDescription = att.fileName,
                        modifier = Modifier.fillMaxWidth()
                            .graphicsLayer(scaleX = imgScale, scaleY = imgScale, translationX = imgOffsetX, translationY = imgOffsetY)
                            .transformable(state = transformState)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { showBars = !showBars },
                                    onDoubleTap = {
                                        if (imgScale > 1.1f) { imgScale = 1f; imgOffsetX = 0f; imgOffsetY = 0f }
                                        else imgScale = 2.5f
                                    },
                                )
                            },
                        contentScale = ContentScale.Fit,
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = showBars,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f))
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { fullscreenImage = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, context.getString(R.string.chat_close), tint = androidx.compose.ui.graphics.Color.White)
                        }
                        Text(att.fileName, color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))

                        // Delete button
                        var isMine by remember { mutableStateOf(false) }
                        var msgTs by remember { mutableLongStateOf(0L) }
                        LaunchedEffect(att.messageId) {
                            val msg = ChatService.db?.messageDao()?.findById(att.messageId)
                            isMine = msg?.sent == true
                            msgTs = msg?.timestamp ?: 0L
                        }
                        var showDelMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showDelMenu = true }) {
                                Icon(Icons.Default.Delete, context.getString(R.string.chat_label_delete), tint = C.error)
                            }
                            DropdownMenu(expanded = showDelMenu, onDismissRequest = { showDelMenu = false }, containerColor = C.card) {
                                DropdownMenuItem(
                                    text = { Text(context.getString(R.string.media_delete_for_me), color = C.error) },
                                    onClick = {
                                        scope.launch { ChatService.db?.messageDao()?.markDeletedById(att.messageId) }
                                        showDelMenu = false; fullscreenImage = null
                                        Toast.makeText(context, context.getString(R.string.media_deleted), Toast.LENGTH_SHORT).show()
                                    },
                                )
                                if (isMine) {
                                    DropdownMenuItem(
                                        text = { Text(context.getString(R.string.media_delete_for_everyone), color = C.error) },
                                        onClick = {
                                            scope.launch {
                                                val state = ChatService.db?.chatStateDao()?.get()
                                                if (state?.myHandle != null && msgTs > 0) {
                                                    ChatService.db?.messageDao()?.markDeletedById(att.messageId)
                                                    val delPayload = mapOf(
                                                        "v" to 1, "t" to "delete",
                                                        "ts" to System.currentTimeMillis() / 1000,
                                                        "from" to state.myHandle!!, "to" to handle,
                                                        "msg_ts" to msgTs,
                                                    )
                                                    if (isGroup) {
                                                        ChatService.groups.sendGroupPayload(handle, delPayload)
                                                    } else {
                                                        val contact = ChatService.db?.contactDao()?.findByHandle(handle)
                                                        if (!contact?.walletId.isNullOrEmpty()) {
                                                            ChatService.sbbs.sendWithRetry(contact!!.walletId!!, delPayload)
                                                        }
                                                    }
                                                }
                                            }
                                            showDelMenu = false; fullscreenImage = null
                                            Toast.makeText(context, context.getString(R.string.media_deleted_for_everyone), Toast.LENGTH_SHORT).show()
                                        },
                                    )
                                }
                            }
                        }

                        IconButton(onClick = {
                            if (path != null) saveFileToDownloads(context, path, att.fileName, att.mimeType ?: "image/jpeg")
                        }) {
                            Icon(Icons.Default.Download, context.getString(R.string.chat_save_file), tint = androidx.compose.ui.graphics.Color.White)
                        }
                    }
                }
            }
        }
    }
}

private fun saveFileToDownloads(context: android.content.Context, srcPath: String, fileName: String, mimeType: String) {
    try {
        val srcFile = java.io.File(srcPath)
        if (!srcFile.exists()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    srcFile.inputStream().use { it.copyTo(out) }
                }
            }
        } else {
            val dest = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
            srcFile.inputStream().use { input -> dest.outputStream().use { input.copyTo(it) } }
        }
        Toast.makeText(context, R.string.media_saved_to_downloads, Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        Toast.makeText(context, R.string.media_save_failed, Toast.LENGTH_SHORT).show()
    }
}
