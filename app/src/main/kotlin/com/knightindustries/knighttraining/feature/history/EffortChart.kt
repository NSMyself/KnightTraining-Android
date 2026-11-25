package com.knightindustries.knighttraining.feature.history

import com.knightindustries.knighttraining.designsystem.Palette
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.store.WeeklyEffort
import com.knightindustries.knighttraining.designsystem.Theme
import com.knightindustries.knighttraining.feedback.FeedbackEvent
import com.knightindustries.knighttraining.feedback.rememberFeedback
import com.knightindustries.knighttraining.ui.currentLocale
import com.knightindustries.knighttraining.ui.durationText
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.roundToInt

// iOS Training History: planned in the secondary text color, actual in the accent.
private val PlannedColor @Composable get() = Palette.textSecondary
private val ActualColor @Composable get() = Palette.accent

/** The two series' keys: planned dashed, as on the chart. */
@Composable
fun ChartLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        LegendItem(PlannedColor, dashed = true, stringResource(R.string.planned))
        LegendItem(ActualColor, dashed = false, stringResource(R.string.actual))
    }
}

@Composable
private fun LegendItem(color: Color, dashed: Boolean, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Canvas(Modifier.width(20.dp).height(8.dp)) {
            drawLine(
                color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2.dp.toPx(),
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null,
            )
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Planned against actual minutes per week, a line each (iOS's Swift Chart). Press and drag to
 * read a week, as in Stocks or Health: the callout follows the finger and goes on release, a
 * look-up rather than a selection. TalkBack reads every week from the chart's description.
 */
@Composable
fun EffortChart(weeks: List<WeeklyEffort>) {
    val plannedColor = PlannedColor
    val actualColor = ActualColor
    // iOS marks the selected week with a rule in the border color.
    val selectionRule = Palette.border
    val locale = currentLocale()
    val axisFormat = remember(locale) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "Md"), locale) }
    val measurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val feedback = rememberFeedback()
    var selected by remember { mutableStateOf<Int?>(null) }

    val maxMinutes = weeks.maxOfOrNull { maxOf(it.plannedSeconds, it.actualSeconds) / 60f } ?: 0f
    val step = niceStep(maxMinutes / 4f)
    val top = maxOf(step * ceil(maxMinutes / step), step)

    val plannedText = weeks.map { stringResource(R.string.planned_value, durationText(it.plannedSeconds)) }
    val actualText = weeks.map { stringResource(R.string.actual_value, durationText(it.actualSeconds)) }
    val spoken = weeks.indices.joinToString("; ") { "${axisFormat.format(weeks[it].weekStart)}: ${plannedText[it]}, ${actualText[it]}" }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val width = constraints.maxWidth.toFloat()
        // Room on the right for the minute labels, below for the dates (Swift Charts' layout).
        val plotRight = width - with(density) { 36.dp.toPx() }
        val xAt = { index: Int -> if (weeks.size < 2) plotRight / 2 else plotRight * index / (weeks.size - 1) }

        Canvas(
            Modifier.fillMaxWidth().height(240.dp)
                .semantics { contentDescription = spoken }
                .pointerInput(weeks) {
                    awaitEachGesture {
                        fun pick(x: Float) {
                            val index = weeks.indices.minByOrNull { kotlin.math.abs(xAt(it) - x) } ?: return
                            if (index != selected) {
                                selected = index
                                feedback(FeedbackEvent.SelectionChanged)
                            }
                        }
                        pick(awaitFirstDown(requireUnconsumed = false).position.x)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            pick(change.position.x)
                        }
                        selected = null
                    }
                },
        ) {
            val plotBottom = size.height - 20.dp.toPx()
            val yAt = { minutes: Float -> plotBottom * (1 - minutes / top) }
            val grid = Color.White.copy(alpha = 0.08f)

            // Horizontal gridlines with their minutes; 0 is implied by the baseline.
            var tick = 0f
            while (tick <= top + 0.01f) {
                val y = yAt(tick)
                drawLine(grid, Offset(0f, y), Offset(plotRight, y), strokeWidth = 1.dp.toPx())
                if (tick > 0f) {
                    val label = measurer.measure("${tick.roundToInt()}m", labelStyle)
                    drawText(label, topLeft = Offset(plotRight + 6.dp.toPx(), y - label.size.height / 2f))
                }
                tick += step
            }
            // A vertical gridline and date per week; the end dates align to their own outer edge.
            weeks.forEachIndexed { index, week ->
                val x = xAt(index)
                drawLine(grid, Offset(x, 0f), Offset(x, plotBottom), strokeWidth = 1.dp.toPx())
                val label = measurer.measure(axisFormat.format(week.weekStart), labelStyle)
                val left = when (index) {
                    0 -> x
                    weeks.lastIndex -> x - label.size.width
                    else -> x - label.size.width / 2f
                }
                drawText(label, topLeft = Offset(left, plotBottom + 6.dp.toPx()))
            }
            selected?.let { drawLine(selectionRule, Offset(xAt(it), 0f), Offset(xAt(it), plotBottom), strokeWidth = 1.dp.toPx()) }

            fun series(minutes: List<Float>, color: Color, width: Float, dashed: Boolean) {
                val path = Path()
                minutes.forEachIndexed { index, value ->
                    val point = Offset(xAt(index), yAt(value))
                    if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                }
                drawPath(
                    path, color,
                    style = Stroke(width, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())) else null),
                )
                minutes.forEachIndexed { index, value -> drawCircle(color, 4.dp.toPx(), Offset(xAt(index), yAt(value))) }
            }
            series(weeks.map { it.plannedSeconds / 60f }, plannedColor, 2.dp.toPx(), dashed = true)
            series(weeks.map { it.actualSeconds / 60f }, actualColor, 2.5.dp.toPx(), dashed = false)
        }

        // Drawn over the chart, outside its layout, so a selection never resizes the plot.
        selected?.let { index ->
            val calloutWidth = with(density) { 140.dp.toPx() }
            val x = (xAt(index) - calloutWidth / 2).coerceIn(0f, maxOf(width - calloutWidth, 0f))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.width(140.dp).offset { IntOffset(x.roundToInt(), 0) },
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(axisFormat.format(weeks[index].weekStart), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(plannedText[index], style = MaterialTheme.typography.labelLarge, color = PlannedColor)
                    Text(actualText[index], style = MaterialTheme.typography.labelLarge, color = ActualColor)
                }
            }
        }
    }
}

/** A round gridline step: 5, 10, 15, 30 minutes, then whole hours, doubling. */
private fun niceStep(raw: Float): Float {
    val steps = listOf(5f, 10f, 15f, 30f, 60f, 120f, 240f, 480f, 960f)
    return steps.firstOrNull { it >= raw } ?: (ceil(raw / 960f) * 960f)
}
