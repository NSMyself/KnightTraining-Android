package com.knightindustries.knighttraining.core.store

import com.knightindustries.knighttraining.core.model.Activity
import com.knightindustries.knighttraining.core.model.TargetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * Ports of iOS `WeeklyEffortHistoryTests` and `DailyDurationTotalsTests`, plus the time-of-day and
 * most-trained picks behind Training History's stat cards (untested on iOS).
 */
class TrainingHistoryTest : StoreTest() {
    private suspend fun save(code: String = "openings", startedAt: Instant, duration: Int = 600, ongoing: Boolean = false) =
        Activity(
            activityType = type(code), startedAt = startedAt,
            endedAt = if (ongoing) null else startedAt.plusSeconds(duration.toLong()), duration = duration,
        ).also { store.saveActivity(it) }

    private val weekStart get() = calendar.weekStart(now)
    private val weekStartInstant get() = calendar.startOf(weekStart)

    // MARK: - Weekly effort

    @Test
    fun anEmptyDatabaseReturnsZeroedWeeks() = test {
        val history = store.weeklyEffortHistory(weeks = 4)
        assertEquals(4, history.size)
        assertTrue(history.all { it.plannedSeconds == 0 && it.actualSeconds == 0 })
    }

    @Test
    fun weeksRunOldestFirstEndingAtTheCurrentWeek() = test {
        val history = store.weeklyEffortHistory(weeks = 3)
        assertEquals(listOf(weekStart.minusWeeks(2), weekStart.minusWeeks(1), weekStart), history.map { it.weekStart })
    }

    @Test
    fun plannedSumsOnlyDurationGoals() = test {
        val plan = commitWeek(weekStart, "openings" to 1800, targetType = TargetType.DURATION)
        store.saveWeeklyGoal(com.knightindustries.knighttraining.core.model.WeeklyGoal(weeklyPlanId = plan.id, activityType = type("puzzles"), targetType = TargetType.COUNT, targetValue = 50))
        assertEquals(1800, store.weeklyEffortHistory(weeks = 1).single().plannedSeconds)
    }

    @Test
    fun actualCountsEveryCompletedActivityWhateverItsGoal() = test {
        commitWeek(weekStart, "openings" to 1800, targetType = TargetType.DURATION)
        save("openings", weekStartInstant.plusSeconds(3600), 900)
        save("puzzles", weekStartInstant.plusSeconds(7200), 300)
        assertEquals(1200, store.weeklyEffortHistory(weeks = 1).single().actualSeconds)
    }

    @Test
    fun actualLeavesOutOngoingActivities() = test {
        save(startedAt = weekStartInstant.plusSeconds(3600), duration = 600)
        save(startedAt = weekStartInstant.plusSeconds(7200), duration = 999, ongoing = true)
        assertEquals(600, store.weeklyEffortHistory(weeks = 1).single().actualSeconds)
    }

    @Test
    fun aWeekWithNoPlanStillAppearsWithNothingPlanned() = test {
        save(startedAt = weekStartInstant.plusSeconds(3600), duration = 450)
        val week = store.weeklyEffortHistory(weeks = 1).single()
        assertEquals(0, week.plannedSeconds)
        assertEquals(450, week.actualSeconds)
    }

    @Test
    fun activitiesOutsideTheWeekAreLeftOut() = test {
        save(startedAt = weekStartInstant.minusSeconds(1), duration = 300)
        save(startedAt = calendar.startOf(weekStart.plusWeeks(1)), duration = 300)
        save(startedAt = weekStartInstant, duration = 300)
        assertEquals(300, store.weeklyEffortHistory(weeks = 1).single().actualSeconds)
    }

    @Test
    fun weeksControlsTheCount() = test {
        assertEquals(6, store.weeklyEffortHistory(weeks = 6).size)
    }

    // MARK: - Daily totals

    @Test
    fun anEmptyDatabaseHasNoDays() = test {
        assertTrue(store.dailyDurationTotals().isEmpty())
    }

