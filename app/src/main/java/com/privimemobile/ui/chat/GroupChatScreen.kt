package com.privimemobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.chat.ChatService
import com.privimemobile.chat.db.entities.GroupEntity
import com.privimemobile.ui.theme.C

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    groupId: String,
    onBack: () -> Unit,
    onGroupSettings: () -> Unit,
) {
    var group by remember { mutableStateOf<GroupEntity?>(null) }

    LaunchedEffect(groupId) {
        group = ChatService.db?.groupDao()?.findByGroupId(groupId)
        if (group == null) {
            ChatService.groups.refreshGroupInfo(groupId)
            group = ChatService.db?.groupDao()?.findByGroupId(groupId)
        }
        ChatService.groups.refreshGroupMembers(groupId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(onClick = onGroupSettings),
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp).background(C.accent, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Group, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = group?.name ?: "Group",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${group?.memberCount ?: 0} members",
                                color = C.textSecondary,
                                fontSize = 13.sp,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = onGroupSettings) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Settings", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = C.surface),
            )
        },
        containerColor = C.bg,
    ) { padding ->
        // Placeholder — full group messaging UI will reuse ChatScreen patterns
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Group, null, tint = C.textSecondary, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    text = group?.name ?: "Loading...",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Group messaging coming soon",
                    color = C.textSecondary,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Contract deployed. SBBS messaging in progress.",
                    color = C.textMuted,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

private fun Modifier.clickable(onClick: () -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable { onClick() })
