package com.privimemobile.ui.chat.chats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.R
import com.privimemobile.chat.ChatService
import com.privimemobile.chat.db.entities.ChatStateEntity
import com.privimemobile.protocol.Helpers
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.delay

@Composable
internal fun ReRegisterLanding(chatState: ChatStateEntity) {
    val context = LocalContext.current
    val currentDisplayName = chatState.myDisplayName ?: ""
    var displayName by remember { mutableStateOf(currentDisplayName) }
    var useExistingName by remember { mutableStateOf(currentDisplayName.isNotEmpty()) }
    var updating by remember { mutableStateOf(false) }
    var txStatus by remember { mutableStateOf("pending") } // pending, confirmed, failed
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var newAddress by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // Check for existing pending update_profile TX (survives navigation)
    val pendingTxs by ChatService.db?.pendingTxDao()?.observePending()
        ?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    val existingPendingUpdate = pendingTxs.firstOrNull {
        it.action == com.privimemobile.chat.db.entities.PendingTxEntity.ACTION_UPDATE_PROFILE
    }
    LaunchedEffect(existingPendingUpdate) {
        if (existingPendingUpdate != null && !updating) {
            updating = true
            txStatus = "pending"
        }
        if (existingPendingUpdate == null && updating && txStatus == "pending") {
            // TX processed — check if SBBS update flag is clear
            if (!ChatService.identity.sbbsNeedsUpdate.value) {
                txStatus = "confirmed"
                ChatService.identity.refreshIdentity(forceRefresh = true)
            }
        }
    }

    // Block back navigation while TX is pending
    if (updating && txStatus == "pending") {
        androidx.activity.compose.BackHandler {}
    }

    // Poll to detect TX confirmation — check if SBBS address is now ours
    if (updating && txStatus == "pending" && newAddress.isNotEmpty()) {
        LaunchedEffect(newAddress) {
            while (true) {
                delay(5000)
                try {
                    val result = com.privimemobile.protocol.WalletApi.callAsync(
                        "validate_address", mapOf("address" to newAddress)
                    )
                    // Check contract for updated wallet_id
                    val contractResult = com.privimemobile.protocol.ShaderInvoker.invokeAsync(
                        "user", "my_handle"
                    )
                    val onChainWalletId = Helpers.normalizeWalletId(
                        contractResult["wallet_id"] as? String ?: ""
                    )
                    if (onChainWalletId == newAddress) {
                        txStatus = "confirmed"
                        ChatService.identity.clearSbbsNeedsUpdate()
                        ChatService.identity.refreshIdentity(forceRefresh = true)
                        break
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // Auto-dismiss after confirmation
    LaunchedEffect(txStatus) {
        if (txStatus == "confirmed") {
            delay(1500)
        }
    }

    if (updating) {
        Column(
            modifier = Modifier.fillMaxSize().background(C.bg).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (txStatus == "confirmed") {
                Text("✅", fontSize = 48.sp)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.chats_address_updated), color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.chats_handle_ready, chatState.myHandle ?: ""), color = C.accent, fontSize = 16.sp)
            } else if (txStatus == "failed") {
                Text("❌", fontSize = 48.sp)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.chats_update_failed), color = C.error, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(errorMsg ?: stringResource(R.string.register_transaction_failed), color = C.textSecondary, fontSize = 14.sp)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { updating = false; txStatus = "pending"; errorMsg = null },
                    colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                ) { Text(stringResource(R.string.register_try_again), color = C.textDark, fontWeight = FontWeight.Bold) }
            } else {
                CircularProgressIndicator(color = C.accent, modifier = Modifier.size(48.dp), strokeWidth = 4.dp)
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.chats_updating_address), color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("@${chatState.myHandle}", color = C.accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.chats_updating_address_msg),
                    color = C.textSecondary, fontSize = 14.sp,
                    textAlign = TextAlign.Center, lineHeight = 20.sp,
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(C.bg)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.chats_welcome_back), color = C.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("@${chatState.myHandle}", color = C.accent, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.chats_restored_notice),
            color = C.textSecondary, fontSize = 14.sp,
            textAlign = TextAlign.Center, lineHeight = 20.sp,
        )

        // Display name option
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.register_display_name_label), color = C.textSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        if (currentDisplayName.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = useExistingName,
                    onClick = { useExistingName = true; displayName = currentDisplayName },
                    colors = RadioButtonDefaults.colors(selectedColor = C.accent, unselectedColor = C.textSecondary),
                )
                Text(stringResource(R.string.chats_reregister_keep_name, currentDisplayName), color = C.text, fontSize = 14.sp,
                    modifier = Modifier.clickable { useExistingName = true; displayName = currentDisplayName })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = !useExistingName,
                    onClick = { useExistingName = false; displayName = "" },
                    colors = RadioButtonDefaults.colors(selectedColor = C.accent, unselectedColor = C.textSecondary),
                )
                Text(stringResource(R.string.chats_reregister_change_name), color = C.text, fontSize = 14.sp,
                    modifier = Modifier.clickable { useExistingName = false; displayName = "" })
            }
        }
        if (!useExistingName || currentDisplayName.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = displayName,
                onValueChange = { if (it.length <= 32) displayName = it },
                placeholder = { Text(stringResource(R.string.chats_reregister_name_placeholder), color = C.textSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = C.text, unfocusedTextColor = C.text,
                    cursorColor = C.accent, focusedBorderColor = C.accent, unfocusedBorderColor = C.border,
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = Color(0x1AFFC107),
            border = BorderStroke(1.dp, Color(0x4DFFC107)),
        ) {
            Text(
                stringResource(R.string.chats_reregister_info),
                color = Color(0xFFFFC107), fontSize = 12.sp,
                textAlign = TextAlign.Center, lineHeight = 17.sp,
                modifier = Modifier.padding(12.dp),
            )
        }

        val beamStatus by com.privimemobile.wallet.WalletEventBus.beamStatus.collectAsState()
        val hasBalance = beamStatus.available > 0
        if (!hasBalance) {
            Spacer(Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = Color(0x1AFF6B6B),
                border = BorderStroke(1.dp, Color(0x4DFF6B6B)),
            ) {
                Text(
                    stringResource(R.string.chats_reregister_no_beam),
                    color = C.error, fontSize = 12.sp,
                    textAlign = TextAlign.Center, lineHeight = 17.sp,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                updating = true
                txStatus = "pending"
                val newDn = if (useExistingName) currentDisplayName else displayName.trim()
                com.privimemobile.protocol.WalletApi.call("create_address", mapOf(
                    "type" to "regular",
                    "expiration" to "never",
                    "comment" to context.getString(R.string.chats_messaging_address_comment),
                )) { result ->
                    val addr = result["address"] as? String
                    if (addr != null) {
                        val normalized = Helpers.normalizeWalletId(addr) ?: addr
                        newAddress = normalized
                        ChatService.identity.updateMessagingAddress(normalized, newDn) { success, error ->
                            if (!success) {
                                errorMsg = error
                                txStatus = "failed"
                            }
                        }
                    } else {
                        errorMsg = context.getString(R.string.register_sbbs_failed)
                        txStatus = "failed"
                    }
                }
            },
            enabled = !updating && hasBalance,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = C.accent,
                disabledContainerColor = C.accent.copy(alpha = 0.3f),
            ),
        ) {
            Text(stringResource(R.string.chats_reregister_button), color = C.textDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}