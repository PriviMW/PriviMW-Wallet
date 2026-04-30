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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * CreateOfferScreen — exchange-style limit order form for DEX swap offers.
 *
 * Layout: Buy asset → Unit Price → Pay → You'll Receive (editable) → Rate → Expiry → Post
 * Bidirectional calculation: Pay × Price = Receive, and Receive ÷ Price = Pay.
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

    // Available assets (with balance > 0) — for sell picker
    val availableAssets = assetBalances.filter { (_, v) -> (v.available + v.shielded) > 0 }
        .keys.sorted().toList()

    // Fetch asset info for all supported assets on mount
    LaunchedEffect(Unit) {
        listOf(7, 9, 36, 37, 38, 39, 47).forEach { id ->
            if (!assetInfoCache.containsKey(id)) WalletManager.walletInstance?.getAssetInfo(id)
        }
    }

    // ---- Form state ----
    var buyAssetId by remember { mutableIntStateOf(7) }        // what user wants to buy
    var sellAssetId by remember { mutableIntStateOf(0) }        // what user pays
    var priceText by remember { mutableStateOf("") }             // unit price as string
    var payAmountText by remember { mutableStateOf("") }         // user-editable Pay amount
    var receiveAmountText by remember { mutableStateOf("") }     // user-editable Receive amount
    var rateFlipped by remember { mutableStateOf(false) }        // false = "1 BUY = X SELL"
    var expireMinutes by remember { mutableIntStateOf(60) }
    var showBuyPicker by remember { mutableStateOf(false) }
    var showSellPicker by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }

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

    // Hardcoded buyable assets (what users can search for)
    val supportedBuyAssets = listOf(0, 7, 9, 36, 37, 38, 39, 47)

    // ---- Ticker helpers ----
    fun buyTicker() = if (buyAssetId == 0) "BEAM" else {
        val info = assetInfoCache[buyAssetId]
        info?.unitName?.ifEmpty { null } ?: info?.shortName?.ifEmpty { null } ?: "Asset #${buyAssetId}"
    }

    fun sellTicker() = if (sellAssetId == 0) "BEAM" else {
        val info = assetInfoCache[sellAssetId]
        info?.unitName?.ifEmpty { null } ?: info?.shortName?.ifEmpty { null } ?: "Asset #${sellAssetId}"
    }

    fun assetBalance(id: Int): Long {
        val bal = assetBalances[id] ?: return 0
        return bal.available + bal.shielded
    }

    val sellBalance = assetBalance(sellAssetId)

    // ---- Recalculate Receive from Pay ÷ Unit Price ----
    fun recalcReceive() {
        val price = priceText.replace(',', '.').toDoubleOrNull() ?: return
        val pay = payAmountText.replace(',', '.').toDoubleOrNull() ?: return
        if (price <= 0 || pay <= 0) { receiveAmountText = ""; return }
        receiveAmountText = String.format("%.8f", pay / price).trimEnd('0').trimEnd { it == '.' || it == ',' }
        if (receiveAmountText.isEmpty()) receiveAmountText = "0"
    }

    // ---- Recalculate Price from Pay ÷ Receive ----
    fun recalcPrice() {
        val pay = payAmountText.replace(',', '.').toDoubleOrNull() ?: return
        val receive = receiveAmountText.replace(',', '.').toDoubleOrNull() ?: return
        if (pay <= 0 || receive <= 0) { priceText = ""; return }
        priceText = String.format("%.8f", pay / receive).trimEnd('0').trimEnd { it == '.' || it == ',' }
        if (priceText.isEmpty()) priceText = "0"
    }

    // Parse groth amounts
    val payGroth = (payAmountText.replace(',', '.').toDoubleOrNull() ?: 0.0) * 100_000_000
    val receiveGroth = (receiveAmountText.replace(',', '.').toDoubleOrNull() ?: 0.0) * 100_000_000

    val canCreate = payGroth > 0 && receiveGroth > 0 &&
            sellAssetId != buyAssetId && payGroth.toLong() <= sellBalance && !creating

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

            // ---- BUY ASSET ----
            Text("Buy", color = C.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = C.card),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showBuyPicker = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.privimemobile.ui.components.AssetIcon(
                            assetId = buyAssetId,
                            ticker = buyTicker(),
                            size = 24.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(buyTicker(), color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Icon(Icons.Default.ArrowDropDown, null, tint = C.textSecondary)
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---- UNIT PRICE CARD ----
            Text("Unit Price", color = C.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = C.card),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "${buyTicker()}/${sellTicker()}",
                        color = C.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { raw ->
                            val filtered = raw.filter { c -> c.isDigit() || c == '.' || c == ',' }
                            val sepCount = filtered.count { it == '.' || it == ',' }
                            if (sepCount > 1) return@OutlinedTextField
                            priceText = filtered
                            if (priceText.isNotEmpty() && payAmountText.isNotEmpty()) {
                                recalcReceive()
                            }
                        },
                        placeholder = { Text("0.00", color = C.textMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = C.text,
                            unfocusedTextColor = C.text,
                            cursorColor = C.accent,
                            focusedBorderColor = C.accent,
                            unfocusedBorderColor = C.border,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---- PAY ----
            Text("Pay", color = C.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = C.card),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Sell asset picker
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSellPicker = true }
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            com.privimemobile.ui.components.AssetIcon(
                                assetId = sellAssetId,
                                ticker = sellTicker(),
                                size = 24.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(sellTicker(), color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Default.ArrowDropDown, null, tint = C.textSecondary)
                    }
                    // Amount input
                    OutlinedTextField(
                        value = payAmountText,
                        onValueChange = { raw ->
                            val filtered = raw.filter { c -> c.isDigit() || c == '.' || c == ',' }
                            val sepCount = filtered.count { it == '.' || it == ',' }
                            if (sepCount > 1) return@OutlinedTextField
                            payAmountText = filtered
                            if (payAmountText.isNotEmpty() && priceText.isNotEmpty()) {
                                recalcReceive()
                            }
                        },
                        placeholder = { Text("0.00", color = C.textMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = C.text,
                            unfocusedTextColor = C.text,
                            cursorColor = C.accent,
                            focusedBorderColor = C.accent,
                            unfocusedBorderColor = C.border,
                        ),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Available: ${Helpers.formatBeam(sellBalance)} ${sellTicker()}",
                        color = if (payGroth.toLong() > sellBalance) C.error else C.textMuted,
                        fontSize = 12.sp,
                    )
                }
            }

            // ---- YOU'LL RECEIVE ----
            Text("You'll Receive", color = C.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = C.card),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            com.privimemobile.ui.components.AssetIcon(
                                assetId = buyAssetId,
                                ticker = buyTicker(),
                                size = 24.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(buyTicker(), color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = receiveAmountText,
                        onValueChange = { raw ->
                            val filtered = raw.filter { c -> c.isDigit() || c == '.' || c == ',' }
                            val sepCount = filtered.count { it == '.' || it == ',' }
                            if (sepCount > 1) return@OutlinedTextField
                            receiveAmountText = filtered
                            if (receiveAmountText.isNotEmpty() && priceText.isNotEmpty()) {
                                recalcPrice()
                            }
                        },
                        placeholder = { Text("0.00", color = C.textMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = C.text,
                            unfocusedTextColor = C.text,
                            cursorColor = C.accent,
                            focusedBorderColor = C.accent,
                            unfocusedBorderColor = C.border,
                        ),
                    )
                }
            }

            // Same asset warning
            if (sellAssetId == buyAssetId) {
                Spacer(Modifier.height(8.dp))
                Text("Cannot swap the same asset", color = C.error, fontSize = 12.sp)
            }

            // Rate bar (tappable to flip, default "1 BUY = X SELL")
            val priceDouble = priceText.replace(',', '.').toDoubleOrNull()
            if (priceDouble != null && priceDouble > 0) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.clickable { rateFlipped = !rateFlipped },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (!rateFlipped) {
                            "1 ${buyTicker()} = ${formatRate(priceDouble)} ${sellTicker()}"
                        } else {
                            "1 ${sellTicker()} = ${formatRate(1.0 / priceDouble)} ${buyTicker()}"
                        },
                        color = C.accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
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

            // Post Offer button
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    creating = true
                    // send = what user pays (sell), receive = what user gets (buy)
                    pendingOffer = PendingOfferParams(
                        sendAssetId = sellAssetId,
                        sendAmount = payGroth.toLong(),
                        sendSname = sellTicker(),
                        receiveAssetId = buyAssetId,
                        receiveAmount = receiveGroth.toLong(),
                        receiveSname = buyTicker(),
                        expireMinutes = expireMinutes,
                    )
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

        // ---- Asset picker dialogs ----
        if (showBuyPicker) {
            AssetPickerDialog(
                title = "Buy Asset",
                assets = supportedBuyAssets,
                selectedId = buyAssetId,
                assetInfoCache = assetInfoCache,
                assetBalances = assetBalances,
                onSelect = {
                    buyAssetId = it
                    showBuyPicker = false
                    // Reset price when changing assets
                    priceText = ""
                    payAmountText = ""
                    receiveAmountText = ""
                },
                onDismiss = { showBuyPicker = false },
            )
        }

        if (showSellPicker) {
            AssetPickerDialog(
                title = "Pay Asset",
                assets = availableAssets,
                selectedId = sellAssetId,
                assetInfoCache = assetInfoCache,
                assetBalances = assetBalances,
                onSelect = {
                    sellAssetId = it
                    showSellPicker = false
                    priceText = ""
                    payAmountText = ""
                    receiveAmountText = ""
                },
                onDismiss = { showSellPicker = false },
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
