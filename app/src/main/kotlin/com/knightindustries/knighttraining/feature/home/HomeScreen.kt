package com.knightindustries.knighttraining.feature.home

import androidx.compose.ui.platform.LocalContext
import com.knightindustries.knighttraining.ui.durationText
import com.knightindustries.knighttraining.ui.rememberWithNotificationPermission
import com.knightindustries.knighttraining.feature.share.ShareWeekSheet
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.clickable
import com.knightindustries.knighttraining.designsystem.glowTopAppBarColors
import androidx.compose.foundation.lazy.rememberLazyListState
import com.knightindustries.knighttraining.ui.ScrollToTopOnReselect
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalTextStyle
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import com.knightindustries.knighttraining.ui.AnimatedLinearProgress
import com.knightindustries.knighttraining.ui.AnimatedValueText
import com.knightindustries.knighttraining.ui.RevealedTitle
import com.knightindustries.knighttraining.feedback.FeedbackEvent
import com.knightindustries.knighttraining.feedback.rememberFeedback
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
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Timer
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.knightindustries.knighttraining.ui.LabeledFab
import com.knightindustries.knighttraining.ui.activityIcon
import com.knightindustries.knighttraining.ui.activityName
import com.knightindustries.knighttraining.ui.currentLocale
import com.knightindustries.knighttraining.ui.goalTitle
import com.knightindustries.knighttraining.ui.progressText
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onCreatePlan: () -> Unit,
    onPlanThisWeek: () -> Unit,
    onOpenSession: () -> Unit,
    onOpenHistory: () -> Unit = {},
    onEditPlan: (WeeklyPlan) -> Unit = {},
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
    val listState = rememberLazyListState()
    ScrollToTopOnReselect(list = listState, topBar = scrollBehavior)
    // The session banner comes and goes above the plan. Someone at the top stays at the top, rather
    // than a restored scroll index landing one item further down once the banner is gone.
    val hasSession = session != null
    LaunchedEffect(hasSession) { if (listState.firstVisibleItemIndex <= 1) listState.scrollToItem(0) }
    var choosingStart by rememberSaveable { mutableStateOf(false) }
    var pendingStart by remember { mutableStateOf<SessionStart?>(null) }
    val feedback = rememberFeedback()
    val startWithNotifications = rememberStartWithNotifications {
        pendingStart?.let {
            viewModel.start(it)
            feedback(FeedbackEvent.SessionStarted)
        }
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
    var sharingWeek by rememberSaveable { mutableStateOf(false) }
    (state as? HomeState.Loaded)?.surface?.plan?.takeIf { sharingWeek }?.let { ShareWeekSheet(it, onDismiss = { sharingWeek = false }) }

    // Once, after a week is committed. Remind me asks for notifications first.
    val offerDue by viewModel.remindersOfferDue.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val withNotifications = rememberWithNotificationPermission()
    if (offerDue && session == null) {
        RemindersOfferSheet(
            onRemind = { withNotifications { viewModel.answerRemindersOffer(true, context) } },
            onDecline = { viewModel.answerRemindersOffer(false, context) },
        )
    }

    // By id, so the sheet follows the goal's live progress.
    var openGoalId by rememberSaveable { mutableStateOf<String?>(null) }
    (state as? HomeState.Loaded)?.surface?.plan?.weeklyGoals?.firstOrNull { it.id.toString() == openGoalId }?.let { goal ->
        GoalDetailsSheet(goal, onStart = { onStartGoal(goal) }, onDismiss = { openGoalId = null })
    }
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
            onAdd = { duration, puzzles -> viewModel.acceptOffer(batch, duration, puzzles); feedback(FeedbackEvent.LichessActivityTracked); tracking = null },
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
                title = { RevealedTitle(greeting()) },
                actions = {
                    IconButton(onClick = { editingHome = true }) {
                        Icon(Icons.Rounded.DashboardCustomize, contentDescription = stringResource(R.string.edit_home))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = glowTopAppBarColors(),
            )
        },
        floatingActionButton = {
            if (state is HomeState.Loaded) {
                LabeledFab(Icons.Rounded.PlayArrow, stringResource(if (session != null) R.string.session_in_progress else R.string.start_session), onStartSession)
            }
        },
        // The tab's glow (`GradientBackground`) is drawn behind it.
        containerColor = Color.Transparent,
    ) { padding ->
        when (val current = state) {
            HomeState.Loading -> Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(64.dp))
                CircularProgressIndicator()
            }
            is HomeState.Loaded -> LazyColumn(
                state = listState,
                // Below the bar, not under it: the bar is clear over the glow until it collapses.
                modifier = Modifier.padding(top = padding.calculateTopPadding()),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                session?.let { item(key = "session") { SessionBanner(it, onOpenSession, Modifier.animateItem()) } }
                offers.firstOrNull()?.let { offer ->
                    item(key = offer.id) { Box(Modifier.animateItem()) { LichessOfferBanner(offer, onTrack = { tracking = offer }, onDismiss = { viewModel.declineOffer(offer) }) } }
                }
                item(key = "plan") { PlanCard(
                        current.surface, current.weekEnd, onCreatePlan, onPlanThisWeek, onStartGoal,
                        onOpenGoal = { openGoalId = it.id.toString() },
                        onEditPlan = onEditPlan,
                        onShareWeek = { sharingWeek = true },
                        modifier = Modifier.animateItem(),
                    ) { addingMoreTo = it } }
                homeTiles(
                    layout,
                    TileData(current.surface, current.stats, current.lastWeek, milestones),
                    MilestoneActions(
                        onToggle = { milestone ->
                            if (milestone.completedAt == null) feedback(FeedbackEvent.MilestoneCompleted)
                            viewModel.toggleMilestone(milestone)
                        },
                        onAdd = { addingMilestone = true },
                        onEdit = { editingMilestone = it },
                        onDelete = { deletingMilestone = it },
                    ),
                    onOpenHistory,
                )
            }
        }
    }
}

