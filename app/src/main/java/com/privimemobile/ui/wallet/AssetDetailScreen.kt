package com.privimemobile.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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
                if (obj.optInt("assetId") != assetId) return@mapNotNull null
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

                // Divider
                HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 12.dp))

                // Balance breakdown
                BalanceRow("Available", "${Helpers.formatBeam(assetStatus.available)} $assetName", primary = true)

                if (assetStatus.shielded > 0 || (assetStatus.available > 0 && assetStatus.shielded != assetStatus.available)) {
                    val regularBal = assetStatus.available - assetStatus.shielded
                    if (regularBal > 0 || assetStatus.shielded > 0) {
                        BalanceRow("  Regular", "${Helpers.formatBeam(regularBal.coerceAtLeast(0))} $assetName")
                        BalanceRow("  Shielded", "${Helpers.formatBeam(assetStatus.shielded)} $assetName")
                    }
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
                val isOut = tx.sender
                val isPending = tx.status == TxStatus.PENDING || tx.status == TxStatus.IN_PROGRESS || tx.status == TxStatus.REGISTERING
                val isFailed = tx.status == TxStatus.FAILED || tx.status == TxStatus.CANCELLED
                val amountColor = if (isFailed) C.textSecondary else if (isOut) C.outgoing else C.incoming
                val peerAddr = if (tx.peerId.isNotEmpty()) {
                    if (tx.peerId.length > 16) "${tx.peerId.take(8)}...${tx.peerId.takeLast(8)}" else tx.peerId
                } else "—"
                val addrType = when {
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
                                .background(if (isOut) C.outgoing else C.incoming),
                        )
                        Spacer(Modifier.width(12.dp))

                        // Info
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                buildString {
                                    append(if (tx.sender) "Sent" else "Received")
                                    if (tx.message.isNotEmpty()) append(" · ${tx.message}")
                                },
                                color = C.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "$peerAddr · $addrType",
                                color = C.textSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        // Amount + time
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${if (isOut) "−" else "+"}${Helpers.formatBeam(tx.amount)}",
                                color = amountColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
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
