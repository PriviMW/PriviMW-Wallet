package com.privimemobile.ui.dapps

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.graphicsLayer
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    var editMode by remember { mutableStateOf(false) }

    // Persisted order — load from prefs
    val prefs = context.getSharedPreferences("dapp_order", 0)
    var orderedDapps by remember(dapps) {
        val savedOrder = prefs.getString("order", null)?.split(",") ?: emptyList()
        val ordered = if (savedOrder.isNotEmpty()) {
            val orderMap = savedOrder.withIndex().associate { (i, guid) -> guid to i }
            dapps.sortedBy { orderMap[it.guid] ?: Int.MAX_VALUE }
        } else dapps
        mutableStateOf(ordered)
    }

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
            // Exit edit mode on back press
            if (editMode) {
                androidx.activity.compose.BackHandler { editMode = false }
            }

            // DApp list with long-press edit mode
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = 16.dp, bottom = 80.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (editMode) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Arrange DApps", color = C.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = { editMode = false }) {
                                Text("Done", color = C.accent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                items(orderedDapps.size, key = { orderedDapps[it].guid }) { index ->
                    val dapp = orderedDapps[index]
                    if (editMode) {
                        // Edit mode — jiggle + move up/down buttons
                        val infiniteTransition = rememberInfiniteTransition(label = "jiggle${dapp.guid}")
                        val rotation by infiniteTransition.animateFloat(
                            initialValue = -1f, targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(150, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse,
                            ), label = "rot${dapp.guid}",
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer(rotationZ = rotation),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Move buttons
                            Column {
                                IconButton(
                                    onClick = {
                                        if (index > 0) {
                                            val list = orderedDapps.toMutableList()
                                            val temp = list[index]
                                            list[index] = list[index - 1]
                                            list[index - 1] = temp
                                            orderedDapps = list
                                            prefs.edit().putString("order", list.joinToString(",") { it.guid }).apply()
                                        }
                                    },
                                    enabled = index > 0,
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Text("\u25B2", color = if (index > 0) C.accent else C.textMuted, fontSize = 14.sp)
                                }
                                IconButton(
                                    onClick = {
                                        if (index < orderedDapps.size - 1) {
                                            val list = orderedDapps.toMutableList()
                                            val temp = list[index]
                                            list[index] = list[index + 1]
                                            list[index + 1] = temp
                                            orderedDapps = list
                                            prefs.edit().putString("order", list.joinToString(",") { it.guid }).apply()
                                        }
                                    },
                                    enabled = index < orderedDapps.size - 1,
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Text("\u25BC", color = if (index < orderedDapps.size - 1) C.accent else C.textMuted, fontSize = 14.sp)
                                }
                            }
                            Spacer(Modifier.width(4.dp))
                            Box(Modifier.weight(1f)) {
                                DAppCard(
                                    dapp = dapp,
                                    onOpen = {},
                                    onUninstall = { uninstallTarget = dapp },
                                )
                            }
                        }
                    } else {
                        // Normal mode — tap to open, long press to edit
                        Box(
                            modifier = Modifier.combinedClickable(
                                onClick = { handleLaunch(dapp) },
                                onLongClick = { editMode = true },
                            ),
                        ) {
                            DAppCard(
                                dapp = dapp,
                                onOpen = { handleLaunch(dapp) },
                                onUninstall = { uninstallTarget = dapp },
                            )
                        }
                    }
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
        Box {
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
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }

        }
        // Small X button at top-right corner
        IconButton(
            onClick = onUninstall,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(22.dp),
        ) {
            Text("X", color = C.textSecondary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
        } // Box
    }
}
