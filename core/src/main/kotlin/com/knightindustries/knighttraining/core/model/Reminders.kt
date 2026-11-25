package com.knightindustries.knighttraining.core.model

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** What, if anything, is worth reminding the user about right now. */
sealed interface TrainingReminderDecision {
    /** The week is done, training already happened today, or there's nothing to train toward. */
    data object None : TrainingReminderDecision

    /** No plan is committed for this week yet. */
    data object CommitWeeklyPlan : TrainingReminderDecision

    /** A plan is committed, work remains, and nothing has been logged today. */
    data class TrainingNudge(val completedGoals: Int, val totalGoals: Int, val daysLeftInWeek: Int) :
        TrainingReminderDecision
}

/**
 * Whether the user is behind on the week and worth nudging. Pure, so the whole decision table is
 * testable. Evaluated when scheduling, not when firing, so the caller re-runs it on every data
 * refresh and rebuilds the pending reminders.
 */
object TrainingReminderPolicy {
    fun decide(
        hasCommittedPlan: Boolean,
        totalGoals: Int,
        completedGoals: Int,
        trainedToday: Boolean,
        now: Instant,
        calendar: WeekCalendar,
    ): TrainingReminderDecision = when {
        !hasCommittedPlan -> TrainingReminderDecision.CommitWeeklyPlan
        // Shouldn't be reachable, but never a reason to ask for a plan they already have.
        totalGoals <= 0 -> TrainingReminderDecision.None
        // A finished week is a floor: training past it is welcome, never prompted for.
        completedGoals >= totalGoals -> TrainingReminderDecision.None
        // Nudging someone who trained today is nagging.
        trainedToday -> TrainingReminderDecision.None
        else -> TrainingReminderDecision.TrainingNudge(completedGoals, totalGoals, daysLeftInWeek(now, calendar))
    }

    /** Days left in the week **including today**, so the last day reads "1 day left". */
    fun daysLeftInWeek(now: Instant, calendar: WeekCalendar): Int {
        val elapsed = ChronoUnit.DAYS.between(calendar.weekStart(now), calendar.localDate(now)).toInt()
        return maxOf(1, 7 - elapsed)
    }
}

/**
 * When the user is willing to be nudged. A reminder schedule, not a training schedule: it never
 * implies a per-day goal. Off by default; reminders are opt-in.
 */
data class ReminderSchedule(
    val isEnabled: Boolean = false,
    val weekdays: Set<DayOfWeek> = EVERY_DAY,
    val hour: Int = 18,
    val minute: Int = 30,
) {
    /** Enabled with no days selected is inert, which the settings UI should say. */
    val isActive: Boolean get() = isEnabled && weekdays.isNotEmpty()

    fun includes(weekday: DayOfWeek): Boolean = weekday in weekdays

    /**
     * The next [limit] moments this fires, strictly after [after]. Concrete instants rather than a
     * repeating trigger: each reminder quotes the numbers for the day it fires, so the set is
     * rebuilt whenever the data changes. Empty when inactive.
     */
    fun upcomingOccurrences(after: Instant, limit: Int, calendar: WeekCalendar): List<Instant> {
        if (!isActive || limit <= 0) return emptyList()

        val occurrences = mutableListOf<Instant>()
        // Start today so a later time today still counts; scan far enough for one selected day.
        var day = calendar.localDate(after)
        repeat(limit * 7 + 7) {
            if (occurrences.size >= limit) return occurrences
            if (includes(day.dayOfWeek)) {
                val fire = day.atTime(LocalTime.of(hour, minute)).atZone(calendar.zone).toInstant()
                if (fire > after) occurrences += fire
            }
            day = day.plusDays(1)
        }
        return occurrences
    }

    companion object {
        val EVERY_DAY: Set<DayOfWeek> = DayOfWeek.entries.toSet()
        val DEFAULT = ReminderSchedule()
    }
}

/** Whether to suggest adding more once everything committed to is done. */
sealed interface StretchGoalNudgeDecision {
    data object None : StretchGoalNudgeDecision

    data class Offer(val fireAt: Instant) : StretchGoalNudgeDecision
}

object StretchGoalNudgePolicy {
    /** Long enough after finishing to read as "you have room to spare". */
    val nudgeDelay: Duration = Duration.ofDays(2)

    /** Never inside the last day of the week: no time left to act on it. */
    val minimumRunwayBeforeDeadline: Duration = Duration.ofDays(1)

    fun decide(plan: WeeklyPlan?, now: Instant, calendar: WeekCalendar): StretchGoalNudgeDecision {
        val completedAt = plan?.allGoalsCompletedAt ?: return StretchGoalNudgeDecision.None

        val fireAt = completedAt + nudgeDelay
        val latestAllowed = calendar.startOf(plan.nextWeekStart) - minimumRunwayBeforeDeadline

        // A window that passed while the app was closed stays missed.
        return if (fireAt > now && fireAt <= latestAllowed) StretchGoalNudgeDecision.Offer(fireAt) else StretchGoalNudgeDecision.None
    }
}
