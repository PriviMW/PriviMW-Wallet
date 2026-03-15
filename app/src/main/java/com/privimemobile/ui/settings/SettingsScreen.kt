package com.privimemobile.ui.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mw.beam.beamwallet.core.Api
import com.privimemobile.protocol.Config
import com.privimemobile.protocol.SecureStorage
import com.privimemobile.ui.theme.C
import com.privimemobile.wallet.BackgroundService
import com.privimemobile.wallet.WalletEventBus
import com.privimemobile.wallet.NodeConnectionEvent

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val libVersion = remember { try { Api.getLibVersion() } catch (_: Exception) { "unknown" } }
    val nodeConnection by WalletEventBus.nodeConnection.collectAsState(
        initial = NodeConnectionEvent(connected = false)
    )
    var askPasswordOnSend by remember {
        mutableStateOf(SecureStorage.getBoolean(SecureStorage.KEY_ASK_PASSWORD_ON_SEND))
    }
    var biometricsEnabled by remember {
        mutableStateOf(SecureStorage.getBoolean(SecureStorage.KEY_FINGERPRINT_ENABLED))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Settings", color = C.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        // Node
        SettingsSection("Node") {
            SettingsItem("Address", Config.DEFAULT_NODE)
            SettingsItem(
                "Status",
                if (nodeConnection.connected) "Connected" else "Disconnected",
                valueColor = if (nodeConnection.connected) C.online else C.error,
            )
            SettingsItem("Network", "Mainnet")
        }

        Spacer(Modifier.height(16.dp))

        // Security
        SettingsSection("Security") {
            SettingsToggle(
                label = "Password on Send",
                checked = askPasswordOnSend,
                onCheckedChange = {
                    askPasswordOnSend = it
                    SecureStorage.putBoolean(SecureStorage.KEY_ASK_PASSWORD_ON_SEND, it)
                },
            )
            SettingsToggle(
                label = "Biometrics",
                checked = biometricsEnabled,
                onCheckedChange = {
                    biometricsEnabled = it
                    SecureStorage.putBoolean(SecureStorage.KEY_FINGERPRINT_ENABLED, it)
                },
            )
        }

        Spacer(Modifier.height(16.dp))

        // Background Service
        SettingsSection("Background Service") {
            SettingsAction("Start Service") {
                val intent = Intent(context, BackgroundService::class.java)
                context.startForegroundService(intent)
            }
            SettingsAction("Stop Service") {
                context.stopService(Intent(context, BackgroundService::class.java))
            }
        }

        Spacer(Modifier.height(16.dp))

        // About
        SettingsSection("About") {
            SettingsItem("App", "PriviMW 2.0.0")
            SettingsItem("Beam Core", libVersion)
            val ipfsPeers = remember { try { "${Api.getIPFSPeerCount()}" } catch (_: Exception) { "N/A" } }
            SettingsItem("IPFS Peers", ipfsPeers)
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title.uppercase(),
        color = C.textSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = C.card),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingsItem(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = C.textSecondary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = C.text, fontSize = 15.sp)
        Text(value, color = valueColor, fontSize = 14.sp)
    }
}

@Composable
private fun SettingsToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = C.text, fontSize = 15.sp)
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
private fun SettingsAction(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = C.accent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}
