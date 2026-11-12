package com.knightindustries.knighttraining.core.lichess

import com.knightindustries.knighttraining.core.model.LichessStreakDay
import com.knightindustries.knighttraining.core.model.LichessStreakDetection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** Port of iOS `LichessStreakDetectionTests`: UTC days, the last sync somewhere inside one. */
class LichessStreakDetectionTest {
    private val base = Instant.parse("2026-10-11T00:00:00Z")

    private fun day(offset: Int): Instant = base.plusSeconds(offset * 86_400L)

    private fun streakDay(offset: Int, runs: Int, score: Int) = LichessStreakDay(day(offset), day(offset + 1), runs, score)

    private fun hours(value: Double, after: Instant): Instant = after.plusSeconds((value * 3600).toLong())

    @Test
    fun runsLaterTheSameDayAreNew() {
        val result = LichessStreakDetection.detect(listOf(streakDay(0, 8, 14)), hours(10.0, day(0)), 5, 14, hours(12.0, day(0)))
        assertEquals(3, result.newRuns)
        assertNull("The day's best didn't move, so no new run can be credited with it", result.bestScore)
        assertEquals(8, result.runsForCurrentDay)
        assertEquals(14, result.scoreForCurrentDay)
    }

    @Test
    fun aNewDayBestIsCreditedToTheNewRuns() {
        val result = LichessStreakDetection.detect(listOf(streakDay(0, 6, 21)), hours(10.0, day(0)), 5, 14, hours(12.0, day(0)))
        assertEquals(1, result.newRuns)
        assertEquals(21, result.bestScore)
    }

    @Test
    fun runsOnADayThatHadNoneAtTheLastSyncAreAllNew() {
        val result = LichessStreakDetection.detect(
            listOf(streakDay(0, 2, 9), streakDay(-1, 4, 30)), hours(1.0, day(0)), 0, 0, hours(20.0, day(0)),
        )
        assertEquals(2, result.newRuns)
        assertEquals(9, result.bestScore)
    }

    @Test
    fun runsAcrossAMidnightAreAllCounted() {
        val result = LichessStreakDetection.detect(
            listOf(streakDay(0, 1, 7), streakDay(-1, 3, 12), streakDay(-2, 5, 40)),
            hours(20.0, day(-1)), 2, 10, hours(9.0, day(0)),
        )
        assertEquals(2, result.newRuns)
        assertEquals(12, result.bestScore)
        assertEquals(1, result.runsForCurrentDay)
        assertEquals(7, result.scoreForCurrentDay)
    }

    @Test
    fun nothingNewMeansNoRunsAndNothingToSaveForAnEmptyToday() {
        val result = LichessStreakDetection.detect(listOf(streakDay(-1, 3, 12)), hours(23.0, day(-1)), 3, 12, hours(8.0, day(0)))
        assertEquals(0, result.newRuns)
        assertNull(result.bestScore)
        assertEquals(0, result.runsForCurrentDay)
        assertEquals(0, result.scoreForCurrentDay)
    }

    @Test
    fun aShrinkingCountIsNeverNegative() {
        val result = LichessStreakDetection.detect(listOf(streakDay(0, 2, 5)), hours(10.0, day(0)), 199, 34, hours(12.0, day(0)))
        assertEquals(0, result.newRuns)
        assertNull(result.bestScore)
    }
}
