package com.knightindustries.knighttraining.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knightindustries.knighttraining.AppContainer
import com.knightindustries.knighttraining.core.model.WeekCalendar
import com.knightindustries.knighttraining.core.model.WeeklyTrainingStats
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.core.session.PracticeSession
import com.knightindustries.knighttraining.core.store.addBonusGoal
import com.knightindustries.knighttraining.core.store.increaseGoalTarget
import com.knightindustries.knighttraining.core.store.resource
import com.knightindustries.knighttraining.feature.plan.DraftBlock
import kotlinx.coroutines.launch
import com.knightindustries.knighttraining.core.model.LichessActivityBatch
import com.knightindustries.knighttraining.core.model.Milestone
import com.knightindustries.knighttraining.core.model.MilestonesSnapshot
import com.knightindustries.knighttraining.core.store.acceptLichessActivityBatch
import com.knightindustries.knighttraining.core.store.completeMilestone
import com.knightindustries.knighttraining.core.store.declineLichessActivityBatch
import com.knightindustries.knighttraining.core.store.deleteMilestone
import com.knightindustries.knighttraining.core.store.observeMilestonesSnapshot
import com.knightindustries.knighttraining.core.store.observePendingLichessActivityBatches
import com.knightindustries.knighttraining.core.store.reopenMilestone
import com.knightindustries.knighttraining.core.store.saveMilestone
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

    /** Pending Lichess offers, oldest first; Home shows the first. */
    val lichessOffers: StateFlow<List<LichessActivityBatch>> = container.store.observePendingLichessActivityBatches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val milestones: StateFlow<MilestonesSnapshot?> = container.store.observeMilestonesSnapshot()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun acceptOffer(batch: LichessActivityBatch, durationSeconds: Int, puzzleCount: Int?) {
        viewModelScope.launch {
            container.store.acceptLichessActivityBatch(batch, durationSeconds, puzzleCount, container.preferences.firstLaunch())
        }
    }

    /** Remembered: the same items are never offered again. */
    fun declineOffer(batch: LichessActivityBatch) {
        viewModelScope.launch { container.store.declineLichessActivityBatch(batch.id) }
    }

    fun toggleMilestone(milestone: Milestone) {
        viewModelScope.launch {
            if (milestone.isCompleted) container.store.reopenMilestone(milestone.id) else container.store.completeMilestone(milestone.id)
        }
    }

    fun saveMilestone(milestone: Milestone) {
        viewModelScope.launch { container.store.saveMilestone(milestone) }
    }

    fun deleteMilestone(milestone: Milestone) {
        viewModelScope.launch { container.store.deleteMilestone(milestone.id) }
    }

    fun refresh() {
        refreshes.value++
    }

    fun raiseTarget(goal: WeeklyGoal, target: Int) {
        viewModelScope.launch { container.store.increaseGoalTarget(goal.id, target) }
    }

    /** A one-week extra, never written back to the plan. */
    fun addBonus(weeklyPlanId: java.util.UUID, block: DraftBlock) {
        viewModelScope.launch {
            container.store.addBonusGoal(
                weeklyPlanId, container.store.activityType(block.activityTypeCode)!!, block.targetType, block.targetValue,
                name = block.name, resource = block.resourceId?.let { container.store.resource(it) }, timeControlLabel = block.timeControlLabel,
            )
        }
    }

    /** One session at a time: if one is already running, this only reopens it. */
    fun start(start: SessionStart) {
        if (container.sessions.session.value != null) return
        container.sessions.start(PracticeSession.start(Instant.now(), start.attribution, start.resourceId, start.availableGoalIds))
    }
}
