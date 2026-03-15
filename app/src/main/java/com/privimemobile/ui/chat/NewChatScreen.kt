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
import com.privimemobile.protocol.Contact
import com.privimemobile.protocol.ContactResolver
import com.privimemobile.protocol.Helpers
import com.privimemobile.protocol.ProtocolStartup
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class ContactEntry(
    val handle: String,
    val walletId: String,
    val displayName: String,
)

@Composable
fun NewChatScreen(onStartChat: (String) -> Unit, onBack: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var resolving by remember { mutableStateOf(false) }
    var lookupResult by remember { mutableStateOf<ContactEntry?>(null) }
    var lookupStatus by remember { mutableStateOf("idle") } // idle, searching, found, not_found, error
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var lookupJob by remember { mutableStateOf<Job?>(null) }

    val rawContacts by ProtocolStartup.contacts.collectAsState()
    val identity by ProtocolStartup.identity.collectAsState()

    // Build list of known contacts (handles only) for local filtering
    val knownContacts = remember(rawContacts, identity) {
        rawContacts.entries.mapNotNull { (key, c) ->
            if (!key.startsWith("@") || c.walletId.isEmpty()) return@mapNotNull null
            val handle = key.removePrefix("@")
            if (handle == identity?.handle?.lowercase()) return@mapNotNull null
            ContactEntry(handle = handle, walletId = c.walletId, displayName = c.displayName)
        }.sortedBy { it.handle }
    }

    // Filter contacts by input prefix
    val filteredContacts = remember(input, knownContacts) {
        val q = input.trim().removePrefix("@").lowercase()
        if (q.isEmpty()) knownContacts
        else knownContacts.filter { c ->
            c.handle.contains(q) || c.displayName.lowercase().contains(q)
        }
    }

    fun openChat(handle: String, walletId: String, displayName: String) {
        val convKey = "@$handle"
        // Ensure contact is saved so chat screen can find the wallet_id
        val updatedContacts = ProtocolStartup.contacts.value.toMutableMap()
        updatedContacts[convKey] = Contact(
            handle = handle,
            walletId = walletId,
            displayName = displayName,
        )
        // Update contacts in protocol
        // (ProtocolStartup exposes _contacts via StateFlow, update through updateConversations pattern)
        // Use the contacts map directly — the chat screen reads from ProtocolStartup.contacts
        ProtocolStartup.updateContacts(updatedContacts)
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
        if (trimmed == identity?.handle?.lowercase()) return

        // If exact match in local contacts, skip contract lookup
        val convKey = "@$trimmed"
        if (rawContacts[convKey]?.walletId?.isNotEmpty() == true) return

        lookupStatus = "searching"
        lookupJob = scope.launch {
            delay(400) // debounce
            ContactResolver.resolveHandleToContact(trimmed) { error, result ->
                if (error != null) {
                    val errLower = error.lowercase()
                    if (errLower.contains("not found") || errLower.contains("not registered")) {
                        lookupStatus = "not_found"
                    } else {
                        lookupStatus = "error"
                    }
                } else if (result != null && result.walletId.isNotEmpty()) {
                    lookupResult = ContactEntry(
                        handle = result.handle,
                        walletId = result.walletId,
                        displayName = result.displayName,
                    )
                    lookupStatus = "found"
                } else {
                    lookupStatus = "not_found"
                }
            }
        }
    }

    fun handleStart() {
        // If we have a lookup result, use it directly
        if (lookupResult != null) {
            openChat(lookupResult!!.handle, lookupResult!!.walletId, lookupResult!!.displayName)
            return
        }

        val trimmed = input.trim()
        if (trimmed.isEmpty()) return

        // Check if it's a handle
        val isHandle = Regex("^@?[a-z0-9_]{3,32}$", RegexOption.IGNORE_CASE).matches(trimmed)
        if (isHandle) {
            val handle = trimmed.removePrefix("@").lowercase()
            val convKey = "@$handle"

            // If already in contacts, go directly
            val existing = rawContacts[convKey]
            if (existing != null && existing.walletId.isNotEmpty()) {
                onStartChat(handle)
                return
            }

            // Resolve via contract
            resolving = true
            errorMessage = null
            ContactResolver.resolveHandleToContact(handle) { error, result ->
                resolving = false
                if (error != null) {
                    errorMessage = if (error.lowercase().contains("not found")) "Handle not found" else error
                    return@resolveHandleToContact
                }
                if (result != null) {
                    openChat(result.handle, result.walletId, result.displayName)
                }
            }
            return
        }

        // Check if it's a raw wallet ID
        val normalized = Helpers.normalizeWalletId(trimmed)
        if (normalized != null) {
            // Create a contact entry for the raw wallet_id and navigate
            val updatedContacts = rawContacts.toMutableMap()
            if (!updatedContacts.containsKey(normalized)) {
                updatedContacts[normalized] = Contact(
                    handle = "",
                    walletId = normalized,
                    displayName = "",
                )
                ProtocolStartup.updateContacts(updatedContacts)
            }
            // Navigate using wallet_id as convKey (will be migrated to @handle on resolution)
            onStartChat(normalized)
            return
        }

        errorMessage = "Enter a @handle (3-32 chars) or wallet address (66-68 hex chars)"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg)
            .padding(16.dp),
    ) {
        TextButton(onClick = onBack) {
            Text("< Back", color = C.textSecondary)
        }
        Spacer(Modifier.height(8.dp))
        Text("New Chat", color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        // Search input
        OutlinedTextField(
            value = input,
            onValueChange = { onInputChange(it.lowercase().trim()) },
            placeholder = { Text("Search handle...", color = C.textMuted) },
            prefix = { Text("@", color = C.accent, fontWeight = FontWeight.Bold) },
            trailingIcon = {
                if (lookupStatus == "searching") {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = C.accent,
                        strokeWidth = 2.dp,
                    )
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = C.accent,
                unfocusedBorderColor = C.border,
                focusedContainerColor = C.card,
                unfocusedContainerColor = C.card,
                cursorColor = C.accent,
                focusedTextColor = C.text,
                unfocusedTextColor = C.text,
            ),
        )

        // Contract lookup result (new user not in contacts)
        if (lookupStatus == "found" && lookupResult != null) {
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        openChat(lookupResult!!.handle, lookupResult!!.walletId, lookupResult!!.displayName)
                    },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = C.card),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(C.incoming),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(C.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            (lookupResult!!.displayName.ifEmpty { lookupResult!!.handle })
                                .first().uppercase(),
                            color = C.textDark,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "@${lookupResult!!.handle}",
                            color = C.text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (lookupResult!!.displayName.isNotEmpty()) {
                            Text(
                                lookupResult!!.displayName,
                                color = C.textSecondary,
                                fontSize = 13.sp,
                            )
                        }
                    }
                    // "New" badge
                    Text(
                        "New",
                        color = C.incoming,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(C.incoming.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }

        if (lookupStatus == "not_found" && input.trim().removePrefix("@").length >= 3) {
            Spacer(Modifier.height(8.dp))
            Text(
                "@${input.trim().removePrefix("@")} not found on-chain",
                color = C.error,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        if (lookupStatus == "error") {
            Spacer(Modifier.height(8.dp))
            Text(
                "Lookup timed out -- try again",
                color = C.error,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        if (errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                errorMessage!!,
                color = C.error,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        // Known contacts section header
        if (knownContacts.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                if (input.trim().isNotEmpty()) "Contacts" else "Your Contacts",
                color = C.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }

        // Contacts list
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(filteredContacts, key = { it.handle }) { contact ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openChat(contact.handle, contact.walletId, contact.displayName) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(C.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            (contact.displayName.ifEmpty { contact.handle }).first().uppercase(),
                            color = C.textDark,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "@${contact.handle}",
                            color = C.text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (contact.displayName.isNotEmpty()) {
                            Text(
                                contact.displayName,
                                color = C.textSecondary,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
                HorizontalDivider(color = C.border, thickness = 0.5.dp)
            }

            // Empty state
            if (filteredContacts.isEmpty()) {
                item {
                    Text(
                        if (knownContacts.isNotEmpty() && input.trim().isNotEmpty()) {
                            "No matching contacts"
                        } else if (knownContacts.isEmpty()) {
                            "No contacts yet -- search by @handle above"
                        } else "",
                        color = C.textMuted,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                    )
                }
            }
        }

        // Resolving spinner
        if (resolving) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally).size(36.dp),
                color = C.accent,
                strokeWidth = 3.dp,
            )
        }

        // Start chat button (shown when input has text but no automatic result)
        if (input.trim().isNotEmpty() && lookupStatus != "found") {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { handleStart() },
                enabled = !resolving,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.accent),
            ) {
                if (resolving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = C.textDark,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Start Chat", color = C.textDark, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
