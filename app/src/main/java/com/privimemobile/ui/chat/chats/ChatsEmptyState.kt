package com.privimemobile.ui.chat.chats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.R
import com.privimemobile.ui.theme.C

/** Empty state shown when a tab folder has no conversations. */
@Composable
internal fun ChatsEmptyState(searchQuery: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (searchQuery.isNotBlank()) {
                Text(stringResource(R.string.chat_no_results, searchQuery), color = C.textSecondary, fontSize = 15.sp)
            } else {
                Text(stringResource(R.string.chats_empty), color = C.textSecondary, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.chats_tap_to_chat), color = C.textMuted, fontSize = 13.sp)
            }
        }
    }
}