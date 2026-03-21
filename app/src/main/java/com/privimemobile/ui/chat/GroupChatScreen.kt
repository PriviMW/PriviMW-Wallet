package com.privimemobile.ui.chat

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
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
import com.privimemobile.chat.db.entities.MessageEntity
import com.privimemobile.ui.components.AvatarDisplay
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    groupId: String,
    onBack: () -> Unit,
    onGroupSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var group by remember { mutableStateOf<GroupEntity?>(null) }
    var inputText by remember { mutableStateOf("") }

    val state by ChatService.observeState().collectAsState(initial = null)
    val myHandle = state?.myHandle

    // Load group info
    LaunchedEffect(groupId) {
        group = ChatService.db?.groupDao()?.findByGroupId(groupId)
        if (group == null) {
            ChatService.groups.refreshGroupInfo(groupId)
            group = ChatService.db?.groupDao()?.findByGroupId(groupId)
        }
        ChatService.groups.refreshGroupMembers(groupId)
        // Clear unread
        ChatService.db?.groupDao()?.clearUnread(groupId)
    }

    // Observe messages for this group
    val groupEntity = group
    val messages by if (groupEntity != null) {
        ChatService.db?.messageDao()?.observeAll(groupEntity.id)
            ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    } else {
        remember { mutableStateOf(emptyList<MessageEntity>()) }
    }

    val reversedMessages = remember(messages) { messages.reversed() }

    // Scroll to bottom on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
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
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = onGroupSettings) {
                        Icon(Icons.Default.MoreVert, "Settings", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = C.card),
            )
        },
        containerColor = C.bg,
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            // Message list
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                items(reversedMessages, key = { it.id }) { msg ->
                    GroupMessageBubble(
                        msg = msg,
                        isMe = msg.sent,
                        myHandle = myHandle,
                    )
                }
            }

            // Input bar
            Surface(
                color = C.card,
                tonalElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message", color = C.textMuted) },
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = C.accent,
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = C.accent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                        ),
                        shape = RoundedCornerShape(24.dp),
                    )

                    Spacer(Modifier.width(6.dp))

                    // Send button
                    if (inputText.isNotBlank()) {
                        IconButton(
                            onClick = {
                                val text = inputText.trim()
                                if (text.isNotEmpty() && groupId.isNotEmpty()) {
                                    inputText = ""
                                    scope.launch {
                                        ChatService.groups.sendGroupMessage(groupId, text)
                                    }
                                }
                            },
                        ) {
                            Icon(Icons.Default.Send, "Send", tint = C.accent, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Group message bubble — shows sender name for non-self messages. */
@Composable
private fun GroupMessageBubble(
    msg: MessageEntity,
    isMe: Boolean,
    myHandle: String?,
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    // Service messages (join/leave/etc.)
    if (msg.type == "group_service") {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = msg.text ?: "",
                color = C.textMuted,
                fontSize = 13.sp,
                modifier = Modifier
                    .background(C.card.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        return
    }

    val arrangement = if (isMe) Arrangement.End else Arrangement.Start

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp, horizontal = 4.dp),
        horizontalArrangement = arrangement,
    ) {
        if (!isMe) {
            // Sender avatar
            AvatarDisplay(handle = msg.senderHandle ?: "", size = 32.dp)
            Spacer(Modifier.width(6.dp))
        }

        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            // Sender name (only for others)
            if (!isMe && msg.senderHandle != null) {
                Text(
                    text = "@${msg.senderHandle}",
                    color = C.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
                )
            }

            // Message bubble
            Card(
                shape = RoundedCornerShape(
                    topStart = if (isMe) 16.dp else 4.dp,
                    topEnd = if (isMe) 4.dp else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp,
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isMe) C.accent.copy(alpha = 0.2f) else C.card,
                ),
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = msg.text ?: "",
                        color = Color.White,
                        fontSize = 15.sp,
                    )
                    Text(
                        text = timeFormat.format(Date(msg.timestamp * 1000)),
                        color = C.textMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                    )
                }
            }
        }
    }
}
