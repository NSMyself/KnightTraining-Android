package com.knightindustries.knighttraining.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.feature.plan.BlockSheet
import com.knightindustries.knighttraining.feature.plan.DraftBlock
import com.knightindustries.knighttraining.ui.goalTitle
import com.knightindustries.knighttraining.ui.progressText

/**
 * The mid-week stretch (iOS `AddMoreGoalSheet`), offered on a completed goal (Home) or on every
 * goal completed this week (the plan editor). Both ways are upward only and never a penalty: a
 * raised target keeps the goal's completion, and a bonus goal never counts toward "is this week
 * done".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMoreSheet(goals: List<WeeklyGoal>, onDismiss: () -> Unit, onRaise: (WeeklyGoal, Int) -> Unit, onAddBonus: (DraftBlock) -> Unit) {
    var step by rememberSaveable { mutableStateOf("choose") }
    // Stacking an extra on an extra has no clear meaning, so bonus goals can't be raised.
    val raisable = goals.filter { !it.isBonus }
    var raisingId by rememberSaveable { mutableStateOf(raisable.singleOrNull()?.id?.toString()) }
    val raising = raisable.firstOrNull { it.id.toString() == raisingId }

    if (step == "bonus") {
        BlockSheet(initial = null, onDismiss = onDismiss, onSave = onAddBonus)
        return
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.add_more_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.add_more_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (step == "raise" && raising != null) {
                RaiseTarget(raising) { onRaise(raising, it) }
            } else if (step == "raise") {
                // Several goals done this week: which one to stretch.
                raisable.forEach { goal ->
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(goalTitle(goal)) },
                        supportingContent = { Text(progressText(goal)) },
                        modifier = Modifier.selectable(selected = false, onClick = { raisingId = goal.id.toString() }, role = Role.Button),
                    )
                }
            } else {
                if (raisable.isNotEmpty()) Choice(Icons.Rounded.TrendingUp, R.string.raise_target, R.string.raise_target_body) { step = "raise" }
                Choice(Icons.Rounded.Add, R.string.add_different_activity, R.string.add_different_activity_body) { step = "bonus" }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Choice(icon: androidx.compose.ui.graphics.vector.ImageVector, title: Int, subtitle: Int, onClick: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(subtitle)) },
        modifier = Modifier.selectable(selected = false, onClick = onClick, role = Role.Button),
    )
}

/** The new target must pass what's already been done; duration targets are entered in minutes. */
@Composable
private fun RaiseTarget(goal: WeeklyGoal, onRaise: (Int) -> Unit) {
    val isDuration = goal.targetType == TargetType.DURATION
    var text by rememberSaveable { mutableStateOf("") }
    val value = text.toIntOrNull()?.let { if (isDuration) it * 60 else it }
    val valid = value != null && value > goal.currentValue

    Text(goalTitle(goal), style = MaterialTheme.typography.titleMedium)
    Text(progressText(goal), color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.filter(Char::isDigit).take(4) },
        label = { Text(stringResource(if (isDuration) R.string.duration_minutes_label else R.string.new_target)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = text.isNotEmpty() && !valid,
        supportingText = { if (text.isNotEmpty() && !valid) Text(stringResource(R.string.new_target_too_low)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(enabled = valid, onClick = { onRaise(value!!) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_new_target)) }
}
