package com.knightindustries.knighttraining.feature.training

import com.knightindustries.knighttraining.feature.review.intentName
import com.knightindustries.knighttraining.core.model.WeeklyReview
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.LocalTextStyle
import com.knightindustries.knighttraining.ui.AnimatedValueText
import com.knightindustries.knighttraining.ui.AnimatedLinearProgress
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.feature.share.ShareWeekSheet
import com.knightindustries.knighttraining.core.model.Activity
import com.knightindustries.knighttraining.core.model.ActivityType
import com.knightindustries.knighttraining.feature.home.weekRange
import com.knightindustries.knighttraining.ui.LabeledFab
import com.knightindustries.knighttraining.ui.activityIcon
import com.knightindustries.knighttraining.ui.activityName
import com.knightindustries.knighttraining.ui.durationText
import com.knightindustries.knighttraining.ui.goalTitle
import com.knightindustries.knighttraining.ui.progressText
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import androidx.compose.material3.OutlinedButton

/** One week: its goals, then every session, newest first. This week's sessions are editable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekLogScreen(viewModel: WeekLogViewModel, onBack: () -> Unit, onReflect: (UUID) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deleteLabel = stringResource(R.string.delete_session)
    var editing by remember { mutableStateOf<Activity?>(null) }
    var deleting by remember { mutableStateOf<Activity?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    var adding by rememberSaveable { mutableStateOf(false) }
    var sharing by rememberSaveable { mutableStateOf(false) }
    state?.plan?.takeIf { sharing }?.let { plan -> ShareWeekSheet(plan, onDismiss = { sharing = false }) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state?.let { weekRange(it.weekStart, it.weekEnd) } ?: stringResource(R.string.training_log)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null) } },
                actions = {
                    if (state?.plan != null) {
                        IconButton(onClick = { sharing = true }) { Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.share_week_title)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            if (state?.isCurrentWeek == true) {
                LabeledFab(Icons.Rounded.Add, stringResource(R.string.add_session)) { adding = true }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val current = state ?: return@Scaffold CircularProgressIndicator(Modifier.padding(padding).padding(32.dp))
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp)) {
            current.plan?.let { plan ->
                // A finished week can be reflected on, or its reflection edited, at any time; what
                // was written shows here (iOS's week log does).
                if (!current.isCurrentWeek) {
                    item {
                        val review = current.review
                        if (review != null && review.hasContent()) {
                            ReflectionCard(review, onEdit = { onReflect(plan.id) })
                        } else {
                            OutlinedButton(onClick = { onReflect(plan.id) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text(stringResource(R.string.reflect_on_week))
                            }
                        }
                    }
                }
                item { SectionHeader(pluralStringResource(R.plurals.goals_done, plan.goalCount, plan.completedGoalCount, plan.goalCount)) }
                items(plan.weeklyGoals, key = { it.id }) { goal ->
                    ListItem(
                        modifier = Modifier.animateItem(),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { Icon(activityIcon(goal.activityType.code), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text(goalTitle(goal)) },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                AnimatedValueText(progressText(goal), style = LocalTextStyle.current)
                                AnimatedLinearProgress((goal.progressPercentage / 100).toFloat(), Modifier.fillMaxWidth())
                            }
                        },
                    )
                }
            }
            item { SectionHeader(stringResource(R.string.training_log)) }
            if (current.activities.isEmpty()) {
                item { Text(stringResource(R.string.no_activity_this_week), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp)) }
            } else if (!current.isCurrentWeek) {
                item { Text(stringResource(R.string.past_weeks_read_only), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp)) }
            }
            items(current.activities, key = { it.id }) { activity ->
                if (current.isCurrentWeek) {
                    // Swipe to delete, confirmed first: it takes the time back from the week's goals.
                    val swipe = rememberSwipeToDismissBoxState()
                    LaunchedEffect(swipe.currentValue) {
                        if (swipe.currentValue == SwipeToDismissBoxValue.EndToStart) {
                            deleting = activity
                            swipe.snapTo(SwipeToDismissBoxValue.Settled)
                        }
                    }
                    SwipeToDismissBox(
                        swipe,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp), contentAlignment = Alignment.CenterEnd) {
                                Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        },
                        modifier = Modifier.animateItem(),
                    ) {
                        ActivityRow(
                            activity,
                            Modifier.background(MaterialTheme.colorScheme.background).clickable { editing = activity }
                                .semantics { customActions = listOf(CustomAccessibilityAction(deleteLabel) { deleting = activity; true }) },
                        )
                    }
                } else {
                    ActivityRow(activity, Modifier.animateItem())
                }
            }
        }

        deleting?.let { activity ->
            AlertDialog(
                onDismissRequest = { deleting = null },
                title = { Text(stringResource(R.string.delete_session_title)) },
                text = { Text(stringResource(R.string.delete_session_body)) },
                confirmButton = { TextButton(onClick = { viewModel.delete(activity); deleting = null }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
            )
        }

        if (adding) {
            SessionEditorSheet(
                existing = null, weekStart = current.weekStart, weekEnd = current.weekEnd, candidates = viewModel::candidates,
                onDismiss = { adding = false }, onSave = { viewModel.log(it); adding = false },
            )
        }
        editing?.let { activity ->
            SessionEditorSheet(
                existing = activity, weekStart = current.weekStart, weekEnd = current.weekEnd, candidates = viewModel::candidates,
                onDismiss = { editing = null },
                onSave = { viewModel.edit(activity, it); editing = null },
                onDelete = { viewModel.delete(activity); editing = null },
            )
        }
    }
}

private fun WeeklyReview.hasContent() =
    listOfNotNull(wentWell, heldBack, wishIDone, nextWeekIntent, nextWeekFocus).any { it.isNotBlank() }

/** The week's saved reflection, answer by answer, with a way back in to edit it. */
@Composable
private fun ReflectionCard(review: WeeklyReview, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reflection), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onEdit) { Text(stringResource(R.string.edit)) }
            }
            listOf(R.string.review_went_well to review.wentWell, R.string.review_held_back to review.heldBack, R.string.review_wish to review.wishIDone)
                .forEach { (question, answer) ->
                    answer?.takeIf { it.isNotBlank() }?.let {
                        Text(stringResource(question), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            review.nextWeekIntent?.let {
                Text(stringResource(R.string.week_log_intent), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(intentName(it)), style = MaterialTheme.typography.bodyMedium)
            }
            review.nextWeekFocus?.takeIf { it.isNotBlank() }?.let {
                Text(stringResource(R.string.last_week_focus), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ActivityRow(activity: Activity, modifier: Modifier) {
    val pattern = remember { DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEjmm") }
    val whenText = activity.startedAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern))
    val details = buildList {
        add(durationText(activity.duration))
        activity.count?.let { add(it.toString()) }
        activity.timeControlLabel?.let { add(it) }
    }.joinToString(" · ")
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(activityIcon(activity.activityType.code), contentDescription = null) },
        headlineContent = { Text(if (activity.activityType.code == ActivityType.GENERAL) stringResource(R.string.general_practice) else activityName(activity.activityType.code)) },
        overlineContent = { Text(whenText) },
        supportingContent = { Text(listOfNotNull(details, activity.notes).joinToString("\n")) },
        modifier = modifier,
    )
}
