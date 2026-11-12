package com.knightindustries.knighttraining.core.store

import com.knightindustries.knighttraining.core.model.Activity
import com.knightindustries.knighttraining.core.model.GoalTemplate
import com.knightindustries.knighttraining.core.model.PlanTemplate
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Ports of iOS `EditActivityTests` and `EditPlanTests`. */
class EditTest : StoreTest() {
    private val puzzles get() = type("puzzles")
    private val playing get() = type("playing")

    /** A one-block plan committed through the real path, so `goalTemplateId` is stamped as in production. */
    private suspend fun committedPlan(
        targetType: TargetType = TargetType.COUNT,
        targetValue: Int = 10,
        name: String? = "Tactical Puzzles",
    ): Triple<PlanTemplate, WeeklyPlan, GoalTemplate> {
        val template = PlanTemplate(name = "Test Plan")
        store.savePlanTemplate(template)
        val goalTemplate = GoalTemplate(planTemplateId = template.id, name = name, activityType = puzzles, targetType = targetType, targetValue = targetValue)
        store.saveGoalTemplate(goalTemplate)
        return Triple(template, store.createWeeklyPlan(template.id)!!, goalTemplate)
    }

    /** A committed goal with one session logged through `logSession`, the production path. */
    private suspend fun loggedGoal(targetType: TargetType, targetValue: Int, duration: Int, count: Int?, delta: Int): Pair<WeeklyGoal, Activity> {
        val (_, plan, goalTemplate) = committedPlan(targetType, targetValue)
        val goal = store.weeklyGoals(plan.id).first { it.goalTemplateId == goalTemplate.id }
        val activity = Activity(activityType = puzzles, startedAt = now, endedAt = now, duration = duration, count = count)
        store.logSession(listOf(SessionSegmentLog(activity, SessionSegmentLog.GoalProgress(goal.id, delta))))
        return store.weeklyGoal(goal.id)!! to store.activity(activity.id)!!
    }

    private suspend fun countGoal(onPlanOf: WeeklyGoal, targetValue: Int, targetType: TargetType = TargetType.COUNT, code: String = "puzzles"): WeeklyGoal {
        val goal = WeeklyGoal(weeklyPlanId = onPlanOf.weeklyPlanId, activityType = type(code), targetType = targetType, targetValue = targetValue)
        store.saveWeeklyGoal(goal)
        return goal
    }

    private suspend fun goal(id: UUID) = store.weeklyGoal(id)!!

    // MARK: - updateActivity: same goal

    @Test
    fun correctsADurationGoalByTheExactDelta() = test {
        val (goal, activity) = loggedGoal(TargetType.DURATION, 3600, 1800, null, 1800)
        assertEquals(1800, goal.currentValue)
        assertNotNull("logSession must stamp weeklyGoalId", activity.weeklyGoalId)

        store.updateActivity(activity.id, ActivityEdit(duration = 2700))
        assertEquals(2700, goal(goal.id).currentValue)
    }

    @Test
    fun handlesADownwardCorrection() = test {
        val (goal, activity) = loggedGoal(TargetType.DURATION, 3600, 1800, null, 1800)
        store.updateActivity(activity.id, ActivityEdit(duration = 600))
        assertEquals("a negative delta, not just an add", 600, goal(goal.id).currentValue)
    }

    @Test
    fun correctsACountGoalByTheExactDelta() = test {
        val (goal, activity) = loggedGoal(TargetType.COUNT, 50, 600, 20, 20)
        store.updateActivity(activity.id, ActivityEdit(duration = 600, count = 32))
        assertEquals("+12, not +32", 32, goal(goal.id).currentValue)
    }

    @Test
    fun canCompleteAGoalThatWasNotBefore() = test {
        val (goal, activity) = loggedGoal(TargetType.COUNT, 50, 600, 40, 40)
        assertNull(goal.completedAt)
        store.updateActivity(activity.id, ActivityEdit(duration = 600, count = 55))
        assertNotNull(goal(goal.id).completedAt)
    }

