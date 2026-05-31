package com.privimemobile.ui.chat.chats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.R
import com.privimemobile.ui.theme.C

/** Top bar with title, encrypted notice, and search icon. */
@Composable
internal fun ChatsTopBar(
    onSearch: () -> Unit,
    searchContent: @Composable () -> Unit,
) {
    Surface(color = C.bg) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.chat_title), color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.chats_encrypted_notice), color = C.textSecondary, fontSize = 10.sp)
                }
                IconButton(onClick = onSearch, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(R.string.chat_search),
                        tint = C.textSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            // Search bar slot
            Spacer(Modifier.height(8.dp))
            searchContent()
        }
    }
}