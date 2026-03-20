package com.privimemobile.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.ui.theme.C
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * Result from avatar selection: compressed bytes + SHA-256 hash.
 */
data class AvatarResult(
    val bytes: ByteArray,
    val hashHex: String,
    val bitmap: Bitmap,
)

/**
 * Reusable avatar picker with circle crop preview.
 * Used in Settings (change avatar) and RegisterScreen (initial avatar).
 *
 * @param currentAvatarPath Path to current avatar file (null = no avatar)
 * @param size Display size of the avatar circle
 * @param onAvatarSelected Called with compressed bytes + hash when user confirms
 */
@Composable
fun AvatarPicker(
    currentAvatarPath: String? = null,
    initialLetter: String = "?",
    size: Dp = 100.dp,
    onAvatarSelected: (AvatarResult) -> Unit,
) {
    val context = LocalContext.current
    var showPreview by remember { mutableStateOf(false) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewResult by remember { mutableStateOf<AvatarResult?>(null) }

    // Current avatar bitmap (from file)
    val currentBitmap = remember(currentAvatarPath) {
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
                    // Center-crop to square
                    val minDim = minOf(bmp.width, bmp.height)
                    val x = (bmp.width - minDim) / 2
                    val y = (bmp.height - minDim) / 2
                    val cropped = Bitmap.createBitmap(bmp, x, y, minDim, minDim)

                    // Scale to 128x128
                    val scaled = Bitmap.createScaledBitmap(cropped, 128, 128, true)

                    // Compress to WebP
                    val baos = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.WEBP, 80, baos)
                    val bytes = baos.toByteArray()

                    // SHA-256 hash
                    val digest = MessageDigest.getInstance("SHA-256")
                    val hashBytes = digest.digest(bytes)
                    val hashHex = hashBytes.joinToString("") { "%02x".format(it) }

                    previewBitmap = scaled
                    previewResult = AvatarResult(bytes, hashHex, scaled)
                    showPreview = true
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
        }
        // Camera overlay hint
        Box(
            modifier = Modifier.fillMaxSize().clip(CircleShape)
                .background(C.bg.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("\uD83D\uDCF7", fontSize = (size.value * 0.25f).sp)  // 📷
        }
    }

    // Circle crop preview dialog
    if (showPreview && previewBitmap != null && previewResult != null) {
        AlertDialog(
            onDismissRequest = { showPreview = false },
            containerColor = C.card,
            title = {
                Text("Profile Picture", color = C.text, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Circle preview
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(CircleShape)
                            .border(3.dp, C.accent, CircleShape),
                    ) {
                        Image(
                            bitmap = previewBitmap!!.asImageBitmap(),
                            contentDescription = "Preview",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("This is how your profile picture will look", color = C.textSecondary, fontSize = 13.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAvatarSelected(previewResult!!)
                        showPreview = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                ) {
                    Text("Confirm", color = C.textDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPreview = false
                    // Re-open picker
                    imagePicker.launch("image/*")
                }) {
                    Text("Choose Another", color = C.textSecondary)
                }
            },
        )
    }
}
