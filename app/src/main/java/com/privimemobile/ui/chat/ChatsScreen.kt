package com.privimemobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.ui.theme.C

@Composable
fun ChatsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg)
            .padding(16.dp),
    ) {
        Text("Chats", color = C.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "End-to-end encrypted messaging on Beam",
            color = C.textSecondary,
            fontSize = 14.sp,
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("No conversations yet", color = C.textSecondary, fontSize = 14.sp)
        }
    }
}
