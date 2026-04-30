package com.privimemobile.ui.wallet

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.protocol.Helpers
import com.privimemobile.protocol.WalletApi
import com.privimemobile.ui.theme.C
import com.privimemobile.wallet.CurrencyManager
import com.privimemobile.wallet.WalletEventBus
import com.privimemobile.wallet.assetTicker
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

// Uses TxStatus from WalletScreen.kt (internal visibility)

private val FAILURE_REASONS = mapOf(
    1 to "Invalid peer signature",
    2 to "Cancelled",
    3 to "Kernel not found",
    4 to "Expired",
    5 to "Cannot get proof",
    6 to "No inputs",
    7 to "Invalid or missing asset info",
    8 to "Invalid asset amount",
    9 to "Fee too small",
    10 to "Insufficient funds",
    11 to "Asset locked",
    12 to "Register fail",
    13 to "No such asset",
    14 to "Asset OI limit exceeded",
    15 to "Asset not visible",
    16 to "Asset invalid lifetime",
    17 to "Transaction limits exceeded",
    18 to "Not enough privacy",
    19 to "Limit exceeded",
)

/** Full transaction detail model. */
private data class TxDetail(
    val txId: String,
    val amount: Long,
    val fee: Long,
    val sender: Boolean,
    val status: Int,
    val message: String,
    val createTime: Long,
    val assetId: Int,
    val peerId: String,
    val myId: String,
    val kernelId: String,
    val selfTx: Boolean,
    val failureReason: Int,
    val senderAddress: String,
    val receiverAddress: String,
    val addressType: Int,
    val isShielded: Boolean,
    val isOffline: Boolean,
    val isMaxPrivacy: Boolean,
    val isPublicOffline: Boolean,
    val minConfirmationsProgress: String,
    val isDapps: Boolean = false,
    val appName: String? = null,
    val appID: String? = null,
    val contractCids: String? = null,
    val contractAssets: List<ContractAsset> = emptyList(),
)

private data class PaymentProof(
    val senderId: String,
    val receiverId: String,
    val amount: Long,
    val kernelId: String,
    val isValid: Boolean,
    val rawProof: String,
    val assetId: Int,
)

