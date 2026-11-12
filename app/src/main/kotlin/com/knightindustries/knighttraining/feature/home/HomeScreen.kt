package com.knightindustries.knighttraining.feature.home

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.model.PlanTemplate
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.core.model.WeeklyTrainingStats
import com.knightindustries.knighttraining.core.store.WeeklySurface
import com.knightindustries.knighttraining.ui.activityIcon
import com.knightindustries.knighttraining.ui.activityName
import com.knightindustries.knighttraining.ui.durationText
import com.knightindustries.knighttraining.ui.goalTitle
import com.knightindustries.knighttraining.ui.progressText
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onCreatePlan: () -> Unit,
    onPlanThisWeek: () -> Unit,
    onStartGoal: (WeeklyGoal) -> Unit,
    onStartSession: () -> Unit,
    contentPadding: PaddingValues,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.padding(contentPadding).nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(greeting()) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            if ((state as? HomeState.Loaded)?.surface?.plan != null || (state as? HomeState.Loaded)?.surface?.hasPlanTemplates == true) {
                ExtendedFloatingActionButton(
                    onClick = onStartSession,
                    icon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
                    text = { Text(stringResource(R.string.start_session)) },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (val current = state) {
            HomeState.Loading -> Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(64.dp))
                CircularProgressIndicator()
            }
            is HomeState.Loaded -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { PlanCard(current.surface, current.weekEnd, onCreatePlan, onPlanThisWeek, onStartGoal) }
                item { ActivityCard(current.surface, current.stats) }
            }
        }
    }
}

@Composable
private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> stringResource(R.string.greeting_morning)
    in 12..17 -> stringResource(R.string.greeting_afternoon)
    else -> stringResource(R.string.greeting_evening)
}

/** "9 Nov – 15 Nov", in the locale's own order. */
@Composable
fun weekRange(start: LocalDate, endExclusive: LocalDate): String {
    val formatter = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMd"))
    return stringResource(R.string.week_range, start.format(formatter), endExclusive.minusDays(1).format(formatter))
}

/** The week's plan: the three states of iOS's `WeeklyPlanState`. Never auto-renewed. */
@Composable
private fun PlanCard(
    surface: WeeklySurface,
    weekEnd: LocalDate?,
    onCreatePlan: () -> Unit,
    onPlanThisWeek: () -> Unit,
    onStartGoal: (WeeklyGoal) -> Unit,
) {
    val plan = surface.plan
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        when {
            plan == null && !surface.hasPlanTemplates -> CallToAction(
                stringResource(R.string.no_plan_title), stringResource(R.string.no_plan_body),
                stringResource(R.string.create_plan_title), onCreatePlan,
            )
            plan == null -> CallToAction(
                stringResource(R.string.plan_week_title), stringResource(R.string.plan_week_body),
                stringResource(R.string.plan_this_week), onPlanThisWeek,
            )
            else -> Column(Modifier.padding(vertical = 8.dp)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        PlanTemplate.displayName(surface.planName, stringResource(R.string.my_training_plan)),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        stringResource(R.string.goals_done, plan.completedGoalCount, plan.goalCount) + " · " +
                            weekRange(plan.weekStart, weekEnd ?: plan.nextWeekStart),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                plan.weeklyGoals.forEach { GoalRow(it) { onStartGoal(it) } }
            }
        }
    }
}

@Composable
private fun CallToAction(title: String, body: String, action: String, onClick: () -> Unit) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Button(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun GoalRow(goal: WeeklyGoal, onStart: () -> Unit) {
    val title = goalTitle(goal)
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(activityIcon(goal.activityType.code), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(progressText(goal))
                LinearProgressIndicator(
                    progress = { (goal.progressPercentage / 100).toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        trailingContent = {
            if (goal.isCompleted) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = stringResource(R.string.goal_done), tint = MaterialTheme.colorScheme.primary)
            } else {
                IconButton(onClick = onStart) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.start_goal, title))
                }
            }
        },
    )
}

/** Derived figures for this week; hidden figures stay hidden rather than reading zero. */
@Composable
private fun ActivityCard(surface: WeeklySurface, stats: WeeklyTrainingStats) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.this_week_activity), style = MaterialTheme.typography.titleMedium)
            if (surface.recentActivities.isEmpty()) {
                Text(stringResource(R.string.no_activity_this_week), color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(stringResource(R.string.total_this_week), durationText(stats.totalSeconds), Modifier.weight(1f))
                stats.mostTrainedActivityCode?.let { Stat(stringResource(R.string.most_trained), activityName(it), Modifier.weight(1f)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(stringResource(R.string.puzzles_solved), stats.puzzleCount.toString(), Modifier.weight(1f))
                Stat(stringResource(R.string.games_played), stats.gameCount.toString(), Modifier.weight(1f))
                Stat(stringResource(R.string.classical_games), stats.classicalGameCount.toString(), Modifier.weight(1f))
            }
            // A streak is a caption, never a badge, and never shown below 2 (iOS AGENTS.md §7).
            if (surface.currentStreak >= 2) {
                Text(stringResource(R.string.week_streak, surface.currentStreak), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
