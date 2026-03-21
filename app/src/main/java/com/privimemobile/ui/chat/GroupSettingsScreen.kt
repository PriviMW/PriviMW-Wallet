package com.privimemobile.ui.chat

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.chat.ChatService
import com.privimemobile.chat.db.entities.GroupEntity
import com.privimemobile.chat.db.entities.GroupMemberEntity
import com.privimemobile.ui.components.AvatarDisplay
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSettingsScreen(
    groupId: String,
    onBack: () -> Unit,
    onDeleteGroup: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var group by remember { mutableStateOf<GroupEntity?>(null) }
    val members by ChatService.db?.groupDao()?.observeMembers(groupId)
        ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }

    val state by ChatService.observeState().collectAsState(initial = null)
    val myHandle = state?.myHandle

    LaunchedEffect(groupId) {
        group = ChatService.db?.groupDao()?.findByGroupId(groupId)
        ChatService.groups.refreshGroupInfo(groupId)
        ChatService.groups.refreshGroupMembers(groupId)
        group = ChatService.db?.groupDao()?.findByGroupId(groupId)
    }

    val isCreator = group?.myRole == 2
    val isAdmin = group?.myRole == 1 || isCreator

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Group Info", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = C.card),
            )
        },
        containerColor = C.bg,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            // Group header
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier.size(80.dp).background(C.accent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Group, null, tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = group?.name ?: "",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${group?.memberCount ?: 0} members",
                        color = C.textSecondary,
                        fontSize = 14.sp,
                    )
                    if (group?.isPublic == true) {
                        Text("Public Group", color = C.accent, fontSize = 13.sp)
                    } else {
                        Text("Private Group", color = C.textMuted, fontSize = 13.sp)
                    }
                }
            }

            // Divider
            item { HorizontalDivider(color = C.card, thickness = 1.dp) }

            // Members section header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Members (${members.size})",
                        color = C.accent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Member list
            items(members, key = { it.id }) { member ->
                MemberRow(
                    member = member,
                    isMe = member.handle == myHandle,
                    callerIsCreator = isCreator,
                    callerIsAdmin = isAdmin,
                    onPromote = {
                        ChatService.groups.setMemberRole(groupId, member.handle, 1) { s, e ->
                            if (s) {
                                Toast.makeText(context, "${member.handle} promoted to admin", Toast.LENGTH_SHORT).show()
                                scope.launch { ChatService.groups.refreshGroupMembers(groupId) }
                            } else Toast.makeText(context, e ?: "Failed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDemote = {
                        ChatService.groups.setMemberRole(groupId, member.handle, 0) { s, e ->
                            if (s) {
                                Toast.makeText(context, "${member.handle} demoted to member", Toast.LENGTH_SHORT).show()
                                scope.launch { ChatService.groups.refreshGroupMembers(groupId) }
                            } else Toast.makeText(context, e ?: "Failed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onRemove = {
                        ChatService.groups.removeMember(groupId, member.handle, ban = false) { s, e ->
                            if (s) {
                                Toast.makeText(context, "${member.handle} removed", Toast.LENGTH_SHORT).show()
                                scope.launch { ChatService.groups.refreshGroupMembers(groupId) }
                            } else Toast.makeText(context, e ?: "Failed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onBan = {
                        ChatService.groups.removeMember(groupId, member.handle, ban = true) { s, e ->
                            if (s) {
                                Toast.makeText(context, "${member.handle} banned", Toast.LENGTH_SHORT).show()
                                scope.launch { ChatService.groups.refreshGroupMembers(groupId) }
                            } else Toast.makeText(context, e ?: "Failed", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }

            // Divider
            item { Spacer(Modifier.height(16.dp)); HorizontalDivider(color = C.card) }

            // Actions
            if (!isCreator) {
                item {
                    TextButton(
                        onClick = {
                            ChatService.groups.leaveGroup(groupId) { s, e ->
                                if (s) {
                                    Toast.makeText(context, "Left group", Toast.LENGTH_SHORT).show()
                                    onDeleteGroup()
                                } else Toast.makeText(context, e ?: "Failed", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Text("Leave Group", color = Color(0xFFEF5350), fontSize = 16.sp)
                    }
                }
            }

            if (isCreator) {
                item {
                    TextButton(
                        onClick = {
                            ChatService.groups.deleteGroup(groupId) { s, e ->
                                if (s) {
                                    Toast.makeText(context, "Group deleted", Toast.LENGTH_SHORT).show()
                                    onDeleteGroup()
                                } else Toast.makeText(context, e ?: "Failed", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Text("Delete Group", color = Color(0xFFEF5350), fontSize = 16.sp)
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun MemberRow(
    member: GroupMemberEntity,
    isMe: Boolean,
    callerIsCreator: Boolean,
    callerIsAdmin: Boolean,
    onPromote: () -> Unit,
    onDemote: () -> Unit,
    onRemove: () -> Unit,
    onBan: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (!isMe && (callerIsAdmin || callerIsCreator)) showMenu = true }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarDisplay(handle = member.handle, size = 44.dp)

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = member.displayName ?: "@${member.handle}",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isMe) {
                    Spacer(Modifier.width(6.dp))
                    Text("you", color = C.textMuted, fontSize = 12.sp)
                }
            }
            Text(
                text = "@${member.handle}",
                color = C.textSecondary,
                fontSize = 13.sp,
            )
        }

        // Role badge
        val roleText = when (member.role) {
            2 -> "Creator"
            1 -> "Admin"
            else -> null
        }
        if (roleText != null) {
            Text(
                text = roleText,
                color = if (member.role == 2) C.accent else Color(0xFF66BB6A),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(
                        if (member.role == 2) C.accent.copy(alpha = 0.15f) else Color(0xFF66BB6A).copy(alpha = 0.15f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        // Context menu for admin actions
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            if (callerIsCreator) {
                if (member.role == 0) {
                    DropdownMenuItem(
                        text = { Text("Promote to Admin") },
                        onClick = { showMenu = false; onPromote() },
                    )
                }
                if (member.role == 1) {
                    DropdownMenuItem(
                        text = { Text("Demote to Member") },
                        onClick = { showMenu = false; onDemote() },
                    )
                }
            }
            if (callerIsAdmin || callerIsCreator) {
                if (member.role < 2) { // Can't remove/ban creator
                    DropdownMenuItem(
                        text = { Text("Remove") },
                        onClick = { showMenu = false; onRemove() },
                    )
                    DropdownMenuItem(
                        text = { Text("Ban", color = Color(0xFFEF5350)) },
                        onClick = { showMenu = false; onBan() },
                    )
                }
            }
        }
    }
}
