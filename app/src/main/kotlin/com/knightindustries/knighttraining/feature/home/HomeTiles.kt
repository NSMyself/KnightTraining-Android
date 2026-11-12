package com.knightindustries.knighttraining.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.model.MilestonesSnapshot
import com.knightindustries.knighttraining.core.model.WeeklyTrainingStats
import com.knightindustries.knighttraining.core.store.WeeklySurface
import com.knightindustries.knighttraining.ui.activityName
import com.knightindustries.knighttraining.ui.currentLocale
import com.knightindustries.knighttraining.ui.durationText
import com.knightindustries.knighttraining.ui.goalTitle
import com.knightindustries.knighttraining.ui.progressText
import com.knightindustries.knighttraining.ui.ratingTypeNameRes
import java.text.NumberFormat
import java.time.format.TextStyle

/** What a tile can draw on: everything Home already loads, nothing tile-specific. */
data class TileData(
    val surface: WeeklySurface,
    val stats: WeeklyTrainingStats,
    val lastWeek: LastWeek?,
    val milestones: MilestonesSnapshot?,
)

data class MilestoneActions(
    val onToggle: (com.knightindustries.knighttraining.core.model.Milestone) -> Unit,
    val onAdd: () -> Unit,
    val onEdit: (com.knightindustries.knighttraining.core.model.Milestone) -> Unit,
    val onDelete: (com.knightindustries.knighttraining.core.model.Milestone) -> Unit,
)

