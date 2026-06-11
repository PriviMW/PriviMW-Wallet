package com.privimemobile.ui.chat

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Layout constants for chat message bubbles. Kept in one place so image/album
 * width caps stay in sync.
 *
 * Why two ratios?
 * - [IMAGE_BUBBLE_RATIO] (0.70f) caps single-image bubbles and the image column
 *   inside a mixed text+image bubble. Matches Telegram's default.
 * - [ALBUM_BUBBLE_RATIO] (0.72f) caps multi-image album cards. Slightly wider
 *   because album cells already self-pad; a tighter cap would make 2x2 grids
 *   look cramped.
 */
object ChatLayoutConstants {
    const val IMAGE_BUBBLE_RATIO: Float = 0.70f
    const val ALBUM_BUBBLE_RATIO: Float = 0.72f
}

internal fun imageBubbleWidth(screenWidthDp: Int): Dp = (screenWidthDp * ChatLayoutConstants.IMAGE_BUBBLE_RATIO).dp
internal fun albumBubbleWidth(screenWidthDp: Int): Dp = (screenWidthDp * ChatLayoutConstants.ALBUM_BUBBLE_RATIO).dp
