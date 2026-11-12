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
import androidx.compose.runtime.Composable
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
fun KnightTrainingApp() {
    val backStack = rememberNavBackStack(HomeRoute)
    val top = backStack.lastOrNull()

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
                        onStartGoal = { },
                        onStartSession = { },
                        contentPadding = padding,
                    )
                }
                entry<TrainingRoute> { Placeholder(R.string.tab_training, R.string.coming_soon, padding) }
                entry<ResourcesRoute> { Placeholder(R.string.tab_resources, R.string.resources_coming_soon, padding) }
                entry<ProfileRoute> { Placeholder(R.string.tab_profile, R.string.profile_coming_soon, padding) }
                entry<CreatePlanRoute> { route ->
                    CreatePlanScreen(
                        viewModel = appViewModel { CreatePlanViewModel(it, route.commit) },
                        onClose = { backStack.removeLastOrNull() },
                        // A plan created from "Plan this week" commits, so both screens are done.
                        onSaved = { backStack.popTo(HomeRoute) },
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
