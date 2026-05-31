package com.privimemobile.ui.chat.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.R
import com.privimemobile.chat.ChatService
import com.privimemobile.chat.db.entities.ContactEntity
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * On-chain search result items for a LazyColumn.
 * Shows a spinner while searching, then handle results and group results.
 */
internal fun LazyListScope.onChainSearchResults(
    isSearching: Boolean,
    searchQuery: String,
    onChainHandles: List<ContactEntity>,
    onChainGroups: List<Map<String, Any?>>,
    scope: CoroutineScope,
    onOpenChat: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onSearchCleared: () -> Unit,
) {
    // Show spinner while searching
    if (isSearching && searchQuery.isNotBlank()) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), color = C.accent, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.search_searching), color = C.textSecondary, fontSize = 13.sp)
            }
        }
    }

    // On-chain handle results
    if (onChainHandles.isNotEmpty()) {
        item {
            Text(
                stringResource(R.string.search_section_global),
                color = C.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
            )
        }
        items(onChainHandles.size, key = { "oc_${onChainHandles[it].handle}" }) { idx ->
            val contact = onChainHandles[idx]
            Column(modifier = Modifier.background(C.bg)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch {
                                ChatService.contacts.ensureContact(contact.handle, contact.displayName, contact.walletId)
                            }
                            // Navigate to chat (clears search)
                            onSearchCleared()
                            onOpenChat(contact.handle)
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    com.privimemobile.ui.components.AvatarDisplay(
                        handle = contact.handle,
                        displayName = contact.displayName,
                        size = 44.dp,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            contact.displayName?.ifEmpty { null } ?: "@${contact.handle}",
                            color = C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        )
                        if (!contact.displayName.isNullOrEmpty()) {
                            Text("@${contact.handle}", color = C.textSecondary, fontSize = 13.sp)
                        }
                    }
                    Surface(shape = RoundedCornerShape(20.dp), color = C.accent) {
                        Text(
                            stringResource(R.string.search_button_chat), color = C.textDark, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
                HorizontalDivider(
                    color = C.border.copy(alpha = 0.5f),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(start = 74.dp),
                )
            }
        }
    }

    // On-chain group results
    if (onChainGroups.isNotEmpty()) {
        item {
            Text(
                stringResource(R.string.search_section_groups),
                color = C.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
            )
        }
        items(onChainGroups.size, key = { "ocg_${onChainGroups[it]["group_id"]}" }) { idx ->
            val g = onChainGroups[idx]
            val groupId = g["group_id"] as? String ?: return@items
            val name = g["name"] as? String ?: ""
            val creator = g["creator"] as? String ?: ""
            val memberCount = g["member_count"] as? Int ?: 0
            val needsApproval = (g["require_approval"] as? Int ?: 0) == 1
            var joining by remember { mutableStateOf(false) }

            Column(modifier = Modifier.background(C.bg)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF5C6BC0)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Group, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, color = C.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            LocalContext.current.getString(R.string.chat_join_group_subtitle, memberCount, creator),
                            color = C.textSecondary, fontSize = 13.sp,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            joining = true
                            ChatService.groups.joinGroup(groupId) { success, error ->
                                joining = false
                                if (success) {
                                    onSearchCleared()
                                    scope.launch {
                                        ChatService.groups.refreshMyGroups()
                                    }
                                    onOpenGroup(groupId)
                                }
                            }
                        },
                        enabled = !joining,
                        colors = ButtonDefaults.buttonColors(containerColor = C.accent),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp),
                    ) {
                        if (joining) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text(
                                if (needsApproval) stringResource(R.string.search_button_request) else stringResource(R.string.search_button_join),
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                HorizontalDivider(
                    color = C.border.copy(alpha = 0.5f),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(start = 74.dp),
                )
            }
        }
    }
}