    /** iOS 2026-10-16: an edit corrects a mistake, so "6/50, completed" would be wrong. */
    @Test
    fun aCorrectionBelowTargetUnCompletesTheGoal() = test {
        val (goal, activity) = loggedGoal(TargetType.COUNT, 50, 600, 60, 60)
        assertNotNull(goal.completedAt)
        store.updateActivity(activity.id, ActivityEdit(duration = 600, count = 6))
        assertEquals(6, goal(goal.id).currentValue)
        assertNull(goal(goal.id).completedAt)
    }

    @Test
    fun aCorrectionThatStaysAboveTargetKeepsTheOriginalCompletionTime() = test {
        val (goal, activity) = loggedGoal(TargetType.COUNT, 50, 600, 60, 60)
        val completedAt = goal.completedAt
        now = now.plusSeconds(60)
        store.updateActivity(activity.id, ActivityEdit(duration = 600, count = 55))
        assertEquals(completedAt, goal(goal.id).completedAt)
    }

    @Test
    fun withNoGoalOnlyTheActivityChanges() = test {
        val activity = Activity(activityType = puzzles, startedAt = now, endedAt = now, duration = 300, count = 10)
        store.saveActivity(activity)

        assertNull(store.updateActivity(activity.id, ActivityEdit(duration = 900, count = 25, notes = "corrected")))
        val updated = store.activity(activity.id)!!
        assertEquals(900, updated.duration)
        assertEquals(25, updated.count)
        assertEquals("corrected", updated.notes)
    }

    @Test
    fun progressGoalsNeverMove() = test {
        val (_, plan, _) = committedPlan(TargetType.PROGRESS, 100)
        val goal = store.weeklyGoals(plan.id).single()
        val activity = Activity(activityType = puzzles, startedAt = now, endedAt = now, duration = 1200, weeklyGoalId = goal.id)
        store.saveActivity(activity)

        store.updateActivity(activity.id, ActivityEdit(duration = 2400))
        assertEquals(0, goal(goal.id).currentValue)
    }

    @Test
    fun notesAloneLeaveTheGoalAlone() = test {
        val (goal, activity) = loggedGoal(TargetType.DURATION, 3600, 1800, null, 1800)
        store.updateActivity(activity.id, ActivityEdit(duration = 1800, notes = "Focused on endgames today"))
        assertEquals("Focused on endgames today", store.activity(activity.id)!!.notes)
        assertEquals(goal.currentValue, goal(goal.id).currentValue)
    }

    // MARK: - updateActivity: moving credit

    /** The reported case: games credited to the wrong goal move to the right one. */
    @Test
    fun movingASessionToAnotherGoalMovesItsCredit() = test {
        val (wrong, activity) = loggedGoal(TargetType.COUNT, 5, 9000, 2, 2)
        val right = countGoal(wrong, 3)

        store.updateActivity(activity.id, ActivityEdit(duration = 9000, count = 2, weeklyGoalId = Change.Set(right.id)))

        val updated = store.activity(activity.id)!!
        assertEquals(0, goal(wrong.id).currentValue)
        assertEquals(2, goal(right.id).currentValue)
        assertEquals(right.id, updated.weeklyGoalId)
        assertEquals(right.weeklyPlanId, updated.weeklyPlanId)
    }

    /** A session that was really a coaching lesson: the type moves, the credit follows in each goal's measure. */
    @Test
    fun changingTheActivityTypeMovesTheSessionAndItsCredit() = test {
        val (puzzleGoal, activity) = loggedGoal(TargetType.COUNT, 50, 3600, 20, 20)
        val coachingGoal = countGoal(puzzleGoal, 7200, TargetType.DURATION, "coaching")

        store.updateActivity(
            activity.id,
            ActivityEdit(duration = 3600, count = null, weeklyGoalId = Change.Set(coachingGoal.id), activityTypeId = Change.Set(type("coaching").id)),
        )

        val updated = store.activity(activity.id)!!
        assertEquals("coaching", updated.activityType.code)
        assertNull(updated.count)
        assertEquals(coachingGoal.id, updated.weeklyGoalId)
        assertEquals(0, goal(puzzleGoal.id).currentValue)
        assertEquals(3600, goal(coachingGoal.id).currentValue)
    }