@Composable
fun TransactionDetailScreen(txId: String, onBack: () -> Unit) {
    val txJson by WalletEventBus.transactions.collectAsState(initial = "[]")
    val exchangeRates by WalletEventBus.exchangeRates.collectAsState()
    val currency = CurrencyManager.getPreferredCurrency()
    val rate = exchangeRates["beam_$currency"] ?: 0.0
    val clipboard = LocalClipboardManager.current

    val tx = remember(txJson, txId) {
        try {
            val arr = JSONArray(txJson)
            (0 until arr.length()).firstNotNullOfOrNull { i ->
                val obj = arr.optJSONObject(i) ?: return@firstNotNullOfOrNull null
                if (obj.optString("txId") != txId) return@firstNotNullOfOrNull null
                TxDetail(
                    txId = obj.optString("txId"),
                    amount = obj.optLong("amount"),
                    fee = obj.optLong("fee"),
                    sender = obj.optBoolean("sender"),
                    status = obj.optInt("status"),
                    message = obj.optString("message", ""),
                    createTime = obj.optLong("createTime"),
                    assetId = obj.optInt("assetId"),
                    peerId = obj.optString("peerId", ""),
                    myId = obj.optString("myId", ""),
                    kernelId = obj.optString("kernelId", ""),
                    selfTx = obj.optBoolean("selfTx"),
                    failureReason = obj.optInt("failureReason", 0),
                    senderAddress = obj.optString("senderAddress", ""),
                    receiverAddress = obj.optString("receiverAddress", ""),
                    addressType = obj.optInt("addressType", 0),
                    isShielded = obj.optBoolean("isShielded"),
                    isOffline = obj.optBoolean("isOffline"),
                    isMaxPrivacy = obj.optBoolean("isMaxPrivacy"),
                    isPublicOffline = obj.optBoolean("isPublicOffline"),
                    minConfirmationsProgress = obj.optString("minConfirmationsProgress", ""),
                    isDapps = obj.optBoolean("isDapps"),
                    appName = obj.optString("appName", "").ifEmpty { null },
                    appID = obj.optString("appID", "").ifEmpty { null },
                    contractCids = obj.optString("contractCids", "").ifEmpty { null },
                    contractAssets = obj.optJSONArray("contractAssets")?.let { ca ->
                        (0 until ca.length()).mapNotNull { j ->
                            val ao = ca.optJSONObject(j) ?: return@mapNotNull null
                            ContractAsset(
                                assetId = ao.optInt("assetId"),
                                sending = ao.optLong("sending"),
                                receiving = ao.optLong("receiving"),
                            )
                        }
                    } ?: emptyList(),
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    var activeTab by remember { mutableIntStateOf(0) } // 0=general, 1=proof
    var proof by remember { mutableStateOf<PaymentProof?>(null) }
    var proofLoading by remember { mutableStateOf(false) }
    var proofRequested by remember { mutableStateOf(false) }
    var snackMessage by remember { mutableStateOf<String?>(null) }

    // Listen for payment proof event from JNI callback (matches RN onPaymentProofExported)
    LaunchedEffect(txId) {
        WalletEventBus.paymentProof.collect { event ->
            if (event.txId == txId) {
                proof = PaymentProof(
                    senderId = event.senderId,
                    receiverId = event.receiverId,
                    amount = event.amount,
                    kernelId = event.kernelId,
                    isValid = event.isValid,
                    rawProof = event.rawProof,
                    assetId = event.assetId,
                )
                proofLoading = false
            }
        }
    }

    if (tx == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(C.bg),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Transaction not found", color = C.error, fontSize = 16.sp)
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onBack) {
                Text("Go Back", color = C.textSecondary)
            }
        }
        return
    }

    // For contract DApp TXs, C++ sender flag is inverted (positive amount = spending but sender=false)
    // But tx_send tips (isDapps but no contractCids) use normal sender flag
    val isOutgoing = if (tx.isDapps && tx.amount > 0 && !tx.contractCids.isNullOrEmpty()) !tx.sender else tx.sender
    val isPending = tx.status == TxStatus.PENDING ||
            tx.status == TxStatus.IN_PROGRESS ||
            tx.status == TxStatus.REGISTERING
    val isDone = tx.status == TxStatus.COMPLETED ||
            tx.status == TxStatus.FAILED ||
            tx.status == TxStatus.CANCELLED
    val isSelf = tx.selfTx
    val isOnline = !tx.isShielded && !tx.isMaxPrivacy && !tx.isPublicOffline
    val isCancelable = isOutgoing && (tx.status == TxStatus.PENDING ||
            (tx.status == TxStatus.IN_PROGRESS && isOnline))

    val statusText = run {
        if (isOutgoing && isOnline && (tx.status == TxStatus.PENDING || tx.status == TxStatus.IN_PROGRESS)) {
            "Waiting for receiver"
        } else if (tx.status == TxStatus.IN_PROGRESS && tx.minConfirmationsProgress.isNotEmpty()) {
            "Confirming (${tx.minConfirmationsProgress})"
        } else when (tx.status) {
            TxStatus.PENDING -> "Pending"
            TxStatus.IN_PROGRESS -> "In Progress"
            TxStatus.CANCELLED -> "Cancelled"
            TxStatus.COMPLETED -> "Completed"
            TxStatus.FAILED -> "Failed"
            TxStatus.REGISTERING -> "In Progress"
            else -> "Unknown"
        }
    }

    val amountColor = when {
        tx.status == TxStatus.FAILED || tx.status == TxStatus.CANCELLED -> C.textSecondary
        isOutgoing -> C.outgoing
        else -> C.incoming
    }

    // Detect swap TX by: message contains "Assets Swap" OR contractAssets has both send+receive
    val isSwapTx = tx.message.contains("Assets Swap", ignoreCase = true) ||
            tx.appName?.contains("Assets Swap", ignoreCase = true) == true ||
            (tx.contractAssets.size >= 2 && tx.contractAssets.any { it.sending > 0 } && tx.contractAssets.any { it.receiving > 0 })

    val addressTypeLabel = when {
        isSwapTx -> "Assets Swap"
        tx.isPublicOffline -> "Public Offline"
        tx.isMaxPrivacy -> "Max Privacy"
        tx.isOffline -> "Offline"
        tx.isShielded -> "Offline"
        else -> "Regular"
    }

    val canHaveProof = isOutgoing && !isSelf &&
            tx.status == TxStatus.COMPLETED && tx.kernelId.isNotEmpty()

    val failureMsg = FAILURE_REASONS[tx.failureReason] ?: ""

    val ticker = assetTicker(tx.assetId)

    fun copyToClipboard(label: String, value: String) {
        clipboard.setText(AnnotatedString(value))
        snackMessage = "$label copied"
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

        // Amount header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (tx.isDapps && tx.contractAssets.isNotEmpty()) {
                // Per-asset breakdown from JNI
                tx.contractAssets.forEach { ca ->
                    val isSpending = ca.sending != 0L
                    val displayAmount = Math.abs(if (isSpending) ca.sending else ca.receiving)
                    val caPrefix = if (isSpending) "-" else "+"
                    val caColor = if (isSpending) C.outgoing else C.incoming
                    val caTicker = if (ca.assetId != 0) assetTicker(ca.assetId) else "BEAM"
                    if (displayAmount > 0) {
                        AutoSizeAmount(
                            text = "$caPrefix${Helpers.formatBeam(displayAmount)} $caTicker",
                            color = caColor,
                            maxFontSize = 28,
                        )
                        if (ca.assetId == 0 && rate > 0) {
                            val caFiat = formatFiatCurrent(displayAmount, rate)
                            if (caFiat != null) {
                                Text(
                                    "≈ $caFiat",
                                    color = C.textSecondary,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }
            } else if (isSelf) {
                Text(
                    text = "Self-transfer",
                    color = C.textSecondary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                AutoSizeAmount(
                    text = "${Helpers.formatBeam(tx.amount)} $ticker",
                    color = C.text,
                    maxFontSize = 28,
                )
                if (tx.assetId == 0 && rate > 0) {
                    val txFiat = formatFiatCurrent(tx.amount, rate)
                    if (txFiat != null) {
                        Text(
                            "≈ $txFiat",
                            color = C.textSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            } else {
                val sign = when {
                    isOutgoing -> "-"
                    else -> "+"
                }
                AutoSizeAmount(
                    text = "$sign${Helpers.formatBeam(tx.amount)} $ticker",
                    color = amountColor,
                )
                // Fiat value in preferred currency
                if (tx.assetId == 0 && rate > 0) {
                    val txFiat = formatFiatCurrent(tx.amount, rate)
                    if (txFiat != null) {
                        Text(
                            "≈ $txFiat",
                            color = C.textSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Status badge
            val (badgeBg, badgeTextColor) = when {
                isPending -> Color(0x1FF0A030) to C.warning
                tx.status == TxStatus.COMPLETED -> Color(0x1F25D4D0) to C.online
                tx.status == TxStatus.FAILED || tx.status == TxStatus.CANCELLED ->
                    Color(0x1FFF6B6B) to C.error
                else -> C.card to C.textSecondary
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = badgeBg,
            ) {
                Text(
                    text = if (isSelf) "Self-transfer" else statusText,
                    color = badgeTextColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                )
            }
        }

        // Tabs (General / Payment Proof)
        if (canHaveProof) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
                    .background(C.card, RoundedCornerShape(10.dp))
                    .padding(4.dp),
            ) {
                TabItem(
                    label = "GENERAL",
                    selected = activeTab == 0,
                    modifier = Modifier.weight(1f),
                    onClick = { activeTab = 0 },
                )
                TabItem(
                    label = "PAYMENT PROOF",
                    selected = activeTab == 1,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        activeTab = 1
                        if (!proofRequested) {
                            proofRequested = true
                            proofLoading = true
                            // Use JNI getPaymentInfo — result comes via onPaymentProofExported callback
                            try {
                                com.privimemobile.wallet.WalletManager.walletInstance?.getPaymentInfo(txId)
                            } catch (_: Exception) {
                                proofLoading = false
                            }
                        }
                    },
                )
            }
        }

        // General tab content
        if (activeTab == 0) {
            // Failure reason
            if (failureMsg.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x14FF6B6B),
                ) {
                    Row {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(C.error),
                        )
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "FAILURE REASON",
                                color = C.error,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(failureMsg, color = C.text, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Details card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = C.card),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DetailRow("Date", fullTimestamp(tx.createTime))
                    DetailRow("Address type", addressTypeLabel, valueColor = C.accent)
                    if (tx.message.isNotEmpty()) {
                        DetailRow(
                            if (isSwapTx) "Comment" else if (tx.isDapps) "Description" else "Comment",
                            tx.message,
                        )
                    }
                    if (isSwapTx) {
                        // Asset Swap TX — show addresses, fee, asset IDs
                        if (tx.senderAddress.isNotEmpty()) {
                            DetailRow(
                                "Sending address",
                                tx.senderAddress,
                                mono = true,
                                onCopy = { copyToClipboard("Sending address", tx.senderAddress) },
                            )
                        }
                        if (tx.receiverAddress.isNotEmpty()) {
                            DetailRow(
                                "Receiving address",
                                tx.receiverAddress,
                                mono = true,
                                onCopy = { copyToClipboard("Receiving address", tx.receiverAddress) },
                            )
                        }
                        DetailRow("Transaction fee", "${Helpers.formatBeam(tx.fee)} BEAM")
                        // Show asset IDs for each swapped asset
                        tx.contractAssets.forEach { ca ->
                            if (ca.assetId != 0) {
                                DetailRow("Confidential asset ID", "${ca.assetId}")
                            }
                        }
                    } else if (tx.isDapps) {
                        // DApp TX — show DApp name and contract CIDs instead of addresses
                        DetailRow("DApp Name", tx.appName ?: "Unknown DApp")
                        if (!tx.contractCids.isNullOrEmpty()) {
                            DetailRow(
                                "Contract ID",
                                tx.contractCids,
                                mono = true,
                                onCopy = { copyToClipboard("Contract ID", tx.contractCids) },
                            )
                        }
                    } else {
                        // Regular TX — show sender/receiver addresses
                        DetailRow(
                            label = if (isOutgoing) "Sending address" else "Receiving address",
                            value = (if (isOutgoing) tx.senderAddress else tx.receiverAddress)
                                .ifEmpty { tx.myId }.ifEmpty { "--" },
                            mono = true,
                            onCopy = {
                                val v = (if (isOutgoing) tx.senderAddress else tx.receiverAddress)
                                    .ifEmpty { tx.myId }
                                if (v.isNotEmpty()) copyToClipboard("Address", v)
                            },
                        )
                        DetailRow(
                            label = if (isOutgoing) "Receiver address" else "Sender address",
                            value = (if (isOutgoing) tx.receiverAddress else tx.senderAddress)
                                .ifEmpty { tx.peerId }.ifEmpty { "--" },
                            mono = true,
                            onCopy = {
                                val v = (if (isOutgoing) tx.receiverAddress else tx.senderAddress)
                                    .ifEmpty { tx.peerId }
                                if (v.isNotEmpty()) copyToClipboard("Address", v)
                            },
                        )
                    }
                    DetailRow("Fee", "${Helpers.formatBeam(tx.fee)} BEAM")
                    DetailRow(
                        label = "Transaction ID",
                        value = tx.txId,
                        mono = true,
                        onCopy = { copyToClipboard("Transaction ID", tx.txId) },
                    )
                    if (tx.kernelId.isNotEmpty()) {
                        DetailRow(
                            label = "Kernel ID",
                            value = tx.kernelId,
                            mono = true,
                            onCopy = { copyToClipboard("Kernel ID", tx.kernelId) },
                            showBorder = false,
                        )
                    }
                }
            }

            // Action buttons
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (isCancelable) {
                    OutlinedButton(
                        onClick = {
                            try {
                                com.privimemobile.wallet.WalletManager.walletInstance?.cancelTx(tx.txId)
                                com.privimemobile.wallet.WalletManager.walletInstance?.getTransactions()
                            } catch (_: Exception) {}
                            onBack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = ButtonDefaults.outlinedButtonBorder(true).copy(
                            brush = androidx.compose.ui.graphics.SolidColor(C.error)
                        ),
                    ) {
                        Text("Cancel Transaction", color = C.error, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (isDone) {
                    OutlinedButton(
                        onClick = {
                            try {
                                com.privimemobile.wallet.WalletManager.walletInstance?.deleteTx(tx.txId)
                                com.privimemobile.wallet.WalletManager.walletInstance?.getTransactions()
                            } catch (_: Exception) {}
                            onBack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = ButtonDefaults.outlinedButtonBorder(true).copy(
                            brush = androidx.compose.ui.graphics.SolidColor(C.border)
                        ),
                    ) {
                        Text(
                            "Delete from History",
                            color = C.textSecondary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        // Payment proof tab content
        if (activeTab == 1) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = C.card),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (proofLoading && proof == null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator(color = C.accent, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Fetching payment proof...",
                                color = C.textSecondary,
                                fontSize = 13.sp,
                            )
                        }
                    } else if (proof != null) {
                        val p = proof!!
                        // Valid/invalid indicator
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = if (p.isValid) Color(0x1A25D4D0) else Color(0x14FF6B6B),
                        ) {
                            Text(
                                text = if (p.isValid) "Payment proof is valid" else "Proof not available or invalid",
                                color = if (p.isValid) C.online else C.error,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(10.dp),
                            )
                        }

                        DetailRow(
                            label = "Sender wallet signature",
                            value = p.senderId.ifEmpty { "--" },
                            mono = true,
                            onCopy = if (p.senderId.isNotEmpty()) {
                                { copyToClipboard("Sender signature", p.senderId) }
                            } else null,
                        )
                        DetailRow(
                            label = "Receiver wallet signature",
                            value = p.receiverId.ifEmpty { "--" },
                            mono = true,
                            onCopy = if (p.receiverId.isNotEmpty()) {
                                { copyToClipboard("Receiver signature", p.receiverId) }
                            } else null,
                        )
                        val proofTicker = assetTicker(p.assetId)
                        DetailRow("Amount", "${Helpers.formatBeam(p.amount)} $proofTicker")
                        DetailRow(
                            label = "Kernel ID",
                            value = p.kernelId.ifEmpty { "--" },
                            mono = true,
                            onCopy = if (p.kernelId.isNotEmpty()) {
                                { copyToClipboard("Kernel ID", p.kernelId) }
                            } else null,
                        )
                        if (p.rawProof.isNotEmpty()) {
                            DetailRow(
                                label = "Proof code",
                                value = p.rawProof,
                                mono = true,
                                onCopy = { copyToClipboard("Proof code", p.rawProof) },
                                showBorder = false,
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "Payment proof not available for this transaction.",
                                color = C.textSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }

        // Snackbar-style message
        snackMessage?.let { msg ->
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(2000)
                snackMessage = null
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(8.dp),
                color = C.card,
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

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun AutoSizeAmount(
    text: String,
    color: Color,
    maxFontSize: Int = 32,
    minFontSize: Int = 18,
    fontWeight: FontWeight = FontWeight.Bold,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val availPx = with(density) { maxWidth.toPx() }
        val pxPerChar = with(density) { (18.sp).toPx() }
        val fitCount = (availPx / pxPerChar).toInt()
        val fontSize = when {
            text.length > (fitCount * 1.0f).toInt() -> minFontSize.sp
            text.length > (fitCount * 0.82f).toInt() -> (minFontSize + 4).sp
            text.length > (fitCount * 0.68f).toInt() -> (maxFontSize - 4).sp
            else -> maxFontSize.sp
        }

        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TabItem(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(7.dp),
        color = if (selected) C.bg else Color.Transparent,
    ) {
        Text(
            text = label,
            color = if (selected) C.accent else C.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 9.dp),
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    mono: Boolean = false,
    valueColor: Color = C.text,
    onCopy: (() -> Unit)? = null,
    showBorder: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onCopy != null) Modifier.clickable { onCopy() } else Modifier
            )
            .padding(vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = label,
                color = C.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            )
            Column(
                modifier = Modifier.weight(2f),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = value,
                    color = valueColor,
                    fontSize = if (mono) 11.sp else 13.sp,
                    fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
                    textAlign = TextAlign.End,
                    maxLines = if (mono) 3 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (onCopy != null) {
                    Text(
                        "tap to copy",
                        color = C.textSecondary.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        if (showBorder) {
            HorizontalDivider(
                modifier = Modifier.padding(top = 12.dp),
                color = C.border,
                thickness = 1.dp,
            )
        }
    }
}

private val fullDateFormat = SimpleDateFormat("MMM d, yyyy 'at' hh:mm a", Locale.getDefault())

private fun fullTimestamp(ts: Long): String {
    return try {
        fullDateFormat.format(Date(ts * 1000))
    } catch (_: Exception) {
        ""
    }
}
