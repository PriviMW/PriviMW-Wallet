package com.privimemobile.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.protocol.Helpers
import com.privimemobile.ui.theme.C
import com.privimemobile.wallet.WalletEventBus
import com.privimemobile.wallet.WalletStatusEvent
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

/** Simple transaction display model. */
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
)

@Composable
fun WalletScreen(onSend: () -> Unit = {}, onReceive: () -> Unit = {}) {
    val walletStatus by WalletEventBus.walletStatus.collectAsState(
        initial = WalletStatusEvent(0, 0, 0, 0)
    )
    val txJson by WalletEventBus.transactions.collectAsState(initial = "[]")

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
                )
            }.sortedByDescending { it.createTime }
        } catch (_: Exception) {
            emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg)
            .padding(16.dp),
    ) {
        // Balance card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = C.card),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Available Balance", color = C.textSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = Helpers.formatBeam(walletStatus.available),
                    color = C.text,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text("BEAM", color = C.textSecondary, fontSize = 14.sp)

                if (walletStatus.receiving > 0 || walletStatus.sending > 0) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        if (walletStatus.receiving > 0) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Receiving", color = C.textSecondary, fontSize = 11.sp)
                                Text(
                                    Helpers.formatBeam(walletStatus.receiving),
                                    color = C.incoming,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        if (walletStatus.sending > 0) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Sending", color = C.textSecondary, fontSize = 11.sp)
                                Text(
                                    Helpers.formatBeam(walletStatus.sending),
                                    color = C.outgoing,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Send / Receive buttons
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
                Text("Send", color = C.text, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onReceive,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.incoming),
            ) {
                Text("Receive", color = C.text, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Transactions", color = C.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        if (transactions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("No transactions yet", color = C.textSecondary, fontSize = 14.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(transactions, key = { it.txId }) { tx ->
                    TxCard(tx)
                }
            }
        }
    }
}

@Composable
private fun TxCard(tx: TxItem) {
    val isSend = tx.sender
    val statusText = when (tx.status) {
        0 -> "Pending"
        1 -> "In Progress"
        2 -> "Cancelled"
        3 -> "Completed"
        4 -> "Failed"
        5 -> "Registering"
        else -> "Unknown"
    }
    val statusColor = when (tx.status) {
        3 -> C.online          // completed
        4, 2 -> C.error        // failed, cancelled
        else -> C.textSecondary // pending, in progress
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = C.card),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isSend) "Sent" else "Received",
                    color = C.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    statusText,
                    color = statusColor,
                    fontSize = 11.sp,
                )
                if (tx.createTime > 0) {
                    Text(
                        formatDate(tx.createTime),
                        color = C.textSecondary,
                        fontSize = 11.sp,
                    )
                }
            }
            Text(
                text = "${if (isSend) "-" else "+"}${Helpers.formatBeam(tx.amount)} BEAM",
                color = if (isSend) C.outgoing else C.incoming,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
private fun formatDate(timestamp: Long): String {
    return try { dateFormat.format(Date(timestamp * 1000)) } catch (_: Exception) { "" }
}
