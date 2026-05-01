package com.privimemobile.ui.chat

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.R
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
    var description by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(true) }
    var requireApproval by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chat_new_group), color = C.text) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.general_back), tint = Color.White)
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Group icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(C.accent, CircleShape)
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Group, null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
            Text(
                "Set group icon later in Group Info",
                color = C.textMuted, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(Modifier.height(4.dp))

            // Group name
            OutlinedTextField(
                value = groupName,
                onValueChange = { if (it.length <= 32) groupName = it },
                label = { Text("Group Name *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("${groupName.length}/32", color = C.textMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = C.accent, unfocusedBorderColor = C.textSecondary,
                    focusedLabelColor = C.accent, unfocusedLabelColor = C.textSecondary,
                    cursorColor = C.accent, focusedTextColor = C.text, unfocusedTextColor = C.text,
                ),
            )

            HorizontalDivider(color = C.border.copy(alpha = 0.3f))

            // Group type selector — card-style
            Text("Group Type", color = C.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

            // Public option
            Surface(
                onClick = { isPublic = true },
                shape = RoundedCornerShape(12.dp),
                color = if (isPublic) C.accent.copy(alpha = 0.12f) else C.card,
                border = if (isPublic) androidx.compose.foundation.BorderStroke(1.5.dp, C.accent) else null,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Public, null, tint = if (isPublic) C.accent else C.textSecondary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Public", color = C.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("Anyone can find and join this group", color = C.textSecondary, fontSize = 12.sp)
                    }
                    RadioButton(selected = isPublic, onClick = { isPublic = true }, colors = RadioButtonDefaults.colors(selectedColor = C.accent))
                }
            }

            // Private option
            Surface(
                onClick = { isPublic = false },
                shape = RoundedCornerShape(12.dp),
                color = if (!isPublic) C.accent.copy(alpha = 0.12f) else C.card,
                border = if (!isPublic) androidx.compose.foundation.BorderStroke(1.5.dp, C.accent) else null,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Lock, null, tint = if (!isPublic) C.accent else C.textSecondary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Private", color = C.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("Only people with an invite can join", color = C.textSecondary, fontSize = 12.sp)
                    }
                    RadioButton(selected = !isPublic, onClick = { isPublic = false }, colors = RadioButtonDefaults.colors(selectedColor = C.accent))
                }
            }

            if (!isPublic) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1B3A4B),
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Lock, null, tint = C.accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Private groups are protected by a password. When you invite someone, the password is shared automatically via encrypted SBBS.",
                            color = C.textSecondary, fontSize = 12.sp, lineHeight = 16.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Create button
            Button(
                onClick = {
                    if (groupName.isBlank()) {
                        Toast.makeText(context, R.string.create_group_enter_name, Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    creating = true
                    val password = if (!isPublic) java.util.UUID.randomUUID().toString().replace("-", "") else null
                    val desc = description.trim().ifEmpty { null }
                    // Check if wallet is syncing — warn user TX may be slow
                    val syncState = com.privimemobile.wallet.WalletEventBus.syncProgress.value
                    val isSyncing = syncState.total > 0 && syncState.done < syncState.total
                    if (isSyncing) {
                        Toast.makeText(context, R.string.create_group_syncing_warning, Toast.LENGTH_LONG).show()
                    }
                    ChatService.groups.createGroup(
                        name = groupName.trim(),
                        isPublic = isPublic,
                        requireApproval = requireApproval,
                        joinPassword = password,
                        description = desc,
                    ) { success, _, error ->
                        creating = false
                        if (success) {
                            Toast.makeText(context, R.string.create_group_tx_submitted, Toast.LENGTH_LONG).show()
                            onGroupCreated()
                        } else {
                            Toast.makeText(context, error ?: context.getString(R.string.create_group_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = groupName.isNotBlank() && !creating,
                colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (creating) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(if (isPublic) Icons.Default.Public else Icons.Default.Lock, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (isPublic) R.string.group_create_public else R.string.group_create_private), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
