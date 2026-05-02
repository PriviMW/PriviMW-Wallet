package com.privimemobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.R
import com.privimemobile.chat.ChatService
import com.privimemobile.chat.db.entities.ContactEntity
import com.privimemobile.protocol.Helpers
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NewChatScreen(
    onStartChat: (String) -> Unit,
    onBack: () -> Unit,
    onJoinGroup: ((String) -> Unit)? = null,
) {
    var input by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var onChainResults by remember { mutableStateOf<List<ContactEntity>>(emptyList()) }
    var groupResults by remember { mutableStateOf<List<Map<String, Any?>>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Groups user is already a member of
    val myGroups by ChatService.db?.groupDao()?.observeAll()
        ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val myGroupIds = remember(myGroups) { myGroups.map { it.groupId }.toSet() }

    // Local contacts from Room DB — only DM contacts (active non-group conversations)
    val allContacts by ChatService.db?.contactDao()?.observeDmContacts()
        ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val chatState by ChatService.observeState().collectAsState(initial = null)
    val myHandle = chatState?.myHandle

    // Filter local contacts by input
    val query = input.trim().removePrefix("@").lowercase()
    val localMatches = remember(query, allContacts, myHandle) {
        val contacts = allContacts.filter { it.handle != myHandle && !it.walletId.isNullOrEmpty() && !it.isDeleted }
        if (query.isEmpty()) contacts
        else contacts.filter { c ->
            c.handle.contains(query) || (c.displayName ?: "").lowercase().contains(query)
        }
    }

    // Resolve missing display names / wallet_ids from chain for DM contacts
    LaunchedEffect(allContacts) {
        for (contact in allContacts) {
            if (contact.displayName.isNullOrEmpty() || contact.walletId.isNullOrEmpty()) {
                ChatService.contacts.resolveHandle(contact.handle)
            }
        }
    }

    // Merge: on-chain results that aren't already in local matches
    val onChainNew = remember(onChainResults, localMatches, myHandle) {
        val localHandles = localMatches.map { it.handle }.toSet()
        onChainResults.filter { it.handle !in localHandles && it.handle != myHandle }
    }

    fun openChat(handle: String, walletId: String?, displayName: String?) {
        scope.launch {
            ChatService.contacts.ensureContact(handle, displayName, walletId)
        }
        onStartChat(handle)
    }

    // Debounced on-chain search — triggers from 1 character
    fun onInputChange(value: String) {
        input = value
        searchJob?.cancel()
        onChainResults = emptyList()

        val trimmed = value.trim().removePrefix("@").lowercase()
        if (trimmed.isEmpty() || !Regex("^[a-z0-9_]+$").matches(trimmed)) {
            searching = false
            return
        }

        searching = true
        searchJob = scope.launch {
            delay(300) // debounce
            val results = ChatService.contacts.searchOnChain(trimmed)
            onChainResults = results
            // Also search groups
            val groups = ChatService.groups.searchGroups(trimmed)
            groupResults = groups
            searching = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(C.bg),
    ) {
        // Top bar — Telegram-style with back arrow
        Surface(color = C.card, shadowElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.general_back), tint = C.text, modifier = Modifier.size(22.dp))
                }
                Text(stringResource(R.string.chat_new_chat), color = C.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Search field
        OutlinedTextField(
            value = input,
            onValueChange = { onInputChange(it) },
            placeholder = { Text(stringResource(R.string.new_chat_search_placeholder), color = C.textMuted, fontSize = 14.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = C.textSecondary, modifier = Modifier.size(20.dp))
            },
            trailingIcon = {
                if (searching) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = C.accent, strokeWidth = 2.dp)
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            shape = RoundedCornerShape(22.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = C.card,
                unfocusedContainerColor = C.card,
                cursorColor = C.accent,
                focusedTextColor = C.text,
                unfocusedTextColor = C.text,
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp),
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            // On-chain search results (new contacts not in local DB)
            if (onChainNew.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.new_chat_onchain_results),
                        color = C.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp),
                    )
                }
                items(onChainNew, key = { "onchain_${it.handle}" }) { contact ->
                    ContactRow(contact, tag = stringResource(R.string.new_chat_tag_new)) { openChat(contact.handle, contact.walletId, contact.displayName) }
                }
            }

            // Local contacts
            if (localMatches.isNotEmpty()) {
                item {
                    Text(
                        if (query.isNotEmpty()) stringResource(R.string.new_chat_contacts) else stringResource(R.string.new_chat_your_contacts),
                        color = C.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(start = 16.dp, top = if (onChainNew.isNotEmpty()) 16.dp else 4.dp, bottom = 8.dp),
                    )
                }
                items(localMatches, key = { "local_${it.handle}" }) { contact ->
                    ContactRow(contact) { openChat(contact.handle, contact.walletId, contact.displayName) }
                }
            }

            // Public group results
            if (groupResults.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.new_chat_public_groups),
                        color = C.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
                    )
                }
                items(groupResults.size, key = { "group_$it" }) { idx ->
                    val g = groupResults[idx]
                    val groupId = g["group_id"] as? String ?: return@items
                    val name = g["name"] as? String ?: ""
                    val creator = g["creator"] as? String ?: ""
                    val memberCount = g["member_count"] as? Int ?: 0
                    val needsApproval = (g["require_approval"] as? Int ?: 0) == 1
                    val alreadyMember = groupId in myGroupIds

                    GroupSearchRow(
                        name = name,
                        creator = creator,
                        memberCount = memberCount,
                        needsApproval = needsApproval,
                        alreadyMember = alreadyMember,
                        onJoin = { onDone ->
                            ChatService.groups.joinGroup(groupId) { success, error ->
                                if (success) {
                                    android.widget.Toast.makeText(context,
                                        if (needsApproval) context.getString(R.string.new_chat_join_request_sent) else context.getString(R.string.create_group_tx_submitted),
                                        android.widget.Toast.LENGTH_LONG).show()
                                    onBack()
                                } else {
                                    android.widget.Toast.makeText(context, error ?: context.getString(R.string.new_chat_failed_to_join), android.widget.Toast.LENGTH_SHORT).show()
                                    onDone() // reset spinner on failure/cancel
                                }
                            }
                        },
                    )
                }
            }

            // Empty state
            if (localMatches.isEmpty() && onChainNew.isEmpty() && groupResults.isEmpty() && !searching) {
                item {
                    Text(
                        if (query.isNotEmpty()) stringResource(R.string.new_chat_no_results_format, query)
                        else stringResource(R.string.new_chat_empty_prompt),
                        color = C.textMuted, fontSize = 14.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: ContactEntity, tag: String? = null, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.privimemobile.ui.components.AvatarDisplay(
                handle = contact.handle,
                displayName = contact.displayName,
                size = 44.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        contact.displayName?.ifEmpty { null } ?: "@${contact.handle}",
                        color = C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    )
                    if (tag != null) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = C.accent.copy(alpha = 0.15f),
                        ) {
                            Text(
                                tag, color = C.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                if (!contact.displayName.isNullOrEmpty()) {
                    Text("@${contact.handle}", color = C.textSecondary, fontSize = 13.sp)
                }
            }
        }
        HorizontalDivider(
            color = C.border.copy(alpha = 0.5f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(start = 74.dp),
        )
    }
}

@Composable
private fun GroupSearchRow(
    name: String,
    creator: String,
    memberCount: Int,
    needsApproval: Boolean,
    alreadyMember: Boolean = false,
    onJoin: (onDone: () -> Unit) -> Unit,
) {
    var joining by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Group icon
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF5C6BC0)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Group, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.new_chat_group_members_format, memberCount, creator),
                    color = C.textSecondary, fontSize = 13.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (alreadyMember) {
                Text(stringResource(R.string.new_chat_joined), color = C.textMuted, fontSize = 13.sp)
            } else {
            Button(
                onClick = {
                    joining = true
                    onJoin { joining = false }
                },
                enabled = !joining,
                colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp),
            ) {
                if (joining) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(
                        if (needsApproval) stringResource(R.string.new_chat_request_button) else stringResource(R.string.new_chat_join_button),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
            } // end !alreadyMember
        }
        HorizontalDivider(
            color = C.border.copy(alpha = 0.5f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(start = 74.dp),
        )
    }
}
