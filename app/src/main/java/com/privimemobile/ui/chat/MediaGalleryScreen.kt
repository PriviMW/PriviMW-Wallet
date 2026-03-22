package com.privimemobile.ui.chat

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.privimemobile.chat.ChatService
import com.privimemobile.chat.db.entities.AttachmentEntity
import com.privimemobile.chat.transport.IpfsTransport
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.launch

@Composable
fun MediaGalleryScreen(
    handle: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isGroup = handle.length == 64 && handle.all { it.isLetterOrDigit() }
    val convKey = if (isGroup) "g_${handle.take(16)}" else "@$handle"

    // Find conversation
    val conversations by ChatService.db?.conversationDao()?.observeAll()
        ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val conv = conversations.firstOrNull { it.convKey == convKey }
    val convId = conv?.id ?: 0L

    // Observe image attachments
    val images by remember(convId) {
        if (convId > 0L) {
            ChatService.db!!.attachmentDao().observeImages(convId)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.collectAsState(initial = emptyList())

    // Track local file paths
    val filePaths = remember { mutableStateMapOf<String, String>() }

    // Load cached paths
    LaunchedEffect(images) {
        images.forEach { att ->
            val cid = att.ipfsCid ?: return@forEach
            if (!filePaths.containsKey(cid)) {
                val path = IpfsTransport.getLocalFilePath(cid)
                if (path != null) filePaths[cid] = path
            }
        }
    }

    // Fullscreen image state
    var fullscreenImage by remember { mutableStateOf<AttachmentEntity?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().background(C.bg).padding(16.dp),
    ) {
        TextButton(onClick = onBack) {
            Text("< Back", color = C.textSecondary)
        }
        Spacer(Modifier.height(8.dp))
        Text("Media", color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("@$handle", color = C.textSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))

        if (images.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No images shared yet", color = C.textMuted, fontSize = 14.sp)
            }
        } else {
            Text("${images.size} images", color = C.textSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
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
                                    // Download first
                                    scope.launch {
                                        try {
                                            val downloaded = IpfsTransport.downloadFile(
                                                attachmentId = att.id,
                                                ipfsCid = cid,
                                                keyHex = att.encryptionKey,
                                                ivHex = att.encryptionIv,
                                                inlineData = att.inlineData,
                                            )
                                            filePaths[cid] = downloaded
                                            fullscreenImage = att
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (path != null) {
                            AsyncImage(
                                model = "file://$path",
                                contentDescription = att.fileName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            CircularProgressIndicator(
                                color = C.accent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp,
                            )
                        }
                    }
                }
            }
        }
    }

    // Fullscreen image viewer
    if (fullscreenImage != null) {
        val att = fullscreenImage!!
        val cid = att.ipfsCid ?: ""
        val path = filePaths[cid]

        AlertDialog(
            onDismissRequest = { fullscreenImage = null },
            containerColor = C.bg,
            title = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(att.fileName, color = C.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    TextButton(onClick = {
                        if (path != null) {
                            try {
                                val srcFile = java.io.File(path)
                                if (srcFile.exists()) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        val values = ContentValues().apply {
                                            put(MediaStore.Downloads.DISPLAY_NAME, att.fileName)
                                            put(MediaStore.Downloads.MIME_TYPE, att.mimeType)
                                            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                                        }
                                        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                                        if (uri != null) {
                                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                                srcFile.inputStream().use { it.copyTo(out) }
                                            }
                                        }
                                    } else {
                                        val dest = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), att.fileName)
                                        srcFile.inputStream().use { input -> dest.outputStream().use { input.copyTo(it) } }
                                    }
                                    Toast.makeText(context, "Saved to Downloads", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Text("Save", color = C.accent, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            text = {
                if (path != null) {
                    AsyncImage(
                        model = "file://$path",
                        contentDescription = att.fileName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 500.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            },
            confirmButton = {},
        )
    }
}
