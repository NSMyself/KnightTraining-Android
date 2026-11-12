package com.knightindustries.knighttraining.core.model

import com.knightindustries.knighttraining.core.model.Fixtures.at
import com.knightindustries.knighttraining.core.model.Fixtures.newYorkSunday
import com.knightindustries.knighttraining.core.model.Fixtures.puzzles
import com.knightindustries.knighttraining.core.model.Fixtures.utcMonday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Ports of iOS `TrainingReminderPolicyTests`, `ReminderScheduleTests` (minus display strings and
 * JSON coding, which move to the app) and `StretchGoalNudgePolicyTests`.
 */
class RemindersTest {
    private val calendar = utcMonday

    private fun decide(
        hasCommittedPlan: Boolean = true,
        totalGoals: Int = 5,
        completedGoals: Int = 2,
        trainedToday: Boolean = false,
        now: String = "2026-08-12 09:00", // a Wednesday
    ) = TrainingReminderPolicy.decide(hasCommittedPlan, totalGoals, completedGoals, trainedToday, at(now), calendar)

    // MARK: - TrainingReminderPolicy

    @Test
    fun noCommittedPlanPromptsToCommit() {
        assertEquals(TrainingReminderDecision.CommitWeeklyPlan, decide(hasCommittedPlan = false))
    }

    /** Unscheduled training doesn't substitute for committing to the week. */
    @Test
    fun noCommittedPlanPromptsEvenIfTrainedToday() {
        assertEquals(TrainingReminderDecision.CommitWeeklyPlan, decide(hasCommittedPlan = false, trainedToday = true))
    }

    @Test
    fun completedWeekIsSilent() {
        assertEquals(TrainingReminderDecision.None, decide(totalGoals = 5, completedGoals = 5))
    }

    @Test
    fun overshootingTheWeekIsSilent() {
        assertEquals(TrainingReminderDecision.None, decide(totalGoals = 5, completedGoals = 7))
    }

    @Test
    fun trainedTodayIsSilent() {
        assertEquals(TrainingReminderDecision.None, decide(trainedToday = true))
    }

    @Test
    fun committedPlanWithNoGoalsIsSilent() {
        assertEquals(TrainingReminderDecision.None, decide(totalGoals = 0, completedGoals = 0))
    }

    @Test
    fun behindAndUntrainedTodayNudges() {
        assertEquals(TrainingReminderDecision.TrainingNudge(2, 5, 5), decide())
    }

    @Test
    fun nothingLoggedAllWeekNudges() {
        assertEquals(
            TrainingReminderDecision.TrainingNudge(0, 3, 7),
            decide(totalGoals = 3, completedGoals = 0, now = "2026-08-10 09:00"), // Monday
        )
    }

    @Test
    fun daysLeftInWeekIncludesToday() {
        val expected = listOf(
            "2026-08-10 09:00" to 7, // Monday, week start
            "2026-08-12 09:00" to 5, // Wednesday
            "2026-08-15 09:00" to 2, // Saturday
            "2026-08-16 23:59" to 1, // Sunday, last minute
        )
        for ((text, days) in expected) {
            assertEquals("$text should have $days day(s) left", days, TrainingReminderPolicy.daysLeftInWeek(at(text), calendar))
        }
    }

    @Test
    fun daysLeftInWeekIsNeverZeroOrNegative() {
        for (hour in 0..23) {
            val now = at("2026-08-16 %02d:00".format(hour))
            assertTrue(TrainingReminderPolicy.daysLeftInWeek(now, calendar) >= 1)
        }
    }

    // MARK: - ReminderSchedule

    @Test
    fun defaultIsOffEveryDayAtHalfPastSix() {
        assertFalse(ReminderSchedule.DEFAULT.isEnabled)
        assertFalse(ReminderSchedule.DEFAULT.isActive)
        assertEquals(ReminderSchedule.EVERY_DAY, ReminderSchedule.DEFAULT.weekdays)
        assertEquals(18, ReminderSchedule.DEFAULT.hour)
        assertEquals(30, ReminderSchedule.DEFAULT.minute)
    }

    @Test
    fun enabledWithNoDaysIsNotActive() {
        assertFalse(ReminderSchedule(isEnabled = true, weekdays = emptySet()).isActive)
        assertTrue(ReminderSchedule(isEnabled = true, weekdays = setOf(MONDAY)).isActive)
        assertFalse(ReminderSchedule(isEnabled = false).isActive)
    }

    @Test
    fun includesWeekday() {
        val schedule = ReminderSchedule(isEnabled = true, weekdays = setOf(MONDAY, THURSDAY))
        assertTrue(schedule.includes(MONDAY))
        assertTrue(schedule.includes(THURSDAY))
        assertFalse(schedule.includes(SUNDAY))
    }

    @Test
    fun upcomingOccurrencesIsEmptyWhenInactive() {
        val after = at("2026-08-12 09:00")
        assertTrue(ReminderSchedule(isEnabled = false).upcomingOccurrences(after, 5, calendar).isEmpty())
        assertTrue(ReminderSchedule(isEnabled = true, weekdays = emptySet()).upcomingOccurrences(after, 5, calendar).isEmpty())
    }

    @Test
    fun upcomingOccurrencesIncludesLaterToday() {
        val schedule = ReminderSchedule(isEnabled = true, hour = 19, minute = 0)
        assertEquals(listOf(at("2026-08-12 19:00")), schedule.upcomingOccurrences(at("2026-08-12 09:00"), 1, calendar))
    }

