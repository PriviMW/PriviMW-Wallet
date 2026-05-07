package com.privimemobile.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.privimemobile.R
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

private enum class AddressFilter(@androidx.annotation.StringRes val labelResId: Int) {
    ALL(R.string.general_all),
    FAVOURITES(R.string.addresses_filter_favourites),
    ACTIVE(R.string.addresses_filter_active),
    EXPIRED(R.string.addresses_filter_expired),
}

private fun getFavourites(context: android.content.Context): Set<String> {
    return context.getSharedPreferences("address_prefs", android.content.Context.MODE_PRIVATE)
        .getStringSet("favourites", emptySet()) ?: emptySet()
}

private fun toggleFavourite(context: android.content.Context, walletId: String): Boolean {
    val prefs = context.getSharedPreferences("address_prefs", android.content.Context.MODE_PRIVATE)
    val favs = prefs.getStringSet("favourites", emptySet())?.toMutableSet() ?: mutableSetOf()
    val added = if (walletId in favs) { favs.remove(walletId); false } else { favs.add(walletId); true }
    prefs.edit().putStringSet("favourites", favs).apply()
    return added
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
    val context = LocalContext.current
    var favourites by remember { mutableStateOf(getFavourites(context)) }

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

    val filtered = remember(ownAddresses, filter, now, favourites) {
        val list = when (filter) {
            AddressFilter.ALL -> ownAddresses
            AddressFilter.FAVOURITES -> ownAddresses.filter { it.walletID in favourites }
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
                Text(stringResource(R.string.dapps_back_button), color = C.textSecondary)
            }

            // Filter tabs
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AddressFilter.entries.forEach { f ->
                    val label = if (f == AddressFilter.ALL) "${stringResource(R.string.general_all)} (${ownAddresses.size})" else stringResource(f.labelResId)
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
                        BoxWithConstraints(contentAlignment = Alignment.Center) {
                            val density = LocalDensity.current
                            val availPx = with(density) { maxWidth.toPx() }
                            val pxPerChar = with(density) { (9.sp).toPx() }
                            val fitCount = (availPx / pxPerChar).toInt()
                            val fontSize = when {
                                label.length > (fitCount * 1.0f).toInt() -> 9.sp
                                label.length > (fitCount * 0.82f).toInt() -> 10.sp
                                label.length > (fitCount * 0.68f).toInt() -> 11.sp
                                else -> 13.sp
                            }
                            Text(
                                text = label,
                                color = if (filter == f) C.accent else C.textSecondary,
                                fontSize = fontSize,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
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
                            Text(stringResource(R.string.addresses_no_addresses), color = C.textSecondary, fontSize = 14.sp)
                        }
                    }
                } else {
                    items(filtered, key = { it.walletID }) { addr ->
                        val expired = addr.duration > 0 && (addr.createTime + addr.duration) < now
                        AddressCard(
                            addr = addr,
                            expired = expired,
                            expiryText = getExpiryText(addr, now),
                            isFavourite = addr.walletID in favourites,
                            onCopy = {
                                clipboard.setText(AnnotatedString(addr.walletID))
                                snackMessage = context.getString(R.string.addresses_copied_toast)
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
                            onToggleFavourite = {
                                val added = toggleFavourite(context, addr.walletID)
                                favourites = getFavourites(context)
                                snackMessage = if (added) context.getString(R.string.addresses_added_favourites) else context.getString(R.string.addresses_removed_favourites)
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
                        stringResource(R.string.addresses_info_note),
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
                        stringResource(R.string.addresses_edit_label_title),
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
                        label = { Text(stringResource(R.string.addresses_label_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = C.text,
                            unfocusedTextColor = C.text,
                            focusedBorderColor = C.accent,
                            unfocusedBorderColor = C.border,
                            focusedLabelColor = C.accent,
                            unfocusedLabelColor = C.textSecondary,
                            cursorColor = C.accent,
                            focusedContainerColor = C.bg,
                            unfocusedContainerColor = C.bg,
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
                            Text(stringResource(R.string.general_cancel), color = C.textSecondary, fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = {
                                val addr = editingAddress ?: return@Button
                                try {
                                    WalletManager.walletInstance?.updateAddress(
                                        addr.walletID, editLabel.trim(), 3 /* AsIs — keep current expiration */
                                    )
                                    WalletManager.walletInstance?.getAddresses(true)
                                } catch (_: Exception) {}
                                editingAddress = null
                                snackMessage = context.getString(R.string.addresses_label_updated)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                        ) {
                            Text(stringResource(R.string.general_save), color = C.textDark, fontWeight = FontWeight.Bold)
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
    isFavourite: Boolean,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavourite: () -> Unit,
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
            // Header: label + star + expiry
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (isFavourite) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = "Favourite",
                    tint = if (isFavourite) Color(0xFFFFC107) else C.textMuted,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onToggleFavourite() }
                        .padding(end = 2.dp),
                )
                Text(
                    text = addr.label.ifEmpty { stringResource(R.string.addresses_no_label) },
                    color = if (expired) C.text.copy(alpha = 0.5f) else C.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp, end = 8.dp),
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
                    stringResource(R.string.addresses_created, formatAddrTimestamp(addr.createTime)),
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
                    Text(stringResource(R.string.general_copy), color = C.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                    Text(stringResource(R.string.general_edit), color = C.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                    Text(stringResource(R.string.general_delete), color = C.error, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun getExpiryText(addr: WalletAddress, now: Long): String {
    if (addr.duration == 0L) return stringResource(R.string.addresses_never_expires)
    val expiresAt = addr.createTime + addr.duration
    if (expiresAt < now) return stringResource(R.string.addresses_expired_label)
    val hoursLeft = ((expiresAt - now) / 3600).toInt() + 1
    return if (hoursLeft < 24) stringResource(R.string.addresses_expires_in, "${hoursLeft}h")
    else stringResource(R.string.addresses_expires_in, "${(hoursLeft + 23) / 24}d")
}

private val addrDateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

private fun formatAddrTimestamp(ts: Long): String {
    return try {
        addrDateFormat.format(Date(ts * 1000))
    } catch (_: Exception) {
        ""
    }
}
