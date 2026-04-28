package com.privimemobile.ui.wallet

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.ui.theme.C
import com.privimemobile.wallet.WalletEventBus
import com.privimemobile.wallet.WalletManager
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * Full transaction history screen.
 *
 * Shows all transactions in a scrollable list with an export button.
 * Reuses TxCard from WalletScreen for consistent rendering.
 */
@Composable
fun TransactionHistoryScreen(
    onBack: () -> Unit = {},
    onTxDetail: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val exchangeRates by WalletEventBus.exchangeRates.collectAsState()

    // Export confirmation dialog
    var showExportDialog by remember { mutableStateOf(false) }

    // Listen for TX history CSV bundle (all types accumulated) and save as ZIP to Downloads
    LaunchedEffect(Unit) {
        WalletEventBus.exportCsvBundle.collect { csvs ->
            try {
                val date = java.text.SimpleDateFormat("yyyy-MM-dd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                val zipFilename = "privimw-tx-history-$date.zip"
                val csvFilenames = mapOf(
                    "transactions" to "transactions.csv",
                    "atomic_swap" to "atomic_swap_transactions.csv",
                    "assets_swap" to "assets_swap_transactions.csv",
                    "contracts" to "contracts_transactions.csv",
                )
                val baos = java.io.ByteArrayOutputStream()
                java.util.zip.ZipOutputStream(baos).use { zos ->
                    for ((key, csv) in csvs) {
                        val entry = java.util.zip.ZipEntry(csvFilenames[key] ?: "$key.csv")
                        zos.putNextEntry(entry)
                        zos.write(csv.toByteArray())
                        zos.closeEntry()
                    }
                }
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, zipFilename)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/zip")
                    put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(baos.toByteArray()) }
                    Toast.makeText(context, "Saved to Downloads/$zipFilename", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to save file", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Throwable) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Asset info for ticker resolution
    var assetInfoMap by remember { mutableStateOf<Map<Int, com.privimemobile.wallet.AssetInfoEvent>>(emptyMap()) }
    LaunchedEffect(Unit) {
        // Collect asset info events (must start collecting BEFORE requesting)
        launch {
            WalletEventBus.assetInfo.collect { event ->
                assetInfoMap = assetInfoMap + (event.id to event)
            }
        }
        // Refresh wallet state to trigger asset info emissions
        try {
            WalletManager.walletInstance?.getWalletStatus()
            WalletManager.walletInstance?.getTransactions()
        } catch (_: Exception) {}
    }

    // Balance visibility
    var balanceHidden by remember {
        mutableStateOf(com.privimemobile.protocol.SecureStorage.getBoolean(
            com.privimemobile.protocol.SecureStorage.KEY_BALANCE_HIDDEN, false
        ))
    }

    // Transactions (same parsing logic as WalletScreen)
    val txJson by WalletEventBus.transactions.collectAsState()
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
                )
            }.sortedByDescending { it.createTime }.also { txList ->
                val currentRate = WalletEventBus.exchangeRates.value["beam_usd"] ?: 0.0
                TxRateStore.backfillNewTxs(txList.map { it.txId }, currentRate)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // Request asset info for each unique non-zero asset ID so ticker names resolve
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

    Column(modifier = Modifier.fillMaxSize().background(C.bg)) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = C.text)
            }
            Text(
                "Transaction History",
                color = C.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { showExportDialog = true },
            ) {
                Icon(Icons.Default.Download, "Export", tint = C.accent)
            }
        }

        HorizontalDivider(color = C.border, thickness = 1.dp)

        if (transactions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No transactions yet",
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

    // Export confirmation dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            containerColor = C.card,
            title = { Text("Export Transaction History", color = C.text, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "This will export all transaction history as a ZIP file containing:",
                        color = C.textSecondary,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("  • transactions.csv", color = C.textSecondary, fontSize = 13.sp)
                    Text("  • assets_swap_transactions.csv", color = C.textSecondary, fontSize = 13.sp)
                    Text("  • contracts_transactions.csv", color = C.textSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "The ZIP will be saved to your Downloads folder.",
                        color = C.textSecondary,
                        fontSize = 14.sp,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showExportDialog = false
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            Toast.makeText(context, "Export requires Android 10+", Toast.LENGTH_SHORT).show()
                        } else {
                            try {
                                WalletManager.walletInstance?.exportTxHistoryToCsv()
                                    ?: run { Toast.makeText(context, "Wallet not open", Toast.LENGTH_SHORT).show(); return@Button }
                                Toast.makeText(context, "Exporting transaction history...", Toast.LENGTH_SHORT).show()
                            } catch (e: Throwable) {
                                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                ) {
                    Text("Export", color = Color(0xFF032E49), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showExportDialog = false },
                    border = BorderStroke(1.dp, C.border),
                ) {
                    Text("Cancel", color = C.textSecondary)
                }
            },
        )
    }
}
