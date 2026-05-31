package com.privimemobile.ui.chat.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.privimemobile.ui.theme.C

/** Full-screen loading spinner while chat list prerequisites are not ready. */
@Composable
internal fun ChatsLoadingPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(C.bg),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = C.accent)
    }
}
