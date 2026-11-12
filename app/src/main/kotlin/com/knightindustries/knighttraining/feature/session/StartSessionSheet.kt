package com.knightindustries.knighttraining.feature.session

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.core.session.PracticeSession
import com.knightindustries.knighttraining.core.session.PracticeSession.Attribution
import com.knightindustries.knighttraining.feature.plan.ActivityGrid
import com.knightindustries.knighttraining.ui.activityIcon
import com.knightindustries.knighttraining.ui.goalTitle
import com.knightindustries.knighttraining.ui.progressText
import java.util.UUID

/** How a session starts: what it's attributed to, and the goals "Change" may switch between. */
data class SessionStart(val attribution: Attribution, val availableGoalIds: List<UUID> = emptyList(), val resourceId: UUID? = null) {
    companion object {
        /** Several goals picked: start on the first, offer the rest in "Change". */
        fun goals(picked: List<WeeklyGoal>) =
            SessionStart(PracticeSession.attributionFor(picked.first()), picked.map { it.id }, picked.first().resource?.id)
    }
}

/**
 * iOS `StartSessionScreen`: this week's open goals (one or several), something outside the plan,
 * or just the timer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartSessionSheet(openGoals: List<WeeklyGoal>, onDismiss: () -> Unit, onStart: (SessionStart) -> Unit) {
    val selected = remember { mutableStateListOf<UUID>() }
    var choosingOther by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.start_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(8.dp))
            if (choosingOther) {
                ActivityGrid { code -> onStart(SessionStart(PracticeSession.adHoc(code, null))) }
                Spacer(Modifier.height(16.dp))
                return@Column
            }

            if (openGoals.isNotEmpty()) {
                Text(stringResource(R.string.start_from_plan), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
                openGoals.forEach { goal ->
                    val checked = goal.id in selected
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { Icon(activityIcon(goal.activityType.code), contentDescription = null) },
                        headlineContent = { Text(goalTitle(goal)) },
                        supportingContent = { Text(progressText(goal)) },
                        trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                        modifier = Modifier.toggleable(checked, role = Role.Checkbox) { if (it) selected.add(goal.id) else selected.remove(goal.id) },
                    )
                }
                Button(
                    enabled = selected.isNotEmpty(),
                    onClick = { onStart(SessionStart.goals(openGoals.filter { it.id in selected })) },
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                ) { Text(stringResource(R.string.start_selected, selected.size)) }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }

            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(R.string.something_else)) },
                modifier = Modifier.toggleable(false, role = Role.Button) { choosingOther = true },
            )
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(R.string.just_start)) },
                supportingContent = { Text(stringResource(R.string.just_start_body)) },
                modifier = Modifier.toggleable(false, role = Role.Button) { onStart(SessionStart(Attribution.General)) },
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Asks for notification permission (Android 13+) the first time a session starts: the timer's
 * notification is how a running session stays reachable. The session starts either way.
 */
@Composable
fun rememberStartWithNotifications(start: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { start() }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            start()
        }
    }
}
