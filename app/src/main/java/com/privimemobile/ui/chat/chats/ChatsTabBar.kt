package com.privimemobile.ui.chat.chats

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.R
import com.privimemobile.ui.theme.C

/** Tab selector for chat folders (All / Unread / Groups / DMs / Archived). */
@Composable
internal fun ChatsTabBar(
    activeTab: Int,
    onTabSelected: (Int) -> Unit,
    conversations: List<com.privimemobile.chat.db.entities.ConversationEntity>,
    groups: List<com.privimemobile.chat.db.entities.GroupEntity>,
) {
    val tabLabelRes = listOf(R.string.chats_tab_all, R.string.chats_tab_unread, R.string.chats_tab_groups, R.string.chats_tab_dms, R.string.chats_tab_archived)
    val unreadTotal = conversations.count { !it.archived && it.unreadCount > 0 } + groups.count { !it.archived && it.unreadCount > 0 }
    val groupsTotal = groups.count { !it.archived }
    val dms = conversations.filter { !it.convKey.startsWith("g_") }
    val dmsTotal = dms.count { !it.archived }
    val archivedTotal = conversations.count { it.archived } + groups.count { it.archived }
    // Telegram-style tab selector with animated pill indicator — horizontally scrollable
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabLabelRes.forEachIndexed { idx, labelRes ->
            val label = stringResource(labelRes)
            val badge = when (idx) {
                1 -> if (unreadTotal > 0) " ($unreadTotal)" else ""
                2 -> if (groupsTotal > 0) " ($groupsTotal)" else ""
                3 -> if (dmsTotal > 0) " ($dmsTotal)" else ""
                4 -> if (archivedTotal > 0) " ($archivedTotal)" else ""
                else -> ""
            }
            val selected = activeTab == idx
            val bgColor by animateColorAsState(
                if (selected) C.accent.copy(alpha = 0.15f) else Color.Transparent,
                animationSpec = tween(250), label = "tabBg$idx",
            )
            val textColor by animateColorAsState(
                if (selected) C.accent else C.textSecondary,
                animationSpec = tween(250), label = "tabTxt$idx",
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(bgColor)
                    .clickable { onTabSelected(idx) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    "$label$badge",
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}