/** The way back to a session left running (or stopped and not yet saved). */
@Composable
private fun SessionBanner(session: PracticeSession, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onOpen, modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
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
    val formatter = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(currentLocale(), "MMMd"))
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
    onOpenGoal: (WeeklyGoal) -> Unit,
    onEditPlan: (WeeklyPlan) -> Unit,
    onShareWeek: (WeeklyPlan) -> Unit,
    modifier: Modifier = Modifier,
    onAddMore: (WeeklyGoal) -> Unit,
) {
    val plan = surface.plan
    // Grows and shrinks smoothly as the plan changes state or gains a bonus goal.
    Card(modifier.animateContentSize(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
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
                PlanHeader(surface, plan, weekEnd, onEditPlan, onShareWeek)
                plan.weeklyGoals.forEach { goal -> GoalRow(goal, onOpen = { onOpenGoal(goal) }, onStart = { onStartGoal(goal) }, onAddMore = { onAddMore(goal) }) }
            }
        }
    }
}

/**
 * The plan's name and where the week stands (iOS `TodayTasksHeader`): "Well done!" once every
 * goal is done, the week's tracked time large at the trailing edge (shown at 0 too), and a menu
 * to edit the plan or share the week.
 */
@Composable
private fun PlanHeader(surface: WeeklySurface, plan: WeeklyPlan, weekEnd: LocalDate?, onEditPlan: (WeeklyPlan) -> Unit, onShareWeek: (WeeklyPlan) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                PlanTemplate.displayName(surface.planName, stringResource(R.string.my_training_plan)),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            AnimatedValueText(
                if (plan.isFullyCompleted) {
                    stringResource(R.string.well_done)
                } else {
                    pluralStringResource(R.plurals.goals_done, plan.goalCount, plan.completedGoalCount, plan.goalCount) + " · " +
                        weekRange(plan.weekStart, weekEnd ?: plan.nextWeekStart)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedValueText(durationText(surface.weeklyTrackedSeconds), style = MaterialTheme.typography.headlineSmall, maxLines = 1, modifier = Modifier.padding(start = 12.dp))
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit_plan)) },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    onClick = { menu = false; onEditPlan(plan) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.share_week_title)) },
                    leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                    onClick = { menu = false; onShareWeek(plan) },
                )
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
 * A goal and its progress; tapping it opens its details. A completed one can still be trained
 * (the target is a floor) and offers "Add more".
 */
@Composable
private fun GoalRow(goal: WeeklyGoal, onOpen: () -> Unit, onStart: () -> Unit, onAddMore: () -> Unit) {
    val title = goalTitle(goal)
    ListItem(
        modifier = Modifier.clickable(onClickLabel = stringResource(R.string.goal_details), onClick = onOpen),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            // The check pops in when a goal completes, instead of swapping in place.
            Crossfade(goal.isCompleted, label = "done") { done ->
                if (done) Icon(Icons.Rounded.CheckCircle, contentDescription = stringResource(R.string.goal_done), tint = MaterialTheme.colorScheme.primary)
                else Icon(activityIcon(goal.activityType.code), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        },
        overlineContent = if (goal.isBonus) ({ Text(stringResource(R.string.bonus)) }) else null,
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AnimatedValueText(progressText(goal), style = LocalTextStyle.current)
                AnimatedLinearProgress((goal.progressPercentage / 100).toFloat(), Modifier.fillMaxWidth())
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
