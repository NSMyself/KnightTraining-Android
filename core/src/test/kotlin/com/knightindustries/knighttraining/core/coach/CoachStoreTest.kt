package com.knightindustries.knighttraining.core.coach

import com.knightindustries.knighttraining.core.model.Activity
import com.knightindustries.knighttraining.core.model.EloSnapshot
import com.knightindustries.knighttraining.core.model.PlanSuggestionHistory
import com.knightindustries.knighttraining.core.model.PlanTemplate
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import com.knightindustries.knighttraining.core.model.WeeklyReview
import com.knightindustries.knighttraining.core.store.NewBlock
import com.knightindustries.knighttraining.core.store.StoreTest
import com.knightindustries.knighttraining.core.store.applyCoachTarget
import com.knightindustries.knighttraining.core.store.coachingGate
import com.knightindustries.knighttraining.core.store.createPlan
import com.knightindustries.knighttraining.core.store.ensureLichessAccount
import com.knightindustries.knighttraining.core.store.planTemplate
import com.knightindustries.knighttraining.core.store.saveActivity
import com.knightindustries.knighttraining.core.store.saveEloSnapshots
import com.knightindustries.knighttraining.core.store.savePlanTemplate
import com.knightindustries.knighttraining.core.store.saveWeeklyGoal
import com.knightindustries.knighttraining.core.store.saveWeeklyPlan
import com.knightindustries.knighttraining.core.store.saveWeeklyReview
import com.knightindustries.knighttraining.core.store.setNextWeekFocus
import com.knightindustries.knighttraining.core.store.test
import com.knightindustries.knighttraining.core.store.weeklyPlan
import com.knightindustries.knighttraining.core.store.weeklyReview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

/** The coach's database side: the gate's signals, and what accepting a tip writes. */
class CoachStoreTest : StoreTest() {
    private val thisWeek: LocalDate get() = calendar.weekStart(now)

    private suspend fun completedWeek(weekStart: LocalDate): WeeklyPlan {
        val template = PlanTemplate(name = "Plan $weekStart")
        store.savePlanTemplate(template)
        val plan = WeeklyPlan(planTemplateId = template.id, weekStart = weekStart)
        store.saveWeeklyPlan(plan)
        store.saveWeeklyGoal(
            WeeklyGoal(
                weeklyPlanId = plan.id, activityType = type("puzzles"), targetType = TargetType.COUNT, targetValue = 10,
                currentValue = 10, completedAt = calendar.startOf(weekStart),
            ),
        )
        return store.weeklyPlan(plan.id)!!
    }

    @Test
    fun someoneWhoAlreadyLogsCoachingMayHearAboutItButIsNeverPitchedACoach() = test {
        store.saveActivity(Activity(activityType = type("coaching"), startedAt = now, endedAt = now, duration = 3600))
        val result = store.coachingGate(completedWeek(thisWeek.minusWeeks(1)), hasNotes = true, weeklyPuzzleStatus = null, savedStreak = null, lastSuggestedAt = null)
        assertNull(result.gate.suggestion)
        assertTrue(result.gate.mayMentionCoach)
    }

    @Test
    fun fourSteadyWeeksOfRapidWithAFlatRatingOpenTheGate() = test {
        val reviewed = (4 downTo 1).map { completedWeek(thisWeek.minusWeeks(it.toLong())) }.last()
        val account = store.ensureLichessAccount("player", null, null)
        val windowStart = calendar.startOf(thisWeek.minusWeeks(4))
        store.saveEloSnapshots(
            listOf(
                EloSnapshot(accountId = account, rating = 1500, ratingType = "rapid", capturedAt = windowStart.plusSeconds(3600)),
                EloSnapshot(accountId = account, rating = 1510, ratingType = "rapid", capturedAt = calendar.startOf(thisWeek.minusWeeks(1).plusDays(5))),
            ),
        )
        for (weeksAgo in 1L..4L) {
            val day = calendar.startOf(thisWeek.minusWeeks(weeksAgo).plusDays(2))
            store.saveActivity(Activity(activityType = type("playing"), startedAt = day, endedAt = day, duration = 3600, count = 3, timeControlLabel = "15+10"))
        }
        val result = store.coachingGate(reviewed, hasNotes = false, weeklyPuzzleStatus = null, savedStreak = null, lastSuggestedAt = null)
        assertEquals(CoachingSuggestion.Reason.FlatRating(CoachingSuggestion.RatingChange("rapid", 10, 12)), result.gate.suggestion?.reason)
        assertTrue(result.gate.mayMentionCoach)
    }

