package com.knightindustries.knighttraining.feature.profile

import com.knightindustries.knighttraining.designsystem.Palette
import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.designsystem.Theme
import com.knightindustries.knighttraining.ui.currentLocale
import com.knightindustries.knighttraining.ui.durationText
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/** One week of the Profile chart: time studied and planned, and the rating at the week's end. */
data class RatingChartPoint(val weekStart: LocalDate, val seconds: Int, val plannedSeconds: Int, val rating: Int?)

// iOS `EloHistoryChart` on Palette: hours in the accent, the plan's dashes in the accent too, the
// rating line in `rating`.
private class ChartColors(val hours: Color, val planned: Color, val rating: Color)

@Composable
private fun chartColors() = Palette.current.let { ChartColors(it.accent.copy(alpha = 0.55f), it.accent, it.rating) }

/*
 * Bands of the chart's height (iOS `EloHistoryChart`): bars rise to 90%, the rating line lives in
 * 80–92% whatever the bars do, so a week with little logged never leaves a dot floating far above
 * a bar that barely shows. Both share one canvas; no second axis to fall out of step.
 */
private const val BAR_TOP = 0.9f
private const val RATING_BOTTOM = 0.8f
private const val RATING_TOP = 0.92f

/**
 * Effort and rating side by side (iOS `EloHistoryChart`): hours as bars, the planned hours as a
 * dashed line, the rating in gold above, its latest value on the right axis. Leading weeks with
 * nothing in them are trimmed, so a new account's few weeks fill the chart. With ratings but no
 * time ever logged or planned it's a plain rating line instead (iOS `RatingOnlyChart`); with
 * nothing at all, a note saying so.
 *
 * Bars rise one after another, then the rating dots appear, then the lines join them.
 */
@Composable
fun RatingChart(points: List<RatingChartPoint>, modifier: Modifier = Modifier) {
    val hasEffort = points.any { it.seconds > 0 || it.plannedSeconds > 0 }
    val hasRatings = points.any { it.rating != null }
    when {
        hasEffort -> EffortAndRatingChart(points.dropWhile { it.seconds == 0 && it.plannedSeconds == 0 }, modifier)
        hasRatings -> RatingOnlyChart(points.filter { it.rating != null }, modifier)
        else -> NoData(modifier)
    }
}

@Composable
private fun NoData(modifier: Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.AutoMirrored.Rounded.ShowChart, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(28.dp))
        Text(stringResource(R.string.chart_no_training), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.chart_no_training_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), textAlign = TextAlign.Center)
    }
}

/** The staged reveal's clocks: one per bar, per dot, per line segment. */
private class Reveal(bars: Int, dots: Int, segments: Int) {
    val bars = List(bars) { Animatable(0f) }
    val dots = List(dots) { Animatable(0f) }
    val segments = List(segments) { Animatable(0f) }

    suspend fun play(hasRating: (Int) -> Boolean) = coroutineScope {
        // iOS: bars 0.05 s apart over 0.4 s; a dot once its bar settles; then the line, segment by segment.
        val barJobs = bars.mapIndexed { index, bar ->
            async {
                delay(index * 50L)
                bar.animateTo(1f, tween(400, easing = EaseOut))
                delay(80)
                if (hasRating(index)) dots[index].animateTo(1f, tween(250, easing = EaseOut))
            }
        }
        barJobs.awaitAll()
        segments.forEachIndexed { index, segment ->
            async {
                delay(index * 150L)
                segment.animateTo(1f, tween(300, easing = EaseOut))
            }
        }
    }
}

