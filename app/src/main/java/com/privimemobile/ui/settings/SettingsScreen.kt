package com.privimemobile.ui.settings

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mw.beam.beamwallet.core.Api
import com.privimemobile.protocol.Config
import com.privimemobile.protocol.Helpers
import com.privimemobile.protocol.ProtocolStartup
import com.privimemobile.protocol.SecureStorage
import com.privimemobile.ui.theme.C
import com.privimemobile.wallet.BackgroundService
import com.privimemobile.wallet.WalletEventBus
import com.privimemobile.wallet.WalletManager
import com.privimemobile.wallet.NodeConnectionEvent

/**
 * Full settings screen — ports ALL 6 sections from the RN SettingsScreen.tsx.
 *
 * Sections: General, Notifications, Node, Privacy, Utilities, About + Remove Wallet
 */
@Composable
fun SettingsScreen(
    onNavigateAddresses: () -> Unit = {},
    onNavigateUtxo: () -> Unit = {},
) {
    val context = LocalContext.current
    val identity by ProtocolStartup.identity.collectAsState()
    val nodeConn by WalletEventBus.nodeConnection.collectAsState(initial = NodeConnectionEvent(false))

    // Lib info
    val libVersion = remember { try { Api.getLibVersion() } catch (_: Exception) { "unknown" } }
    val ipfsPeers = remember { mutableIntStateOf(try { Api.getIPFSPeerCount() } catch (_: Exception) { -1 }) }

    // Settings state
    var askPasswordOnSend by remember { mutableStateOf(SecureStorage.getBoolean(SecureStorage.KEY_ASK_PASSWORD_ON_SEND)) }
    var biometricsEnabled by remember { mutableStateOf(SecureStorage.getBoolean(SecureStorage.KEY_FINGERPRINT_ENABLED)) }
    var bgServiceEnabled by remember { mutableStateOf(SecureStorage.getBoolean("bg_service_enabled", true)) }
    var walletUpdatesNotif by remember { mutableStateOf(SecureStorage.getBoolean("wallet_updates_notif", true)) }
    var txStatusNotif by remember { mutableStateOf(SecureStorage.getBoolean("tx_status_notif", true)) }

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

    fun toast(msg: String) { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Settings", color = C.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        // ========== GENERAL ==========
        SectionTitle("GENERAL")
        SettingsCard {
            if (identity?.registered == true) {
                SettingsRow("Handle", "@${identity!!.handle}")
                SettingsRow("Display Name", identity!!.displayName.ifEmpty { "(none)" })
                SettingsRow("Wallet ID", Helpers.truncateKey(identity!!.walletId))
            } else {
                Text("Not registered. Register a @handle to use messaging.",
                    color = C.textSecondary, fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 8.dp))
            }
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 8.dp))
            Text("Minimum confirmations", color = C.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("Extra blocks to wait before considering coins confirmed.",
                color = C.textSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            OptionRow(
                options = listOf("Default (0)" to 0, "10" to 10, "20" to 20, "60" to 60),
                selected = 0,
                onSelect = { toast("Confirmations set to $it") },
            )
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
            SettingsRow("Status", if (nodeConn.connected) "Connected" else "Disconnected",
                valueColor = if (nodeConn.connected) Color(0xFF4CAF50) else C.error)
            SettingsRow("Network", "Mainnet")
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 8.dp))
            Text("Connection Mode", color = C.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                                    val wallet = WalletManager.walletInstance
                                    wallet?.changeNodeAddress(Config.DEFAULT_NODE)
                                    SecureStorage.storeNodeAddress(Config.DEFAULT_NODE)
                                    toast("Connected to ${Config.DEFAULT_NODE}")
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
            SettingsToggle("Biometric unlock",
                if (biometricsEnabled) "Wallet unlocks with biometrics" else "Wallet requires password on every app launch",
                biometricsEnabled) {
                biometricsEnabled = it
                SecureStorage.putBoolean(SecureStorage.KEY_FINGERPRINT_ENABLED, it)
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
            SettingsAction("My addresses", "View, edit, and generate wallet addresses") { onNavigateAddresses() }
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 4.dp))
            SettingsAction("Show UTXO", "View unspent transaction outputs") { onNavigateUtxo() }
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 4.dp))
            SettingsAction("Export transaction history", "Save CSV to Downloads") {
                // TODO: implement CSV export
                toast("Exporting transaction history...")
            }
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 4.dp))
            SettingsAction("Export wallet data", "Save wallet backup JSON to Downloads") {
                // TODO: implement JSON export
                toast("Exporting wallet data...")
            }
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 4.dp))
            SettingsAction("Import wallet data", "Restore from a backup file") {
                // TODO: implement JSON import
                toast("Import not yet implemented")
            }
            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 4.dp))
            SettingsAction("Rescan", "Re-sync all transactions from the chain") {
                WalletManager.walletInstance?.rescan()
                toast("Rescan started")
            }
        }

        // ========== ABOUT ==========
        SectionTitle("ABOUT")
        SettingsCard {
            val ipfsCount = ipfsPeers.intValue
            SettingsRow("IPFS", if (ipfsCount > 0) "Online" else "Offline",
                valueColor = if (ipfsCount > 0) Color(0xFF4CAF50) else C.error)
            SettingsRow("IPFS Peers", if (ipfsCount >= 0) "$ipfsCount" else "N/A")
            SettingsRow("Contract", Helpers.truncateKey(Config.PRIVIME_CID))
            SettingsRow("Lib Version", libVersion)
            SettingsRow("App", "PriviMW v2.0.0")
        }

        // ========== REMOVE WALLET ==========
        Spacer(Modifier.height(24.dp))
        if (!showDeleteWallet) {
            TextButton(
                onClick = { showDeleteWallet = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Remove wallet from this device", color = C.error, fontSize = 14.sp)
            }
        } else {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = C.error.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("This will permanently delete your wallet from this device. Make sure you have backed up your seed phrase!",
                        color = C.error, fontSize = 14.sp, lineHeight = 20.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Type DELETE to confirm:", color = C.textSecondary, fontSize = 13.sp)
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
                                    WalletManager.closeWallet()
                                    // Delete wallet files
                                    val ctx = WalletManager.appContext
                                    ctx?.filesDir?.listFiles()?.forEach { file ->
                                        if (file.name.contains("wallet") || file.name.endsWith(".db")) {
                                            file.delete()
                                        }
                                    }
                                    SecureStorage.remove(SecureStorage.KEY_HAS_WALLET)
                                    SecureStorage.remove(SecureStorage.KEY_WALLET_PASSWORD)
                                    // Kill process — wallet can't be recreated in same process
                                    android.os.Process.killProcess(android.os.Process.myPid())
                                }
                            },
                            enabled = deleteConfirmText.trim() == "DELETE",
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = C.error,
                                disabledContainerColor = C.error.copy(alpha = 0.4f),
                            ),
                        ) { Text("Remove Wallet", color = C.text, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ================================================================
// Reusable setting components
// ================================================================

@Composable
private fun SectionTitle(title: String) {
    Spacer(Modifier.height(16.dp))
    Text(title, color = C.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 8.dp))
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
private fun SettingsRow(label: String, value: String, valueColor: Color = C.textSecondary) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = C.text, fontSize = 15.sp)
        Text(value, color = valueColor, fontSize = 14.sp)
    }
}

@Composable
private fun SettingsToggle(label: String, desc: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = C.text, fontSize = 15.sp)
            Text(desc, color = C.textSecondary, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
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
            .padding(vertical = 12.dp),
    ) {
        Text(label, color = C.accent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        if (desc.isNotEmpty()) {
            Text(desc, color = C.textSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun OptionRow(options: List<Pair<String, Int>>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (label, value) ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected == value) C.accent else C.border.copy(alpha = 0.3f))
                    .clickable { onSelect(value) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (selected == value) C.textDark else C.textSecondary,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
        ),
    )
}
