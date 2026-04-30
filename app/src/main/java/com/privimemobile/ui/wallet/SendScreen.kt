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
import androidx.compose.runtime.collectAsState
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
import com.privimemobile.chat.ChatService
import com.privimemobile.protocol.Helpers
import com.privimemobile.protocol.WalletApi
import com.privimemobile.ui.theme.C
import com.privimemobile.wallet.CurrencyManager
import com.privimemobile.wallet.WalletEventBus
import com.privimemobile.wallet.WalletManager
import com.privimemobile.wallet.assetTicker

import kotlinx.coroutines.delay
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
 * Ports SendScreen.tsx fully — includes ownNode detection, multi-asset support,
 * fee insufficiency checks, and txType passing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    onBack: () -> Unit,
    onSent: () -> Unit,
    onScanQr: () -> Unit = {},
    scannedAddress: String? = null,
    onNavigateConfirm: (address: String, amountGroth: Long, fee: Long, comment: String, assetId: Int, txType: String) -> Unit = { _, _, _, _, _, _ -> },
) {
    val beamStatus by WalletEventBus.beamStatus.collectAsState()
    val scope = rememberCoroutineScope()

    var address by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var isUsdMode by remember { mutableStateOf(false) }
    val exchangeRates by WalletEventBus.exchangeRates.collectAsState()
    val currency = CurrencyManager.getPreferredCurrency()
    val currencyRate = exchangeRates["beam_$currency"] ?: 0.0
    var comment by remember { mutableStateOf("") }
    var commentExpanded by remember { mutableStateOf(false) }

    // @handle search state
    var handleQuery by remember { mutableStateOf("") }
    var handleResults by remember { mutableStateOf<List<Pair<String, String?>>>(emptyList()) } // handle, displayName
    var showHandleDropdown by remember { mutableStateOf(false) }
    var resolvingHandle by remember { mutableStateOf(false) }
    var resolvedHandle by remember { mutableStateOf<String?>(null) } // the @handle that was resolved

    // Address validation state
    var validatingAddr by remember { mutableStateOf(false) }
    var addressValid by remember { mutableStateOf<Boolean?>(null) }
    var addrType by remember { mutableStateOf<AddrType?>(null) }
    var sendOffline by remember { mutableStateOf(false) }
    var validateReqId by remember { mutableIntStateOf(0) }

    // Own node / mobile protocol detection
    var ownNode by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            val trusted = WalletManager.walletInstance?.isConnectionTrusted() ?: false
            val mobileProtocol = com.privimemobile.protocol.SecureStorage.getString("node_mode") == "mobile"
            ownNode = trusted || mobileProtocol
            if (ownNode) sendOffline = true // default offline for own/mobile node
        } catch (_: Exception) {}
    }

    // Per-asset balances + asset info for picker
    val assetBalanceMap = remember { mutableStateMapOf<Int, com.privimemobile.wallet.WalletStatusEvent>().apply {
        putAll(WalletEventBus.assetBalances)
    }}
    LaunchedEffect(Unit) {
        WalletEventBus.walletStatus.collect { event -> assetBalanceMap[event.assetId] = event }
    }
    val assetInfoMap = remember { mutableStateMapOf<Int, com.privimemobile.wallet.AssetInfoEvent>().apply {
        putAll(WalletEventBus.assetInfoCache)
    }}
    LaunchedEffect(Unit) {
        WalletEventBus.assetInfo.collect { event -> assetInfoMap[event.id] = event }
    }

    // Build asset list: BEAM + any other assets with balance
    data class AssetOption(val assetId: Int, val available: Long, val ticker: String)
    val assetList = remember(assetBalanceMap.size, assetInfoMap.size) {
        val list = mutableListOf<AssetOption>()
        val beam = assetBalanceMap[0]
        val beamTotal = (beam?.available ?: 0) + (beam?.shielded ?: 0)
        list.add(AssetOption(0, beamTotal, "BEAM"))
        assetBalanceMap.filterKeys { it != 0 }.forEach { (id, status) ->
            val total = status.available + status.shielded
            if (total > 0 || status.sending > 0 || status.receiving > 0) {
                list.add(AssetOption(id, total, assetTicker(id)))
            }
        }
        list.sortedBy { it.assetId }
    }

    var selectedAssetId by remember { mutableIntStateOf(0) }
    var assetPickerOpen by remember { mutableStateOf(false) }
    val selectedAsset = assetList.find { it.assetId == selectedAssetId } ?: assetList[0]
    val ticker = selectedAsset.ticker
    val assetAvailable = selectedAsset.available
    val beamAvailable = (assetBalanceMap[0]?.let { it.available + it.shielded }) ?: (beamStatus.available + beamStatus.shielded)

    // Debounced @handle search
    LaunchedEffect(handleQuery) {
        if (handleQuery.length < 1) {
            handleResults = emptyList()
            showHandleDropdown = false
            return@LaunchedEffect
        }
        delay(300)
        try {
            val results = ChatService.contacts.searchOnChain(handleQuery)
            handleResults = results.map { it.handle to it.displayName }
            showHandleDropdown = handleResults.isNotEmpty()
        } catch (_: Exception) {
            handleResults = emptyList()
            showHandleDropdown = false
        }
    }

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

    // Fee depends on address type and send mode — matches RN exactly
    val fee = when {
        addrType is AddrType.Sbbs -> FEE_SBBS
        addrType is AddrType.Regular && !sendOffline -> FEE_SBBS
        else -> FEE_REGULAR
    }

    val amountGroth = if (isUsdMode && currencyRate > 0) {
        val fiatVal = amount.replace(',', '.').toDoubleOrNull() ?: 0.0
        (fiatVal / currencyRate * 100_000_000).toLong()
    } else Helpers.parseBeamToGroth(amount)

    // Validation — matches RN's 3-part check
    val assetInsufficient = amountGroth > 0 && amountGroth > assetAvailable
    val feeInsufficient = addrType != null && selectedAssetId != 0 && beamAvailable < fee
    val beamInsufficient = selectedAssetId == 0 && amountGroth > 0 && (amountGroth + fee) > beamAvailable
    val insufficient = assetInsufficient || feeInsufficient || beamInsufficient
    val canNext = addressValid == true && amountGroth > 0 && !insufficient && !validatingAddr

    val amountError = when {
        amount.isEmpty() -> ""
        (amount.replace(',', '.').toDoubleOrNull() ?: 0.0) <= 0.0 -> "Amount must be greater than 0"
        assetInsufficient -> "Insufficient $ticker balance"
        feeInsufficient -> "Insufficient BEAM for fee (need ${Helpers.formatBeam(fee)} BEAM)"
        beamInsufficient -> "Insufficient funds \u2014 need ${Helpers.formatBeam(amountGroth + fee - beamAvailable)} more BEAM"
        else -> ""
    }

    val addressBorderColor = when (addressValid) {
        true -> C.incoming  // matches RN C.received (green/teal)
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
                        resolvedHandle = null
                        val trimmed = text.trim()

                        // Detect @handle mode
                        if (trimmed.startsWith("@")) {
                            val query = trimmed.removePrefix("@")
                            handleQuery = query
                            addrType = null
                            addressValid = null
                            validatingAddr = false
                            return@OutlinedTextField
                        }

                        // Normal address mode
                        handleQuery = ""
                        showHandleDropdown = false
                        handleResults = emptyList()

                        if (trimmed.length < 20) {
                            addrType = null
                            addressValid = null
                            validatingAddr = false
                            return@OutlinedTextField
                        }
                        validatingAddr = true
                        addressValid = null
                        addrType = null
                        validateAddress(trimmed) { validateReqId = it }
                    },
                    placeholder = {
                        Text("Paste address or @handle", color = C.textMuted)
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

            // @handle dropdown
            if (showHandleDropdown && handleResults.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = C.card),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(C.border)
                    ),
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        handleResults.take(5).forEach { (handle, displayName) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // Resolve handle to wallet address
                                        resolvingHandle = true
                                        showHandleDropdown = false
                                        handleResults = emptyList()
                                        handleQuery = ""
                                        scope.launch {
                                            try {
                                                val contact = ChatService.contacts.resolveHandle(handle)
                                                val walletId = contact?.walletId
                                                if (walletId != null) {
                                                    val normalized = Helpers.normalizeWalletId(walletId) ?: walletId
                                                    address = normalized
                                                    resolvedHandle = "@$handle"
                                                    // Trigger address validation
                                                    validatingAddr = true
                                                    addressValid = null
                                                    addrType = null
                                                    validateAddress(normalized) { validateReqId = it }
                                                } else {
                                                    address = "@$handle"
                                                    addressValid = false
                                                }
                                            } catch (_: Exception) {
                                                address = "@$handle"
                                                addressValid = false
                                            }
                                            resolvingHandle = false
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "@$handle",
                                    color = C.accent,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (displayName != null) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        displayName,
                                        color = C.textMuted,
                                        fontSize = 13.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Resolved handle indicator
            if (resolvedHandle != null && addressValid == true) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Text(
                        "Sending to $resolvedHandle",
                        color = C.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Resolving handle spinner
            if (resolvingHandle) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = C.accent,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Resolving handle...", color = C.textSecondary, fontSize = 12.sp)
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
                                    if (addrType is AddrType.Sbbs) C.warning else C.incoming
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

            // === ASSET SELECTOR === (tappable, opens picker if multiple assets)
            Text(
                "ASSET",
                color = C.textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(C.card, RoundedCornerShape(12.dp))
                    .border(1.dp, C.border, RoundedCornerShape(12.dp))
                    .clickable { if (assetList.size > 1) assetPickerOpen = true }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.privimemobile.ui.components.AssetIcon(assetId = selectedAssetId, ticker = ticker, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(ticker, color = C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Available: ${Helpers.formatBeam(assetAvailable)} $ticker",
                        color = C.textMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (assetList.size > 1) {
                    Text("\u25BE", color = C.textSecondary, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }

            // Non-BEAM: show BEAM available for fee
            if (selectedAssetId != 0) {
                Text(
                    "BEAM available for fee: ${Helpers.formatBeam(beamAvailable)} BEAM",
                    color = C.textMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Spacer(Modifier.height(20.dp))

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
                        val cleaned = text.replace(Regex("[^0-9.,]"), "")
                        val sepCount = cleaned.count { it == '.' || it == ',' }
                        if (sepCount > 1) return@OutlinedTextField
                        val sep = cleaned.firstOrNull { it == '.' || it == ',' }
                        if (sep != null) {
                            val parts = cleaned.split(sep)
                            if (parts.size == 2 && parts[1].length > MAX_DECIMALS) return@OutlinedTextField
                        }
                        amount = cleaned
                    },
                    placeholder = { Text("0", color = C.textMuted) },
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
                if (selectedAssetId == 0 && currencyRate > 0) {
                    // Toggle BEAM / fiat currency
                    Text(
                        if (isUsdMode) currency.uppercase() else "BEAM",
                        color = C.accent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .clickable { isUsdMode = !isUsdMode; amount = "" },
                    )
                } else {
                    Text(
                        ticker,
                        color = C.textSecondary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }

            // Conversion display
            if (selectedAssetId == 0 && currencyRate > 0 && amount.isNotEmpty()) {
                val displayVal = amount.replace(',', '.').toDoubleOrNull() ?: 0.0
                if (displayVal > 0) {
                    val convText = if (isUsdMode) {
                        "≈ ${Helpers.formatBeam(amountGroth)} BEAM"
                    } else {
                        val fiat = displayVal * currencyRate
                        "≈ ${CurrencyManager.formatFiat(fiat, currency)}"
                    }
                    Text(
                        convText,
                        color = C.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            // Available + Send All — matches RN layout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Available: ${Helpers.formatBeam(assetAvailable)} $ticker",
                    color = C.textMuted,
                    fontSize = 13.sp,
                )
                TextButton(
                    onClick = {
                        // Send All — BEAM: subtract fee; other assets: send full available
                        if (selectedAssetId == 0) {
                            val maxAmount = beamAvailable - fee
                            if (maxAmount > 0) {
                                amount = if (isUsdMode && currencyRate > 0) {
                                    val maxFiat = (maxAmount / 100_000_000.0) * currencyRate
                                    String.format("%.2f", maxFiat).trimEnd('0').trimEnd { it == '.' || it == ',' }
                                } else {
                                    Helpers.formatBeam(maxAmount)
                                }
                            }
                        } else {
                            if (assetAvailable > 0) amount = Helpers.formatBeam(assetAvailable)
                        }
                    },
                    enabled = assetAvailable > 0,
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

            // === SEND MODE TOGGLE — only for regular addresses on own node (matches RN) ===
            AnimatedVisibility(
                visible = addrType is AddrType.Regular && ownNode,
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
                            color = C.textMuted,
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
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
                                color = C.textMuted,
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
                        Text("Local note (not sent on-chain)", color = C.textMuted)
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
                    // Determine txType like RN: SBBS = 'regular', otherwise depends on sendOffline
                    val txType = if (addrType is AddrType.Sbbs) "regular"
                        else if (sendOffline) "offline"
                        else "regular"
                    onNavigateConfirm(
                        address.trim(),
                        amountGroth,
                        fee,
                        comment.trim(),
                        selectedAssetId,
                        txType,
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

    // === ASSET PICKER MODAL ===
    if (assetPickerOpen) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { assetPickerOpen = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = C.card),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "SELECT ASSET",
                        color = C.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    assetList.forEach { asset ->
                        val isSelected = asset.assetId == selectedAssetId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0x1425D4D0) else Color.Transparent)
                                .clickable {
                                    selectedAssetId = asset.assetId
                                    amount = ""
                                    assetPickerOpen = false
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            com.privimemobile.ui.components.AssetIcon(assetId = asset.assetId, ticker = asset.ticker, size = 36.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    asset.ticker,
                                    color = if (isSelected) C.accent else C.text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "${Helpers.formatBeam(asset.available)} ${asset.ticker}",
                                    color = C.textMuted,
                                    fontSize = 12.sp,
                                )
                            }
                            if (isSelected) {
                                Text("\u2713", color = C.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
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
    WalletManager.callWalletApi(payload)
}
