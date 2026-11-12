package com.knightindustries.knighttraining.feature.session

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.model.ActivityType
import com.knightindustries.knighttraining.core.model.CoachingTopic
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.core.session.PracticeSession
import com.knightindustries.knighttraining.core.session.PracticeSession.Attribution
import com.knightindustries.knighttraining.core.session.PracticeSession.GoalCredit
import com.knightindustries.knighttraining.feature.plan.ActivityGrid
import com.knightindustries.knighttraining.feature.plan.TimeControlPicker
import com.knightindustries.knighttraining.ui.activityIcon
import com.knightindustries.knighttraining.ui.activityName
import com.knightindustries.knighttraining.ui.clockText
import com.knightindustries.knighttraining.ui.goalTitle
import com.knightindustries.knighttraining.ui.progressText
import kotlinx.coroutines.delay
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(viewModel: SessionViewModel, onLeave: () -> Unit, onFinished: () -> Unit) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is SessionEvent.Finished -> {
                    if (event.discardedAsAccidental) Toast.makeText(context, R.string.session_discarded, Toast.LENGTH_SHORT).show()
                    onFinished()
                }
                SessionEvent.SaveFailed -> Toast.makeText(context, R.string.save_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    val current = session
    if (current == null) {
        LaunchedEffect(Unit) { onFinished() }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.training_session)) },
                navigationIcon = {
                    // Leaving never ends the session: it carries on in the notification and on Home.
                    IconButton(onClick = onLeave) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).navigationBarsPadding()) {
            when (val phase = current.phase) {
                PracticeSession.Phase.Running -> TimerContent(current, viewModel)
                PracticeSession.Phase.Categorizing -> CategorizeContent(viewModel)
                is PracticeSession.Phase.Reviewing -> ReviewContent(current, phase.activityTypeCode, viewModel)
            }
        }
    }
}

/** Wall-clock "now", re-read every second while the clock is visibly running. */
@Composable
private fun rememberNow(ticking: Boolean): Instant {
    val now by produceState(Instant.now(), ticking) {
        value = Instant.now()
        while (ticking) {
            delay(1000 - (System.currentTimeMillis() % 1000))
            value = Instant.now()
        }
    }
    return now
}

@Composable
private fun attributionTitle(session: PracticeSession, goals: Map<java.util.UUID, WeeklyGoal>): String =
    when (val attribution = session.attribution) {
        is Attribution.Goal -> goals[attribution.goalId]?.let { goalTitle(it) } ?: activityName(attribution.activityTypeCode)
        is Attribution.AdHoc -> activityName(attribution.activityTypeCode)
        Attribution.General -> stringResource(R.string.general_practice)
    }

