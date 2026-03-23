package com.privimemobile.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.ui.theme.C
import java.io.File
import kotlin.math.abs

/** Deterministic color from handle/name string. */
private val avatarColors = listOf(
    Color(0xFF5C6BC0), Color(0xFF42A5F5), Color(0xFF26A69A), Color(0xFF66BB6A),
    Color(0xFFFF7043), Color(0xFFAB47BC), Color(0xFFEC407A), Color(0xFF78909C),
    Color(0xFFFFCA28), Color(0xFF8D6E63), Color(0xFF26C6DA), Color(0xFFD4E157),
)

/**
 * Display a user's avatar — loads from local cache, falls back to colored letter circle.
 *
 * @param handle The user's @handle (used for file lookup + letter fallback)
 * @param displayName Display name (used for letter if available)
 * @param size Circle size
 * @param isMe If true, loads from my_avatar.webp instead of avatars/{handle}.webp
 */
@Composable
fun AvatarDisplay(
    handle: String,
    displayName: String? = null,
    size: Dp = 44.dp,
    isMe: Boolean = false,
) {
    val context = LocalContext.current
    val avatarBitmap = remember(handle, isMe) {
        try {
            val file = if (isMe) {
                File(context.filesDir, "my_avatar.webp")
            } else {
                File(context.filesDir, "avatars/$handle.webp")
            }
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        } catch (_: Exception) { null }
    }

    val isDeletedAccount = displayName == "Deleted Account"

    Box(
        modifier = Modifier.size(size).clip(CircleShape)
            .background(
                if (isDeletedAccount) Color(0xFF555555)
                else if (avatarBitmap != null) Color.Transparent
                else avatarColors[abs(handle.hashCode()) % avatarColors.size]
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isDeletedAccount) {
            // Grey ghost icon for deleted accounts
            Text("\uD83D\uDEAB", fontSize = (size.value * 0.4f).sp) // 🚫
        } else if (avatarBitmap != null) {
            var appeared by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { appeared = true }
            val alpha by animateFloatAsState(
                targetValue = if (appeared) 1f else 0f,
                animationSpec = tween(300),
                label = "avatarFade",
            )
            Image(
                bitmap = avatarBitmap.asImageBitmap(),
                contentDescription = "Avatar",
                modifier = Modifier.fillMaxSize().clip(CircleShape).graphicsLayer { this.alpha = alpha },
                contentScale = ContentScale.Crop,
            )
        } else {
            val letter = (displayName?.firstOrNull() ?: handle.firstOrNull() ?: '?').uppercase()
            Text(
                letter,
                color = Color.White,
                fontSize = (size.value * 0.4f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
