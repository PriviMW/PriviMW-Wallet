package com.privimemobile.ui.wallet

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.unit.sp
import com.privimemobile.protocol.Helpers
import com.privimemobile.ui.theme.C
import com.privimemobile.wallet.*

/**
 * SwapScreen — DEX order book with All Offers / My Offers tabs.
 * Simple OTC marketplace for trading Beam Confidential Assets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapScreen(
    onBack: () -> Unit = {},
    onCreateOffer: () -> Unit = {},
    onTxDetail: (String) -> Unit = {},
) {
    val orders by WalletEventBus.dexOrders.collectAsState()
    // Force recomposition when asset info arrives
    val assetInfoVersion by WalletEventBus.assetInfo.collectAsState(initial = null)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0 = All, 1 = My Offers, 2 = History

    // Refresh on mount + fetch asset info for all order assets
    LaunchedEffect(Unit) {
        SwapManager.loadParams()
        SwapManager.refreshOrders()
    }

    // Auto-fetch asset info for any unknown assets in orders
    LaunchedEffect(orders) {
        val knownIds = WalletEventBus.assetInfoCache.keys
        val orderAssetIds = orders.flatMap { listOf(it.sendAssetId, it.receiveAssetId) }.filter { it != 0 }.toSet()
        orderAssetIds.forEach { id ->
            if (id !in knownIds) {
                WalletManager.walletInstance?.getAssetInfo(id)
            }
        }
    }

    // Auto-refresh every 30s
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            SwapManager.refreshOrders()
        }
    }

    var dismissedIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var filterAssetId by rememberSaveable { mutableIntStateOf(-1) } // -1 = All assets
    var showFilterPicker by rememberSaveable { mutableStateOf(false) }

    // Swap TX history from wallet transactions
    val txJson by WalletEventBus.transactions.collectAsState(initial = "[]")
    val swapHistory = remember(txJson) {
        try {
            val arr = org.json.JSONArray(txJson)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                val appName = obj.optString("appName", "")
                if (!appName.contains("Assets Swap", ignoreCase = true)) return@mapNotNull null
                TxItem(
                    txId = obj.optString("txId", ""),
                    amount = obj.optLong("amount", 0),
                    fee = obj.optLong("fee", 0),
                    sender = obj.optBoolean("sender"),
                    status = obj.optInt("status"),
                    message = obj.optString("comment", ""),
                    createTime = obj.optLong("createTime", 0),
                    assetId = obj.optInt("assetId", 0),
                    peerId = obj.optString("peerId", ""),
                    isDapps = true,
                    appName = appName,
                    contractAssets = obj.optJSONArray("contractAssets")?.let { ca ->
                        (0 until ca.length()).map { j ->
                            val a = ca.getJSONObject(j)
                            ContractAsset(a.optInt("assetId", 0), a.optLong("sending", 0), a.optLong("receiving", 0))
                        }
                    } ?: emptyList(),
                )
            }.sortedByDescending { it.createTime }
        } catch (_: Exception) { emptyList() }
    }
    var dismissedTxIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    // Load dismissed history IDs from SecureStorage on mount
    LaunchedEffect(Unit) {
        dismissedTxIds = com.privimemobile.protocol.SecureStorage.getStringSet(
            com.privimemobile.protocol.SecureStorage.KEY_DISMISSED_SWAP_HISTORY
        )
    }
    val filteredHistory = swapHistory.filter { it.txId.isNotEmpty() && it.txId !in dismissedTxIds }
    var confirmOrder by rememberSaveable { mutableStateOf<DexOrder?>(null) }
    val allOffers = orders.filter { it.isActive && !it.isMine && it.orderId !in dismissedIds &&
            (filterAssetId == -1 || it.sendAssetId == filterAssetId || it.receiveAssetId == filterAssetId) &&
            SwapManager.isLegitimateReceiveAsset(it.receiveAssetId, it.receiveSname) }
    val myOffers = orders.filter { it.isMine && it.orderId !in dismissedIds }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Assets Swap", color = C.text) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = C.bg),
                windowInsets = WindowInsets(0),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateOffer,
                containerColor = C.accent,
            ) {
                Icon(Icons.Default.Add, "Create Offer", tint = C.bg)
            }
        },
        containerColor = C.bg,
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Tab selector
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("All Offers" to allOffers.size, "My Offers" to myOffers.size, "History" to filteredHistory.size).forEachIndexed { idx, (label, count) ->
                    Surface(
                        modifier = Modifier.clickable { selectedTab = idx },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectedTab == idx) C.accent.copy(alpha = 0.15f) else C.card,
                    ) {
                        Text(
                            "$label ($count)",
                            color = if (selectedTab == idx) C.accent else C.textSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }

            // Asset filter (only for All Offers tab)
            if (selectedTab == 0) {
                val assetIds = allOffers
                    .flatMap { listOf(it.sendAssetId, it.receiveAssetId) }.toSet().sorted()
                if (assetIds.size > 1) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // All chip
                        Surface(
                            modifier = Modifier.clickable { filterAssetId = -1 },
                            shape = RoundedCornerShape(16.dp),
                            color = if (filterAssetId == -1) C.accent.copy(alpha = 0.15f) else C.card,
                        ) {
                            Text("All", color = if (filterAssetId == -1) C.accent else C.textSecondary,
                                fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                        // Per-asset chips
                        assetIds.forEach { id ->
                            val ticker = com.privimemobile.wallet.assetTicker(id)
                            Surface(
                                modifier = Modifier.clickable { filterAssetId = if (filterAssetId == id) -1 else id },
                                shape = RoundedCornerShape(16.dp),
                                color = if (filterAssetId == id) C.accent.copy(alpha = 0.15f) else C.card,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    com.privimemobile.ui.components.AssetIcon(assetId = id, ticker = ticker, size = 16.dp)
                                    Spacer(Modifier.width(4.dp))
                                    Text(ticker, color = if (filterAssetId == id) C.accent else C.textSecondary,
                                        fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }

            // History tab
            if (selectedTab == 2) {
                if (filteredHistory.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.SwapHoriz, null, tint = C.textMuted, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No swap history", color = C.textSecondary, fontSize = 13.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(filteredHistory, key = { it.txId.ifEmpty { "swap_${it.createTime}_${it.hashCode()}" } }) { tx ->
                            SwapHistoryCard(
                                tx = tx,
                                onClick = { onTxDetail(tx.txId) },
                                onDismiss = {
                                    dismissedTxIds = dismissedTxIds + tx.txId
                                    com.privimemobile.protocol.SecureStorage.putStringSet(
                                        com.privimemobile.protocol.SecureStorage.KEY_DISMISSED_SWAP_HISTORY,
                                        dismissedTxIds
                                    )
                                },
                            )
                        }
                    }
                }
                return@Column
            }

            val displayOrders = if (selectedTab == 0) allOffers else myOffers

            if (displayOrders.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SwapHoriz, null, tint = C.textMuted, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (selectedTab == 0) "No offers available" else "You have no active offers",
                            color = C.textSecondary,
                            fontSize = 15.sp,
                        )
                        if (selectedTab == 0) {
                            Spacer(Modifier.height(4.dp))
                            Text("Create one with the + button", color = C.textMuted, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(displayOrders, key = { it.orderId }) { order ->
                        SwapOfferCard(
                            order = order,
                            assetBalances = WalletEventBus.assetBalances,
                            onAccept = {
                                confirmOrder = order
                            },
                            onCancel = {
                                SwapManager.cancelOffer(order.orderId)
                            },
                            onDismiss = {
                                dismissedIds = dismissedIds + order.orderId
                            },
                        )
                    }
                }
            }
        }
    }

    // Auth + Confirmation dialog
    val context = LocalContext.current
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf("") }

    val askPasswordOnSend = remember {
        com.privimemobile.protocol.SecureStorage.getBoolean(
            com.privimemobile.protocol.SecureStorage.KEY_ASK_PASSWORD_ON_SEND, true
        )
    }
    val biometricsEnabled = remember {
        com.privimemobile.protocol.SecureStorage.getBoolean(
            com.privimemobile.protocol.SecureStorage.KEY_FINGERPRINT_ENABLED
        )
    }
    val biometricAvailable = remember {
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }

    fun executeSwap() {
        val order = confirmOrder ?: return
        SwapManager.acceptOffer(order)
        confirmOrder = null
    }

    fun authenticateAndSwap() {
        if (!askPasswordOnSend) { executeSwap(); return }

        if (biometricsEnabled && biometricAvailable) {
            val activity = context as? FragmentActivity
            if (activity != null) {
                val executor = ContextCompat.getMainExecutor(context)
                val prompt = BiometricPrompt(activity, executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            executeSwap()
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                                errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                                passwordInput = ""; passwordError = ""; showPasswordDialog = true
                            }
                        }
                        override fun onAuthenticationFailed() {}
                    }
                )
                val order = confirmOrder!!
                val sendTicker = order.sendSname.ifEmpty { com.privimemobile.wallet.assetTicker(order.sendAssetId) }
                prompt.authenticate(
                    BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Confirm Swap")
                        .setSubtitle("Authenticate to swap ${Helpers.formatBeam(order.sendAmount)} $sendTicker")
                        .setNegativeButtonText("Use Password")
                        .build()
                )
                return
            }
        }

        passwordInput = ""; passwordError = ""; showPasswordDialog = true
    }

    if (confirmOrder != null) {
        val order = confirmOrder!!
        val sendTicker = order.sendSname.ifEmpty { com.privimemobile.wallet.assetTicker(order.sendAssetId) }
        val receiveTicker = order.receiveSname.ifEmpty { com.privimemobile.wallet.assetTicker(order.receiveAssetId) }

        AlertDialog(
            onDismissRequest = { confirmOrder = null },
            containerColor = C.card,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SwapHoriz, null, tint = C.accent, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Confirm Swap", color = C.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = C.bg),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("You send", color = C.textSecondary, fontSize = 13.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                com.privimemobile.ui.components.AssetIcon(assetId = order.sendAssetId, ticker = sendTicker, size = 20.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("${Helpers.formatBeam(order.sendAmount)} $sendTicker", color = C.outgoing, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("\u2193", color = C.accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = C.bg),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("You receive", color = C.textSecondary, fontSize = 13.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                com.privimemobile.ui.components.AssetIcon(assetId = order.receiveAssetId, ticker = receiveTicker, size = 20.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("${Helpers.formatBeam(order.receiveAmount)} $receiveTicker", color = C.incoming, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    var dialogRateFlipped by remember { mutableStateOf(false) }
                    val dialogRateText = if (!dialogRateFlipped) {
                        "Rate: 1 $sendTicker = ${formatRate(order.rate)} $receiveTicker"
                    } else {
                        val inv = if (order.receiveAmount > 0) order.sendAmount.toDouble() / order.receiveAmount.toDouble() else 0.0
                        "Rate: 1 $receiveTicker = ${formatRate(inv)} $sendTicker"
                    }
                    Row(
                        modifier = Modifier.clickable { dialogRateFlipped = !dialogRateFlipped },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(dialogRateText, color = C.textSecondary, fontSize = 12.sp)
                        Spacer(Modifier.width(4.dp))
                        Text("\u21C4", color = C.accent, fontSize = 12.sp)
                    }
                    Text("Fee: 0.001 BEAM (paid by you)", color = C.textMuted, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = { authenticateAndSwap() },
                    colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Confirm Swap", color = C.bg, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmOrder = null }) {
                    Text("Cancel", color = C.textSecondary)
                }
            },
        )
    }

    // Password fallback dialog
    if (showPasswordDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showPasswordDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = C.card),
                modifier = Modifier.widthIn(max = 340.dp),
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Enter Password", color = C.text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(4.dp))
                    Text("Confirm your wallet password to swap", color = C.textSecondary, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(20.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it; passwordError = "" },
                        placeholder = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = C.border, unfocusedBorderColor = C.border,
                            cursorColor = C.accent, focusedContainerColor = C.bg, unfocusedContainerColor = C.bg,
                            focusedTextColor = C.text, unfocusedTextColor = C.text,
                        ),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                    )
                    if (passwordError.isNotEmpty()) {
                        Text(passwordError, color = C.error, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { showPasswordDialog = false },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                        ) { Text("Cancel", color = C.textSecondary) }
                        Button(
                            onClick = {
                                if (passwordInput.isNotBlank()) {
                                    val wallet = com.privimemobile.wallet.WalletManager.walletInstance
                                    if (wallet != null && !wallet.checkWalletPassword(passwordInput.trim())) {
                                        passwordError = "Incorrect password"
                                        return@Button
                                    }
                                    showPasswordDialog = false
                                    executeSwap()
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                        ) { Text("Confirm", color = C.bg, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SwapOfferCard(
    order: DexOrder,
    assetBalances: Map<Int, WalletStatusEvent>,
    onAccept: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit = {},
) {
    // Swipe to dismiss for non-active offers
    if (!order.isActive) {
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value != SwipeToDismissBoxValue.Settled) { onDismiss(); true } else false
            }
        )
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                Box(
                    modifier = Modifier.fillMaxSize().background(C.error.copy(alpha = 0.2f), RoundedCornerShape(12.dp)).padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Text("Remove", color = C.error, fontWeight = FontWeight.Bold)
                }
            },
        ) {
            SwapOfferCardContent(order, assetBalances, onAccept, onCancel)
        }
        return
    }

    SwapOfferCardContent(order, assetBalances, onAccept, onCancel)
}

@Composable
private fun SwapOfferCardContent(
    order: DexOrder,
    assetBalances: Map<Int, WalletStatusEvent>,
    onAccept: () -> Unit,
    onCancel: () -> Unit,
) {
    val sendTicker = order.sendSname.ifEmpty { com.privimemobile.wallet.assetTicker(order.sendAssetId) }
    val receiveTicker = order.receiveSname.ifEmpty { com.privimemobile.wallet.assetTicker(order.receiveAssetId) }

    // Check if user can afford (sendAssetId = what viewer sends, perspective-aware)
    val neededAsset = order.sendAssetId
    val neededAmount = order.sendAmount
    val userBalance = (assetBalances[neededAsset]?.available ?: 0) + (assetBalances[neededAsset]?.shielded ?: 0)
    val canAfford = userBalance >= neededAmount

    val remainingSec = order.remainingSeconds()
    val remainingText = when {
        remainingSec < 0 -> "" // no expiry data
        remainingSec == 0L -> "Expired"
        remainingSec < 60 -> "${remainingSec}s"
        remainingSec < 3600 -> "${remainingSec / 60}m"
        else -> "${remainingSec / 3600}h ${(remainingSec % 3600) / 60}m"
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = C.card),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Swap direction
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                com.privimemobile.ui.components.AssetIcon(assetId = order.sendAssetId, ticker = sendTicker, size = 24.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    "${Helpers.formatBeam(order.sendAmount)} $sendTicker",
                    color = C.outgoing,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text("  \u2192  ", color = C.textSecondary, fontSize = 15.sp)
                com.privimemobile.ui.components.AssetIcon(assetId = order.receiveAssetId, ticker = receiveTicker, size = 24.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    "${Helpers.formatBeam(order.receiveAmount)} $receiveTicker",
                    color = C.incoming,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Rate (tappable to toggle) + expiry
            var rateFlipped by remember { mutableStateOf(false) }
            val rateText = if (!rateFlipped) {
                "Rate: 1 $sendTicker = ${formatRate(order.rate)} $receiveTicker"
            } else {
                val inverseRate = if (order.receiveAmount > 0) order.sendAmount.toDouble() / order.receiveAmount.toDouble() else 0.0
                "Rate: 1 $receiveTicker = ${formatRate(inverseRate)} $sendTicker"
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.clickable { rateFlipped = !rateFlipped },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(rateText, color = C.textSecondary, fontSize = 13.sp)
                    Spacer(Modifier.width(4.dp))
                    Text("\u21C4", color = C.accent, fontSize = 13.sp)
                }
                Text(
                    remainingText,
                    color = if (remainingSec < 300) C.warning else C.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (remainingSec < 300) FontWeight.Bold else FontWeight.Normal,
                )
            }

            Spacer(Modifier.height(12.dp))

            // Action button
            if (order.isMine) {
                // My offer — show status + cancel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        when {
                            order.isAccepted -> "Accepted"
                            order.isCanceled -> "Cancelled"
                            order.isExpired -> "Expired"
                            else -> "Active"
                        },
                        color = when {
                            order.isAccepted -> C.incoming
                            order.isCanceled || order.isExpired -> C.textMuted
                            else -> C.accent
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (order.isActive) {
                        OutlinedButton(
                            onClick = onCancel,
                            shape = RoundedCornerShape(8.dp),
                            border = ButtonDefaults.outlinedButtonBorder(true).copy(
                                brush = androidx.compose.ui.graphics.SolidColor(C.error)
                            ),
                        ) {
                            Text("Cancel", color = C.error, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                // Other's offer — accept button
                Button(
                    onClick = onAccept,
                    enabled = canAfford && order.isActive,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = C.accent,
                        disabledContainerColor = C.border,
                    ),
                ) {
                    if (!canAfford && order.isActive) {
                        Text("Insufficient $sendTicker", color = C.textMuted, fontSize = 12.sp)
                    } else {
                        Text("Accept Swap", color = C.bg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** Format rate like exchanges — show significant digits with appropriate precision */
