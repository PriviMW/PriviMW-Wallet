package com.privimemobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.ui.theme.C

@Composable
fun NewChatScreen(onStartChat: (String) -> Unit, onBack: () -> Unit) {
    var handle by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg)
            .padding(24.dp),
    ) {
        TextButton(onClick = onBack) {
            Text("< Back", color = C.textSecondary)
        }
        Spacer(Modifier.height(16.dp))
        Text("New Chat", color = C.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Enter a handle to start a conversation", color = C.textSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = handle,
            onValueChange = { handle = it.lowercase().trim(); error = null },
            label = { Text("Handle") },
            prefix = { Text("@", color = C.accent) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = C.accent,
                unfocusedBorderColor = C.border,
                focusedLabelColor = C.accent,
                cursorColor = C.accent,
            ),
        )

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error!!, color = C.error, fontSize = 13.sp)
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                if (handle.length < 3) {
                    error = "Handle must be at least 3 characters"
                } else {
                    searching = true
                    error = null
                    // TODO: resolve handle via PriviMe contract
                    // For now, navigate directly
                    searching = false
                    onStartChat(handle)
                }
            },
            enabled = !searching,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = C.accent),
        ) {
            if (searching) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = C.textDark, strokeWidth = 2.dp)
            } else {
                Text("Start Chat", color = C.textDark, fontWeight = FontWeight.Bold)
            }
        }
    }
}
