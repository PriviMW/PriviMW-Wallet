package com.privimemobile.ui.wallet

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import androidx.compose.ui.res.stringResource
import com.privimemobile.R
import com.privimemobile.protocol.SecureStorage
import com.privimemobile.protocol.WalletApi
import com.privimemobile.ui.theme.C
import com.privimemobile.wallet.NodeConnectionEvent
import com.privimemobile.wallet.WalletEventBus
import com.privimemobile.wallet.WalletManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Receive screen — address type selection, QR code display, copy, share, generate new.
 *
 * Fully ports ReceiveScreen.tsx including:
 * - Own node vs remote node detection (different tabs)
 * - Offline address type (own node only)
 * - Dynamic max privacy lock hours from settings
 * - Remote node info banner
 */

private data class TabInfo(
    val key: String,
    @androidx.annotation.StringRes val labelResId: Int,
    @androidx.annotation.StringRes val descResId: Int,
    val feeGroth: Long,
)

// Tabs when connected to own node (offline + sbbs + max privacy)
private val OWN_NODE_TABS = listOf(
    TabInfo("offline", R.string.receive_tab_regular, R.string.receive_desc_regular_default, 1_100_000L),
    TabInfo("sbbs", R.string.receive_tab_sbbs, R.string.receive_desc_sbbs, 100_000L),
    TabInfo("max_privacy", R.string.receive_tab_max_privacy, R.string.receive_desc_max_privacy, 1_100_000L),
)

// Tabs when connected to remote node (regular + sbbs only)
private val REMOTE_NODE_TABS = listOf(
    TabInfo("regular", R.string.receive_tab_regular, R.string.receive_desc_regular_remote, 1_100_000L),
    TabInfo("sbbs", R.string.receive_tab_sbbs, R.string.receive_desc_sbbs, 100_000L),
)