    @Test
    fun leavingTheTypeUnchangedKeepsIt() = test {
        val (_, activity) = loggedGoal(TargetType.COUNT, 50, 600, 20, 20)
        store.updateActivity(activity.id, ActivityEdit(duration = 900, count = 20))
        assertEquals("puzzles", store.activity(activity.id)!!.activityType.code)
    }

    @Test
    fun movingASessionOffACompletedGoalUnCompletesIt() = test {
        val (goal, activity) = loggedGoal(TargetType.COUNT, 2, 600, 2, 2)
        assertNotNull(goal.completedAt)
        val other = countGoal(goal, 10)

        store.updateActivity(activity.id, ActivityEdit(duration = 600, count = 2, weeklyGoalId = Change.Set(other.id)))

        assertEquals(0, goal(goal.id).currentValue)
        assertNull(goal(goal.id).completedAt)
    }

    /** No goal also clears the week stamp, so the edit lock stops counting it. */
    @Test
    fun countingTowardNoGoalTakesTheCreditBackAndClearsTheWeek() = test {
        val (goal, activity) = loggedGoal(TargetType.COUNT, 50, 600, 20, 20)
        assertNotNull(activity.weeklyPlanId)

        store.updateActivity(activity.id, ActivityEdit(duration = 600, count = 20, weeklyGoalId = Change.Set(null)))

        val updated = store.activity(activity.id)!!
        assertEquals(0, goal(goal.id).currentValue)
        assertNull(updated.weeklyGoalId)
        assertNull(updated.weeklyPlanId)
    }

    @Test
    fun movingTheStartMovesTheEndAndSavesTheTimeControl() = test {
        val (_, activity) = loggedGoal(TargetType.COUNT, 50, 600, 20, 20)
        val newStart = activity.startedAt.minus(Duration.ofDays(1))

        store.updateActivity(
            activity.id,
            ActivityEdit(duration = 5400, count = 20, startedAt = Change.Set(newStart), timeControlLabel = Change.Set("60 min")),
        )

        val updated = store.activity(activity.id)!!
        assertEquals(newStart, updated.startedAt)
        assertEquals(newStart.plusSeconds(5400), updated.endedAt)
        assertEquals("60 min", updated.timeControlLabel)
    }

    @Test
    fun deletingASessionTakesBackItsCreditAndUnCompletesTheGoal() = test {
        val (goal, activity) = loggedGoal(TargetType.COUNT, 20, 600, 20, 20)
        assertNotNull(goal.completedAt)

        store.deleteLoggedActivity(activity.id)

        assertNull(store.activity(activity.id))
        assertEquals(0, goal(goal.id).currentValue)
        assertNull(goal(goal.id).completedAt)
    }

    @Test
    fun movingFromACountGoalToADurationGoalUsesEachGoalsOwnMeasure() = test {
        val (countGoal, activity) = loggedGoal(TargetType.COUNT, 5, 5400, 2, 2)
        val durationGoal = countGoal(countGoal, 7200, TargetType.DURATION)

        store.updateActivity(activity.id, ActivityEdit(duration = 5400, count = 2, weeklyGoalId = Change.Set(durationGoal.id)))

        assertEquals(0, goal(countGoal.id).currentValue)
        assertEquals(5400, goal(durationGoal.id).currentValue)
    }

