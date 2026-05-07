package com.privimemobile.ui.dapps

import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.mw.beam.beamwallet.core.Api
import com.privimemobile.R
import com.privimemobile.dapp.BeamDAppWebView
import com.privimemobile.dapp.DAppResponseRouter
import com.privimemobile.dapp.NativeTxApprovalDialog
import com.privimemobile.protocol.Helpers
import com.privimemobile.ui.theme.C
import com.privimemobile.ui.wallet.*
import com.privimemobile.wallet.WalletEventBus
import com.privimemobile.wallet.WalletManager
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

/**
 * Singleton that keeps the active DApp WebView alive across tab switches.
 * The WebView is detached from its parent when navigating away, and re-attached when returning.
 * Only destroyed when explicitly closed (back button) or a different DApp is launched.
 */
object DAppWebViewHolder {
    var activeWebView: BeamDAppWebView? = null
        private set
    var activeGuid: String = ""
        private set
    var activeName: String = ""
        private set

    fun getOrCreate(ctx: android.content.Context, name: String, path: String, guid: String): BeamDAppWebView {
        val existing = activeWebView
        if (existing != null && activeGuid == guid) {
            return existing
        }
        destroy()
        val wv = BeamDAppWebView(ctx).also {
            it.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            it.launchDApp(name, path, guid)
        }
        activeWebView = wv
        activeGuid = guid
        activeName = name
        return wv
    }

    fun destroy() {
        activeWebView?.let { wv ->
            wv.stopLoading()
            wv.removeJavascriptInterface("BEAM")
            DAppResponseRouter.setActiveWebView(null)
            (wv.parent as? ViewGroup)?.removeView(wv)
        }
        activeWebView = null
        activeGuid = ""
        activeName = ""
        val wallet = WalletManager.walletInstance
        if (wallet != null && Api.isWalletRunning()) {
            try { wallet.launchApp("PriviMe", "") } catch (_: Exception) {}
        }
    }

    fun detach() {
        activeWebView?.let { wv ->
            (wv.parent as? ViewGroup)?.removeView(wv)
        }
    }

    fun hasActive(): Boolean = activeWebView != null
}

/**
 * In-app DApp screen — hosts WebView within Compose navigation (tabs stay visible).
 * Includes collapsible bottom panel with wallet balance + DApp-specific transactions.
 */
@Composable
fun DAppScreen(
    dappName: String,
    dappPath: String,
    dappGuid: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity

    // Pause WebView + restore PriviMe context when navigating away, resume on return
    DisposableEffect(Unit) {
        // Resuming — restore DApp context
        val wallet = WalletManager.walletInstance
        if (wallet != null && Api.isWalletRunning()) {
            try { wallet.launchApp(dappName, "") } catch (_: Exception) {}
        }
        DAppWebViewHolder.activeWebView?.onResume()

        onDispose {
            // Navigating away — pause WebView + restore PriviMe context
            DAppWebViewHolder.activeWebView?.onPause()
            val w = WalletManager.walletInstance
            if (w != null && Api.isWalletRunning()) {
                try { w.launchApp("PriviMe", "") } catch (_: Exception) {}
            }
        }
    }

    // Bottom panel state
    var panelExpanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Balance, 1 = Transactions

    // Wallet data
    val beamStatus by WalletEventBus.beamStatus.collectAsState()
    val txJson by WalletEventBus.transactions.collectAsState(initial = "[]")

    // Collect asset balances
    val assetBalanceMap = remember { mutableStateMapOf<Int, com.privimemobile.wallet.WalletStatusEvent>() }
    LaunchedEffect(Unit) {
        WalletEventBus.assetBalances.forEach { (k, v) -> assetBalanceMap[k] = v }
        WalletEventBus.walletStatus.collect { event -> assetBalanceMap[event.assetId] = event }
    }

    // Asset info for tickers
    val assetInfoMap = remember { mutableStateMapOf<Int, com.privimemobile.wallet.AssetInfoEvent>() }
    LaunchedEffect(Unit) {
        assetInfoMap.putAll(WalletEventBus.assetInfoCache)
        WalletEventBus.assetInfo.collect { assetInfoMap[it.id] = it }
    }

    // Filter TXs for this DApp
    val dappTxs = remember(txJson, dappName) {
        try {
            val arr = JSONArray(txJson)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                if (!obj.optBoolean("isDapps")) return@mapNotNull null
                val appName = obj.optString("appName", "")
                if (appName != dappName) return@mapNotNull null
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
                    isDapps = true,
                    appName = appName.ifEmpty { null },
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
            }.sortedByDescending { it.createTime }
        } catch (_: Exception) { emptyList() }
    }

    DisposableEffect(Unit) {
        NativeTxApprovalDialog.dappActivity = activity
        onDispose {
            NativeTxApprovalDialog.dappActivity = null
            DAppWebViewHolder.detach()
        }
    }

    BackHandler {
        if (panelExpanded) {
            panelExpanded = false
        } else {
            DAppWebViewHolder.destroy()
            onBack()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(C.card)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                DAppWebViewHolder.destroy()
                onBack()
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.dapp_close), tint = C.text)
            }
            Text(
                dappName,
                color = C.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        // WebView fills remaining space (weight pushes bottom panel down)
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { ctx ->
                    val wv = DAppWebViewHolder.getOrCreate(ctx, dappName, dappPath, dappGuid)
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    wv
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Collapsible bottom panel — swipe up to open, down to close
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(C.card)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount < -20) panelExpanded = true   // swipe up → open
                        if (dragAmount > 20) panelExpanded = false   // swipe down → close
                    }
                },
        ) {
            // Toggle bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { panelExpanded = !panelExpanded }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Tab buttons
                val tabs = listOf(stringResource(R.string.dapp_balance_tab), stringResource(R.string.dapp_tx_tab, dappName.uppercase()))
                tabs.forEachIndexed { idx, label ->
                    BoxWithConstraints {
                        val density = LocalDensity.current
                        val availPx = with(density) { maxWidth.toPx() }
                        val pxPerChar = with(density) { (7.sp).toPx() }
                        val fitCount = (availPx / pxPerChar).toInt()
                        val fontSize = when {
                            label.length > (fitCount * 1.0f).toInt() -> 7.sp
                            label.length > (fitCount * 0.82f).toInt() -> 8.sp
                            label.length > (fitCount * 0.68f).toInt() -> 9.sp
                            else -> 10.sp
                        }
                        Text(
                            label,
                            color = if (selectedTab == idx) C.accent else C.textSecondary,
                            fontSize = fontSize,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clickable {
                                    selectedTab = idx
                                    if (!panelExpanded) panelExpanded = true
                                }
                                .padding(end = 16.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    if (panelExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.dapp_toggle_panel),
                    tint = C.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }

            // Expandable content
            AnimatedVisibility(
                visible = panelExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                ) {
                    when (selectedTab) {
                        0 -> BalancePanel(beamStatus, assetBalanceMap, assetInfoMap)
                        1 -> TransactionsPanel(dappTxs, assetInfoMap)
                    }
                }
            }
        }
    }
}

