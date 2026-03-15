package com.privimemobile.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.ui.theme.C
import com.mw.beam.beamwallet.core.Api

@Composable
fun SettingsScreen() {
    val libVersion = try { Api.getLibVersion() } catch (_: Exception) { "unknown" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg)
            .padding(16.dp),
    ) {
        Text("Settings", color = C.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        SettingsSection("General") {
            SettingsItem("Node Address", "eu-nodes.mainnet.beam.mw:8100")
            SettingsItem("Network", "Mainnet")
        }

        Spacer(Modifier.height(16.dp))

        SettingsSection("About") {
            SettingsItem("App Version", "2.0.0")
            SettingsItem("Beam Core", libVersion)
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title,
        color = C.textSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
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
private fun SettingsItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = C.text, fontSize = 15.sp)
        Text(value, color = C.textSecondary, fontSize = 15.sp)
    }
}
