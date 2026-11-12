package com.knightindustries.knighttraining.core.model

import com.knightindustries.knighttraining.core.model.PlanSuggestionHistory.ActiveResource
import com.knightindustries.knighttraining.core.model.PlanSuggestionHistory.CatalogCandidate
import com.knightindustries.knighttraining.core.model.PlanSuggestionHistory.CoachTarget
import com.knightindustries.knighttraining.core.model.PlanSuggestionHistory.GoalOutcome
import com.knightindustries.knighttraining.core.model.PlanSuggestionHistory.Week
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import kotlin.math.ceil

/** Port of iOS `PlanSuggestionEngineTests`, same inputs, same numbers. */
class PlanSuggestionEngineTest {
    private fun suggest(request: PlanSuggestionRequest, history: PlanSuggestionHistory = PlanSuggestionHistory()) =
        PlanSuggestionEngine.suggest(request, history)

    private fun request(minutes: Int, rating: Int? = null, scale: RatingScale = RatingScale.CHESS_COM_BLITZ, focus: List<String> = emptyList()) =
        PlanSuggestionRequest(minutes, rating, scale, focus)

    private fun estimated(suggestion: PlanSuggestion): Int = PlanEstimator.estimate(
        suggestion.goals.map { PlanEstimatorBlock(it.activityTypeCode, it.targetType, it.targetValue, it.timeControlLabel) },
    )?.minutes ?: 0

    private fun PlanSuggestion.goal(code: String) = goals.firstOrNull { it.activityTypeCode == code }

    private fun PlanSuggestion.hasFocusReason() = reasons.any { it is PlanSuggestionReason.Focus }

    @Test
    fun aPlanFillsRoughlyTheRequestedTime() {
        for (minutes in listOf(120, 240, 480)) {
            assertEquals("$minutes min", minutes.toDouble(), estimated(suggest(request(minutes, 1400))).toDouble(), minutes * 0.15)
        }
    }

    @Test
    fun everyGoalIsATargetTheBuilderCanExpress() {
        val suggestion = suggest(request(240, 1400))
        assertEquals(TargetType.COUNT, suggestion.goal("puzzles")?.targetType)
        assertEquals(TargetType.COUNT, suggestion.goal("playing")?.targetType)
        assertNotNull(suggestion.goal("playing")?.timeControlLabel)
        for (code in listOf("analyzing", "openings", "endgames")) assertEquals(code, TargetType.DURATION, suggestion.goal(code)?.targetType)
    }

    @Test
    fun aSmallBudgetDropsBlocksTooSmallToBeWorthHaving() {
        val suggestion = suggest(request(60, 1400))
        assertTrue(suggestion.goals.size < 5)
        assertTrue(suggestion.goals.isNotEmpty())
    }

    @Test
    fun aFocusAreaGetsMoreTimeAndIsNeverDropped() {
        val plain = suggest(request(60, 1400))
        val focused = suggest(request(60, 1400, focus = listOf("openings")))
        assertNotNull(focused.goal("openings"))
        assertTrue((focused.goal("openings")?.targetValue ?: 0) > (plain.goal("openings")?.targetValue ?: 0))
        assertTrue(PlanSuggestionReason.Focus(listOf("openings")) in focused.reasons)
    }

    @Test
    fun unknownFocusCodesAreIgnored() {
        assertFalse(suggest(request(240, focus = listOf("general"))).hasFocusReason())
    }

    @Test
    fun pickingCoachingBooksAnHourLessonOutOfTheBudget() {
        val suggestion = suggest(request(240, 1400, focus = listOf("coaching")))
        assertEquals(SuggestedGoal("coaching", TargetType.DURATION, 3600), suggestion.goal("coaching"))
        assertFalse("A lesson isn't a training area to lean into", suggestion.hasFocusReason())
        assertEquals(240.0, estimated(suggestion).toDouble(), 30.0)
    }

    @Test
    fun coachingIsNeverSuggestedUnlessPicked() {
        assertNull(suggest(request(600, 1400)).goal("coaching"))
    }

    @Test
    fun inASmallBudgetTheLessonIsShortenedToLeaveRoomForTraining() {
        val suggestion = suggest(request(60, 1400, focus = listOf("coaching")))
        assertEquals(40 * 60, suggestion.goal("coaching")?.targetValue)
        assertEquals("One other block in the 20 minutes left", 2, suggestion.goals.size)
        assertEquals(60.0, estimated(suggestion).toDouble(), 10.0)
    }

    @Test
    fun everyPickedAreaIsKeptHoweverManyThereAre() {
        val picked = listOf("puzzles", "analyzing", "openings", "endgames")
        val suggestion = suggest(request(20 * 60, 1400, focus = picked + "coaching"))
        assertTrue(PlanSuggestionReason.Focus(picked) in suggestion.reasons)
        for (code in picked + "coaching") assertNotNull(code, suggestion.goal(code))
    }

