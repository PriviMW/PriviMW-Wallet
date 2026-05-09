package com.privimemobile.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.privimemobile.R
import com.privimemobile.protocol.Helpers
import com.privimemobile.protocol.NodeReconnect
import com.privimemobile.protocol.SecureStorage
import com.privimemobile.ui.theme.C
import com.privimemobile.wallet.CurrencyManager
import com.privimemobile.wallet.PortfolioSnapshotStore
import com.privimemobile.wallet.WalletEventBus
import com.privimemobile.wallet.WalletManager
import com.privimemobile.wallet.WalletStatusEvent
import com.privimemobile.wallet.SyncProgressEvent
import com.privimemobile.wallet.NodeConnectionEvent
import com.privimemobile.wallet.AssetInfoEvent
import com.privimemobile.wallet.assetTicker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

// TX status codes — shared with TransactionDetailScreen
internal object TxStatus {
    const val PENDING = 0
    const val IN_PROGRESS = 1
    const val CANCELLED = 2
    const val COMPLETED = 3
    const val FAILED = 4
    const val REGISTERING = 5
}

/** Transaction display model — shared with AssetDetailScreen. */
internal data class TxItem(
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
    val isDapps: Boolean = false,
    val appName: String? = null,
    val appID: String? = null,
    val contractCids: String? = null,
    val contractAssets: List<ContractAsset> = emptyList(),
    val usdRate: Double = 0.0,
    val selfTx: Boolean = false,
)

/** Per-asset spend/receive entry for contract TXs (like beam-ui's _contractSpend). */
internal data class ContractAsset(
    val assetId: Int,
    val sending: Long,   // amount spent (positive = you're paying)
    val receiving: Long,  // amount received (positive = you're getting)
)

/** Asset balance model for non-BEAM assets. */
private data class AssetBalance(
    val assetId: Int,
    val available: Long,
    val maturing: Long,
    val sending: Long,
    val receiving: Long,
    val maxPrivacy: Long,
    val shielded: Long,
    val unitName: String,
    val shortName: String,
    val name: String,
)

/** Store/load USD rate per TX at creation time. */
object TxRateStore {
    private const val PREFS = "tx_usd_rates"
    private var prefs: android.content.SharedPreferences? = null
    fun init(ctx: android.content.Context) {
        if (prefs == null) prefs = ctx.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
    }
    fun save(txId: String, rate: Double) {
        if (rate > 0) prefs?.edit()?.putFloat(txId, rate.toFloat())?.apply()
    }
    fun get(txId: String): Double = prefs?.getFloat(txId, 0f)?.toDouble() ?: 0.0
    /** Save current rate for any new TXs that don't have a stored rate. */
    fun backfillNewTxs(txIds: List<String>, currentRate: Double) {
        if (currentRate <= 0) return
        val editor = prefs?.edit() ?: return
        for (id in txIds) {
            if (prefs?.contains(id) == false) editor.putFloat(id, currentRate.toFloat())
        }
        editor.apply()
    }
}

/** Format fiat value in user's preferred currency. Pass explicit rate for Compose-observable usage. */
internal fun formatFiatCurrent(groth: Long, rate: Double? = null): String? {
    val currency = CurrencyManager.getPreferredCurrency()
    val actualRate = rate ?: CurrencyManager.getRate(currency)
    if (actualRate <= 0) return null
    val value = groth.toDouble() / 100_000_000.0 * actualRate
    return if (value > 0) CurrencyManager.formatFiat(value, currency) else null
}

