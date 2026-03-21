package com.privimemobile.ui.chat

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.chat.ChatService
import com.privimemobile.ui.theme.C

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    onBack: () -> Unit,
    onGroupCreated: () -> Unit,
) {
    val context = LocalContext.current
    var groupName by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(false) }
    var requireApproval by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Group", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = C.card),
            )
        },
        containerColor = C.bg,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Group icon placeholder
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(C.card, RoundedCornerShape(40.dp))
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Group, contentDescription = null, tint = C.accent, modifier = Modifier.size(40.dp))
            }

            // Group name
            OutlinedTextField(
                value = groupName,
                onValueChange = { if (it.length <= 32) groupName = it },
                label = { Text("Group Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = C.accent,
                    unfocusedBorderColor = C.textSecondary,
                    focusedLabelColor = C.accent,
                    unfocusedLabelColor = C.textSecondary,
                    cursorColor = C.accent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                ),
            )

            // Public/Private toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Public Group", color = Color.White, fontSize = 16.sp)
                    Text(
                        if (isPublic) "Anyone can find and join" else "Invite only",
                        color = C.textSecondary, fontSize = 13.sp,
                    )
                }
                Switch(
                    checked = isPublic,
                    onCheckedChange = { isPublic = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = C.accent),
                )
            }

            // Require approval (only for public groups)
            if (isPublic) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Require Approval", color = Color.White, fontSize = 16.sp)
                        Text("Admin must approve join requests", color = C.textSecondary, fontSize = 13.sp)
                    }
                    Switch(
                        checked = requireApproval,
                        onCheckedChange = { requireApproval = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = C.accent),
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Create button
            Button(
                onClick = {
                    if (groupName.isBlank()) {
                        Toast.makeText(context, "Enter a group name", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    creating = true
                    ChatService.groups.createGroup(
                        name = groupName.trim(),
                        isPublic = isPublic,
                        requireApproval = requireApproval,
                    ) { success, _, error ->
                        creating = false
                        if (success) {
                            Toast.makeText(context, "Transaction submitted. Group will appear when confirmed.", Toast.LENGTH_LONG).show()
                            onGroupCreated()
                        } else {
                            Toast.makeText(context, error ?: "Failed to create group", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = groupName.isNotBlank() && !creating,
                colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (creating) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Text("Create Group", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
