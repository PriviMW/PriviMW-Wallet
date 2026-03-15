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
import com.privimemobile.protocol.WalletApi
import com.privimemobile.ui.theme.C
import com.privimemobile.wallet.WalletEventBus
import com.privimemobile.wallet.WalletManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Receive screen — address type selection (Regular/SBBS/Max Privacy),
 * QR code display, copy, share, generate new address.
 *
 * Fully ports ReceiveScreen.tsx.
 */
private enum class AddressType(val label: String, val desc: String, val fee: String) {
    REGULAR("Regular", "Default address. Sender can pay online or offline.", "Fee: 0.011 BEAM"),
    SBBS("SBBS", "Classic short address. Both wallets must be open simultaneously.", "Fee: 0.001 BEAM"),
    MAX_PRIVACY("Max Privacy", "Maximum anonymity. Sender does not need to be online.", "Fee: 0.011 BEAM"),
}

@Composable
fun ReceiveScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var addressType by remember { mutableStateOf(AddressType.REGULAR) }
    var currentAddress by remember { mutableStateOf<String?>(null) }
    var generating by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    val availableTabs = listOf(AddressType.REGULAR, AddressType.SBBS, AddressType.MAX_PRIVACY)

    // Listen for generated address responses
    var sbbsReqId by remember { mutableIntStateOf(0) }

    // Listen for API results (for SBBS create_address)
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

    // Listen for address events from native module
    LaunchedEffect(Unit) {
        WalletEventBus.addresses.collect { event ->
            if (event.own) {
                try {
                    val arr = org.json.JSONArray(event.json)
                    if (arr.length() > 0) {
                        val addr = arr.optString(0, "")
                        if (addr.length > 10) {
                            currentAddress = addr
                            generating = false
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // Generate address function
    fun generateAddress(type: AddressType) {
        generating = true
        currentAddress = null
        copied = false

        when (type) {
            AddressType.SBBS -> {
                val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
                sbbsReqId = id
                WalletApi.call("create_address", mapOf(
                    "type" to "regular",
                    "expiration" to "never",
                    "comment" to "sbbs receive",
                )) { result ->
                    val addr = result["result"] as? String
                        ?: result["address"] as? String
                        ?: result["token"] as? String
                    if (addr != null && addr.length > 10) {
                        currentAddress = addr
                    }
                    generating = false
                }
            }
            AddressType.REGULAR -> {
                WalletApi.call("create_address", mapOf(
                    "type" to "regular",
                    "label" to "PriviMW Receive",
                    "expiration" to "auto",
                )) { result ->
                    val addr = result["address"] as? String ?: result["token"] as? String ?: ""
                    if (addr.length > 10) currentAddress = addr
                    generating = false
                }
            }
            AddressType.MAX_PRIVACY -> {
                WalletApi.call("create_address", mapOf(
                    "type" to "max_privacy",
                    "label" to "PriviMW Max Privacy",
                    "expiration" to "auto",
                )) { result ->
                    val addr = result["address"] as? String ?: result["token"] as? String ?: ""
                    if (addr.length > 10) currentAddress = addr
                    generating = false
                }
            }
        }

        // Safety timeout
        scope.launch {
            delay(8000)
            if (generating) generating = false
        }
    }

    // Generate initial address on mount
    LaunchedEffect(Unit) {
        generateAddress(addressType)
    }

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
            Text("< Back", color = C.textSecondary)
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
                availableTabs.forEach { tab ->
                    val selected = addressType == tab
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .clickable {
                                if (addressType != tab) {
                                    addressType = tab
                                    generateAddress(tab)
                                }
                            },
                        shape = RoundedCornerShape(9.dp),
                        color = if (selected) C.bg else Color.Transparent,
                    ) {
                        Text(
                            tab.label,
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
                addressType.desc,
                color = C.textSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )
            Text(
                addressType.fee,
                color = C.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            // Max Privacy warning
            if (addressType == AddressType.MAX_PRIVACY) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x1AFF9800),
                    border = BorderStroke(1.dp, Color(0x4DFF9800)),
                ) {
                    Text(
                        "Funds will be locked for a minimum of 72h. Longer lock time = stronger privacy. You can change this in Settings.",
                        color = Color(0xFFFF9800),
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
                            Text("Generating address...", color = C.textSecondary, fontSize = 13.sp)
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
                                contentDescription = "QR Code",
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
                            Text("No address", color = C.textSecondary, fontSize = 14.sp)
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
                            if (copied) "\u2713  COPIED" else "COPY",
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
                            "New address",
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
                        context.startActivity(Intent.createChooser(shareIntent, "Share Address"))
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
                        "SHARE ADDRESS",
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