    @Test
    fun theStreakAdvancesWithThisReviewsPuzzles() = test {
        val reviewed = completedWeek(thisWeek.minusWeeks(1))
        val status = LichessPuzzleStatus.Played(50, 60, 1500, 1500, LichessPuzzleWeakness("Fork", 8, -150))
        val saved = WeakThemeStreak.of("Fork", 2, reviewed.weekStart.minusWeeks(1))
        val result = store.coachingGate(reviewed, false, status, saved, null)
        assertEquals(WeakThemeStreak.of("Fork", 3, reviewed.weekStart), result.streak)
    }

    @Test
    fun anAcceptedFocusIsSavedOnTheReview() = test {
        val reviewed = completedWeek(thisWeek.minusWeeks(1))
        store.saveWeeklyReview(WeeklyReview(weeklyPlanId = reviewed.id, heldBack = "Missed a lot of forks"))
        store.setNextWeekFocus("Forks", reviewed.id)
        val review = store.weeklyReview(reviewed.id)!!
        assertEquals("Forks", review.nextWeekFocus)
        assertEquals("Missed a lot of forks", review.heldBack)
    }

    private suspend fun currentWeekFromTemplate(): Pair<UUID, WeeklyGoal> {
        val template = store.createPlan("Plan", listOf(NewBlock(null, type("puzzles"), TargetType.COUNT, 30)), "Untitled", { "Untitled ($it)" })
        val goal = store.weeklyPlan(thisWeek)!!.weeklyGoals.single()
        return template.id to goal
    }

    @Test
    fun anAcceptedTargetRetargetsTheTemplateAndAnEditableWeek() = test {
        val reviewed = completedWeek(thisWeek.minusWeeks(1))
        store.saveWeeklyReview(WeeklyReview(weeklyPlanId = reviewed.id))
        val (templateId, goal) = currentWeekFromTemplate()

        store.applyCoachTarget(PlanSuggestionHistory.CoachTarget("puzzles", 40), reviewed.id, goal.goalTemplateId, thisWeek)

        val review = store.weeklyReview(reviewed.id)!!
        assertEquals("puzzles" to 40, review.nextWeekTargetCode to review.nextWeekTargetValue)
        assertEquals(40, store.planTemplate(templateId)!!.goalTemplates.single().targetValue)
        assertEquals(40, store.weeklyPlan(thisWeek)!!.weeklyGoals.single().targetValue)
    }

    @Test
    fun aWeekAlreadyTrainedAgainstKeepsItsTarget() = test {
        val reviewed = completedWeek(thisWeek.minusWeeks(1))
        store.saveWeeklyReview(WeeklyReview(weeklyPlanId = reviewed.id))
        val (templateId, goal) = currentWeekFromTemplate()
        repeat(2) {
            store.saveActivity(Activity(activityType = type("puzzles"), startedAt = now, endedAt = now, duration = 600, sessionId = UUID.randomUUID(), weeklyPlanId = goal.weeklyPlanId))
        }

        store.applyCoachTarget(PlanSuggestionHistory.CoachTarget("puzzles", 40), reviewed.id, goal.goalTemplateId, thisWeek)

        assertEquals("The template still moves", 40, store.planTemplate(templateId)!!.goalTemplates.single().targetValue)
        assertEquals("Two sittings lock the week", 30, store.weeklyPlan(thisWeek)!!.weeklyGoals.single().targetValue)
    }
}
