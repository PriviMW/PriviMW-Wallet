package com.privimemobile.ui.chat

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.asImageBitmap
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
    onContactInfo: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Reactive group state — auto-updates when DB changes
    val allGroups by ChatService.db?.groupDao()?.observeAll()
        ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val group = allGroups.firstOrNull { it.groupId == groupId }

    val members by ChatService.db?.groupDao()?.observeMembers(groupId)
        ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }

    val state by ChatService.observeState().collectAsState(initial = null)
    val myHandle = state?.myHandle

    LaunchedEffect(groupId) {
        if (allGroups.none { it.groupId == groupId }) {
            ChatService.groups.refreshGroupInfo(groupId)
        }
        ChatService.groups.refreshGroupMembers(groupId)
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
                var groupAvatarVersion by remember { mutableStateOf(0) }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val groupAvatarPath = remember(groupId, groupAvatarVersion) {
                        val f = java.io.File(context.filesDir, "group_avatars/$groupId.webp")
                        if (f.exists()) f.absolutePath else null
                    }

                    if (isAdmin) {
                        // Admin can change group avatar
                        com.privimemobile.ui.components.AvatarPicker(
                            currentAvatarPath = groupAvatarPath,
                            initialLetter = (group?.name ?: "G").take(1).uppercase(),
                            size = 80.dp,
                            cacheVersion = groupAvatarVersion,
                        ) { result ->
                            // Save locally
                            val dir = java.io.File(context.filesDir, "group_avatars").also { it.mkdirs() }
                            java.io.File(dir, "$groupId.webp").writeBytes(result.bytes)
                            groupAvatarVersion++
                            Toast.makeText(context, "Group picture updated", Toast.LENGTH_SHORT).show()
                            // Broadcast to all members via SBBS
                            scope.launch {
                                val ts = System.currentTimeMillis() / 1000
                                // Insert local service message
                                val convId = ChatService.groups.getOrCreateGroupConversation(groupId, group?.name ?: "Group")
                                val dedupKey = "$ts:info_update:avatar:$groupId".hashCode().toString(16)
                                ChatService.db?.messageDao()?.insert(com.privimemobile.chat.db.entities.MessageEntity(
                                    conversationId = convId, timestamp = ts,
                                    senderHandle = myHandle, text = "You updated group picture",
                                    type = "group_service", sent = true, sbbsDedupKey = dedupKey,
                                ))
                                ChatService.db?.groupDao()?.updateLastMessage(groupId, ts, "You updated group picture")
                                // Broadcast
                                val base64 = android.util.Base64.encodeToString(result.bytes, android.util.Base64.NO_WRAP)
                                val payload = mapOf(
                                    "v" to 1, "t" to "group_info_update",
                                    "ts" to ts,
                                    "avatar" to base64,
                                    "avatar_hash" to result.hashHex,
                                )
                                ChatService.groups.sendGroupPayload(groupId, payload)
                                ChatService.db?.groupDao()?.updateAvatarHash(groupId, result.hashHex)
                            }
                        }
                    } else {
                        // Non-admin: show group avatar or default icon
                        if (groupAvatarPath != null) {
                            val bmp = remember(groupAvatarPath, groupAvatarVersion) {
                                try { android.graphics.BitmapFactory.decodeFile(groupAvatarPath) } catch (_: Exception) { null }
                            }
                            if (bmp != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Group avatar",
                                    modifier = Modifier.size(80.dp).clip(CircleShape),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                )
                            } else {
                                Box(modifier = Modifier.size(80.dp).background(C.accent, CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Group, null, tint = Color.White, modifier = Modifier.size(40.dp))
                                }
                            }
                        } else {
                            Box(modifier = Modifier.size(80.dp).background(C.accent, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Group, null, tint = Color.White, modifier = Modifier.size(40.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = group?.name ?: "",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${members.size} members",
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

            // Description section (admin can edit)
            item {
                var showDescDialog by remember { mutableStateOf(false) }
                val desc = group?.description
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isAdmin) Modifier.clickable { showDescDialog = true } else Modifier)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Info, null, tint = C.textSecondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Description", color = C.textSecondary, fontSize = 12.sp)
                        Text(
                            desc?.ifEmpty { "No description" } ?: "No description",
                            color = if (desc.isNullOrEmpty()) C.textMuted else Color.White,
                            fontSize = 14.sp,
                        )
                    }
                    if (isAdmin) {
                        Icon(Icons.Default.Edit, null, tint = C.textMuted, modifier = Modifier.size(16.dp))
                    }
                }

                if (showDescDialog) {
                    var newDesc by remember { mutableStateOf(desc ?: "") }
                    AlertDialog(
                        onDismissRequest = { showDescDialog = false },
                        containerColor = C.card,
                        title = { Text("Edit Description", color = Color.White) },
                        text = {
                            OutlinedTextField(
                                value = newDesc,
                                onValueChange = { if (it.length <= 200) newDesc = it },
                                placeholder = { Text("Group description...", color = C.textMuted) },
                                maxLines = 4,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = C.accent, unfocusedBorderColor = C.border,
                                    cursorColor = C.accent, focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                ),
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val descToSave = newDesc.trim()
                                // Save description locally (contract only stores hash)
                                scope.launch {
                                    ChatService.db?.groupDao()?.updateDescription(groupId, descToSave)
                                    // Insert local service message (sendGroupPayload excludes self)
                                    val ts = System.currentTimeMillis() / 1000
                                    val convId = ChatService.groups.getOrCreateGroupConversation(groupId, group?.name ?: "Group")
                                    val dedupKey = "$ts:info_update:desc:$groupId".hashCode().toString(16)
                                    ChatService.db?.messageDao()?.insert(com.privimemobile.chat.db.entities.MessageEntity(
                                        conversationId = convId, timestamp = ts,
                                        senderHandle = myHandle, text = "You updated group description",
                                        type = "group_service", sent = true, sbbsDedupKey = dedupKey,
                                    ))
                                    ChatService.db?.groupDao()?.updateLastMessage(groupId, ts, "You updated group description")
                                    // Broadcast to all members
                                    val infoPayload = mapOf(
                                        "v" to 1, "t" to "group_info_update",
                                        "ts" to ts,
                                        "description" to descToSave,
                                    )
                                    ChatService.groups.sendGroupPayload(groupId, infoPayload)
                                }
                                Toast.makeText(context, "Description updated", Toast.LENGTH_SHORT).show()
                                showDescDialog = false
                            }) { Text("Save", color = C.accent) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDescDialog = false }) { Text("Cancel", color = C.textSecondary) }
                        },
                    )
                }
            }

            item { HorizontalDivider(color = C.card, thickness = 1.dp) }

            // Members section header + Add Member button
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
                    if (isAdmin) {
                        var showAddDialog by remember { mutableStateOf(false) }
                        TextButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.PersonAdd, null, tint = C.accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Add", color = C.accent, fontSize = 13.sp)
                        }

                        if (showAddDialog) {
                            var addQuery by remember { mutableStateOf("") }
                            var addLoading by remember { mutableStateOf(false) }
                            var searching by remember { mutableStateOf(false) }
                            var searchResults by remember { mutableStateOf<List<com.privimemobile.chat.db.entities.ContactEntity>>(emptyList()) }
                            var searchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
                            val memberHandles = remember(members) { members.map { it.handle }.toSet() }

                            AlertDialog(
                                onDismissRequest = { showAddDialog = false; searchJob?.cancel() },
                                containerColor = C.card,
                                title = { Text("Add Member", color = Color.White) },
                                text = {
                                    Column(modifier = Modifier.heightIn(max = 350.dp)) {
                                        OutlinedTextField(
                                            value = addQuery,
                                            onValueChange = { input ->
                                                addQuery = input.lowercase().replace(Regex("[^a-z0-9_@]"), "")
                                                searchJob?.cancel()
                                                val q = addQuery.removePrefix("@").trim()
                                                if (q.isEmpty()) {
                                                    searchResults = emptyList()
                                                    searching = false
                                                    return@OutlinedTextField
                                                }
                                                searching = true
                                                searchJob = scope.launch {
                                                    kotlinx.coroutines.delay(300)
                                                    val results = ChatService.contacts.searchOnChain(q)
                                                    searchResults = results
                                                    searching = false
                                                }
                                            },
                                            placeholder = { Text("Search handle...", color = C.textMuted) },
                                            singleLine = true,
                                            enabled = !addLoading,
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = C.accent, unfocusedBorderColor = C.border,
                                                cursorColor = C.accent, focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                                            ),
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        if (searching) {
                                            Text("Searching...", color = C.textMuted, fontSize = 13.sp)
                                        } else if (addQuery.removePrefix("@").trim().isNotEmpty() && searchResults.isEmpty()) {
                                            Text("No handles found", color = C.textSecondary, fontSize = 13.sp)
                                        }
                                        // Results list
                                        searchResults.forEach { contact ->
                                            val isMember = contact.handle in memberHandles
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .then(if (!isMember && !addLoading) Modifier.clickable {
                                                        addLoading = true
                                                        scope.launch {
                                                            val sent = ChatService.groups.sendGroupInvite(groupId, contact.handle)
                                                            addLoading = false
                                                            if (sent) {
                                                                Toast.makeText(context, "Invite sent to @${contact.handle}", Toast.LENGTH_SHORT).show()
                                                                showAddDialog = false
                                                            } else {
                                                                Toast.makeText(context, "Failed to send invite", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    } else Modifier)
                                                    .padding(vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                AvatarDisplay(handle = contact.handle, size = 36.dp)
                                                Spacer(Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        contact.displayName?.ifEmpty { null } ?: "@${contact.handle}",
                                                        color = if (isMember) C.textMuted else Color.White,
                                                        fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                                    )
                                                    Text("@${contact.handle}", color = C.textSecondary, fontSize = 12.sp)
                                                }
                                                if (isMember) {
                                                    Text("Member", color = C.textMuted, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                },
                                confirmButton = {},
                                dismissButton = {
                                    TextButton(onClick = { showAddDialog = false; searchJob?.cancel() }) { Text("Close", color = C.textSecondary) }
                                },
                            )
                        }
                    }
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
                            if (s) Toast.makeText(context, "TX submitted. @${member.handle} will be promoted when confirmed.", Toast.LENGTH_LONG).show()
                            else Toast.makeText(context, e ?: "Failed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDemote = {
                        ChatService.groups.setMemberRole(groupId, member.handle, 0) { s, e ->
                            if (s) Toast.makeText(context, "TX submitted. @${member.handle} will be demoted when confirmed.", Toast.LENGTH_LONG).show()
                            else Toast.makeText(context, e ?: "Failed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onRemove = {
                        ChatService.groups.removeMember(groupId, member.handle, ban = false) { s, e ->
                            if (s) Toast.makeText(context, "TX submitted. @${member.handle} will be removed when confirmed.", Toast.LENGTH_LONG).show()
                            else Toast.makeText(context, e ?: "Failed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onBan = {
                        ChatService.groups.removeMember(groupId, member.handle, ban = true) { s, e ->
                            if (s) Toast.makeText(context, "TX submitted. @${member.handle} will be banned when confirmed.", Toast.LENGTH_LONG).show()
                            else Toast.makeText(context, e ?: "Failed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onTap = { onContactInfo(member.handle) },
                )
            }

            // Divider
            item { Spacer(Modifier.height(16.dp)); HorizontalDivider(color = C.card) }

            // ── Admin settings ──
            if (isAdmin) {
                item {
                    Text(
                        "Admin Settings",
                        color = C.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }


            }

            // Transfer Ownership (creator only)
            if (isCreator) {
                item {
                    var showTransferDialog by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTransferDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.SwapHoriz, null, tint = Color(0xFFFFA726), modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(14.dp))
                        Text("Transfer Ownership", color = Color(0xFFFFA726), fontSize = 15.sp)
                    }
                    if (showTransferDialog) {
                        val admins = members.filter { it.role == 1 }
                        AlertDialog(
                            onDismissRequest = { showTransferDialog = false },
                            containerColor = C.card,
                            title = { Text("Transfer Ownership", color = Color.White) },
                            text = {
                                Column {
                                    if (admins.isEmpty()) {
                                        Text("Promote an admin first before transferring ownership.", color = C.textSecondary, fontSize = 14.sp)
                                    } else {
                                        Text("Choose a new owner:", color = C.textSecondary, fontSize = 13.sp)
                                        Spacer(Modifier.height(8.dp))
                                        admins.forEach { admin ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        ChatService.groups.transferOwnership(groupId, admin.handle) { s, e ->
                                                            if (s) {
                                                                Toast.makeText(context, "Ownership transferred to @${admin.handle}", Toast.LENGTH_SHORT).show()
                                                                scope.launch {
                                                                    ChatService.groups.refreshGroupMembers(groupId)
                                                                    ChatService.groups.sendGroupService(groupId, "ownership_transferred", admin.handle)
                                                                }
                                                            } else Toast.makeText(context, e ?: "Failed", Toast.LENGTH_SHORT).show()
                                                        }
                                                        showTransferDialog = false
                                                    }
                                                    .padding(vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                AvatarDisplay(handle = admin.handle, size = 36.dp)
                                                Spacer(Modifier.width(10.dp))
                                                Text("@${admin.handle}", color = Color.White, fontSize = 15.sp)
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {},
                            dismissButton = { TextButton(onClick = { showTransferDialog = false }) { Text("Cancel", color = C.textSecondary) } },
                        )
                    }
                }
            }

            item { HorizontalDivider(color = C.card) }

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

@OptIn(ExperimentalFoundationApi::class)
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
    onTap: () -> Unit = {},
) {
    var showMenu by remember { mutableStateOf(false) }
    var actionPending by remember { mutableStateOf(false) }

    // Reset pending after 10 seconds (TX either confirmed or failed by then)
    LaunchedEffect(actionPending) {
        if (actionPending) {
            kotlinx.coroutines.delay(10000)
            actionPending = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (!isMe && !actionPending && (callerIsAdmin || callerIsCreator)) showMenu = true
                    else if (!isMe) onTap()
                },
                onLongClick = {
                    if (!isMe) onTap() // long press → contact info for admin/creator too
                },
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarDisplay(handle = member.handle, size = 44.dp, isMe = isMe)

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (!member.displayName.isNullOrEmpty()) member.displayName else "@${member.handle}",
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
            if (!member.displayName.isNullOrEmpty()) {
                Text(
                    text = "@${member.handle}",
                    color = C.textSecondary,
                    fontSize = 13.sp,
                )
            }
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
            if (actionPending) {
                DropdownMenuItem(text = { Text("Action submitted...", color = C.textMuted) }, onClick = { showMenu = false })
            } else {
            if (callerIsCreator) {
                if (member.role == 0) {
                    DropdownMenuItem(
                        text = { Text("Promote to Admin") },
                        onClick = { showMenu = false; actionPending = true; onPromote() },
                    )
                }
                if (member.role == 1) {
                    DropdownMenuItem(
                        text = { Text("Demote to Member") },
                        onClick = { showMenu = false; actionPending = true; onDemote() },
                    )
                }
            }
            if (callerIsAdmin || callerIsCreator) {
                if (member.role < 2) {
                    DropdownMenuItem(
                        text = { Text("Remove") },
                        onClick = { showMenu = false; actionPending = true; onRemove() },
                    )
                    DropdownMenuItem(
                        text = { Text("Ban", color = Color(0xFFEF5350)) },
                        onClick = { showMenu = false; actionPending = true; onBan() },
                    )
                }
            }
            } // end !actionPending
        }
    }
}
