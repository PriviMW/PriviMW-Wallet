package com.privimemobile.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import com.privimemobile.wallet.WalletEventBus
import com.privimemobile.wallet.WalletManager
import com.privimemobile.wallet.WalletStatusEvent
import com.privimemobile.wallet.SyncProgressEvent
import com.privimemobile.wallet.NodeConnectionEvent
import com.privimemobile.wallet.AssetInfoEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

// TX status codes
internal object TxStatus {
    const val PENDING = 0
    const val IN_PROGRESS = 1
    const val CANCELLED = 2
    const val COMPLETED = 3
    const val FAILED = 4
    const val REGISTERING = 5
}

/** Transaction display model. */
private data class TxItem(
    val txId: String,
    val amount: Long,
    val fee: Long,
    val sender: Boolean,
    val status: Int,
    val message: String,
    val createTime: Long,
    val assetId: Int,
    val peerId: String,
    val isShielded: Boolean = false,
    val isMaxPrivacy: Boolean = false,
    val isOffline: Boolean = false,
    val isPublicOffline: Boolean = false,
)

/** Asset balance model for non-BEAM assets. */
private data class AssetBalance(
    val assetId: Int,
    val available: Long,
    val maturing: Long,
    val sending: Long,
    val receiving: Long,
    val maxPrivacy: Long,
    val unitName: String,
    val shortName: String,
    val name: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    onSend: () -> Unit = {},
    onReceive: () -> Unit = {},
    onTxDetail: (String) -> Unit = {},
    onAssetDetail: (Int) -> Unit = {},
) {
    val walletStatus by WalletEventBus.walletStatus.collectAsState(
        initial = WalletStatusEvent(0, 0, 0, 0)
    )
    val txJson by WalletEventBus.transactions.collectAsState(initial = "[]")
    val syncProgress by WalletEventBus.syncProgress.collectAsState(
        initial = SyncProgressEvent(0, 0)
    )
    val nodeConnection by WalletEventBus.nodeConnection.collectAsState(
        initial = NodeConnectionEvent(connected = false)
    )

    // Collect asset info events to build asset name map
    val assetInfoMap = remember { mutableStateMapOf<Int, AssetInfoEvent>() }
    LaunchedEffect(Unit) {
        WalletEventBus.assetInfo.collect { event ->
            assetInfoMap[event.id] = event
        }
    }

    val scope = rememberCoroutineScope()

    // Parse transactions from JSON
    val transactions = remember(txJson) {
        try {
            val arr = JSONArray(txJson)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
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
        } catch (_: Exception) {
            emptyList()
        }
    }

    // Build other-asset balances from TX data + asset info
    // In production this would come from wallet_status asset breakdown
    val otherAssets = remember(txJson, assetInfoMap.size) {
        val assetAmounts = mutableMapOf<Int, MutableList<TxItem>>()
        transactions.forEach { tx ->
            if (tx.assetId != 0) {
                assetAmounts.getOrPut(tx.assetId) { mutableListOf() }.add(tx)
            }
        }
        // Build AssetBalance from the info we have
        assetAmounts.keys.mapNotNull { assetId ->
            val info = assetInfoMap[assetId]
            AssetBalance(
                assetId = assetId,
                available = 0, // Would come from wallet_status per-asset
                maturing = 0,
                sending = 0,
                receiving = 0,
                maxPrivacy = 0,
                unitName = info?.unitName ?: "",
                shortName = info?.shortName ?: "",
                name = info?.name ?: "",
            )
        }.filter { it.available > 0 || it.unitName.isNotEmpty() }
            .sortedBy { it.assetId }
    }

    // Pull to refresh
    var refreshing by remember { mutableStateOf(false) }
    fun doRefresh() {
        refreshing = true
        val wallet = WalletManager.walletInstance
        if (wallet != null) {
            try {
                wallet.getWalletStatus()
                wallet.getTransactions()
                wallet.getAddresses(true)
            } catch (_: Exception) {}
        }
        scope.launch {
            delay(2000)
            refreshing = false
        }
    }

    // Sync status
    val isSyncing = syncProgress.total > 0 && syncProgress.done < syncProgress.total
    val syncPercent = if (syncProgress.total > 0)
        (syncProgress.done.toFloat() / syncProgress.total * 100).toInt().coerceAtMost(100)
    else 0
    val isConnected = nodeConnection.connected

    val nodeLabel = SecureStorage.getString("node_mode")?.let {
        if (it == "own") "Own Node" else "Random Node"
    } ?: "Random Node"

    val statusText = when {
        !isConnected -> "Connecting to node..."
        isSyncing && syncProgress.total > 0 ->
            "Syncing ${syncPercent}% (${syncProgress.done / 1000}k / ${syncProgress.total / 1000}k blocks)"
        isSyncing -> "Syncing..."
        walletStatus.height > 0 -> "$nodeLabel \u00B7 Block #${walletStatus.height}"
        else -> "$nodeLabel \u00B7 Connected"
    }

    val statusDotColor = when {
        !isConnected -> Color(0xFF666666) // offline gray
        isSyncing -> C.warning
        else -> C.online
    }

    val hasPending = walletStatus.sending > 0 || walletStatus.receiving > 0 || walletStatus.maturing > 0

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { doRefresh() },
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            // Balance Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = C.card),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Status row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 16.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusDotColor),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(statusText, color = C.textSecondary, fontSize = 12.sp)
                        }

                        // Sync progress bar
                        if (isSyncing && syncProgress.total > 0) {
                            LinearProgressIndicator(
                                progress = { syncPercent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = C.warning,
                                trackColor = C.border,
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        Text("Available", color = C.textSecondary, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${Helpers.formatBeam(walletStatus.available)} BEAM",
                            color = C.text,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                        )

                        // Pending amounts
                        if (hasPending) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (walletStatus.sending > 0) {
                                    Text(
                                        "Sending: ${Helpers.formatBeam(walletStatus.sending)}",
                                        color = C.textSecondary,
                                        fontSize = 12.sp,
                                    )
                                }
                                if (walletStatus.receiving > 0) {
                                    Text(
                                        "Receiving: ${Helpers.formatBeam(walletStatus.receiving)}",
                                        color = C.accent,
                                        fontSize = 12.sp,
                                    )
                                }
                                if (walletStatus.maturing > 0) {
                                    Text(
                                        "Locked: ${Helpers.formatBeam(walletStatus.maturing)}",
                                        color = Color(0xFFFF9800),
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }

                        // Send / Receive buttons
                        Spacer(Modifier.height(20.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Button(
                                onClick = onSend,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                            ) {
                                Text(">", color = C.textDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(6.dp))
                                Text("Send", color = C.textDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = onReceive,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                            ) {
                                Text("<", color = C.textDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(6.dp))
                                Text("Receive", color = C.textDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // BEAM asset row
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp)
                        .clickable { onAssetDetail(0) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = C.card),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // BEAM icon circle
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0x2625D4D0)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("B", color = C.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("BEAM", color = C.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            if (hasPending) {
                                val subParts = mutableListOf<String>()
                                if (walletStatus.sending > 0) subParts.add("Sending: ${Helpers.formatBeam(walletStatus.sending)}")
                                if (walletStatus.receiving > 0) subParts.add("Receiving: ${Helpers.formatBeam(walletStatus.receiving)}")
                                if (walletStatus.maturing > 0) subParts.add("Locked: ${Helpers.formatBeam(walletStatus.maturing)}")
                                Text(
                                    subParts.joinToString("  "),
                                    color = C.textSecondary,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                        Text(
                            Helpers.formatBeam(walletStatus.available),
                            color = C.text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            // Other assets
            if (otherAssets.isNotEmpty()) {
                item {
                    Text(
                        "OTHER ASSETS",
                        color = C.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp),
                    )
                }
                items(otherAssets, key = { it.assetId }) { asset ->
                    val assetLabel = asset.unitName.ifEmpty { asset.shortName.ifEmpty { asset.name.ifEmpty { "Asset #${asset.assetId}" } } }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp)
                            .clickable { onAssetDetail(asset.assetId) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = C.card),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x2625D4D0)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    assetLabel.first().uppercase(),
                                    color = C.accent,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(assetLabel, color = C.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                val subParts = mutableListOf<String>()
                                if (asset.sending > 0) subParts.add("Sending: ${Helpers.formatBeam(asset.sending)}")
                                if (asset.receiving > 0) subParts.add("Receiving: ${Helpers.formatBeam(asset.receiving)}")
                                if (asset.maturing > 0) subParts.add("Locked: ${Helpers.formatBeam(asset.maturing)}")
                                if (asset.maxPrivacy > 0) subParts.add("Max Privacy: ${Helpers.formatBeam(asset.maxPrivacy)}")
                                if (subParts.isNotEmpty()) {
                                    Text(
                                        subParts.joinToString("  "),
                                        color = C.textSecondary,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                            Text(
                                Helpers.formatBeam(asset.available),
                                color = C.text,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }

            // Transactions header
            item {
                Text(
                    "TRANSACTIONS",
                    color = C.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
                )
            }

            if (transactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (isSyncing) "Syncing blockchain..." else "No transactions yet",
                            color = C.textSecondary,
                            fontSize = 14.sp,
                        )
                    }
                }
            } else {
                items(transactions, key = { it.txId }) { tx ->
                    TxCard(
                        tx = tx,
                        assetInfoMap = assetInfoMap,
                        onClick = { onTxDetail(tx.txId) },
                    )
                }
            }
        }

    }
}

@Composable
private fun TxCard(
    tx: TxItem,
    assetInfoMap: Map<Int, AssetInfoEvent>,
    onClick: () -> Unit,
) {
    val isSend = tx.sender

    // Status text and color
    val statusText = when (tx.status) {
        TxStatus.PENDING -> "Pending"
        TxStatus.IN_PROGRESS -> "In Progress"
        TxStatus.CANCELLED -> "Cancelled"
        TxStatus.COMPLETED -> "Completed"
        TxStatus.FAILED -> "Failed"
        TxStatus.REGISTERING -> "Registering"
        else -> "Unknown"
    }

    val isActive = tx.status == TxStatus.PENDING ||
            tx.status == TxStatus.IN_PROGRESS ||
            tx.status == TxStatus.REGISTERING
    val isFailed = tx.status == TxStatus.FAILED || tx.status == TxStatus.CANCELLED

    val statusColor = when {
        isActive -> C.warning
        isFailed -> C.error
        else -> C.textSecondary
    }

    // Peer address display
    val peerAddr = if (tx.peerId.isNotEmpty()) {
        if (tx.peerId.length > 12)
            "${tx.peerId.take(6)}...${tx.peerId.takeLast(6)}"
        else tx.peerId
    } else {
        when {
            tx.isMaxPrivacy -> "Max Privacy"
            tx.isPublicOffline -> "Public Offline"
            tx.isOffline || tx.isShielded -> "Offline"
            else -> "Regular"
        }
    }

    // Asset label for non-BEAM assets
    val assetLabel = if (tx.assetId != 0) {
        val info = assetInfoMap[tx.assetId]
        info?.unitName?.ifEmpty { null } ?: info?.name?.ifEmpty { null } ?: "Asset #${tx.assetId}"
    } else ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = C.card),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Direction icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isSend) Color(0x26FF6B6B) else Color(0x2625D4D0)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (isSend) ">" else "<",
                    color = C.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(10.dp))

            // Type + peer
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    buildString {
                        append(if (isSend) "Sent" else "Received")
                        if (tx.message.isNotEmpty()) append(" - ${tx.message}")
                    },
                    color = C.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    peerAddr,
                    color = C.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            // Amount + status
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (isSend) "-" else "+"}${Helpers.formatBeam(tx.amount)}${if (assetLabel.isNotEmpty()) " $assetLabel" else ""}",
                    color = if (isSend) C.outgoing else C.incoming,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "$statusText ${formatDate(tx.createTime)}",
                    color = statusColor,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

private val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
private fun formatDate(timestamp: Long): String {
    return try { dateFormat.format(Date(timestamp * 1000)) } catch (_: Exception) { "" }
}
