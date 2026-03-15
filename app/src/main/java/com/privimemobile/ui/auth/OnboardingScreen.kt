package com.privimemobile.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.protocol.Config
import com.privimemobile.protocol.SecureStorage
import com.privimemobile.ui.theme.C
import com.privimemobile.wallet.WalletManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class OnboardingStep {
    CHOOSE, CREATE_PASSWORD, SHOW_SEED, CREATING, RESTORE_SEED, RESTORE_PASSWORD
}

@Composable
fun OnboardingScreen(onWalletReady: () -> Unit) {
    var step by remember { mutableStateOf(OnboardingStep.CHOOSE) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var seedWords by remember { mutableStateOf<List<String>>(emptyList()) }
    var restoreWords by remember { mutableStateOf(List(12) { "" }) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg)
            .systemBarsPadding()
    ) {
        AnimatedContent(targetState = step, label = "onboarding") { currentStep ->
            when (currentStep) {
                OnboardingStep.CHOOSE -> ChooseScreen(
                    onCreateNew = {
                        seedWords = WalletManager.generateSeed()
                        step = OnboardingStep.CREATE_PASSWORD
                    },
                    onRestore = {
                        step = OnboardingStep.RESTORE_SEED
                    },
                )

                OnboardingStep.CREATE_PASSWORD -> PasswordScreen(
                    title = "Create Password",
                    subtitle = "This password encrypts your wallet on this device",
                    password = password,
                    confirmPassword = confirmPassword,
                    onPasswordChange = { password = it },
                    onConfirmChange = { confirmPassword = it },
                    error = error,
                    onNext = {
                        if (password.length < 6) {
                            error = "Password must be at least 6 characters"
                        } else if (password != confirmPassword) {
                            error = "Passwords don't match"
                        } else {
                            error = null
                            step = OnboardingStep.SHOW_SEED
                        }
                    },
                    onBack = { step = OnboardingStep.CHOOSE },
                )

                OnboardingStep.SHOW_SEED -> SeedScreen(
                    words = seedWords,
                    onConfirm = {
                        loading = true
                        step = OnboardingStep.CREATING
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                WalletManager.createWallet(
                                    seed = seedWords.joinToString(";"),
                                    password = password,
                                    nodeAddr = Config.DEFAULT_NODE,
                                )
                            }
                            loading = false
                            if (ok) {
                                SecureStorage.storeWalletPassword(password)
                                SecureStorage.setHasWallet(true)
                                onWalletReady()
                            } else {
                                error = "Failed to create wallet"
                                step = OnboardingStep.CREATE_PASSWORD
                            }
                        }
                    },
                    onBack = { step = OnboardingStep.CREATE_PASSWORD },
                )

                OnboardingStep.CREATING -> LoadingScreen("Creating wallet...")

                OnboardingStep.RESTORE_SEED -> RestoreSeedScreen(
                    words = restoreWords,
                    onWordChange = { idx, word ->
                        restoreWords = restoreWords.toMutableList().also { it[idx] = word }
                    },
                    error = error,
                    onNext = {
                        val filled = restoreWords.count { it.isNotBlank() }
                        if (filled < 12) {
                            error = "Please enter all 12 seed words"
                        } else {
                            error = null
                            step = OnboardingStep.RESTORE_PASSWORD
                        }
                    },
                    onBack = { step = OnboardingStep.CHOOSE },
                )

                OnboardingStep.RESTORE_PASSWORD -> PasswordScreen(
                    title = "Set Password",
                    subtitle = "Create a password for your restored wallet",
                    password = password,
                    confirmPassword = confirmPassword,
                    onPasswordChange = { password = it },
                    onConfirmChange = { confirmPassword = it },
                    error = error,
                    onNext = {
                        if (password.length < 6) {
                            error = "Password must be at least 6 characters"
                        } else if (password != confirmPassword) {
                            error = "Passwords don't match"
                        } else {
                            error = null
                            loading = true
                            step = OnboardingStep.CREATING
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    WalletManager.restoreWallet(
                                        seed = restoreWords.joinToString(";"),
                                        password = password,
                                        nodeAddr = Config.DEFAULT_NODE,
                                    )
                                }
                                loading = false
                                if (ok) {
                                    SecureStorage.storeWalletPassword(password)
                                    SecureStorage.setHasWallet(true)
                                    onWalletReady()
                                } else {
                                    error = "Failed to restore wallet. Check your seed phrase."
                                    step = OnboardingStep.RESTORE_SEED
                                }
                            }
                        }
                    },
                    onBack = { step = OnboardingStep.RESTORE_SEED },
                )
            }
        }
    }
}

@Composable
private fun ChooseScreen(onCreateNew: () -> Unit, onRestore: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "PriviMW",
            color = C.accent,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Wallet on the Beam Privacy Blockchain",
            color = C.textSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(64.dp))

        Button(
            onClick = onCreateNew,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = C.accent),
        ) {
            Text("Create New Wallet", color = C.textDark, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = onRestore,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            border = ButtonDefaults.outlinedButtonBorder(true).copy(
                brush = androidx.compose.ui.graphics.SolidColor(C.accent)
            ),
        ) {
            Text("Restore Wallet", color = C.accent, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PasswordScreen(
    title: String,
    subtitle: String,
    password: String,
    confirmPassword: String,
    onPasswordChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
    error: String?,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        TextButton(onClick = onBack) {
            Text("< Back", color = C.textSecondary)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, color = C.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, color = C.textSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
            value = confirmPassword,
            onValueChange = onConfirmChange,
            label = { Text("Confirm Password") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
            Text(error, color = C.error, fontSize = 13.sp)
        }

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = C.accent),
        ) {
            Text("Continue", color = C.textDark, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SeedScreen(words: List<String>, onConfirm: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        TextButton(onClick = onBack) {
            Text("< Back", color = C.textSecondary)
        }
        Spacer(Modifier.height(16.dp))
        Text("Your Seed Phrase", color = C.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Write these 12 words down and keep them safe. They are the only way to recover your wallet.",
            color = C.textSecondary,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(24.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(words) { idx, word ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(C.card)
                        .border(1.dp, C.border, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${idx + 1}. $word",
                        color = C.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = C.accent),
        ) {
            Text("I've Written It Down", color = C.textDark, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RestoreSeedScreen(
    words: List<String>,
    onWordChange: (Int, String) -> Unit,
    error: String?,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        TextButton(onClick = onBack) {
            Text("< Back", color = C.textSecondary)
        }
        Spacer(Modifier.height(16.dp))
        Text("Restore Wallet", color = C.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Enter your 12-word seed phrase", color = C.textSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(words) { idx, word ->
                OutlinedTextField(
                    value = word,
                    onValueChange = { onWordChange(idx, it.lowercase().trim()) },
                    label = { Text("${idx + 1}", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = C.accent,
                        unfocusedBorderColor = C.border,
                        cursorColor = C.accent,
                    ),
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                )
            }
        }

        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = C.error, fontSize = 13.sp)
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = C.accent),
        ) {
            Text("Continue", color = C.textDark, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LoadingScreen(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = C.accent)
            Spacer(Modifier.height(16.dp))
            Text(message, color = C.textSecondary, fontSize = 14.sp)
        }
    }
}
