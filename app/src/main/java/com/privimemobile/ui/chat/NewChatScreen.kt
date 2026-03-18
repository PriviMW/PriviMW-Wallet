package com.privimemobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.chat.ChatService
import com.privimemobile.chat.db.entities.ContactEntity
import com.privimemobile.protocol.Helpers
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NewChatScreen(onStartChat: (String) -> Unit, onBack: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var onChainResults by remember { mutableStateOf<List<ContactEntity>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    // Local contacts from Room DB
    val allContacts by ChatService.db?.contactDao()?.observeAll()
        ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val chatState by ChatService.observeState().collectAsState(initial = null)
    val myHandle = chatState?.myHandle

    // Filter local contacts by input
    val query = input.trim().removePrefix("@").lowercase()
    val localMatches = remember(query, allContacts, myHandle) {
        val contacts = allContacts.filter { it.handle != myHandle && !it.walletId.isNullOrEmpty() }
        if (query.isEmpty()) contacts
        else contacts.filter { c ->
            c.handle.contains(query) || (c.displayName ?: "").lowercase().contains(query)
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
            searching = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(C.bg).padding(16.dp),
    ) {
        TextButton(onClick = onBack) {
            Text("< Back", color = C.textSecondary)
        }
        Spacer(Modifier.height(8.dp))
        Text("New Chat", color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = input,
            onValueChange = { onInputChange(it) },
            placeholder = { Text("Search handle...", color = C.textMuted) },
            prefix = { Text("@", color = C.accent, fontWeight = FontWeight.Bold) },
            trailingIcon = {
                if (searching) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = C.accent, strokeWidth = 2.dp)
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = C.accent, unfocusedBorderColor = C.border,
                focusedContainerColor = C.card, unfocusedContainerColor = C.card,
                cursorColor = C.accent, focusedTextColor = C.text, unfocusedTextColor = C.text,
            ),
        )

        Spacer(Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            // On-chain search results (new contacts not in local DB)
            if (onChainNew.isNotEmpty()) {
                item {
                    Text("On-chain", color = C.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                }
                items(onChainNew, key = { "onchain_${it.handle}" }) { contact ->
                    ContactRow(contact, tag = "New") { openChat(contact.handle, contact.walletId, contact.displayName) }
                    HorizontalDivider(color = C.border, thickness = 0.5.dp)
                }
            }

            // Local contacts
            if (localMatches.isNotEmpty()) {
                item {
                    Text(
                        if (query.isNotEmpty()) "Contacts" else "Your Contacts",
                        color = C.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp, top = if (onChainNew.isNotEmpty()) 16.dp else 0.dp, bottom = 8.dp),
                    )
                }
                items(localMatches, key = { "local_${it.handle}" }) { contact ->
                    ContactRow(contact) { openChat(contact.handle, contact.walletId, contact.displayName) }
                    HorizontalDivider(color = C.border, thickness = 0.5.dp)
                }
            }

            // Empty state
            if (localMatches.isEmpty() && onChainNew.isEmpty() && !searching) {
                item {
                    Text(
                        if (query.isNotEmpty()) "No handles found for \"$query\""
                        else "Search for a @handle to start chatting",
                        color = C.textMuted, fontSize = 14.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: ContactEntity, tag: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(C.accent), contentAlignment = Alignment.Center) {
            Text(
                (contact.displayName?.ifEmpty { null } ?: contact.handle).first().uppercase(),
                color = C.textDark, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("@${contact.handle}", color = C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (!contact.displayName.isNullOrEmpty()) {
                Text(contact.displayName!!, color = C.textSecondary, fontSize = 13.sp)
            }
        }
        if (tag != null) {
            Text(tag, color = C.incoming, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(C.incoming.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
}
