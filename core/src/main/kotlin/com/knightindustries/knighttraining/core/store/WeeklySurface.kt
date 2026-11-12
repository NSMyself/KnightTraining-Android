package com.knightindustries.knighttraining.core.store

import com.knightindustries.knighttraining.core.model.Activity
import com.knightindustries.knighttraining.core.model.WeekCalendar
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import com.knightindustries.knighttraining.core.persistence.toDomain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Everything the weekly surface (Home, widgets, the reminder scheduler) is derived from, read in
 * one pass. Every iOS bug in this area was two readers disagreeing about the same week; reading it
 * once removes the disagreement by construction.
 *
 * Equality is load-bearing: [observeWeeklySurface] drops values equal to the last, so a write that
 * moves nothing here goes no further. No timestamp of its own for the same reason.
 */
data class WeeklySurface(
    /** The effective week containing the fetch's `now`, first-week grace included. */
    val weekStart: LocalDate,
    /** This week's committed plan; `null` doesn't mean no templates exist (see [hasPlanTemplates]). */
    val plan: WeeklyPlan?,
    /** The plan template's stored name. The app applies the "My Training Plan" fallback. */
    val planName: String?,
    val hasPlanTemplates: Boolean,
    /** Completed activities started since [weekStart], most recently ended first. Nothing filtered for length. */
    val recentActivities: List<Activity>,
    /** Anything logged today, ongoing included: drives reminder suppression. */
    val trainedToday: Boolean,
    val allTimeStudySeconds: Int,
    val continueResource: ContinueResource?,
    val currentStreak: Int,
    /** Tracked seconds per day for the trailing [WIDGET_GRID_DAYS] days: the Daily Activity widget. */
    val recentDailyDurationSeconds: Map<LocalDate, Int>,
    val hasLichessAccount: Boolean,
    /** The most recent rating across every type, `null` before the first sync. */
    val currentRating: Int?,
    val currentRatingType: String?,
) {
    /** The "continue" resource, plus whether a this-week surface may claim it. */
    data class ContinueResource(
        val id: UUID,
        val title: String,
        val progressPercentage: Int,
        /** Touched since [weekStart], by a session or by moving its progress by hand. */
        val wasAccessedThisWeek: Boolean,
    ) {
        val isWorthSurfacing: Boolean get() = wasAccessedThisWeek
    }

    /** Seconds trained this week, plan or no plan. */
    val weeklyTrackedSeconds: Int get() = recentActivities.sumOf { it.duration }

    val weeklyTrackedMinutes: Int get() = weeklyTrackedSeconds / 60

    /** Distinct sittings, not rows; an activity without a session is a sitting of its own. */
    val sessionsThisWeekCount: Int get() = recentActivities.map { it.sessionId ?: it.id }.toSet().size

    /** From the plan, never from activities: completion is a property of the goals. */
    val completionPercentage: Int get() = plan?.completionPercentage?.toInt() ?: 0

    val goals: List<WeeklyGoal> get() = plan?.weeklyGoals.orEmpty()

    companion object {
        /** 16 weeks: the Training History grid's depth. */
        const val WIDGET_GRID_DAYS = 16 * 7
    }
}

/** One consistent read of the surface. */
suspend fun TrainingStore.weeklySurface(
    at: Instant = now(),
    firstLaunch: Instant? = null,
    calendar: WeekCalendar = calendar(),
): WeeklySurface = read { fetchWeeklySurface(at, firstLaunch, calendar) }

private suspend fun TrainingStore.fetchWeeklySurface(at: Instant, firstLaunch: Instant?, calendar: WeekCalendar): WeeklySurface {
    val weekStart = WeeklyPlan.effectiveWeekStart(at, firstLaunch, calendar)
    val weekStartInstant = calendar.startOf(weekStart)
    val today = calendar.localDate(at)

    val plan = withGoals(weekDao.plan(weekStart))
    val planName = plan?.let { planDao.templateName(it.planTemplateId) }

    // Scoped to the goals' week, never a trailing seven days (iOS 2026-10-09: a rolling window
    // disagreed with the week's goals in both directions). Fetched by end with two days of slack,
    // then narrowed by start, so a session is judged on when the user sat down.
    val daysSinceWeekStart = java.time.temporal.ChronoUnit.DAYS.between(weekStart, today).toInt()
    val recent = recentCompletedActivitiesInTransaction(at, daysSinceWeekStart + 2).filter { it.startedAt >= weekStartInstant }

    val daily = recentCompletedActivitiesInTransaction(at, WeeklySurface.WIDGET_GRID_DAYS)
        .groupBy { calendar.localDate(it.startedAt) }
        .mapValues { (_, activities) -> activities.sumOf { it.duration } }

    val continueResource = resourceDao.continueResource()?.toDomain()?.let {
        WeeklySurface.ContinueResource(
            id = it.id,
            title = it.title,
            progressPercentage = it.progressPercentage,
            wasAccessedThisWeek = (it.lastAccessedAt ?: Instant.MIN) >= weekStartInstant,
        )
    }

    val lichessAccountId = accountDao.lichessAccountId()
    val rating = lichessAccountId?.let { accountDao.latestRating(it) }

    return WeeklySurface(
        weekStart = weekStart,
        plan = plan,
        planName = planName,
        hasPlanTemplates = planDao.templateCount() > 0,
        recentActivities = recent,
        trainedToday = activityDao.countStartedBetween(calendar.startOf(today), calendar.startOf(today.plusDays(1))) > 0,
        allTimeStudySeconds = activityDao.totalCompletedDuration().toInt(),
        continueResource = continueResource,
        currentStreak = currentStreak(weekDao.completedWeekStarts(), at, calendar),
        recentDailyDurationSeconds = daily,
        hasLichessAccount = lichessAccountId != null,
        currentRating = rating?.rating,
        currentRatingType = rating?.ratingType,
    )
}

/** Every table the surface reads. Keep in step with [fetchWeeklySurface]. */
private val surfaceTables = arrayOf(
    "weekly_plan", "weekly_goal", "plan_template", "activity", "resource", "account", "elo_snapshot",
)

/**
 * A live stream of surfaces: one on collection, then one per committed write that changes it.
 *
 * [now] is a function so each fetch is dated correctly. Nothing fires on its own at midnight:
 * nothing is written then, so the app's foreground hook still has to refresh across a rollover
 * (iOS `MainScreen`'s `scenePhase` check).
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TrainingStore.observeWeeklySurface(
    now: () -> Instant = this.now,
    firstLaunch: Instant? = null,
    calendar: () -> WeekCalendar = this.calendar,
): Flow<WeeklySurface> =
    db.invalidationTracker.createFlow(*surfaceTables)
        .mapLatest { weeklySurface(now(), firstLaunch, calendar()) }
        .distinctUntilChanged()
