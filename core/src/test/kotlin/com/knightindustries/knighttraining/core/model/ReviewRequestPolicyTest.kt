package com.knightindustries.knighttraining.core.model

import com.knightindustries.knighttraining.core.model.ReviewRequestPolicy.Decision
import com.knightindustries.knighttraining.core.model.ReviewRequestPolicy.Reason
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** iOS `ReviewRequestCoordinator`'s gates, in its order. */
class ReviewRequestPolicyTest {
    private val now = Instant.parse("2026-11-25T12:00:00Z")
    private val engaged = Duration.ofMinutes(5)

    private suspend fun decide(
        streak: Int = 2,
        state: ReviewState = ReviewState(),
        version: String = "1.1",
        timeInApp: Duration = engaged,
        calm: Boolean = true,
    ) = ReviewRequestPolicy.evaluate(streak, state, version, now, timeInApp) { calm }

    @Test
    fun milestonesAreTheHighestReachedNeverAnEarlierOne() {
        assertNull(ReviewMilestone.highestReached(1))
        assertEquals(ReviewMilestone.FIRST, ReviewMilestone.highestReached(2))
        assertEquals(ReviewMilestone.FIRST, ReviewMilestone.highestReached(7))
        assertEquals(ReviewMilestone.SECOND, ReviewMilestone.highestReached(9))
        assertEquals(ReviewMilestone.THIRD, ReviewMilestone.highestReached(30))
    }

    @Test
    fun aTwoWeekStreakAsks() = runTest {
        assertEquals(Decision.Request(ReviewMilestone.FIRST), decide())
    }

    @Test
    fun noMilestoneNoAsk() = runTest {
        assertEquals(Decision.Suppressed(Reason.NO_MILESTONE_REACHED), decide(streak = 1))
    }

    @Test
    fun eachMilestoneIsAskedOnce() = runTest {
        val asked = ReviewState().asked(ReviewMilestone.FIRST, "1.0", now.minus(Duration.ofDays(200)))
        assertEquals(Decision.Suppressed(Reason.NO_MILESTONE_REACHED), decide(streak = 5, state = asked))
        assertEquals(Decision.Request(ReviewMilestone.SECOND), decide(streak = 8, state = asked))
    }

    @Test
    fun neverTwiceInOneVersion() = runTest {
        val asked = ReviewState().asked(ReviewMilestone.FIRST, "1.1", now.minus(Duration.ofDays(200)))
        assertEquals(Decision.Suppressed(Reason.ALREADY_ASKED_THIS_VERSION), decide(streak = 8, state = asked))
    }

    @Test
    fun neverWithinNinetyDays() = runTest {
        val asked = ReviewState().asked(ReviewMilestone.FIRST, "1.0", now.minus(Duration.ofDays(89)))
        assertEquals(Decision.Suppressed(Reason.WITHIN_DATE_WINDOW), decide(streak = 8, state = asked))
    }

    @Test
    fun notRightAfterOpeningTheApp() = runTest {
        assertEquals(Decision.Suppressed(Reason.SESSION_TOO_SHORT), decide(timeInApp = Duration.ofSeconds(30)))
    }

    @Test
    fun notWhileThePhoneIsMoving() = runTest {
        assertEquals(Decision.Suppressed(Reason.DEVICE_IN_MOTION), decide(calm = false))
    }

    /** A suppressed week leaves the milestone armed: only an actual ask records it. */
    @Test
    fun aSuppressedWeekKeepsTheMilestoneForNextTime() = runTest {
        val state = ReviewState()
        decide(calm = false)
        assertEquals(Decision.Request(ReviewMilestone.FIRST), decide(state = state))
    }

    @Test
    fun theMotionSensorIsOnlyReadWhenEverythingElsePasses() = runTest {
        var sampled = false
        ReviewRequestPolicy.evaluate(1, ReviewState(), "1.1", now, engaged) { sampled = true; true }
        assertFalse(sampled)
    }

    @Test
    fun calmIsASteadyAcceleration() {
        assertTrue(ReviewRequestPolicy.isCalm(listOf(0.01, 0.02, 0.015, 0.01)))
        assertFalse(ReviewRequestPolicy.isCalm(listOf(0.0, 0.4, 0.05, 0.6)))
        assertTrue("no sensor fails open", ReviewRequestPolicy.isCalm(emptyList()))
    }
}
