package com.knightindustries.knighttraining.core.store

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/** Port of iOS `GoalCompletionMomentTests`. */
class GoalCompletionTest {
    private val plan = UUID.randomUUID()
    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()
    private val c = UUID.randomUUID()

    private fun snapshot(plan: UUID?, completed: Set<UUID>) =
        GoalCompletionSnapshot(plan, completed, isWholeWeekComplete = completed == setOf(a, b, c))

    private fun moment(previous: GoalCompletionSnapshot?, current: GoalCompletionSnapshot) =
        GoalCompletionSnapshot.isMoment(previous, current)

    @Test fun aGoalCrossingItsTargetIsAMoment() {
        assertTrue(moment(snapshot(plan, emptySet()), snapshot(plan, setOf(a))))
    }

    /** Opening the app on a week that's already partly done. */
    @Test fun theFirstSurfaceIsNotAMoment() {
        assertFalse(moment(null, snapshot(plan, setOf(a))))
    }

    @Test fun nothingNewlyCompletedIsNotAMoment() {
        assertFalse(moment(snapshot(plan, setOf(a)), snapshot(plan, setOf(a))))
    }

    /** A week rolling over (or a different plan committed) with goals already complete. */
    @Test fun aDifferentWeekIsNotAMoment() {
        assertFalse(moment(snapshot(UUID.randomUUID(), emptySet()), snapshot(plan, setOf(a))))
    }

    @Test fun noPlanIsNotAMoment() {
        assertFalse(moment(snapshot(null, emptySet()), snapshot(null, setOf(a))))
    }

    /** The last goal completing is the week's moment, not a goal's. */
    @Test fun completingTheWholeWeekIsLeftToTheWeekCelebration() {
        assertFalse(moment(snapshot(plan, setOf(a, b)), snapshot(plan, setOf(a, b, c))))
    }

    /** A bonus goal done after the week already completed is still a goal's moment. */
    @Test fun aGoalAfterTheWeekIsCompleteIsAMoment() {
        val done = setOf(a, b, c)
        val bonus = UUID.randomUUID()
        assertTrue(moment(GoalCompletionSnapshot(plan, done, true), GoalCompletionSnapshot(plan, done + bonus, true)))
    }
}
