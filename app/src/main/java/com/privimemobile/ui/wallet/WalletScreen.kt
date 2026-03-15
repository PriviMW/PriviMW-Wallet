package com.privimemobile.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.ui.theme.C
import com.privimemobile.wallet.WalletEventBus
import com.privimemobile.wallet.WalletStatusEvent

@Composable
fun WalletScreen() {
    val walletStatus by WalletEventBus.walletStatus.collectAsState(
        initial = WalletStatusEvent(0, 0, 0, 0)
    )

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
                    text = formatBeam(walletStatus.available),
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
                                    formatBeam(walletStatus.receiving),
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
                                    formatBeam(walletStatus.sending),
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
                onClick = { /* TODO: navigate to send */ },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.outgoing),
            ) {
                Text("Send", color = C.text, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { /* TODO: navigate to receive */ },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.incoming),
            ) {
                Text("Receive", color = C.text, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(24.dp))

        // Transaction list header
        Text("Transactions", color = C.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        // Placeholder
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("No transactions yet", color = C.textSecondary, fontSize = 14.sp)
        }
    }
}

/** Convert groth (10^-8 BEAM) to display string */
private fun formatBeam(groth: Long): String {
    if (groth == 0L) return "0"
    val beam = groth / 100_000_000.0
    return String.format("%.8f", beam).trimEnd('0').trimEnd('.')
}
