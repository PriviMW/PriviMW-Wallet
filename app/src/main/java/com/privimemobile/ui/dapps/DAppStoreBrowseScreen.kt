package com.privimemobile.ui.dapps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.protocol.AvailableDApp
import com.privimemobile.protocol.DAppManager
import com.privimemobile.protocol.DAppStore
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DAppStoreBrowseScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var available by remember { mutableStateOf<List<AvailableDApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var installing by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    // Load available DApps on mount
    fun loadAvailable() {
        DAppStore.queryAvailableDApps(context) { dapps ->
            // Filter out already-installed DApps
            val installed = DAppManager.getInstalled(context).map { it.guid }.toSet()
            available = dapps.filter { it.guid !in installed }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        DAppStore.loadShader(context)
        loadAvailable()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg),
    ) {
        // Back button
        TextButton(
            onClick = onBack,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        ) {
            Text("< Back", color = C.textSecondary)
        }

        Text(
            "DApp Store",
            color = C.text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Browse and install DApps from the Beam on-chain registry",
            color = C.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(12.dp))

        // Loading indicator
        if (loading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    color = C.accent,
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text("Loading DApp Store...", color = C.textSecondary, fontSize = 13.sp)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Sideload card
            item {
                SideloadCard(
                    installing = installing == "sideload",
                    onClick = {
                        // Sideloading from file is not yet implemented in the native wallet
                        message = "Sideloading from file is coming soon"
                    },
                )
            }

            // DApp list
            if (!loading && available.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("No additional DApps available", color = C.textSecondary, fontSize = 15.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Pull down to refresh or sideload a .dapp package",
                            color = C.textSecondary.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            items(available, key = { it.guid }) { dapp ->
                DAppStoreCard(
                    dapp = dapp,
                    installing = installing == dapp.guid,
                    onInstall = {
                        if (installing != null) return@DAppStoreCard
                        installing = dapp.guid
                        scope.launch {
                            try {
                                // For bundled assets, install from APK assets
                                if (dapp.bundledAsset.isNotEmpty()) {
                                    withContext(Dispatchers.IO) {
                                        DAppManager.installFromAsset(
                                            context,
                                            dapp.guid,
                                            dapp.bundledAsset,
                                            dapp.name,
                                        )
                                    }
                                    message = "${dapp.name} installed"
                                    loadAvailable()
                                } else if (dapp.ipfsCid.isNotEmpty()) {
                                    // IPFS download not yet implemented in native wallet
                                    message = "IPFS download coming soon"
                                } else {
                                    message = "No download source available"
                                }
                            } catch (e: Exception) {
                                message = "Install failed: ${e.message ?: "Unknown error"}"
                            }
                            installing = null
                        }
                    },
                )
            }

            item { Spacer(Modifier.height(20.dp)) }
        }

        // Message toast
        message?.let { msg ->
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(3000)
                message = null
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(8.dp),
                color = C.card,
                shadowElevation = 4.dp,
            ) {
                Text(
                    msg,
                    color = C.accent,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun SideloadCard(installing: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = C.border,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(enabled = !installing) { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = C.card),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "+",
                color = C.accent,
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .size(48.dp)
                    .wrapContentHeight(Alignment.CenterVertically),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Install from File",
                    color = C.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Sideload a .dapp package from your device",
                    color = C.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (installing) {
                CircularProgressIndicator(
                    color = C.accent,
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

@Composable
private fun DAppStoreCard(
    dapp: AvailableDApp,
    installing: Boolean,
    onInstall: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = C.card),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon (letter fallback)
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

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    dapp.name,
                    color = C.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "v${dapp.version}",
                    color = C.textSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 1.dp),
                )
                if (dapp.publisher.isNotEmpty()) {
                    Text(
                        text = if (dapp.publisher.length > 20) {
                            "${dapp.publisher.take(8)}...${dapp.publisher.takeLast(8)}"
                        } else dapp.publisher,
                        color = C.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (dapp.description.isNotEmpty()) {
                    Text(
                        dapp.description,
                        color = C.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Text(
                    text = if (dapp.bundledAsset.isNotEmpty()) "BUNDLED" else "IPFS",
                    color = C.accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }

            // Install button
            Button(
                onClick = onInstall,
                enabled = !installing,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = C.accent,
                    disabledContainerColor = C.accent.copy(alpha = 0.5f),
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier.widthIn(min = 70.dp),
            ) {
                if (installing) {
                    CircularProgressIndicator(
                        color = C.textDark,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        "Install",
                        color = C.textDark,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
