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
import com.privimemobile.protocol.ShaderInvoker
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NewChatScreen(onStartChat: (String) -> Unit, onBack: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var resolving by remember { mutableStateOf(false) }
    var lookupResult by remember { mutableStateOf<ContactEntity?>(null) }
    var lookupStatus by remember { mutableStateOf("idle") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var lookupJob by remember { mutableStateOf<Job?>(null) }

    // Contacts from Room DB
    val allContacts by ChatService.db?.contactDao()?.observeAll()
        ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val chatState by ChatService.observeState().collectAsState(initial = null)
    val myHandle = chatState?.myHandle

    // Filter contacts by input prefix (from 1st character)
    val filteredContacts = remember(input, allContacts, myHandle) {
        val q = input.trim().removePrefix("@").lowercase()
        val contacts = allContacts.filter { it.handle != myHandle && !it.walletId.isNullOrEmpty() }
        if (q.isEmpty()) contacts
        else contacts.filter { c ->
            c.handle.contains(q) || (c.displayName ?: "").lowercase().contains(q)
        }
    }

    fun openChat(handle: String, walletId: String?, displayName: String?) {
        // Ensure contact exists in Room DB
        scope.launch {
            ChatService.contacts.ensureContact(handle, displayName, walletId)
        }
        onStartChat(handle)
    }

    fun onInputChange(value: String) {
        input = value
        lookupResult = null
        lookupStatus = "idle"
        errorMessage = null
        lookupJob?.cancel()

        val trimmed = value.trim().removePrefix("@").lowercase()
        if (trimmed.length < 3 || !Regex("^[a-z0-9_]+$").matches(trimmed)) return
        if (trimmed == myHandle) return

        // If exact match in local contacts, skip contract lookup
        if (allContacts.any { it.handle == trimmed && !it.walletId.isNullOrEmpty() }) return

        lookupStatus = "searching"
        lookupJob = scope.launch {
            delay(400) // debounce
            val result = ChatService.contacts.resolveHandle(trimmed)
            if (result != null && !result.walletId.isNullOrEmpty()) {
                lookupResult = result
                lookupStatus = "found"
            } else {
                lookupStatus = "not_found"
            }
        }
    }

    fun handleStart() {
        if (lookupResult != null) {
            openChat(lookupResult!!.handle, lookupResult!!.walletId, lookupResult!!.displayName)
            return
        }

        val trimmed = input.trim()
        if (trimmed.isEmpty()) return

        val isHandle = Regex("^@?[a-z0-9_]{3,32}$", RegexOption.IGNORE_CASE).matches(trimmed)
        if (isHandle) {
            val handle = trimmed.removePrefix("@").lowercase()
            val existing = allContacts.firstOrNull { it.handle == handle }
            if (existing != null && !existing.walletId.isNullOrEmpty()) {
                onStartChat(handle)
                return
            }

            resolving = true
            errorMessage = null
            scope.launch {
                val result = ChatService.contacts.resolveHandle(handle)
                resolving = false
                if (result != null && !result.walletId.isNullOrEmpty()) {
                    openChat(result.handle, result.walletId, result.displayName)
                } else {
                    errorMessage = "Handle not found"
                }
            }
            return
        }

        val normalized = Helpers.normalizeWalletId(trimmed)
        if (normalized != null) {
            scope.launch {
                val result = ChatService.contacts.resolveWalletId(normalized)
                if (result != null) {
                    openChat(result.handle, result.walletId, result.displayName)
                } else {
                    openChat(normalized, normalized, "")
                }
            }
            return
        }

        errorMessage = "Enter a @handle (3-32 chars) or wallet address (66-68 hex chars)"
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
            onValueChange = { onInputChange(it.lowercase().trim()) },
            placeholder = { Text("Search handle...", color = C.textMuted) },
            prefix = { Text("@", color = C.accent, fontWeight = FontWeight.Bold) },
            trailingIcon = {
                if (lookupStatus == "searching") {
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

        // Contract lookup result
        if (lookupStatus == "found" && lookupResult != null) {
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth().clickable {
                    openChat(lookupResult!!.handle, lookupResult!!.walletId, lookupResult!!.displayName)
                },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = C.card),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(C.accent), contentAlignment = Alignment.Center) {
                        Text(
                            (lookupResult!!.displayName?.ifEmpty { null } ?: lookupResult!!.handle).first().uppercase(),
                            color = C.textDark, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("@${lookupResult!!.handle}", color = C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        if (!lookupResult!!.displayName.isNullOrEmpty()) {
                            Text(lookupResult!!.displayName!!, color = C.textSecondary, fontSize = 13.sp)
                        }
                    }
                    Text("New", color = C.incoming, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(C.incoming.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }

        if (lookupStatus == "not_found" && input.trim().removePrefix("@").length >= 3) {
            Spacer(Modifier.height(8.dp))
            Text("@${input.trim().removePrefix("@")} not found on-chain", color = C.error, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
        }
        if (lookupStatus == "error") {
            Spacer(Modifier.height(8.dp))
            Text("Lookup timed out -- try again", color = C.error, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
        }
        if (errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(errorMessage!!, color = C.error, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp))
        }

        // Known contacts
        if (filteredContacts.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                if (input.trim().isNotEmpty()) "Contacts" else "Your Contacts",
                color = C.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(filteredContacts, key = { it.handle }) { contact ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { openChat(contact.handle, contact.walletId, contact.displayName) }
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
                    Column {
                        Text("@${contact.handle}", color = C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        if (!contact.displayName.isNullOrEmpty()) {
                            Text(contact.displayName!!, color = C.textSecondary, fontSize = 13.sp)
                        }
                    }
                }
                HorizontalDivider(color = C.border, thickness = 0.5.dp)
            }
            if (filteredContacts.isEmpty()) {
                item {
                    Text(
                        if (allContacts.isNotEmpty() && input.trim().isNotEmpty()) "No matching contacts"
                        else if (allContacts.isEmpty()) "No contacts yet -- search by @handle above"
                        else "",
                        color = C.textMuted, fontSize = 14.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    )
                }
            }
        }

        if (resolving) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).size(36.dp), color = C.accent, strokeWidth = 3.dp)
        }

        if (input.trim().isNotEmpty() && lookupStatus != "found") {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { handleStart() }, enabled = !resolving,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.accent),
            ) {
                if (resolving) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = C.textDark, strokeWidth = 2.dp)
                } else {
                    Text("Start Chat", color = C.textDark, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
