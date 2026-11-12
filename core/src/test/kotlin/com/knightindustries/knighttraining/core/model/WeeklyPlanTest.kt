package com.knightindustries.knighttraining.core.model

import com.knightindustries.knighttraining.core.model.Fixtures.at
import com.knightindustries.knighttraining.core.model.Fixtures.newYorkSunday
import com.knightindustries.knighttraining.core.model.Fixtures.puzzles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Ports of iOS `WeeklyPlanTests` (the first-week grace period) and `WeeklyPlanBonusGoalTests`.
 * iOS runs these against `Date()`; here they're pinned to a fixed week in a DST zone.
 */
class WeeklyPlanTest {
    private val calendar = newYorkSunday

    /** Sunday 2026-11-08, the week containing the fixed "now" below. */
    private val currentWeekStart = LocalDate.of(2026, 11, 8)
    private val now = at("2026-11-11 12:00", calendar)
    private val firstRollover = calendar.startOf(currentWeekStart.plusDays(7))
    private val secondRollover = calendar.startOf(currentWeekStart.plusDays(14))

    private fun effective(instant: Instant, firstLaunch: Instant?) =
        WeeklyPlan.effectiveWeekStart(instant, firstLaunch, calendar)

    @Test
    fun noFirstLaunchResolvesNormally() {
        assertEquals(calendar.weekStart(now), effective(now, null))
        assertEquals(currentWeekStart, calendar.weekStart(now))
    }

    @Test
    fun firstLaunchWithPlentyOfRunwayResolvesNormally() {
        val firstLaunch = calendar.startOf(currentWeekStart)
        val nextWeek = currentWeekStart.plusDays(7)
        assertEquals(nextWeek, effective(calendar.startOf(nextWeek), firstLaunch))
    }

    @Test
    fun firstLaunchNearWeekEndExtendsThroughTheNextRollover() {
        val firstLaunch = firstRollover.minus(2, ChronoUnit.HOURS)

        val justAfterRollover = firstRollover.plus(5, ChronoUnit.MINUTES)
        assertEquals(
            "should still resolve to the week active at first launch",
            currentWeekStart,
            effective(justAfterRollover, firstLaunch),
        )

        val midSecondWeek = firstRollover.plus(3, ChronoUnit.DAYS)
        assertEquals("extension should hold through the whole following week", currentWeekStart, effective(midSecondWeek, firstLaunch))
    }

    @Test
    fun resolvesNormallyAgainAfterTheSecondRollover() {
        val firstLaunch = firstRollover.minus(2, ChronoUnit.HOURS)
        val afterExtension = secondRollover.plus(1, ChronoUnit.HOURS)
        assertEquals(calendar.weekStart(afterExtension), effective(afterExtension, firstLaunch))
    }

    @Test
    fun exactlyAtTheGraceThresholdDoesNotExtend() {
        val firstLaunch = firstRollover.minus(48, ChronoUnit.HOURS)
        val justAfterRollover = firstRollover.plus(5, ChronoUnit.MINUTES)
        assertEquals(calendar.weekStart(justAfterRollover), effective(justAfterRollover, firstLaunch))
    }

    /** iOS 2026-10-16: an extended week's log stopped after 7 days though Home still showed it. */
    @Test
    fun anExtendedFirstWeekEndsOneWeekLater() {
        val firstLaunch = firstRollover.minus(2, ChronoUnit.HOURS)
        val plan = WeeklyPlan(planTemplateId = UUID.randomUUID(), weekStart = currentWeekStart)

        assertEquals(currentWeekStart.plusDays(14), plan.effectiveNextWeekStart(firstLaunch, calendar))
        assertTrue(plan.isCurrentWeek(firstLaunch, firstRollover.plus(3, ChronoUnit.DAYS), calendar))
        assertFalse(plan.isCurrentWeek(firstLaunch, secondRollover.plus(1, ChronoUnit.HOURS), calendar))
    }

