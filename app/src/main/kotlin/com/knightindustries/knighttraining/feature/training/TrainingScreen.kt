package com.knightindustries.knighttraining.feature.training

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.knightindustries.knighttraining.AppContainer
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.model.PlanTemplate
import com.knightindustries.knighttraining.core.model.WeekCalendar
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import com.knightindustries.knighttraining.core.store.observePlanTemplates
import com.knightindustries.knighttraining.core.store.observeWeeklyPlans
import com.knightindustries.knighttraining.feature.home.weekRange
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate

data class TrainingState(val weeks: List<WeeklyPlan>, val plans: List<PlanTemplate>, val currentWeekStart: LocalDate, val firstLaunch: Instant)

/** Both lists observed: the tab stays mounted while weeks are committed and sessions logged. */
class TrainingViewModel(private val container: AppContainer) : ViewModel() {
    val state: StateFlow<TrainingState?> = combine(
        container.store.observeWeeklyPlans(limit = 52),
        container.store.observePlanTemplates(),
        flow { emit(container.preferences.firstLaunch()) },
    ) { weeks, plans, firstLaunch ->
        TrainingState(weeks, plans, WeeklyPlan.effectiveWeekStart(Instant.now(), firstLaunch, WeekCalendar.system()), firstLaunch)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(
    viewModel: TrainingViewModel,
    onOpenWeek: (LocalDate) -> Unit,
    onEditPlan: (PlanTemplate) -> Unit,
    onCreatePlan: () -> Unit,
    contentPadding: PaddingValues,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.padding(contentPadding).nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.tab_training)) },
                actions = {
                    IconButton(onClick = onCreatePlan) { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.create_new_plan)) }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val current = state
        if (current == null) {
            CircularProgressIndicator(Modifier.padding(padding).padding(32.dp))
            return@Scaffold
        }
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp)) {
            item { SectionHeader(stringResource(R.string.your_weeks)) }
            if (current.weeks.isEmpty()) item { Empty(stringResource(R.string.no_weeks_yet)) }
            items(current.weeks, key = { it.id }) { week ->
                val isCurrent = week.weekStart == current.currentWeekStart
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        Text(weekRange(week.weekStart, week.effectiveNextWeekStart(current.firstLaunch, WeekCalendar.system())))
                    },
                    overlineContent = if (isCurrent) ({ Text(stringResource(R.string.current_plan)) }) else null,
                    supportingContent = { Text(pluralStringResource(R.plurals.goals_done, week.goalCount, week.completedGoalCount, week.goalCount)) },
                    leadingContent = {
                        if (week.isFullyCompleted) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        else CircularProgressIndicator(progress = { (week.completionPercentage / 100).toFloat() }, modifier = Modifier.padding(2.dp))
                    },
                    trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                    modifier = Modifier.clickable { onOpenWeek(week.weekStart) },
                )
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader(stringResource(R.string.training_plans)) }
            if (current.plans.isEmpty()) item { Empty(stringResource(R.string.no_plans_yet)) }
            items(current.plans, key = { it.id }) { plan ->
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(plan.displayName(stringResource(R.string.my_training_plan))) },
                    supportingContent = { Text(pluralStringResource(R.plurals.goal_count, plan.goalCount, plan.goalCount)) },
                    trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                    modifier = Modifier.clickable { onEditPlan(plan) },
                )
            }
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
}

@Composable
private fun Empty(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

