package com.knightindustries.knighttraining.feature.profile

import com.knightindustries.knighttraining.ui.ScrollToTopOnReselect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.knightindustries.knighttraining.AppContainer
import com.knightindustries.knighttraining.BuildConfig
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.model.RatingType
import com.knightindustries.knighttraining.core.model.ReminderSchedule
import com.knightindustries.knighttraining.core.model.WeekCalendar
import com.knightindustries.knighttraining.core.store.LichessProfile
import com.knightindustries.knighttraining.core.store.WeekCompletionTally
import com.knightindustries.knighttraining.core.store.currentStreak
import com.knightindustries.knighttraining.core.store.deleteAllUserData
import com.knightindustries.knighttraining.core.store.finishedResourceCount
import com.knightindustries.knighttraining.core.store.lichessAccount
import com.knightindustries.knighttraining.core.store.longestStreak
import com.knightindustries.knighttraining.core.store.observeLichessProfile
import com.knightindustries.knighttraining.core.store.totalDuration
import com.knightindustries.knighttraining.core.store.weekCompletionTally
import com.knightindustries.knighttraining.core.store.weeklyEloHistory
import com.knightindustries.knighttraining.feature.home.EditHomeSheet
import com.knightindustries.knighttraining.feature.home.HomeLayout
import com.knightindustries.knighttraining.lichess.LichessAuth
import com.knightindustries.knighttraining.reminders.ReminderScheduler
import com.knightindustries.knighttraining.ui.currentLocale
import com.knightindustries.knighttraining.ui.durationText
import com.knightindustries.knighttraining.ui.ratingTypeNameRes
import com.knightindustries.knighttraining.ui.ratingTypeOrder
import java.time.DayOfWeek
import java.time.format.TextStyle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileStats(val tally: WeekCompletionTally, val currentStreak: Int, val longestStreak: Int, val totalSeconds: Int, val resourcesFinished: Int)

class ProfileViewModel(private val container: AppContainer) : ViewModel() {
    var stats by mutableStateOf<ProfileStats?>(null)
        private set
    val feedback = container.feedback
    val reminders: StateFlow<ReminderSchedule> = container.preferences.reminderSchedule
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderSchedule.DEFAULT)

    /** Derived every time, never stored (iOS AGENTS.md §7). */
    fun refresh() {
        viewModelScope.launch {
            val store = container.store
            stats = ProfileStats(
                tally = store.weekCompletionTally(firstLaunch = container.preferences.firstLaunch()),
                currentStreak = store.currentStreak(),
                longestStreak = store.longestStreak(),
                totalSeconds = store.totalDuration(),
                resourcesFinished = store.finishedResourceCount(),
            )
        }
    }

    fun setReminders(schedule: ReminderSchedule, context: android.content.Context) {
        viewModelScope.launch {
            container.preferences.setReminderSchedule(schedule)
            ReminderScheduler.reschedule(context)
        }
    }

    val lichess: StateFlow<LichessProfile?> = container.store.observeLichessProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val lichessSignIn: StateFlow<LichessAuth.State> = container.lichessAuth.signInState

    /** Whether a login is stored; a row without one means Lichess signed the user out. */
    var lichessSignedIn by mutableStateOf(false)
        private set

    fun refreshLichess() {
        viewModelScope.launch { lichessSignedIn = container.lichessCredentials.token() != null }
    }

    /** The rating the chart follows; rapid first, as on iOS. */
    var chartRatingType by mutableStateOf(RatingType.RAPID)
        private set
    var chart by mutableStateOf<List<RatingChartPoint>>(emptyList())
        private set

    fun selectChartRatingType(type: String) {
        chartRatingType = type
        loadChart()
    }

    /** The last [CHART_WEEKS] weeks: time studied in each, and the rating at its end. */
    fun loadChart() {
        viewModelScope.launch {
            val store = container.store
            val account = store.lichessAccount() ?: run { chart = emptyList(); return@launch }
            val calendar = WeekCalendar.system()
            val ratings = store.weeklyEloHistory(account.id, chartRatingType, weeks = CHART_WEEKS)
            chart = ratings.map { week ->
                val seconds = store.totalDuration(calendar.startOf(week.weekStart), calendar.startOf(week.weekStart.plusDays(7)))
                RatingChartPoint(week.weekStart, seconds, week.rating)
            }
        }
    }

    fun connectLichess(context: android.content.Context) = container.lichessAuth.beginSignIn(context)

    fun disconnectLichess() {
        viewModelScope.launch {
            container.lichessAuth.signOut()
            refreshLichess()
        }
    }

    val homeLayout: StateFlow<HomeLayout> = container.preferences.homeLayout
        .map { ids -> ids?.let(HomeLayout::fromIds) ?: HomeLayout.DEFAULT }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeLayout.DEFAULT)

    fun setHomeLayout(layout: HomeLayout) {
        viewModelScope.launch { container.preferences.setHomeLayout(layout.ids) }
    }

    fun resetHomeLayout() {
        viewModelScope.launch { container.preferences.resetHomeLayout() }
    }

    fun replayIntro() {
        viewModelScope.launch { container.preferences.setOnboardingCompleted(false) }
    }

    /**
     * Wipes every user table and signs out of Lichess, like iOS's `UserDataEraser`. The token is
     * revoked and forgotten first, while it can still be read. Preferences, the Home layout and the
     * course catalog survive.
     */
    fun deleteAll() {
        viewModelScope.launch {
            container.sessions.clear()
            container.lichessAuth.signOut()
            container.store.deleteAllUserData()
            refresh()
            refreshLichess()
        }
    }
}

