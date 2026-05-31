package com.privimemobile.ui.chat.message

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import com.privimemobile.ui.theme.C

@Composable
fun TickIndicator(read: Boolean, delivered: Boolean) {
    val tickText = if (read || delivered) "\u2713\u2713" else "\u2713"
    val tickColor = if (read) C.accent else C.textSecondary
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 800f),
        label = "tickScale",
    )
    Text(
        tickText,
        color = tickColor,
        fontSize = 10.sp,
        modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale),
    )
}
