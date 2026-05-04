package com.privimemobile.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Token
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.R
import com.privimemobile.ui.theme.C
import com.privimemobile.wallet.AssetInfoEvent
import com.privimemobile.wallet.CurrencyManager
import com.privimemobile.wallet.WalletEventBus
import com.privimemobile.wallet.WalletManager
import com.privimemobile.wallet.assetTicker
import org.json.JSONArray

/**
 * Full transaction history screen for a specific asset.
 *
 * Shows all transactions filtered to the given asset, with a UTXO button
 * in the top bar that navigates to the UTXO screen pre-filtered to this asset.
 */
@Composable
fun AssetTxHistoryScreen(
    assetId: Int,
    onBack: () -> Unit = {},
    onTxDetail: (String) -> Unit = {},
    onUtxos: (assetId: Int) -> Unit = {},
) {
    val exchangeRates by WalletEventBus.exchangeRates.collectAsState()

    // Asset info for ticker resolution
    var assetInfoMap by remember { mutableStateOf<Map<Int, AssetInfoEvent>>(emptyMap()) }
    LaunchedEffect(Unit) {
        WalletEventBus.assetInfo.collect { event ->
            assetInfoMap = assetInfoMap + (event.id to event)
        }
    }

    // Balance visibility
    var balanceHidden by remember {
        mutableStateOf(com.privimemobile.protocol.SecureStorage.getBoolean(
            com.privimemobile.protocol.SecureStorage.KEY_BALANCE_HIDDEN, false
        ))
    }

    // Transactions filtered by asset (same logic as AssetDetailScreen)
    val txJson by WalletEventBus.transactions.collectAsState()
    val transactions = remember(txJson, assetId) {
        try {
            val arr = JSONArray(txJson)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null

                val contractAssets = obj.optJSONArray("contractAssets")?.let { ca ->
                    (0 until ca.length()).mapNotNull { j ->
                        val ao = ca.optJSONObject(j) ?: return@mapNotNull null
                        Pair(ao.optInt("assetId"), Pair(ao.optLong("sending"), ao.optLong("receiving")))
                    }
                } ?: emptyList()

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
        } catch (_: Exception) {
            emptyList()
        }
    }

    // Request asset info for each unique non-zero asset ID so ticker names resolve in TxCard
    LaunchedEffect(transactions) {
        val assetIds = transactions.flatMap { tx ->
            listOf(tx.assetId) + tx.contractAssets.map { it.assetId }
        }.filter { it != 0 }.distinct()
        for (id in assetIds) {
            try {
                WalletManager.walletInstance?.getAssetInfo(id)
            } catch (_: Exception) {}
        }
    }

    val ticker = assetTicker(assetId)
    val title = "$ticker ${stringResource(R.string.wallet_transactions_title)}"

    Column(modifier = Modifier.fillMaxSize().background(C.bg)) {
        // Top bar: back + title + UTXO button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.general_back), tint = C.text)
            }
            Text(
                title,
                color = C.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onUtxos(assetId) }) {
                Icon(Icons.Default.Token, stringResource(R.string.utxo_title), tint = C.accent)
            }
        }

        HorizontalDivider(color = C.border, thickness = 1.dp)

        if (transactions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.asset_no_transactions),
                    color = C.textMuted,
                    fontSize = 14.sp,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(transactions, key = { it.txId }) { tx ->
                    TxCard(
                        tx = tx,
                        assetInfoMap = assetInfoMap,
                        balanceHidden = balanceHidden,
                        onClick = { onTxDetail(tx.txId) },
                        exchangeRates = exchangeRates,
                    )
                }
            }
        }
    }
}