@Composable
private fun BalancePanel(
    beamStatus: com.privimemobile.wallet.WalletStatusEvent,
    assetBalanceMap: Map<Int, com.privimemobile.wallet.WalletStatusEvent>,
    assetInfoMap: Map<Int, com.privimemobile.wallet.AssetInfoEvent>,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        // BEAM balance
        item {
            BalanceRow(assetId = 0, name = "BEAM", available = beamStatus.available + beamStatus.shielded)
        }
        // Other assets
        val otherAssets = assetBalanceMap.filter { it.key != 0 && (it.value.available + it.value.shielded) > 0 }
        items(otherAssets.entries.toList(), key = { it.key }) { (assetId, bal) ->
            val info = assetInfoMap[assetId]
            val name = info?.unitName?.ifEmpty { null } ?: info?.shortName?.ifEmpty { null } ?: stringResource(R.string.dapp_asset_fallback, assetId)
            BalanceRow(assetId = assetId, name = name, available = bal.available + bal.shielded)
        }
        if (otherAssets.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.dapp_no_other_assets),
                    color = C.textMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun BalanceRow(assetId: Int, name: String, available: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.privimemobile.ui.components.AssetIcon(assetId = assetId, ticker = name, size = 28.dp)
        Spacer(Modifier.width(10.dp))
        Text(name, color = C.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(Helpers.formatBeam(available), color = C.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TransactionsPanel(
    txs: List<TxItem>,
    assetInfoMap: Map<Int, com.privimemobile.wallet.AssetInfoEvent>,
) {
    if (txs.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.wallet_no_transactions), color = C.textMuted, fontSize = 13.sp)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        items(txs, key = { it.txId }) { tx ->
            DAppTxRow(tx, assetInfoMap)
        }
    }
}

private val panelDateFmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

@Composable
private fun DAppTxRow(tx: TxItem, assetInfoMap: Map<Int, com.privimemobile.wallet.AssetInfoEvent>) {
    val isFailed = tx.status == TxStatus.FAILED || tx.status == TxStatus.CANCELLED
    val isPending = tx.status == TxStatus.PENDING || tx.status == TxStatus.IN_PROGRESS || tx.status == TxStatus.REGISTERING

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isFailed -> C.error
                        isPending -> C.warning
                        else -> C.online
                    }
                ),
        )
        Spacer(Modifier.width(10.dp))

        // Description + time
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tx.message.ifEmpty { tx.appName ?: stringResource(R.string.dapp_contract_call) },
                color = C.text,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                try { panelDateFmt.format(Date(tx.createTime * 1000)) } catch (_: Exception) { "" },
                color = C.textMuted,
                fontSize = 11.sp,
            )
        }

        // Amount(s)
        Column(horizontalAlignment = Alignment.End) {
            if (tx.contractAssets.isNotEmpty()) {
                tx.contractAssets.forEach { ca ->
                    val isSpending = ca.sending != 0L
                    val displayAmount = Math.abs(if (isSpending) ca.sending else ca.receiving)
                    val caPrefix = if (isSpending) "-" else "+"
                    val caColor = when {
                        isFailed -> C.textSecondary
                        isSpending -> C.outgoing
                        else -> C.incoming
                    }
                    val caTicker = if (ca.assetId != 0) {
                        val info = assetInfoMap[ca.assetId]
                        info?.unitName?.ifEmpty { null } ?: stringResource(R.string.dapp_asset_fallback, ca.assetId)
                    } else "BEAM"
                    if (displayAmount > 0) {
                        Text(
                            "$caPrefix${Helpers.formatBeam(displayAmount)} $caTicker",
                            color = caColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            } else {
                val effectiveOut = if (tx.amount > 0) !tx.sender else tx.sender
                Text(
                    "${if (effectiveOut) "-" else "+"}${Helpers.formatBeam(tx.amount)} BEAM",
                    color = if (isFailed) C.textSecondary else if (effectiveOut) C.outgoing else C.incoming,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