    @Test
    fun sessionsOnOneDaySum() = test {
        val today = calendar.localDate(now)
        save(startedAt = calendar.startOf(today).plus(Duration.ofHours(9)), duration = 600)
        save(startedAt = calendar.startOf(today).plus(Duration.ofHours(10)), duration = 300)
        assertEquals(900, store.dailyDurationTotals()[today])
    }

    @Test
    fun separateDaysStaySeparate() = test {
        val today = calendar.localDate(now)
        val yesterday = today.minusDays(1)
        save(startedAt = calendar.startOf(today).plus(Duration.ofHours(1)), duration = 600)
        save(startedAt = calendar.startOf(yesterday).plus(Duration.ofHours(1)), duration = 1200)
        val totals = store.dailyDurationTotals()
        assertEquals(600, totals[today])
        assertEquals(1200, totals[yesterday])
    }

    @Test
    fun aSessionOutsideTheWindowIsLeftOut() = test {
        val longAgo = calendar.localDate(now).minusDays(200)
        save(startedAt = calendar.startOf(longAgo).plus(Duration.ofHours(1)), duration = 600)
        assertNull(store.dailyDurationTotals(days = 112)[longAgo])
    }

    @Test
    fun anOngoingSessionIsLeftOut() = test {
        val today = calendar.localDate(now)
        save(startedAt = calendar.startOf(today).plus(Duration.ofHours(1)), duration = 600, ongoing = true)
        assertNull(store.dailyDurationTotals()[today])
    }

    // MARK: - Stat cards

    @Test
    fun theMostCommonTimeCountsSessionsNotMinutes() = test {
        val day = calendar.localDate(now).minusDays(1)
        save(startedAt = calendar.startOf(day).plus(Duration.ofHours(14)), duration = 7200)
        save(startedAt = calendar.startOf(day).plus(Duration.ofHours(8)), duration = 300)
        save(startedAt = calendar.startOf(day).plus(Duration.ofHours(9)), duration = 300)
        assertEquals(TimeOfDayBucket.MORNING, store.mostCommonStudyTimeOfDay())
    }

    @Test
    fun timeOfDayBucketsFollowIOS() {
        assertEquals(TimeOfDayBucket.NIGHT, TimeOfDayBucket.forHour(4))
        assertEquals(TimeOfDayBucket.MORNING, TimeOfDayBucket.forHour(5))
        assertEquals(TimeOfDayBucket.AFTERNOON, TimeOfDayBucket.forHour(12))
        assertEquals(TimeOfDayBucket.EVENING, TimeOfDayBucket.forHour(17))
        assertEquals(TimeOfDayBucket.NIGHT, TimeOfDayBucket.forHour(21))
    }

    @Test
    fun nothingLoggedMeansNoStats() = test {
        assertNull(store.mostCommonStudyTimeOfDay())
        assertNull(store.mostTrainedActivityType())
    }

    @Test
    fun mostTrainedIsByTotalTime() = test {
        val day = calendar.localDate(now).minusDays(1)
        save("puzzles", calendar.startOf(day).plus(Duration.ofHours(8)), 300)
        save("puzzles", calendar.startOf(day).plus(Duration.ofHours(9)), 300)
        save("openings", calendar.startOf(day).plus(Duration.ofHours(10)), 900)
        assertEquals("openings", store.mostTrainedActivityType()?.code)
    }

    @Test
    fun intensityThresholdsFollowIOS() {
        val of = com.knightindustries.knighttraining.core.model.DailyTrainingIntensity.Companion::of
        assertNull(of(9))
        assertEquals(com.knightindustries.knighttraining.core.model.DailyTrainingIntensity.LIGHT, of(10))
        assertEquals(com.knightindustries.knighttraining.core.model.DailyTrainingIntensity.LIGHT, of(60))
        assertEquals(com.knightindustries.knighttraining.core.model.DailyTrainingIntensity.MODERATE, of(61))
        assertEquals(com.knightindustries.knighttraining.core.model.DailyTrainingIntensity.MODERATE, of(180))
        assertEquals(com.knightindustries.knighttraining.core.model.DailyTrainingIntensity.INTENSE, of(181))
    }
}
