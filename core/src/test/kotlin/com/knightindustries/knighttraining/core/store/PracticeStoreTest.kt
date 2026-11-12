package com.knightindustries.knighttraining.core.store

import com.knightindustries.knighttraining.core.model.CoachingTopic
import com.knightindustries.knighttraining.core.model.PlanTemplate
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import com.knightindustries.knighttraining.core.session.PracticeSession
import com.knightindustries.knighttraining.core.session.PracticeSession.Attribution
import com.knightindustries.knighttraining.core.session.PracticeSession.GoalCredit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** `createPlan` and saving a practice session, the two writes Phase 2's screens make. */
class PracticeStoreTest : StoreTest() {
    private suspend fun create(name: String? = "Week", vararg blocks: NewBlock, commit: Boolean = true) =
        store.createPlan(name, blocks.toList(), "Untitled training plan", { "Untitled training plan ($it)" }, commit)

    private fun block(code: String, targetType: TargetType, target: Int, timeControl: String? = null) =
        NewBlock(name = null, activityType = type(code), targetType = targetType, targetValue = target, timeControlLabel = timeControl)

    private suspend fun committedPlan(vararg blocks: NewBlock): WeeklyPlan {
        create("Week", *blocks)
        return store.weeklyPlan(calendar.weekStart(now))!!
    }

    private fun at(seconds: Long): Instant = now.plusSeconds(seconds)

    // MARK: - createPlan

    @Test
    fun createPlanSavesActivatesAndCommits() = test {
        val template = create("Endgame week", block("endgames", TargetType.DURATION, 3600))

        assertEquals("Endgame week", template.name)
        assertEquals(template.id, store.activePlanTemplate()?.id)
        val week = store.weeklyPlan(calendar.weekStart(now))!!
        assertEquals(template.id, week.planTemplateId)
        assertEquals(1, week.weeklyGoals.size)
    }

    @Test
    fun aBlankNameGetsTheFirstFreeUntitledName() = test {
        assertEquals("Untitled training plan", create("  ", block("puzzles", TargetType.COUNT, 50), commit = false).name)
        assertEquals("Untitled training plan (1)", create(null, block("puzzles", TargetType.COUNT, 50), commit = false).name)
    }

    @Test
    fun onlyOnePlanIsActive() = test {
        create("A", block("puzzles", TargetType.COUNT, 50), commit = false)
        val second = create("B", block("puzzles", TargetType.COUNT, 50), commit = false)
        assertEquals(second.id, store.activePlanTemplate()?.id)
        assertEquals(1, store.allPlanTemplates().count { it.isActive })
    }

    @Test
    fun aTimeControlIsKeptForGamesOnly() = test {
        val template = create("Mixed", block("playing", TargetType.COUNT, 2, "15+10"), block("puzzles", TargetType.COUNT, 30, "15+10"), commit = false)
        assertEquals(listOf("15+10", null), template.goalTemplates.map { it.timeControlLabel })
    }

    @Test
    fun withoutCommitNoWeekIsCreated() = test {
        create("Later", block("puzzles", TargetType.COUNT, 50), commit = false)
        assertNull(store.weeklyPlan(calendar.weekStart(now)))
    }

    // MARK: - logPracticeSession

    @Test
    fun aPlanGoalSessionCreditsItsGoalByCount() = test {
        val goal = committedPlan(block("puzzles", TargetType.COUNT, 50)).weeklyGoals.single()
        val session = PracticeSession.start(now, PracticeSession.attributionFor(goal)).stop(at(900))

        store.logPracticeSession(session, finalActivityTypeCode = null, count = 18)

        assertEquals(18, store.weeklyGoal(goal.id)!!.currentValue)
        val logged = store.activities(now, at(901)).single()
        assertEquals(900, logged.duration)
        assertEquals(session.sessionId, logged.sessionId)
        assertEquals(goal.id, logged.weeklyGoalId)
    }

    @Test
    fun eachSegmentIsItsOwnActivityCreditingItsOwnGoal() = test {
        val plan = committedPlan(block("puzzles", TargetType.COUNT, 50), block("openings", TargetType.DURATION, 3600))
        val (puzzles, openings) = plan.weeklyGoals
        val session = PracticeSession.start(now, PracticeSession.attributionFor(puzzles))
            .change(at(600), PracticeSession.attributionFor(openings), null, outgoingCount = 12)
            .stop(at(1500))

        store.logPracticeSession(session, null)

        assertEquals(12, store.weeklyGoal(puzzles.id)!!.currentValue)
        assertEquals(900, store.weeklyGoal(openings.id)!!.currentValue)
        val activities = store.activities(now, at(1501))
        assertEquals(2, activities.size)
        assertEquals(setOf(session.sessionId), activities.map { it.sessionId }.toSet())
    }

