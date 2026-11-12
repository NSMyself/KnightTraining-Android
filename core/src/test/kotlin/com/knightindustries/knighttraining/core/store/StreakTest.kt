package com.knightindustries.knighttraining.core.store

import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

/** Port of iOS `StreakTests`: streaks and the weeks-completed tally are derived, never stored. */
class StreakTest : StoreTest() {
    /** A one-goal week [offset] weeks from this one (0 = this week), completed or not. */
    private suspend fun week(offset: Long, completed: Boolean): WeeklyPlan {
        val plan = commitWeek(calendar.weekStart(now).plusWeeks(offset), "puzzles" to 10)
        if (completed) store.updateWeeklyGoalProgress(plan.weeklyGoals.single(), 10)
        return plan
    }

    @Test
    fun currentStreakIsZeroWithNoCompletedWeeks() = test {
        assertEquals(0, store.currentStreak())
    }

    @Test
    fun currentStreakCountsConsecutiveWeeksEndingNow() = test {
        week(0, true); week(-1, true); week(-2, true)
        assertEquals(3, store.currentStreak())
    }

    /** A week still in progress must never zero out a real streak. */
    @Test
    fun anIncompleteCurrentWeekDoesNotBreakIt() = test {
        week(0, false); week(-1, true); week(-2, true)
        assertEquals(2, store.currentStreak())
    }

    @Test
    fun noPlanThisWeekStillCountsPastWeeks() = test {
        week(-1, true); week(-2, true)
        assertEquals(2, store.currentStreak())
    }

    @Test
    fun currentStreakBreaksOnAGapWeek() = test {
        week(0, true); week(-1, true); week(-2, false); week(-3, true)
        assertEquals("the week behind the gap doesn't count", 2, store.currentStreak())
    }

    @Test
    fun longestStreakIsZeroWithNoCompletedWeeks() = test {
        assertEquals(0, store.longestStreak())
    }

    @Test
    fun longestStreakFindsTheLongestRunEvenIfNotTheCurrentOne() = test {
        week(-6, true); week(-5, true); week(-4, true); week(-3, false); week(-1, true); week(0, true)
        assertEquals(3, store.longestStreak())
    }

    // MARK: - Weeks-completed tally

    @Test
    fun tallyIsZeroWhenNothingCommitted() = test {
        assertEquals(WeekCompletionTally(0, 0), store.weekCompletionTally())
    }

    @Test
    fun tallyCountsPastWeeksCompleteOrNot() = test {
        week(-4, true); week(-3, false); week(-1, true) // -2 never committed: doesn't count against them
        assertEquals(WeekCompletionTally(2, 3), store.weekCompletionTally())
    }

    @Test
    fun anUnfinishedCurrentWeekIsLeftOut() = test {
        week(-1, true); week(0, false)
        assertEquals("a week in progress can't count as missed yet", WeekCompletionTally(1, 1), store.weekCompletionTally())
    }

    @Test
    fun aFinishedCurrentWeekCounts() = test {
        week(-1, false); week(0, true)
        assertEquals(WeekCompletionTally(1, 2), store.weekCompletionTally())
    }

    @Test
    fun anExtendedFirstWeekStaysOpen() = test {
        val firstLaunch = calendar.startOf(calendar.weekStart(now)).minus(Duration.ofHours(12))
        week(-1, false)
        assertEquals(WeekCompletionTally(0, 0), store.weekCompletionTally(firstLaunch = firstLaunch))
    }

    @Test
    fun anUnfinishedBonusGoalDoesNotCount() = test {
        val plan = week(-1, true)
        store.saveWeeklyGoal(WeeklyGoal(weeklyPlanId = plan.id, activityType = type("puzzles"), targetType = TargetType.COUNT, targetValue = 99, isBonus = true))
        assertEquals(WeekCompletionTally(1, 1), store.weekCompletionTally())
    }
}
