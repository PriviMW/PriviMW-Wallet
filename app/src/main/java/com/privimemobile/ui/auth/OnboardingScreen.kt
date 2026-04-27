package com.privimemobile.ui.auth

import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.privimemobile.R
import com.privimemobile.protocol.Config
import com.privimemobile.protocol.SecureStorage
import com.privimemobile.ui.theme.C
import com.privimemobile.wallet.WalletManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class OnboardingStep {
    CHOOSE, CREATE_PASSWORD, SHOW_SEED, VERIFY_SEED, CREATING,
    RESTORE_SEED, RESTORE_PASSWORD
}

/** Pick N unique random indices from 0..max-1 */
private fun pickRandom(count: Int, max: Int): List<Int> {
    val indices = mutableSetOf<Int>()
    while (indices.size < count) {
        indices.add((0 until max).random())
    }
    return indices.sorted()
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

    // Node selection
    var nodeMode by remember { mutableStateOf("random") }
    var customNode by remember { mutableStateOf("") }
    val nodeAddr = if (nodeMode == "own" && customNode.isNotBlank()) customNode.trim() else Config.DEFAULT_NODE

    // Seed verification
    var verifyIndices by remember { mutableStateOf<List<Int>>(emptyList()) }
    var verifyAnswers by remember { mutableStateOf(List(3) { "" }) }
    var verifyErrors by remember { mutableStateOf(List(3) { false }) }

    // Dictionary for restore autocomplete
    var dictionary by remember { mutableStateOf<List<String>>(emptyList()) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeWordIdx by remember { mutableIntStateOf(-1) }

    val scope = rememberCoroutineScope()

    // FLAG_SECURE: block screenshots on seed phrase screen
    val activity = (LocalContext.current as? android.app.Activity)
    DisposableEffect(step) {
        if (step == OnboardingStep.SHOW_SEED) {
            activity?.window?.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // Load dictionary when entering restore flow
    LaunchedEffect(step) {
        if (step == OnboardingStep.RESTORE_SEED && dictionary.isEmpty()) {
            try {
                dictionary = WalletManager.getDictionary()
            } catch (_: Exception) {}
        }
    }

    // Clear sensitive data on dispose
    DisposableEffect(Unit) {
        onDispose {
            password = ""
            confirmPassword = ""
            seedWords = emptyList()
            restoreWords = List(12) { "" }
            verifyAnswers = List(3) { "" }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg)
            .systemBarsPadding()
    ) {
        if (loading) {
            LoadingScreen(
                when (step) {
                    OnboardingStep.CREATING -> "Creating wallet..."
                    OnboardingStep.RESTORE_PASSWORD -> "Restoring wallet..."
                    else -> "Generating seed phrase..."
                }
            )
            return@Column
        }

        AnimatedContent(targetState = step, label = "onboarding") { currentStep ->
            when (currentStep) {
                OnboardingStep.CHOOSE -> ChooseScreen(
                    onCreateNew = { step = OnboardingStep.CREATE_PASSWORD },
                    onRestore = { step = OnboardingStep.RESTORE_SEED },
                )

                OnboardingStep.CREATE_PASSWORD -> CreatePasswordScreen(
                    password = password,
                    confirmPassword = confirmPassword,
                    nodeMode = nodeMode,
                    customNode = customNode,
                    error = error,
                    onPasswordChange = { password = it; error = null },
                    onConfirmChange = { confirmPassword = it; error = null },
                    onNodeModeChange = { nodeMode = it },
                    onCustomNodeChange = { customNode = it },
                    onNext = {
                        when {
                            password.length < 6 -> error = "Password must be at least 6 characters"
                            password != confirmPassword -> error = "Passwords don't match"
                            nodeMode == "own" && !isValidNodeAddress(customNode) -> error = "Invalid node address. Use hostname:port with valid port (1-65535)"
                            else -> {
                                error = null
                                loading = true
                                scope.launch {
                                    try {
                                        seedWords = withContext(Dispatchers.Main) {
                                            WalletManager.generateSeed()
                                        }
                                        step = OnboardingStep.SHOW_SEED
                                    } catch (e: Exception) {
                                        error = e.message ?: "Failed to generate seed"
                                    }
                                    loading = false
                                }
                            }
                        }
                    },
                    onBack = {
                        step = OnboardingStep.CHOOSE
                        password = ""; confirmPassword = ""
                    },
                )

                OnboardingStep.SHOW_SEED -> SeedScreen(
                    words = seedWords,
                    onConfirm = {
                        verifyIndices = pickRandom(3, seedWords.size)
                        verifyAnswers = List(3) { "" }
                        verifyErrors = List(3) { false }
                        step = OnboardingStep.VERIFY_SEED
                    },
                    onBack = {
                        seedWords = emptyList()
                        step = OnboardingStep.CREATE_PASSWORD
                    },
                )

                OnboardingStep.VERIFY_SEED -> VerifySeedScreen(
                    verifyIndices = verifyIndices,
                    verifyAnswers = verifyAnswers,
                    verifyErrors = verifyErrors,
                    onAnswerChange = { idx, text ->
                        verifyAnswers = verifyAnswers.toMutableList().also { it[idx] = text }
                        if (verifyErrors[idx]) {
                            verifyErrors = verifyErrors.toMutableList().also { it[idx] = false }
                        }
                    },
                    error = error,
                    onConfirm = {
                        val errors = verifyIndices.mapIndexed { i, wordIdx ->
                            verifyAnswers[i].trim().lowercase() != seedWords[wordIdx].lowercase()
                        }
                        verifyErrors = errors
                        if (errors.any { it }) {
                            error = "One or more words are incorrect. Please check your seed phrase."
                            return@VerifySeedScreen
                        }
                        error = null
                        loading = true
                        step = OnboardingStep.CREATING
                        scope.launch {
                            val ok = withContext(Dispatchers.Main) {
                                WalletManager.createWallet(
                                    seed = seedWords.joinToString(";"),
                                    password = password,
                                    nodeAddr = nodeAddr,
                                )
                            }
                            loading = false
                            if (ok) {
                                SecureStorage.storeWalletPassword(password)
                                SecureStorage.storeNodeAddress(nodeAddr)
                                SecureStorage.putString("node_mode", nodeMode)
                                if (nodeMode == "own") SecureStorage.putString("custom_node", customNode.trim())
                                SecureStorage.setHasWallet(true)
                                onWalletReady()
                            } else {
                                error = "Failed to create wallet"
                                step = OnboardingStep.SHOW_SEED
                            }
                        }
                    },
                    onShowSeedAgain = { step = OnboardingStep.SHOW_SEED },
                )

                OnboardingStep.CREATING -> LoadingScreen("Creating wallet...")

                OnboardingStep.RESTORE_SEED -> RestoreSeedScreen(
                    words = restoreWords,
                    dictionary = dictionary,
                    suggestions = suggestions,
                    activeWordIdx = activeWordIdx,
                    onWordChange = { idx, word ->
                        restoreWords = restoreWords.toMutableList().also { it[idx] = word.lowercase().trim() }
                        activeWordIdx = idx
                        // Update suggestions
                        val lower = word.lowercase().trim()
                        suggestions = if (lower.isNotEmpty() && dictionary.isNotEmpty()) {
                            dictionary.filter { it.startsWith(lower) }.take(4)
                        } else emptyList()
                    },
                    onSelectSuggestion = { word ->
                        if (activeWordIdx >= 0) {
                            restoreWords = restoreWords.toMutableList().also { it[activeWordIdx] = word }
                            suggestions = emptyList()
                            // Move to next empty word after current index
                            var nextIdx = -1
                            for (i in (activeWordIdx + 1) until 12) {
                                if (restoreWords[i].isEmpty()) { nextIdx = i; break }
                            }
                            // If no empty after current, wrap around and check before
                            if (nextIdx < 0) {
                                for (i in 0 until activeWordIdx) {
                                    if (restoreWords[i].isEmpty()) { nextIdx = i; break }
                                }
                            }
                            activeWordIdx = if (nextIdx >= 0) nextIdx else activeWordIdx + 1
                        }
                    },
                    onFocusWord = { idx ->
                        activeWordIdx = idx
                        val word = restoreWords[idx].lowercase().trim()
                        suggestions = if (word.isNotEmpty() && dictionary.isNotEmpty()) {
                            dictionary.filter { it.startsWith(word) }.take(4)
                        } else emptyList()
                    },
                    error = error,
                    onNext = {
                        // Validate all words
                        if (dictionary.isNotEmpty()) {
                            val invalid = restoreWords.mapIndexedNotNull { i, w ->
                                if (w.isNotEmpty() && w !in dictionary) i else null
                            }
                            if (invalid.isNotEmpty()) {
                                error = "Invalid seed word(s) at position ${invalid.map { "#${it + 1}" }.joinToString(", ")}"
                                return@RestoreSeedScreen
                            }
                        }
                        val filled = restoreWords.count { it.isNotBlank() }
                        if (filled < 12) {
                            error = "Please enter all 12 seed words"
                            return@RestoreSeedScreen
                        }
                        error = null
                        step = OnboardingStep.RESTORE_PASSWORD
                    },
                    onBack = {
                        step = OnboardingStep.CHOOSE
                        restoreWords = List(12) { "" }
                        suggestions = emptyList()
                        activeWordIdx = -1
                    },
                )

                OnboardingStep.RESTORE_PASSWORD -> RestorePasswordScreen(
                    password = password,
                    confirmPassword = confirmPassword,
                    nodeMode = nodeMode,
                    customNode = customNode,
                    error = error,
                    onPasswordChange = { password = it; error = null },
                    onConfirmChange = { confirmPassword = it; error = null },
                    onNodeModeChange = { nodeMode = it },
                    onCustomNodeChange = { customNode = it },
                    onRestore = {
                        when {
                            password.length < 6 -> error = "Password must be at least 6 characters"
                            password != confirmPassword -> error = "Passwords don't match"
                            nodeMode == "own" && !isValidNodeAddress(customNode) -> error = "Invalid node address. Use hostname:port with valid port (1-65535)"
                            else -> {
                                error = null
                                loading = true
                                step = OnboardingStep.CREATING
                                scope.launch {
                                    val ok = withContext(Dispatchers.Main) {
                                        WalletManager.restoreWallet(
                                            seed = restoreWords.joinToString(";"),
                                            password = password,
                                            nodeAddr = nodeAddr,
                                        )
                                    }
                                    if (ok) {
                                        SecureStorage.storeWalletPassword(password)
                                        SecureStorage.storeNodeAddress(nodeAddr)
                                        SecureStorage.putString("node_mode", nodeMode)
                                        if (nodeMode == "own") SecureStorage.putString("custom_node", customNode.trim())
                                        SecureStorage.setHasWallet(true)
                                        // Auto-rescan for random node users to recover UTXOs
                                        // Own node users don't need this — node already tracks UTXOs
                                        if (nodeMode != "own") {
                                            try { WalletManager.walletInstance?.rescan() } catch (_: Exception) {}
                                        }
                                        loading = false
                                        onWalletReady()
                                    } else {
                                        loading = false
                                        error = "Failed to restore wallet. Check your seed phrase."
                                        step = OnboardingStep.RESTORE_SEED
                                    }
                                }
                            }
                        }
                    },
                    onBack = {
                        step = OnboardingStep.RESTORE_SEED
                        password = ""; confirmPassword = ""
                    },
                )
            }
        }
    }
}

// ================================================================
// Sub-screens
// ================================================================

@Composable
private fun ChooseScreen(onCreateNew: () -> Unit, onRestore: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
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
        Text("Wallet on Beam Privacy Blockchain", color = C.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(64.dp))

        Button(
            onClick = onCreateNew,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = C.accent),
        ) {
            Text("Create New Wallet", color = C.textDark, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onRestore,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(8.dp),
            border = ButtonDefaults.outlinedButtonBorder(true).copy(
                brush = androidx.compose.ui.graphics.SolidColor(C.accent)
            ),
        ) {
            Text("Restore from Seed", color = C.accent, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CreatePasswordScreen(
    password: String, confirmPassword: String,
    nodeMode: String, customNode: String, error: String?,
    onPasswordChange: (String) -> Unit, onConfirmChange: (String) -> Unit,
    onNodeModeChange: (String) -> Unit, onCustomNodeChange: (String) -> Unit,
    onNext: () -> Unit, onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
    ) {
        TextButton(onClick = onBack) { Text("Back", color = C.accent) }
        Spacer(Modifier.height(8.dp))
        Text("Create Wallet", color = C.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        Text("Password", color = C.textSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        PasswordField(password, onPasswordChange, "Min 6 characters")
        Spacer(Modifier.height(16.dp))

        Text("Confirm Password", color = C.textSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        PasswordField(confirmPassword, onConfirmChange, "Repeat password")
        Spacer(Modifier.height(16.dp))

        NodeSelector(nodeMode, customNode, onNodeModeChange, onCustomNodeChange)

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = C.error, fontSize = 13.sp)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = C.accent),
        ) {
            Text("Generate Seed Phrase", color = C.textDark, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SeedScreen(words: List<String>, onConfirm: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
    ) {
        Text("Save Your Seed Phrase", color = C.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Write these 12 words down and store them safely. This is the ONLY way to recover your wallet. Never share them with anyone.",
            color = C.error,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(16.dp))

        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = C.card),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                words.chunked(2).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        row.forEachIndexed { colIdx, word ->
                            val idx = words.indexOf(word)
                            Row(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
                                Text("${idx + 1}.", color = C.textSecondary, fontSize = 14.sp, modifier = Modifier.width(24.dp))
                                Text(word, color = C.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = Color(0x1AFFC107),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x4DFFC107)),
        ) {
            Text(
                "This seed phrase can only be used on one device at a time. " +
                "Running the same seed on multiple devices will cause transaction conflicts. " +
                "To monitor your wallet from another device, use your owner key with your own node.",
                color = Color(0xFFFFC107),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(12.dp),
            )
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = C.accent),
        ) {
            Text("I've saved my seed phrase", color = C.textDark, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(8.dp),
            border = ButtonDefaults.outlinedButtonBorder(true).copy(
                brush = androidx.compose.ui.graphics.SolidColor(C.accent)
            ),
        ) {
            Text("Back", color = C.accent, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun VerifySeedScreen(
    verifyIndices: List<Int>,
    verifyAnswers: List<String>,
    verifyErrors: List<Boolean>,
    onAnswerChange: (Int, String) -> Unit,
    error: String?,
    onConfirm: () -> Unit,
    onShowSeedAgain: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
    ) {
        Text("Verify Seed Phrase", color = C.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "To confirm you saved your seed phrase, enter the following words:",
            color = C.textSecondary,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(24.dp))

        verifyIndices.forEachIndexed { i, wordIdx ->
            Text("Word #${wordIdx + 1}", color = C.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = verifyAnswers[i],
                onValueChange = { onAnswerChange(i, it) },
                placeholder = { Text("Enter word #${wordIdx + 1}") },
                singleLine = true,
                isError = verifyErrors[i],
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = C.accent,
                    unfocusedBorderColor = C.border,
                    errorBorderColor = C.error,
                    cursorColor = C.accent,
                ),
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Text,
                ),
            )
            if (verifyErrors[i]) {
                Text("Incorrect word", color = C.error, fontSize = 12.sp)
            }
            Spacer(Modifier.height(16.dp))
        }

        if (error != null) {
            Text(error, color = C.error, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = C.accent),
        ) {
            Text("Confirm & Create Wallet", color = C.textDark, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onShowSeedAgain,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(8.dp),
            border = ButtonDefaults.outlinedButtonBorder(true).copy(
                brush = androidx.compose.ui.graphics.SolidColor(C.accent)
            ),
        ) {
            Text("Show seed phrase again", color = C.accent, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun RestoreSeedScreen(
    words: List<String>,
    dictionary: List<String>,
    suggestions: List<String>,
    activeWordIdx: Int,
    onWordChange: (Int, String) -> Unit,
    onSelectSuggestion: (String) -> Unit,
    onFocusWord: (Int) -> Unit,
    error: String?,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val filledCount = words.count { it.isNotBlank() }
    val allValid = words.all { it.isNotBlank() } &&
        (dictionary.isEmpty() || words.all { it in dictionary })

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
    ) {
        TextButton(onClick = onBack) { Text("Back", color = C.accent) }
        Text("Restore Wallet", color = C.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Enter your 12-word seed phrase. Type each word — suggestions will appear.",
            color = C.textSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))

        // Autocomplete suggestions bar
        if (suggestions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(C.card)
                    .padding(6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                suggestions.forEach { word ->
                    Text(
                        text = word,
                        color = C.textDark,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(C.accent)
                            .clickable { onSelectSuggestion(word) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // FocusRequesters for each word field — auto-focus next on suggestion tap
        val focusRequesters = remember { List(12) { FocusRequester() } }

        // When activeWordIdx changes, request focus on that field
        LaunchedEffect(activeWordIdx) {
            if (activeWordIdx in 0..11) {
                try { focusRequesters[activeWordIdx].requestFocus() } catch (_: Exception) {}
            }
        }

        // 12 word inputs in scrollable grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(12) { idx ->
                val word = words[idx]
                val isValid = word.isNotBlank() && (dictionary.isEmpty() || word in dictionary)
                val isInvalid = word.isNotBlank() && dictionary.isNotEmpty() && word !in dictionary
                val borderColor = when {
                    activeWordIdx == idx -> C.incoming
                    isValid -> C.accent
                    isInvalid -> C.error
                    else -> C.border
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${idx + 1}.",
                        color = C.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.width(22.dp),
                        textAlign = TextAlign.End,
                    )
                    Spacer(Modifier.width(4.dp))
                    OutlinedTextField(
                        value = word,
                        onValueChange = { onWordChange(idx, it) },
                        placeholder = { Text("word", fontSize = 14.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequesters[idx])
                            .onFocusChanged { if (it.isFocused) onFocusWord(idx) },
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = borderColor,
                            unfocusedBorderColor = borderColor,
                            cursorColor = C.accent,
                        ),
                        keyboardOptions = KeyboardOptions(
                            autoCorrectEnabled = false,
                            imeAction = if (idx < 11) ImeAction.Next else ImeAction.Done,
                        ),
                    )
                }
            }
        }

        Text("$filledCount/12 words entered", color = C.textSecondary, fontSize = 12.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

        if (error != null) {
            Spacer(Modifier.height(4.dp))
            Text(error, color = C.error, fontSize = 13.sp)
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onNext,
            enabled = allValid,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = C.accent,
                disabledContainerColor = C.accent.copy(alpha = 0.4f),
            ),
        ) {
            Text("Next", color = C.textDark, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RestorePasswordScreen(
    password: String, confirmPassword: String,
    nodeMode: String, customNode: String, error: String?,
    onPasswordChange: (String) -> Unit, onConfirmChange: (String) -> Unit,
    onNodeModeChange: (String) -> Unit, onCustomNodeChange: (String) -> Unit,
    onRestore: () -> Unit, onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
    ) {
        TextButton(onClick = onBack) { Text("Back", color = C.accent) }
        Spacer(Modifier.height(8.dp))
        Text("Set Password", color = C.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Choose a password to protect your wallet on this device.",
            color = C.textSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))

        Text("Password", color = C.textSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        PasswordField(password, onPasswordChange, "Min 6 characters")
        Spacer(Modifier.height(16.dp))

        Text("Confirm Password", color = C.textSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        PasswordField(confirmPassword, onConfirmChange, "Repeat password")
        Spacer(Modifier.height(16.dp))

        NodeSelector(nodeMode, customNode, onNodeModeChange, onCustomNodeChange)

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = C.error, fontSize = 13.sp)
        }

        Spacer(Modifier.height(24.dp))
        val enabled = password.length >= 6 && password == confirmPassword
        Button(
            onClick = onRestore,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = C.accent,
                disabledContainerColor = C.accent.copy(alpha = 0.4f),
            ),
        ) {
            Text("Restore Wallet", color = C.textDark, fontWeight = FontWeight.Bold)
        }
    }
}

// ================================================================
// Shared components
// ================================================================

@Composable
private fun PasswordField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = C.accent,
            unfocusedBorderColor = C.border,
            cursorColor = C.accent,
        ),
    )
}

@Composable
private fun NodeSelector(
    nodeMode: String, customNode: String,
    onNodeModeChange: (String) -> Unit, onCustomNodeChange: (String) -> Unit,
) {
    Text("Node", color = C.textSecondary, fontSize = 14.sp)
    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, C.border, RoundedCornerShape(8.dp)),
    ) {
        listOf("random" to "Remote Node", "own" to "Own Node").forEach { (mode, label) ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (nodeMode == mode) C.accent else C.card)
                    .clickable { onNodeModeChange(mode) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (nodeMode == mode) C.textDark else C.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
    if (nodeMode == "own") {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = customNode,
            onValueChange = onCustomNodeChange,
            placeholder = { Text("ip:port (e.g. 192.168.1.10:8100)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = C.accent,
                unfocusedBorderColor = C.border,
                cursorColor = C.accent,
            ),
        )
    } else {
        Spacer(Modifier.height(6.dp))
        Text(Config.DEFAULT_NODE, color = C.textSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun LoadingScreen(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = C.accent)
            Spacer(Modifier.height(16.dp))
            Text(message, color = C.textSecondary, fontSize = 16.sp)
        }
    }
}

/** Validate a user-provided node address: must contain exactly one colon with a numeric port (1-65535). */
private fun isValidNodeAddress(addr: String): Boolean {
    if (addr.isBlank()) return false
    val parts = addr.split(":")
    if (parts.size != 2) return false
    val port = parts[1].toIntOrNull()
    return port != null && port in 1..65535
}
