package com.knightindustries.knighttraining.core.store

import com.knightindustries.knighttraining.core.model.ActivityType
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.WeekCalendar
import com.knightindustries.knighttraining.core.persistence.Lookups
import java.time.Instant
import java.time.LocalDate

/*
 * Training History's data (iOS `AppDatabase+WeeklyEffortHistory`, `+DailyDurationTotals`,
 * `+MostCommonStudyTime`, `+MostTrainedActivityType`). Every completed activity counts, however
 * short and whatever it was credited to (iOS AGENTS.md §7).
 */

/**
 * One week of the "planned vs. actual" chart. [plannedSeconds] sums the week's duration-type goal
 * targets only, since counts and progress have no time to plan against; [actualSeconds] is every
 * completed session that week. The two aren't apples-to-apples on purpose: a plan of count goals
 * reads "0 planned, N actual".
 */
data class WeeklyEffort(val weekStart: LocalDate, val plannedSeconds: Int, val actualSeconds: Int)

/**
 * A coarse part of day from a session's local start hour. A broad bucket stays honest from a
 * handful of sessions, where "around 7 PM" would overclaim regularity.
 */
enum class TimeOfDayBucket {
    MORNING, AFTERNOON, EVENING, NIGHT;

    companion object {
        fun forHour(hour: Int): TimeOfDayBucket = when (hour) {
            in 5..11 -> MORNING
            in 12..16 -> AFTERNOON
            in 17..20 -> EVENING
            else -> NIGHT
        }
    }
}

/**
 * The last [weeks] calendar weeks, oldest first, ending with the one containing [at]. A week with
 * no plan still appears with nothing planned, so the timeline never silently skips a gap.
 */
suspend fun TrainingStore.weeklyEffortHistory(weeks: Int = 8, at: Instant = now(), calendar: WeekCalendar = calendar()): List<WeeklyEffort> {
    if (weeks <= 0) return emptyList()
    val current = calendar.weekStart(at)
    return read {
        (0 until weeks).map { index ->
            val weekStart = current.plusWeeks((index - (weeks - 1)).toLong())
            val planned = withGoals(weekDao.plan(weekStart))?.weeklyGoals.orEmpty()
                .filter { it.targetType == TargetType.DURATION }
                .sumOf { it.targetValue }
            val actual = activityDao.completedDurationBetween(calendar.startOf(weekStart), calendar.startOf(weekStart.plusWeeks(1))).toInt()
            WeeklyEffort(weekStart, planned, actual)
        }
    }
}

/** Seconds per local day over the last [days] days of [from], by each session's start day. */
suspend fun TrainingStore.dailyDurationTotals(days: Int = 112, from: Instant = now(), calendar: WeekCalendar = calendar()): Map<LocalDate, Int> =
    read { recentCompletedActivitiesInTransaction(from, days) }
        .groupingBy { calendar.localDate(it.startedAt) }
        .fold(0) { total, activity -> total + activity.duration }

/**
 * The part of day most sessions *started* in, all-time: it counts sessions, not minutes, since it
 * describes when the habit happens. Ties go to the bucket iOS's SQL picks (its raw value, descending).
 */
suspend fun TrainingStore.mostCommonStudyTimeOfDay(calendar: WeekCalendar = calendar()): TimeOfDayBucket? =
    activityDao.completedStartTimes()
        .groupingBy { TimeOfDayBucket.forHour(it.atZone(calendar.zone).hour) }
        .eachCount()
        .maxWithOrNull(compareBy<Map.Entry<TimeOfDayBucket, Int>> { it.value }.thenBy { it.key.name.lowercase() })
        ?.key

/** The activity with the most tracked time, all-time; ties break on code, descending, like iOS. */
suspend fun TrainingStore.mostTrainedActivityType(): ActivityType? =
    activityDao.completedDurationByType()
        .map { Lookups.activityType(it.typeId) to it.seconds }
        .maxWithOrNull(compareBy<Pair<ActivityType, Long>> { it.second }.thenBy { it.first.code })
        ?.first