    @Test
    fun aNormalWeekEndsAfterSevenDays() {
        val plan = WeeklyPlan(planTemplateId = UUID.randomUUID(), weekStart = currentWeekStart)
        val firstLaunch = calendar.startOf(currentWeekStart).plus(1, ChronoUnit.HOURS)

        assertEquals(plan.nextWeekStart, plan.effectiveNextWeekStart(firstLaunch, calendar))
        assertEquals(plan.nextWeekStart, plan.effectiveNextWeekStart(null, calendar))
    }

    @Test
    fun onlyTheFirstLaunchWeekStretches() {
        val firstLaunch = firstRollover.minus(2, ChronoUnit.HOURS)
        val laterWeek = WeeklyPlan(planTemplateId = UUID.randomUUID(), weekStart = currentWeekStart.plusDays(14))
        assertEquals(laterWeek.nextWeekStart, laterWeek.effectiveNextWeekStart(firstLaunch, calendar))
    }

    /** New York leaves DST on Sunday 2026-11-01: that week is 7 days and 1 hour long. */
    @Test
    fun aDaylightSavingWeekStillEndsAtLocalMidnight() {
        val dstWeek = LocalDate.of(2026, 11, 1)
        val plan = WeeklyPlan(planTemplateId = UUID.randomUUID(), weekStart = dstWeek)
        val lastMinute = at("2026-11-07 23:59", calendar)

        assertTrue(plan.isCurrentWeek(null, lastMinute, calendar))
        assertFalse(plan.isCurrentWeek(null, calendar.startOf(dstWeek.plusDays(7)), calendar))
    }

    // MARK: - Bonus goals

    private fun goal(planId: UUID, currentValue: Int, completedAt: Instant?, isBonus: Boolean = false) = WeeklyGoal(
        weeklyPlanId = planId, activityType = puzzles, targetType = TargetType.COUNT,
        targetValue = 10, currentValue = currentValue, completedAt = completedAt, isBonus = isBonus,
    )

    private fun plan(vararg goals: WeeklyGoal) =
        WeeklyPlan(planTemplateId = UUID.randomUUID(), weekStart = currentWeekStart, weeklyGoals = goals.toList())

    @Test
    fun weeklyGoalsIncludesBonusGoals() {
        val id = UUID.randomUUID()
        val plan = plan(goal(id, 10, now), goal(id, 0, null, isBonus = true))
        assertEquals("a bonus goal must still show up as a real task", 2, plan.weeklyGoals.size)
        assertEquals(1, plan.goalCount)
    }

    @Test
    fun anUnfinishedBonusGoalNeverMakesTheWeekIncomplete() {
        val id = UUID.randomUUID()
        val plan = plan(goal(id, 10, now), goal(id, 3, null, isBonus = true))
        assertTrue(plan.isFullyCompleted)
        assertEquals(100.0, plan.completionPercentage, 0.0)
    }

    @Test
    fun aFinishedBonusGoalDoesNotExcuseAnUnfinishedRealOne() {
        val id = UUID.randomUUID()
        assertFalse(plan(goal(id, 3, null), goal(id, 10, now, isBonus = true)).isFullyCompleted)
    }

    @Test
    fun allGoalsCompletedAtIgnoresABonusGoalsCompletionTime() {
        val id = UUID.randomUUID()
        val realDone = Instant.ofEpochSecond(1000)
        val plan = plan(goal(id, 10, realDone), goal(id, 10, Instant.ofEpochSecond(2000), isBonus = true))
        assertEquals(realDone, plan.allGoalsCompletedAt)
    }

    @Test
    fun allGoalsCompletedAtIsNullWhenNotFullyCompleted() {
        assertNull(plan(goal(UUID.randomUUID(), 3, null)).allGoalsCompletedAt)
    }

    @Test
    fun anEmptyPlanIsNeverComplete() {
        val empty = plan()
        assertFalse(empty.isFullyCompleted)
        assertEquals(0.0, empty.completionPercentage, 0.0)
    }
}
