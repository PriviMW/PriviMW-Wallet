package com.privimemobile.ui.chat.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.privimemobile.R
import com.privimemobile.protocol.Config
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.Icon
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AttachmentPickerSheet(
    onDismiss: () -> Unit,
    onPickGallery: () -> Unit,
    onPickFile: () -> Unit,
    onPreviewImage: (Uri, String, Long, String) -> Unit, // (uri, name, size, mimeType)
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
                        Triple(0, stringResource(R.string.chat_attach_gallery), Icons.Default.PhotoLibrary),
                        Triple(1, stringResource(R.string.chat_attach_files), Icons.Default.Description),
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
                                BoxWithConstraints {
                                    val density = LocalDensity.current
                                    val availPx = with(density) { maxWidth.toPx() - 26.dp.toPx() }.coerceAtLeast(0f)
                                    val pxPerChar = with(density) { (10.sp).toPx() }
                                    val fitCount = (availPx / pxPerChar).toInt()
                                    val fontSize = when {
                                        label.length > (fitCount * 1.0f).toInt() -> 10.sp
                                        label.length > (fitCount * 0.82f).toInt() -> 12.sp
                                        else -> 14.sp
                                    }
                                    Text(
                                        label,
                                        color = if (isSelected) C.accent else C.textSecondary,
                                        fontSize = fontSize,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
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
                                Text(stringResource(R.string.chat_gallery_access_needed), color = C.textSecondary, fontSize = 14.sp)
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
                                    Text(stringResource(R.string.chat_grant_access), color = C.textDark)
                                }
                                Spacer(Modifier.height(8.dp))
                                TextButton(onClick = onPickGallery) {
                                    Text(stringResource(R.string.chat_pick_gallery), color = C.accent, fontSize = 13.sp)
                                }
                            }
                        } else if (galleryImages.value.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(stringResource(R.string.chat_no_images), color = C.textMuted, fontSize = 14.sp)
                            }
                        } else {
                            val selectedUris = remember { mutableStateListOf<Uri>() }
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Send button when images are selected
                                if (selectedUris.size > 0) {
                                    Surface(
                                        color = C.accent,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (selectedUris.size == 1) {
                                                    // Single selected: go through preview flow
                                                    onMultiImageSelected(selectedUris.toList())
                                                } else {
                                                    onMultiImageSelected(selectedUris.toList())
                                                }
                                            },
                                    ) {
                                        Text(
                                            if (selectedUris.size == 1) stringResource(R.string.chat_send_photo_one) else stringResource(R.string.chat_send_photo_other, selectedUris.size),
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
                                        val inMultiMode = selectedUris.isNotEmpty()
                                        Box(
                                            modifier = Modifier
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(4.dp))
                                                .combinedClickable(
                                                    onClick = {
                                                        if (inMultiMode) {
                                                            if (isSelected) selectedUris.remove(uri)
                                                            else selectedUris.add(uri)
                                                        } else {
                                                            onPreviewImage(uri, "", 0L, "")
                                                        }
                                                    },
                                                    onLongClick = {
                                                        if (!isSelected) selectedUris.add(uri)
                                                    },
                                                ),
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
                                        Text(stringResource(R.string.chat_browse_files), color = C.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                        Text(stringResource(R.string.chat_browse_files_desc), color = C.textSecondary, fontSize = 12.sp)
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
                                        Text(stringResource(R.string.chat_photo_video), color = C.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                        Text(stringResource(R.string.chat_pick_from_gallery), color = C.textSecondary, fontSize = 12.sp)
                                    }
                                }
                            }

                            Spacer(Modifier.weight(1f))

                            Text(
                                context.getString(R.string.chat_max_file_size_hint, Config.MAX_FILE_SIZE / 1024 / 1024),
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
