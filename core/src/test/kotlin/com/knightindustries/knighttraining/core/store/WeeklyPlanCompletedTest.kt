package com.knightindustries.knighttraining.core.store

import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ports of iOS `WeeklyPlanCompletedTests` and `BonusGoalDatabaseTests`: the completion event fires
 * exactly once, on the transition, and bonus goals never gate it.
 */
class WeeklyPlanCompletedTest : StoreTest() {
    private lateinit var plan: WeeklyPlan
    private val goalA: WeeklyGoal get() = plan.weeklyGoals[0]
    private val goalB: WeeklyGoal get() = plan.weeklyGoals[1]

    /** Two goals this week, so the streak resolves to 1 once both are done. */
    private suspend fun twoGoalPlan() {
        plan = commitWeek(goals = arrayOf("puzzles" to 10, "playing" to 5))
    }

    private suspend fun addBonus() =
        store.addBonusGoal(plan.id, type("puzzles"), TargetType.COUNT, targetValue = 20)

    @Test
    fun emitsExactlyOnceWhenTheLastGoalCompletes() = test {
        twoGoalPlan()
        assertTrue("1 of 2 goals must not emit", eventsDuring { store.updateWeeklyGoalProgress(goalA, 10) }.isEmpty())

        val events = eventsDuring { store.updateWeeklyGoalProgress(goalB, 5) }
        assertEquals(listOf(WeeklyPlanCompleted(plan.weekStart, streakLength = 1, goalCount = 2)), events)
    }

    @Test
    fun doesNotReemitOnFurtherLogsInAnAlreadyCompleteWeek() = test {
        twoGoalPlan()
        assertEquals(1, eventsDuring { store.updateWeeklyGoalProgress(goalA, 10); store.updateWeeklyGoalProgress(goalB, 5) }.size)
        assertTrue(eventsDuring { store.updateWeeklyGoalProgress(goalA, 12) }.isEmpty())
    }

    /** The iOS regression: one observer cancelling killed the stream for every later one. */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aCancelledObserverDoesNotKillTheStreamForOthers() = test {
        twoGoalPlan()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { store.weeklyPlanCompletedEvents.collect { } }.cancel()

        assertEquals(1, eventsDuring { store.updateWeeklyGoalProgress(goalA, 10); store.updateWeeklyGoalProgress(goalB, 5) }.size)
    }

    @Test
    fun logSessionEmitsWhenItCompletesTheWeek() = test {
        twoGoalPlan()
        store.updateWeeklyGoalProgress(goalA, 10)
        val events = eventsDuring {
            store.logSession(
                listOf(SessionSegmentLog(com.knightindustries.knighttraining.core.model.Activity(activityType = goalB.activityType, startedAt = now, endedAt = now, duration = 600, count = 5), SessionSegmentLog.GoalProgress(goalB.id, 5))),
            )
        }
        assertEquals(1, events.size)
    }

    // MARK: - Bonus goals

    @Test
    fun addBonusGoalInsertsABonusWithNoTemplateLink() = test {
        twoGoalPlan()
        val bonus = addBonus()
        assertTrue(bonus.isBonus)
        assertNull("a bonus goal is never written back to the plan", bonus.goalTemplateId)
        assertEquals(0, bonus.currentValue)
        assertEquals(3, store.weeklyGoals(plan.id).size)
    }

    @Test
    fun increaseGoalTargetRaisesTheTarget() = test {
        twoGoalPlan()
        store.increaseGoalTarget(goalA.id, 25)
        assertEquals(25, store.weeklyGoal(goalA.id)!!.targetValue)
    }

    /** Adding more is an extra, never a penalty: a completed goal stays completed. */
    @Test
    fun increaseGoalTargetKeepsCompletedAt() = test {
        twoGoalPlan()
        store.updateWeeklyGoalProgress(goalA, 10)
        val completedAt = store.weeklyGoal(goalA.id)!!.completedAt!!

        store.increaseGoalTarget(goalA.id, 30)

        val after = store.weeklyGoal(goalA.id)!!
        assertEquals(30, after.targetValue)
        assertEquals(completedAt, after.completedAt)
    }

    @Test
    fun increaseGoalTargetIgnoresALowerTarget() = test {
        twoGoalPlan()
        store.increaseGoalTarget(goalA.id, 5)
        assertEquals(10, store.weeklyGoal(goalA.id)!!.targetValue)
    }

    @Test
    fun anUnfinishedBonusGoalDoesNotBlockPlanCompletion() = test {
        twoGoalPlan()
        addBonus()
        val events = eventsDuring { store.updateWeeklyGoalProgress(goalA, 10); store.updateWeeklyGoalProgress(goalB, 5) }

        assertEquals(1, events.size)
        assertEquals("only real goals count", 2, events.single().goalCount)
        val reloaded = store.weeklyPlan(plan.id)!!
        assertTrue(reloaded.isFullyCompleted)
        assertEquals(100.0, reloaded.completionPercentage, 0.0)
    }

    @Test
    fun finishingTheBonusGoalAfterwardDoesNotReemit() = test {
        twoGoalPlan()
        val bonus = addBonus()
        store.updateWeeklyGoalProgress(goalA, 10)
        store.updateWeeklyGoalProgress(goalB, 5)
        assertTrue(eventsDuring { store.updateWeeklyGoalProgress(bonus, 20) }.isEmpty())
    }

    @Test
    fun anUnfinishedBonusGoalDoesNotBreakTheStreak() = test {
        twoGoalPlan()
        addBonus()
        store.updateWeeklyGoalProgress(goalA, 10)
        store.updateWeeklyGoalProgress(goalB, 5)
        assertEquals(1, store.currentStreak())
    }
}
