package com.privimemobile.ui.dapps

import android.content.Intent
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.dapp.DAppActivity
import com.privimemobile.protocol.DApp
import com.privimemobile.protocol.DAppManager
import com.privimemobile.ui.theme.C

@Composable
fun DAppsScreen(onBrowseStore: () -> Unit = {}) {
    val context = LocalContext.current
    var dapps by remember { mutableStateOf(DAppManager.getInstalled(context)) }

    // Refresh on resume
    LaunchedEffect(Unit) {
        dapps = DAppManager.getInstalled(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg)
            .padding(16.dp),
    ) {
        Text("DApps", color = C.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Decentralized applications on Beam",
            color = C.textSecondary,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(16.dp))

        if (dapps.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No DApps installed", color = C.textSecondary, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onBrowseStore,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                    ) {
                        Text("Browse DApp Store", color = C.textDark, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(dapps, key = { it.guid }) { dapp ->
                    DAppCard(
                        dapp = dapp,
                        onOpen = {
                            context.startActivity(
                                Intent(context, DAppActivity::class.java).apply {
                                    putExtra("dapp_name", dapp.name)
                                    putExtra("dapp_path", DAppManager.getLaunchUrl(dapp))
                                    putExtra("dapp_guid", dapp.guid)
                                }
                            )
                        },
                        onUninstall = {
                            DAppManager.uninstall(context, dapp.guid)
                            dapps = DAppManager.getInstalled(context)
                        },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = { /* TODO: DApp Store browse */ },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = ButtonDefaults.outlinedButtonBorder(true).copy(
                    brush = androidx.compose.ui.graphics.SolidColor(C.accent)
                ),
            ) {
                Text("+ Get More DApps", color = C.accent, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DAppCard(dapp: DApp, onOpen: () -> Unit, onUninstall: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = C.card),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(C.border),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    dapp.name.first().uppercase(),
                    color = C.accent,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(dapp.name, color = C.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("v${dapp.version}", color = C.textSecondary, fontSize = 11.sp)
                if (dapp.description.isNotEmpty()) {
                    Text(dapp.description, color = C.textSecondary, fontSize = 12.sp, maxLines = 1)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onOpen,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text("Open", color = C.textDark, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                IconButton(
                    onClick = onUninstall,
                    modifier = Modifier.size(32.dp),
                ) {
                    Text("X", color = C.error, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}
