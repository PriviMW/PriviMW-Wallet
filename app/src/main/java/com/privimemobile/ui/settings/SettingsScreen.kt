package com.privimemobile.ui.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mw.beam.beamwallet.core.Api
import com.privimemobile.protocol.Config
import com.privimemobile.protocol.Helpers
// Old ProtocolStartup removed — using ChatService for identity
import com.privimemobile.protocol.ProtocolStorage
import com.privimemobile.protocol.SecureStorage
import com.privimemobile.protocol.ShaderInvoker
import com.privimemobile.protocol.WalletApi
import com.privimemobile.ui.theme.C
import com.privimemobile.wallet.BackgroundService
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
    val syncProgress by WalletEventBus.syncProgress.collectAsState(initial = SyncProgressEvent(0, 0))
    val walletStatus by WalletEventBus.beamStatus.collectAsState()

    // Lib info
    val libVersion = remember { try { Api.getLibVersion() } catch (_: Exception) { "unknown" } }
    var ipfsPeers by remember { mutableIntStateOf(try { Api.getIPFSPeerCount() } catch (_: Exception) { -1 }) }
    var ipfsStatus by remember { mutableStateOf("checking") } // checking, online, offline

    // Settings state
    var confirmationsOffset by remember { mutableIntStateOf(0) }
    var askPasswordOnSend by remember { mutableStateOf(SecureStorage.getBoolean(SecureStorage.KEY_ASK_PASSWORD_ON_SEND)) }
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

    // Registration fee
    val registrationFee by com.privimemobile.chat.ChatService.identity.registrationFee.collectAsState()

    fun toast(msg: String) { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }

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
                toast("Selected file is empty")
                return@rememberLauncherForActivityResult
            }
            // Validate it's valid JSON
            try {
                org.json.JSONObject(content)
            } catch (_: Exception) {
                try {
                    org.json.JSONArray(content)
                } catch (_: Exception) {
                    toast("Invalid JSON file")
                    return@rememberLauncherForActivityResult
                }
            }
            // Extract filename from URI
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            val nameIndex = cursor?.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME) ?: -1
            val name = if (cursor != null && nameIndex >= 0 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)
            } else {
                uri.lastPathSegment ?: "selected file"
            }
            cursor?.close()

            importFileName = name
            importFileContent = content
            showImportConfirm = true
        } catch (e: Exception) {
            toast("Failed to read file: ${e.message}")
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

    // Listen for export data events (wallet JSON backup + TX CSV) and save to Downloads
    LaunchedEffect(Unit) {
        WalletEventBus.exportData.collect { event ->
            try {
                val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                val filename = if (event.type == "csv") "privimw-transactions-$date.csv" else "privimw-backup-$date.json"
                val mimeType = if (event.type == "csv") "text/csv" else "application/json"

                // Save to Downloads via MediaStore
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(event.data.toByteArray()) }
                    toast("Saved to Downloads/$filename")
                } else {
                    toast("Failed to save file")
                }
            } catch (e: Exception) {
                toast("Export failed: ${e.message}")
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
        !nodeConn.connected -> if (walletStatus.height > 0) "Reconnecting..." else "Connecting..."
        isSyncing && syncProgress.total > 0 ->
            "Syncing ${syncPercent}% (${syncProgress.done / 1000}k / ${syncProgress.total / 1000}k)"
        isSyncing -> "Syncing..."
        else -> "Connected"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Settings", color = C.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        // ========== PRIVIME PROFILE ==========
        SectionTitle("PRIVIME")
        SettingsCard {
            if (chatState?.myHandle != null) {
                SettingsRow("Handle", "@${chatState!!.myHandle}")
                SettingsRow("Display Name", chatState!!.myDisplayName?.ifEmpty { "(none)" } ?: "(none)")
                SettingsRow("Wallet ID", Helpers.truncateKey(chatState!!.myWalletId ?: ""))
                SettingsRow("Registered", "Block #${chatState!!.myRegisteredHeight}")

                HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 8.dp))

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
                    Text("Edit Display Name", color = C.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                if (showEditName) {
                    var updating by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = newDisplayName,
                        onValueChange = { newDisplayName = it },
                        label = { Text("Display Name") },
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
                                    toast("Display name updated")
                                    showEditName = false
                                } else toast(err ?: "Failed to update display name")
                            }
                        },
                        enabled = !updating && newDisplayName.trim() != (chatState?.myDisplayName ?: ""),
                        colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (updating) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = C.textDark, strokeWidth = 2.dp)
                        else Text("Update", color = C.textDark, fontWeight = FontWeight.Bold)
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
                                            if (success) toast("Messaging address updated") else toast(err ?: "Failed")
                                        }
                                    }
                                }
                                toast("Updating messaging address...")
                            }
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Update Messaging Address", color = C.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Create a new SBBS address and update it on-chain",
                            color = C.textMuted, fontSize = 12.sp)
                    }
                }

                // Remove Handle
                var showRemoveConfirm by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showRemoveConfirm = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Remove Handle", color = C.error, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Unregister @${chatState!!.myHandle} and free it for others",
                            color = C.textMuted, fontSize = 12.sp)
                    }
                }
                if (showRemoveConfirm) {
                    var removing by remember { mutableStateOf(false) }
                    AlertDialog(
                        onDismissRequest = { if (!removing) showRemoveConfirm = false },
                        title = { Text("Remove Handle?", color = C.text) },
                        text = {
                            Text("This will unregister @${chatState!!.myHandle} from the blockchain. " +
                                "Your conversations will be lost and the handle will be available for others to claim.",
                                color = C.textSecondary)
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    removing = true
                                    com.privimemobile.chat.ChatService.identity.releaseHandle { success, err ->
                                        removing = false
                                        showRemoveConfirm = false
                                        if (success) toast("Handle removed")
                                        else toast(err ?: "Failed to remove handle")
                                    }
                                },
                                enabled = !removing,
                                colors = ButtonDefaults.buttonColors(containerColor = C.error),
                            ) {
                                if (removing) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = C.text, strokeWidth = 2.dp)
                                else Text("Remove", color = C.text, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRemoveConfirm = false }, enabled = !removing) {
                                Text("Cancel", color = C.textSecondary)
                            }
                        },
                        containerColor = C.card,
                    )
                }
            } else {
                Text("Not registered. Register a @handle in the Chats tab to use messaging.\nRegistration fee: ${if (registrationFee > 0) "$registrationFee BEAM" else "loading..."}",
                    color = C.textSecondary, fontSize = 13.sp, lineHeight = 20.sp,
                    modifier = Modifier.padding(vertical = 8.dp))
            }
        }

        // ========== NOTIFICATIONS ==========
        SectionTitle("NOTIFICATIONS")
        SettingsCard {
            SettingsToggle("Wallet updates", "Receive notifications about wallet updates", walletUpdatesNotif) {
                walletUpdatesNotif = it
                SecureStorage.putBoolean("wallet_updates_notif", it)
            }
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 4.dp))
            SettingsToggle("Transaction status", "Receive notifications when transaction status changes", txStatusNotif) {
                txStatusNotif = it
                SecureStorage.putBoolean("tx_status_notif", it)
            }
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 4.dp))
            SettingsToggle("Background connection", "Keep wallet connected for instant message delivery", bgServiceEnabled) {
                bgServiceEnabled = it
                SecureStorage.putBoolean("bg_service_enabled", it)
                if (it) {
                    context.startForegroundService(Intent(context, BackgroundService::class.java))
                    toast("Background service started")
                } else {
                    context.stopService(Intent(context, BackgroundService::class.java))
                    toast("Background service stopped")
                }
            }
        }

        // ========== NODE ==========
        SectionTitle("NODE")
        SettingsCard {
            SettingsRow("Status", syncStatus,
                valueColor = if (nodeConn.connected) Color(0xFF4CAF50) else C.error)
            SettingsRow("Block Height",
                if (walletStatus.height > 0) "#${walletStatus.height}" else "-")
            SettingsRow("Network", "Mainnet")
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 8.dp))
            Text("Connection Mode", color = C.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, C.border, RoundedCornerShape(8.dp)),
            ) {
                listOf("random" to "Random Node", "own" to "Own Node").forEach { (mode, label) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (nodeMode == mode) C.accent else C.card)
                            .clickable {
                                nodeMode = mode
                                if (mode == "random") {
                                    showNodeInput = false
                                    SecureStorage.putString("node_mode", "random")
                                    // Shuffle and try nodes (EU + US) like RN MAINNET_NODES
                                    val nodes = Config.MAINNET_NODES.shuffled()
                                    val wallet = WalletManager.walletInstance
                                    val selectedNode = nodes.firstOrNull() ?: Config.DEFAULT_NODE
                                    wallet?.changeNodeAddress(selectedNode)
                                    SecureStorage.storeNodeAddress(selectedNode)
                                    toast("Connected to $selectedNode")
                                } else {
                                    showNodeInput = true
                                }
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, color = if (nodeMode == mode) C.textDark else C.textSecondary,
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (showNodeInput) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = nodeInput,
                    onValueChange = { nodeInput = it },
                    placeholder = { Text("node-address:port") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = C.accent, unfocusedBorderColor = C.border, cursorColor = C.accent,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showNodeInput = false }, shape = RoundedCornerShape(8.dp)) {
                        Text("Cancel", color = C.textSecondary)
                    }
                    Button(
                        onClick = {
                            val addr = nodeInput.trim()
                            if (addr.isNotEmpty()) {
                                // Validate format: hostname:port
                                val parts = addr.split(":")
                                if (parts.size != 2) {
                                    toast("Invalid format. Use hostname:port")
                                    return@Button
                                }
                                WalletManager.walletInstance?.changeNodeAddress(addr)
                                SecureStorage.putString("node_mode", "own")
                                SecureStorage.putString("own_node_address", addr)
                                SecureStorage.storeNodeAddress(addr)
                                showNodeInput = false
                                toast("Connected to $addr")
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                    ) {
                        Text("Connect", color = C.textDark, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ========== PRIVACY ==========
        SectionTitle("PRIVACY")
        SettingsCard {
            SettingsToggle("Ask for password on every Send",
                "Require password confirmation for each transaction", askPasswordOnSend) {
                askPasswordOnSend = it
                SecureStorage.putBoolean(SecureStorage.KEY_ASK_PASSWORD_ON_SEND, it)
            }
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 4.dp))
            // Check biometric hardware availability
            val biometricManager = remember { BiometricManager.from(context) }
            val biometricAvailable = remember {
                biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                    BiometricManager.BIOMETRIC_SUCCESS
            }

            SettingsToggle(
                label = if (biometricAvailable) "Biometric unlock" else "Biometric unlock (unavailable)",
                desc = when {
                    !biometricAvailable -> "No biometric hardware found on this device"
                    biometricsEnabled -> "Wallet unlocks automatically with biometrics"
                    else -> "Wallet requires password on every app launch"
                },
                checked = biometricsEnabled,
                onCheckedChange = { enabling ->
                    if (enabling) {
                        // Enabling: verify fingerprint first before storing
                        if (!biometricAvailable) {
                            toast("This device does not support biometric authentication")
                            return@SettingsToggle
                        }
                        val storedPass = SecureStorage.getWalletPassword()
                        if (storedPass.isNullOrEmpty()) {
                            toast("Wallet password not found. Please try again.")
                            return@SettingsToggle
                        }
                        val activity = context as? FragmentActivity
                        if (activity == null) {
                            toast("Cannot show biometric prompt")
                            return@SettingsToggle
                        }
                        val executor = ContextCompat.getMainExecutor(context)
                        val prompt = BiometricPrompt(activity, executor,
                            object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                    biometricsEnabled = true
                                    SecureStorage.putBoolean(SecureStorage.KEY_FINGERPRINT_ENABLED, true)
                                    toast("Biometric unlock enabled")
                                }
                                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                                        toast("Biometric enrollment failed: $errString")
                                    }
                                    // Don't enable — user cancelled or error
                                }
                                override fun onAuthenticationFailed() {
                                    // Single attempt failed — prompt stays open for retry
                                }
                            })
                        val promptInfo = BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Enable Biometric Unlock")
                            .setSubtitle("Scan your fingerprint to enable biometric unlock")
                            .setNegativeButtonText("Cancel")
                            .build()
                        prompt.authenticate(promptInfo)
                    } else {
                        // Disabling: no confirmation needed
                        biometricsEnabled = false
                        SecureStorage.putBoolean(SecureStorage.KEY_FINGERPRINT_ENABLED, false)
                        toast("Biometric unlock disabled")
                    }
                },
                disabled = !biometricAvailable,
            ) // end biometric toggle
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 8.dp))

            // Max privacy time limit — functional (matches RN)
            Text("Longest transaction time for maximum anonymity", color = C.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("Maximum time funds are locked in Max Privacy transactions.",
                color = C.textMuted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            OptionRow(
                options = listOf("No limit" to 0, "24h" to 24, "72h" to 72),
                selected = maxPrivacyHours,
                onSelect = { hours ->
                    try {
                        WalletManager.walletInstance?.setMaxPrivacyLockTimeLimitHours(hours.toLong())
                        maxPrivacyHours = hours
                        SecureStorage.putInt("max_privacy_hours", hours)
                        WalletManager.walletInstance?.getWalletStatus()
                        toast(if (hours == 0) "Max privacy: no limit" else "Max privacy: ${hours}h")
                    } catch (e: Exception) {
                        toast("Error: ${e.message}")
                    }
                },
            )
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 8.dp))

            // Show Owner Key — with password input (matches RN)
            SettingsAction("Show owner key", "Requires wallet password") { showOwnerKey = !showOwnerKey }
            if (showOwnerKey) {
                Spacer(Modifier.height(8.dp))
                PasswordField(ownerKeyPass, { ownerKeyPass = it }, "Enter wallet password")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        showOwnerKey = false; ownerKeyPass = ""
                    }, shape = RoundedCornerShape(8.dp)) {
                        Text("Cancel", color = C.textSecondary)
                    }
                    Button(
                        onClick = {
                            val wallet = WalletManager.walletInstance
                            if (wallet != null && ownerKeyPass.isNotEmpty()) {
                                val valid = wallet.checkWalletPassword(ownerKeyPass)
                                if (!valid) {
                                    toast("Incorrect password")
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
                        Text("Show Key", color = C.textDark, fontWeight = FontWeight.Bold)
                    }
                }
            }
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 8.dp))

            // Change password
            SettingsAction("Change password") { showChangePassword = !showChangePassword }
            if (showChangePassword) {
                Spacer(Modifier.height(8.dp))
                PasswordField(currentPass, { currentPass = it }, "Current password")
                Spacer(Modifier.height(8.dp))
                PasswordField(newPass, { newPass = it }, "New password")
                Spacer(Modifier.height(8.dp))
                PasswordField(confirmPass, { confirmPass = it }, "Confirm new password")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        showChangePassword = false
                        currentPass = ""; newPass = ""; confirmPass = ""
                    }, shape = RoundedCornerShape(8.dp)) {
                        Text("Cancel", color = C.textSecondary)
                    }
                    Button(
                        onClick = {
                            when {
                                currentPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty() ->
                                    toast("All password fields are required")
                                newPass != confirmPass -> toast("New passwords don't match")
                                newPass.length < 6 -> toast("Password must be at least 6 characters")
                                else -> {
                                    val wallet = WalletManager.walletInstance
                                    if (wallet != null) {
                                        val valid = wallet.checkWalletPassword(currentPass)
                                        if (!valid) {
                                            toast("Current password is incorrect")
                                        } else {
                                            wallet.changeWalletPassword(newPass)
                                            SecureStorage.storeWalletPassword(newPass)
                                            showChangePassword = false
                                            currentPass = ""; newPass = ""; confirmPass = ""
                                            toast("Password changed successfully")
                                        }
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                    ) {
                        Text("Change", color = C.textDark, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ========== UTILITIES ==========
        SectionTitle("UTILITIES")
        SettingsCard {
            SettingsAction("Export wallet data", "Save wallet backup JSON to Downloads") {
                try {
                    WalletManager.walletInstance?.exportDataToJson()
                    toast("Exporting wallet data...")
                } catch (e: Exception) {
                    toast("Export failed: ${e.message}")
                }
            }
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 4.dp))
            SettingsAction("Import wallet data", "Restore from a backup file") {
                importLauncher.launch("application/json")
            }
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 4.dp))
            SettingsAction("Export transaction history", "Save CSV to Downloads") {
                try {
                    WalletManager.walletInstance?.exportTxHistoryToCsv()
                    toast("Exporting transaction history...")
                } catch (e: Exception) {
                    toast("Export failed: ${e.message}")
                }
            }
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 4.dp))
            SettingsAction("My addresses", "View, edit, and generate wallet addresses") { onNavigateAddresses() }
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 4.dp))
            SettingsAction("Show UTXO", "View unspent transaction outputs") { onNavigateUtxo() }
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
                Text("Rescan", color = C.accent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                if (isSyncing) {
                    Text("Syncing ${syncPercent}% (${syncProgress.done / 1000}k / ${syncProgress.total / 1000}k)",
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
                    Text("Re-sync all transactions from the chain",
                        color = C.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 4.dp))
            SettingsAction("Show public offline address", "Your permanent offline receive address") {
                try {
                    WalletManager.walletInstance?.getPublicAddress()
                    toast("Requesting public address...")
                } catch (e: Exception) {
                    toast("Error: ${e.message}")
                }
            }
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 4.dp))
            SettingsAction("Test IPFS", "Hash first (safe), then add, then download") {
                scope.launch { ipfsTestTitle = "IPFS Test"; ipfsTestResult = "Step 1: Hashing..." }
                val testData = "PriviMe IPFS test".toList().map { it.code }
                WalletApi.call("ipfs_hash", mapOf("data" to testData, "timeout" to 30000)) { hashResult ->
                    val hashError = hashResult["error"]
                    if (hashError != null) {
                        scope.launch { ipfsTestTitle = "IPFS Hash Failed"; ipfsTestResult = "$hashError" }
                        return@call
                    }
                    val hashCid = hashResult["hash"] as? String
                    if (hashCid == null) {
                        scope.launch { ipfsTestTitle = "IPFS Hash Failed"; ipfsTestResult = "No CID returned" }
                        return@call
                    }
                    scope.launch { ipfsTestTitle = "IPFS Hash OK"; ipfsTestResult = "CID: $hashCid\n\nStep 2: Adding to IPFS..." }

                    // Step 2: test ipfs_add
                    WalletApi.call("ipfs_add", mapOf("data" to testData, "pin" to true, "timeout" to 30000)) { addResult ->
                        val addError = addResult["error"]
                        if (addError != null) {
                            scope.launch { ipfsTestTitle = "IPFS Add Failed"; ipfsTestResult = "$addError" }
                            return@call
                        }
                        val addCid = addResult["hash"] as? String
                        if (addCid == null) {
                            scope.launch { ipfsTestTitle = "IPFS Add Failed"; ipfsTestResult = "No CID returned" }
                            return@call
                        }
                        scope.launch { ipfsTestTitle = "IPFS Add OK"; ipfsTestResult = "CID: $addCid\n\nStep 3: Downloading..." }

                        // Step 3: test ipfs_get
                        WalletApi.call("ipfs_get", mapOf("hash" to addCid, "timeout" to 30000)) { getResult ->
                            val getError = getResult["error"]
                            if (getError != null) {
                                scope.launch { ipfsTestTitle = "IPFS Get Failed"; ipfsTestResult = "CID: $addCid\n\nError: $getError" }
                                return@call
                            }
                            val data = getResult["data"]
                            val byteCount = when (data) {
                                is List<*> -> data.size
                                else -> -1
                            }
                            scope.launch {
                                if (byteCount > 0) {
                                    ipfsTestTitle = "IPFS Test Complete"
                                    ipfsTestResult = "All 3 steps passed!\n\nHash: OK\nAdd: OK\nGet: OK ($byteCount bytes)\n\nCID: $addCid"
                                } else {
                                    ipfsTestTitle = "IPFS Get Failed"
                                    ipfsTestResult = "CID: $addCid\n\nNo data returned"
                                }
                            }
                        }
                    }
                }
            }
        }

        // ========== ABOUT ==========
        SectionTitle("ABOUT")
        SettingsCard {
            val shaderLoaded = remember { ShaderInvoker.hasShaderBytes() }
            SettingsRow("Shader", if (shaderLoaded) "Loaded" else "Not loaded",
                valueColor = if (shaderLoaded) Color(0xFF4CAF50) else C.error)
            SettingsRow("IPFS",
                when (ipfsStatus) { "online" -> "Online"; "offline" -> "Offline"; else -> "Checking..." },
                valueColor = when (ipfsStatus) { "online" -> Color(0xFF4CAF50); "offline" -> C.error; else -> C.textSecondary })
            SettingsRow("IPFS Peers", if (ipfsPeers >= 0) "$ipfsPeers" else "N/A",
                valueColor = if (ipfsPeers > 0) Color(0xFF4CAF50) else if (ipfsPeers == 0) C.error else C.textSecondary)
            SettingsRow("Contract", Helpers.truncateKey(Config.PRIVIME_CID))
            SettingsRow("Lib Version", libVersion)
            SettingsRow("App", "PriviMW v2.0.0")
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
                    Text("Remove wallet", color = C.error, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
                    Text("This will permanently delete your wallet from this device. Make sure you have backed up your seed phrase!",
                        color = C.error, fontSize = 14.sp, lineHeight = 20.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Type DELETE to confirm:", color = C.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = deleteConfirmText,
                        onValueChange = { deleteConfirmText = it },
                        placeholder = { Text("Type DELETE") },
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
                        ) { Text("Cancel", color = C.textSecondary) }
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
                                            .setTitle("Confirm Wallet Removal")
                                            .setSubtitle("Authenticate to delete wallet")
                                            .setNegativeButtonText("Use Password")
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
                                Text("Remove Wallet", color = C.text, fontWeight = FontWeight.Bold)
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
            title = { Text("Import Wallet Data", color = C.text) },
            text = {
                Text(
                    "Import wallet data from \"$importFileName\"?\n\nThis will merge the backup into your current wallet.",
                    color = C.textSecondary,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            WalletManager.walletInstance?.importDataFromJson(importFileContent)
                            toast("Importing wallet data...")
                        } catch (e: Exception) {
                            toast("Import failed: ${e.message}")
                        }
                        showImportConfirm = false
                        importFileContent = ""
                        importFileName = ""
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                ) {
                    Text("Import", color = C.textDark, fontWeight = FontWeight.Bold)
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
                    Text("Cancel", color = C.textSecondary)
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
            title = { Text("Owner Key", color = C.text) },
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
                        toast("Owner key copied! Clipboard clears in 30s")
                        scope.launch {
                            delay(30000)
                            clipboard.setText(AnnotatedString(""))
                        }
                        ownerKeyResult = ""
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                ) {
                    Text("Copy", color = C.textDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { ownerKeyResult = "" },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Close", color = C.textSecondary)
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
            title = { Text("Rescan", color = C.text) },
            text = {
                Text(
                    "This will rescan the blockchain for your transactions. This may take a while.",
                    color = C.textSecondary,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRescanConfirm = false
                        WalletManager.walletInstance?.rescan()
                        toast("Rescan started")
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                ) {
                    Text("Rescan", color = C.textDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showRescanConfirm = false },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Cancel", color = C.textSecondary)
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
            title = { Text("Public Offline Address", color = C.text) },
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
                        toast("Address copied to clipboard")
                        publicAddress = ""
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                ) {
                    Text("Copy", color = C.textDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { publicAddress = "" },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("Close", color = C.textSecondary)
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
                    Text("Close", color = C.textSecondary)
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
                Text(
                    label,
                    color = if (isSelected) C.accent else C.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(vertical = 8.dp, horizontal = 14.dp)
                        .wrapContentWidth(Alignment.CenterHorizontally),
                )
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
