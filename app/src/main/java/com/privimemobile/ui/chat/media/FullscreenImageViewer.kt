package com.privimemobile.ui.chat.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.privimemobile.R
import com.privimemobile.ui.chat.FullscreenImageItem
import com.privimemobile.ui.theme.C

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullscreenImageViewer(
    images: List<FullscreenImageItem>,
    initialIndex: Int = 0,
    onDismiss: () -> Unit,
    onSave: (FullscreenImageItem) -> Unit,
    onDeleteForMe: ((FullscreenImageItem) -> Unit)? = null,
    onDeleteForEveryone: ((FullscreenImageItem) -> Unit)? = null,
) {
    val safeInitial = initialIndex.coerceIn(0, images.lastIndex)
    val pagerState = rememberPagerState(
        initialPage = safeInitial,
        pageCount = { images.size },
    )
    val currentImage = images[pagerState.currentPage]

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        var showBars by remember { mutableStateOf(true) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            val pageZoomScales = remember(images) { List(images.size) { mutableFloatStateOf(1f) } }
            val currentPageScale = pageZoomScales[pagerState.currentPage].floatValue

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = currentPageScale <= 1.05f,
            ) { page ->
                val item = images[page]
                var scale by remember(page) { mutableFloatStateOf(1f) }
                var offsetX by remember(page) { mutableFloatStateOf(0f) }
                var offsetY by remember(page) { mutableFloatStateOf(0f) }

                LaunchedEffect(pagerState.currentPage) {
                    if (page != pagerState.currentPage) {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                        pageZoomScales[page].floatValue = 1f
                    }
                }

                val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                    scale = (scale * zoomChange).coerceIn(1f, 5f)
                    pageZoomScales[page].floatValue = scale
                    if (scale > 1f) {
                        offsetX += panChange.x
                        offsetY += panChange.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }

                AsyncImage(
                    model = "file://${item.filePath}",
                    contentDescription = item.fileName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY,
                        )
                        .transformable(state = transformState)
                        .pointerInput(page) {
                            detectTapGestures(
                                onTap = { showBars = !showBars },
                                onDoubleTap = {
                                    if (scale > 1.1f) {
                                        scale = 1f
                                        offsetX = 0f
                                        offsetY = 0f
                                        pageZoomScales[page].floatValue = 1f
                                    } else {
                                        scale = 2.5f
                                        pageZoomScales[page].floatValue = scale
                                    }
                                },
                            )
                        },
                    contentScale = ContentScale.Fit,
                )
            }

            AnimatedVisibility(
                visible = showBars,
                enter = fadeIn(),
                exit = fadeOut(),
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
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.chat_close),
                            tint = Color.White,
                        )
                    }
                    Text(
                        if (images.size > 1) {
                            stringResource(
                                R.string.chat_image_viewer_position,
                                pagerState.currentPage + 1,
                                images.size,
                            )
                        } else {
                            currentImage.fileName
                        },
                        color = C.bubbleText,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (onDeleteForMe != null) {
                        var showDeleteMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showDeleteMenu = true }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.chat_label_delete),
                                    tint = C.error,
                                )
                            }
                            DropdownMenu(
                                expanded = showDeleteMenu,
                                onDismissRequest = { showDeleteMenu = false },
                                containerColor = C.card,
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.chat_delete_for_me), color = C.error) },
                                    onClick = {
                                        showDeleteMenu = false
                                        onDeleteForMe(currentImage)
                                    },
                                )
                                if (onDeleteForEveryone != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.chat_delete_for_everyone), color = C.error) },
                                        onClick = {
                                            showDeleteMenu = false
                                            onDeleteForEveryone(currentImage)
                                        },
                                    )
                                }
                            }
                        }
                    }
                    IconButton(onClick = { onSave(currentImage) }) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = stringResource(R.string.chat_save_file),
                            tint = Color.White,
                        )
                    }
                }
            }

            if (images.size > 1) {
                AnimatedVisibility(
                    visible = showBars,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        images.forEachIndexed { index, _ ->
                            Box(
                                modifier = Modifier
                                    .size(if (index == pagerState.currentPage) 8.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (index == pagerState.currentPage) Color.White
                                        else Color.White.copy(alpha = 0.4f),
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}
