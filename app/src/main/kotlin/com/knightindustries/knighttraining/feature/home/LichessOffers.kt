package com.knightindustries.knighttraining.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.model.LichessActivityBatch
import com.knightindustries.knighttraining.core.model.TimeControl
import com.knightindustries.knighttraining.feature.plan.MinutesField
import com.knightindustries.knighttraining.ui.activityName

/** The default for Streak, which records no time at all (iOS `defaultStreakMinutes`). */
private const val DEFAULT_STREAK_MINUTES = 10

@Composable
private fun offerTitle(batch: LichessActivityBatch): String = when (batch.kind) {
    LichessActivityBatch.STREAK -> stringResource(R.string.lichess_streak_detected)
    LichessActivityBatch.STORM -> stringResource(R.string.lichess_storm_detected)
    LichessActivityBatch.PUZZLE -> stringResource(R.string.lichess_puzzles_detected)
    else -> when (batch.timeControlCategory) {
        TimeControl.Category.CLASSICAL -> pluralStringResource(R.plurals.lichess_classical_detected, batch.itemCount)
        TimeControl.Category.BLITZ -> pluralStringResource(R.plurals.lichess_blitz_detected, batch.itemCount)
        else -> pluralStringResource(R.plurals.lichess_rapid_detected, batch.itemCount)
    }
}

/** "3+2 · 4 games". Nothing for Streak: its runs only trigger the offer and read like a puzzle count. */
@Composable
private fun offerSubtitle(batch: LichessActivityBatch): String? {
    val count = when (batch.kind) {
        LichessActivityBatch.STREAK -> null
        LichessActivityBatch.STORM -> pluralStringResource(R.plurals.storm_runs, batch.itemCount, batch.itemCount)
        LichessActivityBatch.PUZZLE -> pluralStringResource(R.plurals.target_puzzles, batch.itemCount, batch.itemCount)
        else -> pluralStringResource(R.plurals.target_games, batch.itemCount, batch.itemCount)
    }
    val timeControl = if (batch.isGame) batch.timeControlLabel else null
    return listOfNotNull(timeControl, count).joinToString(" · ").ifEmpty { null }
}

/** "We noticed…": the oldest pending Lichess offer, above the plan card. */
@Composable
fun LichessOfferBanner(batch: LichessActivityBatch, onTrack: () -> Unit, onDismiss: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(Icons.Rounded.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            headlineContent = { Text(offerTitle(batch)) },
            supportingContent = offerSubtitle(batch)?.let { { Text(it) } },
            trailingContent = {
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.dismiss)) }
            },
        )
        TextButton(onClick = onTrack, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)) {
            Text(stringResource(R.string.add_to_history))
        }
    }
}

/**
 * Confirms an offer before it's logged: the duration is reviewable (a real estimate for puzzles,
 * absent for Streak), and Streak may add how many puzzles it solved, pre-filled from a best score
 * the new runs set.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackLichessSheet(batch: LichessActivityBatch, onDismiss: () -> Unit, onAdd: (durationSeconds: Int, puzzleCount: Int?) -> Unit) {
    val initialMinutes = if (batch.isStreak) DEFAULT_STREAK_MINUTES else maxOf(1, (batch.totalDurationSeconds + 30) / 60)
    var minutes by rememberSaveable { mutableStateOf(initialMinutes.toString()) }
    var puzzles by rememberSaveable { mutableStateOf(batch.bestScore?.toString().orEmpty()) }
    val minuteValue = minutes.toIntOrNull()?.takeIf { it > 0 }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 24.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.track_title), style = MaterialTheme.typography.titleLarge)
            SummaryRow(stringResource(R.string.track_activity), activityName(batch.activityTypeCode))
            batch.timeControlLabel?.takeIf { batch.isGame }?.let { SummaryRow(stringResource(R.string.track_time_control), it) }
            if (!batch.isStreak) SummaryRow(stringResource(R.string.track_quantity), batch.itemCount.toString())
            MinutesField(minutes, label = stringResource(R.string.track_duration)) { minutes = it }
            if (batch.isStreak) {
                OutlinedTextField(
                    value = puzzles,
                    onValueChange = { value -> puzzles = value.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.track_puzzles_solved)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(
                onClick = { onAdd(minuteValue!! * 60, puzzles.toIntOrNull()) },
                enabled = minuteValue != null,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) {
                Text(stringResource(R.string.add_to_history))
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}
