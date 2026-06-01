package com.privimemobile.ui.chat.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.R
import com.privimemobile.protocol.Helpers
import com.privimemobile.ui.chat.ChatInputState
import com.privimemobile.ui.chat.format.formatTimerLabel
import com.privimemobile.ui.theme.C

/** Strips above the input bar: self-destruct timer, edit target, reply target. */
@Composable
fun ChatReplyEditBars(input: ChatInputState) {
    val context = LocalContext.current

    if (input.oneShotTimer > 0) {
        Surface(color = C.card, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("\u23F3", fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    context.getString(R.string.chat_self_destruct_timer) + ": ${formatTimerLabel(context, input.oneShotTimer)}",
                    color = C.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { input.oneShotTimer = 0 }) {
                    Text(stringResource(R.string.chat_close), color = C.error, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    val editing = input.editingMsg
    if (editing != null) {
        Surface(color = C.card, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(32.dp)
                        .background(Color(0xFFFFA726)),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                ) {
                    Text(
                        stringResource(R.string.chat_editing_message),
                        color = Color(0xFFFFA726),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        editing.text.take(80),
                        color = C.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = {
                    input.editingMsg = null
                    input.setInputText("")
                }) {
                    Text(stringResource(R.string.chat_close), color = C.error, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    val replying = input.replyingTo
    if (replying != null) {
        Surface(color = C.card, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(32.dp)
                        .background(C.accent),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                ) {
                    Text(
                        if (replying.sent) stringResource(R.string.chat_you_label) else "@${replying.from}",
                        color = C.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        replying.text.ifEmpty {
                            when (replying.type) {
                                "file" -> stringResource(R.string.chat_reply_file)
                                "tip" -> context.getString(
                                    R.string.chat_tip_simple,
                                    "${Helpers.formatBeam(replying.tipAmount)} ${com.privimemobile.wallet.assetTicker(replying.tipAssetId)}",
                                )
                                else -> context.getString(R.string.chat_message_label)
                            }
                        }.take(80),
                        color = C.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = { input.replyingTo = null }) {
                    Text(stringResource(R.string.chat_close), color = C.error, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
