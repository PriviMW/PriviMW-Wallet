package com.privimemobile.ui.dapps

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.privimemobile.dapp.DAppActivity
import com.privimemobile.protocol.DApp
import com.privimemobile.protocol.DAppManager
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import coil.ImageLoader
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import java.io.File

/**
 * My DApps screen -- installed DApps list with launch, uninstall, and store navigation.
 *
 * Fully ports DAppsScreen.tsx (MyDAppsScreen).
 * - Pull to refresh
 * - Refresh on focus (when returning from store)
 * - Uninstall confirmation dialog
 * - Launch via DAppActivity Intent
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DAppsScreen(
    onBrowseStore: () -> Unit = {},
    onLaunchDApp: (name: String, path: String, guid: String) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var dapps by remember { mutableStateOf(DAppManager.getInstalled(context)) }
    var refreshing by remember { mutableStateOf(false) }

    // Uninstall confirmation dialog state
    var uninstallTarget by remember { mutableStateOf<DApp?>(null) }

    fun loadDApps() {
        dapps = DAppManager.getInstalled(context)
    }

    // Refresh on focus (when returning from store or DApp)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                loadDApps()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Initial load
    LaunchedEffect(Unit) {
        loadDApps()
    }

    fun doRefresh() {
        refreshing = true
        scope.launch {
            loadDApps()
            delay(500)
            refreshing = false
        }
    }

    fun handleLaunch(dapp: DApp) {
        onLaunchDApp(dapp.name, DAppManager.getLaunchUrl(dapp), dapp.guid)
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { doRefresh() },
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg),
    ) {
        if (dapps.isEmpty()) {
            // Empty state
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Column(
                        modifier = Modifier.padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "D",
                            color = C.border,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No DApps Installed",
                            color = C.text,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Browse the DApp Store to install decentralized apps",
                            color = C.textSecondary,
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = onBrowseStore,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                        ) {
                            Text(
                                "Browse DApp Store",
                                color = C.textDark,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        } else {
            // DApp list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = 16.dp, bottom = 80.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(dapps, key = { it.guid }) { dapp ->
                    DAppCard(
                        dapp = dapp,
                        onOpen = { handleLaunch(dapp) },
                        onUninstall = { uninstallTarget = dapp },
                    )
                }
            }

            // "Get More DApps" bottom button
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
                    .clickable { onBrowseStore() },
                shape = RoundedCornerShape(12.dp),
                color = C.card,
                border = ButtonDefaults.outlinedButtonBorder(true).copy(
                    brush = androidx.compose.ui.graphics.SolidColor(C.accent)
                ),
            ) {
                Text(
                    "+ Get More DApps",
                    color = C.accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

    }

    // Uninstall confirmation dialog
    if (uninstallTarget != null) {
        AlertDialog(
            onDismissRequest = { uninstallTarget = null },
            title = { Text("Uninstall", color = C.text) },
            text = {
                Text(
                    "Remove ${uninstallTarget!!.name}?",
                    color = C.textSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val guid = uninstallTarget!!.guid
                        uninstallTarget = null
                        DAppManager.uninstall(context, guid)
                        loadDApps()
                    },
                ) {
                    Text("Uninstall", color = C.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { uninstallTarget = null }) {
                    Text("Cancel", color = C.textSecondary)
                }
            },
            containerColor = C.card,
        )
    }
}

@Composable
private fun DAppCard(dapp: DApp, onOpen: () -> Unit, onUninstall: () -> Unit) {
    val context = LocalContext.current

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
            // Icon — render stored SVG string (like RN build), fall back to file
            val hasSvgString = remember(dapp.icon) {
                dapp.icon.isNotBlank() && (dapp.icon.trimStart().startsWith("<svg") || dapp.icon.trimStart().startsWith("<?xml"))
            }
            val svgLoader = remember {
                ImageLoader.Builder(context)
                    .components { add(SvgDecoder.Factory()) }
                    .build()
            }

            if (hasSvgString) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(java.nio.ByteBuffer.wrap(dapp.icon.toByteArray()))
                        .build(),
                    imageLoader = svgLoader,
                    contentDescription = dapp.name,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            } else {
                // Try loading from file as fallback
                val iconFile = remember(dapp.localPath) {
                    listOf("app/appicon.svg", "app/icon.svg", "app/logo.svg")
                        .map { File(dapp.localPath, it) }
                        .firstOrNull { it.exists() }
                }
                if (iconFile != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(iconFile)
                            .build(),
                        imageLoader = svgLoader,
                        contentDescription = dapp.name,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                } else {
                    // Fallback letter icon
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
                }
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
                if (dapp.description.isNotEmpty()) {
                    Text(
                        dapp.description,
                        color = C.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }

            // Actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(C.border),
                ) {
                    Text("X", color = C.error, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}
