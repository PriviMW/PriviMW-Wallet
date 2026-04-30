package com.privimemobile.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.protocol.Helpers
import com.privimemobile.ui.theme.C
import com.privimemobile.wallet.WalletEventBus
import com.privimemobile.wallet.assetTicker
import org.json.JSONArray

// UTXO status codes from Beam C++ core
private object UtxoStatus {
    const val AVAILABLE = 1
    const val MATURING = 2
    const val UNAVAILABLE = 3
    const val OUTGOING = 4
    const val INCOMING = 5
    const val SPENT = 6
    const val CONSUMED = 7
}

private data class Utxo(
    val id: Long,
    val stringId: String,
    val amount: Long,
    val status: Int,
    val maturity: Long,
    val confirmHeight: Long,
    val createTxId: String,
    val spentTxId: String,
    val assetId: Int,
    val isShielded: Boolean,
)

private enum class UtxoFilter(val label: String) {
    ALL("All"),
    AVAILABLE("Available"),
    MATURING("Maturing"),
    SPENT("Spent"),
}

@Composable
fun UTXOScreen(onBack: () -> Unit = {}) {
    LaunchedEffect(Unit) {
        try { com.privimemobile.wallet.WalletManager.walletInstance?.getAllUtxosStatus() } catch (_: Exception) {}
    }

    val utxoJson by WalletEventBus.utxos.collectAsState()
    var filter by remember { mutableStateOf(UtxoFilter.ALL) }
    var selectedAsset by remember { mutableIntStateOf(0) } // default to BEAM

    val utxos = remember(utxoJson) {
        try {
            val arr = JSONArray(utxoJson)
            parseUtxos(arr)
        } catch (_: Exception) { emptyList() }
    }

    // Unique asset IDs from available UTXOs
    val assetIds = remember(utxos) {
        utxos.map { it.assetId }.distinct().sorted()
    }

    // Reset selected asset if it no longer exists
    LaunchedEffect(assetIds) {
        if (selectedAsset !in assetIds) {
            selectedAsset = 0
        }
    }

    // Filter by status + optionally by asset
    val filtered = remember(utxos, filter, selectedAsset) {
        val byStatus = when (filter) {
            UtxoFilter.ALL -> utxos
            UtxoFilter.AVAILABLE -> utxos.filter { it.status == UtxoStatus.AVAILABLE }
            UtxoFilter.MATURING -> utxos.filter {
                it.status == UtxoStatus.MATURING || it.status == UtxoStatus.INCOMING
            }
            UtxoFilter.SPENT -> utxos.filter {
                it.status == UtxoStatus.SPENT ||
                        it.status == UtxoStatus.CONSUMED ||
                        it.status == UtxoStatus.OUTGOING
            }
        }
        val list = byStatus.filter { it.assetId == selectedAsset }
        list.sortedWith(compareBy<Utxo> { it.status }.thenByDescending { it.amount })
    }

    // Per-asset totals
    val scopeUtxos = utxos.filter { it.assetId == selectedAsset }
    val scopeAvailable = scopeUtxos.filter { it.status == UtxoStatus.AVAILABLE }.sumOf { it.amount }
    val scopeMaturing = scopeUtxos.filter { it.status == UtxoStatus.MATURING || it.status == UtxoStatus.INCOMING }.sumOf { it.amount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
            Text("< Back", color = C.textSecondary)
        }

        // Summary card
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = C.card),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SummaryItem("Available", Helpers.formatBeam(scopeAvailable), C.text, Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(32.dp).background(C.border))
                SummaryItem("Maturing", Helpers.formatBeam(scopeMaturing), C.warning, Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(32.dp).background(C.border))
                SummaryItem("Total UTXOs", scopeUtxos.size.toString(), C.text, Modifier.weight(1f))
            }
        }

        // Asset selector
        Spacer(Modifier.height(12.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(assetIds.ifEmpty { listOf(0) }) { assetId ->
                val isSelected = selectedAsset == assetId
                val ticker = assetTicker(assetId)
                Surface(
                    onClick = { selectedAsset = assetId },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) C.accent else C.card,
                    border = ButtonDefaults.outlinedButtonBorder(true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(if (isSelected) C.accent else C.border)
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        com.privimemobile.ui.components.AssetIcon(assetId = assetId, ticker = ticker, size = 16.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            ticker,
                            color = if (isSelected) C.textDark else C.text,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        // Filter tabs
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UtxoFilter.entries.forEach { f ->
                FilterChip(
                    label = f.label,
                    selected = filter == f,
                    onClick = { filter = f },
                )
            }
        }

        // UTXO list
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (utxos.isEmpty()) "No UTXOs yet — waiting for data..."
                    else "No UTXOs match this filter",
                    color = C.textSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered) { utxo ->
                    UtxoCard(utxo)
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String, valueColor: Color, modifier: Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, color = C.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val density = LocalDensity.current
            val availPx = with(density) { maxWidth.toPx() }
            val pxPerChar = with(density) { (18.sp).toPx() }
            val fitCount = (availPx / pxPerChar).toInt()
            val fontSize = when {
                value.length > (fitCount * 1.0f).toInt() -> 12.sp
                value.length > (fitCount * 0.82f).toInt() -> 14.sp
                value.length > (fitCount * 0.68f).toInt() -> 15.sp
                else -> 16.sp
            }
            Text(
                value,
                color = valueColor,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Color(0x1A25D4D0) else Color.Transparent,
        border = ButtonDefaults.outlinedButtonBorder(true).copy(
            brush = androidx.compose.ui.graphics.SolidColor(if (selected) C.accent else C.border)
        ),
    ) {
        Text(
            text = label,
            color = if (selected) C.accent else C.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun UtxoCard(utxo: Utxo) {
    val statusColor = utxoStatusColor(utxo.status)
    val statusLabel = utxoStatusLabel(utxo.status)
    val ticker = com.privimemobile.wallet.assetTicker(utxo.assetId)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = C.card),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: amount + status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${Helpers.formatBeam(utxo.amount)} $ticker",
                        color = C.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Transparent,
                    border = ButtonDefaults.outlinedButtonBorder(true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(statusColor)
                    ),
                ) {
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            // Detail rows
            Spacer(Modifier.height(4.dp))
            if (utxo.assetId != 0) {
                UtxoDetailRow("Asset ID", "#${utxo.assetId}")
            }
            if (utxo.isShielded) {
                UtxoDetailRow("Type", "Shielded")
            }
            if (utxo.maturity > 0) {
                UtxoDetailRow("Maturity", "Block #${utxo.maturity}")
            }
            if (utxo.confirmHeight > 0) {
                UtxoDetailRow("Confirmed", "Block #${utxo.confirmHeight}")
            }
            if (utxo.createTxId.isNotEmpty()) {
                UtxoDetailRow("Created by", Helpers.truncateKey(utxo.createTxId, 6, 6))
            }
            if (utxo.spentTxId.isNotEmpty()) {
                UtxoDetailRow("Spent by", Helpers.truncateKey(utxo.spentTxId, 6, 6))
            }
        }
    }
}

@Composable
private fun UtxoDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = C.textSecondary.copy(alpha = 0.7f), fontSize = 12.sp)
        Text(value, color = C.textSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

private fun utxoStatusColor(status: Int): Color = when (status) {
    UtxoStatus.AVAILABLE -> C.online
    UtxoStatus.MATURING, UtxoStatus.INCOMING -> C.warning
    UtxoStatus.OUTGOING -> C.outgoing
    UtxoStatus.SPENT, UtxoStatus.CONSUMED -> C.textSecondary.copy(alpha = 0.5f)
    else -> C.textSecondary
}

private fun utxoStatusLabel(status: Int): String = when (status) {
    UtxoStatus.AVAILABLE -> "Available"
    UtxoStatus.MATURING -> "Maturing"
    UtxoStatus.UNAVAILABLE -> "Unavailable"
    UtxoStatus.OUTGOING -> "Outgoing"
    UtxoStatus.INCOMING -> "Incoming"
    UtxoStatus.SPENT -> "Spent"
    UtxoStatus.CONSUMED -> "Consumed"
    else -> "Unknown"
}

private fun parseUtxos(arr: JSONArray): List<Utxo> {
    return (0 until arr.length()).mapNotNull { i ->
        val obj = arr.optJSONObject(i) ?: return@mapNotNull null
        Utxo(
            id = obj.optLong("id"),
            stringId = obj.optString("stringId", ""),
            amount = obj.optLong("amount"),
            status = obj.optInt("status"),
            maturity = obj.optLong("maturity"),
            confirmHeight = obj.optLong("confirmHeight"),
            createTxId = obj.optString("createTxId", ""),
            spentTxId = obj.optString("spentTxId", ""),
            assetId = obj.optInt("assetId"),
            isShielded = obj.optBoolean("isShielded"),
        )
    }
}
