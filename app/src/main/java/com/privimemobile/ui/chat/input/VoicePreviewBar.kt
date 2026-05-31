package com.privimemobile.ui.chat.input

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privimemobile.R
import com.privimemobile.chat.voice.VoiceWaveformView
import com.privimemobile.ui.theme.C

/** Telegram-style voice preview bar after user pauses a locked recording. */
@Composable
fun VoicePreviewBar(
    waveform: ByteArray?,
    durationMs: Long,
    onDelete: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(40.dp).clip(CircleShape),
        ) {
            Icon(
                Icons.Default.Delete,
                stringResource(R.string.chat_label_delete),
                tint = C.textSecondary,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(Modifier.width(4.dp))

        VoiceWaveformView(
            waveform = waveform,
            progress = 0f,
            isMine = true,
            modifier = Modifier.weight(1f).height(24.dp),
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text = formatVoiceDuration(durationMs),
            color = C.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )

        Spacer(Modifier.width(8.dp))

        IconButton(
            onClick = onSend,
            modifier = Modifier.size(40.dp).clip(CircleShape).background(C.accent),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                stringResource(R.string.chat_send_message),
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
