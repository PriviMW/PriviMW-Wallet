package com.privimemobile.ui.chat.message

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.privimemobile.R
import com.privimemobile.protocol.Helpers
import com.privimemobile.ui.theme.C

@Composable
fun FileContent(
    cid: String,
    fileName: String,
    fileSize: Long,
    filePath: String?,
    downloadStatus: String?,
    isImage: Boolean,
    onDownload: () -> Unit,
    onSave: () -> Unit,
    onFullscreen: () -> Unit = {},
) {
    Column(modifier = Modifier.padding(bottom = 2.dp)) {
        if (isImage && filePath != null) {
            // Telegram-style: image drives bubble size
            // - Wide image → wide bubble, narrow height
            // - Square image → square bubble
            // - Tall image → narrower bubble, taller height (clamped to 75% screen height)
            val density = LocalDensity.current
            val config = LocalConfiguration.current
            val screenWidthPx = config.screenWidthDp * density.density
            val screenHeightPx = config.screenHeightDp * density.density

            // Max dimensions: 70% of screen width, 75% of screen height
            val maxWidthPx = screenWidthPx * 0.70f
            val maxHeightPx = screenHeightPx * 0.75f
            // Min size for tiny images (so they don't look like postage stamps)
            val minSidePx = 120f * density.density

            var intrinsicW by remember { mutableStateOf(0) }
            var intrinsicH by remember { mutableStateOf(0) }

            // Decode image dimensions off the main thread
            LaunchedEffect(filePath) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(filePath, opts)
                    intrinsicW = opts.outWidth
                    intrinsicH = opts.outHeight
                }
            }

            // Compute display size: fit within max bounds while preserving aspect ratio
            // and never going below min side
            val (displayWidthPx, displayHeightPx) = if (intrinsicW > 0 && intrinsicH > 0) {
                val imgRatio = intrinsicW.toFloat() / intrinsicH.toFloat()  // w/h
                val containerRatio = maxWidthPx / maxHeightPx  // 0.70/0.75 = 0.933

                if (imgRatio > containerRatio) {
                    // Image is wider than container (relatively) — constrain by width
                    val w = maxWidthPx
                    val h = w / imgRatio
                    // Make sure we hit at least minSide on height
                    val finalH = h.coerceAtLeast(minSidePx).coerceAtMost(maxHeightPx)
                    val finalW = if (finalH == maxHeightPx) maxHeightPx * imgRatio else w
                    Pair(finalW, finalH)
                } else {
                    // Image is taller than container (relatively) — constrain by height
                    val h = maxHeightPx
                    val w = h * imgRatio
                    // Make sure we hit at least minSide on width
                    val finalW = w.coerceAtLeast(minSidePx).coerceAtMost(maxWidthPx)
                    val finalH = if (finalW == maxWidthPx) maxWidthPx / imgRatio else h
                    Pair(finalW, finalH)
                }
            } else {
                // Fallback: square while dimensions load
                Pair(200f * density.density, 200f * density.density)
            }

            Box(
                modifier = Modifier
                    .width(with(density) { displayWidthPx.toDp() })
                    .height(with(density) { displayHeightPx.toDp() })
                    .clip(RoundedCornerShape(8.dp))
                    .background(C.border)
                    .clickable { onFullscreen() },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = "file://$filePath",
                    contentDescription = fileName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                // Subtle bottom fade for very tall images (hints there's more in fullscreen)
                if (intrinsicW > 0 && intrinsicH > 0) {
                    val ratio = intrinsicH.toFloat() / intrinsicW.toFloat()
                    if (ratio > 1.8f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .align(Alignment.BottomCenter)
                                .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(androidx.compose.ui.graphics.Color.Transparent, C.border.copy(alpha = 0.7f)),
                                    ),
                                ),
                        )
                    }
                }
            }
        } else if (isImage && (downloadStatus == "downloading" || downloadStatus == "decrypting")) {
            val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
            val shimmerOffset by shimmerTransition.animateFloat(
                initialValue = -1f,
                targetValue = 2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "shimmerOff",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(C.border, C.border.copy(alpha = 0.3f), C.border),
                            start = Offset(shimmerOffset * 300f, 0f),
                            end = Offset(shimmerOffset * 300f + 300f, 160f),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = C.accent.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            }
        } else if (isImage && downloadStatus == "error") {
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
                    Text(
                        stringResource(R.string.chat_tap_to_retry),
                        color = C.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.chat_sender_offline),
                        color = C.textSecondary,
                        fontSize = 10.sp,
                    )
                }
            }
        } else {
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
                            Text(
                                stringResource(R.string.chat_retry),
                                color = C.error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        else -> {
                            if (filePath != null) {
                                Text(
                                    stringResource(R.string.general_save),
                                    color = C.accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            } else {
                                Text(
                                    stringResource(R.string.chat_download),
                                    color = C.accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
