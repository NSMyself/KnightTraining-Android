package com.knightindustries.knighttraining.core.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** An immutable snapshot of a plan template for one week. */
data class WeeklyPlan(
    val id: UUID = UUID.randomUUID(),
    val planTemplateId: UUID,
    val weekStart: LocalDate,
    val weeklyGoals: List<WeeklyGoal> = emptyList(),
    val createdAt: Instant = Instant.now(),
) {
    /** The first day of the following calendar week: the exclusive end of a normal week. */
    val nextWeekStart: LocalDate get() = weekStart.plusDays(7)

    /** The last day of a normal week, for display ranges. */
    val weekEnd: LocalDate get() = weekStart.plusDays(6)

    /**
     * [nextWeekStart], except for a first week stretched by the grace period, which ends a week
     * later. Detected by probing the next calendar week: if it still resolves to this week, the
     * week was extended. Use this wherever a screen must agree with Home about where "this week"
     * ends.
     */
    fun effectiveNextWeekStart(firstLaunch: Instant?, calendar: WeekCalendar): LocalDate {
        val probe = calendar.startOf(nextWeekStart)
        return if (effectiveWeekStart(probe, firstLaunch, calendar) == weekStart) nextWeekStart.plusDays(7) else nextWeekStart
    }

    /** Whether [now] falls in this week as Home sees it, extension included. */
    fun isCurrentWeek(firstLaunch: Instant?, now: Instant, calendar: WeekCalendar): Boolean =
        calendar.startOf(weekStart) <= now && now < calendar.startOf(effectiveNextWeekStart(firstLaunch, calendar))

    /**
     * Goals that count toward "is this week done": everything except mid-week bonus goals, which
     * stay in [weeklyGoals] as real tasks but can never drag a finished week back to incomplete.
     */
    private val goalsCountingTowardCompletion: List<WeeklyGoal>
        get() = weeklyGoals.filterNot { it.isBonus }

    val goalCount: Int get() = goalsCountingTowardCompletion.size

    val completedGoalCount: Int get() = goalsCountingTowardCompletion.count { it.isCompleted }

    val isFullyCompleted: Boolean
        get() = goalsCountingTowardCompletion.let { goals -> goals.isNotEmpty() && goals.all { it.isCompleted } }

    val completionPercentage: Double
        get() = if (goalCount == 0) 0.0 else completedGoalCount.toDouble() / goalCount * 100

    /** When the last counting goal was done; `null` unless [isFullyCompleted]. */
    val allGoalsCompletedAt: Instant?
        get() = if (!isFullyCompleted) null else goalsCountingTowardCompletion.mapNotNull { it.completedAt }.maxOrNull()

    companion object {
        /**
         * Below this much runway left in a brand-new user's first week, that week is extended by
         * one. Fixed hours, not a weekday: what matters is how much of the week was left.
         */
        val firstWeekGrace: Duration = Duration.ofHours(48)

        /**
         * A committed week stays editable until this many sessions are logged against it. Past
         * that, renegotiating is exactly what the frozen snapshot exists to prevent.
         */
        const val EDITABLE_SESSION_LIMIT = 2

        /**
         * The week [instant] belongs to: its calendar week, except during a brand-new user's
         * first week. If less than [firstWeekGrace] remained in the week active at [firstLaunch],
         * that week runs through the next rollover too. Fires at most once, since [firstLaunch]
         * never moves. `firstLaunch == null` always resolves normally.
         *
         * Keyed to first launch, not plan creation: an established user recommitting on a
         * Saturday knows what they're doing.
         */
        fun effectiveWeekStart(instant: Instant, firstLaunch: Instant?, calendar: WeekCalendar): LocalDate {
            val plainWeekStart = calendar.weekStart(instant)
            if (firstLaunch == null) return plainWeekStart

            val firstLaunchWeekStart = calendar.weekStart(firstLaunch)
            val firstRollover = calendar.startOf(firstLaunchWeekStart.plusDays(7))
            if (Duration.between(firstLaunch, firstRollover) >= firstWeekGrace) return plainWeekStart

            val extendedRollover = calendar.startOf(firstLaunchWeekStart.plusDays(14))
            // The lower bound stops this misfiring on an instant before the first week entirely,
            // which "the instant before the current week" probes can reach.
            val isWithinExtendedWeek = instant >= calendar.startOf(firstLaunchWeekStart) && instant < extendedRollover
            return if (isWithinExtendedWeek) firstLaunchWeekStart else plainWeekStart
        }
    }
}

/** One point on the planned-vs-actual chart. Planned counts duration goals only; actual, everything. */
data class WeeklyEffort(
    val weekStart: LocalDate,
    val plannedSeconds: Int,
    val actualSeconds: Int,
)
