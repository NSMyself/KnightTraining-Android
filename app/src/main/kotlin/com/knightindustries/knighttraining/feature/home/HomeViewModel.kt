package com.knightindustries.knighttraining.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knightindustries.knighttraining.AppContainer
import com.knightindustries.knighttraining.core.model.WeekCalendar
import com.knightindustries.knighttraining.core.model.WeeklyTrainingStats
import com.knightindustries.knighttraining.core.session.PracticeSession
import com.knightindustries.knighttraining.feature.session.SessionStart
import java.time.Instant
import java.time.LocalDate
import com.knightindustries.knighttraining.core.store.WeeklySurface
import com.knightindustries.knighttraining.core.store.observeWeeklySurface
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface HomeState {
    data object Loading : HomeState

    /** [weekEnd] is the exclusive end of this week as Home sees it: 14 days for an extended first week. */
    data class Loaded(val surface: WeeklySurface, val stats: WeeklyTrainingStats, val weekEnd: LocalDate?) : HomeState
}

/**
 * Home is the weekly surface, observed: any write anywhere updates it, nobody asks. [refresh]
 * covers the one thing a write can't: the clock crossing midnight or a week rollover while the app
 * sat in the background (iOS `MainScreen`'s `scenePhase` check).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val refreshes = MutableStateFlow(0)

    val state: StateFlow<HomeState> = refreshes
        .flatMapLatest {
            val firstLaunch = container.preferences.firstLaunch()
            container.store.observeWeeklySurface(firstLaunch = firstLaunch).map { surface ->
                HomeState.Loaded(
                    surface = surface,
                    stats = WeeklyTrainingStats.from(surface.recentActivities),
                    weekEnd = surface.plan?.effectiveNextWeekStart(firstLaunch, WeekCalendar.system()),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState.Loading)

    /** The session in progress, if any: Home shows a way back to it. */
    val session: StateFlow<PracticeSession?> = container.sessions.session

    fun refresh() {
        refreshes.value++
    }

    /** One session at a time: if one is already running, this only reopens it. */
    fun start(start: SessionStart) {
        if (container.sessions.session.value != null) return
        container.sessions.start(PracticeSession.start(Instant.now(), start.attribution, start.resourceId, start.availableGoalIds))
    }
}
