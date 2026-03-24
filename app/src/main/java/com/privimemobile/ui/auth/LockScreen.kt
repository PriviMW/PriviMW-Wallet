package com.privimemobile.ui.auth

import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.privimemobile.R
import com.privimemobile.protocol.Config
import com.privimemobile.protocol.SecureStorage
import android.util.Log
import com.privimemobile.ui.theme.C
import com.privimemobile.wallet.WalletManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Check biometric availability
    val biometricEnabled = remember { SecureStorage.getBoolean(SecureStorage.KEY_FINGERPRINT_ENABLED) }
    val biometricAvailable = remember {
        val mgr = BiometricManager.from(context)
        mgr.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }
    val canUseBiometric = biometricEnabled && biometricAvailable

    fun openWallet(pass: String) {
        // Brute-force protection: check lockout
        val lockoutSecs = SecureStorage.getLockoutRemaining()
        if (lockoutSecs > 0) {
            error = "Too many attempts. Try again in ${lockoutSecs}s"
            password = ""
            return
        }

        // Validate password BEFORE calling native Api.openWallet — wrong password crashes the C++ core
        val storedPass = SecureStorage.getWalletPassword()
        if (storedPass != null && pass != storedPass) {
            SecureStorage.recordFailedAttempt()
            val attempts = SecureStorage.getFailedAttempts()
            val newLockout = SecureStorage.getLockoutRemaining()
            error = if (newLockout > 0) "Wrong password. Locked for ${newLockout}s ($attempts attempts)"
                    else "Wrong password ($attempts/10 attempts)"
            password = ""
            return
        }

        // Password correct — clear failed attempts
        SecureStorage.clearFailedAttempts()

        // If wallet is already open (BackgroundService kept it alive after swipe-away),
        // skip Api.openWallet() — calling it again causes "database is locked" native crash.
        if (WalletManager.walletInstance != null) {
            Log.d("LockScreen", "Wallet already open — reusing existing instance")
            onUnlocked()
            return
        }

        loading = true
        error = null
        scope.launch {
            val nodeAddr = SecureStorage.getNodeAddress()
            val ok = withContext(Dispatchers.Main) {
                WalletManager.openWallet(password = pass, nodeAddr = nodeAddr)
            }
            loading = false
            if (ok) {
                onUnlocked()
            } else {
                error = "Failed to open wallet"
                password = ""
            }
        }
    }

    fun showBiometricPrompt() {
        val activity = context as? FragmentActivity ?: return
        val storedPass = SecureStorage.getWalletPassword() ?: return

        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    openWallet(storedPass)
                }
                override fun onAuthenticationFailed() {
                    // Single attempt failed — prompt stays open
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        Toast.makeText(context, errString, Toast.LENGTH_SHORT).show()
                    }
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock PriviMW")
            .setSubtitle("Authenticate to unlock your wallet")
            .setNegativeButtonText("Use Password")
            .build()

        prompt.authenticate(promptInfo)
    }

    // Auto-trigger biometric prompt on mount
    LaunchedEffect(canUseBiometric) {
        if (canUseBiometric) {
            showBiometricPrompt()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg)
            .systemBarsPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.privimw_logo),
                contentDescription = "PriviMW Logo",
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text("PriviMW", color = C.accent, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Wallet on the Beam Privacy Blockchain",
            color = C.textSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(48.dp))

        // Biometric button (if enabled)
        if (canUseBiometric) {
            OutlinedButton(
                onClick = { showBiometricPrompt() },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                border = ButtonDefaults.outlinedButtonBorder(true).copy(
                    brush = androidx.compose.ui.graphics.SolidColor(C.accent)
                ),
            ) {
                Text("Unlock with Biometrics", color = C.accent, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            Text("or enter password", color = C.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { openWallet(password) }),
            singleLine = true,
            enabled = !loading,
            autoFocus = !canUseBiometric,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = C.accent,
                unfocusedBorderColor = C.border,
                focusedLabelColor = C.accent,
                cursorColor = C.accent,
            ),
        )

        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, color = C.error, fontSize = 13.sp)
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { openWallet(password) },
            enabled = !loading && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = C.accent),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = C.textDark,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Unlock", color = C.textDark, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// Extension to support autoFocus on OutlinedTextField
@Composable
private fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable (() -> Unit)?,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    singleLine: Boolean,
    enabled: Boolean,
    autoFocus: Boolean,
    modifier: Modifier,
    colors: TextFieldColors,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            kotlinx.coroutines.delay(300)
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        enabled = enabled,
        modifier = modifier.then(androidx.compose.ui.Modifier.focusRequester(focusRequester)),
        colors = colors,
    )
}