    /** One candidate fills itself in; two never get a guess. */
    @Test
    fun aQuickStartSessionCreditsOnlyAnUnambiguousGoal() = test {
        val goal = committedPlan(block("endgames", TargetType.DURATION, 3600)).weeklyGoals.single()
        val session = PracticeSession.start(now, Attribution.General).stop(at(1200)).categorize("endgames")

        store.logPracticeSession(session, "endgames")
        assertEquals(1200, store.weeklyGoal(goal.id)!!.currentValue)
    }

    @Test
    fun twoCandidatesCreditNothingUnlessOneIsPicked() = test {
        val plan = committedPlan(block("endgames", TargetType.DURATION, 3600), block("endgames", TargetType.DURATION, 1800))
        val (first, second) = plan.weeklyGoals

        store.logPracticeSession(PracticeSession.start(now, Attribution.General).stop(at(600)), "endgames")
        assertEquals(0, store.weeklyGoal(first.id)!!.currentValue)
        assertEquals(0, store.weeklyGoal(second.id)!!.currentValue)

        now = at(3600)
        val picked = PracticeSession.start(now, Attribution.General).stop(now.plusSeconds(600)).withGoalCredit(GoalCredit.Picked(second.id))
        store.logPracticeSession(picked, "endgames")
        assertEquals(600, store.weeklyGoal(second.id)!!.currentValue)
    }

    @Test
    fun notCountedCreditsNothing() = test {
        val goal = committedPlan(block("endgames", TargetType.DURATION, 3600)).weeklyGoals.single()
        val session = PracticeSession.start(now, Attribution.General).stop(at(600)).withGoalCredit(GoalCredit.NotCounted)
        store.logPracticeSession(session, "endgames")
        assertEquals(0, store.weeklyGoal(goal.id)!!.currentValue)
    }

    @Test
    fun skippingTheCategoryLogsGeneralAndCreditsNothing() = test {
        committedPlan(block("endgames", TargetType.DURATION, 3600))
        store.logPracticeSession(PracticeSession.start(now, Attribution.General).stop(at(600)), null)
        assertEquals("general", store.activities(now, at(601)).single().activityType.code)
    }

    @Test
    fun anAdHocSegmentCreditsTheGoalPickedWhenLeavingIt() = test {
        val goal = committedPlan(block("endgames", TargetType.DURATION, 3600)).weeklyGoals.single()
        val session = PracticeSession.start(now, PracticeSession.adHoc("endgames", null))
            .change(at(900), Attribution.General, null, outgoingCreditedGoalId = goal.id)
            .stop(at(1000))

        store.logPracticeSession(session, null)
        assertEquals(900, store.weeklyGoal(goal.id)!!.currentValue)
    }

    @Test
    fun aDurationCorrectionIsWhatGetsSaved() = test {
        val goal = committedPlan(block("openings", TargetType.DURATION, 7200)).weeklyGoals.single()
        // Left running overnight, corrected to 45 minutes at the review.
        val session = PracticeSession.start(now, PracticeSession.attributionFor(goal)).stop(at(36_000)).overrideFinalDuration(45)

        store.logPracticeSession(session, null)
        assertEquals(45 * 60, store.weeklyGoal(goal.id)!!.currentValue)
    }

    @Test
    fun timeControlsAndTopicsLandOnlyWhereTheyBelong() = test {
        store.logPracticeSession(
            PracticeSession.start(now, Attribution.General).stop(at(600)), "puzzles",
            count = 10, timeControlLabel = "15+10", coachingTopics = listOf(CoachingTopic.TACTICS),
        )
        val logged = store.activities(now, at(601)).single()
        assertNull(logged.timeControlLabel)
        assertTrue(logged.coachingTopics.isEmpty())
    }

    /** iOS 2026-10-25: a session started before midnight credits the week it started in. */
    @Test
    fun aSessionSavedAfterTheRolloverCreditsTheWeekItStarted() = test {
        now = at(2026, 11, 14, 23, 30) // Saturday, last evening of the week
        val goal = committedPlan(block("endgames", TargetType.DURATION, 3600)).weeklyGoals.single()
        val session = PracticeSession.start(now, Attribution.General).stop(at(2026, 11, 15, 0, 20)).categorize("endgames")
        now = at(2026, 11, 15, 0, 25)

        store.logPracticeSession(session, "endgames")
        assertEquals(50 * 60, store.weeklyGoal(goal.id)!!.currentValue)
    }

    @Test
    fun createPlanRejectsAPlanWithNoBlocks() = test {
        val failure = runCatching { store.createPlan("Empty", emptyList(), "Untitled", { "Untitled ($it)" }) }
        assertTrue(failure.isFailure)
        assertTrue(store.allPlanTemplates().isEmpty())
        assertNull(PlanTemplate().name)
    }
}
