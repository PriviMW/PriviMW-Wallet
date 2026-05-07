package com.privimemobile.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mw.beam.beamwallet.core.Api
import com.privimemobile.protocol.Config
import com.privimemobile.protocol.Helpers
import com.privimemobile.protocol.LocaleHelper
import com.privimemobile.protocol.NodeReconnect
// Old ProtocolStartup removed — using ChatService for identity
import com.privimemobile.protocol.ProtocolStorage
import com.privimemobile.protocol.SecureStorage
import com.privimemobile.protocol.ShaderInvoker
import com.privimemobile.protocol.WalletApi
import com.privimemobile.R
import com.privimemobile.ui.theme.C
import com.privimemobile.wallet.BackgroundService
import com.privimemobile.wallet.CurrencyManager
import com.privimemobile.wallet.WalletEventBus
import com.privimemobile.wallet.WalletManager
import com.privimemobile.wallet.NodeConnectionEvent
import com.privimemobile.wallet.SyncProgressEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full settings screen — ports ALL 6 sections from the RN SettingsScreen.tsx:
 * General, Notifications, Node, Privacy, Utilities, About + Remove Wallet
 *
 * Includes all RN features: owner key export, max privacy time options,
 * export/import, rescan with progress, public offline address, IPFS test,
 * block height display.
 */
