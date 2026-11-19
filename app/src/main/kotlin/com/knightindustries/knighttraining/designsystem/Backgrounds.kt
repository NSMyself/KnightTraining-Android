package com.knightindustries.knighttraining.designsystem

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

/**
 * The app's purple-glow background (iOS `NewBackgroundView`, dark): a near-black radial base with
 * a lighter glow upper-left and a deeper one lower-right.
 */
@Composable
fun GradientBackground(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        drawRect(
            Brush.radialGradient(
                listOf(Theme.gradientBase, Color(0xFF141319)),
                center = center,
                radius = max(size.width, size.height) * 0.75f,
            ),
        )
        // Peak 0.7, not the full color: on the near-black base it keeps the blooms soft.
        val radius = size.width * 0.6f
        for ((color, at) in listOf(Theme.MeshBlob.purple to Offset(0.12f, 0.33f), Color(0xFF5516BE) to Offset(0.90f, 0.65f))) {
            val glowCenter = Offset(size.width * at.x, size.height * at.y)
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.7f), color.copy(alpha = 0f)), glowCenter, radius), radius, glowCenter)
        }
    }
}

/**
 * The practice timer's drifting blobs (iOS `AnimatedMeshGradient`): four soft color fields that
 * wander, pulse and change shape, blurred and added onto what's behind them.
 *
 * [alpha] is read in the draw phase, so a long fade costs no recomposition.
 * [isAnimating] `false` freezes the blobs where they are without dropping them, so a caller can
 * stop paying for the simulation and fade a still picture out, rather than fading something still
 * moving. The simulation runs at 30 Hz like iOS's, in dp, so it looks the same on every density.
 *
 * Blur needs API 31 (`RenderEffect`); below that each blob is drawn as a radial gradient instead,
 * which is what a blurred blob looks like anyway, minus the shape-shifting.
 */
@Composable
fun AnimatedMeshBackground(isAnimating: Boolean, modifier: Modifier = Modifier, alpha: () -> Float = { 1f }) {
    val density = LocalDensity.current.density
    var size by remember { mutableStateOf(IntSize.Zero) }
    var blobs by remember { mutableStateOf<List<MeshBlob>?>(null) }
    var frame by remember { mutableIntStateOf(0) }
    val fadeIn = remember { Animatable(0f) }

    LaunchedEffect(Unit) { fadeIn.animateTo(1f, tween(durationMillis = 3_000, easing = EaseIn)) }
    LaunchedEffect(size) {
        if (blobs == null && size.width > 0 && size.height > 0) blobs = MeshBlob.make(size.width / density, size.height / density)
    }
    LaunchedEffect(isAnimating, blobs, size) {
        val current = blobs ?: return@LaunchedEffect
        if (!isAnimating) return@LaunchedEffect
        val width = size.width / density
        val height = size.height / density
        var last = -1L
        var pending = 0L
        while (true) {
            // The infinite variant, so UI tests (and anything else with an infinite-animation
            // policy) can stop it instead of waiting forever for an idle frame.
            withInfiniteAnimationFrameNanos { now ->
                if (last >= 0) pending = min(pending + now - last, MAX_CATCH_UP_NANOS)
                last = now
                var stepped = false
                while (pending >= TICK_NANOS) {
                    current.forEach { it.step(width, height) }
                    pending -= TICK_NANOS
                    stepped = true
                }
                // Only a step redraws: the screen refreshes at 60–120 Hz, the blobs move at 30.
                if (stepped) frame++
            }
        }
    }

    val blurs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val blurPx = 55.dp.value * density
    Canvas(
        modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .graphicsLayer {
                // Its own alpha, not a parent's: a faded parent layer would composite the blobs
                // normally instead of adding them onto the background.
                this.alpha = fadeIn.value * alpha()
                compositingStrategy = CompositingStrategy.Offscreen
                // iOS's `.plusLighter`: the blobs add light to the background under them.
                blendMode = BlendMode.Plus
                if (blurs) renderEffect = BlurEffect(blurPx, blurPx, TileMode.Decal)
            },
    ) {
        @Suppress("UNUSED_EXPRESSION") frame // Read in the draw phase, so a step redraws without recomposing.
        blobs?.forEach { blob ->
            val center = Offset(blob.x * density, blob.y * density)
            if (blurs) {
                drawPath(blob.path(density), blob.color, blendMode = BlendMode.Plus)
            } else {
                val reach = blob.radius * density + blurPx
                drawCircle(Brush.radialGradient(listOf(blob.color, blob.color.copy(alpha = 0f)), center, reach), reach, center, blendMode = BlendMode.Plus)
            }
        }
    }
}

