package com.knightindustries.knighttraining.core.model

import com.knightindustries.knighttraining.core.model.Fixtures.openings
import com.knightindustries.knighttraining.core.model.Fixtures.playing
import com.knightindustries.knighttraining.core.model.Fixtures.puzzles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** Port of iOS `WeeklyTrainingStatsTests`. */
class WeeklyTrainingStatsTest {
    private fun activity(type: ActivityType, duration: Int, count: Int? = null, timeControl: String? = null) =
        Activity(activityType = type, startedAt = Instant.now(), endedAt = Instant.now(), duration = duration, count = count, timeControlLabel = timeControl)

    private fun stats(vararg activities: Activity) = WeeklyTrainingStats.from(activities.toList())

    @Test
    fun emptyProducesNoMostTrainedCode() {
        // The card hides itself on null: a week with no training must not render as zeroes.
        assertEquals(WeeklyTrainingStats(0, null, 0, 0, 0), stats())
    }

    @Test
    fun totalSecondsSumsEveryActivity() {
        assertEquals(3000, stats(activity(puzzles, 900), activity(openings, 1800), activity(playing, 300)).totalSeconds)
    }

    @Test
    fun mostTrainedPicksTheMostTimeNotTheMostSessions() {
        val result = stats(activity(puzzles, 300), activity(puzzles, 300), activity(puzzles, 300), activity(openings, 3600))
        assertEquals("openings", result.mostTrainedActivityCode)
    }

    @Test
    fun mostTrainedSumsAcrossSessionsOfTheSameType() {
        assertEquals("puzzles", stats(activity(puzzles, 1200), activity(puzzles, 1200), activity(openings, 1800)).mostTrainedActivityCode)
    }

    @Test
    fun mostTrainedBreaksTiesDeterministically() {
        val first = stats(activity(puzzles, 600), activity(openings, 600))
        val reversed = stats(activity(openings, 600), activity(puzzles, 600))
        assertEquals(first.mostTrainedActivityCode, reversed.mostTrainedActivityCode)
        // The same winner as iOS: the alphabetically first code.
        assertEquals("openings", first.mostTrainedActivityCode)
    }

    @Test
    fun puzzleCountSumsReportedQuantitiesNotSessions() {
        assertEquals(35, stats(activity(puzzles, 900, 20), activity(puzzles, 900, 15)).puzzleCount)
    }

    @Test
    fun puzzleCountTreatsUnreportedQuantityAsZeroNotOne() {
        assertEquals(10, stats(activity(puzzles, 900, null), activity(puzzles, 900, 10)).puzzleCount)
    }

    @Test
    fun puzzleCountIgnoresOtherActivityTypes() {
        assertEquals(10, stats(activity(puzzles, 900, 10), activity(playing, 900, 3), activity(openings, 900, 7)).puzzleCount)
    }

    @Test
    fun gameCountSumsPlayingQuantities() {
        assertEquals(3, stats(activity(playing, 3600, 2), activity(playing, 1800, 1), activity(puzzles, 900, 40)).gameCount)
    }

    @Test
    fun classicalGameCountExcludesFasterTimeControls() {
        val result = stats(
            activity(playing, 5400, 2, "90+30"),
            activity(playing, 3600, 1, "60 min"),
            activity(playing, 1800, 4, "10+5"),
            activity(playing, 900, 6, "3+2"),
        )
        assertEquals(13, result.gameCount)
        assertEquals(3, result.classicalGameCount)
    }

    /** "Classical" is a claim an unknown time control can't support. */
    @Test
    fun classicalGameCountExcludesGamesWithNoRecordedTimeControl() {
        val result = stats(activity(playing, 3600, 2, null), activity(playing, 3600, 1, "60 min"))
        assertEquals(3, result.gameCount)
        assertEquals(1, result.classicalGameCount)
    }

    @Test
    fun classicalGameCountIgnoresNonPlayingActivities() {
        assertEquals(0, stats(activity(puzzles, 3600, 30, "60 min")).classicalGameCount)
    }

    @Test
    fun puzzlesWithNoCountLeaveTheMostTrainedCodeIntact() {
        assertNull(stats().mostTrainedActivityCode)
        assertEquals("puzzles", stats(activity(puzzles, 60)).mostTrainedActivityCode)
    }
}
