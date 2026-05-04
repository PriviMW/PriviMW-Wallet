package com.privimemobile.ui.chat

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import com.privimemobile.R
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
    val bannedMembers by ChatService.db?.groupDao()?.observeBannedMembers(groupId)
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
                title = { Text(stringResource(R.string.group_info_title), color = C.text) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.general_back), tint = C.text)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = C.card),
                windowInsets = WindowInsets(0),
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
                            Toast.makeText(context, context.getString(R.string.group_avatar_updated), Toast.LENGTH_SHORT).show()
                            // Broadcast to all members via SBBS
                            scope.launch {
                                val ts = System.currentTimeMillis() / 1000
                                // Insert local service message
                                val convId = ChatService.groups.getOrCreateGroupConversation(groupId, group?.name ?: "Group")
                                val dedupKey = "$ts:info_update:avatar:$groupId".hashCode().toString(16)
                                ChatService.db?.messageDao()?.insert(com.privimemobile.chat.db.entities.MessageEntity(
                                    conversationId = convId, timestamp = ts,
                                    senderHandle = myHandle, text = context.getString(R.string.group_you_updated_picture),
                                    type = "group_service", sent = true, sbbsDedupKey = dedupKey,
                                ))
                                ChatService.db?.groupDao()?.updateLastMessage(groupId, ts, context.getString(R.string.group_you_updated_picture))
                                // Broadcast
                                val base64 = android.util.Base64.encodeToString(result.bytes, android.util.Base64.NO_WRAP)
                                val payload = mapOf(
                                    "v" to 1, "t" to "group_info_update",
                                    "ts" to ts,
                                    "avatar" to base64,
                                    "avatar_hash" to result.hashHex,
                                )
                                ChatService.groups.sendGroupPayload(groupId, payload)
                                ChatService.db?.groupDao()?.updateGroupInfo(groupId, result.hashHex, ts)
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
                                    Icon(Icons.Default.Group, null, tint = C.text, modifier = Modifier.size(40.dp))
                                }
                            }
                        } else {
                            Box(modifier = Modifier.size(80.dp).background(C.accent, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Group, null, tint = C.text, modifier = Modifier.size(40.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    var showNameDialog by remember { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = group?.name ?: "",
                            color = C.text,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        if (isAdmin) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Default.Edit, null, tint = C.textMuted,
                                modifier = Modifier.size(16.dp).clickable { showNameDialog = true },
                            )
                        }
                    }
                    if (showNameDialog) {
                        var newName by remember { mutableStateOf(group?.name ?: "") }
                        AlertDialog(
                            onDismissRequest = { showNameDialog = false },
                            containerColor = C.card,
                            title = { Text(stringResource(R.string.group_edit_name_title), color = C.text) },
                            text = {
                                Column {
                                    OutlinedTextField(
                                        value = newName,
                                        onValueChange = { if (it.length <= 32) newName = it },
                                        placeholder = { Text(stringResource(R.string.group_name_placeholder), color = C.textMuted) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = C.accent, unfocusedBorderColor = C.border,
                                            cursorColor = C.accent, focusedTextColor = C.text, unfocusedTextColor = C.text,
                                        ),
                                    )
                                    Text("${newName.length}/32", color = C.textMuted, fontSize = 12.sp, modifier = Modifier.align(Alignment.End))
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    val trimmed = newName.trim()
                                    if (trimmed.isNotEmpty() && trimmed != group?.name) {
                                        // Submit on-chain TX — SBBS broadcast + local update happens after TX confirms
                                        ChatService.groups.updateGroupInfo(groupId, name = trimmed) { success, _ ->
                                            if (success) {
                                                android.widget.Toast.makeText(context, context.getString(R.string.settings_toast_tx_name_update), android.widget.Toast.LENGTH_LONG).show()
                                            } else {
                                                android.widget.Toast.makeText(context, context.getString(R.string.settings_toast_name_update_failed), android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                    showNameDialog = false
                                }) { Text(stringResource(R.string.general_save), color = C.accent) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showNameDialog = false }) { Text(stringResource(R.string.general_cancel), color = C.textSecondary) }
                            },
                        )
                    }
                    Text(
                        text = stringResource(R.string.group_member_count_format, members.size),
                        color = C.textSecondary,
                        fontSize = 14.sp,
                    )
                    if (group?.isPublic == true) {
                        Text(stringResource(R.string.group_public_label), color = C.accent, fontSize = 13.sp)
                    } else {
                        Text(stringResource(R.string.group_private_label), color = C.textMuted, fontSize = 13.sp)
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
                        Text(stringResource(R.string.general_description), color = C.textSecondary, fontSize = 12.sp)
                        Text(
                            desc?.ifEmpty { stringResource(R.string.group_no_description) } ?: stringResource(R.string.group_no_description),
                            color = if (desc.isNullOrEmpty()) C.textMuted else C.text,
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
                        title = { Text(stringResource(R.string.group_edit_description_title), color = C.text) },
                        text = {
                            OutlinedTextField(
                                value = newDesc,
                                onValueChange = { if (it.length <= 200) newDesc = it },
                                placeholder = { Text(stringResource(R.string.group_edit_description_placeholder), color = C.textMuted) },
                                maxLines = 4,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = C.accent, unfocusedBorderColor = C.border,
                                    cursorColor = C.accent, focusedTextColor = C.text, unfocusedTextColor = C.text,
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
                                        senderHandle = myHandle, text = context.getString(R.string.group_updated_description),
                                        type = "group_service", sent = true, sbbsDedupKey = dedupKey,
                                    ))
                                    ChatService.db?.groupDao()?.updateLastMessage(groupId, ts, context.getString(R.string.group_updated_description))
                                    // Broadcast to all members
                                    val infoPayload = mapOf(
                                        "v" to 1, "t" to "group_info_update",
                                        "ts" to ts,
                                        "description" to descToSave,
                                    )
                                    ChatService.groups.sendGroupPayload(groupId, infoPayload)
                                }
                                Toast.makeText(context, context.getString(R.string.settings_toast_description_updated), Toast.LENGTH_SHORT).show()
                                showDescDialog = false
                            }) { Text(stringResource(R.string.general_save), color = C.accent) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDescDialog = false }) { Text(stringResource(R.string.general_cancel), color = C.textSecondary) }
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
                        stringResource(R.string.group_members_count, members.size),
                        color = C.accent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (isAdmin) {
                        var showAddDialog by remember { mutableStateOf(false) }
                        TextButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.PersonAdd, null, tint = C.accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.group_add_member_button), color = C.accent, fontSize = 13.sp)
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
                                title = { Text(stringResource(R.string.group_add_member_title), color = C.text) },
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
                                            placeholder = { Text(stringResource(R.string.group_search_handle_placeholder), color = C.textMuted) },
                                            singleLine = true,
                                            enabled = !addLoading,
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = C.accent, unfocusedBorderColor = C.border,
                                                cursorColor = C.accent, focusedTextColor = C.text, unfocusedTextColor = C.text,
                                            ),
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        if (searching) {
                                            Text(stringResource(R.string.search_searching), color = C.textMuted, fontSize = 13.sp)
                                        } else if (addQuery.removePrefix("@").trim().isNotEmpty() && searchResults.isEmpty()) {
                                            Text(stringResource(R.string.group_search_no_results), color = C.textSecondary, fontSize = 13.sp)
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
                                                                Toast.makeText(context, context.getString(R.string.settings_toast_invite_sent, contact.handle), Toast.LENGTH_SHORT).show()
                                                                showAddDialog = false
                                                            } else {
                                                                Toast.makeText(context, context.getString(R.string.settings_toast_invite_failed), Toast.LENGTH_SHORT).show()
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
                                                        color = if (isMember) C.textMuted else C.text,
                                                        fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                                    )
                                                    Text("@${contact.handle}", color = C.textSecondary, fontSize = 12.sp)
                                                }
                                                if (isMember) {
                                                    Text(stringResource(R.string.group_member_role_member), color = C.textMuted, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                },
                                confirmButton = {},
                                dismissButton = {
                                    TextButton(onClick = { showAddDialog = false; searchJob?.cancel() }) { Text(stringResource(R.string.general_close), color = C.textSecondary) }
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
                            if (s) Toast.makeText(context, context.getString(R.string.settings_toast_promote_tx, member.handle), Toast.LENGTH_LONG).show()
                            else Toast.makeText(context, e ?: context.getString(R.string.toast_update_address_failed), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDemote = {
                        ChatService.groups.setMemberRole(groupId, member.handle, 0) { s, e ->
                            if (s) Toast.makeText(context, context.getString(R.string.settings_toast_demote_tx, member.handle), Toast.LENGTH_LONG).show()
                            else Toast.makeText(context, e ?: context.getString(R.string.toast_update_address_failed), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onRemove = {
                        ChatService.groups.removeMember(groupId, member.handle, ban = false) { s, e ->
                            if (s) Toast.makeText(context, context.getString(R.string.settings_toast_remove_member_tx, member.handle), Toast.LENGTH_LONG).show()
                            else Toast.makeText(context, e ?: context.getString(R.string.toast_update_address_failed), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onBan = {
                        ChatService.groups.removeMember(groupId, member.handle, ban = true) { s, e ->
                            if (s) Toast.makeText(context, context.getString(R.string.settings_toast_ban_tx, member.handle), Toast.LENGTH_LONG).show()
                            else Toast.makeText(context, e ?: context.getString(R.string.toast_update_address_failed), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onTap = { onContactInfo(member.handle) },
                )
            }

            // Banned members section (admin/creator only)
            if (isAdmin && bannedMembers.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.group_banned_count, bannedMembers.size),
                        color = Color(0xFFEF5350), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(bannedMembers, key = { "banned_${it.id}" }) { member ->
                    MemberRow(
                        member = member,
                        isMe = false,
                        callerIsCreator = isCreator,
                        callerIsAdmin = isAdmin,
                        onPromote = {},
                        onDemote = {},
                        onRemove = {},
                        onBan = {},
                        onUnban = {
                            ChatService.groups.removeMember(groupId, member.handle, ban = false, isUnban = true) { s, e ->
                                if (s) Toast.makeText(context, context.getString(R.string.settings_toast_unban_tx, member.handle), Toast.LENGTH_LONG).show()
                                else Toast.makeText(context, e ?: context.getString(R.string.toast_update_address_failed), Toast.LENGTH_SHORT).show()
                            }
                        },
                        onTap = {},
                    )
                }
            }

            // Divider
            item { Spacer(Modifier.height(16.dp)); HorizontalDivider(color = C.card) }

            // ── Admin settings ──
            if (isAdmin) {
                item {
                    Text(
                        stringResource(R.string.group_admin_settings),
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
                        Text(stringResource(R.string.group_transfer_ownership), color = Color(0xFFFFA726), fontSize = 15.sp)
                    }
                    if (showTransferDialog) {
                        val admins = members.filter { it.role == 1 }
                        AlertDialog(
                            onDismissRequest = { showTransferDialog = false },
                            containerColor = C.card,
                            title = { Text(stringResource(R.string.group_transfer_ownership), color = C.text) },
                            text = {
                                Column {
                                    if (admins.isEmpty()) {
                                        Text(stringResource(R.string.group_transfer_need_admin), color = C.textSecondary, fontSize = 14.sp)
                                    } else {
                                        Text(stringResource(R.string.group_transfer_choose_owner), color = C.textSecondary, fontSize = 13.sp)
                                        Spacer(Modifier.height(8.dp))
                                        admins.forEach { admin ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        ChatService.groups.transferOwnership(groupId, admin.handle) { s, e ->
                                                            if (s) {
                                                                Toast.makeText(context, context.getString(R.string.settings_toast_ownership_transferred, admin.handle), Toast.LENGTH_SHORT).show()
                                                                scope.launch {
                                                                    ChatService.groups.refreshGroupMembers(groupId)
                                                                    ChatService.groups.sendGroupService(groupId, "ownership_transferred", admin.handle)
                                                                }
                                                            } else Toast.makeText(context, e ?: context.getString(R.string.toast_update_address_failed), Toast.LENGTH_SHORT).show()
                                                        }
                                                        showTransferDialog = false
                                                    }
                                                    .padding(vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                AvatarDisplay(handle = admin.handle, size = 36.dp)
                                                Spacer(Modifier.width(10.dp))
                                                Text("@${admin.handle}", color = C.text, fontSize = 15.sp)
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {},
                            dismissButton = { TextButton(onClick = { showTransferDialog = false }) { Text(stringResource(R.string.general_cancel), color = C.textSecondary) } },
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
                                    Toast.makeText(context, context.getString(R.string.settings_toast_left_group), Toast.LENGTH_SHORT).show()
                                    onDeleteGroup()
                                } else Toast.makeText(context, e ?: context.getString(R.string.toast_update_address_failed), Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Text(stringResource(R.string.group_leave_group), color = Color(0xFFEF5350), fontSize = 16.sp)
                    }
                }
            }

            if (isCreator) {
                item {
                    TextButton(
                        onClick = {
                            ChatService.groups.deleteGroup(groupId) { s, e ->
                                if (s) {
                                    Toast.makeText(context, context.getString(R.string.settings_toast_group_deleted), Toast.LENGTH_SHORT).show()
                                    onDeleteGroup()
                                } else Toast.makeText(context, e ?: context.getString(R.string.toast_update_address_failed), Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Text(stringResource(R.string.group_delete_group), color = Color(0xFFEF5350), fontSize = 16.sp)
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
    onUnban: () -> Unit = {},
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
                    if (!isMe && !actionPending && (callerIsAdmin || callerIsCreator) && member.role != 2) showMenu = true
                    else if (!isMe && member.role != 3) onTap()
                },
                onLongClick = {
                    if (!isMe) onTap() // long press → contact info for admin/creator too
                },
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarDisplay(handle = member.handle, displayName = member.displayName, size = 44.dp, isMe = isMe)

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (!member.displayName.isNullOrEmpty()) member.displayName else "@${member.handle}",
                    color = C.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isMe) {
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.group_self_label), color = C.textMuted, fontSize = 12.sp)
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
            2 -> stringResource(R.string.group_role_creator)
            1 -> stringResource(R.string.group_member_role_admin)
            3 -> stringResource(R.string.group_role_banned)
            else -> null
        }
        if (roleText != null) {
            val badgeColor = when (member.role) {
                2 -> C.accent
                1 -> Color(0xFF66BB6A)
                3 -> Color(0xFFEF5350)
                else -> C.textMuted
            }
            Text(
                text = roleText,
                color = badgeColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        // Context menu for admin actions
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            if (actionPending) {
                DropdownMenuItem(text = { Text(stringResource(R.string.group_action_pending), color = C.textMuted) }, onClick = { showMenu = false })
            } else {
            if (callerIsCreator) {
                if (member.role == 0) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.group_promote_to_admin)) },
                        onClick = { showMenu = false; actionPending = true; onPromote() },
                    )
                }
                if (member.role == 1) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.group_demote_to_member)) },
                        onClick = { showMenu = false; actionPending = true; onDemote() },
                    )
                }
            }
            if (callerIsAdmin || callerIsCreator) {
                if (member.role == 3) {
                    // Banned member — show unban
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.group_action_unban), color = Color(0xFF66BB6A)) },
                        onClick = { showMenu = false; actionPending = true; onUnban() },
                    )
                } else if (member.role < 2) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.group_action_remove)) },
                        onClick = { showMenu = false; actionPending = true; onRemove() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.group_action_ban), color = Color(0xFFEF5350)) },
                        onClick = { showMenu = false; actionPending = true; onBan() },
                    )
                }
            }
            } // end !actionPending
        }
    }
}
