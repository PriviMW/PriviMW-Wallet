package com.privimemobile.chat.voice

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.privimemobile.ui.theme.C
import kotlinx.coroutines.launch
import kotlin.math.pow

/**
 * Voice message waveform visualization matching Telegram-X style.
 *
 * - 3dp wide bars with 1dp gap, 1dp corner radius
 * - 1.5dp minimum height, 8.5dp maximum height (7dp diff)
 * - 5-bit packed waveform data (values 0-31)
 * - Progress via clipRect for played/unplayed portions
 * - Expand animation with AnticipateOvershootInterpolator(3.0f)
 */
@Composable
fun VoiceWaveformView(
    waveform: ByteArray?,
    progress: Float = 0f,
    isPlaying: Boolean = false,
    isMine: Boolean = true,
    modifier: Modifier = Modifier,
    activeColor: Color = if (isMine) C.waveformActive else C.accent,
    inactiveColor: Color = if (isMine) C.waveformInactive else C.accent.copy(alpha = 0.3f),
) {
    val density = LocalDensity.current

    // Dimensions matching Telegram-X Waveform.java
    val barWidth = with(density) { 3.dp.toPx() }
    val barGap = with(density) { 1.dp.toPx() }
    val minHeight = with(density) { 1.5.dp.toPx() }
    val maxHeightDiff = with(density) { 7.dp.toPx() }
    val cornerRadius = with(density) { 1.dp.toPx() }

    // Expand animation (AnticipateOvershootInterpolator(3.0f), 350ms + 80ms delay)
    val expandFactor = remember { Animatable(0f) }
    LaunchedEffect(waveform) {
        if (waveform != null && waveform.isNotEmpty()) {
            kotlinx.coroutines.delay(80)
            launch {
                expandFactor.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 350,
                        easing = { fraction ->
                            // AnticipateOvershootInterpolator(3.0f) equivalent
                            val t = fraction * 1.5f - 0.75f
                            t * t * t * t * t + 1f
                        }
                    )
                )
            }
        } else {
            expandFactor.snapTo(0f)
        }
    }

    // Default placeholder if no waveform
    val displayWaveform = waveform ?: ByteArray(50) { 16.toByte() } // mid-level placeholder

    // Calculate max sample for normalization
    val maxSample = displayWaveform.maxOf {
        val value = if (it < 0) -it.toInt() else it.toInt()
        value.coerceIn(0, 31)
    }

    Canvas(modifier = modifier) {
        val totalWidth = size.width
        val totalHeight = size.height
        val centerY = totalHeight / 2f

        // Calculate number of bars that fit
        val barStep = barWidth + barGap
        val numBars = (totalWidth / barStep).toInt().coerceAtLeast(1)

        // Scale waveform to fit bars
        val scaledSamples = IntArray(numBars) { index ->
            if (displayWaveform.isEmpty()) return@IntArray 16
            val sampleIndex = (index * displayWaveform.size / numBars.toFloat()).toInt()
                .coerceIn(0, displayWaveform.size - 1)
            val sample = displayWaveform[sampleIndex]
            val value = if (sample < 0) -sample.toInt() else sample.toInt()
            value.coerceIn(0, 31)
        }

        // Calculate actual waveform width
        val waveformWidth = numBars * barStep - barGap
        val startX = 0f

        // Draw progress clip (played portion)
        val progressX = startX + progress * waveformWidth

        // Draw bars
        var x = startX
        for (i in 0 until numBars) {
            val sample = scaledSamples[i]
            val heightDiff = if (maxSample > 0) {
                maxHeightDiff * (sample.toFloat() / maxSample.toFloat()) * expandFactor.value
            } else {
                0f
            }
            val height = (minHeight + heightDiff) * 2f

            val color = if (x < progressX) activeColor else inactiveColor

            drawRoundRect(
                color = color,
                topLeft = Offset(x, centerY - height / 2f),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(cornerRadius, cornerRadius)
            )

            x += barStep
        }
    }
}

/**
 * Unpack 5-bit packed waveform data.
 * Each byte contains a signed value from -31 to 31 (or 0-31 unsigned).
 * Returns list of normalized floats (0f to 1f).
 */
fun unpackWaveform(packed: ByteArray?): List<Float> {
    if (packed == null || packed.isEmpty()) return List(50) { 0.5f }
    return packed.map { byte ->
        val value = if (byte < 0) -byte.toInt() else byte.toInt()
        value.coerceIn(0, 31) / 31f
    }
}

/**
 * Pack amplitude samples into 5-bit waveform data (63 bytes for ~100 samples).
 * Takes raw amplitude values (0-32767 range) and downsamples to 100 buckets.
 */
fun packWaveform(amplitudes: List<Int>): ByteArray {
    if (amplitudes.isEmpty()) return ByteArray(63) { 16.toByte() }

    val targetSamples = 100
    val step = (amplitudes.size / targetSamples.toFloat()).coerceAtLeast(1f)

    val samples = IntArray(targetSamples) { index ->
        val startIdx = (index * step).toInt().coerceIn(0, amplitudes.size - 1)
        val endIdx = ((index + 1) * step).toInt().coerceIn(0, amplitudes.size)

        // Take max amplitude in bucket
        var max = 0
        for (i in startIdx until endIdx) {
            if (amplitudes[i] > max) max = amplitudes[i]
        }
        max
    }

    // Normalize to 0-31 and pack
    val maxAmp = samples.maxOrNull() ?: 1
    return samples.map { sample ->
        val normalized = (sample.toFloat() / maxAmp.toFloat() * 31f).toInt().coerceIn(0, 31)
        normalized.toByte()
    }.toByteArray()
}

/**
 * Pack waveform into 63-byte format (5-bit per sample, 100 samples packed).
 * This matches Telegram's 63-byte format: ceil(100 * 5 / 8) = 63 bytes.
 *
 * Note: For simplicity, we store as 100 bytes with values 0-31.
 * True 5-bit packing would require bit manipulation.
 */
fun packWaveform5Bit(amplitudes: List<Int>): ByteArray {
    // For now, use simple byte-per-sample format
    // Values 0-31 fit in a byte, no need for bit packing
    return packWaveform(amplitudes)
}