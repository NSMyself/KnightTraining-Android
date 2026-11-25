package com.knightindustries.knighttraining.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.model.ActivityReminder
import com.knightindustries.knighttraining.core.model.ReminderSchedule
import com.knightindustries.knighttraining.ui.currentLocale
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.UUID

/**
 * A goal's own reminders, in its details sheet (iOS `ActivityDetailsSheet`'s "Reminders"): each
 * row opens its editor, + adds one. Bonus goals have no block to hang a reminder on, so + is off.
 */
@Composable
fun GoalRemindersSection(goalTemplateId: UUID?, reminders: List<ActivityReminder>, onSave: (ActivityReminder) -> Unit, onDelete: (ActivityReminder) -> Unit) {
    var editing by remember { mutableStateOf<ActivityReminder?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.reminders), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(
                enabled = goalTemplateId != null,
                onClick = { goalTemplateId?.let { editing = ActivityReminder(goalTemplateId = it, schedule = ActivityReminder.NEW_SCHEDULE) } },
            ) { Icon(Icons.Rounded.AddCircle, contentDescription = stringResource(R.string.reminder_add), tint = MaterialTheme.colorScheme.primary) }
        }
        if (reminders.isEmpty()) {
            Text(stringResource(R.string.reminders_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        reminders.forEach { reminder ->
            Card(onClick = { editing = reminder }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(weekdaysSummary(reminder.schedule)) },
                    supportingContent = { Text(timeSummary(reminder.schedule)) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (!reminder.schedule.isEnabled) {
                                Text(stringResource(R.string.reminder_paused), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                        }
                    },
                )
            }
        }
    }
    editing?.let { reminder ->
        val isNew = reminders.none { it.id == reminder.id }
        ReminderEditorSheet(
            reminder = reminder,
            isNew = isNew,
            onDismiss = { editing = null },
            onSave = { onSave(it); editing = null },
            onDelete = if (isNew) null else ({ onDelete(reminder); editing = null }),
        )
    }
}

/** iOS `ReminderEditorSheet`: on or paused, which days, what time. Saving needs at least one day. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReminderEditorSheet(
    reminder: ActivityReminder,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (ActivityReminder) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var enabled by rememberSaveable { mutableStateOf(reminder.schedule.isEnabled) }
    var days by rememberSaveable { mutableStateOf(reminder.schedule.weekdays.map { it.value }) }
    var hour by rememberSaveable { mutableStateOf(reminder.schedule.hour) }
    var minute by rememberSaveable { mutableStateOf(reminder.schedule.minute) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    val schedule = ReminderSchedule(enabled, days.map(DayOfWeek::of).toSet(), hour, minute)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (isNew) R.string.reminder_new else R.string.reminder_edit), style = MaterialTheme.typography.headlineSmall)
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(R.string.reminder_enabled)) },
                trailingContent = { Switch(checked = enabled, onCheckedChange = null) },
                modifier = Modifier.toggleable(value = enabled, role = Role.Switch, onValueChange = { enabled = it }),
            )
            Text(stringResource(R.string.reminder_on_days), style = MaterialTheme.typography.labelLarge)
            // Monday first, labelled in the user's language, like Profile's weekly reminders.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = day.value in days,
                        onClick = { days = if (day.value in days) days - day.value else days + day.value },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, currentLocale())) },
                    )
                }
            }
            if (days.isEmpty()) {
                Text(stringResource(R.string.reminder_pick_a_day), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(R.string.reminder_time)) },
                trailingContent = { Text(timeSummary(schedule)) },
                modifier = Modifier.clickable { pickingTime = true },
            )
            Button(
                enabled = days.isNotEmpty(),
                onClick = { onSave(reminder.copy(schedule = schedule)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save)) }
            onDelete?.let {
                TextButton(
                    onClick = it,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.reminder_delete)) }
            }
        }
    }
    if (pickingTime) {
        val state = rememberTimePickerState(hour, minute)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            text = { TimePicker(state) },
            confirmButton = { TextButton(onClick = { hour = state.hour; minute = state.minute; pickingTime = false }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** The days, Monday first, in the user's language: "Mon, Wed, Fri". */
@Composable
private fun weekdaysSummary(schedule: ReminderSchedule): String {
    val locale = currentLocale()
    return DayOfWeek.entries.filter { it in schedule.weekdays }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
}

/** "7:00 PM" or "19:00", by locale; never hand-built. */
@Composable
private fun timeSummary(schedule: ReminderSchedule): String =
    LocalTime.of(schedule.hour, schedule.minute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(currentLocale()))
