package com.privimemobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

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

@Composable
fun PriviMWTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content,
    )
}
