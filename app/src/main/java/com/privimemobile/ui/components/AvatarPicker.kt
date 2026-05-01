package com.privimemobile.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.privimemobile.R
import com.privimemobile.ui.theme.C
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

data class AvatarResult(
    val bytes: ByteArray,
    val hashHex: String,
    val bitmap: Bitmap,        // 128x128 for storage
    val previewBitmap: Bitmap, // full-res for preview
)

@Composable
fun AvatarPicker(
    currentAvatarPath: String? = null,
    initialLetter: String = "?",
    size: Dp = 100.dp,
    cacheVersion: Int = 0,
    onAvatarSelected: (AvatarResult) -> Unit,
) {
    val context = LocalContext.current
    var showCropScreen by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    var rawBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var croppedResult by remember { mutableStateOf<AvatarResult?>(null) }

    val currentBitmap = remember(currentAvatarPath, cacheVersion) {
        if (currentAvatarPath != null) {
            try { BitmapFactory.decodeFile(currentAvatarPath) } catch (_: Exception) { null }
        } else null
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val input = context.contentResolver.openInputStream(uri)
                val bmp = BitmapFactory.decodeStream(input)
                input?.close()
                if (bmp != null) {
                    // Apply EXIF rotation
                    val rotated = try {
                        val exifInput = context.contentResolver.openInputStream(uri)
                        val exif = android.media.ExifInterface(exifInput!!)
                        exifInput.close()
                        val orientation = exif.getAttributeInt(
                            android.media.ExifInterface.TAG_ORIENTATION,
                            android.media.ExifInterface.ORIENTATION_NORMAL
                        )
                        val matrix = android.graphics.Matrix()
                        when (orientation) {
                            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                            android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
                            android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
                            else -> null
                        }
                        if (matrix != null) {
                            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                        } else bmp
                    } catch (_: Exception) { bmp }
                    rawBitmap = rotated
                    showCropScreen = true
                }
            } catch (_: Exception) {}
        }
    }

    // Avatar circle (tap to pick)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(C.card)
            .border(2.dp, C.accent.copy(alpha = 0.5f), CircleShape)
            .clickable { imagePicker.launch("image/*") },
        contentAlignment = Alignment.Center,
    ) {
        if (currentBitmap != null) {
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = "Avatar",
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                initialLetter.uppercase().take(1),
                color = C.accent,
                fontSize = (size.value * 0.4f).sp,
                fontWeight = FontWeight.Bold,
            )
            Box(
                modifier = Modifier.fillMaxSize().clip(CircleShape)
                    .background(C.bg.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("\uD83D\uDCF7", fontSize = (size.value * 0.25f).sp)
            }
        }
    }

    // Crop screen
    if (showCropScreen && rawBitmap != null) {
        AvatarCropDialog(
            bitmap = rawBitmap!!,
            onConfirm = { result ->
                showCropScreen = false
                croppedResult = result
                showPreview = true
            },
            onCancel = { showCropScreen = false; rawBitmap = null },
            onChooseAnother = {
                showCropScreen = false; rawBitmap = null
                imagePicker.launch("image/*")
            },
        )
    }

    // Preview confirmation dialog
    if (showPreview && croppedResult != null) {
        AlertDialog(
            onDismissRequest = { showPreview = false },
            containerColor = C.card,
            title = { Text(stringResource(R.string.avatar_confirm_title), color = C.text, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier.size(180.dp).clip(CircleShape)
                            .border(3.dp, C.accent, CircleShape),
                    ) {
                        Image(
                            bitmap = croppedResult!!.previewBitmap.asImageBitmap(),
                            contentDescription = "Preview",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.avatar_confirm_subtitle), color = C.textSecondary, fontSize = 13.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPreview = false
                        onAvatarSelected(croppedResult!!)
                        croppedResult = null; rawBitmap = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                ) { Text(stringResource(R.string.avatar_set_picture), color = C.textDark, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPreview = false
                    // Go back to crop
                    showCropScreen = true
                }) { Text(stringResource(R.string.avatar_adjust), color = C.textSecondary) }
            },
        )
    }
}

