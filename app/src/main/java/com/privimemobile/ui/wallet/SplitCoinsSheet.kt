package com.privimemobile.ui.wallet

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.privimemobile.protocol.Helpers
import com.privimemobile.protocol.SecureStorage
import com.privimemobile.ui.theme.C
import com.privimemobile.wallet.SplitCoinsAnalyzer
import com.privimemobile.wallet.WalletEventBus
import com.privimemobile.wallet.WalletManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * Bottom sheet for splitting UTXOs — ported from VSnation Beam-Light-Wallet.
 *
 * Two-stage flow:
 *   1. Config: pick split count, preview result
 *   2. Confirm: review TX summary, authenticate (biometric or password), execute tx_split
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitCoinsSheet(
    assetId: Int,
    assetTicker: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isBeam = assetId == 0

    // Trigger UTXO fetch on mount — WalletListener merges partial callbacks
    // so the StateFlow always has the full set (regular + shielded).
    LaunchedEffect(Unit) {
        try { WalletManager.walletInstance?.getAllUtxosStatus() } catch (_: Exception) {}
    }

    // UTXO data
    val utxoJson by WalletEventBus.utxos.collectAsState()
    val utxos = remember(utxoJson) {
        try {
            val arr = JSONArray(utxoJson)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                SplitCoinsAnalyzer.UtxoInfo(
                    amount = obj.optLong("amount"),
                    status = obj.optInt("status"),
                    assetId = obj.optInt("assetId"),
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    val recommendation = remember(utxos, assetId) {
        SplitCoinsAnalyzer.analyze(utxos)[assetId]
    }

    val assetUtxos = remember(utxos, assetId) {
        utxos.filter { it.assetId == assetId && it.status == 1 }.sortedByDescending { it.amount }
    }

    val totalAvailable = assetUtxos.sumOf { it.amount }

    // Split count — default to recommendation's suggestion or 5
    val initialSplits = recommendation?.suggestedSplits ?: 5
    var splitCount by remember { mutableIntStateOf(initialSplits) }
    val splitOptions = listOf(2, 3, 5, 8, 10)

    // Build preview
    val splitResult = remember(totalAvailable, splitCount, isBeam) {
        if (totalAvailable > 0) SplitCoinsAnalyzer.buildSplitCoins(totalAvailable, splitCount, isBeam)
        else null
    }

    // Stage: config → confirm → submitted
    var showConfirm by remember { mutableStateOf(false) }
    var isExecuting by remember { mutableStateOf(false) }
    var txSubmitted by remember { mutableStateOf(false) }
    var submittedTxId by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Password dialog state
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf("") }

    // Auth settings
    val askPasswordOnSend = remember {
        SecureStorage.getBoolean(SecureStorage.KEY_ASK_PASSWORD_ON_SEND, true)
    }
    val biometricsEnabled = remember {
        SecureStorage.getBoolean(SecureStorage.KEY_FINGERPRINT_ENABLED)
    }
    var biometricAvailable by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val bm = BiometricManager.from(context)
        biometricAvailable = bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun executeSplit() {
        val result = splitResult
        if (result == null || result.coins.isEmpty() || result.coins.first() <= 0) {
            errorMsg = "Amount too small to split"
            isExecuting = false
            return
        }

        isExecuting = true
        errorMsg = null

        scope.launch {
            try {
                val params = mutableMapOf<String, Any?>(
                    "coins" to result.coins,
                    "fee" to result.fee,
                )
                if (assetId != 0) params["asset_id"] = assetId

                val apiResult = com.privimemobile.protocol.WalletApi.callAsyncDirect(
                    "tx_split", params
                )

                if (apiResult.containsKey("error")) {
                    val err = apiResult["error"]
                    val errMsg = when (err) {
                        is Map<*, *> -> err["message"]?.toString() ?: "Unknown error"
                        else -> err.toString()
                    }
                    errorMsg = errMsg
                    isExecuting = false
                    return@launch
                }

                submittedTxId = apiResult["txId"]?.toString()
                txSubmitted = true

                // Refresh UTXOs after TX propagates
                delay(3000)
                try { WalletManager.walletInstance?.getAllUtxosStatus() } catch (_: Exception) {}
                delay(1000)
                try { WalletManager.walletInstance?.getAllUtxosStatus() } catch (_: Exception) {}

                isExecuting = false
                onDismiss()
            } catch (e: Exception) {
                errorMsg = e.message ?: "Split failed"
                isExecuting = false
            }
        }
    }

    fun authenticateThenSplit() {
        if (!askPasswordOnSend) {
            executeSplit()
            return
        }

        if (biometricsEnabled && biometricAvailable) {
            val activity = context as? FragmentActivity
            if (activity != null) {
                val executor = ContextCompat.getMainExecutor(context)
                val prompt = BiometricPrompt(activity, executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            executeSplit()
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                                errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                                passwordInput = ""
                                passwordError = ""
                                showPasswordDialog = true
                            } else {
                                errorMsg = errString.toString()
                            }
                        }
                    }
                )

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Confirm Split")
                    .setSubtitle("Authenticate to split $assetTicker coins")
                    .setNegativeButtonText("Use Password")
                    .build()

                prompt.authenticate(promptInfo)
                return
            }
        }

        passwordInput = ""
        passwordError = ""
        showPasswordDialog = true
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { if (!isExecuting) onDismiss() },
        containerColor = C.card,
        sheetState = sheetState,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(C.textMuted.copy(alpha = 0.4f)),
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            if (txSubmitted) {
                // ═══════════ SUBMITTED STAGE ═══════════
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        color = C.incoming.copy(alpha = 0.15f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("✓", color = C.incoming, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Transaction Submitted",
                        color = C.text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Your coins are being split. The balance will update shortly.",
                        color = C.textSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    if (submittedTxId != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            Helpers.truncateKey(submittedTxId!!, 8, 8),
                            color = C.textMuted,
                            fontSize = 12.sp,
                        )
                    }
                }
            } else {
                AnimatedContent(
                    targetState = showConfirm,
                    transitionSpec = {
                        if (targetState) {
                            (slideInVertically { it } + fadeIn()) togetherWith
                                (slideOutVertically { -it } + fadeOut())
                        } else {
                            (slideInVertically { -it } + fadeIn()) togetherWith
                                (slideOutVertically { it } + fadeOut())
                        }
                    },
                    label = "stageTransition",
                ) { isConfirm ->
                    if (!isConfirm) {
                // ═══════════ CONFIG STAGE ═══════════
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Split $assetTicker", color = C.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Create smaller coins to prevent transaction failures", color = C.textSecondary, fontSize = 13.sp)

                // Recommendation banner
                if (recommendation != null && recommendation.severity == SplitCoinsAnalyzer.Severity.CRITICAL) {
                    Spacer(Modifier.height(12.dp))
                    Surface(shape = RoundedCornerShape(10.dp), color = C.dangerBg) {
                        Text(recommendation.message, color = C.error, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(12.dp))
                    }
                } else if (recommendation != null && recommendation.severity == SplitCoinsAnalyzer.Severity.WARNING) {
                    Spacer(Modifier.height(12.dp))
                    Surface(shape = RoundedCornerShape(10.dp), color = C.warning.copy(alpha = 0.1f)) {
                        Text(recommendation.message, color = C.warning, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(12.dp))
                    }
                }

                // Total balance
                if (assetUtxos.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text("Total Available", color = C.textSecondary, fontSize = 13.sp)
                        Column(horizontalAlignment = Alignment.End) {
                            val splitBalText = "${Helpers.formatBeam(totalAvailable)} $assetTicker"
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                val density = LocalDensity.current
                                val availPx = with(density) { maxWidth.toPx() }
                                val pxPerChar = with(density) { (18.sp).toPx() }
                                val fitCount = (availPx / pxPerChar).toInt()
                                val splitBalFontSize = when {
                                    splitBalText.length > (fitCount * 1.0f).toInt() -> 14.sp
                                    splitBalText.length > (fitCount * 0.82f).toInt() -> 15.sp
                                    splitBalText.length > (fitCount * 0.68f).toInt() -> 16.sp
                                    else -> 18.sp
                                }
                                Text(
                                    splitBalText,
                                    color = C.text,
                                    fontSize = splitBalFontSize,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("CURRENT COINS", color = C.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    Spacer(Modifier.height(8.dp))

                    // Bar chart
                    Box(
                        modifier = Modifier.fillMaxWidth().height(28.dp)
                            .clip(RoundedCornerShape(6.dp)).background(C.bg),
                    ) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            assetUtxos.forEachIndexed { index, utxo ->
                                val fraction = utxo.amount.toFloat() / totalAvailable.toFloat()
                                if (fraction > 0.02f) {
                                    val colors = listOf(C.accent, C.warning, C.incoming, Color(0xFF7C6FF2), C.textMuted)
                                    Box(
                                        modifier = Modifier.weight(fraction).fillMaxHeight()
                                            .background(colors[index % colors.size]),
                                    )
                                }
                            }
                        }
                    }

                    // Summary line
                    Spacer(Modifier.height(6.dp))
                    val summary = buildString {
                        append("${assetUtxos.size} coin")
                        if (assetUtxos.size != 1) append("s")
                        if (assetUtxos.size > 1) {
                            val largest = assetUtxos.maxOf { it.amount }
                            if (largest != totalAvailable) {
                                append(" · Largest ${Helpers.formatBeam(largest)} $assetTicker")
                            }
                        }
                    }
                    Text(summary, color = C.textSecondary, fontSize = 13.sp)

                    // Detail rows — max 5
                    Spacer(Modifier.height(8.dp))
                    val showUtxos = if (assetUtxos.size <= 5) assetUtxos else assetUtxos.take(4)
                    val colors = listOf(C.accent, C.warning, C.incoming, Color(0xFF7C6FF2), C.textMuted)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        showUtxos.forEachIndexed { index, utxo ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = C.bg,
                                modifier = Modifier.weight(1f).padding(horizontal = 3.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Box(
                                        modifier = Modifier.size(10.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(colors[index % colors.size]),
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        Helpers.formatBeam(utxo.amount),
                                        color = C.text,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                    )
                                    Text(
                                        assetTicker,
                                        color = C.textSecondary,
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        if (assetUtxos.size > 5) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = C.bg,
                                modifier = Modifier.weight(1f).padding(horizontal = 3.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(vertical = 12.dp, horizontal = 6.dp)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "+${assetUtxos.size - 4} more",
                                        color = C.textSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }

                // Split count selector
                Spacer(Modifier.height(20.dp))
                Text("SPLIT INTO", color = C.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                Spacer(Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    splitOptions.forEach { count ->
                        val isSelected = splitCount == count
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val pressScale by animateFloatAsState(
                            targetValue = if (isPressed) 0.95f else 1f,
                            animationSpec = spring(dampingRatio = 0.7f, stiffness = 1000f),
                            label = "splitCountScale",
                        )
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer { scaleX = pressScale; scaleY = pressScale },
                            onClick = { splitCount = count },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) C.accent else C.bg,
                            border = ButtonDefaults.outlinedButtonBorder(true).copy(
                                brush = androidx.compose.ui.graphics.SolidColor(if (isSelected) C.accent else C.border)
                            ),
                            interactionSource = interactionSource,
                        ) {
                            Text(
                                text = "$count",
                                color = if (isSelected) C.textDark else C.text,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(),
                            )
                        }
                    }
                }

                // Preview
                if (splitResult != null) {
                    Spacer(Modifier.height(20.dp))
                    Text("PREVIEW", color = C.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    Spacer(Modifier.height(8.dp))

                    Surface(shape = RoundedCornerShape(10.dp), color = C.bg) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            val equalAmount = splitResult.coins.first()
                            val equalCount = splitResult.coins.size - 1
                            val remainderAmount = splitResult.coins.last()

                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${equalCount}x equal", color = C.textSecondary, fontSize = 13.sp)
                                Text("${Helpers.formatBeam(equalAmount)} $assetTicker", color = C.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("1x remainder", color = C.textSecondary, fontSize = 13.sp)
                                Text("${Helpers.formatBeam(remainderAmount)} $assetTicker", color = C.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }

                            HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 6.dp))

                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Fee", color = C.textSecondary, fontSize = 13.sp)
                                Text("${Helpers.formatBeam(splitResult.fee)} BEAM", color = C.textSecondary, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // Error
                if (errorMsg != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(errorMsg!!, color = C.error, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }

                // Review button
                Spacer(Modifier.height(20.dp))
                val reviewInteraction = remember { MutableInteractionSource() }
                val reviewPressed by reviewInteraction.collectIsPressedAsState()
                val reviewScale by animateFloatAsState(
                    targetValue = if (reviewPressed) 0.97f else 1f,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 1000f),
                    label = "reviewScale",
                )
                Button(
                    onClick = { showConfirm = true; errorMsg = null },
                    modifier = Modifier.fillMaxWidth().height(50.dp).graphicsLayer { scaleX = reviewScale; scaleY = reviewScale },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                    enabled = splitResult != null && splitResult.coins.first() > 0,
                    interactionSource = reviewInteraction,
                ) {
                    Text("Review Split", color = C.textDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                } // end scrollable Column

            } else {
                // ═══════════ CONFIRM STAGE ═══════════
                Column(modifier = Modifier.fillMaxWidth()) {
                Text("Confirm Split", color = C.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Review the details before confirming", color = C.textSecondary, fontSize = 13.sp)

                // Summary card
                Spacer(Modifier.height(16.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = C.bg) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Before → After
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            // Before
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("BEFORE", color = C.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                                Spacer(Modifier.height(6.dp))
                                Text("${assetUtxos.size}", color = C.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                Text(if (assetUtxos.size == 1) "coin" else "coins", color = C.textSecondary, fontSize = 12.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("${Helpers.formatBeam(totalAvailable)} $assetTicker", color = C.textSecondary, fontSize = 11.sp)
                            }

                            Box(modifier = Modifier.width(1.dp).height(70.dp).background(C.border))

                            // After
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("AFTER", color = C.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                                Spacer(Modifier.height(6.dp))
                                Text("$splitCount", color = C.accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                Text("coins", color = C.accent, fontSize = 12.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("${Helpers.formatBeam(splitResult?.coins?.sum() ?: 0)} $assetTicker", color = C.accent, fontSize = 11.sp)
                            }
                        }

                        HorizontalDivider(color = C.border, modifier = Modifier.padding(vertical = 12.dp))

                        // Fee row
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Transaction fee", color = C.textSecondary, fontSize = 13.sp)
                            Text("${Helpers.formatBeam(splitResult?.fee ?: 0)} BEAM", color = C.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }

                        if (isBeam) {
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total cost", color = C.textSecondary, fontSize = 13.sp)
                                Text(
                                    "${Helpers.formatBeam(totalAvailable)} $assetTicker",
                                    color = C.text,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }

                // Warning
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(8.dp), color = C.warning.copy(alpha = 0.08f)) {
                    Text(
                        "This creates an on-chain transaction. A fee will be deducted from your balance.",
                        color = C.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(12.dp),
                    )
                }

                if (errorMsg != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(errorMsg!!, color = C.error, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }

                // Action buttons
                Spacer(Modifier.height(20.dp))
                val confirmInteraction = remember { MutableInteractionSource() }
                val confirmPressed by confirmInteraction.collectIsPressedAsState()
                val confirmScale by animateFloatAsState(
                    targetValue = if (confirmPressed) 0.97f else 1f,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 1000f),
                    label = "confirmScale",
                )
                Button(
                    onClick = { authenticateThenSplit() },
                    modifier = Modifier.fillMaxWidth().height(50.dp).graphicsLayer { scaleX = confirmScale; scaleY = confirmScale },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                    enabled = !isExecuting,
                    interactionSource = confirmInteraction,
                ) {
                    if (isExecuting) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = C.textDark, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Splitting...", color = C.textDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text("Confirm & Split", color = C.textDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { showConfirm = false; errorMsg = null },
                    enabled = !isExecuting,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = ButtonDefaults.outlinedButtonBorder(true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(C.border)
                    ),
                ) {
                    Text("Back — Edit", color = C.textSecondary, fontSize = 15.sp)
                }
                } // end confirm Column
            }
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
                    Text("Enter Password", color = C.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Text("Confirm your wallet password to split coins", color = C.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(20.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it; passwordError = "" },
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
                        Text(passwordError, color = C.error, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { showPasswordDialog = false },
                            modifier = Modifier.weight(1f).height(48.dp),
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
                                    val wallet = WalletManager.walletInstance
                                    if (wallet != null) {
                                        val valid = wallet.checkWalletPassword(passwordInput.trim())
                                        if (!valid) {
                                            passwordError = "Incorrect password"
                                            return@Button
                                        }
                                    }
                                    showPasswordDialog = false
                                    executeSplit()
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                        ) {
                            Text("Confirm", color = C.textDark, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
}