/** The chosen tiles below the plan card: a wide tile per row, small ones two to a row. */
fun LazyListScope.homeTiles(layout: HomeLayout, data: TileData, milestoneActions: MilestoneActions) {
    for (section in layout.sections) {
        when (section) {
            is HomeLayout.Section.Wide -> item(key = section.tile.id) { WideTile(section.tile, data, milestoneActions) }
            is HomeLayout.Section.Small -> section.tiles.chunked(2).forEach { pair ->
                item(key = pair.joinToString(",") { it.id }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { SmallTile(it, data, Modifier.weight(1f)) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun WideTile(tile: HomeTile, data: TileData, milestoneActions: MilestoneActions) {
    when (tile) {
        HomeTile.WEEKLY_ACTIVITY -> WeeklyActivityTile(data.surface)
        HomeTile.MILESTONES -> data.milestones?.let {
            MilestonesCard(it, milestoneActions.onToggle, milestoneActions.onAdd, milestoneActions.onEdit, milestoneActions.onDelete)
        }
        HomeTile.LAST_WEEK -> LastWeekTile(data.lastWeek)
        else -> Unit
    }
}

/** Icon and title, shared by the tiles and the edit sheet. */
fun HomeTile.icon(): ImageVector = when (this) {
    HomeTile.WEEKLY_ACTIVITY -> Icons.Rounded.BarChart
    HomeTile.MILESTONES -> Icons.Rounded.Flag
    HomeTile.LAST_WEEK -> Icons.Rounded.Undo
    HomeTile.COMPLETION_RATE -> Icons.Rounded.CheckCircle
    HomeTile.PUZZLES -> Icons.Rounded.Extension
    HomeTile.MOST_TRAINED -> Icons.Rounded.Star
    HomeTile.CLASSICAL_GAMES -> Icons.Rounded.HourglassBottom
    HomeTile.WEEKLY_TOTAL -> Icons.Rounded.Schedule
    HomeTile.ALL_TIME_TOTAL -> Icons.Rounded.AllInclusive
    HomeTile.CURRENT_RATING -> Icons.Rounded.TrendingUp
    HomeTile.LAST_STUDIED -> Icons.Rounded.AutoStories
}

@Composable
fun HomeTile.title(): String = stringResource(
    when (this) {
        HomeTile.WEEKLY_ACTIVITY -> R.string.this_week_activity
        HomeTile.MILESTONES -> R.string.milestones
        HomeTile.LAST_WEEK -> R.string.tile_last_week
        HomeTile.COMPLETION_RATE -> R.string.tile_completion_rate
        HomeTile.PUZZLES -> R.string.puzzles_solved
        HomeTile.MOST_TRAINED -> R.string.most_trained
        HomeTile.CLASSICAL_GAMES -> R.string.classical_games
        HomeTile.WEEKLY_TOTAL -> R.string.tile_weekly_total
        HomeTile.ALL_TIME_TOTAL -> R.string.tile_all_time_total
        HomeTile.CURRENT_RATING -> R.string.tile_current_rating
        HomeTile.LAST_STUDIED -> R.string.tile_last_studied
    },
)

@Composable
fun HomeTile.detail(): String? = when (this) {
    HomeTile.WEEKLY_ACTIVITY -> stringResource(R.string.tile_weekly_activity_detail)
    HomeTile.MILESTONES -> stringResource(R.string.tile_milestones_detail)
    HomeTile.LAST_WEEK -> stringResource(R.string.tile_last_week_detail)
    HomeTile.CURRENT_RATING -> stringResource(R.string.tile_current_rating_detail)
    HomeTile.LAST_STUDIED -> stringResource(R.string.tile_last_studied_detail)
    HomeTile.COMPLETION_RATE, HomeTile.PUZZLES, HomeTile.MOST_TRAINED, HomeTile.CLASSICAL_GAMES -> stringResource(R.string.tile_this_week_detail)
    HomeTile.WEEKLY_TOTAL, HomeTile.ALL_TIME_TOTAL -> null
}

/** A chosen tile shows "0" or "—" rather than disappearing (iOS AGENTS.md §9). */
@Composable
private fun SmallTile(tile: HomeTile, data: TileData, modifier: Modifier) {
    val surface = data.surface
    val dash = "—"
    val value = when (tile) {
        HomeTile.COMPLETION_RATE -> surface.plan?.let { NumberFormat.getPercentInstance().format(it.completionPercentage / 100) } ?: dash
        HomeTile.PUZZLES -> data.stats.puzzleCount.toString()
        HomeTile.MOST_TRAINED -> data.stats.mostTrainedActivityCode?.let { activityName(it) } ?: dash
        HomeTile.CLASSICAL_GAMES -> data.stats.classicalGameCount.toString()
        HomeTile.WEEKLY_TOTAL -> durationText(surface.weeklyTrackedSeconds)
        HomeTile.ALL_TIME_TOTAL -> durationText(surface.allTimeStudySeconds)
        HomeTile.CURRENT_RATING -> surface.currentRating?.toString() ?: dash
        HomeTile.LAST_STUDIED -> surface.continueResource?.title ?: dash
        else -> dash
    }
    val caption = when {
        tile == HomeTile.CURRENT_RATING && surface.currentRatingType != null -> stringResource(ratingTypeNameRes(surface.currentRatingType!!))
        else -> tile.title()
    }
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(tile.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

/** This week's total and a bar per day; the streak as a caption, never below 2 (iOS AGENTS.md §7). */
@Composable
private fun WeeklyActivityTile(surface: WeeklySurface) {
    val days = (0L until 7L).map { surface.weekStart.plusDays(it) }
    val seconds = days.map { surface.recentDailyDurationSeconds[it] ?: 0 }
    val max = seconds.maxOrNull()?.takeIf { it > 0 } ?: 1
    val locale = currentLocale()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.this_week_activity), style = MaterialTheme.typography.titleMedium)
            Text(durationText(surface.weeklyTrackedSeconds), style = MaterialTheme.typography.headlineSmall)
            Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                days.forEachIndexed { index, day ->
                    val label = day.dayOfWeek.getDisplayName(TextStyle.NARROW, locale)
                    val spoken = day.dayOfWeek.getDisplayName(TextStyle.FULL, locale) + " " + durationText(seconds[index])
                    Column(
                        Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = spoken },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            val fraction = seconds[index].toFloat() / max
                            Box(
                                Modifier.fillMaxWidth().fillMaxHeight(maxOf(fraction, 0.04f))
                                    .background(
                                        if (seconds[index] > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(6.dp),
                                    ),
                            )
                        }
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            if (surface.currentStreak >= 2) {
                Text(stringResource(R.string.week_streak, surface.currentStreak), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Last week's goals and what its review said: the context for this week's plan. */
@Composable
private fun LastWeekTile(lastWeek: LastWeek?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.tile_last_week), style = MaterialTheme.typography.titleMedium)
            if (lastWeek == null) {
                Text(stringResource(R.string.last_week_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            lastWeek.plan.weeklyGoals.forEach { goal ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(goalTitle(goal), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(progressText(goal), color = if (goal.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val review = lastWeek.review
            listOfNotNull(review?.heldBack, review?.wishIDone).map { it.trim() }.filter { it.isNotEmpty() }.forEach { quote ->
                Text("“$quote”", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            review?.nextWeekFocus?.trim()?.takeIf { it.isNotEmpty() }?.let { focus ->
                Text(stringResource(R.string.last_week_focus), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(focus, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
