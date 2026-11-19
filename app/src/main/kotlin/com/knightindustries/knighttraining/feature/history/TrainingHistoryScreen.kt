package com.knightindustries.knighttraining.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.store.TimeOfDayBucket
import com.knightindustries.knighttraining.ui.activityName
import com.knightindustries.knighttraining.ui.currentLocale
import com.knightindustries.knighttraining.ui.durationText
import java.text.NumberFormat

/**
 * How much was planned each week next to how much was trained (iOS `TrainingHistoryScreen`):
 * the commitment streak, a five-week chart, the last 36 days as a grid, and three stats. Opened
 * from Home's weekly activity and last week tiles.
 *
 * Deliberately no verdict on a week that fell short: a weekly target is a threshold, not a
 * burndown (iOS AGENTS.md §7). Only the consistency line warms up, and only at the high end.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingHistoryScreen(viewModel: TrainingHistoryViewModel, onBack: () -> Unit) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.training_history)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back)) } },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Text(stringResource(R.string.training_history_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val current = history
            when {
                current == null -> Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                !current.hasTrend -> EmptyState()
                else -> {
                    // Never at 0 or 1: a reset streak isn't a guilt badge, and one week isn't a streak.
                    if (current.streak >= 2) StreakCard(current.streak)
                    WeeklyReport(current)
                    Section(stringResource(R.string.daily_activity)) { ActivityGrid(current.dailySeconds, current.today) }
                    Section(stringResource(R.string.stats)) { Stats(current) }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.AutoMirrored.Rounded.ShowChart, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
        Text(stringResource(R.string.history_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.history_empty_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StreakCard(streak: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Text(stringResource(R.string.commitment_streak), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(streak.toString(), style = MaterialTheme.typography.displaySmall)
        }
    }
}

@Composable
private fun WeeklyReport(history: TrainingHistory) {
    Section(stringResource(R.string.weekly_report)) {
        history.compliancePercent?.let { percent ->
            val formatted = NumberFormat.getPercentInstance(currentLocale()).format(percent / 100.0)
            val sentence = when {
                percent >= 80 -> R.string.compliance_exceptional
                percent >= 40 -> R.string.compliance_nice
                else -> R.string.compliance
            }
            Text(stringResource(sentence, formatted), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ChartLegend()
        EffortChart(history.weeks)
    }
}

@Composable
private fun Stats(history: TrainingHistory) {
    val none = stringResource(R.string.none_yet)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(Icons.Rounded.Schedule, durationText(history.totalSeconds), stringResource(R.string.total_hours), Modifier.weight(1f))
        StatCard(Icons.Rounded.Star, history.mostTrained?.let { activityName(it.code) } ?: none, stringResource(R.string.most_trained), Modifier.weight(1f))
        StatCard(Icons.Rounded.WbTwilight, history.usualTime?.let { stringResource(it.label()) } ?: none, stringResource(R.string.usual_study_time), Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(icon: ImageVector, value: String, caption: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

private fun TimeOfDayBucket.label(): Int = when (this) {
    TimeOfDayBucket.MORNING -> R.string.time_mornings
    TimeOfDayBucket.AFTERNOON -> R.string.time_afternoons
    TimeOfDayBucket.EVENING -> R.string.time_evenings
    TimeOfDayBucket.NIGHT -> R.string.time_late_nights
}