    /** A move that lands the last missing credit completes the week, like a fresh session would. */
    @Test
    fun aMoveThatCompletesTheWeekReturnsItAndFiresTheEvent() = test {
        val (first, _) = loggedGoal(TargetType.COUNT, 2, 600, 2, 2)
        val second = countGoal(first, 2)
        val extra = Activity(activityType = puzzles, startedAt = now, endedAt = now, duration = 600, count = 2)
        store.logSession(listOf(SessionSegmentLog(extra, SessionSegmentLog.GoalProgress(first.id, 2))))

        var completedPlanId: UUID? = null
        val events = eventsDuring {
            completedPlanId = store.updateActivity(extra.id, ActivityEdit(duration = 600, count = 2, weeklyGoalId = Change.Set(second.id)))
        }

        assertEquals(first.weeklyPlanId, completedPlanId)
        assertEquals(listOf(store.weeklyPlan(first.weeklyPlanId)!!.weekStart), events.map { it.weekStart })
        assertEquals(2, goal(first.id).currentValue)
        assertNotNull(goal(first.id).completedAt)
        assertEquals(2, goal(second.id).currentValue)
        assertNotNull(goal(second.id).completedAt)
    }

    @Test
    fun uncreditingASessionLowersThePlansSessionCount() = test {
        val (_, plan, _) = committedPlan(targetValue = 50)
        val goal = store.weeklyGoals(plan.id).single()
        val activity = Activity(activityType = puzzles, startedAt = now, endedAt = now, duration = 600, count = 10, sessionId = UUID.randomUUID())
        store.logSession(listOf(SessionSegmentLog(activity, SessionSegmentLog.GoalProgress(goal.id, 10))))
        assertEquals(1, store.qualifyingSessionCount(plan.id))

        store.updateActivity(activity.id, ActivityEdit(duration = 600, count = 10, weeklyGoalId = Change.Set(null)))
        assertEquals(0, store.qualifyingSessionCount(plan.id))
    }

    // MARK: - qualifyingSessionCount

    private suspend fun logSession(goalId: UUID?, delta: Int, sessionId: UUID = UUID.randomUUID()) {
        val activity = Activity(activityType = puzzles, startedAt = now, endedAt = now, duration = 300, count = delta, sessionId = sessionId)
        store.logSession(listOf(SessionSegmentLog(activity, goalId?.let { SessionSegmentLog.GoalProgress(it, delta) })))
    }

    @Test
    fun aFreshlyCommittedPlanHasNoSessions() = test {
        assertEquals(0, store.qualifyingSessionCount(committedPlan().second.id))
    }

    @Test
    fun sessionCountCountsSittingsNotSegments() = test {
        val (_, plan, _) = committedPlan()
        val goal = store.weeklyGoals(plan.id).single()
        val sessionId = UUID.randomUUID()
        logSession(goal.id, 2, sessionId)
        logSession(goal.id, 3, sessionId)
        assertEquals(1, store.qualifyingSessionCount(plan.id))

        logSession(goal.id, 1)
        assertEquals(2, store.qualifyingSessionCount(plan.id))
    }

    @Test
    fun sessionCountIgnoresSessionsNotSeededWithAGoal() = test {
        val (_, plan, _) = committedPlan()
        logSession(null, 10)
        assertEquals(0, store.qualifyingSessionCount(plan.id))
    }

    @Test
    fun sessionCountIgnoresOtherWeeks() = test {
        val (_, planA, _) = committedPlan()
        logSession(store.weeklyGoals(planA.id).single().id, 1)
        val planB = commitWeek(planA.weekStart.minusWeeks(3), "puzzles" to 5)

        assertEquals(1, store.qualifyingSessionCount(planA.id))
        assertEquals(0, store.qualifyingSessionCount(planB.id))
    }

    @Test
    fun aSessionAgainstARemovedBlockStillCounts() = test {
        val (template, plan, _) = committedPlan()
        logSession(store.weeklyGoals(plan.id).single().id, 1)

        store.updatePlanBlocks(template.id, plan.id, emptyList())
        assertEquals(1, store.qualifyingSessionCount(plan.id))
    }

    // MARK: - updatePlanBlocks