@Composable
private fun AvatarCropDialog(
    bitmap: Bitmap,
    onConfirm: (AvatarResult) -> Unit,
    onCancel: () -> Unit,
    onChooseAnother: () -> Unit,
) {
    // Scale so image fills the crop circle area
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Reset on new image
    LaunchedEffect(bitmap) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().background(Color.Black).statusBarsPadding(),
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.general_cancel), color = Color.White, fontSize = 16.sp)
                }
                Text(stringResource(R.string.avatar_move_and_scale), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onChooseAnother) {
                    Text(stringResource(R.string.general_change), color = C.accent, fontSize = 16.sp)
                }
            }

            // Crop area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { containerSize = it },
                contentAlignment = Alignment.Center,
            ) {
                if (containerSize != IntSize.Zero) {
                    val circleR = minOf(containerSize.width, containerSize.height) * 0.45f

                    // Image with zoom/pan
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 8f)
                                    offsetX += pan.x
                                    offsetY += pan.y
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.avatar_crop),
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY,
                                ),
                            contentScale = ContentScale.Fit,
                        )
                    }

                    // Circle overlay (dark outside, clear inside)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val r = circleR

                        // Dark overlay with circle cutout
                        val circlePath = Path().apply {
                            addOval(androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r))
                        }
                        clipPath(circlePath, clipOp = ClipOp.Difference) {
                            drawRect(Color.Black.copy(alpha = 0.6f))
                        }

                        // Circle border
                        drawCircle(
                            color = Color.White.copy(alpha = 0.8f),
                            radius = r,
                            center = androidx.compose.ui.geometry.Offset(cx, cy),
                            style = Stroke(width = 2f),
                        )
                    }
                }
            }

            // Bottom bar — extra bottom padding for devices with gesture nav / tall displays
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Button(
                    onClick = {
                        if (containerSize != IntSize.Zero) {
                            val circleR = minOf(containerSize.width, containerSize.height) * 0.45f
                            val cx = containerSize.width / 2f
                            val cy = containerSize.height / 2f

                            val bmpW = bitmap.width.toFloat()
                            val bmpH = bitmap.height.toFloat()
                            // ContentScale.Fit: scale to fit (use min)
                            val fitScaleX = containerSize.width / bmpW
                            val fitScaleY = containerSize.height / bmpH
                            val fitScale = minOf(fitScaleX, fitScaleY)
                            // Total scale = fitScale * user zoom
                            val totalScale = fitScale * scale
                            val imgCenterX = cx + offsetX
                            val imgCenterY = cy + offsetY

                            // Circle bounds in bitmap coordinates
                            val bmpLeft = ((cx - circleR - imgCenterX) / totalScale + bmpW / 2).toInt().coerceIn(0, bitmap.width)
                            val bmpTop = ((cy - circleR - imgCenterY) / totalScale + bmpH / 2).toInt().coerceIn(0, bitmap.height)
                            val bmpRight = ((cx + circleR - imgCenterX) / totalScale + bmpW / 2).toInt().coerceIn(0, bitmap.width)
                            val bmpBottom = ((cy + circleR - imgCenterY) / totalScale + bmpH / 2).toInt().coerceIn(0, bitmap.height)

                            val cropW = (bmpRight - bmpLeft).coerceAtLeast(1)
                            val cropH = (bmpBottom - bmpTop).coerceAtLeast(1)
                            val cropSize = minOf(cropW, cropH)

                            val cropped = Bitmap.createBitmap(bitmap,
                                bmpLeft.coerceIn(0, bitmap.width - cropSize),
                                bmpTop.coerceIn(0, bitmap.height - cropSize),
                                cropSize.coerceIn(1, minOf(bitmap.width - bmpLeft.coerceAtLeast(0), bitmap.height - bmpTop.coerceAtLeast(0))),
                                cropSize.coerceIn(1, minOf(bitmap.width - bmpLeft.coerceAtLeast(0), bitmap.height - bmpTop.coerceAtLeast(0))),
                            )
                            // Keep full-res for preview, downscale for storage
                            val previewBmp = if (cropped.width > 512) Bitmap.createScaledBitmap(cropped, 512, 512, true) else cropped
                            val scaled = Bitmap.createScaledBitmap(cropped, 128, 128, true)

                            val baos = ByteArrayOutputStream()
                            scaled.compress(Bitmap.CompressFormat.WEBP, 80, baos)
                            val bytes = baos.toByteArray()
                            val hashHex = MessageDigest.getInstance("SHA-256")
                                .digest(bytes).joinToString("") { "%02x".format(it) }

                            onConfirm(AvatarResult(bytes, hashHex, scaled, previewBmp))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.width(200.dp).height(48.dp),
                ) {
                    Text(stringResource(R.string.general_done), color = C.textDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
