package com.knightindustries.knighttraining.core.model

import com.knightindustries.knighttraining.core.model.Fixtures.book
import com.knightindustries.knighttraining.core.model.Fixtures.endgames
import com.knightindustries.knighttraining.core.model.Fixtures.general
import com.knightindustries.knighttraining.core.model.Fixtures.openings
import com.knightindustries.knighttraining.core.model.Fixtures.playing
import com.knightindustries.knighttraining.core.model.Fixtures.puzzles
import com.knightindustries.knighttraining.core.model.TimeControl.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

/** Ports of iOS `WeeklyGoalCreditTests` and the `displayMinutes` half of `WeeklyGoalDisplayTests`. */
class WeeklyGoalTest {
    private val planId = UUID.randomUUID()

    private fun goal(
        type: ActivityType,
        targetType: TargetType = TargetType.DURATION,
        resource: Resource? = null,
        timeControl: String? = null,
        completed: Boolean = false,
        isBonus: Boolean = false,
        targetValue: Int = 60,
        currentValue: Int = 0,
    ) = WeeklyGoal(
        weeklyPlanId = planId, activityType = type, resource = resource, targetType = targetType,
        targetValue = targetValue, currentValue = currentValue, timeControlLabel = timeControl,
        completedAt = if (completed) Instant.now() else null, isBonus = isBonus,
    )

    private fun candidates(code: String, resource: Resource? = null, category: Category? = null, goals: List<WeeklyGoal>) =
        WeeklyGoal.creditCandidates(code, resource?.id, category, goals).map { it.id }

    // MARK: - creditCandidates

    @Test
    fun onlyTheSameActivityTypeMatches() {
        val openingsGoal = goal(openings)
        assertEquals(listOf(openingsGoal.id), candidates("openings", goals = listOf(goal(endgames), openingsGoal)))
        assertEquals(emptyList<UUID>(), candidates(general.code, goals = listOf(openingsGoal)))
    }

    @Test
    fun progressGoalsNeverMatch() {
        val count = goal(puzzles, TargetType.COUNT)
        assertEquals(listOf(count.id), candidates("puzzles", goals = listOf(goal(puzzles, TargetType.PROGRESS), count)))
    }

    @Test
    fun completedAndBonusGoalsStillMatch() {
        val done = goal(openings, completed = true)
        val bonus = goal(endgames, isBonus = true)
        assertEquals(listOf(done.id), candidates("openings", goals = listOf(done)))
        assertEquals(listOf(bonus.id), candidates("endgames", goals = listOf(bonus)))
    }

    @Test
    fun aGoalOnAnotherResourceIsRuledOut() {
        val onA = goal(openings, resource = Resource(kind = book, title = "A"))
        assertEquals(emptyList<UUID>(), candidates("openings", Resource(kind = book, title = "B"), goals = listOf(onA)))
    }

    @Test
    fun theSameResourceWinsOverAnOpenGoal() {
        val bookA = Resource(kind = book, title = "A")
        val onA = goal(openings, resource = bookA)
        assertEquals(listOf(onA.id), candidates("openings", bookA, goals = listOf(goal(openings), onA)))
    }

    @Test
    fun aResourceWithNoDedicatedGoalFallsBackToOpenGoals() {
        val open = goal(openings)
        val onOther = goal(openings, resource = Resource(kind = book, title = "Other"))
        assertEquals(listOf(open.id), candidates("openings", Resource(kind = book, title = "New"), goals = listOf(open, onOther)))
    }

    /** No resource on the session and a goal per book: both stay candidates, so the user picks. */
    @Test
    fun noResourceKeepsEveryResourceGoalAsACandidate() {
        val onA = goal(openings, resource = Resource(kind = book, title = "A"))
        val onB = goal(openings, resource = Resource(kind = book, title = "B"))
        assertEquals(listOf(onA.id, onB.id), candidates("openings", goals = listOf(onA, onB)))
    }

    @Test
    fun playingPrefersTheSameTimeControlCategory() {
        val rapid = goal(playing, TargetType.COUNT, timeControl = "15+10")
        val blitz = goal(playing, TargetType.COUNT, timeControl = "3+2")
        val open = goal(playing, TargetType.COUNT)
        val all = listOf(blitz, open, rapid)
        assertEquals(listOf(rapid.id), candidates("playing", category = Category.RAPID, goals = all))
        assertEquals(listOf(open.id), candidates("playing", category = Category.CLASSICAL, goals = all))
        assertEquals(listOf(blitz.id, open.id, rapid.id), candidates("playing", goals = all))
    }

    @Test
    fun timeControlIsIgnoredOutsidePlaying() {
        val puzzlesGoal = goal(puzzles, TargetType.COUNT)
        assertEquals(listOf(puzzlesGoal.id), candidates("puzzles", category = Category.BLITZ, goals = listOf(puzzlesGoal)))
    }

    @Test
    fun creditDeltaFollowsTheGoalsDimension() {
        assertEquals(600, goal(openings).creditDelta(600, 3))
        assertEquals(12, goal(puzzles, TargetType.COUNT).creditDelta(600, 12))
        assertNull(goal(puzzles, TargetType.COUNT).creditDelta(600, null))
        assertNull(goal(openings, TargetType.PROGRESS).creditDelta(600, 3))
    }

    // MARK: - displayMinutes

    @Test
    fun nonZeroSecondsNeverDisplayAsZeroMinutes() {
        for (seconds in listOf(1, 15, 29, 30, 45, 59)) {
            assertEquals("${seconds}s must not render as 0 minutes", 1, WeeklyGoal.displayMinutes(seconds))
        }
    }

    @Test
    fun zeroSecondsDisplaysAsZero() {
        assertEquals(0, WeeklyGoal.displayMinutes(0))
    }

    @Test
    fun roundsToNearestMinuteAboveOne() {
        assertEquals(1, WeeklyGoal.displayMinutes(89))
        assertEquals(2, WeeklyGoal.displayMinutes(91))
        assertEquals(10, WeeklyGoal.displayMinutes(600))
        // Halves round up, as Swift's `.rounded()` does, not to even.
        assertEquals(3, WeeklyGoal.displayMinutes(150))
    }

    /** iOS beta report 2026-11-09: 119m30s+ rounded to "120/120" beside "In Progress". */
    @Test
    fun valueShortOfItsTargetNeverDisplaysAsTheTarget() {
        for (seconds in listOf(7170, 7199)) {
            assertEquals(119, WeeklyGoal.displayMinutes(seconds, 7200))
        }
        assertEquals(120, WeeklyGoal.displayMinutes(7200, 7200))
        assertEquals(121, WeeklyGoal.displayMinutes(7230, 7200))
    }

    @Test
    fun progressPercentageShowsASliverForAShortSession() {
        val short = goal(endgames, targetValue = 1800, currentValue = 50)
        assertEquals(1, WeeklyGoal.displayMinutes(short.currentValue, short.targetValue))
        assertTrue(short.progressPercentage > 0)
    }
}
