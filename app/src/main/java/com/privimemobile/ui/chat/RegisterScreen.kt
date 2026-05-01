package com.privimemobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.privimemobile.R
import com.privimemobile.chat.ChatService
import com.privimemobile.protocol.Helpers
import com.privimemobile.protocol.WalletApi
import com.privimemobile.ui.components.AvatarPicker
import com.privimemobile.ui.components.AvatarResult
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(onRegistered: () -> Unit, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var handle by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var avatarResult by remember { mutableStateOf<AvatarResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var registering by remember { mutableStateOf(false) }
    var handleStatus by remember { mutableStateOf("idle") }
    val scope = rememberCoroutineScope()

    // Registration fee from ChatService — trigger refresh on screen load
    val registrationFee by ChatService.identity.registrationFee.collectAsState()
    LaunchedEffect(Unit) {
        if (registrationFee == 0L) ChatService.identity.refreshRegistrationFee()
    }

    // TX tracking — check DB for pending registration TX (survives navigation)
    var txSubmitted by remember { mutableStateOf(false) }
    var txStatus by remember { mutableStateOf("pending") }
    var submittedHandle by remember { mutableStateOf("") }

    // On mount, check if there's already a pending registration TX
    val pendingRegTx by ChatService.db?.pendingTxDao()?.observePending()
        ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val existingPendingReg = pendingRegTx.firstOrNull {
        it.action == com.privimemobile.chat.db.entities.PendingTxEntity.ACTION_REGISTER_HANDLE
    }
    LaunchedEffect(existingPendingReg) {
        if (existingPendingReg != null && !txSubmitted) {
            txSubmitted = true
            submittedHandle = existingPendingReg.targetId
            txStatus = "pending"
        }
        if (existingPendingReg == null && txSubmitted && txStatus == "pending") {
            // TX was processed (confirmed or failed) — check identity
            val state = ChatService.db?.chatStateDao()?.get()
            if (state?.myHandle != null && state.myRegisteredHeight > 0) {
                txStatus = "confirmed"
            }
        }
    }

    // Monitor TX completion — poll identity via ChatService
    if (txSubmitted && txStatus == "pending") {
        LaunchedEffect(Unit) {
            while (true) {
                delay(5000)
                val result = ChatService.contacts.resolveHandle(submittedHandle)
                if (result != null && !result.walletId.isNullOrEmpty()) {
                    txStatus = "confirmed"
                    ChatService.identity.refreshIdentity(forceRefresh = true)
                    break
                }
            }
        }
    }

    LaunchedEffect(txStatus) {
        if (txStatus == "confirmed") {
            // Wait for refreshIdentity to complete (ensure myRegisteredHeight > 0)
            ChatService.identity.refreshIdentity(forceRefresh = true)
            // Poll until identity is fully set (height > 0) before navigating
            var attempts = 0
            while (attempts < 10) {
                val state = ChatService.db?.chatStateDao()?.get()
                if (state?.myRegisteredHeight != null && state.myRegisteredHeight > 0) break
                delay(1000)
                attempts++
            }
            delay(500)
            onRegistered()
        }
    }

    // Debounced handle availability check via ChatService
    LaunchedEffect(handle) {
        handleStatus = "idle"
        if (handle.length < 3 || !handle.matches(Regex("[a-z0-9_]+"))) return@LaunchedEffect
        handleStatus = "checking"
        delay(600)
        ChatService.identity.checkAvailability(handle) { available, err ->
            handleStatus = when {
                available == true -> "available"
                available == false -> "taken"
                else -> "error"
            }
        }
    }

    // Block back navigation while TX is pending
    if (txSubmitted && txStatus == "pending") {
        androidx.activity.compose.BackHandler {}
    }

    // TX submitted — show progress
    if (txSubmitted) {
        Column(
            modifier = Modifier.fillMaxSize().background(C.bg).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (txStatus == "confirmed") {
                Text("\u2705", fontSize = 48.sp)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.register_complete), color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.register_handle_is_yours, submittedHandle), color = C.accent, fontSize = 16.sp)
            } else if (txStatus == "failed") {
                Text("\u274C", fontSize = 48.sp)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.register_failed), color = C.error, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(error ?: stringResource(R.string.register_transaction_failed), color = C.textSecondary, fontSize = 14.sp)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { txSubmitted = false; registering = false; txStatus = "pending" },
                    colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                ) { Text(stringResource(R.string.register_try_again), color = C.textDark, fontWeight = FontWeight.Bold) }
            } else {
                CircularProgressIndicator(color = C.accent, modifier = Modifier.size(48.dp), strokeWidth = 4.dp)
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.register_registering), color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("@$submittedHandle", color = C.accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.register_pending_message),
                    color = C.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp,
                )
            }
        }
        return
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .background(C.bg)
        .imePadding()
        .verticalScroll(rememberScrollState())
        .padding(24.dp)
    ) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.general_back), color = C.textSecondary) }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.register_title), color = C.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.register_description), color = C.textSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(32.dp))

        val handleBorderColor = when (handleStatus) {
            "available" -> C.accent; "taken" -> C.error; else -> C.border
        }
        OutlinedTextField(
            value = handle,
            onValueChange = { val filtered = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' }.take(32); handle = filtered; error = null },
            label = { Text(stringResource(R.string.register_handle_label)) },
            prefix = { Text("@", color = C.accent) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = handleBorderColor, unfocusedBorderColor = handleBorderColor,
                focusedLabelColor = C.accent, cursorColor = C.accent,
            ),
        )
        Text(stringResource(R.string.register_handle_hint), color = C.textMuted, fontSize = 11.sp,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp))

        when (handleStatus) {
            "checking" -> Text(stringResource(R.string.register_checking), color = C.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
            "available" -> Text(stringResource(R.string.register_handle_available, handle), color = C.accent, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
            "taken" -> Text(stringResource(R.string.register_handle_taken, handle), color = C.error, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
            "error" -> Text(stringResource(R.string.register_cannot_check), color = C.warning, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = displayName,
            onValueChange = { if (it.length <= 40) { displayName = it; error = null } },
            label = { Text(stringResource(R.string.register_display_name_label)) },
            supportingText = { Text("${displayName.length}/40", color = C.textMuted, fontSize = 11.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = C.accent, unfocusedBorderColor = C.border,
                focusedLabelColor = C.accent, cursorColor = C.accent,
            ),
        )

        Spacer(Modifier.height(16.dp))
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = C.card), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.register_fee_label), color = C.textSecondary, fontSize = 14.sp)
                Text(
                    if (registrationFee > 0) "${Helpers.grothToBeam(registrationFee)} BEAM" else stringResource(R.string.general_loading),
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
                    handle.length < 3 -> error = context.getString(R.string.register_handle_too_short)
                    else -> {
                        registering = true
                        error = null
                        submittedHandle = handle
                        // Create SBBS address → register via ChatService.identity
                        WalletApi.call("create_address", mapOf(
                            "type" to "regular", "label" to "PriviMe", "expiration" to "never",
                        )) { addrResult ->
                            val walletId = Helpers.normalizeWalletId(addrResult["address"] as? String ?: "")
                            if (walletId == null) {
                                error = context.getString(R.string.register_sbbs_failed)
                                registering = false
                                return@call
                            }
                            ChatService.identity.registerHandle(handle, displayName.ifEmpty { null }, walletId,
                                onResult = { success, errMsg ->
                                    if (success) {
                                        txSubmitted = true
                                    } else {
                                        error = errMsg ?: context.getString(R.string.register_failed_generic)
                                        txSubmitted = true
                                        txStatus = "failed"
                                    }
                                }
                            )
                        }
                    }
                }
            },
            enabled = !registering && handleStatus == "available",
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = C.accent),
        ) {
            if (registering) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = C.textDark, strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.register_button), color = C.textDark, fontWeight = FontWeight.Bold)
            }
        }
    }
}