@Composable
private fun EffortAndRatingChart(points: List<RatingChartPoint>, modifier: Modifier) {
    val colors = chartColors()
    val locale = currentLocale()
    val axis = remember(locale) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale) }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
    val hoursMax = points.maxOf { maxOf(it.seconds, it.plannedSeconds) } / 3600f
    val ratings = points.mapNotNull { it.rating }
    val low = ratings.minOrNull()
    val high = ratings.maxOrNull()
    val latest = points.lastOrNull { it.rating != null }?.rating
    val reveal = remember(points) { Reveal(points.size, points.size, maxOf(points.size - 1, 0)) }
    LaunchedEffect(reveal) { reveal.play { points[it].rating != null } }
    val spoken = spokenSummary(points)

    fun ratingY(rating: Int): Float {
        if (low == null || high == null || high == low) return (RATING_BOTTOM + RATING_TOP) / 2
        return RATING_BOTTOM + (rating - low).toFloat() / (high - low) * (RATING_TOP - RATING_BOTTOM)
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(Modifier.fillMaxWidth().height(200.dp).semantics { contentDescription = spoken }) {
            val left = 32.dp.toPx()
            val right = size.width - if (latest != null) 36.dp.toPx() else 0f
            val bottom = size.height - 18.dp.toPx()
            val slot = (right - left) / points.size
            fun x(index: Int) = left + slot * index + slot / 2
            fun y(fraction: Float) = bottom * (1 - fraction)
            fun barFraction(seconds: Int) = if (hoursMax > 0) seconds / 3600f / hoursMax * BAR_TOP else 0f
            val grid = Color.White.copy(alpha = 0.08f)

            // Left axis: 0h and the tallest week, as iOS does.
            val ticks = if (hoursMax > 0) listOf(0f, BAR_TOP) else listOf(0f)
            ticks.forEach { tick ->
                drawLine(grid, Offset(left, y(tick)), Offset(right, y(tick)), 1.dp.toPx())
                val label = measurer.measure(if (tick == 0f) "0h" else formatHours(hoursMax), labelStyle)
                drawText(label, topLeft = Offset(0f, y(tick) - label.size.height / 2f))
            }
            points.forEachIndexed { index, point ->
                val label = measurer.measure(point.weekStart.format(axis), labelStyle.copy(fontSize = 9.sp))
                drawText(label, topLeft = Offset(x(index) - label.size.width / 2f, bottom + 4.dp.toPx()))
                val barWidth = minOf(slot * 0.6f, 41.dp.toPx())
                val height = bottom * barFraction(point.seconds) * reveal.bars[index].value
                if (height > 0f) {
                    drawRoundRect(colors.hours, Offset(x(index) - barWidth / 2, bottom - height), Size(barWidth, height), CornerRadius(4.dp.toPx()))
                }
            }

            // Planned: dashed between weeks that had a plan, a dot on each.
            val planned = points.indices.filter { points[it].plannedSeconds > 0 }
            planned.zipWithNext().forEachIndexed { segment, (a, b) ->
                val alpha = reveal.segments.getOrNull(segment)?.value ?: 1f
                drawLine(
                    colors.planned.copy(alpha = alpha),
                    Offset(x(a), y(barFraction(points[a].plannedSeconds) * reveal.bars[a].value)),
                    Offset(x(b), y(barFraction(points[b].plannedSeconds) * reveal.bars[b].value)),
                    2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                )
            }
            planned.forEach { drawCircle(colors.planned, 3.dp.toPx(), Offset(x(it), y(barFraction(points[it].plannedSeconds) * reveal.bars[it].value))) }

            // Rating: gold, in its own band above the bars.
            val rated = points.indices.filter { points[it].rating != null }
            rated.zipWithNext().forEachIndexed { segment, (a, b) ->
                val alpha = reveal.segments.getOrNull(segment)?.value ?: 1f
                drawLine(colors.rating.copy(alpha = alpha), Offset(x(a), y(ratingY(points[a].rating!!))), Offset(x(b), y(ratingY(points[b].rating!!))), 2.dp.toPx())
            }
            rated.forEach { index ->
                val dot = reveal.dots[index].value
                if (dot > 0f) drawCircle(colors.rating.copy(alpha = dot), 3.5.dp.toPx() * dot, Offset(x(index), y(ratingY(points[index].rating!!))))
            }
            latest?.let { rating ->
                val label = measurer.measure(rating.toString(), labelStyle)
                drawText(label, topLeft = Offset(right + 6.dp.toPx(), y(ratingY(rating)) - label.size.height / 2f))
            }
        }
        Legend()
    }
}

/** A rating line over its own range: no hours to share the chart with. */
@Composable
private fun RatingOnlyChart(points: List<RatingChartPoint>, modifier: Modifier) {
    val colors = chartColors()
    val locale = currentLocale()
    val axis = remember(locale) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale) }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
    val ratings = points.map { it.rating!! }
    val low = ratings.min()
    val high = ratings.max()
    val reveal = remember(points) { Reveal(points.size, points.size, maxOf(points.size - 1, 0)) }
    LaunchedEffect(reveal) { reveal.play { true } }
    val spoken = spokenSummary(points)

    Canvas(modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = spoken }) {
        val left = 40.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val top = 12.dp.toPx()
        val slot = (size.width - left) / points.size
        fun x(index: Int) = left + slot * index + slot / 2
        fun y(rating: Int) = if (high == low) (top + bottom) / 2 else bottom - (rating - low).toFloat() / (high - low) * (bottom - top)
        val grid = Color.White.copy(alpha = 0.08f)
        listOf(low, high).distinct().forEach { rating ->
            drawLine(grid, Offset(left, y(rating)), Offset(size.width, y(rating)), 1.dp.toPx())
            val label = measurer.measure(rating.toString(), labelStyle)
            drawText(label, topLeft = Offset(0f, y(rating) - label.size.height / 2f))
        }
        points.forEachIndexed { index, point ->
            val label = measurer.measure(point.weekStart.format(axis), labelStyle.copy(fontSize = 9.sp))
            drawText(label, topLeft = Offset(x(index) - label.size.width / 2f, bottom + 4.dp.toPx()))
        }
        points.indices.zipWithNext().forEachIndexed { segment, (a, b) ->
            drawLine(colors.rating.copy(alpha = reveal.segments[segment].value), Offset(x(a), y(ratings[a])), Offset(x(b), y(ratings[b])), 2.dp.toPx())
        }
        points.indices.forEach { index ->
            val dot = reveal.dots[index].value
            if (dot > 0f) drawCircle(colors.rating.copy(alpha = dot), 3.5.dp.toPx() * dot, Offset(x(index), y(ratings[index])))
        }
    }
}

@Composable
private fun Legend() {
    val colors = chartColors()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
        LegendItem(stringResource(R.string.chart_hours)) { drawRoundRect(colors.hours, cornerRadius = CornerRadius(2.dp.toPx())) }
        LegendItem(stringResource(R.string.stat_rating)) { drawLine(colors.rating, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx()) }
        LegendItem(stringResource(R.string.planned)) {
            drawLine(colors.planned, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())))
        }
    }
}

@Composable
private fun LegendItem(label: String, swatch: DrawScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.width(12.dp).height(10.dp), onDraw = swatch)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Every week for TalkBack: its date, time studied and rating. */
@Composable
private fun spokenSummary(points: List<RatingChartPoint>): String {
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(currentLocale())
    val durations = points.map { durationText(it.seconds) }
    return points.mapIndexed { index, point ->
        listOfNotNull(point.weekStart.format(formatter), durations[index], point.rating?.toString()).joinToString(", ")
    }.joinToString("; ")
}

/** "3h", "1.5h": whole hours plain, else one decimal (iOS `formattedHours`). */
private fun formatHours(hours: Float): String =
    if (hours % 1f == 0f) "${hours.roundToInt()}h" else String.format(java.util.Locale.ROOT, "%.1fh", hours)
