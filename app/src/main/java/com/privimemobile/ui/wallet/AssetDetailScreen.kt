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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.protocol.Helpers
import com.privimemobile.protocol.SecureStorage
import com.privimemobile.ui.theme.C
import com.privimemobile.wallet.AssetInfoEvent
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        TextButton(onClick = onBack) {
            Text("< Back", color = C.textSecondary)
        }

        // === Asset Card ===
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = C.card),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header: icon + name
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.privimemobile.ui.components.AssetIcon(assetId = assetId, ticker = assetName, size = 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(assetName, color = C.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        if (fullName != assetName) {
                            Text(fullName, color = C.textSecondary, fontSize = 13.sp)
                        }
                        if (assetId != 0) {
                            Spacer(Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = C.border,
                            ) {
                                Text(
                                    "ID: $assetId",
                                    color = C.textSecondary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }

                val totalBalance = assetStatus.available + assetStatus.shielded
                val exchRates by WalletEventBus.exchangeRates.collectAsState()
                val usdRate = exchRates["beam_usd"] ?: 0.0

                // Divider
                HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 12.dp))

                // Balance breakdown — available + shielded are separate fields from C++ core
                val totalAvailable = totalBalance
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Available", color = C.textSecondary, fontSize = 13.sp)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${Helpers.formatBeam(totalAvailable)} $assetName",
                            color = C.text,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        if (assetId == 0 && usdRate > 0) {
                            val usd = formatUsd(totalAvailable, usdRate)
                            if (usd != null) {
                                Text("≈ $usd USD", color = C.textSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }

                if (assetStatus.available > 0 && assetStatus.shielded > 0) {
                    BalanceRow("  Regular", "${Helpers.formatBeam(assetStatus.available)} $assetName")
                    BalanceRow("  Shielded", "${Helpers.formatBeam(assetStatus.shielded)} $assetName")
                }
                if (assetStatus.maturing > 0) {
                    val lockLabel = if (lockHours > 0) "Locked (min ${lockHours}h)" else "Locked"
                    BalanceRow(lockLabel, "${Helpers.formatBeam(assetStatus.maturing)} $assetName", locked = true)
                }
                if (assetStatus.maxPrivacy > 0) {
                    BalanceRow("Max Privacy (locked)", "${Helpers.formatBeam(assetStatus.maxPrivacy)} $assetName", locked = true)
                }
                if (assetStatus.sending > 0) {
                    BalanceRow("Sending", "${Helpers.formatBeam(assetStatus.sending)} $assetName", outgoing = true)
                }
                if (assetStatus.receiving > 0) {
                    BalanceRow("Receiving", "${Helpers.formatBeam(assetStatus.receiving)} $assetName", incoming = true)
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
                Text("Send", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onReceive,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.incoming),
            ) {
                Text("Receive", color = C.textDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }

        // === Transaction History ===
        Spacer(Modifier.height(24.dp))
        Text(
            "TRANSACTIONS",
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
                Text("No transactions for this asset", color = C.textMuted, fontSize = 14.sp)
            }
        } else {
            assetTxs.forEach { tx ->
                val effectiveOut = if (tx.isDapps && tx.amount > 0 && !tx.contractCids.isNullOrEmpty()) !tx.sender else tx.sender
                val isPending = tx.status == TxStatus.PENDING || tx.status == TxStatus.IN_PROGRESS || tx.status == TxStatus.REGISTERING
                val isFailed = tx.status == TxStatus.FAILED || tx.status == TxStatus.CANCELLED
                val amountColor = if (isFailed) C.textSecondary else if (effectiveOut) C.outgoing else C.incoming
                val peerAddr = when {
                    tx.isDapps -> tx.appName ?: "DApp"
                    tx.peerId.isNotEmpty() -> if (tx.peerId.length > 16) "${tx.peerId.take(8)}...${tx.peerId.takeLast(8)}" else tx.peerId
                    else -> "—"
                }
                val addrType = when {
                    tx.isDapps -> ""
                    tx.isMaxPrivacy -> "Max Privacy"
                    tx.isPublicOffline -> "Public Offline"
                    tx.isOffline || tx.isShielded -> "Offline"
                    else -> "Regular"
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
                                        append(tx.appName ?: "DApp")
                                    } else {
                                        append(if (tx.sender) "Sent" else "Received")
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
                                    }
                                }
                            } else {
                                Text(
                                    "${if (effectiveOut) "−" else "+"}${Helpers.formatBeam(tx.amount)} $assetName",
                                    color = amountColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            // USD at TX time
                            if (assetId == 0) {
                                val txRate = TxRateStore.get(tx.txId)
                                val txUsd = formatUsd(tx.amount, txRate)
                                if (txUsd != null) {
                                    Text("≈ $txUsd USD", color = C.textSecondary, fontSize = 10.sp)
                                }
                            }
                            Text(
                                if (isFailed) {
                                    when (tx.status) {
                                        TxStatus.FAILED -> "Failed"
                                        TxStatus.CANCELLED -> "Cancelled"
                                        else -> "Failed"
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
        }

        Spacer(Modifier.height(40.dp))
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
        incoming -> C.incoming
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
