package com.privimemobile.ui.dapps

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.privimemobile.R
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
    var launchingGuid by remember { mutableStateOf<String?>(null) }

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
        if (launchingGuid == dapp.guid) return
        launchingGuid = dapp.guid

        scope.launch {
            try {
                val updated = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.privimemobile.protocol.DAppStore.checkAndUpdate(context, dapp)
                }
                if (updated) {
                    loadDApps()
                    Toast.makeText(context, context.getString(R.string.dapps_updated_toast, dapp.name), Toast.LENGTH_SHORT).show()
                } else {
                    onLaunchDApp(dapp.name, DAppManager.getLaunchUrl(dapp), dapp.guid)
                }
            } catch (e: Exception) {
                Log.w("DAppsScreen", "Update check failed: ${e.message}")
                onLaunchDApp(dapp.name, DAppManager.getLaunchUrl(dapp), dapp.guid)
            } finally {
                launchingGuid = null
            }
        }
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
                        stringResource(R.string.dapps_empty_title),
                            color = C.text,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                        stringResource(R.string.dapps_empty_subtitle),
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
                            stringResource(R.string.dapps_empty_button),
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

            if (editMode) {
                // ── Edit mode: live reorder with haptic feedback ──
                var dragIndex by remember { mutableStateOf(-1) }
                var dragOffset by remember { mutableFloatStateOf(0f) }
                val itemHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { 86.dp.toPx() }
                val hapticView = androidx.compose.ui.platform.LocalView.current
                fun hapticTick() {
                    hapticView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                }

                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                    Text(stringResource(R.string.dapps_arrange), color = C.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { editMode = false }) {
                            Text(stringResource(R.string.general_done), color = C.accent, fontWeight = FontWeight.Bold)
                        }
                    }

                    orderedDapps.forEachIndexed { index, dapp ->
                        val isDragging = dragIndex == index
                        val infiniteTransition = rememberInfiniteTransition(label = "jiggle$index")
                        val rotation by infiniteTransition.animateFloat(
                            initialValue = -0.7f, targetValue = 0.7f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(100 + (index % 3) * 30, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse,
                            ), label = "rot$index",
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer(
                                    rotationZ = if (isDragging) 0f else rotation,
                                    translationY = if (isDragging) dragOffset else 0f,
                                    scaleX = if (isDragging) 1.05f else 1f,
                                    scaleY = if (isDragging) 1.05f else 1f,
                                    shadowElevation = if (isDragging) 20f else 0f,
                                )
                                .zIndex(if (isDragging) 10f else 0f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.weight(1f)) {
                                DAppCard(dapp = dapp, isLaunching = false, onOpen = {}, onUninstall = { uninstallTarget = dapp })
                            }
                            // Drag handle ≡
                            Box(
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isDragging) C.accent.copy(alpha = 0.2f) else C.card)
                                    .pointerInput(Unit) {
                                        detectVerticalDragGestures(
                                            onDragStart = {
                                                dragIndex = index
                                                dragOffset = 0f
                                                hapticTick()
                                            },
                                            onDragEnd = {
                                                // Save final order
                                                prefs.edit().putString("order", orderedDapps.joinToString(",") { it.guid }).apply()
                                                dragIndex = -1
                                                dragOffset = 0f
                                            },
                                            onDragCancel = { dragIndex = -1; dragOffset = 0f },
                                            onVerticalDrag = { change, amount ->
                                                change.consume()
                                                dragOffset += amount

                                                // Live swap: when dragged past half an item height, swap immediately
                                                if (dragIndex >= 0) {
                                                    val threshold = itemHeightPx * 0.5f
                                                    if (dragOffset > threshold && dragIndex < orderedDapps.size - 1) {
                                                        // Swap down
                                                        val list = orderedDapps.toMutableList()
                                                        val temp = list[dragIndex]
                                                        list[dragIndex] = list[dragIndex + 1]
                                                        list[dragIndex + 1] = temp
                                                        orderedDapps = list
                                                        dragIndex = dragIndex + 1
                                                        dragOffset -= itemHeightPx
                                                        hapticTick()
                                                    } else if (dragOffset < -threshold && dragIndex > 0) {
                                                        // Swap up
                                                        val list = orderedDapps.toMutableList()
                                                        val temp = list[dragIndex]
                                                        list[dragIndex] = list[dragIndex - 1]
                                                        list[dragIndex - 1] = temp
                                                        orderedDapps = list
                                                        dragIndex = dragIndex - 1
                                                        dragOffset += itemHeightPx
                                                        hapticTick()
                                                    }
                                                }
                                            },
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    repeat(3) {
                                        Box(
                                            Modifier
                                                .width(18.dp)
                                                .height(2.dp)
                                                .background(
                                                    if (isDragging) C.accent else C.textSecondary,
                                                    RoundedCornerShape(1.dp),
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // ── Normal mode: LazyColumn with long-press to enter edit ──
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp,
                        top = 16.dp, bottom = 80.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(orderedDapps.size, key = { orderedDapps[it].guid }) { index ->
                        val dapp = orderedDapps[index]
                        val isLaunching = launchingGuid == dapp.guid
                        Box(
                            modifier = Modifier.combinedClickable(
                                enabled = !isLaunching,
                                onClick = { handleLaunch(dapp) },
                                onLongClick = { editMode = true },
                            ),
                        ) {
                            DAppCard(
                                dapp = dapp,
                                isLaunching = isLaunching,
                                onOpen = {},
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
                stringResource(R.string.dapps_get_more),
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
            title = { Text(stringResource(R.string.dapps_uninstall), color = C.text) },
            text = {
                Text(
                stringResource(R.string.dapps_remove_confirm, uninstallTarget!!.name),
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
                    Text(stringResource(R.string.dapps_uninstall), color = C.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { uninstallTarget = null }) {
                    Text(stringResource(R.string.general_cancel), color = C.textSecondary)
                }
            },
            containerColor = C.card,
        )
    }
}

@Composable
private fun DAppCard(
    dapp: DApp,
    isLaunching: Boolean,
    onOpen: () -> Unit,
    onUninstall: () -> Unit,
) {
    val context = LocalContext.current

    // Pulse animation when launching — smooth 1s cycle, gentle alpha range
    val pulseAlpha by if (isLaunching) {
        val transition = rememberInfiniteTransition(label = "pulse")
        transition.animateFloat(
            initialValue = 0.15f,
            targetValue = 0.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = EaseInOutCubic),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseAlpha",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLaunching) C.accent.copy(alpha = 0.06f) else C.card
        ),
        border = if (isLaunching) {
            BorderStroke(
                width = 2.dp,
                color = C.accent.copy(alpha = pulseAlpha),
            )
        } else null,
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
