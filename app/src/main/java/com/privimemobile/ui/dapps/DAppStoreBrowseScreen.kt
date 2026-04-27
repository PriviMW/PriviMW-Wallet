package com.privimemobile.ui.dapps

import android.net.Uri
import kotlin.coroutines.resumeWithException
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import coil.ImageLoader
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import java.nio.ByteBuffer
import kotlinx.coroutines.withContext

/**
 * DApp Store browse screen -- queries on-chain registry, supports install
 * from bundled assets or IPFS, and sideloading .dapp files.
 *
 * Fully ports DAppStoreBrowseScreen.tsx.
 * - Pull to refresh
 * - On-chain query via DAppStore shader
 * - Install from bundled assets
 * - Install from IPFS (via wallet IPFS API)
 * - Sideload from local file
 * - Loading/refresh states
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DAppStoreBrowseScreen(
    onBack: () -> Unit = {},
    onOpenPublishers: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var available by remember { mutableStateOf<List<AvailableDApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    // Load available DApps
    var installedMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    fun loadAvailable() {
        DAppStore.queryAvailableDApps(context) { dapps ->
            installedMap = DAppManager.getInstalled(context).associate { it.guid to it.version }
            available = dapps
            loading = false
        }
    }

    // Initial load
    LaunchedEffect(Unit) {
        DAppStore.loadShader(context)
        loadAvailable()
    }

    fun doRefresh() {
        refreshing = true
        scope.launch {
            loadAvailable()
            delay(500)
            refreshing = false
        }
    }

    // Sideload file picker
    val sideloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        // Verify it's a .dapp file
        val fileName = try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) it.getString(idx) else null
                } else null
            }
        } catch (_: Exception) { null }

        if (fileName != null && !fileName.endsWith(".dapp")) {
            message = "Please select a .dapp package file"
            return@rememberLauncherForActivityResult
        }

        installing = "sideload"
        scope.launch {
            try {
                val zipData = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("Cannot read file")
                }

                // Extract GUID from manifest.json inside the ZIP
                val guid = withContext(Dispatchers.IO) {
                    var manifestGuid: String? = null
                    try {
                        java.util.zip.ZipInputStream(zipData.inputStream()).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                if (entry.name == "manifest.json") {
                                    val json = org.json.JSONObject(zis.readBytes().toString(Charsets.UTF_8))
                                    manifestGuid = json.optString("guid").ifEmpty { null }
                                    break
                                }
                                entry = zis.nextEntry
                            }
                        }
                    } catch (_: Exception) {}
                    manifestGuid ?: fileName?.removeSuffix(".dapp")
                        ?.lowercase()
                        ?.replace(Regex("[^a-f0-9]"), "")
                        ?.padEnd(32, '0')
                        ?.take(32) ?: "sideloaded00000000000000000000"
                }

                withContext(Dispatchers.IO) {
                    DAppManager.installFromZip(
                        context,
                        guid,
                        zipData,
                        fileName?.removeSuffix(".dapp") ?: "Sideloaded DApp",
                    )
                }
                message = "DApp installed. Go back to open it."
                loadAvailable()
            } catch (e: Exception) {
                message = "Install failed: ${e.message ?: "Unknown error"}"
            }
            installing = null
        }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { doRefresh() },
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Back button
            TextButton(
                onClick = onBack,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            ) {
                Text("< Back", color = C.textSecondary)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "DApp Store",
                    color = C.text,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onOpenPublishers) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Publishers",
                        tint = C.textSecondary,
                    )
                }
            }
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
                        onClick = { sideloadLauncher.launch("application/octet-stream") },
                    )
                }

                // Empty state
                if (!loading && available.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "No additional DApps available",
                                color = C.textSecondary,
                                fontSize = 15.sp,
                            )
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

                // DApp list — sorted: bundled first, then PriviBets, then with icon (alpha), then without (alpha)
                items(available.sortedWith(compareBy<AvailableDApp> { dapp ->
                    when {
                        dapp.bundledAsset.isNotEmpty() -> 0       // Bundled first
                        dapp.name.contains("PriviBets", ignoreCase = true) -> 1  // PriviBets second
                        dapp.icon.isNotBlank() -> 2               // Has icon
                        else -> 3                                  // No icon last
                    }
                }.thenBy { it.name.lowercase() }), key = { it.guid }) { dapp ->
                    val localVer = installedMap[dapp.guid]
                    val isInstalled = localVer != null
                    val hasUpdate = isInstalled && localVer != dapp.version
                    DAppStoreCard(
                        dapp = dapp,
                        installing = installing == dapp.guid,
                        isInstalled = isInstalled,
                        hasUpdate = hasUpdate,
                        onAction = {
                            if (installing != null) return@DAppStoreCard
                            installing = dapp.guid
                            scope.launch {
                                try {
                                    if (dapp.bundledAsset.isNotEmpty()) {
                                        // Install from bundled APK assets
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
                                        // Download from IPFS
                                        val zipData = DAppStore.downloadFromIpfs(dapp.ipfsCid)
                                        withContext(Dispatchers.IO) {
                                            DAppManager.installFromZip(
                                                context,
                                                dapp.guid,
                                                zipData,
                                                dapp.name,
                                                dapp.icon,
                                            )
                                        }
                                        message = "${dapp.name} installed"
                                        loadAvailable()
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

            // Toast-style message
            message?.let { msg ->
                LaunchedEffect(msg) {
                    delay(3000)
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
    isInstalled: Boolean = false,
    hasUpdate: Boolean = false,
    onAction: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = C.card),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon — render SVG from on-chain registry or fallback to letter
            val hasSvg = dapp.icon.isNotEmpty() &&
                    (dapp.icon.startsWith("<svg") || dapp.icon.startsWith("<?xml"))
            if (hasSvg) {
                val context = LocalContext.current
                val svgLoader = remember {
                    ImageLoader.Builder(context)
                        .components { add(SvgDecoder.Factory()) }
                        .build()
                }
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(ByteBuffer.wrap(dapp.icon.toByteArray()))
                        .build(),
                    imageLoader = svgLoader,
                    contentDescription = dapp.name,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            } else {
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
                if (dapp.publisherName.isNotEmpty()) {
                    Text(
                        text = dapp.publisherName,
                        color = C.accent,
                        fontSize = 11.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (dapp.publisher.isNotEmpty()) {
                    Text(
                        text = if (dapp.publisher.length > 20) {
                            "${dapp.publisher.take(8)}...${dapp.publisher.takeLast(8)}"
                        } else dapp.publisher,
                        color = C.textSecondary,
                        fontSize = 10.sp,
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

            // Action button
            Button(
                onClick = onAction,
                enabled = !installing && (!isInstalled || hasUpdate),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (hasUpdate) Color(0xFFFF9800) else if (isInstalled) C.border else C.accent,
                    disabledContainerColor = if (isInstalled && !hasUpdate) C.border else C.accent.copy(alpha = 0.5f),
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
                        when {
                            hasUpdate -> "Update"
                            isInstalled -> "Installed"
                            else -> "Install"
                        },
                        color = if (isInstalled && !hasUpdate) C.textSecondary else C.textDark,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
