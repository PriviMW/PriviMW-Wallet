package com.privimemobile.ui.wallet

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.privimemobile.protocol.Helpers
import com.privimemobile.protocol.WalletApi
import com.privimemobile.ui.theme.C

/**
 * Send confirmation screen -- shows TX details and requires user approval.
 * Supports biometric authentication and password fallback.
 *
 * Fully ports SendConfirmScreen.tsx.
 */
@Composable
fun SendConfirmScreen(
    address: String,
    amountGroth: Long,
    fee: Long,
    comment: String = "",
    assetId: Int = 0,
    onApproved: () -> Unit,
    onRejected: () -> Unit,
) {
    val context = LocalContext.current
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var biometricAvailable by remember { mutableStateOf(false) }

    // Password modal state
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf("") }

    val ticker = if (assetId == 0) "BEAM" else "Asset #$assetId"
    val totalGroth = amountGroth + fee

    // Determine TX type from address format (SBBS = short hex)
    val isSbbs = Regex("^[0-9a-fA-F]{62,68}$").matches(address.trim())
    val txTypeLabel = if (isSbbs) "SBBS (Online)" else "Regular (Offline)"

    // Check biometric availability
    LaunchedEffect(Unit) {
        val biometricManager = BiometricManager.from(context)
        biometricAvailable = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun sendTransaction() {
        sending = true
        error = null
        WalletApi.call("tx_send", mapOf(
            "address" to address,
            "value" to amountGroth,
            "fee" to fee,
            "comment" to comment,
            "asset_id" to assetId,
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
                onApproved()
            }
        }
    }

    fun authenticateAndSend() {
        val activity = context as? FragmentActivity
        if (activity == null || !biometricAvailable) {
            sendTransaction()
            return
        }

        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    sendTransaction()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        error = errString.toString()
                    }
                }
                override fun onAuthenticationFailed() {
                    // User can retry
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Confirm Transaction")
            .setSubtitle("Authenticate to send ${Helpers.formatBeam(amountGroth)} $ticker")
            .setNegativeButtonText("Cancel")
            .build()

        prompt.authenticate(promptInfo)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg)
            .verticalScroll(rememberScrollState()),
    ) {
        // Back button
        TextButton(
            onClick = onRejected,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        ) {
            Text("< Back", color = C.textSecondary)
        }

        // Header
        Text(
            "SEND CONFIRMATION",
            color = C.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        )

        // Details card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = C.card),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                // Sending to
                ConfirmRow("Sending to") {
                    Text(
                        address,
                        color = C.text,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                HorizontalDivider(color = C.border, thickness = 1.dp)

                // Transaction type
                ConfirmRow("Transaction type") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (isSbbs) C.warning else Color(0xFF25D4D0),
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                ),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            txTypeLabel,
                            color = C.text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                HorizontalDivider(color = C.border, thickness = 1.dp)

                // Amount
                ConfirmRow("Amount") {
                    Text(
                        "${Helpers.formatBeam(amountGroth)} $ticker",
                        color = C.outgoing,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                HorizontalDivider(color = C.border, thickness = 1.dp)

                // Fee
                ConfirmRow("Transaction fee") {
                    Text(
                        "${Helpers.formatBeam(fee)} BEAM",
                        color = C.textSecondary,
                        fontSize = 14.sp,
                    )
                }
                HorizontalDivider(color = C.border, thickness = 1.dp)

                // Total
                ConfirmRow("Total") {
                    Text(
                        "${Helpers.formatBeam(totalGroth)} BEAM",
                        color = C.text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // Comment
                if (comment.isNotEmpty()) {
                    HorizontalDivider(color = C.border, thickness = 1.dp)
                    ConfirmRow("Comment") {
                        Text(
                            comment,
                            color = C.textSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
            }
        }

        // SBBS warning
        if (isSbbs) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color(0x14F0A030),
            ) {
                Row {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(IntrinsicSize.Min)
                            .background(C.warning),
                    )
                    Text(
                        "SBBS transaction \u2014 recipient must be online within 12 hours or the transaction will expire.",
                        color = C.warning,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        } else {
            // Non-reversible warning
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color(0x14F4CE4A),
            ) {
                Row {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(40.dp)
                            .background(C.warning),
                    )
                    Text(
                        "Transactions cannot be reversed once confirmed.",
                        color = C.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }

        // Error
        if (error != null) {
            Text(
                error!!,
                color = C.error,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
            )
        }

        Spacer(Modifier.weight(1f))

        // Action buttons
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Send button (with biometric if available)
            Button(
                onClick = {
                    if (biometricAvailable) authenticateAndSend()
                    else sendTransaction()
                },
                enabled = !sending,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.outgoing),
            ) {
                if (sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        if (biometricAvailable) "SEND (Biometric)" else "SEND",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = 1.sp,
                    )
                }
            }

            // Back / Edit button
            OutlinedButton(
                onClick = onRejected,
                enabled = !sending,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = ButtonDefaults.outlinedButtonBorder(true).copy(
                    brush = androidx.compose.ui.graphics.SolidColor(C.border)
                ),
            ) {
                Text(
                    "Back \u2014 Edit",
                    color = C.textSecondary,
                    fontSize = 15.sp,
                )
            }
        }
    }

    // Password confirmation dialog
    if (showPasswordDialog) {
        Dialog(onDismissRequest = { showPasswordDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = C.card),
                modifier = Modifier.widthIn(max = 340.dp),
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Enter Password",
                        color = C.text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Confirm your wallet password to send",
                        color = C.textSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(20.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            passwordError = ""
                        },
                        placeholder = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = C.border,
                            unfocusedBorderColor = C.border,
                            cursorColor = C.accent,
                            focusedContainerColor = C.bg,
                            unfocusedContainerColor = C.bg,
                        ),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                    )
                    if (passwordError.isNotEmpty()) {
                        Text(
                            passwordError,
                            color = C.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { showPasswordDialog = false },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = ButtonDefaults.outlinedButtonBorder(true).copy(
                                brush = androidx.compose.ui.graphics.SolidColor(C.border)
                            ),
                        ) {
                            Text("Cancel", color = C.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = {
                                if (passwordInput.isNotBlank()) {
                                    showPasswordDialog = false
                                    sendTransaction()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = C.outgoing),
                        ) {
                            Text("Confirm", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmRow(label: String, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = C.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        Row(
            modifier = Modifier.weight(2f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}
