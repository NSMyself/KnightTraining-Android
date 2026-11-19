package com.knightindustries.knighttraining.feature.celebration

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import com.knightindustries.knighttraining.designsystem.Theme
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.max
import kotlin.random.Random

/**
 * A one-shot confetti burst (iOS `ConfettiView`, timings and counts ported as they are): eight
 * pops, 100 down to 20 pieces, a tenth of a second apart, then a trickle of 600 that tapers off over
 * about seven seconds. Pieces fall at a default-gravity pace, lean together in a shared wind of
 * two or three gusts, spin, and tumble (a squash on one axis, standing in for iOS's 3D rotation).
 *
 * Every piece's position is a function of time alone, so one Canvas draws them all from a single
 * clock: no per-piece state, nothing recomposes. Decorative, and never takes touches.
 */
@Composable
fun Confetti(modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val pieces = remember { ConfettiPiece.burst() }
    val end = remember(pieces) { pieces.maxOf { it.delay + it.fallDuration } }
    var elapsed by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(pieces) {
        val start = withFrameNanos { it }
        while (elapsed < end) {
            withFrameNanos { now -> elapsed = (now - start) / 1e9f }
        }
    }

    Canvas(modifier.fillMaxSize()) {
        val time = elapsed
        if (time >= end) return@Canvas
        for (piece in pieces) {
            val t = time - piece.delay
            if (t < 0f || t > piece.fallDuration) continue
            val progress = t / piece.fallDuration
            val y = -40f * density + (size.height + 80f * density) * progress
            val x = piece.startX * size.width + piece.swayAt(t) * density
            val rotation = piece.startRotation + (piece.endRotation - piece.startRotation) * progress
            val tumble = piece.startTumble + (piece.endTumble - piece.startTumble) * progress
            val alpha = if (progress < 0.75f) 1f else 1f - (1f - piece.fadeOpacity) * (progress - 0.75f) / 0.25f
            val w = piece.size * density
            val h = w * piece.aspectRatio
            translate(x, y) {
                rotate(rotation, pivot = Offset.Zero) {
                    // Tumbling through space reads as the piece flattening and flipping on its axis.
                    scale(scaleX = cos(Math.toRadians(tumble.toDouble())).toFloat().let { if (abs(it) < 0.08f) 0.08f else it }, scaleY = 1f, pivot = Offset.Zero) {
                        val color = piece.color.copy(alpha = alpha)
                        val topLeft = Offset(-w / 2, -h / 2)
                        if (piece.isCircle) drawOval(color, topLeft, Size(w, h))
                        else drawRoundRect(color, topLeft, Size(w, h), CornerRadius(w * 0.25f))
                    }
                }
            }
        }
    }
}

