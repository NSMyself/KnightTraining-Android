package com.knightindustries.knighttraining.core.model

import com.knightindustries.knighttraining.core.model.Fixtures.book
import com.knightindustries.knighttraining.core.model.Fixtures.puzzles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.util.UUID

/**
 * Ports of iOS `PlanEstimatorTests`, the pure half of `CoachingTopicTests` and
 * `UntitledPlanNameTests`, `ActivityTests`, and the `Resource.withProgress` cases from
 * `ResourceTests`.
 */
class SmallModelsTest {
    // MARK: - PlanEstimator

    @Test
    fun aGameCountsBothClocks() {
        assertEquals(20, PlanEstimator.gameMinutes(TimeControl(10)))
        assertEquals(60, PlanEstimator.gameMinutes(TimeControl(30)))
    }

    @Test
    fun theIncrementAddsTimeForBothPlayersOverFortyMoves() {
        assertEquals(43, PlanEstimator.gameMinutes(TimeControl(15, 10)))
        assertEquals(9, PlanEstimator.gameMinutes(TimeControl(3, 2)))
    }

    @Test
    fun aPlayingBlockIsEstimatedFromFullLengthGames() {
        val estimate = PlanEstimator.estimate(
            listOf(
                PlanEstimatorBlock("playing", TargetType.COUNT, 3, "10 min"),
                PlanEstimatorBlock("puzzles", TargetType.COUNT, 10, null),
            ),
        )
        assertEquals(PlanEstimate(60 + 20, false), estimate)
    }

    @Test
    fun aPlayingBlockWithNoTimeControlMakesTheEstimateAFloor() {
        val estimate = PlanEstimator.estimate(
            listOf(
                PlanEstimatorBlock("playing", TargetType.COUNT, 3, null),
                PlanEstimatorBlock("openings", TargetType.DURATION, 1800, null),
            ),
        )
        assertEquals(PlanEstimate(30, true), estimate)
    }

    @Test
    fun nothingEstimableIsNull() {
        assertNull(PlanEstimator.estimate(emptyList()))
        assertNull(PlanEstimator.estimate(listOf(PlanEstimatorBlock("playing", TargetType.COUNT, 3, null))))
    }

    // MARK: - CoachingTopic

    @Test
    fun encodeKeepsAFixedOrderAndDropsDuplicates() {
        assertEquals(
            "openings,endgames,analyzing",
            CoachingTopic.encode(listOf(CoachingTopic.ANALYZING, CoachingTopic.ENDGAMES, CoachingTopic.ENDGAMES, CoachingTopic.OPENINGS)),
        )
        assertNull(CoachingTopic.encode(emptyList()))
    }

    @Test
    fun decodeDropsUnknownCodesInsteadOfFailing() {
        assertEquals(listOf(CoachingTopic.ENDGAMES, CoachingTopic.TACTICS), CoachingTopic.decode("endgames,calculation,tactics"))
        assertEquals(emptyList<CoachingTopic>(), CoachingTopic.decode(null))
    }

    @Test
    fun gameAnalysisReusesTheActivityTypesCode() {
        assertEquals(ActivityType.ANALYZING, CoachingTopic.ANALYZING.code)
    }

    // MARK: - Untitled plan names

    private fun untitled(vararg taken: String?) = PlanTemplate.untitledName(
        taken.toList(),
        base = "Untitled training plan",
        numbered = { "Untitled training plan ($it)" },
    )

    @Test
    fun theFirstUnnamedPlanGetsTheBareName() {
        assertEquals("Untitled training plan", untitled("Endgame Week", null, ""))
    }

    @Test
    fun aTakenNameGetsTheFirstFreeNumber() {
        assertEquals("Untitled training plan (1)", untitled("Untitled training plan"))
        assertEquals("Untitled training plan (2)", untitled("Untitled training plan", "Untitled training plan (1)"))
    }

    @Test
    fun aGapIsReusedBeforeCountingHigher() {
        assertEquals("Untitled training plan (1)", untitled("Untitled training plan", "Untitled training plan (2)"))
    }

    @Test
    fun surroundingWhitespaceStillCountsAsTaken() {
        assertEquals("Untitled training plan (1)", untitled("Untitled training plan "))
    }

    @Test
    fun aBlankNameDisplaysTheFallback() {
        assertEquals("My Training Plan", PlanTemplate(name = "  ").displayName("My Training Plan"))
        assertEquals("Endgames", PlanTemplate(name = "Endgames").displayName("My Training Plan"))
    }

    // MARK: - Activity

    @Test
    fun sessionIdDefaultsToNullAndIsKeptWhenSet() {
        // A row without a session is a session of one, which only holds if the default stays null.
        assertNull(Activity(activityType = puzzles, startedAt = Instant.now(), duration = 60).sessionId)
        val sessionId = UUID.randomUUID()
        assertEquals(sessionId, Activity(activityType = puzzles, startedAt = Instant.now(), duration = 60, sessionId = sessionId).sessionId)
    }

    // MARK: - Resource

    @Test
    fun withProgressClampsAndCountsACompletionAt100() {
        val original = Resource(kind = book, title = "My System", progressPercentage = 20, completionCount = 2)
        assertEquals(40, original.withProgress(40).progressPercentage)
        assertEquals(2, original.withProgress(40).completionCount)
        assertEquals(100, original.withProgress(150).progressPercentage)
        assertEquals(3, original.withProgress(150).completionCount)
        assertEquals(0, original.withProgress(-5).progressPercentage)
        assertEquals(2, original.withProgress(-5).completionCount)
    }

    @Test
    fun metadataEditsKeepLastAccessedAt() {
        val accessed = Instant.ofEpochSecond(1_000)
        val original = Resource(kind = book, title = "My System", lastAccessedAt = accessed)
        assertEquals(accessed, original.withOwnership(true).lastAccessedAt)
        assertEquals(accessed, original.withNotes("Great").lastAccessedAt)
    }
}
