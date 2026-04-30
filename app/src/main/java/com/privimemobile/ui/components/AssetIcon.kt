package com.privimemobile.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.privimemobile.R

/**
 * Asset icon — known assets get their real logo, unknown assets
 * fall back to the generic colored icon pool (assetId % 20).
 */

// Known asset IDs → their specific icon drawable
private val KNOWN_ASSET_ICONS = mapOf(
    0 to R.drawable.icon_asset0,
    7 to R.drawable.icon_asset7,
    36 to R.drawable.icon_asset36,
    37 to R.drawable.icon_asset37,
    38 to R.drawable.icon_asset38,
    39 to R.drawable.icon_asset39,
    47 to R.drawable.icon_asset47,
    174 to R.drawable.icon_asset174,
)

// Generic fallback pool (asset0.png through asset19.png)
private val FALLBACK_DRAWABLES = intArrayOf(
    R.drawable.asset0,  R.drawable.asset1,  R.drawable.asset2,  R.drawable.asset3,
    R.drawable.asset4,  R.drawable.asset5,  R.drawable.asset6,  R.drawable.asset7,
    R.drawable.asset8,  R.drawable.asset9,  R.drawable.asset10, R.drawable.asset11,
    R.drawable.asset12, R.drawable.asset13, R.drawable.asset14, R.drawable.asset15,
    R.drawable.asset16, R.drawable.asset17, R.drawable.asset18, R.drawable.asset19,
)

@Composable
fun AssetIcon(
    assetId: Int,
    ticker: String = "BEAM",
    size: Dp = 36.dp,
    modifier: Modifier = Modifier,
) {
    val drawableId = KNOWN_ASSET_ICONS[assetId]
        ?: FALLBACK_DRAWABLES[assetId % FALLBACK_DRAWABLES.size]

    // BEAM logo is triangular + non-square (238×167) — scale down after clip so
    // corners fit inside the full-size circle
    val beamScale = if (assetId == 0) 0.88f else 1f

    Image(
        painter = painterResource(id = drawableId),
        contentDescription = ticker,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .graphicsLayer { scaleX = beamScale; scaleY = beamScale },
    )
}