/** Masked placeholder for hidden amounts. */
internal const val MASKED = "••••"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WalletScreen(
    onSend: () -> Unit = {},
    onReceive: () -> Unit = {},
    onTxDetail: (String) -> Unit = {},
    onAssetDetail: (Int) -> Unit = {},
    onTxHistory: () -> Unit = {},
) {
    val context = LocalContext.current
    TxRateStore.init(context)

    // Balance visibility toggle
    var balanceHidden by remember {
        mutableStateOf(SecureStorage.getBoolean(SecureStorage.KEY_BALANCE_HIDDEN, false))
    }
    fun toggleBalanceHidden(hidden: Boolean) {
        balanceHidden = hidden
        SecureStorage.putBoolean(SecureStorage.KEY_BALANCE_HIDDEN, hidden)
    }

    val txJson by WalletEventBus.transactions.collectAsState(initial = "[]")
    val exchangeRates by WalletEventBus.exchangeRates.collectAsState()
    val currency = CurrencyManager.getPreferredCurrency()
    val rate = exchangeRates["beam_$currency"] ?: 0.0
    val syncProgress by WalletEventBus.syncProgress.collectAsState()
    val nodeConnection by WalletEventBus.nodeConnection.collectAsState(
        initial = NodeConnectionEvent(connected = false)
    )

    // Per-asset balance map — initialized from persistent singleton, updated by flow
    val assetBalanceMap = remember { mutableStateMapOf<Int, WalletStatusEvent>().apply {
        putAll(WalletEventBus.assetBalances)
    }}
    LaunchedEffect(Unit) {
        WalletEventBus.walletStatus.collect { event ->
            assetBalanceMap[event.assetId] = event
        }
    }
    // BEAM balance (assetId=0) for the balance card
    val beamStatus = assetBalanceMap[0] ?: WalletEventBus.beamStatus.value

    // Collect asset info — observable Compose state that triggers recomposition
    val assetInfoMap = remember { mutableStateMapOf<Int, AssetInfoEvent>().apply {
        putAll(WalletEventBus.assetInfoCache)
    }}
    LaunchedEffect(Unit) {
        WalletEventBus.assetInfo.collect { event ->
            assetInfoMap[event.id] = event
        }
    }

    // Local ticker resolver using Compose-observable assetInfoMap
    fun localTicker(assetId: Int): String {
        if (assetId == 0) return "BEAM"
        val info = assetInfoMap[assetId] ?: return assetTicker(assetId)
        return info.unitName.ifEmpty { null } ?: info.shortName.ifEmpty { null } ?: info.name.ifEmpty { null } ?: "Asset #$assetId"
    }

    val scope = rememberCoroutineScope()

    // Fetch transactions, addresses, and wallet status on mount (like RN useEffect)
    LaunchedEffect(Unit) {
        try {
            WalletManager.walletInstance?.getWalletStatus()
            WalletManager.walletInstance?.getTransactions()
            WalletManager.walletInstance?.getAddresses(true)
        } catch (_: Exception) {}
    }

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
                    usdRate = TxRateStore.get(obj.optString("txId")),
                    selfTx = obj.optBoolean("selfTx"),
                )
            }.sortedByDescending { it.createTime }.also { txList ->
                // Backfill USD rate for new TXs that don't have a stored rate
                val currentRate = WalletEventBus.exchangeRates.value["beam_usd"] ?: 0.0
                TxRateStore.backfillNewTxs(txList.map { it.txId }, currentRate)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // Build other-asset balances from per-asset status events + asset info
    // Derive directly (no remember) — mutableStateMapOf reads trigger recomposition automatically
    val otherAssets = assetBalanceMap
        .filterKeys { it != 0 }
        .filter { (_, s) -> s.available > 0 || s.maturing > 0 || s.sending > 0 || s.receiving > 0 || s.maxPrivacy > 0 || s.shielded > 0 }
        .map { (assetId, status) ->
            val info = assetInfoMap[assetId]
            AssetBalance(
                assetId = assetId,
                available = status.available,
                maturing = status.maturing,
                sending = status.sending,
                receiving = status.receiving,
                maxPrivacy = status.maxPrivacy,
                shielded = status.shielded,
                unitName = info?.unitName ?: "",
                shortName = info?.shortName ?: "",
                name = info?.name ?: "",
            )
        }
        .sortedBy { it.assetId }

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
        com.privimemobile.protocol.ProtocolStartup.fetchPrices()
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
    val isInOwnMode = SecureStorage.getString("node_mode") == "own"
    val isFallback = isInOwnMode && (WalletManager.walletInstance?.isConnectionTrusted() ?: true) == false && isConnected

    val nodeLabel = when (SecureStorage.getString("node_mode")) {
        "own" -> if (isFallback) stringResource(R.string.wallet_fallback_node) else stringResource(R.string.wallet_own_node)
        "mobile" -> stringResource(R.string.wallet_mobile_node)
        else -> stringResource(R.string.wallet_random_node)
    }

    val isMobileMode = SecureStorage.getString("node_mode") == "mobile"
    val statusText = when {
        isFallback -> "$nodeLabel · ${stringResource(R.string.wallet_block_height, beamStatus.height)}"
        !isConnected -> stringResource(R.string.wallet_connecting_node)
        isMobileMode && isSyncing && syncProgress.total > 0 ->
            stringResource(R.string.wallet_mobile_syncing, syncPercent)
        isSyncing && syncProgress.total > 0 ->
            stringResource(R.string.wallet_sync_progress, syncPercent, syncProgress.done / 1000, syncProgress.total / 1000)
        isSyncing -> stringResource(R.string.wallet_syncing)
        beamStatus.height > 0 -> "$nodeLabel \u00B7 ${stringResource(R.string.wallet_block_height, beamStatus.height)}"
        else -> "$nodeLabel \u00B7 ${stringResource(R.string.wallet_connected)}"
    }

    val statusDotColor = when {
        isFallback -> C.warning
        !isConnected -> C.offline
        isSyncing -> C.warning
        else -> C.accent
    }

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
                Box {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = C.card),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Status + sync progress
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .let { if (isFallback) it.clickable {
                                            NodeReconnect.reconnectOwnNode()
                                            Toast.makeText(context, context.getString(R.string.wallet_reconnecting), Toast.LENGTH_SHORT).show()
                                        } else it },
                                ) {
                                    if (isFallback) {
                                        val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(
                                            initialValue = 0.4f,
                                            targetValue = 1.0f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(800),
                                                repeatMode = RepeatMode.Reverse,
                                            ),
                                            label = "pulse",
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(statusDotColor.copy(alpha = pulse.value)),
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(statusDotColor),
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text(statusText, color = C.textSecondary, fontSize = 12.sp)
                                }

                                // Reserved space for sync progress — always 12dp so
                                // content below never shifts when the bar appears/disappears.
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
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
                                    }
                                }
                            }

                        // Total portfolio fiat value (BEAM + DEX-priced assets)
                        // When sending, available already excludes the spent UTXO but change isn't in available yet —
                        // it's in receiving. Adding receiving back gives the true effective balance during pending TXs.
                        val totalGroth = beamStatus.available + beamStatus.shielded + beamStatus.receiving
                        var totalFiat = if (rate > 0) CurrencyManager.grothToFiat(totalGroth, currency, rate) else 0.0

                        // Add non-BEAM assets: value = amount × beamRatio × beamRate
                        if (rate > 0) {
                            for ((assetId, status) in assetBalanceMap) {
                                if (assetId == 0) continue
                                val beamRatio = exchangeRates["${assetId}_beam"] ?: 0.0
                                if (beamRatio > 0) {
                                    val assetGroth = status.available + status.shielded + status.receiving
                                    if (assetGroth > 0) {
                                        val decimals = exchangeRates["${assetId}_decimals"]?.toInt() ?: 8
                                        val humanBalance = assetGroth.toDouble() / Math.pow(10.0, decimals.toDouble())
                                        totalFiat += humanBalance * beamRatio * rate
                                    }
                                }
                            }
                        }

                        if (!balanceHidden && rate > 0) {
                            PortfolioSnapshotStore.saveSnapshot(totalGroth, exchangeRates)
                            val fiatText = CurrencyManager.formatPortfolioValue(totalFiat, currency)
                            androidx.compose.foundation.layout.BoxWithConstraints(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                val density = androidx.compose.ui.platform.LocalDensity.current
                                val availPx = with(density) { maxWidth.toPx() }
                                val pxPerCharAt36 = with(density) { (18.sp).toPx() }
                                val fitCount36 = (availPx / pxPerCharAt36).toInt()
                                val fontSize = when {
                                    fiatText.length > (fitCount36 * 1.0f).toInt() -> 22.sp
                                    fiatText.length > (fitCount36 * 0.82f).toInt() -> 26.sp
                                    fiatText.length > (fitCount36 * 0.68f).toInt() -> 30.sp
                                    else -> 36.sp
                                }
                                Text(
                                    text = fiatText,
                                    color = C.text,
                                    fontSize = fontSize,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                            }
                            // 24h portfolio change (vs snapshot ~24 hours ago)
                            val daily = PortfolioSnapshotStore.get24hChange(totalFiat, currency)
                            if (daily != null) {
                                val (diff, percent) = daily
                                val isUp = diff >= 0
                                val arrow = if (isUp) "↑" else "↓"
                                val diffStr = CurrencyManager.formatFiat(diff, currency)
                                val sign = if (isUp) "+" else ""
                                val changeColor = if (isUp) C.incoming else C.outgoing
                                Text(
                                    text = "$arrow $sign$diffStr ($sign${String.format("%.2f", percent)}%)",
                                    color = changeColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            } else {
                                // Fallback: CoinGecko 24h market change until 24h snapshot exists
                                val marketChange = exchangeRates["beam_${currency}_change"] ?: 0.0
                                if (marketChange != 0.0) {
                                    val isUp = marketChange >= 0
                                    val arrow = if (isUp) "↑" else "↓"
                                    val sign = if (isUp) "+" else ""
                                    val changeColor = if (isUp) C.incoming else C.outgoing
                                    Text(
                                        text = "$arrow $sign${String.format("%.2f", marketChange)}%",
                                        color = changeColor,
                                    fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        } else if (balanceHidden) {
                            Text(
                                text = MASKED,
                                color = C.text,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        } else {
                            // Rates not yet loaded — show loading placeholder
                            Text(
                                text = stringResource(R.string.general_loading),
                                color = C.textMuted,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }

                        // Pending amounts moved to individual asset cards

                        // Send / Receive buttons — centered, same width, side by side
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
                                Text(stringResource(R.string.general_send), color = C.textDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = onReceive,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                            ) {
                                Text("<", color = C.textDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.general_receive), color = C.textDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                // Eye icon overlay at card's top right corner
                    IconButton(
                        onClick = { toggleBalanceHidden(!balanceHidden) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(20.dp),
                    ) {
                        Icon(
                            imageVector = if (balanceHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (balanceHidden) stringResource(R.string.wallet_show_balance) else stringResource(R.string.wallet_hide_balance),
                            tint = C.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            // BEAM asset row — tappable
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
                        com.privimemobile.ui.components.AssetIcon(assetId = 0, ticker = "BEAM", size = 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("BEAM", color = C.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            // Show sub-text for locked/receiving/maturing amounts
                            if (beamStatus.sending > 0 || (beamStatus.receiving > 0 && beamStatus.sending == 0L) ||
                                beamStatus.maturing > 0 || beamStatus.maxPrivacy > 0 || beamStatus.shielded > 0) {
                                val masked = { amount: Long -> if (balanceHidden) MASKED else Helpers.formatBeam(amount) }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(top = 2.dp),
                                ) {
                                    if (beamStatus.sending > 0) Text("${stringResource(R.string.balance_locked)}: ${masked(beamStatus.sending)}", color = Color(0xFFFF9800), fontSize = 11.sp)
                                    if (beamStatus.receiving > 0 && beamStatus.sending == 0L) Text("${stringResource(R.string.balance_receiving)}: ${masked(beamStatus.receiving)}", color = C.accent, fontSize = 11.sp)
                                    if (beamStatus.maturing > 0) Text("${stringResource(R.string.balance_maturing)}: ${masked(beamStatus.maturing)}", color = Color(0xFFFF9800), fontSize = 11.sp)
                                    if (beamStatus.maxPrivacy > 0) Text("${stringResource(R.string.balance_max_privacy)}: ${masked(beamStatus.maxPrivacy)}", color = Color(0xFFFF9800), fontSize = 11.sp)
                                    if (beamStatus.shielded > 0) Text("${stringResource(R.string.balance_shielded)}: ${masked(beamStatus.shielded)}", color = C.textSecondary, fontSize = 11.sp)
                                }
                            }
                        }
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                if (balanceHidden) MASKED
                                else Helpers.formatBeam(beamStatus.available + beamStatus.shielded),
                                color = C.text,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (!balanceHidden && rate > 0) {
                                val cardFiat = formatFiatCurrent(beamStatus.available + beamStatus.shielded, rate)
                                if (cardFiat != null) {
                                    Text(
                                        "≈ $cardFiat",
                                        color = C.textSecondary,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Other assets
            if (otherAssets.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.wallet_other_assets_header).uppercase(),
                        color = C.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp),
                    )
                }
                items(otherAssets, key = { it.assetId }) { asset ->
                    val assetLabel = localTicker(asset.assetId)
                    val masked = { amount: Long -> if (balanceHidden) MASKED else Helpers.formatBeam(amount) }
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
                            com.privimemobile.ui.components.AssetIcon(assetId = asset.assetId, ticker = assetLabel, size = 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(assetLabel, color = C.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = C.border,
                                    ) {
                                        Text(
                                            stringResource(R.string.wallet_id_badge, asset.assetId),
                                            color = C.textSecondary,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                                if (asset.sending > 0 || (asset.receiving > 0 && asset.sending == 0L) ||
                                    asset.maturing > 0 || asset.maxPrivacy > 0 || asset.shielded > 0) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(top = 2.dp),
                                    ) {
                                        if (asset.sending > 0) Text("${stringResource(R.string.balance_locked)}: ${masked(asset.sending)}", color = Color(0xFFFF9800), fontSize = 11.sp)
                                        if (asset.receiving > 0 && asset.sending == 0L) Text("${stringResource(R.string.balance_receiving)}: ${masked(asset.receiving)}", color = C.accent, fontSize = 11.sp)
                                        if (asset.maturing > 0) Text("${stringResource(R.string.balance_maturing)}: ${masked(asset.maturing)}", color = Color(0xFFFF9800), fontSize = 11.sp)
                                        if (asset.maxPrivacy > 0) Text("${stringResource(R.string.balance_max_privacy)}: ${masked(asset.maxPrivacy)}", color = Color(0xFFFF9800), fontSize = 11.sp)
                                        if (asset.shielded > 0) Text("${stringResource(R.string.balance_shielded)}: ${masked(asset.shielded)}", color = C.textSecondary, fontSize = 11.sp)
                                    }
                                }
                            }
                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.padding(start = 8.dp),
                            ) {
                                Text(
                                    if (balanceHidden) MASKED
                                    else Helpers.formatBeam(asset.available + asset.shielded),
                                    color = C.text,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (!balanceHidden) {
                                    val beamRatio = exchangeRates["${asset.assetId}_beam"] ?: 0.0
                                    if (beamRatio > 0 && rate > 0) {
                                        val decimals = exchangeRates["${asset.assetId}_decimals"]?.toInt() ?: 8
                                        val humanBalance = (asset.available + asset.shielded).toDouble() / Math.pow(10.0, decimals.toDouble())
                                        val fiatValue = humanBalance * beamRatio * rate
                                        Text(
                                            "≈ ${CurrencyManager.formatFiat(fiatValue, currency)}",
                                            color = C.textSecondary,
                                            fontSize = 11.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Transactions header
            item {
                Text(
                    stringResource(R.string.wallet_transactions_title).uppercase(),
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
                            if (isSyncing) stringResource(R.string.wallet_syncing_blockchain) else stringResource(R.string.wallet_no_transactions),
                            color = C.textMuted,
                            fontSize = 14.sp,
                        )
                    }
                }
            } else {
                val previewCount = 10
                items(transactions.take(previewCount), key = { it.txId }) { tx ->
                    TxCard(
                        tx = tx,
                        assetInfoMap = assetInfoMap,
                        balanceHidden = balanceHidden,
                        onClick = { onTxDetail(tx.txId) },
                        exchangeRates = exchangeRates,
                    )
                }
                if (transactions.size > previewCount) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(C.card)
                                .clickable { onTxHistory() }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.wallet_view_all_txs, transactions.size),
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
            }
        }
    }
}

@Composable
internal fun TxCard(
    tx: TxItem,
    assetInfoMap: Map<Int, AssetInfoEvent>,
    balanceHidden: Boolean,
    onClick: () -> Unit,
    exchangeRates: Map<String, Double> = emptyMap(),
) {
    val currency = CurrencyManager.getPreferredCurrency()
    val rate = exchangeRates["beam_$currency"] ?: 0.0
    val isSend = tx.sender
    val isSelfTx = tx.selfTx

    // Status text — online SBBS waiting = "Waiting for receiver" (matches TransactionDetailScreen)
    val isOnlineTx = !tx.isShielded && !tx.isMaxPrivacy && !tx.isOffline && !tx.isPublicOffline
    val isWaitingForReceiver = isSend && isOnlineTx &&
            (tx.status == TxStatus.PENDING || tx.status == TxStatus.IN_PROGRESS)
    val statusText = when {
        isWaitingForReceiver -> stringResource(R.string.tx_status_waiting_receiver).capitalize()
        tx.status == TxStatus.PENDING -> stringResource(R.string.tx_status_pending).capitalize()
        tx.status == TxStatus.IN_PROGRESS -> stringResource(R.string.tx_status_in_progress).capitalize()
        tx.status == TxStatus.CANCELLED -> stringResource(R.string.tx_status_cancelled).capitalize()
        tx.status == TxStatus.COMPLETED -> stringResource(R.string.tx_status_completed).capitalize()
        tx.status == TxStatus.FAILED -> stringResource(R.string.tx_status_failed).capitalize()
        tx.status == TxStatus.REGISTERING -> stringResource(R.string.tx_status_in_progress).capitalize()
        else -> stringResource(R.string.wallet_unknown_label)
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

    // Map known on-chain DApp names to localized labels
    val dappLabel = if (tx.appName?.contains("Assets Swap", ignoreCase = true) == true)
        stringResource(R.string.tx_detail_assets_swap) else tx.appName
    // Peer address / DApp name display
    val peerAddr = when {
        tx.isDapps -> dappLabel ?: stringResource(R.string.wallet_dapp_label)
        tx.peerId.isNotEmpty() -> {
            if (tx.peerId.length > 12)
                "${tx.peerId.take(6)}...${tx.peerId.takeLast(6)}"
            else tx.peerId
        }
        else -> when {
            tx.isMaxPrivacy -> stringResource(R.string.wallet_addr_max_privacy)
            tx.isPublicOffline -> stringResource(R.string.wallet_addr_public_offline)
            tx.isOffline || tx.isShielded -> stringResource(R.string.wallet_addr_offline)
            else -> stringResource(R.string.wallet_addr_regular)
        }
    }

    // Asset ticker — always show (BEAM for assetId=0, resolved name for others)
    val assetLabel = if (tx.assetId != 0) {
        val info = assetInfoMap[tx.assetId]
        info?.unitName?.ifEmpty { null } ?: info?.shortName?.ifEmpty { null } ?: info?.name?.ifEmpty { null } ?: "Asset #${tx.assetId}"
    } else "BEAM"
    val maskedAmount = { amount: Long -> if (balanceHidden) MASKED else Helpers.formatBeam(amount) }

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
                    .background(
                        when {
                            tx.isDapps -> Color(0x269B59B6) // purple tint for contract TXs
                            isSelfTx -> Color(0x26F0A030) // gold tint for self-transfers
                            isSend -> Color(0x26FF6B6B)
                            else -> Color(0x2625D4D0)
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when {
                        tx.isDapps -> "\u2B22" // hexagon for contract
                        isSelfTx -> "\u21C4" // arrows for self-transfer
                        isSend -> ">"
                        else -> "<"
                    },
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
                        when {
                            tx.isDapps -> append(dappLabel ?: stringResource(R.string.wallet_dapp_label))
                            isSelfTx -> append(stringResource(R.string.wallet_self_transfer))
                            isSend -> append(stringResource(R.string.wallet_sent_label))
                            else -> append(stringResource(R.string.wallet_received_label))
                        }
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
                if (tx.isDapps && tx.contractAssets.isNotEmpty()) {
                    // Per-asset breakdown from JNI (like beam-ui)
                    tx.contractAssets.forEach { ca ->
                        val isSpending = ca.sending != 0L
                        val displayAmount = Math.abs(if (isSpending) ca.sending else ca.receiving)
                        val caPrefix = if (isSpending) "-" else "+"
                        val caColor = if (isSpending) C.outgoing else C.incoming
                        val caTicker = if (ca.assetId != 0) {
                            val info = assetInfoMap[ca.assetId]
                            info?.unitName?.ifEmpty { null } ?: info?.shortName?.ifEmpty { null } ?: "Asset #${ca.assetId}"
                        } else "BEAM"
                        if (displayAmount > 0) {
                            Text(
                                text = "$caPrefix${maskedAmount(displayAmount)} $caTicker",
                                color = caColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (!balanceHidden && rate > 0) {
                                val caFiat = if (ca.assetId == 0) formatFiatCurrent(displayAmount, rate)
                                              else CurrencyManager.assetToFiat(ca.assetId, displayAmount, currency, rate)?.let { CurrencyManager.formatFiat(it, currency) }
                                if (caFiat != null) Text("≈ $caFiat", color = C.textSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                } else if (isSelfTx) {
                    Text(
                        text = maskedAmount(tx.amount) + " $assetLabel",
                        color = C.textSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    // Fallback: use sender flag (inverted for contract DApp TXs only, not tx_send tips)
                    val effectiveSend = if (tx.isDapps && tx.amount > 0 && !tx.contractCids.isNullOrEmpty()) !isSend else isSend
                    val amountPrefix = if (effectiveSend) "-" else "+"
                    val amountColor = if (effectiveSend) C.outgoing else C.incoming
                    Text(
                        text = "$amountPrefix${maskedAmount(tx.amount)} $assetLabel",
                        color = amountColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                // Fiat value in preferred currency (skip if contractAssets already showed it)
                if (!balanceHidden && rate > 0 && !(tx.isDapps && tx.contractAssets.isNotEmpty())) {
                    val fiatStr = if (tx.assetId == 0) formatFiatCurrent(tx.amount, rate)
                                   else CurrencyManager.assetToFiat(tx.assetId, tx.amount, currency, rate)?.let { CurrencyManager.formatFiat(it, currency) }
                    if (fiatStr != null) {
                        Text(
                            "≈ $fiatStr",
                            color = C.textSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                    }
                }
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

private fun String.capitalize(): String = replaceFirstChar { it.uppercaseChar() }

private val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
private fun formatDate(timestamp: Long): String {
    return try { dateFormat.format(Date(timestamp * 1000)) } catch (_: Exception) { "" }
}
