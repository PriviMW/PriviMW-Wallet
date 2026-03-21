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

    // Get group conversation ID for messages
    var groupConvId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(group) {
        if (group != null) {
            groupConvId = ChatService.groups.getOrCreateGroupConversation(groupId, group!!.name)
        }
    }

    // Observe messages for this group's conversation
    val messages by if (groupConvId != null) {
        ChatService.db?.messageDao()?.observeAll(groupConvId!!)
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

            // Input bar — matches ChatScreen style
            Surface(
                color = C.inputBar,
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
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                        placeholder = { Text("Message", color = C.textMuted, fontSize = 15.sp) },
                        maxLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = C.card,
                            unfocusedContainerColor = C.card,
                            cursorColor = C.accent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                        ),
                        shape = RoundedCornerShape(22.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp),
                    )

                    Spacer(Modifier.width(6.dp))

                    // Send button — animated visibility
                    androidx.compose.animation.AnimatedVisibility(
                        visible = inputText.isNotBlank(),
                        enter = androidx.compose.animation.scaleIn(
                            animationSpec = androidx.compose.animation.core.tween(180)
                        ),
                        exit = androidx.compose.animation.scaleOut(
                            animationSpec = androidx.compose.animation.core.tween(180)
                        ),
                    ) {
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

/** Sender name colors — deterministic from handle hash. */
private val senderColors = listOf(
    Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFEF5350), Color(0xFFAB47BC),
    Color(0xFF42A5F5), Color(0xFFFF7043), Color(0xFF66BB6A), Color(0xFFEC407A),
    Color(0xFFFFA726), Color(0xFF78909C),
)

/** Group message bubble — shows sender name + avatar for non-self messages. */
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
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = msg.text ?: "",
                color = C.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier
                    .background(C.card, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        return
    }

    val arrangement = if (isMe) Arrangement.End else Arrangement.Start
    val senderColor = senderColors[kotlin.math.abs((msg.senderHandle ?: "").hashCode()) % senderColors.size]

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp, horizontal = 8.dp),
        horizontalArrangement = arrangement,
    ) {
        if (!isMe) {
            AvatarDisplay(handle = msg.senderHandle ?: "", size = 32.dp)
            Spacer(Modifier.width(6.dp))
        }

        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            // Sender name (colored, only for others)
            if (!isMe && msg.senderHandle != null) {
                Text(
                    text = "@${msg.senderHandle}",
                    color = senderColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 10.dp, bottom = 1.dp),
                )
            }

            // Message bubble — matches ChatScreen style
            Card(
                shape = RoundedCornerShape(
                    topStart = if (isMe) 18.dp else 4.dp,
                    topEnd = if (isMe) 4.dp else 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 18.dp,
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isMe) C.accent.copy(alpha = 0.15f) else C.card,
                ),
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = msg.text ?: "",
                        color = Color.White,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                    )
                    Text(
                        text = timeFormat.format(Date(msg.timestamp * 1000)),
                        color = C.textMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                    )
                }
            }
        }

        if (isMe) {
            Spacer(Modifier.width(4.dp))
        }
    }
}
