package com.knightindustries.knighttraining.feature.profile

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.ui.currentLocale
import com.knightindustries.knighttraining.ui.durationText
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** One week of the Profile chart: time studied, and the rating as it stood at the week's end. */
data class RatingChartPoint(val weekStart: LocalDate, val seconds: Int, val rating: Int?)

/**
 * Hours studied as bars, the rating as a line over them (iOS `EloHistoryChart`): the point is to
 * see effort and rating side by side, not to read exact values, which the caption gives. A week
 * with no rating leaves a gap in the line rather than a guess.
 */
@Composable
fun RatingChart(points: List<RatingChartPoint>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val barColor = MaterialTheme.colorScheme.surfaceVariant
    val lineColor = MaterialTheme.colorScheme.primary
    val maxSeconds = points.maxOf { it.seconds }.coerceAtLeast(1)
    val ratings = points.mapNotNull { it.rating }
    val low = ratings.minOrNull()
    val high = ratings.maxOrNull()
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(currentLocale())
    val durations = points.map { durationText(it.seconds) }
    val spoken = points.mapIndexed { index, point ->
        listOfNotNull(point.weekStart.format(formatter), durations[index], point.rating?.toString()).joinToString(", ")
    }.joinToString("; ")

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = spoken }) {
            val slot = size.width / points.size
            val barWidth = slot * 0.5f
            val chartTop = 12.dp.toPx()
            val chartHeight = size.height - chartTop
            points.forEachIndexed { index, point ->
                val height = (point.seconds.toFloat() / maxSeconds) * chartHeight * 0.85f
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(slot * index + (slot - barWidth) / 2, size.height - height),
                    size = Size(barWidth, height.coerceAtLeast(2f)),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                )
            }
            if (low != null && high != null) {
                val span = (high - low).coerceAtLeast(1)
                fun y(rating: Int) = chartTop + (1 - (rating - low).toFloat() / span) * chartHeight * 0.7f
                val centers = points.mapIndexed { index, point -> point.rating?.let { Offset(slot * index + slot / 2, if (high == low) chartTop + chartHeight * 0.35f else y(it)) } }
                centers.zipWithNext().forEach { (a, b) -> if (a != null && b != null) drawLine(lineColor, a, b, strokeWidth = 3.dp.toPx()) }
                centers.filterNotNull().forEach {
                    drawCircle(lineColor, radius = 4.dp.toPx(), center = it)
                    drawCircle(barColor, radius = 4.dp.toPx(), center = it, style = Stroke(width = 1.5.dp.toPx()))
                }
            }
        }
        // The locale's own day-and-month order, never a literal pattern (iOS AGENTS.md §14 rule 5).
        val axis = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(currentLocale(), "dM"), currentLocale())
        Row(Modifier.fillMaxWidth()) {
            points.forEach { point ->
                Text(
                    point.weekStart.format(axis),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
            }
        }
        Text(stringResource(R.string.rating_chart_caption), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
