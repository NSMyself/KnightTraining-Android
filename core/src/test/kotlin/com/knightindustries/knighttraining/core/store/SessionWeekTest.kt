package com.knightindustries.knighttraining.core.store

import com.knightindustries.knighttraining.core.model.Activity
import com.knightindustries.knighttraining.core.model.WeekCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.util.UUID

/**
 * Port of iOS `SessionWeekTests`: a session counts toward the week it *started* in (iOS,
 * 2026-10-25, after a session ran past midnight on the last day of the week).
 */
class SessionWeekTest : StoreTest() {
    private val mondayFirst get() = WeekCalendar(calendar.zone, DayOfWeek.MONDAY)

    private suspend fun commitWeekContaining(instant: Instant, calendar: WeekCalendar = this.calendar) =
        commitWeek(calendar.weekStart(instant), "puzzles" to 50)

    private suspend fun found(instant: Instant, firstLaunch: Instant? = null, calendar: WeekCalendar = this.calendar) =
        store.weeklyPlanContaining(instant, firstLaunch, calendar)?.id

    // MARK: - Sunday-first (Saturday → Sunday midnight is the rollover)

    @Test
    fun aSessionStartedBeforeMidnightOnTheLastDayBelongsToThatWeek() = test {
        val lastWeek = commitWeekContaining(at(2026, 10, 20, 12))
        commitWeekContaining(at(2026, 10, 26, 12))
        assertEquals(lastWeek.id, found(at(2026, 10, 24, 23, 40)))
    }

    @Test
    fun aSessionStartedJustAfterMidnightBelongsToTheNewWeek() = test {
        commitWeekContaining(at(2026, 10, 20, 12))
        val thisWeek = commitWeekContaining(at(2026, 10, 26, 12))
        assertEquals(thisWeek.id, found(at(2026, 10, 25, 0, 1)))
    }

    @Test
    fun theLastSecondOfTheWeekStillBelongsToIt() = test {
        val lastWeek = commitWeekContaining(at(2026, 10, 20, 12))
        val rollover = at(2026, 10, 25, 0)
        assertEquals(lastWeek.id, found(rollover.minusSeconds(1)))
        assertNull("the rollover instant itself is the new week, which has no plan here", found(rollover))
    }

    @Test
    fun anUncommittedNewWeekDoesNotHideLastWeeksPlan() = test {
        val lastWeek = commitWeekContaining(at(2026, 10, 20, 12))
        assertEquals(lastWeek.id, found(at(2026, 10, 24, 23, 50)))
        assertNull(found(at(2026, 10, 25, 0, 10)))
    }

    @Test
    fun aWeekWithNoPlanFindsNothingEvenBetweenCommittedWeeks() = test {
        commitWeekContaining(at(2026, 10, 13, 12))
        commitWeekContaining(at(2026, 10, 27, 12))
        assertNull(found(at(2026, 10, 21, 12)))
    }

    // MARK: - Monday-first (Sunday → Monday midnight is the rollover)

    @Test
    fun onAMondayFirstCalendarSaturdayNightIsMidWeek() = test {
        val week = commitWeekContaining(at(2026, 10, 21, 12), mondayFirst)
        assertEquals(week.id, found(at(2026, 10, 24, 23, 40), calendar = mondayFirst))
        assertEquals(week.id, found(at(2026, 10, 25, 0, 10), calendar = mondayFirst))
    }

    @Test
    fun onAMondayFirstCalendarSundayNightIsTheLastDay() = test {
        val lastWeek = commitWeekContaining(at(2026, 10, 21, 12), mondayFirst)
        val thisWeek = commitWeekContaining(at(2026, 10, 28, 12), mondayFirst)
        assertEquals(lastWeek.id, found(at(2026, 10, 25, 23, 40), calendar = mondayFirst))
        assertEquals(thisWeek.id, found(at(2026, 10, 26, 0, 10), calendar = mondayFirst))
    }

    // MARK: - First-week grace

    @Test
    fun aSessionInAnExtendedFirstWeekFindsThatWeek() = test {
        val firstLaunch = at(2026, 10, 24, 10) // Saturday, 14h left in the week
        val extended = commitWeekContaining(firstLaunch)
        assertEquals(extended.id, found(at(2026, 10, 28, 20), firstLaunch))
    }

    @Test
    fun theExtendedFirstWeekEndsAtTheSecondRollover() = test {
        val firstLaunch = at(2026, 10, 24, 10)
        val extended = commitWeekContaining(firstLaunch)
        assertEquals(extended.id, found(at(2026, 10, 31, 23, 50), firstLaunch))
        // 2026-11-01 is also New York's DST change; the rollover is still local midnight.
        assertNull(found(at(2026, 11, 1, 0, 10), firstLaunch))
    }

    // MARK: - Seeded sessions

    /** Saved after midnight, credited to the week it started in; the new week starts from zero. */
    @Test
    fun aSeededSessionSavedAfterMidnightCreditsTheWeekItStartedIn() = test {
        val lastWeek = commitWeekContaining(at(2026, 10, 20, 12))
        val goal = lastWeek.weeklyGoals.single()

        store.logSession(
            listOf(
                SessionSegmentLog(
                    Activity(
                        activityType = goal.activityType, startedAt = at(2026, 10, 24, 23, 40),
                        endedAt = at(2026, 10, 25, 0, 20), duration = 40 * 60, count = 18, sessionId = UUID.randomUUID(),
                    ),
                    SessionSegmentLog.GoalProgress(goal.id, 18),
                ),
            ),
        )

        assertEquals(18, store.weeklyPlan(lastWeek.id)!!.weeklyGoals.single().currentValue)
        val newWeek = store.weeklySurface(at = at(2026, 10, 25, 9))
        assertTrue("the session started last week, so the new week starts from zero", newWeek.recentActivities.isEmpty())
    }
}
