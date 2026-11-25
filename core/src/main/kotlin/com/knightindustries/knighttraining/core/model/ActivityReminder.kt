package com.knightindustries.knighttraining.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.util.UUID

/**
 * A reminder to do one training block (iOS `ActivityReminder`), apart from the weekly "you're
 * behind" nudge, which only ever speaks to the whole week. Reuses [ReminderSchedule]'s days and
 * time; one block can carry several (weekday mornings *and* a Saturday evening).
 *
 * Tied to the block's [GoalTemplate], not a week's goal, so it lasts across weeks like the rest of
 * the plan. Removing the block removes its reminders (the table cascades).
 */
data class ActivityReminder(
    val id: UUID = UUID.randomUUID(),
    val goalTemplateId: UUID,
    val schedule: ReminderSchedule,
    val createdAt: Instant = Instant.now(),
) {
    companion object {
        /** A new reminder's starting point (iOS's editor): on, no days picked yet, 19:00. */
        val NEW_SCHEDULE = ReminderSchedule(isEnabled = true, weekdays = emptySet(), hour = 19, minute = 0)

        /**
         * The stored day list: iOS's `Calendar` weekday numbers (1 = Sunday … 7 = Saturday),
         * comma-separated, so the column means the same thing on both platforms.
         */
        fun encode(weekdays: Set<DayOfWeek>): String = weekdays.map { it.value % 7 + 1 }.sorted().joinToString(",")

        fun decode(raw: String): Set<DayOfWeek> =
            raw.split(",").mapNotNull { it.trim().toIntOrNull()?.takeIf { n -> n in 1..7 } }.map { DayOfWeek.of((it + 5) % 7 + 1) }.toSet()
    }
}
