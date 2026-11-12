package com.knightindustries.knighttraining.feature.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.knightindustries.knighttraining.AppContainer
import com.knightindustries.knighttraining.core.model.Activity
import com.knightindustries.knighttraining.core.model.ActivityType
import com.knightindustries.knighttraining.core.model.CoachingTopic
import com.knightindustries.knighttraining.core.model.TimeControl
import com.knightindustries.knighttraining.core.model.WeekCalendar
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import com.knightindustries.knighttraining.core.store.ActivityEdit
import com.knightindustries.knighttraining.core.store.Change
import com.knightindustries.knighttraining.core.store.SessionSegmentLog
import com.knightindustries.knighttraining.core.store.deleteLoggedActivity
import com.knightindustries.knighttraining.core.store.logSession
import com.knightindustries.knighttraining.core.store.observeActivities
import com.knightindustries.knighttraining.core.store.observeWeeklyPlans
import com.knightindustries.knighttraining.core.store.updateActivity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class WeekLogState(
    val weekStart: LocalDate,
    /** Exclusive end, extension included. */
    val weekEnd: LocalDate,
    val plan: WeeklyPlan?,
    val activities: List<Activity>,
    /** Only the current week's sessions can be logged, edited or deleted (iOS AGENTS.md §7). */
    val isCurrentWeek: Boolean,
)

/** What the session editor hands back, for a new session or a correction. */
data class SessionDraft(
    val activityTypeCode: String,
    val startedAt: Instant,
    val durationSeconds: Int,
    val count: Int?,
    val timeControlLabel: String?,
    val notes: String?,
    val topics: List<CoachingTopic>,
    /** The goal it counts toward; `null` counts toward none. */
    val goalId: UUID?,
)

@OptIn(ExperimentalCoroutinesApi::class)
class WeekLogViewModel(private val container: AppContainer, private val weekStart: LocalDate) : ViewModel() {
    private val calendar get() = WeekCalendar.system()

    val state: StateFlow<WeekLogState?> = flow { emit(container.preferences.firstLaunch()) }
        .flatMapLatest { firstLaunch ->
            val plans = container.store.observeWeeklyPlans(limit = 52).map { list -> list.firstOrNull { it.weekStart == weekStart } }
            plans.flatMapLatest { plan ->
                val end = plan?.effectiveNextWeekStart(firstLaunch, calendar) ?: weekStart.plusDays(7)
                container.store.observeActivities(calendar.startOf(weekStart), calendar.startOf(end)).map { activities ->
                    WeekLogState(
                        weekStart = weekStart,
                        weekEnd = end,
                        plan = plan,
                        activities = activities,
                        isCurrentWeek = WeeklyPlan.effectiveWeekStart(Instant.now(), firstLaunch, calendar) == weekStart,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The goals [code] could count toward this week, by the shared rule. */
    fun candidates(code: String, timeControlLabel: String?): List<WeeklyGoal> =
        WeeklyGoal.creditCandidates(code, null, timeControlLabel?.let(TimeControl::parse)?.category, state.value?.plan?.weeklyGoals.orEmpty())

    /** A session that was never timed: one transaction, credited like any other. */
    fun log(draft: SessionDraft) {
        viewModelScope.launch {
            val goal = draft.goalId?.let { id -> state.value?.plan?.weeklyGoals?.firstOrNull { it.id == id } }
            val type = container.store.activityType(draft.activityTypeCode)!!
            val activity = Activity(
                activityType = type, startedAt = draft.startedAt, endedAt = draft.startedAt.plusSeconds(draft.durationSeconds.toLong()),
                duration = draft.durationSeconds, count = draft.count, notes = draft.notes, sessionId = UUID.randomUUID(),
                timeControlLabel = draft.timeControlLabel.takeIf { type.code == ActivityType.PLAYING },
                coachingTopics = if (type.code == ActivityType.COACHING) draft.topics else emptyList(),
            )
            val progress = goal?.creditDelta(draft.durationSeconds, draft.count)?.let { SessionSegmentLog.GoalProgress(goal.id, it) }
            container.store.logSession(listOf(SessionSegmentLog(activity, progress)))
        }
    }

    /** A correction moves the goal credit by the exact difference (`TrainingStore.updateActivity`). */
    fun edit(activity: Activity, draft: SessionDraft) {
        viewModelScope.launch {
            container.store.updateActivity(
                activity.id,
                ActivityEdit(
                    duration = draft.durationSeconds,
                    count = draft.count,
                    resource = activity.resource,
                    notes = draft.notes,
                    startedAt = Change.Set(draft.startedAt),
                    timeControlLabel = Change.Set(draft.timeControlLabel.takeIf { draft.activityTypeCode == ActivityType.PLAYING }),
                    weeklyGoalId = Change.Set(draft.goalId),
                    coachingTopics = Change.Set(if (draft.activityTypeCode == ActivityType.COACHING) draft.topics else emptyList()),
                    activityTypeId = Change.Set(container.store.activityType(draft.activityTypeCode)!!.id),
                ),
            )
        }
    }

    fun delete(activity: Activity) {
        viewModelScope.launch { container.store.deleteLoggedActivity(activity.id) }
    }
}
