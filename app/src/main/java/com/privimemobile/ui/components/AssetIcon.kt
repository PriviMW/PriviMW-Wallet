package com.privimemobile.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.privimemobile.R

/**
 * Asset icon — matches RN AssetIcon component and official Beam wallet.
 *
 * Uses pre-bundled PNG icons (asset0.png through asset19.png).
 * Asset IDs 0-19 map directly. IDs > 19 hash into the pool (assetId % 20).
 * Same approach as official Beam wallet's AssetManager.getImage().
 */

private val ASSET_DRAWABLES = intArrayOf(
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
    Image(
        painter = painterResource(id = ASSET_DRAWABLES[assetId % ASSET_DRAWABLES.size]),
        contentDescription = ticker,
        modifier = modifier
            .size(size)
            .clip(CircleShape),
    )
}
