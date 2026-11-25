package com.knightindustries.knighttraining.core.coach

import com.knightindustries.knighttraining.core.model.GoalTemplate
import com.knightindustries.knighttraining.core.model.PlanSuggestionHistory
import com.knightindustries.knighttraining.core.model.PlanTemplate
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import com.knightindustries.knighttraining.core.model.WeeklyReview
import com.knightindustries.knighttraining.core.model.WeeklyReview.CoachTip
import com.knightindustries.knighttraining.core.store.StoreTest
import com.knightindustries.knighttraining.core.store.createWeeklyPlan
import com.knightindustries.knighttraining.core.store.createWeeklyPlanFromTemplate
import com.knightindustries.knighttraining.core.store.goalTemplate
import com.knightindustries.knighttraining.core.store.planTemplate
import com.knightindustries.knighttraining.core.store.saveCoachTip
import com.knightindustries.knighttraining.core.store.saveGoalTemplate
import com.knightindustries.knighttraining.core.store.savePlanTemplate
import com.knightindustries.knighttraining.core.store.saveWeeklyGoal
import com.knightindustries.knighttraining.core.store.saveWeeklyPlan
import com.knightindustries.knighttraining.core.store.saveWeeklyReview
import com.knightindustries.knighttraining.core.store.setCoachTipStatus
import com.knightindustries.knighttraining.core.store.test
import com.knightindustries.knighttraining.core.store.weeklyReview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

/**
 * The weekly coach's tip is stored on the reviewed week's review and shown while the following
 * week is planned; an accepted target is applied to whichever plan gets committed (iOS
 * `CoachTipTests`).
 */
class CoachTipTest : StoreTest() {
    private val lastWeekStart: LocalDate get() = calendar.weekStart(now).minusWeeks(1)

    private suspend fun makeReviewedWeek(): WeeklyPlan {
        val template = PlanTemplate(name = "Plan")
        store.savePlanTemplate(template)
        val plan = WeeklyPlan(planTemplateId = template.id, weekStart = lastWeekStart)
        store.saveWeeklyPlan(plan)
        store.saveWeeklyReview(WeeklyReview(weeklyPlanId = plan.id, heldBack = "Work"))
        return plan
    }

    private val readyTip = CoachTip(
        status = CoachTip.Status.READY, text = "Do the fork puzzles first.", focus = "Forks",
        targetCode = "puzzles", targetValue = 40, suggestsCoach = true,
    )

    // MARK: - Storing the tip

    @Test
    fun aTipIsStoredOnTheReviewAndReadBack() = test {
        val plan = makeReviewedWeek()

        assertTrue(store.saveCoachTip(readyTip, plan.id))

        val review = store.weeklyReview(plan.id)
        assertEquals(readyTip, review?.coachTip)
        assertEquals(PlanSuggestionHistory.CoachTarget("puzzles", 40), review?.coachTip?.target)
        assertEquals("Work", review?.heldBack)
    }

    @Test
    fun aReviewTheCoachHasntAnsweredHasNoTip() = test {
        val plan = makeReviewedWeek()
        assertNull("null is what tells the app to ask the coach", store.weeklyReview(plan.id)?.coachTip)
    }

    @Test
    fun nothingToShowIsStoredSoTheCoachIsntAskedAgain() = test {
        val plan = makeReviewedWeek()

        store.saveCoachTip(CoachTip.Unavailable, plan.id)

        val review = store.weeklyReview(plan.id)
        assertEquals(CoachTip.Status.UNAVAILABLE, review?.coachTip?.status)
        assertNull(review?.coachTip?.text)
    }

    @Test
    fun theFirstAnswerWins() = test {
        val plan = makeReviewedWeek()
        store.saveCoachTip(readyTip, plan.id)
        store.setCoachTipStatus(CoachTip.Status.ACCEPTED, plan.id)

        assertFalse(store.saveCoachTip(CoachTip.Unavailable, plan.id))

        val review = store.weeklyReview(plan.id)
        assertEquals(CoachTip.Status.ACCEPTED, review?.coachTip?.status)
        assertEquals(readyTip.text, review?.coachTip?.text)
    }

    @Test
    fun aWeekWithoutAReviewStoresNothing() = test {
        assertFalse(store.saveCoachTip(readyTip, UUID.randomUUID()))
    }

    @Test
    fun resavingTheReviewKeepsTheTip() = test {
        val plan = makeReviewedWeek()
        store.saveCoachTip(readyTip, plan.id)
        val saved = store.weeklyReview(plan.id)!!

        store.saveWeeklyReview(saved.updating(wentWell = "Edited", heldBack = null, wishIDone = null, nextWeekIntent = null))

        val review = store.weeklyReview(plan.id)
        assertEquals(readyTip, review?.coachTip)
        assertEquals("Edited", review?.wentWell)
    }

    @Test
    fun onlyATipWaitingForAnAnswerChangesStatus() = test {
        val plan = makeReviewedWeek()
        store.saveCoachTip(readyTip, plan.id)
        store.setCoachTipStatus(CoachTip.Status.DISMISSED, plan.id)

        store.setCoachTipStatus(CoachTip.Status.ACCEPTED, plan.id)

        assertEquals(CoachTip.Status.DISMISSED, store.weeklyReview(plan.id)?.coachTip?.status)
    }

