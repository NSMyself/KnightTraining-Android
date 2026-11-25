package com.knightindustries.knighttraining.feature.history

import com.knightindustries.knighttraining.designsystem.PaletteColors
import com.knightindustries.knighttraining.designsystem.Palette
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.model.DailyTrainingIntensity
import com.knightindustries.knighttraining.feature.history.TrainingHistory.Companion.GRID_COLUMNS
import com.knightindustries.knighttraining.feature.history.TrainingHistory.Companion.GRID_DAYS
import com.knightindustries.knighttraining.feature.history.TrainingHistory.Companion.GRID_ROWS
import com.knightindustries.knighttraining.feedback.FeedbackEvent
import com.knightindustries.knighttraining.feedback.rememberFeedback
import com.knightindustries.knighttraining.ui.currentLocale
import com.knightindustries.knighttraining.ui.durationText
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/**
 * The day's step on the heatmap (iOS `DailyTrainingIntensity.color`): three levels on a six-step
 * ramp, steps skipped so each reads apart, and only an intense day reaching the highlight end. An
 * empty day is the ramp's first step, a faint blue rather than a gray.
 */
internal fun DailyTrainingIntensity?.color(palette: PaletteColors): Color = palette.heatmap[
    when (this) {
        null -> 0
        DailyTrainingIntensity.LIGHT -> 2
        DailyTrainingIntensity.MODERATE -> 4
        DailyTrainingIntensity.INTENSE -> 5
    },
]

private const val TAP_LABEL_MILLIS = 2_500L

/**
 * The last 36 days as 4 rows of 9 (iOS `RecentActivityGridView`), oldest top-left, today
 * bottom-right, each tinted by that day's tracked time. Tap a day for its date and time, or press
 * and hold and drag across days; the cells pop in column by column. Each cell carries its own
 * description, so TalkBack reads a day at a time.
 */
@Composable
fun ActivityGrid(dailySeconds: Map<LocalDate, Int>, today: LocalDate) {
    val locale = currentLocale()
    val dateFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale) }
    val feedback = rememberFeedback()
    var selected by remember { mutableStateOf<Int?>(null) }
    // Bumped by a tap, so its label hides itself after a moment; a hold hides on release instead.
    var tapCount by remember { mutableStateOf(0) }
    LaunchedEffect(tapCount) {
        if (tapCount == 0) return@LaunchedEffect
        delay(TAP_LABEL_MILLIS)
        selected = null
    }
    val noTraining = stringResource(R.string.no_training)
    fun dateAt(index: Int): LocalDate = today.minusDays((GRID_DAYS - 1 - index).toLong())
    val labels = (0 until GRID_DAYS).map { index ->
        val seconds = dailySeconds[dateAt(index)] ?: 0
        if (seconds >= 60) durationText(seconds) else noTraining
    }
    fun spoken(index: Int): String = dateFormat.format(dateAt(index)) + " " + labels[index]

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val spacing = 4.dp
        val cell = (maxWidth - spacing * (GRID_COLUMNS - 1)) / GRID_COLUMNS
        val density = LocalDensity.current
        val pitchPx = with(density) { (cell + spacing).toPx() }
        fun indexAt(position: Offset): Int {
            val column = (position.x / pitchPx).toInt().coerceIn(0, GRID_COLUMNS - 1)
            val row = (position.y / pitchPx).toInt().coerceIn(0, GRID_ROWS - 1)
            return row * GRID_COLUMNS + column
        }
        fun select(index: Int?) {
            if (index != null && index != selected) feedback(FeedbackEvent.SelectionChanged)
            selected = index
        }

        Column(
            Modifier
                .pointerInput(pitchPx) {
                    detectTapGestures { position ->
                        val index = indexAt(position)
                        if (index == selected) select(null) else { select(index); tapCount++ }
                    }
                }
                .pointerInput(pitchPx) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { select(indexAt(it)) },
                        onDrag = { change, _ -> select(indexAt(change.position)) },
                        onDragEnd = { select(null) },
                        onDragCancel = { select(null) },
                    )
                },
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            for (row in 0 until GRID_ROWS) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    for (column in 0 until GRID_COLUMNS) {
                        val index = row * GRID_COLUMNS + column
                        val minutes = (dailySeconds[dateAt(index)] ?: 0) / 60
                        GridCell(
                            DailyTrainingIntensity.of(minutes).color(Palette.current),
                            isSelected = index == selected,
                            delayMillis = column * 70L,
                            description = spoken(index),
                            modifier = Modifier.size(cell),
                        )
                    }
                }
            }
        }

        selected?.let { index ->
            var labelWidth by remember { mutableStateOf(0) }
            val row = index / GRID_COLUMNS
            val column = index % GRID_COLUMNS
            val center = column * pitchPx + with(density) { cell.toPx() } / 2
            val gridWidth = with(density) { maxWidth.toPx() }
            val x = (center - labelWidth / 2f).coerceIn(0f, maxOf(gridWidth - labelWidth, 0f))
            val y = row * pitchPx - with(density) { 36.dp.toPx() }
            Surface(
                shape = CircleShape,
                color = Palette.accent,
                modifier = Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }.onSizeChanged { labelWidth = it.width },
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(dateFormat.format(dateAt(index)), style = MaterialTheme.typography.labelLarge, color = Palette.onAccent.copy(alpha = 0.8f))
                    Text(labels[index], style = MaterialTheme.typography.labelLarge, color = Palette.onAccent)
                }
            }
        }
    }
}

@Composable
private fun GridCell(color: Color, isSelected: Boolean, delayMillis: Long, description: String, modifier: Modifier) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMillis)
        appear.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
    }
    val shape = RoundedCornerShape(percent = 15)
    val outline = if (isSelected) Palette.textPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    Box(
        modifier
            .graphicsLayer {
                val scale = 0.5f + 0.5f * appear.value
                scaleX = scale
                scaleY = scale
                alpha = appear.value.coerceIn(0f, 1f)
            }
            .background(color, shape)
            .border(if (isSelected) 1.5.dp else 0.5.dp, outline, shape)
            .semantics { contentDescription = description },
    )
}
