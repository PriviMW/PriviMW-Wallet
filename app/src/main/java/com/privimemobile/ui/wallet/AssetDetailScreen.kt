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

/**
 * Asset detail screen — shows balance breakdown and filtered transaction list
 * for a specific asset (BEAM or custom token).
 *
 * Ports AssetDetailScreen.tsx to Compose.
 */
@Composable
fun AssetDetailScreen(assetId: Int = 0, onBack: () -> Unit) {
    val walletStatus by WalletEventBus.walletStatus.collectAsState(
        initial = WalletStatusEvent(0, 0, 0, 0)
    )

    val assetName = if (assetId == 0) "BEAM" else "Asset #$assetId"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg)
            .padding(16.dp),
    ) {
        TextButton(onClick = onBack) {
            Text("< Back", color = C.textSecondary)
        }
        Spacer(Modifier.height(8.dp))

        // Asset header
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = C.card),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(assetName, color = C.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(
                    Helpers.formatBeam(walletStatus.available),
                    color = C.text,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text("Available", color = C.textSecondary, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Balance breakdown
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = C.card),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                BalanceRow("Available", walletStatus.available)
                if (walletStatus.receiving > 0) BalanceRow("Receiving", walletStatus.receiving, C.incoming)
                if (walletStatus.sending > 0) BalanceRow("Sending", walletStatus.sending, C.outgoing)
                if (walletStatus.maturing > 0) BalanceRow("Maturing", walletStatus.maturing, C.warning)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("$assetName Transactions", color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("No transactions for this asset", color = C.textSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun BalanceRow(
    label: String,
    groth: Long,
    valueColor: androidx.compose.ui.graphics.Color = C.text,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = C.textSecondary, fontSize = 14.sp)
        Text(
            "${Helpers.formatBeam(groth)} BEAM",
            color = valueColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