    @Test
    fun upcomingOccurrencesSkipsTodayOnceThatTimeHasPassed() {
        val schedule = ReminderSchedule(isEnabled = true, hour = 19, minute = 0)
        assertEquals(listOf(at("2026-08-13 19:00")), schedule.upcomingOccurrences(at("2026-08-12 19:30"), 1, calendar))
    }

    @Test
    fun upcomingOccurrencesOnlyReturnsSelectedWeekdays() {
        val schedule = ReminderSchedule(isEnabled = true, weekdays = setOf(MONDAY, THURSDAY), hour = 8, minute = 30)
        assertEquals(
            listOf("2026-08-13 08:30", "2026-08-17 08:30", "2026-08-20 08:30", "2026-08-24 08:30").map { at(it) },
            schedule.upcomingOccurrences(at("2026-08-12 09:00"), 4, calendar),
        )
    }

    @Test
    fun upcomingOccurrencesFillsLimitWithASingleSelectedDay() {
        val schedule = ReminderSchedule(isEnabled = true, weekdays = setOf(SUNDAY), hour = 10, minute = 0)
        assertEquals(
            listOf("2026-08-16 10:00", "2026-08-23 10:00", "2026-08-30 10:00").map { at(it) },
            schedule.upcomingOccurrences(at("2026-08-12 09:00"), 3, calendar),
        )
    }

    @Test
    fun upcomingOccurrencesRespectsLimit() {
        val schedule = ReminderSchedule(isEnabled = true, hour = 19, minute = 0)
        assertEquals(0, schedule.upcomingOccurrences(at("2026-08-12 09:00"), 0, calendar).size)
        assertEquals(3, schedule.upcomingOccurrences(at("2026-08-12 09:00"), 3, calendar).size)
    }

    /** Fires at the local time across a DST change, not a fixed 24h apart. */
    @Test
    fun upcomingOccurrencesKeepLocalTimeAcrossDaylightSaving() {
        val schedule = ReminderSchedule(isEnabled = true, hour = 19, minute = 0)
        assertEquals(
            listOf("2026-10-31 19:00", "2026-11-01 19:00").map { at(it, newYorkSunday) },
            schedule.upcomingOccurrences(at("2026-10-31 09:00", newYorkSunday), 2, newYorkSunday),
        )
    }

    // MARK: - StretchGoalNudgePolicy

    private val weekStart = LocalDate.of(2026, 8, 10) // Monday

    private fun completedPlan(completedAt: Instant?, bonusOpen: Boolean = false): WeeklyPlan {
        val id = UUID.randomUUID()
        val goals = buildList {
            add(WeeklyGoal(weeklyPlanId = id, activityType = puzzles, targetType = TargetType.COUNT, targetValue = 10, currentValue = if (completedAt != null) 10 else 0, completedAt = completedAt))
            if (bonusOpen) add(WeeklyGoal(weeklyPlanId = id, activityType = puzzles, targetType = TargetType.COUNT, targetValue = 10, isBonus = true))
        }
        return WeeklyPlan(id = id, planTemplateId = UUID.randomUUID(), weekStart = weekStart, weeklyGoals = goals)
    }

    private fun stretch(plan: WeeklyPlan?, now: Instant) = StretchGoalNudgePolicy.decide(plan, now, calendar)

    @Test
    fun noPlanOrUnfinishedPlanReturnsNone() {
        assertEquals(StretchGoalNudgeDecision.None, stretch(null, at("2026-08-12 09:00")))
        assertEquals(StretchGoalNudgeDecision.None, stretch(completedPlan(null), at("2026-08-12 09:00")))
    }

    /** The reason `isBonus` exists: an open bonus goal never blocks this nudge. */
    @Test
    fun anUnfinishedBonusGoalDoesNotBlockIt() {
        val completedAt = at("2026-08-10 10:00")
        assertEquals(
            StretchGoalNudgeDecision.Offer(completedAt + StretchGoalNudgePolicy.nudgeDelay),
            stretch(completedPlan(completedAt, bonusOpen = true), at("2026-08-10 11:00")),
        )
    }

    @Test
    fun completedMidweekOffersTwoDaysLater() {
        val completedAt = at("2026-08-12 09:00") // Wednesday
        assertEquals(StretchGoalNudgeDecision.Offer(completedAt + StretchGoalNudgePolicy.nudgeDelay), stretch(completedPlan(completedAt), completedAt))
    }

    @Test
    fun aFireDateInsideTheLastDayReturnsNone() {
        val completedAt = at("2026-08-15 09:00") // Saturday: +2 days lands past the deadline
        assertEquals(StretchGoalNudgeDecision.None, stretch(completedPlan(completedAt), completedAt))
    }

    @Test
    fun aDelayThatAlreadyElapsedReturnsNone() {
        val completedAt = at("2026-08-10 10:00")
        val late = completedAt + StretchGoalNudgePolicy.nudgeDelay + java.time.Duration.ofHours(1)
        assertEquals(StretchGoalNudgeDecision.None, stretch(completedPlan(completedAt), late))
    }

    @Test
    fun exactlyAtTheLatestAllowedFireDateStillOffers() {
        val latestAllowed = calendar.startOf(weekStart.plusDays(7)) - StretchGoalNudgePolicy.minimumRunwayBeforeDeadline
        val completedAt = latestAllowed - StretchGoalNudgePolicy.nudgeDelay
        assertEquals(StretchGoalNudgeDecision.Offer(latestAllowed), stretch(completedPlan(completedAt), completedAt))
    }
}
