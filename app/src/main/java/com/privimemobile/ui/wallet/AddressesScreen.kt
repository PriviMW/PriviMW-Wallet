package com.privimemobile.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.privimemobile.protocol.Helpers
import com.privimemobile.protocol.WalletApi
import com.privimemobile.ui.theme.C
import com.privimemobile.wallet.AddressesEvent
import com.privimemobile.wallet.WalletEventBus
import com.privimemobile.wallet.WalletManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

private data class WalletAddress(
    val walletID: String,
    val label: String,
    val createTime: Long,
    val duration: Long,
    val own: Boolean,
    val identity: String,
)

private enum class AddressFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    EXPIRED("Expired"),
}

@Composable
fun AddressesScreen(onBack: () -> Unit = {}) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var addresses by remember { mutableStateOf<List<WalletAddress>>(emptyList()) }
    var filter by remember { mutableStateOf(AddressFilter.ALL) }
    var editingAddress by remember { mutableStateOf<WalletAddress?>(null) }
    var editLabel by remember { mutableStateOf("") }
    var snackMessage by remember { mutableStateOf<String?>(null) }

    // Request addresses on mount via JNI (triggers onAddresses -> WalletEventBus.addresses)
    LaunchedEffect(Unit) {
        WalletManager.walletInstance?.getAddresses(true)
    }

    // Collect address events
    LaunchedEffect(Unit) {
        WalletEventBus.addresses.collect { event: AddressesEvent ->
            if (!event.own) return@collect
            try {
                val arr = JSONArray(event.json)
                addresses = (0 until arr.length()).mapNotNull { i ->
                    val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                    WalletAddress(
                        walletID = obj.optString("walletID", ""),
                        label = obj.optString("label", ""),
                        createTime = obj.optLong("createTime"),
                        duration = obj.optLong("duration"),
                        own = obj.optBoolean("own", true),
                        identity = obj.optString("identity", ""),
                    )
                }.filter { it.own }
            } catch (_: Exception) {}
        }
    }

    val now = System.currentTimeMillis() / 1000

    val ownAddresses = addresses.filter { it.own }

    val filtered = remember(ownAddresses, filter, now) {
        val list = when (filter) {
            AddressFilter.ALL -> ownAddresses
            AddressFilter.ACTIVE -> ownAddresses.filter { addr ->
                addr.duration == 0L || (addr.createTime + addr.duration) >= now
            }
            AddressFilter.EXPIRED -> ownAddresses.filter { addr ->
                addr.duration > 0L && (addr.createTime + addr.duration) < now
            }
        }
        list.sortedByDescending { it.createTime }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(C.bg),
        ) {
            // Back button
            TextButton(
                onClick = onBack,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            ) {
                Text("< Back", color = C.textSecondary)
            }

            // Filter tabs
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AddressFilter.entries.forEach { f ->
                    val label = if (f == AddressFilter.ALL) "All (${ownAddresses.size})" else f.label
                    Surface(
                        modifier = Modifier.clickable { filter = f },
                        shape = RoundedCornerShape(8.dp),
                        color = if (filter == f) Color(0x1A25D4D0) else Color.Transparent,
                        border = ButtonDefaults.outlinedButtonBorder(true).copy(
                            brush = androidx.compose.ui.graphics.SolidColor(
                                if (filter == f) C.accent else C.border
                            )
                        ),
                    ) {
                        Text(
                            text = label,
                            color = if (filter == f) C.accent else C.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            // Address list
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (filtered.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("No addresses found", color = C.textSecondary, fontSize = 14.sp)
                        }
                    }
                } else {
                    items(filtered, key = { it.walletID }) { addr ->
                        val expired = addr.duration > 0 && (addr.createTime + addr.duration) < now
                        AddressCard(
                            addr = addr,
                            expired = expired,
                            expiryText = getExpiryText(addr, now),
                            onCopy = {
                                clipboard.setText(AnnotatedString(addr.walletID))
                                snackMessage = "Address copied"
                            },
                            onEdit = {
                                editingAddress = addr
                                editLabel = addr.label
                            },
                            onDelete = {
                                try {
                                    WalletManager.walletInstance?.deleteAddress(addr.walletID)
                                    WalletManager.walletInstance?.getAddresses(true)
                                } catch (_: Exception) {}
                            },
                        )
                    }
                }
            }

            // Info note
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color(0x0F25D4D0),
            ) {
                Row {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(48.dp)
                            .background(C.accent),
                    )
                    Text(
                        "SBBS addresses used for online transactions and messaging. " +
                                "For offline/max-privacy receive addresses, use the Receive screen.",
                        color = C.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }

        // Snack message
        snackMessage?.let { msg ->
            LaunchedEffect(msg) {
                kotlinx.coroutines.delay(2000)
                snackMessage = null
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
                    .padding(horizontal = 32.dp),
                shape = RoundedCornerShape(8.dp),
                color = C.card,
                shadowElevation = 4.dp,
            ) {
                Text(
                    msg,
                    color = C.accent,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }

    // Edit label dialog
    if (editingAddress != null) {
        Dialog(onDismissRequest = { editingAddress = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = C.card),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Edit Label",
                        color = C.text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        editingAddress!!.walletID,
                        color = C.textSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = editLabel,
                        onValueChange = { editLabel = it },
                        label = { Text("Address label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = C.accent,
                            unfocusedBorderColor = C.border,
                            focusedLabelColor = C.accent,
                            cursorColor = C.accent,
                        ),
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = { editingAddress = null },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = ButtonDefaults.outlinedButtonBorder(true).copy(
                                brush = androidx.compose.ui.graphics.SolidColor(C.border)
                            ),
                        ) {
                            Text("Cancel", color = C.textSecondary, fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = {
                                val addr = editingAddress ?: return@Button
                                try {
                                    WalletManager.walletInstance?.updateAddress(
                                        addr.walletID, editLabel.trim(), 0
                                    )
                                    WalletManager.walletInstance?.getAddresses(true)
                                } catch (_: Exception) {}
                                editingAddress = null
                                snackMessage = "Label updated"
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                        ) {
                            Text("Save", color = C.textDark, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddressCard(
    addr: WalletAddress,
    expired: Boolean,
    expiryText: String,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = C.card,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (expired) Modifier else Modifier), // opacity handled via alpha
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .then(if (expired) Modifier else Modifier),
        ) {
            // Header: label + expiry
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = addr.label.ifEmpty { "No label" },
                    color = if (expired) C.text.copy(alpha = 0.5f) else C.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                )
                Text(
                    text = expiryText,
                    color = if (expired) C.error else C.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Wallet ID
            Text(
                text = addr.walletID,
                color = if (expired) C.textSecondary.copy(alpha = 0.5f) else C.textSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(8.dp))

            // Meta: creation time + identity
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Created ${formatAddrTimestamp(addr.createTime)}",
                    color = C.textSecondary.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                )
                if (addr.identity.isNotEmpty()) {
                    Text(
                        "ID: ${Helpers.truncateKey(addr.identity, 6, 6)}",
                        color = C.textSecondary.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onCopy,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp),
                    border = ButtonDefaults.outlinedButtonBorder(true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(C.border)
                    ),
                ) {
                    Text("Copy", color = C.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp),
                    border = ButtonDefaults.outlinedButtonBorder(true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(C.border)
                    ),
                ) {
                    Text("Edit", color = C.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp),
                    border = ButtonDefaults.outlinedButtonBorder(true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(Color(0x4DFF6B6B))
                    ),
                ) {
                    Text("Delete", color = C.error, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun getExpiryText(addr: WalletAddress, now: Long): String {
    if (addr.duration == 0L) return "Never expires"
    val expiresAt = addr.createTime + addr.duration
    if (expiresAt < now) return "Expired"
    val hoursLeft = ((expiresAt - now) / 3600).toInt() + 1
    return if (hoursLeft < 24) "${hoursLeft}h left"
    else "${(hoursLeft + 23) / 24}d left"
}

private val addrDateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

private fun formatAddrTimestamp(ts: Long): String {
    return try {
        addrDateFormat.format(Date(ts * 1000))
    } catch (_: Exception) {
        ""
    }
}
