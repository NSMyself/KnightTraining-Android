package com.knightindustries.knighttraining.feature.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.model.Activity
import com.knightindustries.knighttraining.core.model.ActivityType
import com.knightindustries.knighttraining.core.model.CoachingTopic
import com.knightindustries.knighttraining.core.model.WeekCalendar
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.feature.plan.ActivityGrid
import com.knightindustries.knighttraining.feature.plan.MinutesField
import com.knightindustries.knighttraining.feature.plan.TimeControlPicker
import com.knightindustries.knighttraining.feature.session.topicName
import com.knightindustries.knighttraining.ui.activityIcon
import com.knightindustries.knighttraining.ui.activityName
import com.knightindustries.knighttraining.ui.goalTitle
import com.knightindustries.knighttraining.ui.progressText
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

/**
 * Logs a session that wasn't timed, or corrects one that was (iOS `LogPastSessionSheet` and
 * `EditActivitySheet`): which day and when, how long, how many, and which goal it counts toward.
 * Days are limited to this week so far: only the current week is editable.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SessionEditorSheet(
    existing: Activity?,
    weekStart: LocalDate,
    weekEnd: LocalDate,
    candidates: (code: String, timeControl: String?) -> List<WeeklyGoal>,
    onDismiss: () -> Unit,
    onSave: (SessionDraft) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val calendar = remember { WeekCalendar.system() }
    val today = remember { LocalDate.now(calendar.zone) }
    val startedAt = existing?.startedAt?.atZone(calendar.zone)

    var code by rememberSaveable { mutableStateOf(existing?.activityType?.code) }
    var day by rememberSaveable { mutableStateOf(startedAt?.toLocalDate() ?: today) }
    // A new past session defaults to having started half an hour ago, never in the future.
    val defaultStart = remember { LocalTime.now(calendar.zone).minusMinutes(30) }
    val time = rememberTimePickerState(startedAt?.hour ?: defaultStart.hour, startedAt?.minute ?: defaultStart.minute)
    var minutes by rememberSaveable { mutableStateOf(existing?.let { (it.duration / 60).toString() } ?: "30") }
    var count by rememberSaveable { mutableStateOf(existing?.count?.toString() ?: "") }
    var timeControl by rememberSaveable { mutableStateOf(existing?.timeControlLabel) }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    val topics = remember { mutableStateListOf<CoachingTopic>().apply { existing?.coachingTopics?.let(::addAll) } }
    // Picked goal; for a new session, the only candidate fills itself in.
    var goalId by rememberSaveable { mutableStateOf(existing?.weeklyGoalId?.toString()) }
    var goalTouched by rememberSaveable { mutableStateOf(existing != null) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(if (existing == null) R.string.log_past_session else R.string.edit_session), style = MaterialTheme.typography.headlineSmall)
            val current = code
            if (current == null) {
                Text(stringResource(R.string.log_past_session_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                ActivityGrid { code = it }
                Spacer(Modifier.height(16.dp))
                return@Column
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(activityIcon(current), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(activityName(current), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { code = null; goalTouched = false; goalId = null }) { Text(stringResource(R.string.change)) }
            }

            Text(stringResource(R.string.session_date), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                generateSequence(weekStart) { it.plusDays(1) }
                    .takeWhile { it < weekEnd && !it.isAfter(today) }
                    .forEach { date ->
                        FilterChip(
                            selected = date == day,
                            onClick = { day = date },
                            label = { Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + date.dayOfMonth) },
                        )
                    }
            }
            Text(stringResource(R.string.session_time), style = MaterialTheme.typography.labelLarge)
            TimeInput(time)

            MinutesField(minutes, stringResource(R.string.duration_minutes_label)) { minutes = it }
            if (ActivityType.hasQuantityUnit(current)) {
                OutlinedTextField(
                    value = count,
                    onValueChange = { count = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.how_many_completed)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (current == ActivityType.PLAYING) TimeControlPicker(timeControl) { timeControl = it }
            if (current == ActivityType.COACHING) {
                Text(stringResource(R.string.coaching_topics), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CoachingTopic.entries.forEach { topic ->
                        FilterChip(selected = topic in topics, onClick = { if (topic in topics) topics.remove(topic) else topics.add(topic) }, label = { Text(stringResource(topicName(topic))) })
                    }
                }
            }
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.notes)) }, modifier = Modifier.fillMaxWidth())

            val goals = candidates(current, timeControl)
            val chosen = if (goalTouched) goalId else goals.singleOrNull()?.id?.toString()
            if (goals.isNotEmpty()) {
                Text(stringResource(R.string.counts_toward), style = MaterialTheme.typography.labelLarge)
                goals.forEach { goal ->
                    GoalChoice(goalTitle(goal), progressText(goal), chosen == goal.id.toString()) { goalTouched = true; goalId = goal.id.toString() }
                }
                GoalChoice(stringResource(R.string.not_counted), null, chosen == null) { goalTouched = true; goalId = null }
            }

            val seconds = minutes.toIntOrNull()?.takeIf { it > 0 }?.times(60)
            Button(
                enabled = seconds != null,
                onClick = {
                    val start = ZonedDateTime.of(day, LocalTime.of(time.hour, time.minute), calendar.zone).toInstant()
                    onSave(
                        SessionDraft(
                            activityTypeCode = current, startedAt = start, durationSeconds = seconds!!,
                            count = count.toIntOrNull(), timeControlLabel = timeControl, notes = notes.trim().ifEmpty { null },
                            topics = topics.toList(), goalId = chosen?.let(UUID::fromString),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save)) }
            if (onDelete != null) {
                OutlinedButton(
                    onClick = { confirmingDelete = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.delete_session)) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmingDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.delete_session_title)) },
            text = { Text(stringResource(R.string.delete_session_body)) },
            confirmButton = { TextButton(onClick = { confirmingDelete = false; onDelete() }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun GoalChoice(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    )
}