@Composable
private fun TimerContent(session: PracticeSession, viewModel: SessionViewModel) {
    val now = rememberNow(ticking = !session.isPaused)
    var changing by rememberSaveable { mutableStateOf(false) }
    var pendingChange by remember { mutableStateOf<Attribution?>(null) }
    val code = session.attribution.activityTypeCode

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(activityIcon(code ?: ActivityType.GENERAL), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        Text(attributionTitle(session, viewModel.goals), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            clockText(session.segmentElapsedSeconds(now)),
            style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
            color = if (session.isPaused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        if (session.isPaused) Text(stringResource(R.string.session_paused), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (session.hasMultipleSegments) {
            Text(stringResource(R.string.total_this_session, clockText(session.elapsedSeconds(now))), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        (session.attribution as? Attribution.Goal)?.let { attribution -> viewModel.goals[attribution.goalId] }?.let { goal ->
            GoalProgress(goal, session.segmentElapsedSeconds(now))
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = { if (session.isPaused) viewModel.resume() else viewModel.pause() }) {
                Icon(if (session.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, contentDescription = null)
                Text(stringResource(if (session.isPaused) R.string.resume else R.string.pause), modifier = Modifier.padding(start = 8.dp))
            }
            Button(onClick = viewModel::stop) {
                Icon(Icons.Rounded.Stop, contentDescription = null)
                Text(stringResource(R.string.stop), modifier = Modifier.padding(start = 8.dp))
            }
        }
        OutlinedButton(onClick = { changing = true }) {
            Icon(Icons.Rounded.SwapHoriz, contentDescription = null)
            Text(stringResource(R.string.change), modifier = Modifier.padding(start = 8.dp))
        }
    }

    if (changing) {
        ChangeSheet(
            session = session,
            goals = viewModel.goals,
            onDismiss = { changing = false },
            onPick = { target ->
                changing = false
                // The segment being left never reaches the review, so anything it needs is asked
                // now: how many, for a countable activity, and which goal, when several fit.
                val needsCount = code != null && ActivityType.hasQuantityUnit(code)
                val candidates = viewModel.outgoingCandidates()
                if (needsCount || candidates.size > 1) pendingChange = target
                else viewModel.change(target, null, candidates.singleOrNull()?.id)
            },
        )
    }
    pendingChange?.let { target ->
        LeaveSegmentDialog(
            activityName = activityName(code ?: ActivityType.GENERAL),
            asksCount = code != null && ActivityType.hasQuantityUnit(code),
            candidates = viewModel.outgoingCandidates(),
            onDone = { count, goalId -> viewModel.change(target, count, goalId); pendingChange = null },
            onDismiss = { pendingChange = null },
        )
    }
}

/** A plan goal's progress with this segment projected in, for duration goals. */
@Composable
private fun GoalProgress(goal: WeeklyGoal, segmentSeconds: Int) {
    val projected = if (goal.targetType == TargetType.DURATION) goal.copy(currentValue = goal.currentValue + segmentSeconds) else goal
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(progress = { (projected.progressPercentage / 100).toFloat() }, modifier = Modifier.fillMaxWidth())
        Text(progressText(projected), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (goal.targetType == TargetType.DURATION && projected.currentValue >= goal.targetValue) {
            Text(stringResource(R.string.target_reached), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** "Change": another goal from this session's picks, something outside the plan, or general practice. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangeSheet(session: PracticeSession, goals: Map<java.util.UUID, WeeklyGoal>, onDismiss: () -> Unit, onPick: (Attribution) -> Unit) {
    var choosingOther by rememberSaveable { mutableStateOf(false) }
    val currentGoalId = (session.attribution as? Attribution.Goal)?.goalId
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.change_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(8.dp))
            if (choosingOther) {
                AdHocPicker(onPick)
                Spacer(Modifier.height(16.dp))
                return@Column
            }
            session.availableGoalIds.filter { it != currentGoalId }.mapNotNull(goals::get).forEach { goal ->
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { Icon(activityIcon(goal.activityType.code), contentDescription = null) },
                    headlineContent = { Text(goalTitle(goal)) },
                    supportingContent = { Text(progressText(goal)) },
                    modifier = Modifier.selectable(selected = false, onClick = { onPick(PracticeSession.attributionFor(goal)) }, role = Role.Button),
                )
            }
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(R.string.something_else)) },
                modifier = Modifier.selectable(selected = false, onClick = { choosingOther = true }, role = Role.Button),
            )
            if (session.attribution != Attribution.General) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(stringResource(R.string.general_practice)) },
                    modifier = Modifier.selectable(selected = false, onClick = { onPick(Attribution.General) }, role = Role.Button),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Asked when switching away from a segment: how many (for a countable activity) and, for a
 * "Something else" stretch that fits several goals, which one it counts toward, never a guess.
 */
@Composable
private fun LeaveSegmentDialog(
    activityName: String,
    asksCount: Boolean,
    candidates: List<WeeklyGoal>,
    onDone: (Int?, java.util.UUID?) -> Unit,
    onDismiss: () -> Unit,
) {
    var count by rememberSaveable { mutableStateOf("") }
    var goalId by rememberSaveable { mutableStateOf(candidates.singleOrNull()?.id?.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (asksCount) R.string.change_count_title else R.string.counts_toward)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (asksCount) {
                    Text(stringResource(R.string.change_count_body, activityName))
                    CountField(count) { count = it }
                }
                if (candidates.size > 1) {
                    if (asksCount) Text(stringResource(R.string.counts_toward), style = MaterialTheme.typography.labelLarge)
                    candidates.forEach { goal ->
                        ChoiceRow(goalTitle(goal), progressText(goal), selected = goalId == goal.id.toString()) { goalId = goal.id.toString() }
                    }
                    ChoiceRow(stringResource(R.string.not_counted), null, selected = goalId == null) { goalId = null }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(count.toIntOrNull(), goalId?.let(java.util.UUID::fromString)) }) { Text(stringResource(R.string.continue_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun CountField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(4)) },
        label = { Text(stringResource(R.string.how_many_completed)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A quick-start session: say what it was, or log it as general practice. */
@Composable
private fun CategorizeContent(viewModel: SessionViewModel) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.what_did_you_train), style = MaterialTheme.typography.headlineSmall)
        ActivityGrid(onPick = viewModel::categorize)
        TextButton(onClick = { viewModel.save(null, null, null, emptyList()) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.skip_categorize))
        }
    }
}

/**
 * Every Stop reviews before saving, so a timer left running overnight can be corrected (iOS).
 * Count, time control and topics appear only where they mean something.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewContent(session: PracticeSession, code: String, viewModel: SessionViewModel) {
    val end = session.stoppedAt ?: Instant.now()
    var count by rememberSaveable { mutableStateOf("") }
    var timeControl by rememberSaveable { mutableStateOf(session.attribution.timeControl) }
    val topics = remember { mutableStateListOf<CoachingTopic>() }
    var editingDuration by rememberSaveable { mutableStateOf(false) }
    var confirmingDiscard by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.session_complete), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 8.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(activityIcon(code), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                headlineContent = { Text(attributionTitle(session, viewModel.goals).takeIf { session.attribution !is Attribution.General } ?: activityName(code)) },
                supportingContent = {
                    Column {
                        Text(stringResource(R.string.this_activity) + " · " + clockText(session.finalSegmentSeconds(end)))
                        if (session.hasMultipleSegments) Text(stringResource(R.string.total_this_session, clockText(session.totalSessionSeconds(end))))
                    }
                },
                trailingContent = {
                    IconButton(onClick = { editingDuration = true }) { Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.edit_duration)) }
                },
            )
        }

        if (ActivityType.hasQuantityUnit(code)) CountField(count) { count = it }
        if (code == ActivityType.PLAYING) TimeControlPicker(timeControl) { timeControl = it }
        if (code == ActivityType.COACHING) {
            Text(stringResource(R.string.coaching_topics), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CoachingTopic.entries.forEach { topic ->
                    FilterChip(
                        selected = topic in topics,
                        onClick = { if (topic in topics) topics.remove(topic) else topics.add(topic) },
                        label = { Text(stringResource(topicName(topic))) },
                    )
                }
            }
        }

        if (session.attribution !is Attribution.Goal) {
            CountsToward(viewModel.candidates(code, timeControl), session.goalCredit, viewModel::setGoalCredit)
        }

        Button(
            onClick = { viewModel.save(code, count.toIntOrNull(), timeControl, topics.toList()) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.save)) }
        TextButton(
            onClick = { confirmingDiscard = true },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.discard)) }
    }

    if (editingDuration) {
        DurationDialog(session.finalSegmentSeconds(end) / 60, onDismiss = { editingDuration = false }) {
            viewModel.overrideDuration(it)
            editingDuration = false
        }
    }
    if (confirmingDiscard) {
        AlertDialog(
            onDismissRequest = { confirmingDiscard = false },
            title = { Text(stringResource(R.string.discard_session_title)) },
            text = { Text(stringResource(R.string.discard_session_body)) },
            confirmButton = { TextButton(onClick = { confirmingDiscard = false; viewModel.discard() }) { Text(stringResource(R.string.discard)) } },
            dismissButton = { TextButton(onClick = { confirmingDiscard = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/**
 * "Counts toward": prefilled only when exactly one goal fits; two never get a guess (iOS
 * AGENTS.md §7).
 */
@Composable
private fun CountsToward(candidates: List<WeeklyGoal>, credit: GoalCredit, onChange: (GoalCredit) -> Unit) {
    if (candidates.isEmpty()) return
    val resolved = credit.resolve(candidates)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(stringResource(R.string.counts_toward), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            if (resolved == null && credit != GoalCredit.NotCounted) {
                Text(stringResource(R.string.fits_more_than_one), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            }
            candidates.forEach { goal ->
                ChoiceRow(goalTitle(goal), progressText(goal), selected = resolved?.id == goal.id) { onChange(GoalCredit.Picked(goal.id)) }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            ChoiceRow(stringResource(R.string.not_counted), null, selected = credit == GoalCredit.NotCounted) { onChange(GoalCredit.NotCounted) }
        }
    }
}

@Composable
private fun ChoiceRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier.selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    )
}

@Composable
private fun DurationDialog(initialMinutes: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var minutes by rememberSaveable { mutableStateOf(initialMinutes.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_duration)) },
        text = {
            OutlinedTextField(
                value = minutes,
                onValueChange = { minutes = it.filter(Char::isDigit).take(4) },
                label = { Text(stringResource(R.string.duration_minutes_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        },
        confirmButton = { TextButton(enabled = minutes.toIntOrNull() != null, onClick = { onSave(minutes.toInt()) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

fun topicName(topic: CoachingTopic): Int = when (topic) {
    CoachingTopic.OPENINGS -> R.string.topic_openings
    CoachingTopic.MIDDLEGAME -> R.string.topic_middlegame
    CoachingTopic.ENDGAMES -> R.string.topic_endgames
    CoachingTopic.TACTICS -> R.string.topic_tactics
    CoachingTopic.STRATEGY -> R.string.topic_strategy
    CoachingTopic.ANALYZING -> R.string.topic_analyzing
}