private const val TICK_NANOS = 1_000_000_000L / 30
/** After the app was away, carry on from where the blobs were, not a burst of missed steps. */
private const val MAX_CATCH_UP_NANOS = TICK_NANOS * 4

/** One blob, in dp. A straight port of iOS `MeshBlob` and `updateBlobs`, constants included. */
private class MeshBlob(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val baseRadius: Float,
    val color: Color,
    var phase: Float,
    val morphing: FloatArray,
    val targetMorphing: FloatArray,
    val morphRange: Float,
) {
    var radius = baseRadius

    fun step(width: Float, height: Float) {
        phase += 0.006f

        // A very gentle wander, a subtle pull to the center, damping, and a low top speed.
        vx += sin(phase) * 0.02f
        vy += cos(phase * 0.7f) * 0.02f
        vx += (width / 2 - x) * 0.00008f
        vy += (height / 2 - y) * 0.00008f
        vx *= 0.992f
        vy *= 0.992f
        vx = vx.coerceIn(-MAX_SPEED, MAX_SPEED)
        vy = vy.coerceIn(-MAX_SPEED, MAX_SPEED)
        x += vx
        y += vy

        // A soft bounce, half a radius past each edge.
        val margin = -radius * 0.5f
        if (x < margin) { x = margin; vx = abs(vx) * 0.5f }
        if (x > width - margin) { x = width - margin; vx = -abs(vx) * 0.5f }
        if (y < margin) { y = margin; vy = abs(vy) * 0.5f }
        if (y > height - margin) { y = height - margin; vy = -abs(vy) * 0.5f }

        radius = baseRadius + sin(phase * 0.8f) * baseRadius * 0.05f

        for (j in morphing.indices) {
            val current = morphing[j]
            val target = targetMorphing[j]
            morphing[j] = current + (target - current) * 0.025f
            if (abs(current - target) < 2f) targetMorphing[j] = Random.nextFloat() * 2 * morphRange - morphRange
        }
    }

    /** iOS `MorphingBlobShape`: a circle of [POINTS] cubic segments, each point pushed in or out. */
    fun path(density: Float): Path {
        val cx = x * density
        val cy = y * density
        val r = radius * density
        val tangentCoefficient = (4.0 / 3.0 * tan(Math.PI / (2 * POINTS))).toFloat()
        fun point(i: Int): Offset {
            val angle = (i % POINTS) * 2 * Math.PI / POINTS
            val reach = r + morphing[i % POINTS] * density
            return Offset(cx + reach * sin(angle).toFloat(), cy + reach * cos(angle).toFloat())
        }
        fun tangents(p: Offset): Pair<Offset, Offset> {
            val dx = cx - p.x
            val dy = cy - p.y
            val length = sqrt(dx * dx + dy * dy)
            val perpendicular = Offset(-dy / length, dx / length)
            val handle = length * tangentCoefficient
            return Offset(p.x - perpendicular.x * handle, p.y - perpendicular.y * handle) to
                Offset(p.x + perpendicular.x * handle, p.y + perpendicular.y * handle)
        }
        return Path().apply {
            var lastTangent = tangents(point(POINTS - 1))
            for (i in 0..POINTS) {
                val p = point(i)
                val tangent = tangents(p)
                if (i == 0) moveTo(p.x, p.y) else cubicTo(lastTangent.second.x, lastTangent.second.y, tangent.first.x, tangent.first.y, p.x, p.y)
                lastTangent = tangent
            }
            close()
        }
    }

    companion object {
        private const val POINTS = 12
        private const val MAX_SPEED = 0.6f
        private val colors = listOf(Theme.MeshBlob.purple, Theme.MeshBlob.pink, Theme.MeshBlob.indigo, Color(0xFFC026D3))

        fun make(width: Float, height: Float): List<MeshBlob> = colors.map { color ->
            val radius = 80f + Random.nextFloat() * 60f
            val range = radius * 0.6f
            fun morph() = FloatArray(POINTS) { Random.nextFloat() * 2 * range - range }
            MeshBlob(
                x = radius + Random.nextFloat() * max(0f, width - 2 * radius),
                y = radius + Random.nextFloat() * max(0f, height - 2 * radius),
                vx = Random.nextFloat() * 0.6f - 0.3f,
                vy = Random.nextFloat() * 0.6f - 0.3f,
                baseRadius = radius,
                color = color,
                phase = Random.nextFloat() * 2 * Math.PI.toFloat(),
                morphing = morph(),
                targetMorphing = morph(),
                morphRange = range,
            )
        }
    }
}

/**
 * A top app bar over [GradientBackground]: clear at rest so the glow runs to the top edge, the
 * glow's own base once content scrolls under it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun glowTopAppBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Theme.gradientBase)
