package com.privimemobile.ui.theme

import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val DarkColorScheme = darkColorScheme(
    primary = C.accent,
    onPrimary = C.textDark,
    secondary = C.incoming,
    tertiary = C.outgoing,
    background = C.bg,
    surface = C.card,
    surfaceVariant = C.cardAlt,
    onBackground = C.text,
    onSurface = C.text,
    onSurfaceVariant = C.textSecondary,
    error = C.error,
    outline = C.border,
)

@OptIn(ExperimentalMaterial3Api::class)
private val VisibleRipple = RippleConfiguration(
    color = C.accent,
    rippleAlpha = RippleAlpha(
        pressedAlpha = 0.24f,
        focusedAlpha = 0.24f,
        draggedAlpha = 0.16f,
        hoveredAlpha = 0.12f,
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriviMWTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
    ) {
        CompositionLocalProvider(
            LocalRippleConfiguration provides VisibleRipple,
            content = content,
        )
    }
}
