package com.privimemobile.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.R
import com.privimemobile.protocol.Helpers
import com.privimemobile.protocol.SecureStorage
import com.privimemobile.ui.theme.C
import com.privimemobile.wallet.AssetInfoEvent
import com.privimemobile.wallet.CurrencyManager
import com.privimemobile.wallet.WalletEventBus
import com.privimemobile.wallet.WalletStatusEvent
import com.privimemobile.wallet.assetTicker
import com.privimemobile.wallet.assetFullName
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

/**
 * Asset detail screen — shows balance breakdown, Send/Receive buttons,
 * and filtered transaction list for a specific asset.
 *
 * Fully ports AssetDetailScreen.tsx.
 */
@Composable
fun AssetDetailScreen(
    assetId: Int = 0,
    onBack: () -> Unit,
    onSend: () -> Unit = {},
    onReceive: () -> Unit = {},
    onTxDetail: (String) -> Unit = {},
    onViewAll: () -> Unit = {},
) {
    // Per-asset balance — from persistent map + live updates
    var assetStatus by remember {
        mutableStateOf(WalletEventBus.assetBalances[assetId] ?: WalletStatusEvent(assetId, 0, 0, 0, 0, 0))
    }
    LaunchedEffect(assetId) {
        WalletEventBus.walletStatus.collect { event ->
            if (event.assetId == assetId) assetStatus = event
        }
    }

    // Exchange rates for fiat display
    val exchangeRates by WalletEventBus.exchangeRates.collectAsState()
    val currency = CurrencyManager.getPreferredCurrency()
    val rate = exchangeRates["beam_$currency"] ?: 0.0

    // Asset info (name, unitName, etc.) — pre-populate from persistent cache
    val assetInfoMap = remember { mutableStateMapOf<Int, AssetInfoEvent>().apply {
        putAll(WalletEventBus.assetInfoCache)
    }}
    LaunchedEffect(Unit) {
        WalletEventBus.assetInfo.collect { event ->
            assetInfoMap[event.id] = event
        }
    }

    // Transaction list filtered for this asset
    val txJson by WalletEventBus.transactions.collectAsState()
    val assetTxs = remember(txJson, assetId) {
        try {
            val arr = JSONArray(txJson)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null

                // Parse contractAssets early to check asset involvement for DApp TXs
                val contractAssets = obj.optJSONArray("contractAssets")?.let { ca ->
                    (0 until ca.length()).mapNotNull { j ->
                        val ao = ca.optJSONObject(j) ?: return@mapNotNull null
                        Pair(ao.optInt("assetId"), Pair(ao.optLong("sending"), ao.optLong("receiving")))
                    }
                } ?: emptyList()

                // Include TX if either top-level assetId matches OR (for DApp TXs) a contract asset matches
                val topAssetId = obj.optInt("assetId")
                val hasContractAsset = contractAssets.any { it.first == assetId }
                val isDapps = obj.optBoolean("isDapps")
                if (topAssetId != assetId && !(isDapps && hasContractAsset)) return@mapNotNull null

                TxItem(
                    txId = obj.optString("txId"),
                    amount = obj.optLong("amount"),
                    fee = obj.optLong("fee"),
                    sender = obj.optBoolean("sender"),
                    status = obj.optInt("status"),
                    message = obj.optString("message", ""),
                    createTime = obj.optLong("createTime"),
                    assetId = obj.optInt("assetId"),
                    peerId = obj.optString("peerId", ""),
                    isShielded = obj.optBoolean("isShielded"),
                    isMaxPrivacy = obj.optBoolean("isMaxPrivacy"),
                    isOffline = obj.optBoolean("isOffline"),
                    isPublicOffline = obj.optBoolean("isPublicOffline"),
                    isDapps = obj.optBoolean("isDapps"),
                    appName = obj.optString("appName", "").ifEmpty { null },
                    contractCids = obj.optString("contractCids", "").ifEmpty { null },
                    contractAssets = contractAssets.map { (cAssetId, amounts) ->
                        ContractAsset(assetId = cAssetId, sending = amounts.first, receiving = amounts.second)
                    },
                    selfTx = obj.optBoolean("selfTx"),
                )
            }.sortedByDescending { it.createTime }
        } catch (_: Exception) { emptyList() }
    }

    // Asset names — resolved from global cache
    val assetName = assetTicker(assetId)
    val fullName = assetFullName(assetId)

    // Lock hours from settings
    var lockHours by remember { mutableIntStateOf(72) }
    LaunchedEffect(Unit) {
        lockHours = SecureStorage.getInt("max_privacy_hours", 72)
    }

    // Split coins sheet
    var showSplitSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.dapps_back_button), color = C.textSecondary)
        }

        // === Asset Card ===
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = C.card),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header: icon + name + split button
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.privimemobile.ui.components.AssetIcon(assetId = assetId, ticker = assetName, size = 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(assetName, color = C.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            if (assetId != 0) {
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = C.border,
                                ) {
                                    Text(
                                        stringResource(R.string.asset_id_value, assetId),
                                        color = C.textSecondary,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        if (fullName != assetName) {
                            Text(fullName, color = C.textSecondary, fontSize = 13.sp)
                        }
                    }

                    // Split coins button — BEAM only
                    if (assetId == 0) {
                        Surface(
                            onClick = { showSplitSheet = true },
                            shape = RoundedCornerShape(8.dp),
                            color = C.accent.copy(alpha = 0.15f),
                            border = ButtonDefaults.outlinedButtonBorder(true).copy(
                                brush = androidx.compose.ui.graphics.SolidColor(C.accent.copy(alpha = 0.5f))
                            ),
                        ) {
                            Text(
                                stringResource(R.string.split_coins_button),
                                color = C.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }

                val totalBalance = assetStatus.available + assetStatus.shielded

                // Divider
                HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 12.dp))

                // Balance breakdown — available + shielded are separate fields from C++ core
                val totalAvailable = totalBalance
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.balance_available), color = C.textSecondary, fontSize = 13.sp)
                    Column(horizontalAlignment = Alignment.End) {
                        val balText = "${Helpers.formatBeam(totalAvailable)} $assetName"
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            val density = LocalDensity.current
                            val availPx = with(density) { maxWidth.toPx() }
                            val pxPerChar = with(density) { (18.sp).toPx() }
                            val fitCount = (availPx / pxPerChar).toInt()
                            val balFontSize = when {
                                balText.length > (fitCount * 1.0f).toInt() -> 14.sp
                                balText.length > (fitCount * 0.82f).toInt() -> 15.sp
                                balText.length > (fitCount * 0.68f).toInt() -> 16.sp
                                else -> 18.sp
                            }
                            Text(
                                balText,
                                color = C.text,
                                fontSize = balFontSize,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (rate > 0) {
                            val fiat = if (assetId == 0) formatFiatCurrent(totalAvailable, rate)
                                       else CurrencyManager.assetToFiat(assetId, totalAvailable, currency, rate)?.let { CurrencyManager.formatFiat(it, currency) }
                            if (fiat != null) {
                                Text("≈ $fiat", color = C.textSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }

                if (assetStatus.available > 0 && assetStatus.shielded > 0) {
                    BalanceRow("  ${stringResource(R.string.wallet_addr_regular)}", "${Helpers.formatBeam(assetStatus.available)} $assetName")
                    BalanceRow("  ${stringResource(R.string.balance_shielded)}", "${Helpers.formatBeam(assetStatus.shielded)} $assetName")
                }
                if (assetStatus.maturing > 0) {
                    val lockLabel = if (lockHours > 0) "${stringResource(R.string.balance_locked)} (min ${lockHours}h)" else stringResource(R.string.balance_locked)
                    BalanceRow(lockLabel, "${Helpers.formatBeam(assetStatus.maturing)} $assetName", locked = true)
                }
                if (assetStatus.maxPrivacy > 0) {
                    BalanceRow(stringResource(R.string.balance_max_privacy_locked), "${Helpers.formatBeam(assetStatus.maxPrivacy)} $assetName", locked = true)
                }
                if (assetStatus.sending > 0) {
                    BalanceRow(stringResource(R.string.balance_locked), "${Helpers.formatBeam(assetStatus.sending)} $assetName", locked = true)
                }
                if (assetStatus.receiving > 0 && assetStatus.sending == 0L) {
                    BalanceRow(stringResource(R.string.balance_receiving), "${Helpers.formatBeam(assetStatus.receiving)} $assetName", incoming = true)
                }
            }
        }

        // === Send / Receive buttons ===
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onSend,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.outgoing),
            ) {
                BoxWithConstraints(contentAlignment = Alignment.Center) {
                    val btnLabel = stringResource(R.string.general_send)
                    val density = LocalDensity.current
                    val availPx = with(density) { maxWidth.toPx() }
                    val pxPerChar = with(density) { (10.sp).toPx() }
                    val fitCount = (availPx / pxPerChar).toInt()
                    val fontSize = when {
                        btnLabel.length > (fitCount * 1.0f).toInt() -> 10.sp
                        btnLabel.length > (fitCount * 0.82f).toInt() -> 12.sp
                        btnLabel.length > (fitCount * 0.68f).toInt() -> 13.sp
                        else -> 15.sp
                    }
                    Text(btnLabel, color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Bold,
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
            }
            Button(
                onClick = onReceive,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.incoming),
            ) {
                BoxWithConstraints(contentAlignment = Alignment.Center) {
                    val btnLabel = stringResource(R.string.general_receive)
                    val density = LocalDensity.current
                    val availPx = with(density) { maxWidth.toPx() }
                    val pxPerChar = with(density) { (10.sp).toPx() }
                    val fitCount = (availPx / pxPerChar).toInt()
                    val fontSize = when {
                        btnLabel.length > (fitCount * 1.0f).toInt() -> 10.sp
                        btnLabel.length > (fitCount * 0.82f).toInt() -> 12.sp
                        btnLabel.length > (fitCount * 0.68f).toInt() -> 13.sp
                        else -> 15.sp
                    }
                    Text(btnLabel, color = C.textDark, fontSize = fontSize, fontWeight = FontWeight.Bold,
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // === Transaction History ===
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.wallet_transactions_title).uppercase(),
            color = C.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )

        if (assetTxs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.asset_no_transactions), color = C.textMuted, fontSize = 14.sp)
            }
        } else {
            val previewCount = 10
            assetTxs.take(previewCount).forEach { tx ->
                val effectiveOut = if (tx.isDapps && tx.amount > 0 && !tx.contractCids.isNullOrEmpty()) !tx.sender else tx.sender
                val isPending = tx.status == TxStatus.PENDING || tx.status == TxStatus.IN_PROGRESS || tx.status == TxStatus.REGISTERING
                val isFailed = tx.status == TxStatus.FAILED || tx.status == TxStatus.CANCELLED
                val isSelf = tx.selfTx
                val amountColor = if (isFailed) C.textSecondary else if (isSelf) C.textSecondary else if (effectiveOut) C.outgoing else C.incoming
                // Map known on-chain DApp names to localized labels
                val dappLabel = if (tx.appName?.contains("Assets Swap", ignoreCase = true) == true)
                    stringResource(R.string.tx_detail_assets_swap) else tx.appName
                val peerAddr = when {
                    tx.isDapps -> dappLabel ?: stringResource(R.string.wallet_dapp_label)
                    isSelf -> stringResource(R.string.wallet_self_label)
                    tx.peerId.isNotEmpty() -> if (tx.peerId.length > 16) "${tx.peerId.take(8)}...${tx.peerId.takeLast(8)}" else tx.peerId
                    else -> "—"
                }
                val addrType = when {
                    tx.isDapps -> ""
                    tx.isMaxPrivacy -> stringResource(R.string.wallet_addr_max_privacy)
                    tx.isPublicOffline -> stringResource(R.string.wallet_addr_public_offline)
                    tx.isOffline || tx.isShielded -> stringResource(R.string.wallet_addr_offline)
                    else -> stringResource(R.string.wallet_addr_regular)
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable { onTxDetail(tx.txId) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = C.card),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Direction dot
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (tx.isDapps) Color(0xFF9B59B6) else if (effectiveOut) C.outgoing else C.incoming),
                        )
                        Spacer(Modifier.width(12.dp))

                        // Info
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                buildString {
                                    if (tx.isDapps) {
                                        append(dappLabel ?: stringResource(R.string.wallet_dapp_label))
                                    } else if (isSelf) {
                                        append(stringResource(R.string.wallet_self_transfer))
                                    } else {
                                        append(if (tx.sender) stringResource(R.string.wallet_sent_label) else stringResource(R.string.wallet_received_label))
                                    }
                                    if (tx.message.isNotEmpty()) append(" · ${tx.message}")
                                },
                                color = C.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (addrType.isEmpty()) peerAddr else "$peerAddr · $addrType",
                                color = C.textSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        // Amount + time
                        Column(horizontalAlignment = Alignment.End) {
                            if (tx.isDapps && tx.contractAssets.isNotEmpty()) {
                                tx.contractAssets.forEach { ca ->
                                    val isSpending = ca.sending != 0L
                                    val displayAmt = Math.abs(if (isSpending) ca.sending else ca.receiving)
                                    val caPrefix = if (isSpending) "-" else "+"
                                    val caColor = if (isFailed) C.textSecondary else if (isSpending) C.outgoing else C.incoming
                                    val caTicker = if (ca.assetId != 0) assetTicker(ca.assetId) else "BEAM"
                                    if (displayAmt > 0) {
                                        Text(
                                            "$caPrefix${Helpers.formatBeam(displayAmt)} $caTicker",
                                            color = caColor,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        // Show fiat for priced assets
                                        if (rate > 0) {
                                            val caFiat = if (ca.assetId == 0) formatFiatCurrent(displayAmt, rate)
                                                          else CurrencyManager.assetToFiat(ca.assetId, displayAmt, currency, rate)?.let { CurrencyManager.formatFiat(it, currency) }
                                            if (caFiat != null) {
                                                Text("≈ $caFiat", color = C.textSecondary, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            } else if (isSelf) {
                                Text(
                                    "${Helpers.formatBeam(tx.amount)} $assetName",
                                    color = C.textSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (rate > 0) {
                                    val selfFiat = if (assetId == 0) formatFiatCurrent(tx.amount, rate)
                                                    else CurrencyManager.assetToFiat(assetId, tx.amount, currency, rate)?.let { CurrencyManager.formatFiat(it, currency) }
                                    if (selfFiat != null) {
                                        Text("≈ $selfFiat", color = C.textSecondary, fontSize = 10.sp)
                                    }
                                }
                            } else {
                                Text(
                                    "${if (effectiveOut) "−" else "+"}${Helpers.formatBeam(tx.amount)} $assetName",
                                    color = amountColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                // Fiat for priced assets
                                if (rate > 0) {
                                    val txFiat = if (assetId == 0) formatFiatCurrent(tx.amount, rate)
                                                  else CurrencyManager.assetToFiat(assetId, tx.amount, currency, rate)?.let { CurrencyManager.formatFiat(it, currency) }
                                    if (txFiat != null) {
                                        Text("≈ $txFiat", color = C.textSecondary, fontSize = 10.sp)
                                    }
                                }
                            }
                            Text(
                                if (isFailed) {
                                    when (tx.status) {
                                        TxStatus.FAILED -> stringResource(R.string.tx_status_failed).replaceFirstChar { it.uppercase() }
                                        TxStatus.CANCELLED -> stringResource(R.string.tx_status_cancelled).replaceFirstChar { it.uppercase() }
                                        else -> stringResource(R.string.tx_status_failed).replaceFirstChar { it.uppercase() }
                                    }
                                } else formatTxDate(tx.createTime),
                                color = when {
                                    isPending -> C.warning
                                    isFailed -> C.error
                                    else -> C.textMuted
                                },
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
            if (assetTxs.size > previewCount) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(C.card)
                        .clickable { onViewAll() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.wallet_view_all_txs, assetTxs.size),
                            color = C.accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("→", color = C.accent, fontSize = 14.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }

    // Split coins bottom sheet
    if (showSplitSheet) {
        SplitCoinsSheet(
            assetId = 0,
            assetTicker = assetName,
            onDismiss = { showSplitSheet = false },
        )
    }
}

@Composable
private fun BalanceRow(
    label: String,
    value: String,
    primary: Boolean = false,
    incoming: Boolean = false,
    outgoing: Boolean = false,
    locked: Boolean = false,
) {
    val valueColor = when {
        locked -> Color(0xFFFF9800)
        incoming -> C.accent
        outgoing -> C.outgoing
        primary -> C.text
        else -> C.textSecondary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = C.textSecondary, fontSize = 13.sp)
        Text(
            value,
            color = valueColor,
            fontSize = if (primary) 18.sp else 13.sp,
            fontWeight = if (primary) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

private val txDateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
private fun formatTxDate(timestamp: Long): String {
    return try { txDateFormat.format(Date(timestamp * 1000)) } catch (_: Exception) { "" }
}