    // MARK: - Applying an accepted target at commit

    private suspend fun makeTemplate(vararg goals: Triple<String, TargetType, Int>): PlanTemplate {
        val template = PlanTemplate(name = "Next")
        store.savePlanTemplate(template)
        for ((code, targetType, value) in goals) {
            store.saveGoalTemplate(GoalTemplate(planTemplateId = template.id, activityType = type(code), targetType = targetType, targetValue = value))
        }
        return store.planTemplate(template.id)!!
    }

    /** Last week had a 30-puzzle goal, and the coach's "40 puzzles" was answered with [status]. */
    private suspend fun reviewLastWeek(status: CoachTip.Status = CoachTip.Status.ACCEPTED) {
        val plan = makeReviewedWeek()
        store.saveWeeklyGoal(WeeklyGoal(weeklyPlanId = plan.id, activityType = type("puzzles"), targetType = TargetType.COUNT, targetValue = 30))
        store.saveCoachTip(CoachTip(CoachTip.Status.READY, text = "More puzzles.", targetCode = "puzzles", targetValue = 40), plan.id)
        if (status != CoachTip.Status.READY) store.setCoachTipStatus(status, plan.id)
    }

    @Test
    fun committingAnyPlanAppliesTheAcceptedTargetToItsMatchingGoalAndTemplate() = test {
        reviewLastWeek()
        val template = makeTemplate(Triple("openings", TargetType.DURATION, 3600), Triple("puzzles", TargetType.COUNT, 25))
        val puzzleTemplate = template.goalTemplates.first { it.activityType.code == "puzzles" }

        val week = store.createWeeklyPlan(template.id)!!

        assertEquals(40, week.weeklyGoals.first { it.activityType.code == "puzzles" }.targetValue)
        assertEquals(3600, week.weeklyGoals.first { it.activityType.code == "openings" }.targetValue)
        assertEquals("later weeks keep the coach's number", 40, store.goalTemplate(puzzleTemplate.id)?.targetValue)
    }

    @Test
    fun theActivePlanPathAppliesItToo() = test {
        reviewLastWeek()
        val template = PlanTemplate(name = "Brand new", isActive = true)
        store.savePlanTemplate(template)
        store.saveGoalTemplate(GoalTemplate(planTemplateId = template.id, activityType = type("puzzles"), targetType = TargetType.COUNT, targetValue = 20))

        val week = store.createWeeklyPlanFromTemplate()

        assertEquals("a plan built by hand this week gets it as well", 40, week?.weeklyGoals?.first()?.targetValue)
    }

    @Test
    fun aTargetNotAcceptedChangesNothing() {
        for (status in listOf(CoachTip.Status.READY, CoachTip.Status.DISMISSED)) {
            test {
                reviewLastWeek(status)
                val template = makeTemplate(Triple("puzzles", TargetType.COUNT, 30))

                val week = store.createWeeklyPlan(template.id)

                assertEquals("$status", 30, week?.weeklyGoals?.first()?.targetValue)
            }
            closeDatabase()
            openDatabase()
        }
    }

    @Test
    fun aTargetInOtherUnitsLeavesThePlanAlone() = test {
        reviewLastWeek()
        val template = makeTemplate(Triple("puzzles", TargetType.DURATION, 1800))

        val week = store.createWeeklyPlan(template.id)

        assertEquals("40 puzzles isn't 40 seconds of puzzles", 1800, week?.weeklyGoals?.first()?.targetValue)
    }

    @Test
    fun aPlanWithoutThatActivityIsCommittedUnchanged() = test {
        reviewLastWeek()
        val template = makeTemplate(Triple("openings", TargetType.DURATION, 3600))

        val week = store.createWeeklyPlan(template.id)

        assertEquals(listOf(3600), week?.weeklyGoals?.map { it.targetValue })
    }

    @Test
    fun anAlreadyCommittedWeekIsntRetargeted() = test {
        val template = makeTemplate(Triple("puzzles", TargetType.COUNT, 30))
        store.createWeeklyPlan(template.id)
        reviewLastWeek()

        val week = store.createWeeklyPlan(template.id)

        assertEquals(30, week?.weeklyGoals?.first()?.targetValue)
        assertEquals(30, store.goalTemplate(template.goalTemplates.first().id)?.targetValue)
    }

    @Test
    fun theTargetCarriesTheReviewedGoalsUnits() = test {
        val plan = WeeklyPlan(
            planTemplateId = UUID.randomUUID(), weekStart = lastWeekStart,
            weeklyGoals = listOf(WeeklyGoal(weeklyPlanId = UUID.randomUUID(), activityType = type("puzzles"), targetType = TargetType.DURATION, targetValue = 1800)),
        )

        assertEquals(
            CoachTargetChange.TemplateTarget("puzzles", TargetType.DURATION, 2400),
            CoachTargetChange.templateTarget(PlanSuggestionHistory.CoachTarget("puzzles", 2400), plan),
        )
        assertNull(CoachTargetChange.templateTarget(PlanSuggestionHistory.CoachTarget("openings", 2400), plan))
    }
}
