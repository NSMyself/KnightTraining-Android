package com.knightindustries.knighttraining.core.session

import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.session.PracticeSession.Attribution
import com.knightindustries.knighttraining.core.session.PracticeSession.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

/** The timer's rules, without a clock: every transition takes the time it happens at. */
class PracticeSessionTest {
    private val t0 = Instant.parse("2026-11-11T18:00:00Z")
    private fun at(seconds: Long) = t0.plusSeconds(seconds)

    private val puzzlesGoal = Attribution.Goal(UUID.randomUUID(), "puzzles", TargetType.COUNT)
    private val openingsGoal = Attribution.Goal(UUID.randomUUID(), "openings", TargetType.DURATION)

    private fun started(attribution: Attribution = puzzlesGoal) = PracticeSession.start(t0, attribution)

    @Test
    fun elapsedTimeComesFromTimestamps() {
        val session = started()
        assertEquals(0, session.elapsedSeconds(t0))
        assertEquals(125, session.elapsedSeconds(at(125)))
    }

    @Test
    fun pausesAreExcludedWhileRunningAndAfterResuming() {
        val paused = started().pause(at(60))
        assertTrue(paused.isPaused)
        assertEquals("the clock holds while paused", 60, paused.elapsedSeconds(at(300)))

        val resumed = paused.resume(at(300))
        assertEquals(60 + 100, resumed.elapsedSeconds(at(400)))
    }

    @Test
    fun changeClosesASegmentAndRestartsOnlyTheSegmentClock() {
        val session = started().change(at(600), openingsGoal, resourceId = null, outgoingCount = 12)

        assertEquals(1, session.completedSegments.size)
        val first = session.completedSegments.single()
        assertEquals(puzzlesGoal, first.attribution)
        assertEquals(600, first.durationSeconds)
        assertEquals(12, first.count)
        assertEquals(0, session.segmentElapsedSeconds(at(600)))
        assertEquals(900, session.elapsedSeconds(at(900)))
        assertEquals(openingsGoal, session.attribution)
    }

    /** A pause in the first segment never comes off the second. */
    @Test
    fun aSegmentOnlyLosesItsOwnPauses() {
        val session = started().pause(at(100)).resume(at(200)).change(at(400), openingsGoal, null)
        assertEquals(300, session.completedSegments.single().durationSeconds)
        assertEquals(500, session.segmentElapsedSeconds(at(900)))
        assertEquals(800, session.totalSessionSeconds(at(900)))
    }

    @Test
    fun changingWhilePausedResumesAndExcludesThePause() {
        val session = started().pause(at(100)).change(at(400), openingsGoal, null)
        assertEquals(100, session.completedSegments.single().durationSeconds)
        assertFalse(session.isPaused)
    }

    @Test
    fun onlyAnAdHocSegmentKeepsItsCreditedGoal() {
        val goalId = UUID.randomUUID()
        val fromGoal = started().change(at(60), Attribution.General, null, outgoingCreditedGoalId = goalId)
        assertNull(fromGoal.completedSegments.single().creditedGoalId)

        val fromAdHoc = started(PracticeSession.adHoc("endgames", null)).change(at(60), Attribution.General, null, outgoingCreditedGoalId = goalId)
        assertEquals(goalId, fromAdHoc.completedSegments.single().creditedGoalId)
    }

    @Test
    fun stopFreezesTheClocksAndReviewsAKnownCategory() {
        val stopped = started().stop(at(300))
        assertEquals(Phase.Reviewing("puzzles"), stopped.phase)
        assertEquals("frozen at stop", 300, stopped.elapsedSeconds(at(5000)))
    }

    @Test
    fun aQuickStartSessionAsksForItsCategory() {
        val stopped = started(Attribution.General).stop(at(300))
        assertEquals(Phase.Categorizing, stopped.phase)
        assertEquals(Phase.Reviewing("endgames"), stopped.categorize("endgames").phase)
    }

    @Test
    fun stoppingWhilePausedDoesNotCountThePause() {
        val stopped = started().pause(at(100)).stop(at(1000))
        assertEquals(100, stopped.finalSegmentSeconds(at(2000)))
    }

    /** Accidental-tap protection: under 10s of the whole session, not of the last segment. */
    @Test
    fun theAccidentalStopThresholdIsWholeSession() {
        assertTrue(started().stop(at(9)).isBelowAccidentalStopThreshold(at(9)))
        assertFalse(started().stop(at(10)).isBelowAccidentalStopThreshold(at(10)))

        val split = started().change(at(8), openingsGoal, null).stop(at(12))
        assertFalse("8s + 4s is a 12-second session", split.isBelowAccidentalStopThreshold(at(12)))
    }

    @Test
    fun aDurationCorrectionReplacesOnlyTheFinalSegment() {
        val session = started().change(at(600), openingsGoal, null).stop(at(36_000)).overrideFinalDuration(45)
        assertEquals(45 * 60, session.finalSegmentSeconds(at(36_000)))
        assertEquals(600 + 45 * 60, session.totalSessionSeconds(at(36_000)))
    }

    @Test
    fun onlyGamesKeepATimeControl() {
        assertEquals("15+10", PracticeSession.adHoc("playing", "15+10").timeControl)
        assertNull(PracticeSession.adHoc("puzzles", "15+10").timeControl)
    }

    /** What survives the app being killed. */
    @Test
    fun aSessionRoundTripsThroughItsSnapshot() {
        val session = started()
            .pause(at(30)).resume(at(40))
            .change(at(100), PracticeSession.adHoc("playing", "3+2"), UUID.randomUUID(), outgoingCount = 5)
            .stop(at(200))
            .withGoalCredit(PracticeSession.GoalCredit.Picked(UUID.randomUUID()))
            .overrideFinalDuration(2)

        assertEquals(session, PracticeSession.decode(session.encode()))
    }

    @Test
    fun anUnreadableSnapshotDecodesToNull() {
        assertNull(PracticeSession.decode("{not json"))
    }
}
