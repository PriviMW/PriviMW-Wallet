package com.privimemobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.protocol.ContactResolver
import com.privimemobile.protocol.SbbsMessaging
import com.privimemobile.protocol.WalletApi
import com.privimemobile.ui.theme.C

@Composable
fun RegisterScreen(onRegistered: () -> Unit, onBack: () -> Unit) {
    var handle by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var registering by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg)
            .padding(24.dp),
    ) {
        TextButton(onClick = onBack) {
            Text("< Back", color = C.textSecondary)
        }
        Spacer(Modifier.height(16.dp))
        Text("Register Handle", color = C.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Choose a unique handle for encrypted messaging on Beam",
            color = C.textSecondary,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = handle,
            onValueChange = { handle = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }; error = null },
            label = { Text("Handle") },
            prefix = { Text("@", color = C.accent) },
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

        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it; error = null },
            label = { Text("Display Name") },
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

        Button(
            onClick = {
                when {
                    handle.length < 3 -> error = "Handle must be at least 3 characters"
                    displayName.isBlank() -> error = "Display name required"
                    else -> {
                        registering = true
                        error = null
                        // Create SBBS address first, then register on-chain
                        WalletApi.call("create_address", mapOf(
                            "type" to "regular",
                            "label" to "PriviMe",
                            "expiration" to "never",
                        )) { addrResult ->
                            val walletId = addrResult["address"] as? String ?: ""
                            if (walletId.isEmpty()) {
                                error = "Failed to create SBBS address"
                                registering = false
                                return@call
                            }
                            ContactResolver.registerHandle(handle, displayName, walletId) { success, errMsg ->
                                registering = false
                                if (success) {
                                    onRegistered()
                                } else {
                                    error = errMsg ?: "Registration failed"
                                }
                            }
                        }
                    }
                }
            },
            enabled = !registering,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = C.accent),
        ) {
            if (registering) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = C.textDark, strokeWidth = 2.dp)
            } else {
                Text("Register", color = C.textDark, fontWeight = FontWeight.Bold)
            }
        }
    }
}
