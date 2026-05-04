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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.R
import com.privimemobile.protocol.Helpers
import com.privimemobile.protocol.WalletApi
import com.privimemobile.ui.theme.C

import com.privimemobile.wallet.assetTicker

// UTXO status codes: Coin and ShieldedCoin have DIFFERENT mappings.
// Coin:   Unavail=0 Avail=1 Maturing=2 Outgoing=3 Incoming=4 Spent=6 Consumed=7
// ShldCoin: Unavail=0 Incoming=1 Avail=2 Maturing=3 Outgoing=4 Spent=5 Consumed=6
// Use status_string from the API for display; use isShielded+status for filtering.
private data class Utxo(
    val id: Long,
    val stringId: String,
    val amount: Long,
    val status: Int,
    val statusString: String,
    val maturity: Long,
    val confirmHeight: Long,
    val createTxId: String,
    val spentTxId: String,
    val assetId: Int,
    val isShielded: Boolean,
)

private enum class UtxoFilter(@androidx.annotation.StringRes val labelResId: Int) {
    ALL(R.string.utxo_filter_all),
    AVAILABLE(R.string.utxo_filter_available),
    MATURING(R.string.utxo_filter_maturing),
    SPENT(R.string.utxo_filter_spent),
}

@Composable
fun UTXOScreen(onBack: () -> Unit = {}, initialAssetId: Int = 0) {
    var filter by remember { mutableStateOf(UtxoFilter.ALL) }
    var selectedAsset by remember { mutableIntStateOf(initialAssetId) }

    // Fetch UTXOs via get_utxo API (returns both regular + shielded in one call)
    var utxos by remember { mutableStateOf<List<Utxo>>(emptyList()) }
    LaunchedEffect(Unit) {
        try {
            val result = WalletApi.callAsyncDirect("get_utxo", mapOf("assets" to true))
            android.util.Log.d("UTXO", "get_utxo keys: ${result.keys}, size=${result.size}")
            // WalletApi wraps JSONArray results as mapOf("messages" to List<Map>)
            val rawList = result["messages"] as? List<*>
            if (rawList != null) {
                android.util.Log.d("UTXO", "Parsing ${rawList.size} UTXOs from messages")
                utxos = rawList.mapNotNull { item ->
                    val obj = item as? Map<*, *> ?: return@mapNotNull null
                    val type = (obj["type"] as? String) ?: ""
                    val isShielded = type == "shld" || (obj["isShielded"] as? Boolean) ?: false
                    Utxo(
                        id = (obj["id"] as? String)?.toLongOrNull() ?: 0L,
                        stringId = (obj["id"] as? String) ?: "",
                        amount = (obj["amount"] as? Number)?.toLong() ?: 0L,
                        status = (obj["status"] as? Number)?.toInt() ?: 0,
                        statusString = (obj["status_string"] as? String) ?: "",
                        maturity = (obj["maturity"] as? Number)?.toLong() ?: 0L,
                        confirmHeight = 0L,
                        createTxId = (obj["createTxId"] as? String) ?: "",
                        spentTxId = (obj["spentTxId"] as? String) ?: "",
                        assetId = (obj["asset_id"] as? Number)?.toInt() ?: (obj["assetId"] as? Number)?.toInt() ?: 0,
                        isShielded = isShielded,
                    )
                }
            } else {
                android.util.Log.w("UTXO", "No 'messages' key in result. Full result: $result")
            }
        } catch (e: Exception) {
            android.util.Log.e("UTXO", "get_utxo failed: ${e.message}", e)
        }
    }

    // Unique asset IDs from available UTXOs
    val assetIds = remember(utxos) {
        utxos.map { it.assetId }.distinct().sorted()
    }

    // Reset selected asset if it no longer exists (skip if initialAssetId still valid)
    LaunchedEffect(assetIds) {
        if (initialAssetId > 0 && initialAssetId in assetIds) {
            if (selectedAsset != initialAssetId) selectedAsset = initialAssetId
        } else if (selectedAsset !in assetIds) {
            selectedAsset = 0
        }
    }

    // Filter by status + optionally by asset
    val filtered = remember(utxos, filter, selectedAsset) {
        val byStatus = when (filter) {
            UtxoFilter.ALL -> utxos
            UtxoFilter.AVAILABLE -> utxos.filter { isAvailableStatus(it) }
            UtxoFilter.MATURING -> utxos.filter { isMaturingStatus(it) }
            UtxoFilter.SPENT -> utxos.filter { isSpentStatus(it) }
        }
        val list = byStatus.filter { it.assetId == selectedAsset }
        list.sortedWith(compareBy<Utxo> { it.status }.thenByDescending { it.amount })
    }

    // Per-asset totals from UTXO list
    val scopeUtxos = utxos.filter { it.assetId == selectedAsset }
    val scopeAvailable = scopeUtxos.filter { isAvailableStatus(it) }.sumOf { it.amount }
    val scopeMaturing = scopeUtxos.filter { isMaturingStatus(it) }.sumOf { it.amount }
    val totalAvailable = scopeAvailable

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
            Text(stringResource(R.string.dapps_back_button), color = C.textSecondary)
        }

        // Summary card
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = C.card),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SummaryItem(stringResource(R.string.balance_available), Helpers.formatBeam(totalAvailable), C.text, Modifier.weight(1f))
                    Box(Modifier.width(1.dp).height(32.dp).background(C.border))
                    SummaryItem(stringResource(R.string.balance_maturing), Helpers.formatBeam(scopeMaturing), C.warning, Modifier.weight(1f))
                    Box(Modifier.width(1.dp).height(32.dp).background(C.border))
                    SummaryItem(stringResource(R.string.utxo_total_label), scopeUtxos.size.toString(), C.text, Modifier.weight(1f))
                }
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
                    label = stringResource(f.labelResId),
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
                    text = if (utxos.isEmpty()) stringResource(R.string.utxo_empty_waiting)
                    else stringResource(R.string.utxo_empty_filter),
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
    val statusColor = utxoStatusColor(utxo)
    val statusLabel = utxoStatusLabel(utxo)
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
                UtxoDetailRow(stringResource(R.string.utxo_asset_id), "#${utxo.assetId}")
            }
            UtxoDetailRow(stringResource(R.string.general_type),
                if (utxo.isShielded) stringResource(R.string.balance_shielded) else stringResource(R.string.wallet_addr_regular))
            if (utxo.maturity > 0) {
                UtxoDetailRow(stringResource(R.string.utxo_maturity), stringResource(R.string.wallet_block_height, utxo.maturity))
            }
            if (utxo.confirmHeight > 0) {
                UtxoDetailRow(stringResource(R.string.utxo_confirmed), stringResource(R.string.wallet_block_height, utxo.confirmHeight))
            }
            if (utxo.createTxId.isNotEmpty()) {
                UtxoDetailRow(stringResource(R.string.utxo_created_by), Helpers.truncateKey(utxo.createTxId, 6, 6))
            }
            if (utxo.spentTxId.isNotEmpty()) {
                UtxoDetailRow(stringResource(R.string.utxo_spent_by), Helpers.truncateKey(utxo.spentTxId, 6, 6))
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

// Status filtering handles both Coin and ShieldedCoin status mappings.
// Coin: Avail=1 Maturing=2 Outgoing=3 Incoming=4 Spent=6 Consumed=7
// ShldCoin: Incoming=1 Avail=2 Maturing=3 Outgoing=4 Spent=5 Consumed=6
private fun isAvailableStatus(utxo: Utxo): Boolean {
    if (utxo.isShielded) return utxo.status == 2 // ShieldedCoin::Available
    return utxo.status == 1 // Coin::Available
}
private fun isMaturingStatus(utxo: Utxo): Boolean {
    if (utxo.isShielded) return utxo.status == 3 // ShieldedCoin::Maturing
    return utxo.status == 2 // Coin::Maturing
}
private fun isSpentStatus(utxo: Utxo): Boolean {
    if (utxo.isShielded) return utxo.status == 5 || utxo.status == 6 // ShieldedCoin::Spent/Consumed
    return utxo.status == 6 || utxo.status == 7 || utxo.status == 3 // Coin::Spent/Consumed/Outgoing
}

private fun utxoStatusColor(utxo: Utxo): Color = when {
    isAvailableStatus(utxo) -> C.online
    isMaturingStatus(utxo) -> C.warning
    utxo.isShielded && utxo.status == 1 -> C.warning // ShieldedCoin::Incoming
    !utxo.isShielded && utxo.status == 4 -> C.warning // Coin::Incoming
    utxo.isShielded && utxo.status == 4 -> C.outgoing // ShieldedCoin::Outgoing
    !utxo.isShielded && utxo.status == 3 -> C.outgoing // Coin::Outgoing
    isSpentStatus(utxo) -> C.textSecondary.copy(alpha = 0.5f)
    else -> C.textSecondary
}

@Composable
private fun utxoStatusLabel(utxo: Utxo): String = when {
        isAvailableStatus(utxo) -> stringResource(R.string.utxo_label_available)
        isMaturingStatus(utxo) -> stringResource(R.string.utxo_label_maturing)
        utxo.status == 0 -> stringResource(R.string.utxo_label_unavailable)
        utxo.isShielded && utxo.status == 1 -> stringResource(R.string.utxo_label_incoming)
        !utxo.isShielded && utxo.status == 4 -> stringResource(R.string.utxo_label_incoming)
        utxo.isShielded && utxo.status == 4 -> stringResource(R.string.utxo_label_outgoing)
        !utxo.isShielded && utxo.status == 3 -> stringResource(R.string.utxo_label_outgoing)
        isSpentStatus(utxo) -> stringResource(R.string.utxo_label_spent)
        else -> stringResource(R.string.utxo_label_unknown)
    }


