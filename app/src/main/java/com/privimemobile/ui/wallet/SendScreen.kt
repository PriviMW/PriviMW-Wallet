package com.privimemobile.ui.wallet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.protocol.Helpers
import com.privimemobile.protocol.WalletApi
import com.privimemobile.ui.theme.C
import com.privimemobile.wallet.WalletEventBus
import com.privimemobile.wallet.WalletStatusEvent
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val FEE_SBBS = 100_000L       // 0.001 BEAM
private const val FEE_REGULAR = 1_100_000L   // 0.011 BEAM
private const val MAX_DECIMALS = 8

private sealed class AddrType {
    data object Sbbs : AddrType()
    data object Regular : AddrType()
    data object MaxPrivacy : AddrType()
}

private fun addrTypeLabel(type: AddrType?): String = when (type) {
    is AddrType.Sbbs -> "SBBS (Online)"
    is AddrType.Regular -> "Regular (Offline)"
    is AddrType.MaxPrivacy -> "Max Privacy"
    null -> ""
}

/**
 * Full send screen — address input, validation, amount, asset selector,
 * fee display, send mode toggle, comment.
 *
 * Ports SendScreen.tsx fully.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    onBack: () -> Unit,
    onSent: () -> Unit,
    onScanQr: () -> Unit = {},
    scannedAddress: String? = null,
    onNavigateConfirm: (address: String, amountGroth: Long, fee: Long, comment: String, assetId: Int) -> Unit = { _, _, _, _, _ -> },
) {
    val walletStatus by WalletEventBus.walletStatus.collectAsState(
        initial = WalletStatusEvent(0, 0, 0, 0)
    )
    val scope = rememberCoroutineScope()

    var address by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var commentExpanded by remember { mutableStateOf(false) }

    // Address validation state
    var validatingAddr by remember { mutableStateOf(false) }
    var addressValid by remember { mutableStateOf<Boolean?>(null) }
    var addrType by remember { mutableStateOf<AddrType?>(null) }
    var sendOffline by remember { mutableStateOf(false) }
    var validateReqId by remember { mutableIntStateOf(0) }

    // Asset selection
    var selectedAssetId by remember { mutableIntStateOf(0) }

    // Listen for scanned address from QR scanner
    LaunchedEffect(scannedAddress) {
        if (scannedAddress != null && scannedAddress.isNotEmpty()) {
            address = scannedAddress
            validateAddress(scannedAddress) { id ->
                validateReqId = id
            }
            validatingAddr = true
            addressValid = null
            addrType = null
        }
    }

    // Listen for validate_address API response
    LaunchedEffect(Unit) {
        WalletEventBus.apiResult.collect { json ->
            try {
                val parsed = JSONObject(json)
                val id = parsed.optInt("id", -1)
                if (id != validateReqId) return@collect
                val result = parsed.optJSONObject("result") ?: return@collect
                if (!result.optBoolean("is_valid", false)) {
                    addressValid = false
                    validatingAddr = false
                    return@collect
                }
                addressValid = true
                val apiType = result.optString("type", "")
                addrType = when {
                    apiType == "max_privacy" || apiType == "max_privacy_offline" -> AddrType.MaxPrivacy
                    Regex("^[0-9a-fA-F]{62,68}$").matches(address.trim()) -> AddrType.Sbbs
                    else -> AddrType.Regular
                }
                validatingAddr = false
            } catch (_: Exception) {}
        }
    }

    // Fee depends on address type
    val fee = when {
        addrType is AddrType.Sbbs -> FEE_SBBS
        addrType is AddrType.Regular && !sendOffline -> FEE_SBBS
        else -> FEE_REGULAR
    }

    val amountGroth = Helpers.parseBeamToGroth(amount)
    val beamAvailable = walletStatus.available

    // Validation
    val assetInsufficient = amountGroth > 0 && amountGroth > beamAvailable
    val beamInsufficient = selectedAssetId == 0 && amountGroth > 0 && (amountGroth + fee) > beamAvailable
    val insufficient = assetInsufficient || beamInsufficient
    val canNext = addressValid == true && amountGroth > 0 && !insufficient && !validatingAddr

    val amountError = when {
        amount.isEmpty() -> ""
        (amount.toDoubleOrNull() ?: 0.0) <= 0.0 -> "Amount must be greater than 0"
        assetInsufficient -> "Insufficient BEAM balance"
        beamInsufficient -> "Insufficient funds \u2014 need ${Helpers.formatBeam(amountGroth + fee - beamAvailable)} more BEAM"
        else -> ""
    }

    val addressBorderColor = when (addressValid) {
        true -> Color(0xFF25D4D0)
        false -> C.error
        null -> C.border
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg)
            .verticalScroll(rememberScrollState()),
    ) {
        // Back button
        TextButton(
            onClick = onBack,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        ) {
            Text("< Back", color = C.textSecondary)
        }

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            // === ADDRESS ===
            Text(
                "SEND TO",
                color = C.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, addressBorderColor, RoundedCornerShape(12.dp))
                    .background(C.card, RoundedCornerShape(12.dp)),
                verticalAlignment = Alignment.Top,
            ) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { text ->
                        address = text
                        val trimmed = text.trim()
                        if (trimmed.length < 20) {
                            addrType = null
                            addressValid = null
                            validatingAddr = false
                            return@OutlinedTextField
                        }
                        validatingAddr = true
                        addressValid = null
                        addrType = null
                        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
                        validateReqId = id
                        validateAddress(trimmed) { validateReqId = it }
                    },
                    placeholder = {
                        Text("Paste address or token", color = C.textSecondary)
                    },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = C.accent,
                        focusedTextColor = C.text,
                        unfocusedTextColor = C.text,
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    ),
                    maxLines = 3,
                )
                IconButton(
                    onClick = onScanQr,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text("\u2399", color = C.accent, fontSize = 22.sp)
                }
            }

            // Address validation hint
            when {
                validatingAddr -> {
                    Text(
                        "Validating...",
                        color = C.textSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                addressValid == false && address.trim().length >= 20 -> {
                    Text(
                        "Invalid address",
                        color = C.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                addressValid == true && addrType != null -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (addrType is AddrType.Sbbs) C.warning else Color(0xFF25D4D0)
                                ),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            addrTypeLabel(addrType),
                            color = C.text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (addrType is AddrType.Sbbs) {
                            Text(
                                "  \u00B7 Both wallets must be online",
                                color = C.warning,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // === AMOUNT ===
            Text(
                "AMOUNT",
                color = C.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.5.dp,
                        if (amountError.isNotEmpty()) C.error else C.border,
                        RoundedCornerShape(12.dp),
                    )
                    .background(C.card, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { text ->
                        val cleaned = text.replace(Regex("[^0-9.]"), "")
                        val parts = cleaned.split(".")
                        if (parts.size > 2) return@OutlinedTextField
                        if (parts.size == 2 && parts[1].length > MAX_DECIMALS) return@OutlinedTextField
                        amount = cleaned
                    },
                    placeholder = { Text("0", color = C.textSecondary) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = C.accent,
                        focusedTextColor = C.outgoing,
                        unfocusedTextColor = C.outgoing,
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    singleLine = true,
                )
                Text(
                    "BEAM",
                    color = C.textSecondary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }

            // Available + Send All
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Available: ${Helpers.formatBeam(beamAvailable)} BEAM",
                    color = C.textSecondary,
                    fontSize = 13.sp,
                )
                TextButton(
                    onClick = {
                        val maxAmount = beamAvailable - fee
                        if (maxAmount > 0) {
                            amount = Helpers.formatBeam(maxAmount)
                        }
                    },
                    enabled = beamAvailable > 0,
                ) {
                    Text(
                        "SEND ALL",
                        color = if (beamAvailable > 0) C.outgoing else C.outgoing.copy(alpha = 0.35f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (amountError.isNotEmpty()) {
                Text(
                    amountError,
                    color = C.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            // === SEND MODE TOGGLE (for regular addresses) ===
            AnimatedVisibility(
                visible = addrType is AddrType.Regular,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = C.card),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(C.border)
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "SEND MODE",
                            color = C.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SendModeTab(
                                label = "Offline",
                                selected = sendOffline,
                                modifier = Modifier.weight(1f),
                                onClick = { sendOffline = true },
                            )
                            SendModeTab(
                                label = "Online",
                                selected = !sendOffline,
                                modifier = Modifier.weight(1f),
                                onClick = { sendOffline = false },
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (sendOffline) "Recipient does not need to be online"
                            else "Both wallets must be online within 12h",
                            color = C.textSecondary,
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            // === FEE ===
            AnimatedVisibility(
                visible = addrType != null,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = C.card),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(C.border)
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                "TRANSACTION FEE",
                                color = C.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                            )
                            Text(
                                if (addrType is AddrType.Sbbs || !sendOffline) "Online \u2014 lower fee"
                                else "Offline \u2014 higher fee",
                                color = C.textSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                        Text(
                            "${Helpers.formatBeam(fee)} BEAM",
                            color = C.text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // === COMMENT (collapsible) ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
                    .clickable { commentExpanded = !commentExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "COMMENT",
                    color = C.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
                Text(
                    if (commentExpanded) "\u25B4" else "\u25BE",
                    color = C.textSecondary,
                    fontSize = 18.sp,
                )
            }

            AnimatedVisibility(
                visible = commentExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                OutlinedTextField(
                    value = comment,
                    onValueChange = { if (it.length <= 1024) comment = it },
                    placeholder = {
                        Text("Local note (not sent on-chain)", color = C.textSecondary)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .padding(top = 4.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = C.border,
                        unfocusedBorderColor = C.border,
                        cursorColor = C.accent,
                        focusedTextColor = C.text,
                        unfocusedTextColor = C.text,
                        focusedContainerColor = C.card,
                        unfocusedContainerColor = C.card,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )
            }

            // === NEXT BUTTON ===
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = {
                    onNavigateConfirm(
                        address.trim(),
                        amountGroth,
                        fee,
                        comment.trim(),
                        selectedAssetId,
                    )
                },
                enabled = canNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = C.outgoing,
                    disabledContainerColor = C.outgoing.copy(alpha = 0.3f),
                ),
            ) {
                Text(
                    "NEXT",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SendModeTab(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = if (selected) C.accent else Color(0x0DFFFFFF),
        border = if (selected) null else ButtonDefaults.outlinedButtonBorder(true).copy(
            brush = androidx.compose.ui.graphics.SolidColor(C.border)
        ),
    ) {
        Text(
            label,
            color = if (selected) Color.White else C.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(vertical = 10.dp)
                .fillMaxWidth()
                .wrapContentWidth(Alignment.CenterHorizontally),
        )
    }
}

/** Validate an address via the wallet API. Returns the request ID used. */
private fun validateAddress(address: String, onIdAssigned: (Int) -> Unit) {
    val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
    onIdAssigned(id)
    val payload = JSONObject().apply {
        put("jsonrpc", "2.0")
        put("id", id)
        put("method", "validate_address")
        put("params", JSONObject().apply {
            put("address", address)
        })
    }.toString()
    com.privimemobile.wallet.WalletManager.callWalletApi(payload)
}
