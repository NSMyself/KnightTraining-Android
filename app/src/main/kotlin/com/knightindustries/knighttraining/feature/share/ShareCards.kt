package com.knightindustries.knighttraining.feature.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.model.LibraryShareSummary
import com.knightindustries.knighttraining.core.model.SharePlayer
import com.knightindustries.knighttraining.core.model.WeekShareSummary
import com.knightindustries.knighttraining.feature.home.weekRange
import com.knightindustries.knighttraining.feature.review.intentName
import com.knightindustries.knighttraining.ui.activityIcon
import com.knightindustries.knighttraining.ui.activityName
import com.knightindustries.knighttraining.ui.currentLocale
import com.knightindustries.knighttraining.ui.durationText
import com.knightindustries.knighttraining.ui.goalTitle
import com.knightindustries.knighttraining.ui.kindIcon
import com.knightindustries.knighttraining.ui.progressText
import com.knightindustries.knighttraining.ui.ratingTypeNameRes
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.TextStyle

/** A share card's width: the image is the same size whatever the phone. */
val SHARE_CARD_WIDTH = 360.dp

private const val LIST_LIMIT = 8
private const val UP_NEXT_LIMIT = 5

@Composable
fun WeekShareCard(summary: WeekShareSummary, includesNotes: Boolean, includesReflection: Boolean) {
    ShareCardFrame {
        Header(stringResource(R.string.share_week_label), weekRange(summary.plan.weekStart, summary.nextWeekStart), summary.planName, summary.player)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Total("${summary.plan.completedGoalCount}/${summary.plan.goalCount}", stringResource(R.string.share_total_goals), Modifier.weight(1f))
            Total(durationText(summary.totalSeconds), stringResource(R.string.share_total_trained), Modifier.weight(1f))
            Total(summary.sessionCount.toString(), pluralStringResource(R.plurals.share_sessions, summary.sessionCount), Modifier.weight(1f))
        }
        if (summary.goals.isNotEmpty()) {
            Section(stringResource(R.string.share_goals)) {
                summary.goals.forEach { goal ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(activityIcon(goal.activityType.code), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(goalTitle(goal), fontWeight = FontWeight.Bold, maxLines = 2)
                                if (goal.isBonus) Text(stringResource(R.string.bonus), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            if (goal.isCompleted) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp).padding(end = 2.dp))
                            Text(progressText(goal), fontWeight = FontWeight.Bold)
                        }
                        LinearProgressIndicator(progress = { (goal.progressPercentage / 100).toFloat() }, modifier = Modifier.fillMaxWidth())
                        goal.resource?.let { Muted(it.title) }
                    }
                }
            }
        }
        if (summary.resourceTimes.isNotEmpty()) {
            Section(stringResource(R.string.share_resources_studied)) {
                summary.resourceTimes.forEach { resource ->
                    Row {
                        Text(resource.title, modifier = Modifier.weight(1f), maxLines = 2)
                        Text(durationText(resource.seconds), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (includesNotes && summary.notes.isNotEmpty()) {
            Section(stringResource(R.string.share_notes)) {
                summary.notes.forEach { note ->
                    Column {
                        val day = note.startedAt.atZone(ZoneId.systemDefault()).dayOfWeek.getDisplayName(TextStyle.FULL, currentLocale())
                        Muted("${activityName(note.activityTypeCode)} · $day")
                        Text(note.text)
                    }
                }
            }
        }
        val review = summary.review
        if (includesReflection && review != null) {
            Section(stringResource(R.string.share_reflection)) {
                review.wentWell?.trim()?.takeIf { it.isNotEmpty() }?.let { Reflection(stringResource(R.string.share_went_well), it) }
                review.heldBack?.trim()?.takeIf { it.isNotEmpty() }?.let { Reflection(stringResource(R.string.share_held_back), it) }
                review.wishIDone?.trim()?.takeIf { it.isNotEmpty() }?.let { Reflection(stringResource(R.string.share_wish), it) }
                review.nextWeekIntent?.takeIf { it.isNotEmpty() }?.let { Reflection(stringResource(R.string.share_next_week), stringResource(intentName(it))) }
            }
        }
    }
}

@Composable
fun LibraryShareCard(summary: LibraryShareSummary, includesUpNext: Boolean) {
    ShareCardFrame {
        Header(stringResource(R.string.share_library_label), stringResource(R.string.share_library_heading), null, summary.player)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Total(summary.inProgress.size.toString(), stringResource(R.string.share_total_in_progress), Modifier.weight(1f))
            Total(summary.finished.size.toString(), stringResource(R.string.share_total_finished), Modifier.weight(1f))
            Total(durationText(summary.totalSeconds), stringResource(R.string.share_total_invested), Modifier.weight(1f))
        }
        if (summary.inProgress.isNotEmpty()) {
            Section(stringResource(R.string.status_in_progress)) {
                summary.inProgress.take(LIST_LIMIT).forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ResourceLine(item) { Text(NumberFormat.getPercentInstance().format(item.resource.progressPercentage / 100.0), fontWeight = FontWeight.Bold) }
                        LinearProgressIndicator(progress = { item.resource.progressPercentage / 100f }, modifier = Modifier.fillMaxWidth())
                        if (item.seconds > 0) Muted(durationText(item.seconds))
                    }
                }
                More(summary.inProgress.size, LIST_LIMIT)
            }
        }
        if (summary.finished.isNotEmpty()) {
            Section(stringResource(R.string.status_completed)) {
                summary.finished.take(LIST_LIMIT).forEach { item ->
                    ResourceLine(item) { if (item.seconds > 0) Text(durationText(item.seconds), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                More(summary.finished.size, LIST_LIMIT)
            }
        }
        if (includesUpNext && summary.notStarted.isNotEmpty()) {
            Section(stringResource(R.string.share_up_next)) {
                summary.notStarted.take(UP_NEXT_LIMIT).forEach { item -> ResourceLine(item) {} }
                More(summary.notStarted.size, UP_NEXT_LIMIT)
            }
        }
    }
}

@Composable
private fun ShareCardFrame(content: @Composable () -> Unit) {
    Column(
        Modifier.width(SHARE_CARD_WIDTH).background(MaterialTheme.colorScheme.background, RoundedCornerShape(24.dp)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        content()
        // The one place the brand name appears on the card, like iOS's `ShareCardFooter`.
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Header(label: String, title: String, subtitle: String?, player: SharePlayer?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label.uppercase(currentLocale()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        subtitle?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        player?.let { PlayerLine(it) }
    }
}

@Composable
private fun PlayerLine(player: SharePlayer) {
    val ratings = player.ratings.map { "${stringResource(ratingTypeNameRes(it.ratingType))} ${it.rating}" }
    Muted((listOf("@${player.username}") + ratings).joinToString(" · "))
}

@Composable
private fun Total(value: String, label: String, modifier: Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title.uppercase(currentLocale()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun ResourceLine(item: LibraryShareSummary.Item, trailing: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(kindIcon(item.resource.kind.code), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(item.resource.title, maxLines = 2)
            item.resource.author?.let { Muted(it) }
        }
        trailing()
    }
}

@Composable
private fun More(total: Int, limit: Int) {
    if (total > limit) Muted(pluralStringResource(R.plurals.share_and_more, total - limit, total - limit))
}

@Composable
private fun Reflection(label: String, text: String) {
    Column {
        Muted(label.uppercase(currentLocale()))
        Text(text)
    }
}

@Composable
private fun Muted(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
