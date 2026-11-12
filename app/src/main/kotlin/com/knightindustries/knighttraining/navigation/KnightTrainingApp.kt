package com.knightindustries.knighttraining.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.knightindustries.knighttraining.core.store.WeeklyPlanCompleted
import com.knightindustries.knighttraining.feature.celebration.WeekCompleteDialog
import com.knightindustries.knighttraining.feature.onboarding.OnboardingScreen
import com.knightindustries.knighttraining.core.store.previousWeeklyPlan
import com.knightindustries.knighttraining.feature.review.WeeklyReviewScreen
import com.knightindustries.knighttraining.feature.review.WeeklyReviewViewModel
import androidx.compose.runtime.saveable.rememberSaveable
import com.knightindustries.knighttraining.feature.resources.AddResourceSheet
import com.knightindustries.knighttraining.feature.resources.AddResourceViewModel
import com.knightindustries.knighttraining.feature.resources.ResourceDetailScreen
import com.knightindustries.knighttraining.feature.resources.ResourceDetailViewModel
import com.knightindustries.knighttraining.feature.resources.ResourcesScreen
import com.knightindustries.knighttraining.feature.resources.ResourcesViewModel
import com.knightindustries.knighttraining.feature.plan.EditPlanScreen
import com.knightindustries.knighttraining.feature.plan.EditPlanViewModel
import com.knightindustries.knighttraining.feature.training.TrainingScreen
import com.knightindustries.knighttraining.feature.training.TrainingViewModel
import com.knightindustries.knighttraining.feature.training.WeekLogScreen
import com.knightindustries.knighttraining.feature.training.WeekLogViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import androidx.compose.ui.platform.LocalContext
import com.knightindustries.knighttraining.AppContainer
import com.knightindustries.knighttraining.session.SessionNotifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.feature.home.HomeScreen
import com.knightindustries.knighttraining.feature.home.HomeViewModel
import com.knightindustries.knighttraining.feature.plan.CreatePlanScreen
import com.knightindustries.knighttraining.feature.plan.CreatePlanViewModel
import com.knightindustries.knighttraining.feature.plan.PlanThisWeekScreen
import com.knightindustries.knighttraining.feature.plan.PlanThisWeekViewModel
import com.knightindustries.knighttraining.feature.session.SessionScreen
import com.knightindustries.knighttraining.feature.session.SessionViewModel
import com.knightindustries.knighttraining.ui.appViewModel

private data class Tab(val route: TabRoute, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(HomeRoute, R.string.tab_home, Icons.Rounded.Home),
    Tab(TrainingRoute, R.string.tab_training, Icons.Rounded.CalendarMonth),
    Tab(ResourcesRoute, R.string.tab_resources, Icons.AutoMirrored.Rounded.LibraryBooks),
    Tab(ProfileRoute, R.string.tab_profile, Icons.Rounded.Person),
)

/**
 * One back stack. The bottom bar shows only on a tab's root; other tabs sit on top of Home, so
 * back from any tab returns Home before leaving the app (Material's navigation guidance).
 */
@Composable
fun KnightTrainingApp(pendingAction: String? = null, onActionHandled: () -> Unit = {}) {
    val application = LocalContext.current.applicationContext as Application
    val container = remember { AppContainer.from(application) }
    val onboarded by container.preferences.hasCompletedOnboarding.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    when (onboarded) {
        null -> Unit // Not read yet: draw nothing rather than flash the wrong screen.
        false -> OnboardingScreen(onFinished = { scope.launch { container.preferences.completeOnboarding() } })
        true -> MainContent(pendingAction, onActionHandled)
    }
}

