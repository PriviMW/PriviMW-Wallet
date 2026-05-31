package com.privimemobile.ui.chat.media

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.privimemobile.R
import com.privimemobile.ui.theme.C

/** Data for single-image preview (from gallery grid -> caption + send). */
data class ImagePreviewData(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String,
)

@Composable
fun ImagePreviewSheet(
    previewData: ImagePreviewData,
    caption: String,
    onCaptionChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit, // called with caption
    isSending: Boolean,
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = { } // consume background clicks without dismissing
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar — dismiss button only
            Surface(color = Color.Black.copy(alpha = 0.7f), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.chat_cancel_scheduled), tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
            }

            // Full-size image — swipe down to dismiss
            var imageOffsetY by remember { mutableStateOf(0f) }
            val dismissThreshold = 150f
            Box(
                modifier = Modifier
                    .weight(1f)
                    .offset(y = imageOffsetY.coerceAtLeast(0f).dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (imageOffsetY > dismissThreshold) {
                                    onDismiss()
                                } else {
                                    imageOffsetY = 0f
                                }
                            },
                            onDragCancel = { imageOffsetY = 0f },
                            onVerticalDrag = { _, dragAmount ->
                                if (dragAmount > 0) {
                                    imageOffsetY = (imageOffsetY + dragAmount).coerceAtLeast(0f)
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = previewData.uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }

            // Bottom bar — caption input + send button (same row as chat composer)
            Surface(color = C.card, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = caption,
                        onValueChange = { onCaptionChange(it) },
                        placeholder = {
                            Text(stringResource(R.string.chat_add_caption), color = C.textMuted, fontSize = 14.sp)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = C.bg,
                            unfocusedContainerColor = C.bg,
                            cursorColor = C.accent,
                            focusedTextColor = C.text,
                            unfocusedTextColor = C.text,
                        ),
                        maxLines = 3,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp),
                    )
                    Spacer(Modifier.width(6.dp))
                    if (isSending) {
                        CircularProgressIndicator(
                            color = C.accent, strokeWidth = 2.dp, modifier = Modifier.size(32.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(48.dp).clip(CircleShape).background(C.accent)
                                .clickable { onSend(caption) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.chat_send_message),
                                tint = C.textDark,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
