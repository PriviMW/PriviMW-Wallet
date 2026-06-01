package com.privimemobile.ui.chat.dialogs

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.R
import com.privimemobile.chat.db.entities.ContactEntity
import com.privimemobile.ui.chat.ChatForwardState
import com.privimemobile.ui.components.AvatarDisplay
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatForwardPickerDialog(
    forward: ChatForwardState,
    allContacts: List<ContactEntity>,
    onNavigateToChat: (String) -> Unit,
) {
    val context = LocalContext.current
    if (forward.isPickerOpen) {
        val fwdMsg = forward.forwardingMsg!!
        val allFwdMsgs = forward.messagesToForward()
        val chatState by com.privimemobile.chat.ChatService.observeState().collectAsState(initial = null)
        val myHandle = chatState?.myHandle
        val forwardContacts = remember(allContacts, myHandle) {
            allContacts.filter { it.handle != myHandle && !it.walletId.isNullOrEmpty() && !it.isDeleted }
        }
        val forwardGroups by com.privimemobile.chat.ChatService.db?.groupDao()?.observeAll()
            ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }

        AlertDialog(
            onDismissRequest = { forward.dismiss() },
            containerColor = C.card,
            title = { Text(stringResource(R.string.chat_forward_to, if (allFwdMsgs.size > 1) "${allFwdMsgs.size} messages" else ""), color = C.text, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(modifier = Modifier.heightIn(max = 400.dp)) {
                    // Preview of message(s) being forwarded
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = C.bg.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            if (allFwdMsgs.size > 1) {
                                Text(
                                    context.getString(R.string.chat_messages_selected, allFwdMsgs.size),
                                    color = C.textSecondary,
                                    fontSize = 12.sp,
                                )
                            } else {
                                if (fwdMsg.file != null) {
                                    Text(
                                        stringResource(R.string.chat_forward_file_msg, fwdMsg.file.name.ifEmpty { "" }),
                                        color = C.accent,
                                        fontSize = 12.sp,
                                    )
                                }
                                if (fwdMsg.text.isNotEmpty()) {
                                    Text(
                                        fwdMsg.text.take(100),
                                        color = C.textSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }

                    if (forwardContacts.isEmpty() && forwardGroups.isEmpty()) {
                        Text(stringResource(R.string.chat_no_contacts), color = C.textSecondary, fontSize = 14.sp)
                    } else {
                        LazyColumn {
                            // Groups section (hidden for multi-forward — SBBS reliability)
                            if (forwardGroups.isNotEmpty() && allFwdMsgs.size <= 1) {
                                item { Text(stringResource(R.string.chat_section_groups), color = C.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp)) }
                                items(forwardGroups, key = { "g_${it.groupId}" }) { grp ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val targetGid = grp.groupId
                                                val targetName = grp.name
                                                val msgs = allFwdMsgs.toList()
                                                com.privimemobile.chat.ChatService.scope.launch {
                                                    val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
                                                    if (state?.myHandle != null) {
                                                        val m = msgs.first()
                                                        val fwdFrom = if (m.sent) state.myHandle!! else m.from
                                                        val isFile = m.file != null
                                                        val ts = System.currentTimeMillis() / 1000

                                                        if (isFile) {
                                                            val f = m.file!!
                                                            val fileMeta = mutableMapOf<String, Any?>(
                                                                "name" to f.name, "size" to f.size,
                                                                "mime" to f.mime, "key" to f.key, "iv" to f.iv,
                                                            )
                                                            if (f.cid.isNotEmpty() && !f.cid.startsWith("inline-")) fileMeta["cid"] = f.cid
                                                            if (f.data != null) fileMeta["data"] = f.data

                                                            val payload = mutableMapOf<String, Any?>(
                                                                "v" to 1, "t" to "file", "ts" to ts,
                                                                "from" to state.myHandle, "to" to targetGid,
                                                                "dn" to (state.myDisplayName ?: ""),
                                                                "file" to fileMeta,
                                                                "fwd_from" to fwdFrom,
                                                                "fwd_ts" to m.timestamp,
                                                            )
                                                            if (m.text.isNotEmpty()) payload["msg"] = m.text

                                                            val convId = com.privimemobile.chat.ChatService.groups.getOrCreateGroupConversation(targetGid, targetName)
                                                            val dedupKey = "$ts:fwd_file:${m.timestamp}:true"
                                                            val entity = com.privimemobile.chat.db.entities.MessageEntity(
                                                                conversationId = convId,
                                                                text = if (m.text.isNotEmpty()) m.text else null,
                                                                timestamp = ts,
                                                                sent = true,
                                                                type = "file",
                                                                senderHandle = state.myHandle,
                                                                sbbsDedupKey = dedupKey,
                                                                fwdFrom = fwdFrom,
                                                                fwdTs = m.timestamp,
                                                            )
                                                            val msgId = com.privimemobile.chat.ChatService.db!!.messageDao().insert(entity)
                                                            if (msgId > 0) {
                                                                com.privimemobile.chat.ChatService.db!!.attachmentDao().insert(
                                                                    com.privimemobile.chat.db.entities.AttachmentEntity(
                                                                        messageId = msgId,
                                                                        conversationId = convId,
                                                                        ipfsCid = f.cid.ifEmpty { "inline-${System.currentTimeMillis().toString(36)}" },
                                                                        encryptionKey = f.key,
                                                                        encryptionIv = f.iv,
                                                                        fileName = f.name,
                                                                        fileSize = f.size,
                                                                        mimeType = f.mime,
                                                                        inlineData = f.data,
                                                                    )
                                                                )
                                                            }
                                                            val preview = if (m.text.isNotEmpty()) context.getString(R.string.chat_forward_file_msg, f.name.ifEmpty { "" }) else context.getString(R.string.chat_forward_file_msg, f.name.ifEmpty { "" })
                                                            com.privimemobile.chat.ChatService.db?.groupDao()?.updateLastMessage(targetGid, ts, preview)

                                                            com.privimemobile.chat.ChatService.groups.sendGroupPayload(targetGid, payload)
                                                        } else {
                                                            Log.d("ChatScreen", "Forward to group $targetGid: text=${m.text.take(20)}")
                                                            com.privimemobile.chat.ChatService.groups.sendGroupMessage(targetGid, m.text, fwdFrom = fwdFrom, fwdTs = m.timestamp)
                                                        }

                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(context, context.getString(R.string.toast_forwarded_to, targetName), Toast.LENGTH_SHORT).show()
                                                        }
                                                    } else {
                                                        Log.w("ChatScreen", "Forward failed: myHandle is null")
                                                    }
                                                }
                                                forward.dismiss()
                                                onNavigateToChat("group:$targetGid")
                                            }
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        val groupAvatarBmp = remember(grp.groupId) {
                                            try {
                                                val f = java.io.File(context.filesDir, "group_avatars/${grp.groupId}.webp")
                                                if (f.exists()) android.graphics.BitmapFactory.decodeFile(f.absolutePath) else null
                                            } catch (_: Exception) { null }
                                        }
                                        if (groupAvatarBmp != null) {
                                            androidx.compose.foundation.Image(
                                                bitmap = groupAvatarBmp.asImageBitmap(),
                                                contentDescription = grp.name,
                                                modifier = Modifier.size(36.dp).clip(CircleShape),
                                                contentScale = ContentScale.Crop,
                                            )
                                        } else {
                                            Box(
                                                Modifier.size(36.dp).clip(CircleShape).background(C.accent),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(Icons.Default.Group, null, tint = C.textDark, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(grp.name, color = C.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                            Text("${grp.memberCount} members", color = C.textSecondary, fontSize = 12.sp)
                                        }
                                    }
                                    HorizontalDivider(color = C.border, thickness = 0.5.dp)
                                }
                                item { Spacer(Modifier.height(8.dp)) }
                            }
                            // Contacts section
                            if (forwardContacts.isNotEmpty()) {
                                item { Text(stringResource(R.string.chat_section_contacts), color = C.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp)) }
                            }
                            items(forwardContacts, key = { it.handle }) { contact ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            // Send forwarded message(s) — use ChatService scope so it survives navigation
                                            com.privimemobile.chat.ChatService.scope.launch {
                                                val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
                                                if (state?.myHandle != null) {
                                                    val toHandle = contact.handle
                                                    val toConvKey = "@$toHandle"
                                                    val fwdConv = com.privimemobile.chat.ChatService.db!!.conversationDao().getOrCreate(toConvKey, toHandle)
                                                    val sendAddr = com.privimemobile.chat.DmAddressResolver.resolve(contact, fwdConv)
                                                        ?: return@launch
                                                    var lastPreview: String? = null
                                                    var lastTs = 0L
                                                    for ((i, m) in allFwdMsgs.withIndex()) {
                                                        val ts = System.currentTimeMillis() / 1000 + i  // offset to avoid dedup collision
                                                        val isFile = m.file != null
                                                        val msgType = if (isFile) "file" else "dm"
                                                        val fwdFrom = if (m.sent) state.myHandle!! else m.from
                                                        val payload = mutableMapOf<String, Any?>(
                                                            "v" to 1, "t" to msgType, "ts" to ts,
                                                            "from" to state.myHandle!!, "to" to toHandle,
                                                            "dn" to (state.myDisplayName ?: ""),
                                                            "fwd_from" to fwdFrom,
                                                            "fwd_ts" to m.timestamp,
                                                        )
                                                        if (m.text.isNotEmpty()) payload["msg"] = m.text
                                                        if (isFile) {
                                                            val f = m.file!!
                                                            val fileMap = mutableMapOf<String, Any?>(
                                                                "name" to f.name, "size" to f.size,
                                                                "mime" to f.mime, "key" to f.key, "iv" to f.iv,
                                                            )
                                                            if (f.cid.isNotEmpty() && !f.cid.startsWith("inline-")) fileMap["cid"] = f.cid
                                                            if (f.data != null) fileMap["data"] = f.data
                                                            payload["file"] = fileMap
                                                        }
                                                        val dedupKey = "$ts:fwd:${m.timestamp}:true"
                                                        val entity = com.privimemobile.chat.db.entities.MessageEntity(
                                                            conversationId = fwdConv.id,
                                                            text = m.text.ifEmpty { null },
                                                            timestamp = ts,
                                                            sent = true,
                                                            type = msgType,
                                                            senderHandle = state.myHandle,
                                                            sbbsDedupKey = dedupKey,
                                                            fwdFrom = fwdFrom,
                                                            fwdTs = m.timestamp,
                                                        )
                                                        val msgId = com.privimemobile.chat.ChatService.db!!.messageDao().insert(entity)
                                                        if (isFile && msgId != -1L) {
                                                            val f = m.file!!
                                                            com.privimemobile.chat.ChatService.db!!.attachmentDao().insert(
                                                                com.privimemobile.chat.db.entities.AttachmentEntity(
                                                                    messageId = msgId,
                                                                    conversationId = fwdConv.id,
                                                                    ipfsCid = f.cid.ifEmpty { "inline-${System.currentTimeMillis().toString(36)}" },
                                                                    encryptionKey = f.key,
                                                                    encryptionIv = f.iv,
                                                                    fileName = f.name,
                                                                    fileSize = f.size,
                                                                    mimeType = f.mime,
                                                                    inlineData = f.data,
                                                                )
                                                            )
                                                        }
                                                        lastPreview = if (isFile) context.getString(R.string.chat_forward_file_msg, m.file!!.name.ifEmpty { "" }) else m.text.take(100)
                                                        lastTs = ts
                                                        com.privimemobile.chat.ChatService.sbbs.sendWithRetry(sendAddr, payload)
                                                        // Delay between forwards to avoid SBBS rate-limiting
                                                        if (i < allFwdMsgs.size - 1) delay(2000)
                                                    }
                                                    if (lastTs > 0) {
                                                        com.privimemobile.chat.ChatService.db!!.conversationDao().updateLastMessage(fwdConv.id, lastTs, lastPreview)
                                                    }
                                                    val count = allFwdMsgs.size
                                                    Toast.makeText(context, context.getString(R.string.toast_forwarded_count, count, if (count > 1) "s" else "", toHandle), Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            forward.dismiss()
                                            // Navigate to the forwarded-to chat
                                            onNavigateToChat(contact.handle)
                                        }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    com.privimemobile.ui.components.AvatarDisplay(
                                        handle = contact.handle,
                                        displayName = contact.displayName,
                                        size = 36.dp,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            contact.displayName?.ifEmpty { null } ?: contact.handle,
                                            color = C.text,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            "@${contact.handle}",
                                            color = C.textSecondary,
                                            fontSize = 12.sp,
                                        )
                                    }
                                }
                                HorizontalDivider(color = C.border, thickness = 0.5.dp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }
}
