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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
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
import com.knightindustries.knighttraining.core.model.LichessActivityBatch
import com.knightindustries.knighttraining.core.model.Milestone
import com.knightindustries.knighttraining.core.model.PlanTemplate
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.core.session.PracticeSession
import com.knightindustries.knighttraining.core.store.WeeklySurface
import com.knightindustries.knighttraining.core.store.openGoals
import com.knightindustries.knighttraining.feature.session.SessionStart
import com.knightindustries.knighttraining.feature.session.StartSessionSheet
import com.knightindustries.knighttraining.feature.session.rememberStartWithNotifications
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.knightindustries.knighttraining.ui.LabeledFab
import com.knightindustries.knighttraining.ui.activityIcon
import com.knightindustries.knighttraining.ui.activityName
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
    onOpenSession: () -> Unit,
    contentPadding: PaddingValues,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val offers by viewModel.lichessOffers.collectAsStateWithLifecycle()
    val milestones by viewModel.milestones.collectAsStateWithLifecycle()
    var tracking by remember { mutableStateOf<LichessActivityBatch?>(null) }
    var editingMilestone by remember { mutableStateOf<Milestone?>(null) }
    var addingMilestone by rememberSaveable { mutableStateOf(false) }
    var deletingMilestone by remember { mutableStateOf<Milestone?>(null) }
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    var editingHome by rememberSaveable { mutableStateOf(false) }
    if (editingHome) {
        EditHomeSheet(layout, onChange = viewModel::setLayout, onReset = viewModel::resetLayout, onDismiss = { editingHome = false })
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var choosingStart by rememberSaveable { mutableStateOf(false) }
    var pendingStart by remember { mutableStateOf<SessionStart?>(null) }
    val startWithNotifications = rememberStartWithNotifications {
        pendingStart?.let(viewModel::start)
        pendingStart = null
        onOpenSession()
    }

    fun start(start: SessionStart) {
        choosingStart = false
        if (session != null) return onOpenSession()
        pendingStart = start
        startWithNotifications()
    }
    val openGoals = (state as? HomeState.Loaded)?.surface?.plan?.openGoals().orEmpty()
    val onStartSession = { if (session != null) onOpenSession() else choosingStart = true }
    val onStartGoal = { goal: WeeklyGoal -> start(SessionStart.goals(listOf(goal) + openGoals.filter { it.id != goal.id })) }

    if (choosingStart) {
        StartSessionSheet(openGoals, onDismiss = { choosingStart = false }, onStart = ::start)
    }
    var addingMoreTo by remember { mutableStateOf<WeeklyGoal?>(null) }
    addingMoreTo?.let { goal ->
        AddMoreSheet(
            goal,
            onDismiss = { addingMoreTo = null },
            onRaise = { viewModel.raiseTarget(goal, it); addingMoreTo = null },
            onAddBonus = { viewModel.addBonus(goal.weeklyPlanId, it); addingMoreTo = null },
        )
    }

    tracking?.let { batch ->
        TrackLichessSheet(
            batch,
            onDismiss = { tracking = null },
            onAdd = { duration, puzzles -> viewModel.acceptOffer(batch, duration, puzzles); tracking = null },
        )
    }
    if (addingMilestone || editingMilestone != null) {
        MilestoneSheet(
            editing = editingMilestone,
            onDismiss = { addingMilestone = false; editingMilestone = null },
            onSave = { viewModel.saveMilestone(it); addingMilestone = false; editingMilestone = null },
        )
    }
    deletingMilestone?.let { milestone ->
        DeleteMilestoneDialog(onConfirm = { viewModel.deleteMilestone(milestone); deletingMilestone = null }, onDismiss = { deletingMilestone = null })
    }

    Scaffold(
        modifier = Modifier.padding(contentPadding).nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(greeting()) },
                actions = {
                    IconButton(onClick = { editingHome = true }) {
                        Icon(Icons.Rounded.DashboardCustomize, contentDescription = stringResource(R.string.edit_home))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            if (state is HomeState.Loaded) {
                LabeledFab(Icons.Rounded.PlayArrow, stringResource(if (session != null) R.string.session_in_progress else R.string.start_session), onStartSession)
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
                session?.let { item { SessionBanner(it, onOpenSession) } }
                offers.firstOrNull()?.let { offer ->
                    item(key = offer.id) { LichessOfferBanner(offer, onTrack = { tracking = offer }, onDismiss = { viewModel.declineOffer(offer) }) }
                }
                item { PlanCard(current.surface, current.weekEnd, onCreatePlan, onPlanThisWeek, onStartGoal) { addingMoreTo = it } }
                homeTiles(
                    layout,
                    TileData(current.surface, current.stats, current.lastWeek, milestones),
                    MilestoneActions(
                        onToggle = viewModel::toggleMilestone,
                        onAdd = { addingMilestone = true },
                        onEdit = { editingMilestone = it },
                        onDelete = { deletingMilestone = it },
                    ),
                )
            }
        }
    }
}

/** The way back to a session left running (or stopped and not yet saved). */
@Composable
private fun SessionBanner(session: PracticeSession, onOpen: () -> Unit) {
    Card(onClick = onOpen, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(Icons.Rounded.Timer, contentDescription = null) },
            headlineContent = {
                Text(
                    stringResource(
                        when {
                            !session.isRunning -> R.string.session_needs_saving
                            session.isPaused -> R.string.session_paused
                            else -> R.string.session_in_progress
                        },
                    ),
                )
            },
            supportingContent = session.attribution.activityTypeCode?.let { { Text(activityName(it)) } },
            trailingContent = { Text(stringResource(R.string.open), style = MaterialTheme.typography.labelLarge) },
        )
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
    onAddMore: (WeeklyGoal) -> Unit,
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
                        pluralStringResource(R.plurals.goals_done, plan.goalCount, plan.completedGoalCount, plan.goalCount) + " · " +
                            weekRange(plan.weekStart, weekEnd ?: plan.nextWeekStart),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                plan.weeklyGoals.forEach { goal -> GoalRow(goal, onStart = { onStartGoal(goal) }, onAddMore = { onAddMore(goal) }) }
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

/**
 * A goal and its progress. A completed one can still be trained (the target is a floor) and
 * offers "Add more".
 */
@Composable
private fun GoalRow(goal: WeeklyGoal, onStart: () -> Unit, onAddMore: () -> Unit) {
    val title = goalTitle(goal)
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            if (goal.isCompleted) Icon(Icons.Rounded.CheckCircle, contentDescription = stringResource(R.string.goal_done), tint = MaterialTheme.colorScheme.primary)
            else Icon(activityIcon(goal.activityType.code), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        overlineContent = if (goal.isBonus) ({ Text(stringResource(R.string.bonus)) }) else null,
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
            Row {
                if (goal.isCompleted) {
                    IconButton(onClick = onAddMore) { Icon(Icons.Rounded.AddCircleOutline, contentDescription = stringResource(R.string.add_more)) }
                }
                IconButton(onClick = onStart) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.start_goal, title))
                }
            }
        },
    )
}