private class ConfettiPiece(
    val isCircle: Boolean,
    val color: Color,
    /** dp. */
    val size: Float,
    val aspectRatio: Float,
    /** Fraction of the width. */
    val startX: Float,
    /** Where the wind pushes it in each stretch of its fall, dp from [startX]. */
    private val swayOffsets: FloatArray,
    private val swayDurations: FloatArray,
    val startRotation: Float,
    val endRotation: Float,
    val startTumble: Float,
    val endTumble: Float,
    val fallDuration: Float,
    val delay: Float,
    val fadeOpacity: Float,
) {
    /** The horizontal offset [t] seconds into the fall: eased from one gust's push to the next. */
    fun swayAt(t: Float): Float {
        var from = 0f
        var segmentStart = 0f
        for (i in swayOffsets.indices) {
            val segmentEnd = segmentStart + swayDurations[i]
            if (t <= segmentEnd || i == swayOffsets.lastIndex) {
                val f = ((t - segmentStart) / max(swayDurations[i], 1e-3f)).coerceIn(0f, 1f)
                val eased = f * f * (3 - 2 * f)
                return from + (swayOffsets[i] - from) * eased
            }
            from = swayOffsets[i]
            segmentStart = segmentEnd
        }
        return from
    }

    /** A shift in the wind shared by the whole burst, so it reads as one wind, not jittering pieces. */
    private class Gust(val startTime: Float, val push: Float)

    companion object {
        private val palette = listOf(Theme.accent, Theme.ratingGold, Color(0xFF5AD8A6), Color(0xFFFF6F91), Color(0xFFEBECFE))

        /** Each pop's size and when it fires (seconds), the cannon tapering off and then holding. */
        private val pops = listOf(100 to 0f, 100 to 0.1f, 80 to 0.2f, 40 to 0.3f, 30 to 0.4f, 20 to 0.5f, 20 to 0.6f, 20 to 0.7f)
        private const val POP_WINDOW = 0.15f
        private const val TRICKLE_COUNT = 600
        private const val TRICKLE_START = 0.5f
        private const val TRICKLE_LENGTH = 6.5f
        /** Launch density `1 - x^2.5`: near full for most of the window, falling away at the end. */
        private const val TRICKLE_TAPER = 2.5

        /** 10% slower than a plain default fall. */
        private const val GRAVITY = 1.1f
        /** Slightly wider than the screen. */
        private const val SPREAD = 0.9f * 1.2f

        private fun Random.between(from: Float, to: Float) = from + nextFloat() * (to - from)

        fun burst(random: Random = Random.Default): List<ConfettiPiece> {
            val gustCount = random.nextInt(2, 4)
            val step = 7f / gustCount
            val gusts = (0 until gustCount).map { i ->
                Gust(i * step + random.between(0f, step * 0.5f), (if (random.nextBoolean()) 1f else -1f) * random.between(10f, 55f) * 1.2f)
            }
            val popPieces = pops.flatMap { (count, at) -> List(count) { piece(random, gusts, at + random.between(0f, POP_WINDOW)) } }
            val trickle = List(TRICKLE_COUNT) { piece(random, gusts, trickleDelay(random)) }
            return popPieces + trickle
        }

        private fun trickleDelay(random: Random): Float {
            while (true) {
                val x = random.nextDouble()
                if (random.nextDouble() < 1 - x.pow(TRICKLE_TAPER)) return TRICKLE_START + x.toFloat() * TRICKLE_LENGTH
            }
        }

        private fun piece(random: Random, gusts: List<Gust>, delay: Float): ConfettiPiece {
            val fallDuration = random.between(2f, 3.2f) * GRAVITY
            val fallEnd = delay + fallDuration
            // The gusts that change partway through this piece's fall, then its landing.
            val boundaries = gusts.map { it.startTime + random.between(-0.3f, 0.3f) }.filter { it > delay && it < fallEnd }.sorted() + fallEnd
            val offsets = FloatArray(boundaries.size)
            val durations = FloatArray(boundaries.size)
            var segmentStart = delay
            boundaries.forEachIndexed { i, boundary ->
                val gust = gusts.filter { it.startTime <= segmentStart }.maxByOrNull { it.startTime }
                offsets[i] = (gust?.push ?: 0f) * random.between(0.85f, 1.15f)
                durations[i] = boundary - segmentStart
                segmentStart = boundary
            }
            return ConfettiPiece(
                isCircle = random.nextBoolean(),
                color = palette.random(random),
                size = random.between(6f, 11f),
                aspectRatio = random.between(0.4f, 1f),
                startX = random.between(0.5f - SPREAD / 2, 0.5f + SPREAD / 2),
                swayOffsets = offsets,
                swayDurations = durations,
                startRotation = random.between(0f, 360f),
                endRotation = random.between(150f, 520f),
                startTumble = random.between(0f, 360f),
                endTumble = random.between(360f, 1080f) * (if (random.nextBoolean()) 1 else -1),
                fallDuration = fallDuration,
                delay = delay,
                fadeOpacity = random.between(0.1f, 0.3f),
            )
        }
    }
}