@Composable
fun ReceiveScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var ownNode by remember { mutableStateOf(false) }
    val nodeConn by WalletEventBus.nodeConnection.collectAsState(initial = NodeConnectionEvent(false))
    val isFallback = nodeConn.isFallback
    var addressType by remember { mutableStateOf("regular") }
    var currentAddress by remember { mutableStateOf<String?>(null) }
    var generating by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var lockHours by remember { mutableIntStateOf(72) }

    // SBBS request tracking
    var sbbsReqId by remember { mutableIntStateOf(0) }

    // Load max privacy lock time setting
    LaunchedEffect(Unit) {
        val hours = SecureStorage.getInt("max_privacy_hours", 72)
        lockHours = hours
    }

    // Listen for API results (SBBS create_address + native address generation)
    LaunchedEffect(Unit) {
        WalletEventBus.apiResult.collect { json ->
            try {
                val parsed = JSONObject(json)
                val id = parsed.optInt("id", -1)
                if (id != sbbsReqId) return@collect
                val result = parsed.opt("result")
                val addr = when (result) {
                    is String -> result
                    is JSONObject -> result.optString("address", "").ifEmpty { result.optString("token", "") }
                    else -> null
                }
                if (addr != null && addr.length > 10) {
                    currentAddress = addr
                    generating = false
                }
            } catch (_: Exception) {}
        }
    }

    // Listen for native address generation events (onRegularAddress, onOfflineAddress, onMaxPrivacyAddress)
    LaunchedEffect(Unit) {
        WalletEventBus.addresses.collect { event ->
            if (event.own) {
                try {
                    val json = event.json.trim()
                    when {
                        // JSON array of address objects (from getAddresses) — ignore for receive screen
                        json.startsWith("[") -> { /* full list refresh, not a generated address */ }
                        // JSON object (single address DTO) — extract the address/token field
                        json.startsWith("{") -> {
                            val obj = JSONObject(json)
                            val addr = obj.optString("address", "")
                                .ifEmpty { obj.optString("token", "") }
                                .ifEmpty { obj.optString("walletID", "") }
                            if (addr.length > 10) {
                                currentAddress = addr
                                generating = false
                            }
                        }
                        // Plain address string (from onRegularAddress etc.)
                        json.length > 10 -> {
                            currentAddress = json
                            generating = false
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // Generate address function — matches RN exactly
    fun generateAddress(type: String) {
        generating = true
        currentAddress = null
        copied = false

        val wallet = WalletManager.walletInstance

        when (type) {
            "offline" -> {
                // Own node: use native generateOfflineAddress
                wallet?.generateOfflineAddress(0, 0)
            }
            "max_privacy" -> {
                wallet?.generateMaxPrivacyAddress(0, 0)
            }
            "regular" -> {
                wallet?.generateRegularAddress(0, 0)
            }
            "sbbs" -> {
                val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
                sbbsReqId = id
                val payload = JSONObject().apply {
                    put("jsonrpc", "2.0")
                    put("id", id)
                    put("method", "create_address")
                    put("params", JSONObject().apply {
                        put("expiration", "never")
                        put("comment", "sbbs receive")
                    })
                }.toString()
                WalletManager.callWalletApi(payload)
            }
        }

        // Safety timeout
        scope.launch {
            delay(8000)
            if (generating) generating = false
        }
    }

    // Detect own node / mobile protocol vs remote — then generate initial address
    LaunchedEffect(Unit) {
        try {
            val trusted = WalletManager.walletInstance?.isConnectionTrusted() ?: false
            val mobileProtocol = SecureStorage.getString("node_mode") == "mobile"
            ownNode = trusted || mobileProtocol
            if (ownNode) {
                addressType = "offline"
                generateAddress("offline")
            } else {
                addressType = "regular"
                generateAddress("regular")
            }
        } catch (_: Exception) {
            generateAddress("regular")
        }
    }

    val tabs = if (ownNode) OWN_NODE_TABS else REMOTE_NODE_TABS
    val currentTab = tabs.find { it.key == addressType } ?: tabs[0]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Back button
        TextButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(start = 4.dp, top = 4.dp),
        ) {
            Text(stringResource(R.string.dapps_back_button), color = C.textSecondary)
        }

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // === TYPE SELECTOR TABS ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(C.card, RoundedCornerShape(12.dp))
                    .padding(4.dp),
            ) {
                tabs.forEach { tab ->
                    val selected = addressType == tab.key
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .clickable {
                                if (addressType != tab.key) {
                                    addressType = tab.key
                                    generateAddress(tab.key)
                                }
                            },
                        shape = RoundedCornerShape(9.dp),
                        color = if (selected) C.bg else Color.Transparent,
                    ) {
                        Text(
                            stringResource(tab.labelResId),
                            color = if (selected) C.accent else C.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 9.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Type description
            Text(
                stringResource(currentTab.descResId),
                color = C.textMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )
            Text(
                stringResource(R.string.receive_fee_regular_format, com.privimemobile.protocol.Helpers.formatBeam(currentTab.feeGroth)),
                color = C.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            // Max Privacy lock time warning — dynamic from settings
            if (addressType == "max_privacy") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x1AFF9800),
                    border = BorderStroke(1.dp, Color(0x4DFF9800)),
                ) {
                    Text(
                        if (lockHours > 0)
                            stringResource(R.string.receive_max_privacy_locked, lockHours)
                        else
                            stringResource(R.string.receive_max_privacy_indefinite),
                        color = Color(0xFFFF9800),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }

            // Remote node / fallback info banner
            if (!ownNode || isFallback) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isFallback) Color(0x1AFFA726) else Color(0x1AFFC107),
                    border = BorderStroke(1.dp, if (isFallback) Color(0x4DFFA726) else Color(0x4DFFC107)),
                ) {
                    Text(
                        if (isFallback)
                            stringResource(R.string.receive_fallback_warning)
                        else
                            stringResource(R.string.receive_remote_node_warning),
                        color = if (isFallback) Color(0xFFFFA726) else Color(0xFFFFC107),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }

            // === QR CODE ===
            Box(
                modifier = Modifier.padding(bottom = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (generating) {
                    Surface(
                        modifier = Modifier.size(236.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = C.card,
                        border = BorderStroke(1.dp, C.border),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator(color = C.accent)
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.receive_generating_address), color = C.textSecondary, fontSize = 13.sp)
                        }
                    }
                } else if (currentAddress != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        shadowElevation = 6.dp,
                    ) {
                        val qrBitmap = remember(currentAddress) { generateQrCode(currentAddress!!, 250) }
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.qr_scanner_title),
                                modifier = Modifier
                                    .padding(18.dp)
                                    .size(200.dp),
                            )
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier.size(236.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = C.card,
                        border = BorderStroke(1.dp, C.border),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Text(stringResource(R.string.receive_no_address), color = C.textMuted, fontSize = 14.sp)
                        }
                    }
                }
            }

            // === ADDRESS TEXT ===
            if (currentAddress != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = C.card,
                    border = BorderStroke(1.dp, C.border),
                ) {
                    SelectionContainer {
                        Text(
                            currentAddress!!,
                            color = C.accent,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }

                // === COPY + NEW ADDRESS ===
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = {
                            clipboard.setText(AnnotatedString(currentAddress!!))
                            copied = true
                            scope.launch {
                                delay(2000)
                                copied = false
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = C.accent,
                        ),
                    ) {
                        Text(
                            if (copied) stringResource(R.string.receive_copy_btn_copied) else stringResource(R.string.receive_copy_btn_label),
                            color = C.textDark,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    OutlinedButton(
                        onClick = { generateAddress(addressType) },
                        enabled = !generating,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, C.border),
                    ) {
                        Text(
                            stringResource(R.string.receive_generate_new_btn),
                            color = C.textSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // === SHARE BUTTON ===
                OutlinedButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, currentAddress)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.receive_share_chooser)))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, C.accent),
                ) {
                    Text(
                        "\u2191",
                        color = C.accent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.receive_share_btn).uppercase(),
                        color = C.accent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

/** Generate a QR code bitmap using ZXing. */
private fun generateQrCode(text: String, size: Int): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val matrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        bitmap
    } catch (_: Exception) {
        null
    }
}