@Composable
fun SettingsScreen(
    onNavigateAddresses: () -> Unit = {},
    onNavigateUtxo: () -> Unit = {},
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val chatState by com.privimemobile.chat.ChatService.observeState().collectAsState(initial = null)
    val nodeConn by WalletEventBus.nodeConnection.collectAsState(initial = NodeConnectionEvent(false))
    val syncProgress by WalletEventBus.syncProgress.collectAsState()
    val walletStatus by WalletEventBus.beamStatus.collectAsState()

    // Lib info
    val libVersion = remember { try { Api.getLibVersion() } catch (_: Exception) { "unknown" } }
    var ipfsPeers by remember { mutableIntStateOf(try { Api.getIPFSPeerCount() } catch (_: Exception) { -1 }) }
    var ipfsStatus by remember { mutableStateOf("checking") } // checking, online, offline

    // Settings state
    var confirmationsOffset by remember { mutableIntStateOf(0) }
    var askPasswordOnSend by remember { mutableStateOf(SecureStorage.getBoolean(SecureStorage.KEY_ASK_PASSWORD_ON_SEND, true)) }
    var biometricsEnabled by remember { mutableStateOf(SecureStorage.getBoolean(SecureStorage.KEY_FINGERPRINT_ENABLED)) }
    var bgServiceEnabled by remember { mutableStateOf(SecureStorage.getBoolean("bg_service_enabled", true)) }
    var walletUpdatesNotif by remember { mutableStateOf(SecureStorage.getBoolean("wallet_updates_notif", true)) }
    var txStatusNotif by remember { mutableStateOf(SecureStorage.getBoolean("tx_status_notif", true)) }
    var maxPrivacyHours by remember { mutableIntStateOf(SecureStorage.getInt("max_privacy_hours", 72)) }

    // Node settings
    var nodeMode by remember { mutableStateOf(SecureStorage.getString("node_mode") ?: "random") }
    var showNodeInput by remember { mutableStateOf(false) }
    var nodeInput by remember { mutableStateOf(SecureStorage.getString("own_node_address") ?: "") }
    var changingNode by remember { mutableStateOf(false) }

    // Privacy - change password
    var showChangePassword by remember { mutableStateOf(false) }
    var currentPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }

    // Owner key
    var showOwnerKey by remember { mutableStateOf(false) }
    var ownerKeyPass by remember { mutableStateOf("") }

    // Delete wallet
    var showDeleteWallet by remember { mutableStateOf(false) }
    var deleteConfirmText by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf(false) }

    // Import wallet data
    var showImportConfirm by remember { mutableStateOf(false) }
    var importFileName by remember { mutableStateOf("") }
    var importFileContent by remember { mutableStateOf("") }

    // Owner key result dialog
    var ownerKeyResult by remember { mutableStateOf("") }

    // Rescan confirmation dialog
    var showRescanConfirm by remember { mutableStateOf(false) }

    // Public offline address dialog
    var publicAddress by remember { mutableStateOf("") }

    // IPFS test result dialog
    var ipfsTestResult by remember { mutableStateOf("") }
    var ipfsTestTitle by remember { mutableStateOf("") }

    // Registration fee — guard against ChatService not yet initialized
    val registrationFee by if (com.privimemobile.chat.ChatService.initialized.value)
        com.privimemobile.chat.ChatService.identity.registrationFee.collectAsState()
    else remember { mutableStateOf(0L) }

    fun toast(msg: String) { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }

    fun saveToDownloads(filename: String, mimeType: String, data: ByteArray) {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, filename)
            put(android.provider.MediaStore.Downloads.MIME_TYPE, mimeType)
            put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(data) }
            toast(context.getString(R.string.tx_history_export_toast_saved, filename))
        } else {
            toast(context.getString(R.string.tx_history_export_toast_failed))
        }
    }

    // File picker launcher for wallet data import
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val content = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
            inputStream?.close()
            if (content.trim().isEmpty()) {
                toast(context.getString(R.string.settings_file_empty))
                return@rememberLauncherForActivityResult
            }
            // Validate it's valid JSON
            try {
                org.json.JSONObject(content)
            } catch (_: Exception) {
                try {
                    org.json.JSONArray(content)
                } catch (_: Exception) {
                    toast(context.getString(R.string.settings_invalid_json))
                    return@rememberLauncherForActivityResult
                }
            }
            // Extract filename from URI
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            val nameIndex = cursor?.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME) ?: -1
            val name = if (cursor != null && nameIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)
            } else {
                uri.lastPathSegment ?: context.getString(R.string.settings_file_unnamed)
            }
            cursor?.close()

            importFileName = name
            importFileContent = content
            showImportConfirm = true
        } catch (e: Exception) {
            toast(context.getString(R.string.settings_file_read_failed, e.message ?: ""))
        }
    }

    // Listen for public address events
    LaunchedEffect(Unit) {
        WalletEventBus.addresses.collect { event ->
            if (event.own && event.json.length > 20 && !event.json.startsWith("[")) {
                publicAddress = event.json
            }
        }
    }

    // Listen for wallet JSON backup export and save to Downloads
    LaunchedEffect(Unit) {
        WalletEventBus.exportData.collect { event ->
            if (event.type != "json") return@collect
            try {
                val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                val filename = "privimw-backup-$date.json"
                saveToDownloads(filename, "application/json", event.data.toByteArray())
            } catch (e: Throwable) {
                toast(context.getString(R.string.toast_export_failed, e.message))
            }
        }
    }

    // Listen for TX history CSV bundle (all types accumulated) and save as ZIP to Downloads
    LaunchedEffect(Unit) {
        WalletEventBus.exportCsvBundle.collect { csvs ->
            try {
                val date = java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                val zipFilename = "privimw-tx-history-$date.zip"
                val csvFilenames = mapOf(
                    "transactions" to "transactions.csv",
                    "atomic_swap" to "atomic_swap_transactions.csv",
                    "assets_swap" to "assets_swap_transactions.csv",
                    "contracts" to "contracts_transactions.csv",
                )
                // Build ZIP in memory
                val baos = java.io.ByteArrayOutputStream()
                java.util.zip.ZipOutputStream(baos).use { zos ->
                    for ((key, csv) in csvs) {
                        val entry = java.util.zip.ZipEntry(csvFilenames[key] ?: "$key.csv")
                        zos.putNextEntry(entry)
                        zos.write(csv.toByteArray())
                        zos.closeEntry()
                    }
                }
                saveToDownloads(zipFilename, "application/zip", baos.toByteArray())
            } catch (e: Throwable) {
                toast(context.getString(R.string.toast_export_failed, e.message))
            }
        }
    }

    // Listen for wallet import result
    LaunchedEffect(Unit) {
        WalletEventBus.walletEvent.collect { event ->
            when (event) {
                "import_ok" -> toast(context.getString(R.string.settings_wallet_import_ok))
                "import_failed" -> toast(context.getString(R.string.settings_wallet_import_failed))
            }
        }
    }

    // Load settings on mount
    LaunchedEffect(Unit) {
        confirmationsOffset = SecureStorage.getInt("confirmations_offset", 0)
        // Push cached settings to native
        val wallet = WalletManager.walletInstance
        if (confirmationsOffset > 0) {
            try { wallet?.setCoinConfirmationsOffset(confirmationsOffset.toLong()) } catch (_: Exception) {}
        }
        if (maxPrivacyHours > 0) {
            try { wallet?.setMaxPrivacyLockTimeLimitHours(maxPrivacyHours.toLong()) } catch (_: Exception) {}
        }
        // Check IPFS status
        try {
            val count = Api.getIPFSPeerCount()
            ipfsPeers = count
            ipfsStatus = if (count > 0) "online" else "offline"
        } catch (_: Exception) {
            ipfsStatus = "offline"
        }
    }

    // Sync status
    val isSyncing = syncProgress.total > 0 && syncProgress.done < syncProgress.total
    val syncPercent = if (syncProgress.total > 0)
        (syncProgress.done.toFloat() / syncProgress.total * 100).toInt().coerceAtMost(100) else 0

    val syncStatus = when {
        !nodeConn.connected -> if (walletStatus.height > 0) stringResource(R.string.wallet_reconnecting) else stringResource(R.string.wallet_connecting_node)
        isSyncing && syncProgress.total > 0 ->
            stringResource(R.string.settings_sync_progress, syncPercent, syncProgress.done / 1000, syncProgress.total / 1000)
        isSyncing -> stringResource(R.string.wallet_syncing)
        else -> stringResource(R.string.wallet_connected)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(stringResource(R.string.settings_title), color = C.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        // ========== PRIVIME PROFILE ==========
        SectionTitle(stringResource(R.string.settings_section_privime))
        SettingsCard {
            if (chatState?.myHandle != null) {
                SettingsRow(stringResource(R.string.settings_display_name_label), chatState!!.myDisplayName?.ifEmpty { stringResource(R.string.settings_none_placeholder) } ?: stringResource(R.string.settings_none_placeholder))
                SettingsRow(stringResource(R.string.settings_handle_label), "@${chatState!!.myHandle}")
                SettingsRow(stringResource(R.string.contact_wallet_id), Helpers.truncateKey(chatState!!.myWalletId ?: ""))
                SettingsRow(stringResource(R.string.settings_registered_label), stringResource(R.string.wallet_block_height, chatState!!.myRegisteredHeight))

                HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 8.dp))

                // Profile Picture (above display name)
                Text(stringResource(R.string.settings_profile_picture), color = C.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 8.dp))
                var avatarUploading by remember { mutableStateOf(false) }
                var avatarVersion by remember { mutableStateOf(0) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val myAvatarPath = remember(avatarVersion) { java.io.File(context.filesDir, "my_avatar.webp").let { if (it.exists()) it.absolutePath else null } }
                    com.privimemobile.ui.components.AvatarPicker(
                        currentAvatarPath = myAvatarPath,
                        initialLetter = chatState?.myDisplayName?.take(1) ?: chatState?.myHandle?.take(1) ?: "?",
                        size = 80.dp,
                        cacheVersion = avatarVersion,
                    ) { result ->
                        java.io.File(context.filesDir, "my_avatar.webp").writeBytes(result.bytes)
                        avatarVersion++
                        toast(context.getString(R.string.toast_profile_picture_updated))
                        scope.launch {
                            com.privimemobile.chat.ChatService.db?.chatStateDao()?.updateAvatarHash(result.hashHex)
                            distributeAvatarToContacts(context, result.bytes, result.hashHex)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(stringResource(R.string.settings_tap_to_change), color = C.textSecondary, fontSize = 13.sp)
                        if (avatarUploading) {
                            Spacer(Modifier.height(4.dp))
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = C.accent, strokeWidth = 2.dp)
                        }
                        if (chatState?.myAvatarCid != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.settings_remove), color = C.error, fontSize = 13.sp,
                                modifier = Modifier.clickable {
                                    java.io.File(context.filesDir, "my_avatar.webp").delete()
                                    avatarVersion++
                                    scope.launch { com.privimemobile.chat.ChatService.db?.chatStateDao()?.updateAvatarHash(null) }
                                    toast(context.getString(R.string.toast_profile_picture_removed))
                                })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                // Edit Display Name
                var showEditName by remember { mutableStateOf(false) }
                var newDisplayName by remember { mutableStateOf(chatState?.myDisplayName ?: "") }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showEditName = !showEditName }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.settings_edit_display_name), color = C.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                if (showEditName) {
                    var updating by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = newDisplayName,
                        onValueChange = { if (it.toByteArray(Charsets.UTF_8).size <= 64) newDisplayName = it },
                        label = { Text(stringResource(R.string.settings_display_name_label)) },
                        supportingText = { Text("${newDisplayName.toByteArray(Charsets.UTF_8).size}/64 bytes", color = C.textMuted, fontSize = 11.sp) },
                        singleLine = true,
                        enabled = !updating,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = C.accent, unfocusedBorderColor = C.border,
                            focusedLabelColor = C.accent, cursorColor = C.accent,
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            updating = true
                            com.privimemobile.chat.ChatService.identity.updateDisplayName(newDisplayName.trim()) { success, err ->
                                updating = false
                                if (success) {
                                    toast(context.getString(R.string.toast_tx_submitted_display_name))
                                    showEditName = false
                                } else toast(err ?: context.getString(R.string.toast_update_display_name_failed))
                            }
                        },
                        enabled = !updating && newDisplayName.trim() != (chatState?.myDisplayName ?: ""),
                        colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (updating) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = C.textDark, strokeWidth = 2.dp)
                        else Text(stringResource(R.string.settings_update), color = C.textDark, fontWeight = FontWeight.Bold)
                    }
                }

                // Update Messaging Address
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            scope.launch {
                                WalletApi.call("create_address", mapOf("type" to "regular", "expiration" to "never", "comment" to "PriviMe")) { result ->
                                    val addr = result["address"] as? String
                                    if (addr != null) {
                                        val normalized = Helpers.normalizeWalletId(addr) ?: addr
                                        com.privimemobile.chat.ChatService.identity.updateMessagingAddress(normalized) { success, err ->
                                            if (success) toast(context.getString(R.string.toast_tx_submitted_address)) else toast(err ?: context.getString(R.string.toast_update_address_failed))
                                        }
                                    }
                                }
                                toast(context.getString(R.string.toast_updating_address))
                            }
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(stringResource(R.string.settings_update_messaging_addr), color = C.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.settings_update_messaging_addr_desc),
                            color = C.textMuted, fontSize = 12.sp)
                    }
                }

                // Remove Handle
                var showRemoveConfirm by remember { mutableStateOf(false) }
                val myHandle = chatState!!.myHandle ?: ""
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showRemoveConfirm = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(stringResource(R.string.settings_remove_handle), color = C.error, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.settings_remove_handle_desc, myHandle),
                            color = C.textMuted, fontSize = 12.sp)
                    }
                }
                if (showRemoveConfirm) {
                    var removing by remember { mutableStateOf(false) }
                    AlertDialog(
                        onDismissRequest = { if (!removing) showRemoveConfirm = false },
                        title = { Text(stringResource(R.string.settings_remove_handle_title), color = C.text) },
                        text = {
                            Text(stringResource(R.string.settings_remove_handle_confirm, myHandle),
                                color = C.textSecondary)
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    removing = true
                                    // Check if user is a group creator — contract will block deletion
                                    scope.launch {
                                        val createdGroups = com.privimemobile.chat.ChatService.db?.groupDao()?.getAllGroups()
                                            ?.filter { it.myRole == 2 } ?: emptyList()
                                        if (createdGroups.isNotEmpty()) {
                                            removing = false
                                            toast(context.getString(R.string.toast_transfer_groups_first, createdGroups.size))
                                            return@launch
                                        }
                                        com.privimemobile.chat.ChatService.identity.releaseHandle { success, err ->
                                            removing = false
                                            showRemoveConfirm = false
                                            if (success) toast(context.getString(R.string.toast_tx_submitted_remove_handle))
                                            else toast(err ?: context.getString(R.string.toast_remove_handle_failed))
                                        }
                                    }
                                },
                                enabled = !removing,
                                colors = ButtonDefaults.buttonColors(containerColor = C.error),
                            ) {
                                if (removing) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = C.text, strokeWidth = 2.dp)
                                else Text(stringResource(R.string.settings_remove), color = C.text, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRemoveConfirm = false }, enabled = !removing) {
                                Text(stringResource(R.string.general_cancel), color = C.textSecondary)
                            }
                        },
                        containerColor = C.card,
                    )
                }
            } else {
                Text(stringResource(R.string.settings_not_registered) + "\nRegistration fee: ${if (registrationFee > 0) "${com.privimemobile.protocol.Helpers.grothToBeam(registrationFee)} BEAM" else "loading..."}",
                    color = C.textSecondary, fontSize = 13.sp, lineHeight = 20.sp,
                    modifier = Modifier.padding(vertical = 8.dp))
            }
        }

        // ========== APPEARANCE ==========
        SectionTitle(stringResource(R.string.settings_section_appearance))
        SettingsCard {
            val currentTheme = C.currentThemeKey(context)
            var showThemePicker by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showThemePicker = true }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.settings_theme_label), color = C.text, fontSize = 15.sp)
                Text(C.themeDisplayName(currentTheme, context), color = C.accent, fontSize = 15.sp)
            }
            if (showThemePicker) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showThemePicker = false },
                    containerColor = C.card,
                    title = { Text(stringResource(R.string.settings_choose_theme), color = C.text, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                    text = {
                        Column {
                            com.privimemobile.ui.theme.ALL_THEMES.keys.toList().forEach { key ->
                                val name = C.themeDisplayName(key, context)
                                val colors = com.privimemobile.ui.theme.ALL_THEMES[key]!!
                                val isSelected = key == currentTheme
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            C.applyTheme(key, context)
                                            showThemePicker = false
                                        }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // Color preview dots
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf(colors.bg, colors.card, colors.accent).forEach { c ->
                                            Box(
                                                Modifier
                                                    .size(16.dp)
                                                    .background(c, shape = CircleShape)
                                                    .then(
                                                        if (c == Color(0xFFFFFFFF) || c == Color(0xFFF5F5F5))
                                                            Modifier.border(1.dp, Color(0xFFCCCCCC), CircleShape)
                                                        else Modifier
                                                    )
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        name,
                                        color = if (isSelected) C.accent else C.text,
                                        fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                                        fontSize = 15.sp,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (isSelected) {
                                        Text("\u2713", color = C.accent, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {},
                )
            }
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 8.dp))
            // Language picker
            var showLanguagePicker by remember { mutableStateOf(false) }
            val currentLanguage = remember { LocaleHelper.getSelectedLanguageDisplay() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLanguagePicker = true }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.settings_language), color = C.text, fontSize = 15.sp)
                Text(currentLanguage, color = C.accent, fontSize = 15.sp)
            }
            if (showLanguagePicker) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showLanguagePicker = false },
                    containerColor = C.card,
                    title = { Text(stringResource(R.string.settings_choose_language), color = C.text, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                        ) {
                            LocaleHelper.supportedLanguages.forEach { (code, name) ->
                                val isSelected = code == LocaleHelper.getSelectedLanguage()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (code != LocaleHelper.getSelectedLanguage()) {
                                                LocaleHelper.setLanguage(code)
                                                showLanguagePicker = false
                                                // Delay recreate so dialog dismiss animation finishes
                                                // before the activity restarts. Prevents focus issues
                                                // on the lock screen after restart.
                                                android.os.Handler(context.mainLooper).postDelayed({
                                                    (context as? android.app.Activity)?.recreate()
                                                }, 350)
                                            } else {
                                                showLanguagePicker = false
                                            }
                                        }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        name,
                                        color = if (isSelected) C.accent else C.text,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 15.sp,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (isSelected) {
                                        Text("✓", color = C.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {},
                )
            }
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 8.dp))
            // Currency picker
            var showCurrencyPicker by remember { mutableStateOf(false) }
            val currentCurrency = CurrencyManager.getPreferredCurrency()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCurrencyPicker = true }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.settings_currency_label), color = C.text, fontSize = 15.sp)
                Text("${currentCurrency.uppercase()} (${stringResource(CurrencyManager.getLabelResId(currentCurrency))})", color = C.accent, fontSize = 15.sp)
            }
            if (showCurrencyPicker) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showCurrencyPicker = false },
                    containerColor = C.card,
                    title = { Text(stringResource(R.string.settings_choose_currency), color = C.text, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                    text = {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                        ) {
                            CurrencyManager.SUPPORTED.forEach { (code, pair) ->
                                val (_, symbol) = pair
                                val isSelected = code == currentCurrency
                                val currencyName = stringResource(CurrencyManager.getLabelResId(code))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            CurrencyManager.setPreferredCurrency(code)
                                            showCurrencyPicker = false
                                        }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "$symbol ${code.uppercase()}",
                                        color = C.text,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.width(80.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        currencyName,
                                        color = if (isSelected) C.accent else C.text,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 15.sp,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (isSelected) {
                                        Text("\u2713", color = C.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {},
                )
            }
        }

        // ========== NOTIFICATIONS ==========
        SectionTitle(stringResource(R.string.settings_section_notifications))
        SettingsCard {
            SettingsToggle(stringResource(R.string.settings_notif_wallet_updates_label), stringResource(R.string.settings_notif_wallet_updates_desc), walletUpdatesNotif) {
                walletUpdatesNotif = it
                SecureStorage.putBoolean("wallet_updates_notif", it)
            }
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 4.dp))
            SettingsToggle(stringResource(R.string.settings_notif_tx_status_label), stringResource(R.string.settings_notif_tx_status_desc), txStatusNotif) {
                txStatusNotif = it
                SecureStorage.putBoolean("tx_status_notif", it)
            }
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 4.dp))
            SettingsToggle(stringResource(R.string.settings_notif_bg_connection_label), stringResource(R.string.settings_notif_bg_connection_desc), bgServiceEnabled) {
                bgServiceEnabled = it
                SecureStorage.putBoolean("bg_service_enabled", it)
                if (it) {
                    context.startForegroundService(Intent(context, BackgroundService::class.java))
                    toast(context.getString(R.string.settings_bg_service_started))
                } else {
                    context.stopService(Intent(context, BackgroundService::class.java))
                    toast(context.getString(R.string.settings_bg_service_stopped))
                }
            }
        }

        // ========== NODE ==========
        SectionTitle(stringResource(R.string.settings_section_node))
        SettingsCard {
            SettingsRow(stringResource(R.string.general_status), syncStatus,
                valueColor = if (nodeConn.connected) Color(0xFF4CAF50) else C.error)
            SettingsRow(stringResource(R.string.settings_node_block_height),
                if (walletStatus.height > 0) "#${walletStatus.height}" else "-")
            SettingsRow(stringResource(R.string.settings_node_network), stringResource(R.string.settings_node_network_value))
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 8.dp))
            Text(stringResource(R.string.settings_node_connection_mode), color = C.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            var showMobileNodeDisclaimer by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, C.border, RoundedCornerShape(8.dp)),
            ) {
                listOf("random" to stringResource(R.string.settings_node_mode_random), "mobile" to stringResource(R.string.settings_node_mode_mobile), "own" to stringResource(R.string.settings_node_mode_own)).forEach { (mode, label) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (nodeMode == mode) C.accent else C.card)
                            .clickable {
                                when (mode) {
                                    "random" -> {
                                        nodeMode = "random"
                                        showNodeInput = false
                                        SecureStorage.putString("node_mode", "random")
                                        val nodes = Config.MAINNET_NODES.shuffled()
                                        val wallet = WalletManager.walletInstance
                                        val selectedNode = nodes.firstOrNull() ?: Config.DEFAULT_NODE
                                        wallet?.changeNodeAddress(selectedNode)
                                        wallet?.enableBodyRequests(false)
                                        SecureStorage.storeNodeAddress(selectedNode)
                                        toast(context.getString(R.string.settings_connected_to, selectedNode))
                                    }
                                    "mobile" -> {
                                        showNodeInput = false
                                        if (nodeMode != "mobile") {
                                            showMobileNodeDisclaimer = true
                                        }
                                    }
                                    "own" -> {
                                        nodeMode = "own"
                                        showNodeInput = true
                                    }
                                }
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BoxWithConstraints(contentAlignment = Alignment.Center) {
                            val density = LocalDensity.current
                            val availPx = with(density) { maxWidth.toPx() }
                            val pxPerChar = with(density) { (9.sp).toPx() }
                            val fitCount = (availPx / pxPerChar).toInt()
                            val fontSize = when {
                                label.length > (fitCount * 1.0f).toInt() -> 9.sp
                                label.length > (fitCount * 0.82f).toInt() -> 10.sp
                                label.length > (fitCount * 0.68f).toInt() -> 11.sp
                                else -> 13.sp
                            }
                            Text(label, color = if (nodeMode == mode) C.textDark else C.textSecondary,
                                fontSize = fontSize, fontWeight = FontWeight.SemiBold,
                                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            // Mobile node status indicator with sync progress
            if (nodeMode == "mobile") {
                Spacer(Modifier.height(8.dp))
                val syncProgress by com.privimemobile.wallet.WalletEventBus.syncProgress.collectAsState()
                val isSyncing = syncProgress.total > 0 && syncProgress.done < syncProgress.total
                val syncPercent = if (syncProgress.total > 0) (syncProgress.done * 100 / syncProgress.total) else 0
                val isSynced = walletStatus.height > 0 && !isSyncing

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSynced) Color(0x1A66BB6A) else Color(0x1AFFA726),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(8.dp).background(
                                    if (isSynced) Color(0xFF66BB6A) else Color(0xFFFFA726),
                                    CircleShape,
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (isSynced) stringResource(R.string.settings_mobile_node_active)
                                else if (isSyncing) stringResource(R.string.settings_mobile_node_syncing)
                                else stringResource(R.string.settings_mobile_node_connecting),
                                color = if (isSynced) Color(0xFF66BB6A) else Color(0xFFFFA726),
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (isSyncing) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { syncProgress.done.toFloat() / syncProgress.total.toFloat() },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = C.accent,
                                trackColor = C.border,
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.settings_progress_percent, syncPercent), color = C.textSecondary, fontSize = 11.sp)
                                Text("${syncProgress.done / 1000}k / ${syncProgress.total / 1000}k blocks", color = C.textMuted, fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (isSynced) stringResource(R.string.settings_mobile_verifying)
                            else stringResource(R.string.settings_mobile_usable_while_sync),
                            color = C.textMuted, fontSize = 11.sp,
                        )
                    }
                }
            }

            // Mobile node disclaimer dialog
            if (showMobileNodeDisclaimer) {
                AlertDialog(
                    onDismissRequest = { showMobileNodeDisclaimer = false },
                    containerColor = C.card,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, null, tint = C.accent, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_mobile_node_title), color = C.text, fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column {
                            Text(
                                stringResource(R.string.settings_mobile_node_desc),
                                color = C.text, fontSize = 14.sp, lineHeight = 20.sp,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.settings_mobile_tradeoffs), color = C.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            listOf(
                                stringResource(R.string.settings_mobile_tradeoff_1),
                                stringResource(R.string.settings_mobile_tradeoff_2),
                                stringResource(R.string.settings_mobile_tradeoff_3),
                                stringResource(R.string.settings_mobile_tradeoff_4),
                            ).forEach { point ->
                                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text("\u2022 ", color = C.textSecondary, fontSize = 13.sp)
                                    Text(point, color = C.textSecondary, fontSize = 13.sp)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0x1A66BB6A),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    stringResource(R.string.settings_mobile_remote_fallback),
                                    color = Color(0xFF66BB6A), fontSize = 12.sp, lineHeight = 17.sp,
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showMobileNodeDisclaimer = false
                                nodeMode = "mobile"
                                SecureStorage.putString("node_mode", "mobile")
                                // Enable mobile protocol — FlyClient + on-demand body requests
                                val wallet = WalletManager.walletInstance
                                // Keep connected to random node while mobile node syncs
                                val nodes = Config.MAINNET_NODES.shuffled()
                                val selectedNode = nodes.firstOrNull() ?: Config.DEFAULT_NODE
                                wallet?.changeNodeAddress(selectedNode)
                                SecureStorage.storeNodeAddress(selectedNode)
                                // Enable body requests (FlyClient mobile protocol)
                                wallet?.enableBodyRequests(true)
                                toast(context.getString(R.string.toast_mobile_node_enabled))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                            shape = RoundedCornerShape(8.dp),
                        ) { Text(stringResource(R.string.settings_enable), color = C.textDark, fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showMobileNodeDisclaimer = false }) {
                            Text(stringResource(R.string.general_cancel), color = C.textSecondary)
                        }
                    },
                )
            }

            if (showNodeInput) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = nodeInput,
                    onValueChange = { nodeInput = it },
                    placeholder = { Text(stringResource(R.string.settings_node_addr_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = C.accent, unfocusedBorderColor = C.border, cursorColor = C.accent,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showNodeInput = false; nodeMode = SecureStorage.getString("node_mode") ?: "random" }, shape = RoundedCornerShape(8.dp)) {
                        Text(stringResource(R.string.general_cancel), color = C.textSecondary)
                    }
                    Button(
                        onClick = {
                            val addr = nodeInput.trim()
                            if (addr.isNotEmpty()) {
                                val parts = addr.split(":")
                                if (parts.size != 2) {
                                    toast(context.getString(R.string.settings_invalid_node_format))
                                    return@Button
                                }
                                val port = parts[1].toIntOrNull()
                                if (port == null || port !in 1..65535) {
                                    toast(context.getString(R.string.settings_invalid_port))
                                    return@Button
                                }
                                try {
                                    WalletManager.walletInstance?.changeNodeAddress(addr)
                                    WalletManager.walletInstance?.enableBodyRequests(false)
                                    NodeReconnect.clearFallbackNode()
                                    SecureStorage.putString("node_mode", "own")
                                    SecureStorage.putString("own_node_address", addr)
                                    SecureStorage.storeNodeAddress(addr)
                                    showNodeInput = false
                                    toast(context.getString(R.string.settings_connected_to, addr))
                                } catch (e: Exception) {
                                    toast(context.getString(R.string.toast_failed_connect, e.message))
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                    ) {
                        Text(stringResource(R.string.settings_connect), color = C.textDark, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ========== PRIVACY ==========
        SectionTitle(stringResource(R.string.settings_section_privacy))
        SettingsCard {
            var showAuthForAskPass by remember { mutableStateOf(false) }
            SettingsToggle(stringResource(R.string.settings_ask_password_label),
                stringResource(R.string.settings_ask_password_desc), askPasswordOnSend) {
                if (!it) {
                    // Disabling security — require auth first
                    showAuthForAskPass = true
                } else {
                    askPasswordOnSend = true
                    SecureStorage.putBoolean(SecureStorage.KEY_ASK_PASSWORD_ON_SEND, true)
                }
            }
            if (showAuthForAskPass) {
                var authPass by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showAuthForAskPass = false },
                    containerColor = C.card,
                    title = { Text(stringResource(R.string.general_confirm_password), color = C.text) },
                    text = {
                        Column {
                            Text(stringResource(R.string.settings_disable_security_prompt), color = C.textSecondary, fontSize = 13.sp)
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = authPass,
                                onValueChange = { authPass = it },
                                label = { Text(stringResource(R.string.general_password)) },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = C.accent,
                                    unfocusedBorderColor = C.border,
                                    focusedTextColor = C.text,
                                    unfocusedTextColor = C.text,
                                ),
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            if (authPass == SecureStorage.getWalletPassword()) {
                                askPasswordOnSend = false
                                SecureStorage.putBoolean(SecureStorage.KEY_ASK_PASSWORD_ON_SEND, false)
                                showAuthForAskPass = false
                            } else { toast(context.getString(R.string.toast_wrong_password)) }
                        }, colors = ButtonDefaults.buttonColors(containerColor = C.accent)) {
                            Text(stringResource(R.string.general_confirm), color = C.bg)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAuthForAskPass = false }) { Text(stringResource(R.string.general_cancel), color = C.textSecondary) }
                    },
                )
            }
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 4.dp))
            // Check biometric hardware availability
            val biometricManager = remember { BiometricManager.from(context) }
            val biometricAvailable = remember {
                biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                    BiometricManager.BIOMETRIC_SUCCESS
            }

            SettingsToggle(
                label = if (biometricAvailable) stringResource(R.string.settings_biometric_unlock_label) else stringResource(R.string.settings_biometric_unlock_unavailable),
                desc = when {
                    !biometricAvailable -> stringResource(R.string.settings_biometric_no_hardware)
                    biometricsEnabled -> stringResource(R.string.settings_biometric_enabled_desc)
                    else -> stringResource(R.string.settings_biometric_disabled_desc)
                },
                checked = biometricsEnabled,
                onCheckedChange = { enabling ->
                    if (enabling) {
                        // Enabling: verify fingerprint first before storing
                        if (!biometricAvailable) {
                            toast(context.getString(R.string.settings_biometric_not_supported))
                            return@SettingsToggle
                        }
                        val storedPass = SecureStorage.getWalletPassword()
                        if (storedPass.isNullOrEmpty()) {
                            toast(context.getString(R.string.settings_biometric_pwd_not_found))
                            return@SettingsToggle
                        }
                        val activity = context as? FragmentActivity
                        if (activity == null) {
                            toast(context.getString(R.string.settings_biometric_cannot_prompt))
                            return@SettingsToggle
                        }
                        val executor = ContextCompat.getMainExecutor(context)
                        val prompt = BiometricPrompt(activity, executor,
                            object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                    biometricsEnabled = true
                                    SecureStorage.putBoolean(SecureStorage.KEY_FINGERPRINT_ENABLED, true)
                                    toast(context.getString(R.string.settings_biometric_enabled_toast))
                                }
                                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                                        toast(context.getString(R.string.settings_biometric_enroll_failed, errString))
                                    }
                                    // Don't enable — user cancelled or error
                                }
                                override fun onAuthenticationFailed() {
                                    // Single attempt failed — prompt stays open for retry
                                }
                            })
                        val promptInfo = BiometricPrompt.PromptInfo.Builder()
                            .setTitle(context.getString(R.string.settings_biometric_enable_title))
                            .setSubtitle(context.getString(R.string.settings_biometric_enable_subtitle))
                            .setNegativeButtonText(context.getString(R.string.general_cancel))
                            .build()
                        prompt.authenticate(promptInfo)
                    } else {
                        // Disabling: no confirmation needed
                        biometricsEnabled = false
                        SecureStorage.putBoolean(SecureStorage.KEY_FINGERPRINT_ENABLED, false)
                        toast(context.getString(R.string.settings_biometric_disabled_toast))
                    }
                },
                disabled = !biometricAvailable,
            ) // end biometric toggle
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 8.dp))

            // Max privacy time limit — functional (matches RN)
            Text(stringResource(R.string.settings_privacy_time_limit), color = C.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.settings_max_privacy_desc),
                color = C.textMuted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            OptionRow(
                options = listOf(stringResource(R.string.settings_privacy_no_limit) to 0, "24h" to 24, "72h" to 72),
                selected = maxPrivacyHours,
                onSelect = { hours ->
                    try {
                        WalletManager.walletInstance?.setMaxPrivacyLockTimeLimitHours(hours.toLong())
                        maxPrivacyHours = hours
                        SecureStorage.putInt("max_privacy_hours", hours)
                        WalletManager.walletInstance?.getWalletStatus()
                        toast(if (hours == 0) context.getString(R.string.toast_max_privacy_no_limit) else context.getString(R.string.toast_max_privacy_hours, hours))
                    } catch (e: Exception) {
                        toast(context.getString(R.string.toast_error_message, e.message))
                    }
                },
            )
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 8.dp))

            // Show Owner Key — with password input (matches RN)
            SettingsAction(stringResource(R.string.settings_show_owner_key), stringResource(R.string.settings_show_owner_key_desc)) { showOwnerKey = !showOwnerKey }
            if (showOwnerKey) {
                Spacer(Modifier.height(8.dp))
                PasswordField(ownerKeyPass, { ownerKeyPass = it }, stringResource(R.string.general_password))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        showOwnerKey = false; ownerKeyPass = ""
                    }, shape = RoundedCornerShape(8.dp)) {
                        Text(stringResource(R.string.general_cancel), color = C.textSecondary)
                    }
                    Button(
                        onClick = {
                            val wallet = WalletManager.walletInstance
                            if (wallet != null && ownerKeyPass.isNotEmpty()) {
                                val valid = wallet.checkWalletPassword(ownerKeyPass)
                                if (!valid) {
                                    toast(context.getString(R.string.settings_incorrect_password))
                                } else {
                                    val key = wallet.exportOwnerKey(ownerKeyPass)
                                    if (key.isNotEmpty()) {
                                        ownerKeyResult = key
                                    }
                                    showOwnerKey = false
                                    ownerKeyPass = ""
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                    ) {
                        Text(stringResource(R.string.settings_show_key), color = C.textDark, fontWeight = FontWeight.Bold)
                    }
                }
            }
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 8.dp))

            // Change password
            SettingsAction(stringResource(R.string.settings_change_password_label)) { showChangePassword = !showChangePassword }
            if (showChangePassword) {
                Spacer(Modifier.height(8.dp))
                PasswordField(currentPass, { currentPass = it }, stringResource(R.string.settings_current_password))
                Spacer(Modifier.height(8.dp))
                PasswordField(newPass, { newPass = it }, stringResource(R.string.settings_new_password))
                Spacer(Modifier.height(8.dp))
                PasswordField(confirmPass, { confirmPass = it }, stringResource(R.string.settings_confirm_new_password))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        showChangePassword = false
                        currentPass = ""; newPass = ""; confirmPass = ""
                    }, shape = RoundedCornerShape(8.dp)) {
                        Text(stringResource(R.string.general_cancel), color = C.textSecondary)
                    }
                    Button(
                        onClick = {
                            when {
                                currentPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty() ->
                                    toast(context.getString(R.string.settings_all_password_fields_required))
                                newPass != confirmPass -> toast(context.getString(R.string.settings_new_passwords_dont_match))
                                newPass.length < 6 -> toast(context.getString(R.string.settings_password_too_short))
                                else -> {
                                    val wallet = WalletManager.walletInstance
                                    if (wallet != null) {
                                        val valid = wallet.checkWalletPassword(currentPass)
                                        if (!valid) {
                                            toast(context.getString(R.string.settings_current_password_incorrect))
                                        } else {
                                            wallet.changeWalletPassword(newPass)
                                            SecureStorage.storeWalletPassword(newPass)
                                            showChangePassword = false
                                            currentPass = ""; newPass = ""; confirmPass = ""
                                            toast(context.getString(R.string.settings_password_changed_toast))
                                        }
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                    ) {
                        Text(stringResource(R.string.general_change), color = C.textDark, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ========== UTILITIES ==========
        SectionTitle(stringResource(R.string.settings_section_utilities))
        SettingsCard {
            SettingsAction(stringResource(R.string.settings_export_wallet_label), stringResource(R.string.settings_export_wallet_desc)) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    toast(context.getString(R.string.tx_history_export_android10))
                } else {
                    try {
                        WalletManager.walletInstance?.exportDataToJson()
                        toast(context.getString(R.string.settings_exporting))
                    } catch (e: Throwable) {
                        toast(context.getString(R.string.toast_export_failed, e.message))
                    }
                }
            }
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 4.dp))
            SettingsAction(stringResource(R.string.settings_import_wallet_label), stringResource(R.string.settings_import_wallet_desc)) {
                importLauncher.launch("*/*")
            }
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 4.dp))
            SettingsAction(stringResource(R.string.tx_export_history), stringResource(R.string.settings_export_tx_desc)) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    toast(context.getString(R.string.tx_history_export_android10))
                } else {
                    try {
                        WalletManager.walletInstance?.exportTxHistoryToCsv()
                            ?: run { toast(context.getString(R.string.settings_wallet_not_open)); return@SettingsAction }
                        toast(context.getString(R.string.tx_history_exporting))
                    } catch (e: Throwable) {
                        toast(context.getString(R.string.toast_export_failed, e.message))
                    }
                }
            }
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 4.dp))
            SettingsAction(stringResource(R.string.settings_my_addresses_label), stringResource(R.string.settings_my_addresses_desc)) { onNavigateAddresses() }
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 4.dp))
            SettingsAction(stringResource(R.string.settings_show_utxo), stringResource(R.string.settings_show_utxo_desc)) { onNavigateUtxo() }
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 4.dp))

            // Rescan with progress (matches RN)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isSyncing) {
                        showRescanConfirm = true
                    }
                    .padding(vertical = 12.dp),
            ) {
                Text(stringResource(R.string.general_rescan), color = C.accent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                if (isSyncing) {
                    Text(stringResource(R.string.settings_sync_progress, syncPercent, syncProgress.done / 1000, syncProgress.total / 1000),
                        color = C.accent, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { syncPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = C.accent,
                        trackColor = C.border,
                    )
                } else {
                    Text(stringResource(R.string.settings_rescan_desc),
                        color = C.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 4.dp))
            SettingsAction(stringResource(R.string.settings_show_public_offline_address), stringResource(R.string.settings_public_offline_desc)) {
                try {
                    WalletManager.walletInstance?.getPublicAddress()
                    toast(context.getString(R.string.settings_requesting_public_addr))
                } catch (e: Exception) {
                    toast(context.getString(R.string.toast_error_message, e.message))
                }
            }
        }

        // ========== ABOUT ==========
        SectionTitle(stringResource(R.string.settings_section_about))
        SettingsCard {
            val shaderLoaded = remember { ShaderInvoker.hasShaderBytes() }
            SettingsRow(stringResource(R.string.settings_shader_label), if (shaderLoaded) stringResource(R.string.settings_loaded) else stringResource(R.string.settings_not_loaded),
                valueColor = if (shaderLoaded) Color(0xFF4CAF50) else C.error)
            SettingsRow(stringResource(R.string.settings_ipfs_label),
                when (ipfsStatus) { "online" -> stringResource(R.string.settings_online_status); "offline" -> stringResource(R.string.settings_offline_status); else -> stringResource(R.string.settings_checking) },
                valueColor = when (ipfsStatus) { "online" -> Color(0xFF4CAF50); "offline" -> C.error; else -> C.textSecondary })
            SettingsRow(stringResource(R.string.settings_ipfs_peers_label), if (ipfsPeers >= 0) "$ipfsPeers" else "N/A",
                valueColor = if (ipfsPeers > 0) Color(0xFF4CAF50) else if (ipfsPeers == 0) C.error else C.textSecondary)
            SettingsRow(stringResource(R.string.settings_contract_label), Helpers.truncateKey(Config.PRIVIME_CID))
            SettingsRow(stringResource(R.string.settings_lib_version_label), libVersion)
            val appVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } catch (_: Exception) { "1.0.0" }
            var showVersionDialog by remember { mutableStateOf(false) }
            val versionLabel = stringResource(R.string.settings_app_label)
            val versionValue = stringResource(R.string.settings_version, appVersion ?: "")
            val releaseUrl = "https://github.com/PriviMW/PriviMW-Wallet/releases/tag/v${appVersion}"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showVersionDialog = true }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(versionLabel, color = C.textSecondary, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(versionValue, color = C.accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(4.dp))
                    Text("↗", color = C.accent, fontSize = 13.sp)
                }
            }
            if (showVersionDialog) {
                AlertDialog(
                    onDismissRequest = { showVersionDialog = false },
                    containerColor = C.card,
                    title = { Text(stringResource(R.string.settings_view_release), color = C.text, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                    text = { Text(stringResource(R.string.settings_open_release_url, releaseUrl), color = C.text, fontSize = 14.sp) },
                    confirmButton = {
                        Button(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl)))
                            showVersionDialog = false
                        }, colors = ButtonDefaults.buttonColors(containerColor = C.accent)) {
                            Text(stringResource(R.string.settings_open), color = C.bg)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showVersionDialog = false }) {
                            Text(stringResource(R.string.settings_cancel), color = C.textSecondary)
                        }
                    }
                )
            }
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 4.dp))
            var checkingUpdate by remember { mutableStateOf(false) }
            var showUpdateDialog by remember { mutableStateOf<com.privimemobile.wallet.UpdateChecker.UpdateInfo?>(null) }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!checkingUpdate) {
                            checkingUpdate = true
                            scope.launch {
                                val info = com.privimemobile.wallet.UpdateChecker.checkForUpdate(context, force = true)
                                checkingUpdate = false
                                if (info != null) {
                                    showUpdateDialog = info
                                } else {
                                    toast(context.getString(R.string.settings_latest_version_toast))
                                }
                            }
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                color = Color.Transparent,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_check_updates_label), color = C.accent, fontSize = 15.sp)
                    if (checkingUpdate) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = C.accent, strokeWidth = 2.dp)
                    }
                }
            }
            if (showUpdateDialog != null) {
                AlertDialog(
                    onDismissRequest = { showUpdateDialog = null },
                    containerColor = C.card,
                    title = { Text(stringResource(R.string.settings_update_available_title), color = C.text, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text(stringResource(R.string.settings_update_available, showUpdateDialog!!.latestVersion), color = C.text, fontSize = 16.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.settings_update_current, showUpdateDialog!!.currentVersion), color = C.textSecondary, fontSize = 13.sp)
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(showUpdateDialog!!.releaseUrl))
                            context.startActivity(intent)
                            showUpdateDialog = null
                        }, colors = ButtonDefaults.buttonColors(containerColor = C.accent)) {
                            Text(stringResource(R.string.settings_view_release), color = C.bg)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showUpdateDialog = null }) {
                            Text(stringResource(R.string.settings_later), color = C.textSecondary)
                        }
                    },
                )
            }
        }

        // ========== REMOVE WALLET ==========
        Spacer(Modifier.height(24.dp))
        if (!showDeleteWallet) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDeleteWallet = true },
                shape = RoundedCornerShape(8.dp),
                color = C.dangerBg,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(stringResource(R.string.settings_remove_wallet), color = C.error, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = C.dangerBg),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(C.error)
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.settings_delete_wallet_warning),
                        color = C.error, fontSize = 14.sp, lineHeight = 20.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.settings_type_delete_confirm), color = C.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = deleteConfirmText,
                        onValueChange = { deleteConfirmText = it },
                        placeholder = { Text(stringResource(R.string.settings_type_delete_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = C.error, unfocusedBorderColor = C.border, cursorColor = C.error,
                        ),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showDeleteWallet = false; deleteConfirmText = "" },
                            shape = RoundedCornerShape(8.dp),
                        ) { Text(stringResource(R.string.general_cancel), color = C.textSecondary) }
                        Button(
                            onClick = {
                                if (deleteConfirmText.trim() == "DELETE") {
                                    // Authenticate first, then delete
                                    fun performDelete() {
                                        deleting = true
                                        try {
                                            context.stopService(Intent(context, BackgroundService::class.java))
                                        } catch (_: Exception) {}
                                        WalletManager.closeWallet()
                                        // Delete wallet database files
                                        val ctx = WalletManager.appContext
                                        ctx?.filesDir?.listFiles()?.forEach { file ->
                                            if (file.name.contains("wallet") ||
                                                file.name.endsWith(".db") ||
                                                file.name.endsWith(".db-journal") ||
                                                file.name.endsWith(".db-wal") ||
                                                file.name.endsWith(".db-shm")) {
                                                file.delete()
                                            }
                                        }
                                        // Nuclear reset — clears ALL app data (prefs, files, DB, cache)
                                        // Same as Settings > App Info > Clear Data. Kills process automatically.
                                        (context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager)
                                            .clearApplicationUserData()
                                    }

                                    // Try biometric auth first, then password fallback
                                    val biometricsEnabled = SecureStorage.getBoolean(SecureStorage.KEY_FINGERPRINT_ENABLED)
                                    val activity = context as? androidx.fragment.app.FragmentActivity
                                    if (biometricsEnabled && activity != null) {
                                        val executor = androidx.core.content.ContextCompat.getMainExecutor(context)
                                        val prompt = androidx.biometric.BiometricPrompt(activity, executor,
                                            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                                                override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                                                    performDelete()
                                                }
                                                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                                    // Fall back to password check
                                                    val pw = SecureStorage.getWalletPassword()
                                                    if (pw != null) {
                                                        val valid = WalletManager.walletInstance?.checkWalletPassword(pw) ?: true
                                                        if (valid) performDelete()
                                                    } else {
                                                        performDelete()
                                                    }
                                                }
                                                override fun onAuthenticationFailed() {}
                                            }
                                        )
                                        val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                                            .setTitle(context.getString(R.string.settings_biometric_delete_title))
                                            .setSubtitle(context.getString(R.string.settings_biometric_delete_subtitle))
                                            .setNegativeButtonText(context.getString(R.string.lock_use_password_button))
                                            .build()
                                        prompt.authenticate(promptInfo)
                                    } else {
                                        // No biometrics — verify wallet password
                                        val pw = SecureStorage.getWalletPassword()
                                        if (pw != null && WalletManager.walletInstance != null) {
                                            val valid = WalletManager.walletInstance!!.checkWalletPassword(pw)
                                            if (valid) performDelete()
                                        } else {
                                            performDelete()
                                        }
                                    }
                                }
                            },
                            enabled = deleteConfirmText.trim() == "DELETE" && !deleting,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = C.error,
                                disabledContainerColor = C.error.copy(alpha = 0.4f),
                            ),
                        ) {
                            if (deleting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = C.text,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(stringResource(R.string.settings_remove_wallet_btn), color = C.text, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    // Import confirmation dialog
    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = {
                showImportConfirm = false
                importFileContent = ""
                importFileName = ""
            },
            title = { Text(stringResource(R.string.settings_import_dialog_title), color = C.text) },
            text = {
                Text(
                    stringResource(R.string.settings_import_dialog_text, importFileName),
                    color = C.textSecondary,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            WalletManager.walletInstance?.importDataFromJson(importFileContent)
                            toast(context.getString(R.string.settings_importing))
                        } catch (e: Throwable) {
                            toast(context.getString(R.string.toast_import_failed, e.message))
                        }
                        showImportConfirm = false
                        importFileContent = ""
                        importFileName = ""
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                ) {
                    Text(stringResource(R.string.settings_import), color = C.textDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showImportConfirm = false
                        importFileContent = ""
                        importFileName = ""
                    },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(stringResource(R.string.general_cancel), color = C.textSecondary)
                }
            },
            containerColor = C.card,
            shape = RoundedCornerShape(12.dp),
        )
    }

    // Owner key result dialog
    if (ownerKeyResult.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { ownerKeyResult = "" },
            title = { Text(stringResource(R.string.settings_owner_key_title), color = C.text) },
            text = {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        ownerKeyResult,
                        color = C.textSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboard.setText(AnnotatedString(ownerKeyResult))
                        toast(context.getString(R.string.settings_owner_key_copied_timed))
                        scope.launch {
                            delay(30000)
                            clipboard.setText(AnnotatedString(""))
                        }
                        ownerKeyResult = ""
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                ) {
                    Text(stringResource(R.string.general_copy), color = C.textDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { ownerKeyResult = "" },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(stringResource(R.string.general_close), color = C.textSecondary)
                }
            },
            containerColor = C.card,
            shape = RoundedCornerShape(12.dp),
        )
    }

    // Rescan confirmation dialog
    if (showRescanConfirm) {
        AlertDialog(
            onDismissRequest = { showRescanConfirm = false },
            title = { Text(stringResource(R.string.general_rescan), color = C.text) },
            text = {
                Text(
                    stringResource(R.string.settings_rescan_confirmation),
                    color = C.textSecondary,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRescanConfirm = false
                        WalletManager.walletInstance?.rescan()
                        toast(context.getString(R.string.toast_rescan_started))
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                ) {
                    Text(stringResource(R.string.general_rescan), color = C.textDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showRescanConfirm = false },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(stringResource(R.string.general_cancel), color = C.textSecondary)
                }
            },
            containerColor = C.card,
            shape = RoundedCornerShape(12.dp),
        )
    }

    // Public offline address dialog
    if (publicAddress.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { publicAddress = "" },
            title = { Text(stringResource(R.string.settings_public_offline_title), color = C.text) },
            text = {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        publicAddress,
                        color = C.textSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboard.setText(AnnotatedString(publicAddress))
                        toast(context.getString(R.string.receive_address_copied))
                        publicAddress = ""
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                ) {
                    Text(stringResource(R.string.general_copy), color = C.textDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { publicAddress = "" },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(stringResource(R.string.general_close), color = C.textSecondary)
                }
            },
            containerColor = C.card,
            shape = RoundedCornerShape(12.dp),
        )
    }

    // IPFS test result dialog
    if (ipfsTestTitle.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { ipfsTestTitle = ""; ipfsTestResult = "" },
            title = { Text(ipfsTestTitle, color = C.text) },
            text = {
                Text(
                    ipfsTestResult,
                    color = C.textSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            },
            confirmButton = {
                OutlinedButton(
                    onClick = { ipfsTestTitle = ""; ipfsTestResult = "" },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(stringResource(R.string.general_close), color = C.textSecondary)
                }
            },
            containerColor = C.card,
            shape = RoundedCornerShape(12.dp),
        )
    }
}

