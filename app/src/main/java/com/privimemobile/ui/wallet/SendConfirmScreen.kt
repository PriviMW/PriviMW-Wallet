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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.privimemobile.protocol.Helpers
import com.privimemobile.protocol.WalletApi
import com.privimemobile.ui.theme.C

/**
 * Send confirmation screen — shows TX details and requires user approval.
 *
 * @param address Recipient address.
 * @param amountGroth Amount in groth (1 BEAM = 100,000,000 groth).
 * @param fee Fee in groth.
 * @param comment Optional transaction comment.
 * @param assetId Asset ID (0 = BEAM).
 * @param onApproved Called after TX is successfully submitted.
 * @param onRejected Called when user rejects the TX.
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

    val ticker = if (assetId == 0) "BEAM" else "Asset #$assetId"
    val totalGroth = amountGroth + fee

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
                    // User can retry, don't show error
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Confirm Transaction",
                color = C.text,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Review the details below before sending",
                color = C.textSecondary,
                fontSize = 14.sp,
            )
        }

        // Amount display
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = C.card),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("SENDING", color = C.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "-${Helpers.formatBeam(amountGroth)} $ticker",
                    color = C.outgoing,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Transaction details card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = C.card),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ConfirmRow("Recipient", address, mono = true)
                HorizontalDivider(color = C.border, thickness = 1.dp)
                ConfirmRow("Amount", "${Helpers.formatBeam(amountGroth)} $ticker")
                HorizontalDivider(color = C.border, thickness = 1.dp)
                ConfirmRow("Fee", "${Helpers.formatBeam(fee)} BEAM")
                HorizontalDivider(color = C.border, thickness = 1.dp)
                ConfirmRow(
                    "Total",
                    "${Helpers.formatBeam(totalGroth)} ${if (assetId == 0) "BEAM" else "$ticker + fee"}",
                    valueColor = C.text,
                    bold = true,
                )
                if (comment.isNotEmpty()) {
                    HorizontalDivider(color = C.border, thickness = 1.dp)
                    ConfirmRow("Comment", comment)
                }
            }
        }

        // Warning note
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
            // Approve with biometric
            if (biometricAvailable) {
                Button(
                    onClick = { authenticateAndSend() },
                    enabled = !sending,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                ) {
                    if (sending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = C.textDark,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            "Approve with Biometrics",
                            color = C.textDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                    }
                }
            }

            // Approve without biometric (or fallback)
            Button(
                onClick = { sendTransaction() },
                enabled = !sending,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (biometricAvailable) C.card else C.accent,
                ),
            ) {
                if (sending && !biometricAvailable) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = if (biometricAvailable) C.accent else C.textDark,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        if (biometricAvailable) "Approve without Biometrics" else "Approve & Send",
                        color = if (biometricAvailable) C.accent else C.textDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }
            }

            // Reject
            OutlinedButton(
                onClick = onRejected,
                enabled = !sending,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = ButtonDefaults.outlinedButtonBorder(true).copy(
                    brush = androidx.compose.ui.graphics.SolidColor(C.error)
                ),
            ) {
                Text(
                    "Reject",
                    color = C.error,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun ConfirmRow(
    label: String,
    value: String,
    mono: Boolean = false,
    valueColor: Color = C.textSecondary,
    bold: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            color = C.textSecondary,
            fontSize = 13.sp,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            text = value,
            color = if (bold) C.text else valueColor,
            fontSize = if (mono) 11.sp else 13.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            textAlign = TextAlign.End,
            maxLines = if (mono) 3 else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
