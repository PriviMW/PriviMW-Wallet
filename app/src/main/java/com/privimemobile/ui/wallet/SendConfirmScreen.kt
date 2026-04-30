package com.privimemobile.ui.wallet

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import com.privimemobile.wallet.CurrencyManager
import com.privimemobile.wallet.WalletEventBus
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.privimemobile.wallet.assetTicker

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
    txType: String = "offline",
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

    val ticker = assetTicker(assetId)
    val exchangeRates by WalletEventBus.exchangeRates.collectAsState()
    val currency = CurrencyManager.getPreferredCurrency()
    val rate = exchangeRates["beam_$currency"] ?: 0.0

    // TX type passed from SendScreen — matches RN: "regular" = SBBS online, "offline" = offline
    val isOffline = txType == "offline"
    val txTypeLabel = if (txType == "regular") "SBBS (Online)" else "Regular (Offline)"

    // Check biometric availability
    LaunchedEffect(Unit) {
        val biometricManager = BiometricManager.from(context)
        biometricAvailable = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    // Use JNI sendTransaction (matches RN — different methods for online vs offline)
    fun executeSend() {
        val wallet = com.privimemobile.wallet.WalletManager.walletInstance
        if (wallet == null) {
            error = "Wallet not connected"
            return
        }
        sending = true
        error = null
        try {
            // Use txType from SendScreen — matches RN: sendTransaction(false) vs sendOfflineTransaction(true)
            wallet.sendTransaction(address, comment, amountGroth, fee, assetId, isOffline)
            onApproved()
        } catch (e: Exception) {
            sending = false
            error = e.message ?: "Send failed"
        }
    }

    // Check if auth is required — matches RN: ONLY askPasswordOnSend triggers auth
    val askPasswordOnSend = remember {
        com.privimemobile.protocol.SecureStorage.getBoolean(
            com.privimemobile.protocol.SecureStorage.KEY_ASK_PASSWORD_ON_SEND, true
        )
    }
    val biometricsEnabled = remember {
        com.privimemobile.protocol.SecureStorage.getBoolean(
            com.privimemobile.protocol.SecureStorage.KEY_FINGERPRINT_ENABLED
        )
    }

    fun authenticateAndSend() {
        // If askPasswordOnSend is OFF, send directly (matches RN lines 63-66)
        if (!askPasswordOnSend) {
            executeSend()
            return
        }

        // askPasswordOnSend is ON — try biometric first if enabled + available
        if (biometricsEnabled && biometricAvailable) {
            val activity = context as? FragmentActivity
            if (activity != null) {
                val executor = ContextCompat.getMainExecutor(context)
                val prompt = BiometricPrompt(activity, executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            executeSend()
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                                errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                                // Fall back to password dialog
                                passwordInput = ""
                                passwordError = ""
                                showPasswordDialog = true
                            } else {
                                error = errString.toString()
                            }
                        }
                        override fun onAuthenticationFailed() {
                            // Single attempt failed — prompt stays open for retry
                        }
                    }
                )

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Confirm Transaction")
                    .setSubtitle("Authenticate to send ${Helpers.formatBeam(amountGroth)} $ticker")
                    .setNegativeButtonText("Use Password")
                    .build()

                prompt.authenticate(promptInfo)
                return
            }
        }

        // Fallback to password dialog
        passwordInput = ""
        passwordError = ""
        showPasswordDialog = true
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
                // Sending to — show front + back so user can verify both ends
                ConfirmRow("Sending to") {
                    val displayAddr = if (address.length > 30) {
                        "${address.take(14)}...${address.takeLast(14)}"
                    } else address
                    Text(
                        displayAddr,
                        color = C.text,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.End,
                        maxLines = 1,
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
                                    if (txType == "regular") C.warning else Color(0xFF25D4D0),
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
                    Column(horizontalAlignment = Alignment.End) {
                        val amtText = "${Helpers.formatBeam(amountGroth)} $ticker"
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            val density = LocalDensity.current
                            val availPx = with(density) { maxWidth.toPx() }
                            val pxPerChar = with(density) { (18.sp).toPx() }
                            val fitCount = (availPx / pxPerChar).toInt()
                            val amtFontSize = when {
                                amtText.length > (fitCount * 1.0f).toInt() -> 12.sp
                                amtText.length > (fitCount * 0.82f).toInt() -> 14.sp
                                amtText.length > (fitCount * 0.68f).toInt() -> 16.sp
                                else -> 18.sp
                            }
                            Text(
                                amtText,
                                color = C.outgoing,
                                fontSize = amtFontSize,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (rate > 0) {
                            val fiat = if (assetId == 0) formatFiatCurrent(amountGroth, rate)
                                       else CurrencyManager.assetToFiat(assetId, amountGroth, currency, rate)?.let { CurrencyManager.formatFiat(it, currency) }
                            if (fiat != null) Text("≈ $fiat", color = C.textSecondary, fontSize = 12.sp)
                        }
                    }
                }
                HorizontalDivider(color = C.border, thickness = 1.dp)

                // Fee
                ConfirmRow("Transaction fee") {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${Helpers.formatBeam(fee)} BEAM",
                            color = C.textSecondary,
                            fontSize = 14.sp,
                        )
                        val feeFiat = if (rate > 0) formatFiatCurrent(fee, rate) else null
                        if (feeFiat != null) Text("≈ $feeFiat", color = C.textSecondary, fontSize = 11.sp)
                    }
                }
                HorizontalDivider(color = C.border, thickness = 1.dp)

                // Total — for BEAM: amount + fee. For other assets: show both separately
                ConfirmRow("Total") {
                    if (assetId == 0) {
                        Column(horizontalAlignment = Alignment.End) {
                            val totalText = "${Helpers.formatBeam(amountGroth + fee)} BEAM"
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                val density = LocalDensity.current
                                val availPx = with(density) { maxWidth.toPx() }
                                val pxPerChar = with(density) { (18.sp).toPx() }
                                val fitCount = (availPx / pxPerChar).toInt()
                                val totalFontSize = when {
                                    totalText.length > (fitCount * 1.0f).toInt() -> 12.sp
                                    totalText.length > (fitCount * 0.82f).toInt() -> 13.sp
                                    totalText.length > (fitCount * 0.68f).toInt() -> 14.sp
                                    else -> 16.sp
                                }
                                Text(
                                    totalText,
                                    color = C.text,
                                    fontSize = totalFontSize,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            val totalFiat = if (rate > 0) formatFiatCurrent(amountGroth + fee, rate) else null
                            if (totalFiat != null) Text("≈ $totalFiat", color = C.textSecondary, fontSize = 12.sp)
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.End) {
                            val assetTotalText = "${Helpers.formatBeam(amountGroth)} $ticker"
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                val density = LocalDensity.current
                                val availPx = with(density) { maxWidth.toPx() }
                                val pxPerChar = with(density) { (18.sp).toPx() }
                                val fitCount = (availPx / pxPerChar).toInt()
                                val assetTotalFontSize = when {
                                    assetTotalText.length > (fitCount * 1.0f).toInt() -> 12.sp
                                    assetTotalText.length > (fitCount * 0.82f).toInt() -> 13.sp
                                    assetTotalText.length > (fitCount * 0.68f).toInt() -> 14.sp
                                    else -> 16.sp
                                }
                                Text(
                                    assetTotalText,
                                    color = C.text,
                                    fontSize = assetTotalFontSize,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (rate > 0) {
                                val assetFiat = CurrencyManager.assetToFiat(assetId, amountGroth, currency, rate)?.let { CurrencyManager.formatFiat(it, currency) }
                                if (assetFiat != null) Text("≈ $assetFiat", color = C.textSecondary, fontSize = 11.sp)
                            }
                            Text(
                                "+ ${Helpers.formatBeam(fee)} BEAM (fee)",
                                color = C.textSecondary,
                                fontSize = 12.sp,
                            )
                            val feeFiat = if (rate > 0) formatFiatCurrent(fee, rate) else null
                            if (feeFiat != null) Text("≈ $feeFiat", color = C.textSecondary, fontSize = 11.sp)
                        }
                    }
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
        if (txType == "regular") {
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
                    authenticateAndSend()
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
                        "SEND",
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
                                    val wallet = com.privimemobile.wallet.WalletManager.walletInstance
                                    if (wallet != null) {
                                        val valid = wallet.checkWalletPassword(passwordInput.trim())
                                        if (!valid) {
                                            passwordError = "Incorrect password"
                                            return@Button
                                        }
                                    }
                                    showPasswordDialog = false
                                    executeSend()
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