// ================================================================
// Reusable setting components
// ================================================================

@Composable
private fun SectionTitle(title: String) {
    Spacer(Modifier.height(16.dp))
    Text(title, color = C.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = C.card),
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun SettingsRow(label: String, value: String, valueColor: Color = C.text) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = C.textSecondary, fontSize = 14.sp)
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SettingsToggle(label: String, desc: String, checked: Boolean, disabled: Boolean = false, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).let { if (disabled) it.then(Modifier.alpha(0.45f)) else it },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, color = C.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(desc, color = C.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Switch(
            checked = checked,
            onCheckedChange = if (disabled) null else onCheckedChange,
            enabled = !disabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = C.accent,
                checkedTrackColor = C.accent.copy(alpha = 0.3f),
                uncheckedThumbColor = C.textSecondary,
                uncheckedTrackColor = C.border,
            ),
        )
    }
}

@Composable
private fun SettingsAction(label: String, desc: String = "", onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
    ) {
        Text(label, color = C.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (desc.isNotEmpty()) {
            Text(desc, color = C.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun OptionRow(options: List<Pair<String, Int>>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (label, value) ->
            val isSelected = selected == value
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(value) },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) C.accent.copy(alpha = 0.1f) else Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isSelected) C.accent else C.border,
                ),
            ) {
                BoxWithConstraints(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val density = LocalDensity.current
                    val availPx = with(density) { maxWidth.toPx() }
                    val pxPerChar = with(density) { (9.sp).toPx() }
                    val fitCount = (availPx / pxPerChar).toInt()
                    val fontSize = when {
                        label.length > (fitCount * 1.0f).toInt() -> 9.sp
                        label.length > (fitCount * 0.82f).toInt() -> 10.sp
                        label.length > (fitCount * 0.68f).toInt() -> 11.sp
                        else -> 13.sp
                    }
                    Text(
                        label,
                        color = if (isSelected) C.accent else C.textSecondary,
                        fontSize = fontSize,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PasswordField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = C.accent, unfocusedBorderColor = C.border, cursorColor = C.accent,
            focusedContainerColor = C.bg, unfocusedContainerColor = C.bg,
        ),
    )
}