internal fun formatRate(rate: Double): String {
    if (rate <= 0) return "0"
    return when {
        rate >= 1_000 -> String.format("%,.0f", rate)          // 1,234
        rate >= 100 -> String.format("%.2f", rate)              // 123.45
        rate >= 1 -> String.format("%.4f", rate)                // 1.2345
        rate >= 0.01 -> String.format("%.6f", rate)             // 0.012345
        rate >= 0.0001 -> String.format("%.8f", rate)           // 0.00012345
        else -> {
            // Very small: show in scientific-ish format
            // Count leading zeros after decimal
            val full = String.format("%.12f", rate)
            val afterDot = full.substringAfter(".").ifEmpty { full.substringAfter(",") }
            val leadingZeros = afterDot.takeWhile { it == '0' }.length
            String.format("%.${leadingZeros + 4}f", rate)       // 0.0000001234
        }
    }.trimEnd('0').trimEnd { it == '.' || it == ',' }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwapHistoryCard(tx: TxItem, onClick: () -> Unit = {}, onDismiss: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) { onDismiss(); true } else false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize().background(C.error.copy(alpha = 0.2f), RoundedCornerShape(12.dp)).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text("Remove", color = C.error, fontWeight = FontWeight.Bold)
            }
        },
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = C.card),
            modifier = Modifier.fillMaxWidth().clickable { onClick() },
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Swap amounts
                if (tx.contractAssets.isNotEmpty()) {
                    tx.contractAssets.forEach { ca ->
                        val isSpending = ca.sending > 0
                        val displayAmount = if (isSpending) ca.sending else ca.receiving
                        val prefix = if (isSpending) "-" else "+"
                        val color = if (isSpending) C.outgoing else C.incoming
                        val ticker = com.privimemobile.wallet.assetTicker(ca.assetId)
                        if (displayAmount > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                com.privimemobile.ui.components.AssetIcon(assetId = ca.assetId, ticker = ticker, size = 20.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("$prefix${Helpers.formatBeam(displayAmount)} $ticker", color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // Fallback: single amount
                    val ticker = com.privimemobile.wallet.assetTicker(tx.assetId)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.privimemobile.ui.components.AssetIcon(assetId = tx.assetId, ticker = ticker, size = 20.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("${Helpers.formatBeam(tx.amount)} $ticker", color = C.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Status + date
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val statusText = when (tx.status) {
                        TxStatus.COMPLETED -> "Completed"
                        TxStatus.CANCELLED -> "Cancelled"
                        TxStatus.FAILED -> "Failed"
                        TxStatus.PENDING -> "Pending"
                        else -> "In Progress"
                    }
                    val statusColor = when (tx.status) {
                        TxStatus.COMPLETED -> C.incoming
                        TxStatus.CANCELLED, TxStatus.FAILED -> C.error
                        else -> C.warning
                    }
                    Text(statusText, color = statusColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

                    val date = java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(tx.createTime * 1000))
                    Text(date, color = C.textSecondary, fontSize = 12.sp)
                }

                // Fee
                if (tx.fee > 0) {
                    Text("Fee: ${Helpers.formatBeam(tx.fee)} BEAM", color = C.textMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
