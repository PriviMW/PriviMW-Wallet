package com.privimemobile.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.RingtoneManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.chat.ChatService
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactInfoScreen(
    handle: String,
    onBack: () -> Unit,
    onMediaGallery: () -> Unit = {},
    onDeleteChat: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val convKey = "@$handle"

    // Observe conversation
    val conversations by ChatService.db?.conversationDao()?.observeAll()
        ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val conv = conversations.firstOrNull { it.convKey == convKey }
    val convId = conv?.id ?: 0L

    // Observe contact
    val contacts by ChatService.db?.contactDao()?.observeAll()
        ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val contact = contacts.firstOrNull { it.handle == handle }

    val displayName = contact?.displayName?.ifEmpty { null } ?: conv?.displayName?.ifEmpty { null }
    val resolvedName = displayName ?: "@$handle"
    val walletId = contact?.walletId

    // Attachment counts
    var totalAttachments by remember { mutableStateOf(0) }
    var imageAttachments by remember { mutableStateOf(0) }
    var linkCount by remember { mutableStateOf(0) }
    LaunchedEffect(convId) {
        if (convId > 0L) {
            totalAttachments = ChatService.db?.attachmentDao()?.countByConversation(convId) ?: 0
            imageAttachments = ChatService.db?.attachmentDao()?.countImagesByConversation(convId) ?: 0
            linkCount = ChatService.db?.messageDao()?.countLinksInConversation(convId) ?: 0
        }
    }

    // States
    val isMuted = conv?.muted ?: false
    val isBlocked = conv?.isBlocked ?: false
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Notification sound
    val prefs = context.getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)
    var notifSoundName by remember {
        mutableStateOf(prefs.getString("notif_sound_name_$convKey", "Default") ?: "Default")
    }
    val soundPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.getParcelableExtra<android.net.Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        // Bump channel version to force new Android notification channel with new sound
        val ver = prefs.getInt("notif_channel_ver_$convKey", 0) + 1
        if (uri != null) {
            val ringtone = RingtoneManager.getRingtone(context, uri)
            val name = ringtone?.getTitle(context) ?: "Custom"
            prefs.edit().putString("notif_sound_$convKey", uri.toString())
                .putString("notif_sound_name_$convKey", name)
                .putInt("notif_channel_ver_$convKey", ver).apply()
            notifSoundName = name
        } else {
            // "Silent" was picked (null URI)
            prefs.edit().putString("notif_sound_$convKey", "silent")
                .putString("notif_sound_name_$convKey", "Silent")
                .putInt("notif_channel_ver_$convKey", ver).apply()
            notifSoundName = "Silent"
        }
    }

    // Avatar
    val avatarColors = listOf(
        Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFEF5350), Color(0xFFAB47BC),
        Color(0xFF42A5F5), Color(0xFFFF7043), Color(0xFF66BB6A), Color(0xFFEC407A),
    )
    val avatarBg = avatarColors[abs(handle.hashCode()) % avatarColors.size]
    val initial = resolvedName.removePrefix("@").firstOrNull()?.uppercase() ?: "?"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg)
    ) {
        // Top bar
        TopAppBar(
            title = { Text("Contact Info", color = C.text) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = C.text)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = C.bg),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // Avatar + name header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(avatarBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(initial, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Text(resolvedName, color = C.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("@$handle", color = C.textSecondary, fontSize = 14.sp)
            }

            // Wallet ID (copyable)
            if (!walletId.isNullOrEmpty()) {
                InfoSection("Wallet ID") {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = C.card,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("wallet_id", walletId))
                                Toast.makeText(context, "Copied wallet ID", Toast.LENGTH_SHORT).show()
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                walletId,
                                color = C.text, fontSize = 11.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.ContentCopy, "Copy", tint = C.textSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Media
            InfoSection("Shared Media") {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = C.card,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        InfoRow(
                            icon = Icons.Default.PhotoLibrary,
                            label = "Photos",
                            value = "$imageAttachments",
                            onClick = if (imageAttachments > 0) onMediaGallery else null,
                        )
                        if (totalAttachments > imageAttachments) {
                            HorizontalDivider(color = C.border.copy(alpha = 0.5f))
                            InfoRow(
                                icon = Icons.Default.InsertDriveFile,
                                label = "Files",
                                value = "${totalAttachments - imageAttachments}",
                            )
                        }
                        if (linkCount > 0) {
                            HorizontalDivider(color = C.border.copy(alpha = 0.5f))
                            InfoRow(
                                icon = Icons.Default.Link,
                                label = "Links",
                                value = "$linkCount",
                            )
                        }
                    }
                }
            }

            // Settings
            InfoSection("Settings") {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = C.card,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        // Mute
                        InfoRow(
                            icon = if (isMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                            label = if (isMuted) "Unmute" else "Mute",
                            onClick = {
                                if (convId > 0L) {
                                    scope.launch {
                                        ChatService.db?.conversationDao()?.setMuted(convId, !isMuted)
                                    }
                                }
                            },
                        )
                        HorizontalDivider(color = C.border.copy(alpha = 0.5f))

                        // Notification sound
                        InfoRow(
                            icon = Icons.Default.MusicNote,
                            label = "Notification sound",
                            value = notifSoundName,
                            onClick = {
                                val intent = android.content.Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Notification sound")
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    val currentUri = prefs.getString("notif_sound_$convKey", null)
                                    if (!currentUri.isNullOrEmpty() && currentUri != "silent") {
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, android.net.Uri.parse(currentUri))
                                    }
                                }
                                soundPickerLauncher.launch(intent)
                            },
                        )
                        HorizontalDivider(color = C.border.copy(alpha = 0.5f))

                        // Block
                        InfoRow(
                            icon = Icons.Default.Block,
                            label = if (isBlocked) "Unblock" else "Block",
                            labelColor = if (!isBlocked) C.error else C.text,
                            onClick = {
                                if (convId > 0L) {
                                    scope.launch {
                                        ChatService.db?.conversationDao()?.setBlocked(convId, !isBlocked)
                                    }
                                }
                            },
                        )
                        HorizontalDivider(color = C.border.copy(alpha = 0.5f))

                        // Delete chat
                        InfoRow(
                            icon = Icons.Default.Delete,
                            label = "Delete chat",
                            labelColor = C.error,
                            onClick = { showDeleteConfirm = true },
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // Delete confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = C.card,
            title = { Text("Delete chat", color = C.text, fontWeight = FontWeight.SemiBold) },
            text = { Text("Delete this entire conversation?", color = C.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    if (convId > 0L) {
                        scope.launch {
                            ChatService.db?.messageDao()?.softDeleteByConversation(convId)
                            ChatService.db?.conversationDao()?.softDelete(convId)
                        }
                    }
                    showDeleteConfirm = false
                    onDeleteChat()
                }) {
                    Text("Delete", color = C.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = C.textSecondary)
                }
            },
        )
    }

}

@Composable
private fun InfoSection(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        color = C.textSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp, start = 4.dp),
    )
    content()
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String? = null,
    labelColor: Color = C.text,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = C.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = labelColor, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, color = C.textSecondary, fontSize = 14.sp)
        }
        if (onClick != null) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.ChevronRight, "Navigate", tint = C.textMuted, modifier = Modifier.size(18.dp))
        }
    }
}
