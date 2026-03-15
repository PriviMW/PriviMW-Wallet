package com.privimemobile.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.protocol.Config
import com.privimemobile.ui.theme.C
import com.privimemobile.wallet.WalletManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun unlock() {
        if (password.isBlank()) {
            error = "Enter your password"
            return
        }
        loading = true
        error = null
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                WalletManager.openWallet(password = password, nodeAddr = Config.DEFAULT_NODE)
            }
            loading = false
            if (ok) {
                onUnlocked()
            } else {
                error = "Wrong password"
                password = ""
            }
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
        Text("PriviMW", color = C.accent, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Wallet on the Beam Privacy Blockchain",
            color = C.textSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(48.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { unlock() }),
            singleLine = true,
            enabled = !loading,
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
            onClick = { unlock() },
            enabled = !loading,
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
