package com.privimemobile.ui.dapps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.R
import com.privimemobile.protocol.DAppStore
import com.privimemobile.protocol.Publisher
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.launch

/**
 * Publishers screen — list all DApp Store publishers with toggle to show/hide their DApps.
 *
 * Ports PublishersList.qml from beam-ui.
 */
@Composable
fun PublishersScreen(
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var publishers by remember { mutableStateOf<List<Publisher>>(emptyList()) }
    var unwanted by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }

    fun refresh() {
        scope.launch {
            loading = true
            val allPublishers = DAppStore.queryPublishersAsync(context)
            // Get ALL DApps regardless of unwanted filter so disabled publishers still appear
            val allDApps = DAppStore.queryAvailableDAppsAsync(context, filterUnwanted = false)
            val activePubkeys = allDApps.map { it.publisher.lowercase() }.filter { it.isNotEmpty() }.toSet()
            publishers = allPublishers.filter { activePubkeys.contains(it.pubkey.lowercase()) }
            unwanted = DAppStore.getUnwantedPublishers(context)
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    Column(modifier = Modifier.fillMaxSize().background(C.bg)) {
        // Back button — consistent with DAppStoreBrowseScreen
        TextButton(
            onClick = onBack,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        ) {
            Text(stringResource(R.string.dapps_back_button), color = C.textSecondary)
        }

        Text(
            stringResource(R.string.dapps_publishers),
            color = C.text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
        stringResource(R.string.dapps_publishers_subtitle),
            color = C.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))

        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = C.accent)
            }
        } else if (publishers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                stringResource(R.string.dapps_no_publishers),
                    color = C.textSecondary,
                    fontSize = 15.sp,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(publishers, key = { it.pubkey }) { pub ->
                    val enabled = !unwanted.contains(pub.pubkey.lowercase())
                    PublisherCard(
                        publisher = pub,
                        enabled = enabled,
                        onToggle = { newEnabled ->
                            if (newEnabled) {
                                DAppStore.removeUnwantedPublisher(context, pub.pubkey)
                            } else {
                                DAppStore.addUnwantedPublisher(context, pub.pubkey)
                            }
                            unwanted = DAppStore.getUnwantedPublishers(context)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PublisherCard(
    publisher: Publisher,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = C.card),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    publisher.name,
                    color = C.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (publisher.aboutMe.isNotEmpty()) {
                    Text(
                        publisher.aboutMe,
                        color = C.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Text(
                    text = if (publisher.pubkey.length > 20)
                        "${publisher.pubkey.take(8)}...${publisher.pubkey.takeLast(8)}"
                    else publisher.pubkey,
                    color = C.textSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = C.accent,
                    checkedTrackColor = C.accent.copy(alpha = 0.5f),
                    uncheckedThumbColor = C.textSecondary,
                    uncheckedTrackColor = C.border,
                ),
            )
        }
    }
}
