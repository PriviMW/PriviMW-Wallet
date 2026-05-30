package com.privimemobile.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.R
import com.privimemobile.ui.theme.C

/**
 * Chat command menu popup (Telegram-style).
 * Floats above the input bar as an overlay — reports 0 height to the parent
 * so it doesn't push content up, and places its content above via a custom
 * layout modifier. Visually merges with the input bar (same background,
 * top-only rounded corners, no shadow, flush edges).
 */
@Composable
fun ChatCommandMenu(
    isGroupMode: Boolean,
    onCommandSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = C.card,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        modifier = modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, 0) {
                    placeable.place(0, -placeable.height)
                }
            },
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                stringResource(R.string.chat_commands),
                color = C.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
            )
            listOf(
                Triple(
                    "/tip",
                    if (isGroupMode) "<amount> [asset_id] [msg] (reply to tip)" else "<amount> [asset_id] [message]",
                    if (isGroupMode) stringResource(R.string.chat_tip_hint) else stringResource(R.string.chat_send_beam_hint),
                ),
                Triple(
                    "/poll",
                    stringResource(R.string.chat_poll_args),
                    stringResource(R.string.chat_poll_desc),
                ),
            ).forEach { (cmd, args, desc) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onCommandSelect("$cmd ") }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(cmd, color = C.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    Text(args, color = C.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                }
                Text(desc, color = C.textMuted, fontSize = 11.sp, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
            }
        }
    }
}