@Composable
private fun MainContent(pendingAction: String?, onActionHandled: () -> Unit) {
    val backStack = rememberNavBackStack(HomeRoute)
    val top = backStack.lastOrNull()
    val application = LocalContext.current.applicationContext as Application

    // The week's last goal just completed, from wherever it was logged.
    var celebration by remember { mutableStateOf<WeeklyPlanCompleted?>(null) }
    LaunchedEffect(Unit) {
        AppContainer.from(application).store.weeklyPlanCompletedEvents.collect { celebration = it }
    }
    celebration?.let { WeekCompleteDialog(it) { celebration = null } }

    // Launch offers last week's review once (iOS `RoutingScreen`), but never over a session
    // waiting to be saved: that's real tracked time the user wants back first.
    LaunchedEffect(Unit) {
        val container = AppContainer.from(application)
        if (container.sessions.session.value != null) return@LaunchedEffect
        val previous = container.store.previousWeeklyPlan(firstLaunch = container.preferences.firstLaunch()) ?: return@LaunchedEffect
        if (container.preferences.lastReviewedWeekStart() != previous.weekStart) {
            backStack.add(WeeklyReviewRoute(previous.id.toString()))
        }
    }

    // The timer notification: tapping it opens the session; its Stop opens straight on the review.
    LaunchedEffect(pendingAction) {
        val sessions = AppContainer.from(application).sessions
        when (pendingAction) {
            SessionNotifier.ACTION_STOP -> sessions.session.value?.takeIf { it.isRunning }?.let { sessions.stop() }
            SessionNotifier.ACTION_OPEN -> Unit
            else -> return@LaunchedEffect
        }
        if (sessions.session.value != null) backStack.openSession()
        onActionHandled()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (top is TabRoute) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = top == tab.route,
                            onClick = { backStack.selectTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
            modifier = Modifier.consumeWindowInsets(padding),
            entryProvider = entryProvider {
                entry<HomeRoute> {
                    HomeScreen(
                        viewModel = appViewModel { HomeViewModel(it) },
                        onCreatePlan = { backStack.add(CreatePlanRoute(commit = true)) },
                        onPlanThisWeek = { backStack.add(PlanThisWeekRoute) },
                        onOpenSession = { backStack.openSession() },
                        contentPadding = padding,
                    )
                }
                entry<SessionRoute> {
                    SessionScreen(
                        viewModel = appViewModel { SessionViewModel(it) },
                        onLeave = { backStack.removeLastOrNull() },
                        onFinished = { backStack.remove(SessionRoute) },
                    )
                }
                entry<TrainingRoute> {
                    TrainingScreen(
                        viewModel = appViewModel { TrainingViewModel(it) },
                        onOpenWeek = { backStack.add(WeekLogRoute(it.toString())) },
                        onEditPlan = { backStack.add(EditPlanRoute(it.id.toString())) },
                        // An extra plan from here doesn't reassign the week: that's Plan this week's job.
                        onCreatePlan = { backStack.add(CreatePlanRoute(commit = false)) },
                        contentPadding = padding,
                    )
                }
                entry<WeekLogRoute> { route ->
                    WeekLogScreen(
                        viewModel = appViewModel(key = route.weekStart) { WeekLogViewModel(it, LocalDate.parse(route.weekStart)) },
                        onBack = { backStack.removeLastOrNull() },
                        onReflect = { backStack.add(WeeklyReviewRoute(it.toString())) },
                    )
                }
                entry<WeeklyReviewRoute> { route ->
                    WeeklyReviewScreen(
                        viewModel = appViewModel(key = route.weeklyPlanId) { WeeklyReviewViewModel(it, UUID.fromString(route.weeklyPlanId)) },
                        onDone = { backStack.remove(route) },
                    )
                }
                entry<EditPlanRoute> { route ->
                    EditPlanScreen(
                        viewModel = appViewModel(key = route.templateId) { EditPlanViewModel(it, UUID.fromString(route.templateId)) },
                        onClose = { backStack.removeLastOrNull() },
                    )
                }
                entry<ResourcesRoute> {
                    var adding by rememberSaveable { mutableStateOf(false) }
                    // Each opening of the sheet starts blank: a fresh ViewModel per opening.
                    var addCount by rememberSaveable { mutableStateOf(0) }
                    ResourcesScreen(
                        viewModel = appViewModel { ResourcesViewModel(it) },
                        onOpen = { backStack.add(ResourceDetailRoute(it.toString())) },
                        onAdd = { addCount++; adding = true },
                        contentPadding = padding,
                    )
                    if (adding) AddResourceSheet(appViewModel(key = "add-resource-$addCount") { AddResourceViewModel(it) }) { adding = false }
                }
                entry<ResourceDetailRoute> { route ->
                    ResourceDetailScreen(
                        viewModel = appViewModel(key = route.resourceId) { ResourceDetailViewModel(it, UUID.fromString(route.resourceId)) },
                        onBack = { backStack.remove(route) },
                    )
                }
                entry<ProfileRoute> { Placeholder(R.string.tab_profile, R.string.profile_coming_soon, padding) }
                entry<CreatePlanRoute> { route ->
                    CreatePlanScreen(
                        viewModel = appViewModel { CreatePlanViewModel(it, route.commit) },
                        onClose = { backStack.removeLastOrNull() },
                        // A committing plan (from Home or "Plan this week") finishes both screens.
                        onSaved = { if (route.commit) backStack.popTo(HomeRoute) else backStack.removeLastOrNull() },
                    )
                }
                entry<PlanThisWeekRoute> {
                    PlanThisWeekScreen(
                        viewModel = appViewModel { PlanThisWeekViewModel(it) },
                        onBack = { backStack.removeLastOrNull() },
                        onCreatePlan = { backStack.add(CreatePlanRoute(commit = true)) },
                        onCommitted = { backStack.popTo(HomeRoute) },
                    )
                }
            },
        )
    }
}

/** At most one session screen on the stack. */
fun NavBackStack<NavKey>.openSession() {
    if (lastOrNull() != SessionRoute) {
        remove(SessionRoute)
        add(SessionRoute)
    }
}

private fun NavBackStack<NavKey>.selectTab(tab: TabRoute) {
    popTo(HomeRoute)
    if (tab != HomeRoute) add(tab)
}

/** Pops back to [route], keeping it; to the root if it isn't on the stack. */
private fun NavBackStack<NavKey>.popTo(route: Route) {
    while (size > 1 && last() != route) removeAt(lastIndex)
}

@Composable
private fun Placeholder(title: Int, body: Int, padding: PaddingValues) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
