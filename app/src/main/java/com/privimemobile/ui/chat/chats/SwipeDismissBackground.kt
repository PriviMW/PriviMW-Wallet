package com.privimemobile.ui.chat.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.privimemobile.R
import com.privimemobile.ui.theme.C

/** Background content for swipe-to-dismiss showing delete (left) or archive (right) icons. */
@Composable
internal fun SwipeDismissBackground(
    progress: Float,
    direction: androidx.compose.material3.SwipeToDismissBoxValue,
) {
    val bgColor = when (direction) {
        androidx.compose.material3.SwipeToDismissBoxValue.EndToStart -> C.error.copy(alpha = (progress * 2.5f).coerceIn(0f, 1f))
        androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd -> C.accent.copy(alpha = (progress * 2.5f).coerceIn(0f, 1f))
        else -> Color.Transparent
    }
    val iconAlpha = (progress * 3f).coerceIn(0f, 1f)
    val iconAlignment = when (direction) {
        androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
        else -> Alignment.CenterEnd
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(horizontal = 24.dp),
        contentAlignment = iconAlignment,
    ) {
        if (direction == androidx.compose.material3.SwipeToDismissBoxValue.EndToStart) {
            Icon(Icons.Default.Delete, stringResource(R.string.chats_cd_delete), tint = Color.White.copy(alpha = iconAlpha))
        } else if (direction == androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd) {
            Icon(Icons.Default.Archive, stringResource(R.string.chats_cd_archive), tint = Color.White.copy(alpha = iconAlpha))
        }
    }
}