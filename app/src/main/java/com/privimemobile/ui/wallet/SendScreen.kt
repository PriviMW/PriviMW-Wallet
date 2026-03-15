package com.privimemobile.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.protocol.Helpers
import com.privimemobile.protocol.WalletApi
import com.privimemobile.ui.theme.C

@Composable
fun SendScreen(onBack: () -> Unit, onSent: () -> Unit) {
    var address by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }

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
        Text("Send BEAM", color = C.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        // Recipient address
        OutlinedTextField(
            value = address,
            onValueChange = { address = it; error = null },
            label = { Text("Recipient Address") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = C.accent,
                unfocusedBorderColor = C.border,
                focusedLabelColor = C.accent,
                cursorColor = C.accent,
            ),
        )
        Spacer(Modifier.height(16.dp))

        // Amount
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it; error = null },
            label = { Text("Amount (BEAM)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = C.accent,
                unfocusedBorderColor = C.border,
                focusedLabelColor = C.accent,
                cursorColor = C.accent,
            ),
        )
        Spacer(Modifier.height(16.dp))

        // Comment (optional)
        OutlinedTextField(
            value = comment,
            onValueChange = { comment = it },
            label = { Text("Comment (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = C.accent,
                unfocusedBorderColor = C.border,
                focusedLabelColor = C.accent,
                cursorColor = C.accent,
            ),
        )

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error!!, color = C.error, fontSize = 13.sp)
        }

        Spacer(Modifier.height(32.dp))

        // Send button
        Button(
            onClick = {
                when {
                    address.isBlank() -> error = "Enter recipient address"
                    amount.isBlank() -> error = "Enter amount"
                    Helpers.parseBeamToGroth(amount) <= 0 -> error = "Invalid amount"
                    else -> {
                        sending = true
                        error = null
                        val groth = Helpers.parseBeamToGroth(amount)
                        WalletApi.call("tx_send", mapOf(
                            "address" to address.trim(),
                            "value" to groth,
                            "comment" to comment,
                            "asset_id" to 0,
                        )) { result ->
                            sending = false
                            if (result.containsKey("error")) {
                                val err = result["error"]
                                error = when (err) {
                                    is Map<*, *> -> err["message"] as? String ?: "Send failed"
                                    is String -> err
                                    else -> "Send failed"
                                }
                            } else {
                                onSent()
                            }
                        }
                    }
                }
            },
            enabled = !sending,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = C.outgoing),
        ) {
            if (sending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = C.text,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Send", color = C.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
