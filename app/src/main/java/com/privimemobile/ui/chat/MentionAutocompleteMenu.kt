package com.privimemobile.ui.chat

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import com.privimemobile.chat.db.entities.GroupMemberEntity
import com.privimemobile.ui.theme.C

/**
 * @mention autocomplete popup for group chats.
 * Floats above the input bar as an overlay — reports 0 height to the parent
 * so it doesn't push content up, and places its content above via a custom
 * layout modifier. Visually merges with the input bar (same background,
 * top-only rounded corners, no shadow, flush edges).
 */
@Composable
fun MentionAutocompleteMenu(
    members: List<GroupMemberEntity>,
    onSelect: (GroupMemberEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

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
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            members.forEach { member ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(member) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Small avatar
                    val avatarFile = java.io.File(context.filesDir, "avatars/${member.handle}.webp")
                    if (avatarFile.exists()) {
                        val bmp = remember(member.handle) { BitmapFactory.decodeFile(avatarFile.absolutePath) }
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            AvatarFallback(handle = member.handle)
                        }
                    } else {
                        AvatarFallback(handle = member.handle)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            member.displayName ?: member.handle,
                            color = C.text, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        )
                        if (!member.displayName.isNullOrEmpty()) {
                            Text("@${member.handle}", color = C.textMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarFallback(handle: String) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(C.accent),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            handle.first().uppercase(),
            color = C.textDark,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}