/** Profile & settings: training stats, the Lichess connection, reminders. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(viewModel: ProfileViewModel, onRequestNotifications: (() -> Unit) -> Unit, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    val lichess by viewModel.lichess.collectAsStateWithLifecycle()
    val signIn by viewModel.lichessSignIn.collectAsStateWithLifecycle()
    var confirmingDisconnect by rememberSaveable { mutableStateOf(false) }
    var editingHome by rememberSaveable { mutableStateOf(false) }
    val homeLayout by viewModel.homeLayout.collectAsStateWithLifecycle()
    if (editingHome) {
        EditHomeSheet(homeLayout, onChange = viewModel::setHomeLayout, onReset = viewModel::resetHomeLayout, onDismiss = { editingHome = false })
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
        viewModel.refreshLichess()
    }
    // A finished sign-in changes the login without a resume of its own.
    LaunchedEffect(signIn, lichess?.account?.id) { viewModel.refreshLichess() }
    // New ratings (a sync) redraw the chart.
    LaunchedEffect(lichess) { viewModel.loadChart() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scrollState = rememberScrollState()
    ScrollToTopOnReselect(scroll = scrollState, topBar = scrollBehavior)

    Scaffold(
        modifier = Modifier.padding(contentPadding).nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(scrollState).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            viewModel.stats?.let { stats ->
                Text(stringResource(R.string.your_training), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat(stringResource(R.string.weeks_completed), stringResource(R.string.weeks_completed_value, stats.tally.completed, stats.tally.committed), Modifier.weight(1f))
                    Stat(stringResource(R.string.total_study_time), durationText(stats.totalSeconds), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat(stringResource(R.string.current_streak), pluralStringResource(R.plurals.weeks_count, stats.currentStreak, stats.currentStreak), Modifier.weight(1f))
                    Stat(stringResource(R.string.longest_streak), pluralStringResource(R.plurals.weeks_count, stats.longestStreak, stats.longestStreak), Modifier.weight(1f))
                    Stat(stringResource(R.string.resources_finished), stats.resourcesFinished.toString(), Modifier.weight(1f))
                }
            }

            LichessCard(
                profile = lichess,
                signedIn = viewModel.lichessSignedIn,
                signIn = signIn,
                onConnect = { viewModel.connectLichess(context) },
                onDisconnect = { confirmingDisconnect = true },
                chart = viewModel.chart,
                chartRatingType = viewModel.chartRatingType,
                onChartRatingType = viewModel::selectChartRatingType,
            )

            Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(stringResource(R.string.reminders)) },
                    supportingContent = { Text(stringResource(R.string.reminders_body)) },
                    trailingContent = {
                        Switch(
                            checked = reminders.isEnabled,
                            onCheckedChange = { on ->
                                val update = { viewModel.setReminders(reminders.copy(isEnabled = on), context) }
                                if (on) onRequestNotifications(update) else update()
                            },
                        )
                    },
                )
                if (reminders.isEnabled) {
                    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.reminder_days), style = MaterialTheme.typography.labelLarge)
                        // Monday first, labelled in the user's language.
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DayOfWeek.entries.forEach { day ->
                                FilterChip(
                                    selected = day in reminders.weekdays,
                                    onClick = {
                                        val days = if (day in reminders.weekdays) reminders.weekdays - day else reminders.weekdays + day
                                        viewModel.setReminders(reminders.copy(weekdays = days), context)
                                    },
                                    label = { Text(day.getDisplayName(TextStyle.SHORT, currentLocale())) },
                                )
                            }
                        }
                        if (reminders.weekdays.isEmpty()) {
                            Text(stringResource(R.string.reminder_no_days), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(stringResource(R.string.reminder_time)) },
                            trailingContent = {
                                Text(java.time.LocalTime.of(reminders.hour, reminders.minute).format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)))
                            },
                            modifier = Modifier.clickable { pickingTime = true },
                        )
                    }
                }
            }

            // Writes straight through, no save button, like reminders.
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                val sounds by viewModel.feedback.soundsEnabled.collectAsStateWithLifecycle()
                val haptics by viewModel.feedback.hapticsEnabled.collectAsStateWithLifecycle()
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(stringResource(R.string.sounds)) },
                    supportingContent = { Text(stringResource(R.string.sounds_body)) },
                    trailingContent = { Switch(checked = sounds, onCheckedChange = null) },
                    modifier = Modifier.toggleable(value = sounds, role = Role.Switch, onValueChange = viewModel.feedback::setSoundsEnabled),
                )
                HorizontalDivider()
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(stringResource(R.string.haptics)) },
                    supportingContent = { Text(stringResource(R.string.haptics_body)) },
                    trailingContent = { Switch(checked = haptics, onCheckedChange = null) },
                    modifier = Modifier.toggleable(value = haptics, role = Role.Switch, onValueChange = viewModel.feedback::setHapticsEnabled),
                )
            }
            Text(
                stringResource(R.string.sounds_footer),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(stringResource(R.string.edit_home)) },
                    modifier = Modifier.clickable { editingHome = true },
                )
                HorizontalDivider()
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(stringResource(R.string.replay_intro)) },
                    modifier = Modifier.clickable { viewModel.replayIntro() },
                )
                HorizontalDivider()
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(stringResource(R.string.delete_all_data), color = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { confirmingDelete = true },
                )
            }
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }

    if (pickingTime) {
        val state = rememberTimePickerState(reminders.hour, reminders.minute)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = { viewModel.setReminders(reminders.copy(hour = state.hour, minute = state.minute), context); pickingTime = false }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmingDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmingDisconnect = false },
            title = { Text(stringResource(R.string.lichess_disconnect_title)) },
            text = { Text(stringResource(R.string.lichess_disconnect_body)) },
            confirmButton = {
                TextButton(onClick = { confirmingDisconnect = false; viewModel.disconnectLichess() }) {
                    Text(stringResource(R.string.lichess_disconnect), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmingDisconnect = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.delete_all_title)) },
            text = { Text(stringResource(R.string.delete_all_body)) },
            confirmButton = { TextButton(onClick = { confirmingDelete = false; viewModel.deleteAll() }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(12.dp).fillMaxWidth()) {
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LichessCard(
    profile: LichessProfile?,
    signedIn: Boolean,
    signIn: LichessAuth.State,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    chart: List<RatingChartPoint>,
    chartRatingType: String,
    onChartRatingType: (String) -> Unit,
) {
    Text(stringResource(R.string.lichess), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        when {
            signIn == LichessAuth.State.Working || (signedIn && profile == null) -> ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(R.string.lichess_connecting)) },
                trailingContent = { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) },
            )
            signedIn && profile != null -> {
                val account = profile.account
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(listOfNotNull(account.title, account.username).joinToString(" ")) },
                    supportingContent = { Text(stringResource(R.string.lichess)) },
                    trailingContent = { TextButton(onClick = onDisconnect) { Text(stringResource(R.string.lichess_disconnect)) } },
                )
                val ratings = ratingTypeOrder.mapNotNull { type -> profile.ratings[type]?.let { type to it } }
                if (ratings.isNotEmpty()) {
                    // Each rating is also the chart's selector: tap one to follow it below.
                    FlowRow(
                        Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ratings.forEach { (type, rating) ->
                            FilterChip(
                                selected = type == chartRatingType,
                                onClick = { onChartRatingType(type) },
                                label = { Text("${stringResource(ratingTypeNameRes(type))} $rating") },
                            )
                        }
                    }
                    if (chart.any { it.rating != null || it.seconds > 0 }) {
                        RatingChart(chart, Modifier.padding(16.dp))
                    } else {
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
            else -> ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(R.string.lichess)) },
                supportingContent = {
                    val body = when {
                        signIn == LichessAuth.State.Failed -> R.string.lichess_failed
                        profile != null -> R.string.lichess_reconnect_body
                        else -> R.string.lichess_connect_body
                    }
                    Text(stringResource(body))
                },
                trailingContent = { Button(onClick = onConnect) { Text(stringResource(R.string.lichess_connect)) } },
            )
        }
    }
}

private const val CHART_WEEKS = 4
