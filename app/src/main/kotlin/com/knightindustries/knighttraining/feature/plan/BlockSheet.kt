package com.knightindustries.knighttraining.feature.plan

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.model.ActivityType
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.TimeControl
import com.knightindustries.knighttraining.ui.activityIcon
import com.knightindustries.knighttraining.ui.activityName

/**
 * Adds or edits one training block (iOS `TrainingBlockCategorySheet`): pick a category, then its
 * target, games with a time control. A block's category can't change once it exists.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BlockSheet(
    initial: DraftBlock?,
    onDismiss: () -> Unit,
    onSave: (DraftBlock) -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var code by rememberSaveable { mutableStateOf(initial?.activityTypeCode) }
    var targetType by rememberSaveable { mutableStateOf(initial?.targetType) }
    var count by rememberSaveable { mutableStateOf(initial?.takeIf { it.targetType == TargetType.COUNT }?.targetValue?.toString() ?: "") }
    var minutes by rememberSaveable { mutableStateOf(initial?.takeIf { it.targetType == TargetType.DURATION }?.let { (it.targetValue / 60).toString() } ?: "30") }
    var timeControl by rememberSaveable { mutableStateOf(initial?.timeControlLabel) }
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }

    fun pick(newCode: String) {
        code = newCode
        targetType = ActivityType.targetTypes(newCode).first()
        count = when (newCode) {
            ActivityType.PUZZLES -> "50"
            ActivityType.PLAYING -> "2"
            else -> ""
        }
    }

    val value: Int? = when (targetType) {
        TargetType.COUNT -> count.toIntOrNull()?.takeIf { it > 0 }
        TargetType.DURATION -> minutes.toIntOrNull()?.takeIf { it > 0 }?.times(60)
        else -> null
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(if (initial == null) R.string.block_title_new else R.string.block_title_edit),
                style = MaterialTheme.typography.headlineSmall,
            )

            val current = code
            if (current == null) {
                ActivityGrid(onPick = ::pick)
                return@Column
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(activityIcon(current), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(activityName(current), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (initial == null) TextButton(onClick = { code = null }) { Text(stringResource(R.string.change)) }
            }

            val options = ActivityType.targetTypes(current)
            if (options.size > 1) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = targetType == option,
                            onClick = { targetType = option },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        ) {
                            Text(stringResource(if (option == TargetType.COUNT) R.string.block_target_quantity else R.string.block_target_time))
                        }
                    }
                }
            }

            when (targetType) {
                TargetType.COUNT -> OutlinedTextField(
                    value = count,
                    onValueChange = { count = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(if (current == ActivityType.PLAYING) R.string.block_how_many_games else R.string.block_how_many_puzzles)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TargetType.DURATION -> MinutesField(minutes) { minutes = it }
                else -> Unit
            }

            if (current == ActivityType.PLAYING) {
                TimeControlPicker(timeControl) { timeControl = it }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.block_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                if (onRemove != null) TextButton(onClick = onRemove) { Text(stringResource(R.string.remove_goal)) }
                Button(
                    enabled = value != null && targetType != null,
                    onClick = {
                        onSave(
                            DraftBlock(
                                id = initial?.id ?: java.util.UUID.randomUUID(),
                                name = name.trim().ifEmpty { null },
                                activityTypeCode = current,
                                targetType = targetType!!,
                                targetValue = value!!,
                                timeControlLabel = timeControl.takeIf { current == ActivityType.PLAYING },
                            ),
                        )
                    },
                ) { Text(stringResource(if (initial == null) R.string.add else R.string.save)) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** The pickable categories; `general` is a fallback, never a choice. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityGrid(codes: List<String> = ActivityType.pickableCodes, onPick: (String) -> Unit) {
    val sorted = codes.map { it to activityName(it) }.sortedBy { it.second }
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        maxItemsInEachRow = 2,
    ) {
        sorted.forEach { (code, title) ->
            OutlinedCard(onClick = { onPick(code) }, modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(activityIcon(code), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Text(title, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MinutesField(minutes: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 45, 60, 90, 120).forEach { preset ->
                FilterChip(
                    selected = minutes == preset.toString(),
                    onClick = { onChange(preset.toString()) },
                    label = { Text(stringResource(R.string.duration_minutes, preset)) },
                )
            }
        }
        OutlinedTextField(
            value = minutes,
            onValueChange = { onChange(it.filter(Char::isDigit).take(4)) },
            label = { Text(stringResource(R.string.block_minutes)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Sections headed by category, so a game counts as the category the user picked it under. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimeControlPicker(selected: String?, onSelect: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.block_time_control), style = MaterialTheme.typography.labelLarge)
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(stringResource(R.string.block_time_control_none)) })
        PlanPreset.timeControlSections.forEach { (heading, labels) ->
            Text(stringResource(heading), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                labels.forEach { label ->
                    FilterChip(selected = selected == label, onClick = { onSelect(label) }, label = { Text(label) })
                }
            }
        }
        if (selected?.let(TimeControl::parse)?.isClassical == true) {
            Text(stringResource(R.string.classical_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