/** Distribute avatar image to all contacts via SBBS profile_update message. */
private suspend fun distributeAvatarToContacts(context: android.content.Context, imageBytes: ByteArray, hashHex: String) {
    val db = com.privimemobile.chat.ChatService.db ?: return
    val state = db.chatStateDao().get() ?: return
    val myHandle = state.myHandle ?: return
    val contacts = db.contactDao().getAll()

    val base64Data = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)

    for (contact in contacts) {
        val walletId = contact.walletId ?: continue
        try {
            val payload = mapOf(
                "v" to 1, "t" to "profile_update",
                "ts" to System.currentTimeMillis() / 1000,
                "from" to myHandle,
                "to" to contact.handle,
                "dn" to (state.myDisplayName ?: ""),
                "avatar_hash" to hashHex,
                "avatar_data" to base64Data,
                "avatar_mime" to "image/webp",
            )
            com.privimemobile.chat.ChatService.sbbs.sendWithRetry(walletId, payload)
            kotlinx.coroutines.delay(500) // small delay between sends
        } catch (e: Exception) {
            android.util.Log.w("Settings", "Failed to send avatar to ${contact.handle}: ${e.message}")
        }
    }
    android.util.Log.d("Settings", "Avatar distributed to ${contacts.size} contacts")
}