    @Test
    fun aLichessRatingWinsOverATypedOne() {
        val suggestion = suggest(request(240, 900), PlanSuggestionHistory(lichessRating = 2100))
        assertTrue(PlanSuggestionReason.Rating(2100, RatingBand.EXPERT, fromLichess = true) in suggestion.reasons)
    }

    @Test
    fun noRatingPlansAsADevelopingPlayer() {
        assertTrue(PlanSuggestionReason.NoRating in suggest(request(240)).reasons)
    }

    @Test
    fun aPlayerMissingGoalsGetsAPlanNearerWhatTheyTrain() {
        val history = PlanSuggestionHistory(weeks = listOf(Week(120, 1, 4), Week(100, 2, 4)))
        val suggestion = suggest(request(240, 1400), history)
        assertEquals(135, suggestion.budgetMinutes)
        assertTrue(PlanSuggestionReason.BudgetCalibrated(240, 135, 110, 0.375) in suggestion.reasons)
    }

    @Test
    fun aPlayerHittingTheirGoalsGetsWhatTheyAskedFor() {
        assertEquals(240, suggest(request(240), PlanSuggestionHistory(weeks = listOf(Week(120, 4, 4), Week(100, 4, 4)))).budgetMinutes)
    }

    @Test
    fun oneWeekOfHistoryIsNotEnoughToOverrideTheRequest() {
        assertEquals(240, suggest(request(240), PlanSuggestionHistory(weeks = listOf(Week(30, 0, 4)))).budgetMinutes)
    }

    @Test
    fun calibrationNeverRaisesTheBudget() {
        assertEquals(240, suggest(request(240), PlanSuggestionHistory(weeks = listOf(Week(600, 0, 4), Week(600, 0, 4)))).budgetMinutes)
    }

    @Test
    fun calibrationNeverGoesBelowAnHour() {
        assertEquals(60, suggest(request(240), PlanSuggestionHistory(weeks = listOf(Week(0, 0, 4), Week(0, 0, 4)))).budgetMinutes)
    }

    @Test
    fun mostlyMissedGoalTypesGetASmallerShare() {
        val plain = suggest(request(300, 1400))
        val missed = suggest(request(300, 1400), PlanSuggestionHistory(goalOutcomes = mapOf("endgames" to GoalOutcome(0, 3))))
        assertTrue((missed.goal("endgames")?.targetValue ?: 0) < (plain.goal("endgames")?.targetValue ?: 0))
        assertTrue(PlanSuggestionReason.LighterOn("endgames", 0.0) in missed.reasons)
    }

    @Test
    fun aRecentRapidTimeControlIsKept() {
        assertEquals("10+5", suggest(request(240), PlanSuggestionHistory(recentTimeControlLabel = "10+5")).goal("playing")?.timeControlLabel)
    }

    @Test
    fun aRecentBlitzTimeControlIsReplacedWithATrainingOne() {
        assertEquals("15+10", suggest(request(240, 1400), PlanSuggestionHistory(recentTimeControlLabel = "3+2")).goal("playing")?.timeControlLabel)
    }

    @Test
    fun anActiveResourceIsLinkedToTheBlockItWasStudiedUnder() {
        val bookId = UUID.randomUUID()
        val history = PlanSuggestionHistory(activeResources = listOf(ActiveResource(bookId, "100 Endgames You Must Know", "endgames")))
        val suggestion = suggest(request(240), history)
        assertEquals(bookId, suggestion.goal("endgames")?.resourceId)
        assertNull(suggestion.goal("openings")?.resourceId)
        assertTrue(PlanSuggestionReason.LinkedResource("100 Endgames You Must Know", "endgames") in suggestion.reasons)
    }

    @Test
    fun theCoachsTargetReplacesTheEnginesNumberAndTheTotalStaysPut() {
        val plain = suggest(request(240, 1400))
        val coached = suggest(request(240, 1400), PlanSuggestionHistory(coachTarget = CoachTarget("puzzles", 50)))
        assertEquals(50, coached.goal("puzzles")?.targetValue)
        assertTrue(PlanSuggestionReason.CoachTargetApplied("puzzles", TargetType.COUNT, 50) in coached.reasons)
        assertTrue(50 > (plain.goal("puzzles")?.targetValue ?: 0))
        assertTrue((coached.goal("endgames")?.targetValue ?: 0) < (plain.goal("endgames")?.targetValue ?: 0))
        assertEquals(240.0, estimated(coached).toDouble(), 240 * 0.15)
    }

    @Test
    fun aTimedCoachTargetIsKeptInSeconds() {
        val suggestion = suggest(request(240), PlanSuggestionHistory(coachTarget = CoachTarget("openings", 5400)))
        assertEquals(TargetType.DURATION, suggestion.goal("openings")?.targetType)
        assertEquals(5400, suggestion.goal("openings")?.targetValue)
    }

