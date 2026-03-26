package com.privimemobile.ui.wallet

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import com.privimemobile.protocol.Helpers
import com.privimemobile.ui.theme.C
import com.privimemobile.wallet.*

private data class PendingOfferParams(
    val sendAssetId: Int,
    val sendAmount: Long,
    val sendSname: String,
    val receiveAssetId: Int,
    val receiveAmount: Long,
    val receiveSname: String,
    val expireMinutes: Int,
)

/**
 * CreateOfferScreen — form to create a new DEX swap offer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateOfferScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val assetBalances = remember { mutableStateMapOf<Int, WalletStatusEvent>().apply { putAll(WalletEventBus.assetBalances) } }
    val assetInfoCache = WalletEventBus.assetInfoCache

    // Collect live balance updates
    LaunchedEffect(Unit) {
        WalletEventBus.walletStatus.collect { event -> assetBalances[event.assetId] = event }
    }

    // Available assets (with balance > 0)
    val availableAssets = assetBalances.filter { (_, v) -> (v.available + v.shielded) > 0 }
        .keys.sorted().toList()

    // Fetch asset info for all supported assets on mount
    LaunchedEffect(Unit) {
        listOf(7, 9, 36, 37, 38, 39, 47).forEach { id ->
            if (!assetInfoCache.containsKey(id)) WalletManager.walletInstance?.getAssetInfo(id)
        }
    }

    var sendAssetId by remember { mutableIntStateOf(0) } // default BEAM
    var receiveAssetId by remember { mutableIntStateOf(if (availableAssets.size > 1) availableAssets.first { it != 0 } else 0) }
    var sendAmountText by remember { mutableStateOf("") }
    var receiveAmountText by remember { mutableStateOf("") }
    var expireMinutes by remember { mutableIntStateOf(60) }
    var showSendPicker by remember { mutableStateOf(false) }
    var showReceivePicker by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Pending offer params — set when user taps Post, consumed when address arrives
    var pendingOffer by remember { mutableStateOf<PendingOfferParams?>(null) }

    // Listen for new address callback to complete offer creation
    LaunchedEffect(Unit) {
        WalletEventBus.newAddress.collect { addr ->
            val pending = pendingOffer ?: return@collect
            pendingOffer = null
            try {
                SwapManager.createOffer(
                    sbbsAddr = addr.walletId,
                    sbbsKeyIdx = addr.ownId,
                    sendAssetId = pending.sendAssetId,
                    sendAmount = pending.sendAmount,
                    sendSname = pending.sendSname,
                    receiveAssetId = pending.receiveAssetId,
                    receiveAmount = pending.receiveAmount,
                    receiveSname = pending.receiveSname,
                    expireMinutes = pending.expireMinutes,
                )
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "Offer posted!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            creating = false
            onBack()
        }
    }

    // Known asset tickers
    val knownTickers = mapOf(
        0 to "BEAM", 7 to "BEAMX", 9 to "TICO",
        36 to "bETH", 37 to "bUSDT", 38 to "bWBTC", 39 to "bDAI", 47 to "NPH",
    )

    fun assetTicker(id: Int): String {
        val name = knownTickers[id]
            ?: assetInfoCache[id]?.let { it.unitName?.ifEmpty { null } ?: it.shortName?.ifEmpty { null } }
            ?: "Asset"
        return if (id == 0) name else "$name ($id)"
    }

    fun assetBalance(id: Int): Long {
        val bal = assetBalances[id] ?: return 0
        return bal.available + bal.shielded
    }

    val sendBalance = assetBalance(sendAssetId)
    val sendAmountGroth = try { (sendAmountText.toDoubleOrNull() ?: 0.0) * 100_000_000 } catch (_: Exception) { 0.0 }
    val receiveAmountGroth = try { (receiveAmountText.toDoubleOrNull() ?: 0.0) * 100_000_000 } catch (_: Exception) { 0.0 }
    val canCreate = sendAmountGroth > 0 && receiveAmountGroth > 0 &&
            sendAssetId != receiveAssetId && sendAmountGroth.toLong() <= sendBalance && !creating

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Swap Offer", color = C.text) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = C.text)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = C.bg),
                windowInsets = WindowInsets(0),
            )
        },
        containerColor = C.bg,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(16.dp))

            // ---- I'M SELLING ----
            Text("I'm selling", color = C.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = C.card),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Asset picker
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showSendPicker = true },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            com.privimemobile.ui.components.AssetIcon(assetId = sendAssetId, ticker = assetTicker(sendAssetId), size = 24.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(assetTicker(sendAssetId), color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Default.ArrowDropDown, null, tint = C.textSecondary)
                    }
                    Spacer(Modifier.height(8.dp))
                    // Amount input
                    OutlinedTextField(
                        value = sendAmountText,
                        onValueChange = { sendAmountText = it.filter { c -> c.isDigit() || c == '.' } },
                        placeholder = { Text("0.00", color = C.textMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = C.text, unfocusedTextColor = C.text,
                            cursorColor = C.accent,
                            focusedBorderColor = C.accent, unfocusedBorderColor = C.border,
                        ),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Available: ${Helpers.formatBeam(sendBalance)} ${assetTicker(sendAssetId)}",
                        color = if (sendAmountGroth.toLong() > sendBalance) C.error else C.textMuted,
                        fontSize = 12.sp,
                    )
                }
            }

            // Swap direction icon
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                IconButton(onClick = {
                    val tmp = sendAssetId; sendAssetId = receiveAssetId; receiveAssetId = tmp
                    val tmpAmt = sendAmountText; sendAmountText = receiveAmountText; receiveAmountText = tmpAmt
                }) {
                    Icon(Icons.Default.SwapVert, "Swap direction", tint = C.accent, modifier = Modifier.size(32.dp))
                }
            }

            // ---- I WANT ----
            Text("I want", color = C.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = C.card),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showReceivePicker = true },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            com.privimemobile.ui.components.AssetIcon(assetId = receiveAssetId, ticker = assetTicker(receiveAssetId), size = 24.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(assetTicker(receiveAssetId), color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Default.ArrowDropDown, null, tint = C.textSecondary)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = receiveAmountText,
                        onValueChange = { receiveAmountText = it.filter { c -> c.isDigit() || c == '.' } },
                        placeholder = { Text("0.00", color = C.textMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = C.text, unfocusedTextColor = C.text,
                            cursorColor = C.accent,
                            focusedBorderColor = C.accent, unfocusedBorderColor = C.border,
                        ),
                    )
                }
            }

            // Rate display (tappable to toggle)
            var createRateFlipped by remember { mutableStateOf(false) }
            if (sendAmountGroth > 0 && receiveAmountGroth > 0) {
                Spacer(Modifier.height(12.dp))
                val createRateText = if (!createRateFlipped) {
                    "Rate: 1 ${assetTicker(sendAssetId)} = ${formatRate(receiveAmountGroth / sendAmountGroth)} ${assetTicker(receiveAssetId)}"
                } else {
                    "Rate: 1 ${assetTicker(receiveAssetId)} = ${formatRate(sendAmountGroth / receiveAmountGroth)} ${assetTicker(sendAssetId)}"
                }
                Row(
                    modifier = Modifier.clickable { createRateFlipped = !createRateFlipped },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(createRateText, color = C.accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(6.dp))
                    Text("\u21C4", color = C.accent, fontSize = 14.sp)
                }
            }

            // Expiry selector
            Spacer(Modifier.height(16.dp))
            Text("Expires in", color = C.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30 to "30m", 60 to "1h", 120 to "2h", 360 to "6h", 720 to "12h").forEach { (min, label) ->
                    Surface(
                        modifier = Modifier.clickable { expireMinutes = min },
                        shape = RoundedCornerShape(8.dp),
                        color = if (expireMinutes == min) C.accent.copy(alpha = 0.15f) else C.card,
                    ) {
                        Text(
                            label,
                            color = if (expireMinutes == min) C.accent else C.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            // Same asset warning
            if (sendAssetId == receiveAssetId) {
                Spacer(Modifier.height(8.dp))
                Text("Cannot swap the same asset", color = C.error, fontSize = 12.sp)
            }

            // Create button
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    creating = true
                    pendingOffer = PendingOfferParams(
                        sendAssetId = sendAssetId,
                        sendAmount = sendAmountGroth.toLong(),
                        sendSname = assetTicker(sendAssetId),
                        receiveAssetId = receiveAssetId,
                        receiveAmount = receiveAmountGroth.toLong(),
                        receiveSname = assetTicker(receiveAssetId),
                        expireMinutes = expireMinutes,
                    )
                    // Generate new SBBS address — onGeneratedNewAddress callback will
                    // trigger the LaunchedEffect above which calls SwapManager.createOffer
                    WalletManager.walletInstance?.generateNewAddress()
                    Toast.makeText(context, "Creating offer...", Toast.LENGTH_SHORT).show()
                },
                enabled = canCreate,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = C.accent,
                    disabledContainerColor = C.border,
                ),
            ) {
                if (creating) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = C.bg, strokeWidth = 2.dp)
                } else {
                    Text("Post Offer", color = C.bg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Fee: 0.001 BEAM (paid by the person who accepts your offer)",
                color = C.textMuted,
                fontSize = 11.sp,
            )

            Spacer(Modifier.height(32.dp))
        }

        // Asset picker dialogs
        if (showSendPicker) {
            AssetPickerDialog(
                title = "Sell Asset",
                assets = availableAssets,
                selectedId = sendAssetId,
                assetInfoCache = assetInfoCache,
                assetBalances = assetBalances,
                onSelect = { sendAssetId = it; showSendPicker = false },
                onDismiss = { showSendPicker = false },
            )
        }
        if (showReceivePicker) {
            // Hardcoded supported assets for buying
            val supportedAssets = listOf(0, 7, 9, 36, 37, 38, 39, 47)
            AssetPickerDialog(
                title = "Buy Asset",
                assets = supportedAssets,
                selectedId = receiveAssetId,
                assetInfoCache = assetInfoCache,
                assetBalances = assetBalances,
                onSelect = { receiveAssetId = it; showReceivePicker = false },
                onDismiss = { showReceivePicker = false },
            )
        }
    }
}

@Composable
private fun AssetPickerDialog(
    title: String,
    assets: List<Int>,
    selectedId: Int,
    assetInfoCache: Map<Int, AssetInfoEvent>,
    assetBalances: Map<Int, WalletStatusEvent>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.card,
        title = { Text(title, color = C.text, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                assets.forEach { id ->
                    val ticker = if (id == 0) "BEAM" else {
                        val info = assetInfoCache[id]
                        info?.unitName?.ifEmpty { null } ?: info?.shortName?.ifEmpty { null } ?: "Asset #$id"
                    }
                    val bal = assetBalances[id]
                    val balText = if (bal != null) Helpers.formatBeam(bal.available + bal.shielded) else "0"
                    val isSelected = id == selectedId

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(id) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            com.privimemobile.ui.components.AssetIcon(assetId = id, ticker = ticker, size = 24.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                ticker,
                                color = if (isSelected) C.accent else C.text,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 15.sp,
                        )
                        }
                        Text("$balText $ticker", color = C.textSecondary, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {},
    )
}
