package com.privimemobile.ui.chat.chrome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.R
import com.privimemobile.chat.db.entities.MessageEntity
import com.privimemobile.ui.chat.ChatSearchState
import com.privimemobile.ui.chat.format.formatMessageTime
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** In-chat search field + results list (below header). */
@Composable
fun ChatSearchOverlay(
    search: ChatSearchState,
    scope: CoroutineScope,
    onResultClick: (timestamp: Long) -> Unit,
    onQuerySearch: suspend (query: String) -> Unit,
) {
    AnimatedVisibility(
        visible = search.showSearch,
        enter = expandVertically(tween(200)) + fadeIn(tween(200)),
        exit = shrinkVertically(tween(200)) + fadeOut(tween(200)),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Surface(color = C.card) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { search.close() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.chat_overflow_close_search),
                            tint = C.textSecondary,
                        )
                    }
                    OutlinedTextField(
                        value = search.searchQuery,
                        onValueChange = { query ->
                            search.onQueryChanged(query)
                            if (query.isNotBlank()) {
                                search.searchJob = scope.launch {
                                    delay(300)
                                    onQuerySearch(query.trim())
                                }
                            }
                        },
                        placeholder = {
                            Text(
                                stringResource(R.string.chat_search_in_chat),
                                color = C.textMuted,
                                fontSize = 14.sp,
                            )
                        },
                        singleLine = true,
                        trailingIcon = if (search.searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { search.clearQueryAndResults() }) {
                                    Text("✕", color = C.textSecondary, fontSize = 18.sp)
                                }
                            }
                        } else {
                            null
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = C.accent,
                            unfocusedBorderColor = C.border,
                            focusedContainerColor = C.bg,
                            unfocusedContainerColor = C.bg,
                            cursorColor = C.accent,
                            focusedTextColor = C.text,
                            unfocusedTextColor = C.text,
                        ),
                    )
                    if (search.searchResults.isNotEmpty()) {
                        Text(
                            "${search.searchResults.size}",
                            color = C.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
            if (search.searchResults.isNotEmpty()) {
                Surface(color = C.card.copy(alpha = 0.95f)) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                    ) {
                        items(search.searchResults, key = { it.id }) { result ->
                            ChatSearchResultRow(
                                result = result,
                                onClick = {
                                    onResultClick(result.timestamp)
                                    search.closeAfterResultPick()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatSearchResultRow(
    result: MessageEntity,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                result.text?.take(80) ?: "",
                color = C.text,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatMessageTime(result.timestamp),
                color = C.textSecondary,
                fontSize = 11.sp,
            )
        }
        if (result.sent) {
            Text(stringResource(R.string.chat_you_label), color = C.textSecondary, fontSize = 11.sp)
        }
    }
    HorizontalDivider(color = C.border, thickness = 0.5.dp)
}