    @Test
    fun aCoachTargetThatFillsTheBudgetLeavesNoRoomForOtherBlocks() {
        val suggestion = suggest(request(60), PlanSuggestionHistory(coachTarget = CoachTarget("openings", 3600)))
        assertEquals(listOf("openings"), suggestion.goals.map { it.activityTypeCode })
    }

    @Test
    fun aCoachedGameTargetUsesThePlansTimeControl() {
        val suggestion = suggest(request(240, 1400), PlanSuggestionHistory(coachTarget = CoachTarget("playing", 3)))
        assertEquals(3, suggestion.goal("playing")?.targetValue)
        assertEquals("15+10", suggestion.goal("playing")?.timeControlLabel)
    }

    private fun candidate(title: String, topic: String, min: Int? = null, max: Int? = null, length: Int? = null) =
        CatalogCandidate(UUID.randomUUID(), title, topic, min, max, length)

    @Test
    fun aCourseIsOfferedForAFocusBlockThatFitsTheRating() {
        val history = PlanSuggestionHistory(
            catalogCandidates = listOf(
                candidate("Endgame Roadmap", "endgames", 1200, 2000, 600),
                candidate("Opening Principles", "openings", 800, 1600),
            ),
        )
        val suggestion = suggest(request(240, 1400, focus = listOf("endgames")), history)
        assertEquals("Endgame Roadmap", suggestion.course?.title)
        assertEquals("endgames", suggestion.course?.activityTypeCode)
        val weekly = (suggestion.goal("endgames")?.targetValue ?: 0) / 60
        assertEquals(ceil(600.0 / weekly).toInt(), suggestion.course?.weeksAtThisPace)
    }

    @Test
    fun aCourseOutsideThePlayersRatingIsNeverOffered() {
        val history = PlanSuggestionHistory(lichessRating = 1100, catalogCandidates = listOf(candidate("Must-Know Endgame Theory", "endgames", 1600, 2000)))
        assertNull(suggest(request(240, focus = listOf("endgames")), history).course)
    }

    @Test
    fun aLichessRatingIsConvertedBeforeMatchingACourse() {
        val history = PlanSuggestionHistory(
            lichessRating = 1600, lichessRatingType = "rapid",
            catalogCandidates = listOf(candidate("For stronger players", "openings", 1500, 2600), candidate("For club players", "openings", 800, 2000)),
        )
        assertEquals("For club players", suggest(request(240, focus = listOf("openings")), history).course?.title)
    }

    @Test
    fun aTypedLichessRatingIsConvertedButATypedChessComRatingIsNot() {
        val history = PlanSuggestionHistory(
            catalogCandidates = listOf(candidate("For stronger players", "openings", 1500, 2600), candidate("For club players", "openings", 800, 1400)),
        )
        assertEquals("For club players", suggest(request(240, 1700, RatingScale.LICHESS_BLITZ, listOf("openings")), history).course?.title)
        assertEquals("For stronger players", suggest(request(240, 1700, RatingScale.CHESS_COM_BLITZ, listOf("openings")), history).course?.title)
    }

    @Test
    fun theRatingReasonKeepsThePlayersOwnNumber() {
        val suggestion = suggest(request(240), PlanSuggestionHistory(lichessRating = 1600, lichessRatingType = "rapid"))
        assertTrue(PlanSuggestionReason.Rating(1600, RatingBand.forRating(1600), fromLichess = true) in suggestion.reasons)
    }

    @Test
    fun aNarrowerFittingRangeWins() {
        val history = PlanSuggestionHistory(
            catalogCandidates = listOf(candidate("Broad", "openings", 0, 3000), candidate("Targeted", "openings", 1300, 1600), candidate("Unknown range", "openings")),
        )
        assertEquals("Targeted", suggest(request(240, 1400, focus = listOf("openings")), history).course?.title)
    }

    @Test
    fun aBlockWithTheirOwnResourceIsNotOfferedACourse() {
        val history = PlanSuggestionHistory(
            activeResources = listOf(ActiveResource(UUID.randomUUID(), "Silman", "endgames")),
            catalogCandidates = listOf(candidate("Endgame Roadmap", "endgames")),
        )
        assertNull(suggest(request(240, 1400, focus = listOf("endgames")), history).course)
    }

    @Test
    fun onlyOneCourseIsOfferedAndCoursesNeverBecomeBlocks() {
        val history = PlanSuggestionHistory(
            catalogCandidates = listOf(candidate("Endgame Roadmap", "endgames"), candidate("Opening Principles", "openings"), candidate("Tactic Ninja", "puzzles")),
        )
        val plain = suggest(request(240, 1400))
        val withCatalog = suggest(request(240, 1400), history)
        assertNotNull(withCatalog.course)
        assertEquals(plain.goals, withCatalog.goals)
    }

    @Test
    fun aStrategyCourseIsNeverOffered() {
        val history = PlanSuggestionHistory(catalogCandidates = listOf(candidate("Silent Strategy", "analyzing")))
        assertNull(suggest(request(240, 1400, focus = listOf("analyzing")), history).course)
    }
}
