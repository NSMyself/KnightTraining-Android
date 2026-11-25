package com.knightindustries.knighttraining.core.coach

import com.knightindustries.knighttraining.core.model.Activity
import com.knightindustries.knighttraining.core.model.ActivityType
import com.knightindustries.knighttraining.core.model.CoachingTopic
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import com.knightindustries.knighttraining.core.model.WeeklyReview
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * Ports of the pure iOS coach tests (`WeeklyFocusTests`' prompt cases, `WeeklyTipLLMDebugTesterTests`)
 * plus the pipeline against a scripted model. Tip *quality* needs the real model on a device.
 */
class WeeklyTipCoachTest {
    private val utc = ZoneOffset.UTC
    private val puzzles = ActivityType(UUID.randomUUID(), "puzzles")
    private val openings = ActivityType(UUID.randomUUID(), "openings")
    private val coaching = ActivityType(UUID.randomUUID(), "coaching")
    private val now = Instant.parse("2026-11-12T12:00:00Z")

    private fun plan(): WeeklyPlan {
        val id = UUID.randomUUID()
        return WeeklyPlan(
            id = id, planTemplateId = UUID.randomUUID(), weekStart = LocalDate.of(2026, 11, 1),
            weeklyGoals = listOf(
                WeeklyGoal(weeklyPlanId = id, activityType = puzzles, targetType = TargetType.COUNT, targetValue = 30, currentValue = 30, completedAt = now),
                WeeklyGoal(weeklyPlanId = id, activityType = openings, targetType = TargetType.DURATION, targetValue = 5400, currentValue = 1800),
            ),
        )
    }

    // MARK: - The prompt

    @Test
    fun theContextLaysOutPlanProgressNotesAndSessions() {
        val context = WeeklyTipCoach.buildContext(plan(), listOf("- What held them back: work trips"), listOf("- Nov 10, 2026 — puzzles: 12"))
        assertTrue(context.contains("Last week's committed plan:\n- puzzles: 30 Puzzles\n- openings: 1 hr 30 min of Openings"))
        assertTrue(context.contains("What they actually did:\n- puzzles: 30/30 — completed\n- openings: 30m/90m"))
        assertTrue(context.contains("Notes:\n- What held them back: work trips"))
        assertTrue(context.contains("- Nov 10, 2026 — puzzles: 12"))
        assertFalse("No Lichess, no puzzle section", context.contains("Lichess puzzles"))
        assertFalse(context.contains(CoachingSuggestion.SECTION_HEADING))
    }

    @Test
    fun theCoachIsToldLastWeeksFocusAndThePuzzleStatus() {
        val status = LichessPuzzleStatus.Played(80, 64, 1750, 1700, LichessPuzzleWeakness("Fork", 12, -180))
        val context = WeeklyTipCoach.buildContext(plan(), emptyList(), emptyList(), status, lastWeekFocus = "Endgame technique")
        assertTrue(context.contains("Lichess puzzles (last 30 days):"))
        assertTrue(context.contains("- 80 puzzles, 64% solved on the first try, performance 1750 (average puzzle rating 1700)"))
        assertTrue(context.contains("- weakest theme: Fork — 12 attempts, performing about 180 points below"))
        assertTrue(context.contains("The focus they chose for this week: Endgame technique"))
        assertTrue(context.contains("- none left this week"))
    }

    @Test
    fun noLichessPuzzlesSaysWhetherAnyWereLoggedInTheApp() {
        assertEquals(listOf("- none solved on Lichess (and none logged in this app either)"), WeeklyTipCoach.puzzleStatusLines(LichessPuzzleStatus.NoPuzzles, 0))
        assertEquals(
            listOf("- none solved on Lichess (they did log 40 puzzles in this app in the same period, so they may solve them elsewhere)"),
            WeeklyTipCoach.puzzleStatusLines(LichessPuzzleStatus.NoPuzzles, 40),
        )
    }

    @Test
    fun theCoachingSectionAppearsOnlyWhenTheGateIsOpen() {
        val suggestion = CoachingSuggestion(CoachingSuggestion.Reason.RecurringWeakTheme("Fork", 3))
        val open = WeeklyTipCoach.buildContext(plan(), emptyList(), emptyList(), coachingSuggestion = suggestion)
        assertTrue(open.contains(CoachingSuggestion.SECTION_HEADING))
        assertTrue(open.contains("Fork at 3 weekly reviews in a row"))
    }

    @Test
    fun theLatestLessonsTopicsAreNamed() {
        val activities = listOf(
            Activity(activityType = coaching, startedAt = now.minusSeconds(7 * 86_400), duration = 3600, coachingTopics = listOf(CoachingTopic.OPENINGS)),
            Activity(activityType = coaching, startedAt = now, duration = 3600, coachingTopics = listOf(CoachingTopic.ENDGAMES, CoachingTopic.ANALYZING)),
            Activity(activityType = coaching, startedAt = now.plusSeconds(60), duration = 3600),
            Activity(activityType = puzzles, startedAt = now.plusSeconds(120), duration = 600),
        )
        assertEquals("Their latest coaching lesson (Nov 12, 2026) covered: endgames, game analysis", WeeklyTipCoach.lastLessonLine(activities, utc))
        assertNull(WeeklyTipCoach.lastLessonLine(activities.drop(2), utc))
    }

    @Test
    fun sittingsAreGroupedBySessionMostRecentFirst() {
        val sitting = UUID.randomUUID()
        fun done(type: ActivityType, endedAt: Instant, seconds: Int = 600, count: Int? = null, session: UUID? = null) =
            Activity(activityType = type, startedAt = endedAt.minusSeconds(seconds.toLong()), endedAt = endedAt, duration = seconds, count = count, sessionId = session)
        val activities = listOf(
            done(puzzles, now.minusSeconds(3 * 86_400), count = 9),
            done(puzzles, now, count = 12, session = sitting),
            done(openings, now.minusSeconds(60), 1500, session = sitting),
            done(openings, now.minusSeconds(86_400), 2400),
        )
        assertEquals(
            listOf("- Nov 12, 2026 — openings: 25 min, puzzles: 12", "- Nov 11, 2026 — openings: 40 min"),
            WeeklyTipCoach.recentSessionLines(activities, zone = utc),
        )
        assertTrue(WeeklyTipCoach.recentSessionLines(emptyList()).isEmpty())
    }

    @Test
    fun theInstructionsKeepIosStructure() {
        val verifier = WeeklyTipCoach.verifierInstructions(null)
        assertTrue(verifier.contains("on eight criteria:\n1. Is it genuinely about chess training, not off-topic?\n2. Is it a sound suggestion"))
        assertTrue("Wrapped lines rejoin", verifier.contains("consistent with the data above, not exaggerated"))
        val coach = WeeklyTipCoach.coachInstructions("Portuguese (Portugal)")
        assertTrue(coach.startsWith("Hard rule, above everything else in these instructions: never write the words \"blitz\" or \"bullet,\""))
        assertTrue(coach.endsWith("only \"tip\" and \"focus\" are natural-language content."))
        assertTrue(coach.contains("Write the \"tip\" and \"focus\" fields entirely in Portuguese (Portugal)"))
        assertFalse(WeeklyTipCoach.coachInstructions(null).contains("entirely in"))
    }

    // MARK: - Backstops

    @Test
    fun aVerbatimWishIsCaughtButShortOrMissingOnesNever() {
        assertTrue(WeeklyTipCoach.echoesWishIDone("Next week, Trained Earlier In The Day, every day.", "  trained earlier in the day  "))
        assertFalse(WeeklyTipCoach.echoesWishIDone("Move your puzzles to the morning, before work.", "Trained earlier in the day"))
        assertFalse(WeeklyTipCoach.echoesWishIDone("Trained more this week.", "Trained more"))
        assertFalse(WeeklyTipCoach.echoesWishIDone("Anything", null))
        assertFalse(WeeklyTipCoach.reallyRepeatsWishIDone(null, true))
        assertFalse(WeeklyTipCoach.reallyRepeatsWishIDone("", true))
        assertTrue(WeeklyTipCoach.reallyRepeatsWishIDone("Trained earlier in the day", true))
    }

    @Test
    fun blankPlaceholderAndBannedFocusesAreDropped() {
        assertNull(WeeklyTipCoach.acceptableFocus("   "))
        assertNull(WeeklyTipCoach.acceptableFocus("N/A"))
        assertNull(WeeklyTipCoach.acceptableFocus("Blitz openings"))
        assertEquals("Pins", WeeklyTipCoach.acceptableFocus("  Pins "))
        assertEquals("blitz", WeeklyTipCoach.bannedTimeControl("Play some BLITZ this week"))
    }

    @Test
    fun aRepliesJsonIsFoundInsideProseOrAFence() {
        val raw = "Sure!\n```json\n{\"tip\": \"Add 10 puzzles.\", \"activityTypeCode\": \"puzzles\", \"suggestedTargetValue\": 40, \"focus\": null, \"extra\": 1}\n```"
        assertEquals(WeeklyTipSuggestion("Add 10 puzzles.", "puzzles", 40, null), WeeklyTipCoach.parse(raw, WeeklyTipSuggestion.serializer()))
        assertNull(WeeklyTipCoach.parse("no json here", WeeklyTipSuggestion.serializer()))
    }

    // MARK: - The pipeline

    private class Script(vararg replies: String) : LanguageModel {
        private val queue = ArrayDeque(replies.toList())
        val prompts = mutableListOf<Pair<String, String>>()

        override suspend fun respond(instructions: String, prompt: String): String {
            prompts += instructions to prompt
            return queue.removeFirst()
        }
    }

    private val goodTip = """{"tip": "Spend part of your puzzle time on Lichess's Fork theme.", "activityTypeCode": "openings", "suggestedTargetValue": 60, "focus": "Forks"}"""
    private val passing = """{"reason": "Grounded in the Fork line.", "isChessRelated": true, "isSound": true, "isPolite": true, "recommendsBannedTimeControl": false, "justRepeatsWishIDone": false, "focusIsGrounded": true, "isSpecific": true, "isActionable": true}"""

    @Test
    fun anApprovedTipCarriesTheVerdictAndTheVerifierSawIt() = runTest {
        val model = Script(goodTip, passing)
        val outcome = WeeklyTipCoach(model).run(WeeklyTipCoach.Input(plan()), languageName = null, zone = utc)
        val approved = outcome as WeeklyTipCoach.Outcome.Approved
        assertEquals("Forks", WeeklyTipCoach.acceptedFocus(approved.suggestion, approved.verdict))
        val verification = model.prompts[1].second
        assertTrue(verification.contains("Proposed tip to review:\n- Spend part of your puzzle time on Lichess's Fork theme."))
        assertTrue(verification.contains("- Suggested change: openings → 60"))
        assertTrue(verification.contains("- Proposed focus for the coming week: Forks"))
        assertTrue("The schema rides along", model.prompts[0].second.contains("Respond with only a JSON object, no other text, with these fields:\n\"tip\" (string)"))
        assertTrue(model.prompts[1].second.contains("with these fields in this order:\n\"reason\" (string)"))
    }

    @Test
    fun aBannedTimeControlIsRejectedBeforeReview() = runTest {
        val model = Script("""{"tip": "Play three blitz games after work."}""")
        val outcome = WeeklyTipCoach(model).run(WeeklyTipCoach.Input(plan()), null, utc)
        assertTrue(outcome is WeeklyTipCoach.Outcome.Rejected && outcome.verdict == null)
        assertEquals("The verifier never ran", 1, model.prompts.size)
    }

    @Test
    fun aCoachMentionIsRejectedWhileTheGateIsClosedButAllowedWhenOpen() = runTest {
        val tip = """{"tip": "A coach could help you with the Fork theme."}"""
        val closed = WeeklyTipCoach(Script(tip)).run(WeeklyTipCoach.Input(plan()), null, utc)
        assertTrue(closed is WeeklyTipCoach.Outcome.Rejected)
        val gate = CoachingSuggestion.Gate(CoachingSuggestion(CoachingSuggestion.Reason.RecurringWeakTheme("Fork", 3)), mayMentionCoach = true)
        val open = WeeklyTipCoach(Script(tip, passing)).run(WeeklyTipCoach.Input(plan(), coachingGate = gate), null, utc)
        assertTrue(open is WeeklyTipCoach.Outcome.Approved)
    }

    @Test
    fun anotherGoalsNumberIsRejectedBeforeReview() = runTest {
        val outcome = WeeklyTipCoach(Script("""{"tip": "Go for 90 puzzles.", "activityTypeCode": "puzzles", "suggestedTargetValue": 90}""")).run(WeeklyTipCoach.Input(plan()), null, utc)
        assertTrue(outcome is WeeklyTipCoach.Outcome.Rejected)
    }

    @Test
    fun aFailingVerdictRejectsButAPhantomWishVerdictDoesNot() = runTest {
        val notSpecific = passing.replace("\"isSpecific\": true", "\"isSpecific\": false")
        assertTrue(WeeklyTipCoach(Script(goodTip, notSpecific)).run(WeeklyTipCoach.Input(plan()), null, utc) is WeeklyTipCoach.Outcome.Rejected)
        // No wish was written, so "repeats the wish" can't be true: the tip stands.
        val phantom = passing.replace("\"justRepeatsWishIDone\": false", "\"justRepeatsWishIDone\": true")
        val review = WeeklyReview(weeklyPlanId = UUID.randomUUID(), heldBack = "Work trips")
        assertTrue(WeeklyTipCoach(Script(goodTip, phantom)).run(WeeklyTipCoach.Input(plan(), review), null, utc) is WeeklyTipCoach.Outcome.Approved)
    }

    @Test
    fun anUnreadableReplyThrows() = runTest {
        try {
            WeeklyTipCoach(Script("I think you should play more.")).run(WeeklyTipCoach.Input(plan()), null, utc)
            fail("Expected UnreadableResponse")
        } catch (expected: WeeklyTipCoach.UnreadableResponse) {
            assertEquals("I think you should play more.", expected.raw)
        }
    }
}
