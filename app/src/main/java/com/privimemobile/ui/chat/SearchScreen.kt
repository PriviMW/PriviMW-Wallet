package com.privimemobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.chat.ChatService
import com.privimemobile.chat.db.entities.MessageEntity
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SearchScreen(
    onOpenChat: (handle: String, scrollToTs: Long) -> Unit,
    onOpenGroupChat: (groupId: String) -> Unit = {},
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MessageEntity>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    // Cache conversation keys by ID
    var convMap by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }

    fun doSearch(q: String) {
        searchJob?.cancel()
        if (q.isBlank()) { results = emptyList(); return }
        searching = true
        searchJob = scope.launch {
            delay(300) // debounce
            try {
                // FTS requires special syntax — wrap in quotes for phrase, or append *
                val ftsQuery = q.trim().replace("\"", "").let {
                    if (it.contains(" ")) "\"$it\"" else "$it*"
                }
                val found = ChatService.db?.messageDao()?.search(ftsQuery) ?: emptyList()
                results = found

                // Resolve conversation keys for results
                val ids = found.map { it.conversationId }.toSet()
                val map = mutableMapOf<Long, String>()
                ids.forEach { convId ->
                    val conv = ChatService.db?.conversationDao()?.findById(convId)
                    if (conv != null) map[convId] = conv.convKey
                }
                convMap = map
            } catch (_: Exception) {
                results = emptyList()
            }
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
        Text("Search Messages", color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it; doSearch(it) },
            placeholder = { Text("Search...", color = C.textMuted) },
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

        if (results.isEmpty() && query.isNotBlank() && !searching) {
            Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                Text("No results for \"$query\"", color = C.textMuted, fontSize = 14.sp)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(results, key = { it.id }) { msg ->
                val convKey = convMap[msg.conversationId] ?: ""
                val isGroupResult = convKey.startsWith("g_")
                val handle = if (isGroupResult) msg.senderHandle ?: "group" else convKey.removePrefix("@")
                SearchResultCard(
                    msg = msg,
                    handle = handle,
                    isGroup = isGroupResult,
                    onClick = {
                        if (isGroupResult) {
                            // Find group_id from convKey prefix and navigate to group chat
                            scope.launch {
                                val groupIdPrefix = convKey.removePrefix("g_")
                                val group = ChatService.db?.groupDao()?.findByConvKey(groupIdPrefix)
                                if (group != null) onOpenGroupChat(group.groupId)
                            }
                        } else if (handle.isNotEmpty()) {
                            onOpenChat(handle, msg.timestamp)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchResultCard(msg: MessageEntity, handle: String, isGroup: Boolean = false, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = C.card),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (isGroup) "\uD83D\uDC65 @$handle" else if (handle.isNotEmpty()) "@$handle" else "Unknown",
                    color = C.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    formatSearchTime(msg.timestamp),
                    color = C.textSecondary, fontSize = 11.sp,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                msg.text ?: "",
                color = C.text, fontSize = 14.sp,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            if (msg.sent) {
                Text("You", color = C.textSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

private val searchTimeFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
private fun formatSearchTime(ts: Long): String {
    if (ts <= 0) return ""
    return searchTimeFormat.format(Date(ts * 1000))
}
