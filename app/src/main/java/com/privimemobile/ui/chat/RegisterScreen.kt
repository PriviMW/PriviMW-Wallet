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
import com.privimemobile.protocol.ProtocolStartup
import com.privimemobile.protocol.SbbsMessaging
import com.privimemobile.protocol.WalletApi
import com.privimemobile.ui.theme.C
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

@Composable
fun RegisterScreen(onRegistered: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var handle by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var registering by remember { mutableStateOf(false) }
    var handleStatus by remember { mutableStateOf("idle") } // idle, checking, available, taken, error
    val registrationFee by ProtocolStartup.registrationFee.collectAsState()

    // TX tracking
    var txSubmitted by remember { mutableStateOf(false) }
    var txStatus by remember { mutableStateOf("pending") } // pending, confirmed, failed
    var submittedHandle by remember { mutableStateOf("") }

    // Monitor TX completion — poll identity state
    if (txSubmitted && txStatus == "pending") {
        LaunchedEffect(Unit) {
            // Poll every 5s to check if registration confirmed on-chain
            while (true) {
                delay(5000)
                ContactResolver.resolveHandleToContact(submittedHandle) { err, result ->
                    if (result != null && !result.walletId.isNullOrEmpty()) {
                        txStatus = "confirmed"
                    }
                }
                if (txStatus == "confirmed") break
            }
        }
    }

    // Auto-navigate on confirmation
    LaunchedEffect(txStatus) {
        if (txStatus == "confirmed") {
            delay(1500) // Show success briefly
            onRegistered()
        }
    }

    // Debounced handle availability check
    LaunchedEffect(handle) {
        handleStatus = "idle"
        if (handle.length < 3 || !handle.matches(Regex("[a-z0-9_]+"))) return@LaunchedEffect
        handleStatus = "checking"
        delay(600)
        ContactResolver.resolveHandleToContact(handle) { err, result ->
            handleStatus = when {
                result != null && !result.walletId.isNullOrEmpty() -> "taken"
                err != null && err.contains("not found", ignoreCase = true) -> "available"
                err != null -> "error"
                else -> "available"
            }
        }
    }

    // TX submitted — show progress screen
    if (txSubmitted) {
        Column(
            modifier = Modifier.fillMaxSize().background(C.bg).padding(32.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            if (txStatus == "confirmed") {
                Text("\u2705", fontSize = 48.sp)
                Spacer(Modifier.height(16.dp))
                Text("Registration Complete!", color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("@$submittedHandle is now yours", color = C.accent, fontSize = 16.sp)
            } else if (txStatus == "failed") {
                Text("\u274C", fontSize = 48.sp)
                Spacer(Modifier.height(16.dp))
                Text("Registration Failed", color = C.error, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(error ?: "Transaction failed", color = C.textSecondary, fontSize = 14.sp)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { txSubmitted = false; registering = false; txStatus = "pending" },
                    colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                ) { Text("Try Again", color = C.textDark, fontWeight = FontWeight.Bold) }
            } else {
                CircularProgressIndicator(color = C.accent, modifier = Modifier.size(48.dp), strokeWidth = 4.dp)
                Spacer(Modifier.height(24.dp))
                Text("Registering Handle", color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("@$submittedHandle", color = C.accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))
                Text(
                    "Your handle is being registered on the Beam blockchain. This usually takes about 1 minute.",
                    color = C.textSecondary,
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 20.sp,
                )
            }
        }
        return
    }

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

        val handleBorderColor = when (handleStatus) {
            "available" -> C.accent
            "taken" -> C.error
            else -> C.border
        }

        OutlinedTextField(
            value = handle,
            onValueChange = { handle = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }; error = null },
            label = { Text("Handle") },
            prefix = { Text("@", color = C.accent) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = handleBorderColor,
                unfocusedBorderColor = handleBorderColor,
                focusedLabelColor = C.accent,
                cursorColor = C.accent,
            ),
        )

        // Handle length hint
        Text(
            "3-32 characters, letters, numbers, underscores",
            color = C.textMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        )

        // Handle availability status
        when (handleStatus) {
            "checking" -> Text(
                "Checking availability...",
                color = C.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
            "available" -> Text(
                "\u2713 @$handle is available",
                color = C.accent,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
            "taken" -> Text(
                "\u2717 @$handle is already taken",
                color = C.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
            "error" -> Text(
                "Could not check availability",
                color = C.warning,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }

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

        Spacer(Modifier.height(16.dp))
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = C.card),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Registration Fee", color = C.textSecondary, fontSize = 14.sp)
                Text(
                    if (registrationFee > 0) "$registrationFee BEAM" else "Loading...",
                    color = C.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error!!, color = C.error, fontSize = 13.sp)
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                when {
                    handle.length < 3 -> error = "Handle must be at least 3 characters"
                    else -> {
                        registering = true
                        error = null
                        submittedHandle = handle
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
                                if (success) {
                                    txSubmitted = true
                                } else {
                                    error = errMsg ?: "Registration failed"
                                    txSubmitted = true
                                    txStatus = "failed"
                                }
                            }
                        }
                    }
                }
            },
            enabled = !registering && handleStatus != "taken",
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
