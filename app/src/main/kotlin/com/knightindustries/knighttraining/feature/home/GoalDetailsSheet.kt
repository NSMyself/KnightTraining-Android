package com.knightindustries.knighttraining.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.ui.AnimatedLinearProgress
import com.knightindustries.knighttraining.ui.AnimatedValueText
import com.knightindustries.knighttraining.ui.activityIcon
import com.knightindustries.knighttraining.ui.goalTitle
import com.knightindustries.knighttraining.ui.progressText
import com.knightindustries.knighttraining.ui.targetText
import kotlinx.coroutines.launch

/**
 * One goal of the week, opened by tapping its row on Home (iOS `ActivityDetailsSheet`): what it
 * is, the resource it's tied to, how far along it is, and Start. A completed goal can still be
 * trained, since a target is a floor. iOS also keeps the goal's own reminders here; per-goal
 * reminders aren't ported yet (AGENTS.md), and this sheet is where they'll go.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDetailsSheet(goal: WeeklyGoal, onStart: () -> Unit, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(activityIcon(goal.activityType.code), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(goalTitle(goal), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                goal.resource?.let {
                    Text(it.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        targetText(goal.activityType.code, goal.targetType, goal.targetValue),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    AnimatedValueText(progressText(goal), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                }
                AnimatedLinearProgress((goal.progressPercentage / 100).toFloat(), Modifier.fillMaxWidth())
            }
            // Closes first, so the timer opens over Home rather than under a sliding sheet.
            Button(
                onClick = { scope.launch { state.hide() }.invokeOnCompletion { onDismiss(); onStart() } },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Text(stringResource(if (goal.isCompleted) R.string.train_again else R.string.start_training))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.padding(start = 8.dp).size(ButtonDefaults.IconSize))
            }
        }
    }
}
