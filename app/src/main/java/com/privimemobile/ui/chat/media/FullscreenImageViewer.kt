package com.privimemobile.ui.chat.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import com.privimemobile.chat.DeleteAuthorization
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
    isGroupMode: Boolean = false,
    myGroupRole: Int = 0,
) {
    val safeInitial = initialIndex.coerceIn(0, images.lastIndex)
    val pagerState = rememberPagerState(
        initialPage = safeInitial,
        pageCount = { images.size },
    )
    val currentImage = images[pagerState.currentPage]
    // Re-evaluated every recomposition: as user swipes between album images, the
    // "Delete for Everyone" option must track the *currently visible* image's owner,
    // not the image the viewer was opened with. Previous bug: a once-computed gate
    // let the user delete someone else's photo by opening the viewer on their own.
    val canDeleteForEveryoneHere = onDeleteForEveryone != null &&
        DeleteAuthorization.canOfferDeleteForEveryone(isGroupMode, myGroupRole, currentImage.isMine)

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
                val scaleState = remember(page) { mutableFloatStateOf(1f) }
                val offsetXState = remember(page) { mutableFloatStateOf(0f) }
                val offsetYState = remember(page) { mutableFloatStateOf(0f) }
                val scale by scaleState
                val offsetX by offsetXState
                val offsetY by offsetYState

                LaunchedEffect(pagerState.currentPage) {
                    if (page != pagerState.currentPage) {
                        scaleState.floatValue = 1f
                        offsetXState.floatValue = 0f
                        offsetYState.floatValue = 0f
                        pageZoomScales[page].floatValue = 1f
                    }
                }

                AsyncImage(
                    model = "file://${item.filePath}",
                    contentDescription = item.fileName,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY,
                        )
                        // Telegram-style arbitration: only consume pointer events when
                        // pinching (2+ fingers) or already zoomed in (1-finger pan).
                        // At 1x with one finger we leave events UNCONSUMED so the parent
                        // HorizontalPager handles the left/right swipe. Keyed on `page`
                        // only, so the scale crossing 1x never restarts the detector.
                        .pointerInput(page) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val pressedCount = event.changes.count { it.pressed }
                                    val currentScale = scaleState.floatValue
                                    val pinching = pressedCount >= 2
                                    val panningZoomed = pressedCount == 1 && currentScale > 1.05f

                                    if (pinching || panningZoomed) {
                                        val zoom = event.calculateZoom()
                                        val pan = event.calculatePan()
                                        val newScale = (currentScale * zoom).coerceIn(1f, 5f)
                                        scaleState.floatValue = newScale
                                        pageZoomScales[page].floatValue = newScale
                                        if (newScale > 1.05f) {
                                            val maxX = (size.width * (newScale - 1f)) / 2f
                                            val maxY = (size.height * (newScale - 1f)) / 2f
                                            offsetXState.floatValue =
                                                (offsetXState.floatValue + pan.x).coerceIn(-maxX, maxX)
                                            offsetYState.floatValue =
                                                (offsetYState.floatValue + pan.y).coerceIn(-maxY, maxY)
                                        } else {
                                            offsetXState.floatValue = 0f
                                            offsetYState.floatValue = 0f
                                        }
                                        event.changes.forEach { if (it.pressed) it.consume() }
                                    }
                                    // else: single finger at 1x → leave unconsumed for the pager
                                } while (event.changes.any { it.pressed })
                            }
                        }
                        .pointerInput(page) {
                            detectTapGestures(
                                onTap = { showBars = !showBars },
                                onDoubleTap = {
                                    if (scaleState.floatValue > 1.1f) {
                                        scaleState.floatValue = 1f
                                        offsetXState.floatValue = 0f
                                        offsetYState.floatValue = 0f
                                        pageZoomScales[page].floatValue = 1f
                                    } else {
                                        scaleState.floatValue = 2.5f
                                        pageZoomScales[page].floatValue = scaleState.floatValue
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
                                if (canDeleteForEveryoneHere) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.chat_delete_for_everyone), color = C.error) },
                                        onClick = {
                                            showDeleteMenu = false
                                            onDeleteForEveryone?.invoke(currentImage)
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