    @Test
    fun keepingABlockUpdatesItWithoutResettingProgress() = test {
        val (template, plan, goalTemplate) = committedPlan(targetValue = 10)
        val original = store.weeklyGoals(plan.id).single()
        store.updateWeeklyGoalProgress(original, 4)

        store.updatePlanBlocks(
            template.id, plan.id,
            listOf(GoalEdit(goalTemplate.id, "Renamed Puzzles", puzzles, targetType = TargetType.COUNT, targetValue = 20)),
        )

        val updated = goal(original.id)
        assertEquals("editing must not reset tracked progress", 4, updated.currentValue)
        assertEquals(20, updated.targetValue)
        assertEquals("Renamed Puzzles", updated.name)
        assertNull(updated.completedAt)
        assertEquals("the template stays in step", 20, store.goalTemplate(goalTemplate.id)!!.targetValue)
    }

    @Test
    fun retargetingBelowCurrentValueMarksItComplete() = test {
        val (template, plan, goalTemplate) = committedPlan(targetValue = 10)
        val original = store.weeklyGoals(plan.id).single()
        store.updateWeeklyGoalProgress(original, 4)

        store.updatePlanBlocks(template.id, plan.id, listOf(GoalEdit(goalTemplate.id, activityType = puzzles, targetType = TargetType.COUNT, targetValue = 3)))
        assertNotNull(goal(original.id).completedAt)
    }

    @Test
    fun keepingACompletedGoalKeepsItsCompletionTime() = test {
        val (template, plan, goalTemplate) = committedPlan(targetValue = 10)
        val original = store.weeklyGoals(plan.id).single()
        store.updateWeeklyGoalProgress(original, 10)
        val completedAt = goal(original.id).completedAt!!
        now = now.plusSeconds(60)

        store.updatePlanBlocks(
            template.id, plan.id,
            listOf(GoalEdit(goalTemplate.id, "Still Puzzles", puzzles, targetType = TargetType.COUNT, targetValue = 10)),
        )
        assertEquals(completedAt, goal(original.id).completedAt)
    }

    @Test
    fun addingABlockCreatesAFreshGoalAtZero() = test {
        val (template, plan, goalTemplate) = committedPlan()

        store.updatePlanBlocks(
            template.id, plan.id,
            listOf(
                GoalEdit(goalTemplate.id, activityType = puzzles, targetType = TargetType.COUNT, targetValue = 10),
                GoalEdit(name = "Blitz Games", activityType = playing, targetType = TargetType.COUNT, targetValue = 5),
            ),
        )

        val goals = store.weeklyGoals(plan.id)
        assertEquals(2, goals.size)
        val added = goals.first { it.name == "Blitz Games" }
        assertEquals(0, added.currentValue)
        assertNull(added.completedAt)
        assertEquals(2, store.goalTemplates(template.id).size)
    }

    @Test
    fun removingABlockKeepsLoggedActivity() = test {
        val (template, plan, _) = committedPlan()
        logSession(store.weeklyGoals(plan.id).single().id, 7)

        store.updatePlanBlocks(template.id, plan.id, emptyList())

        assertTrue(store.weeklyGoals(plan.id).isEmpty())
        assertTrue(store.goalTemplates(template.id).isEmpty())
        val logged = store.activities(Instant.EPOCH, now.plus(Duration.ofDays(365)))
        assertEquals("the activity logged toward the removed block survives", 7, logged.sumOf { it.count ?: 0 })
    }

    @Test
    fun withNoWeekOnlyTheTemplateChanges() = test {
        val template = PlanTemplate(name = "Future Only")
        store.savePlanTemplate(template)
        val goalTemplate = GoalTemplate(planTemplateId = template.id, activityType = puzzles, targetType = TargetType.COUNT, targetValue = 10)
        store.saveGoalTemplate(goalTemplate)

        store.updatePlanBlocks(template.id, null, listOf(GoalEdit(goalTemplate.id, activityType = puzzles, targetType = TargetType.COUNT, targetValue = 25)))
        assertEquals(25, store.goalTemplate(goalTemplate.id)!!.targetValue)
    }
}
