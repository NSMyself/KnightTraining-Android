package com.knightindustries.knighttraining.core.store

import com.knightindustries.knighttraining.core.model.Activity
import com.knightindustries.knighttraining.core.model.CatalogCourse
import com.knightindustries.knighttraining.core.model.EloSnapshot
import com.knightindustries.knighttraining.core.model.PlanSuggestionHistory
import com.knightindustries.knighttraining.core.model.PlanTemplate
import com.knightindustries.knighttraining.core.model.Resource
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import com.knightindustries.knighttraining.core.model.WeeklyReview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Port of iOS `PlanSuggestionHistoryTests`. */
class PlanSuggestionHistoryTest : StoreTest() {
    private val currentWeek: LocalDate get() = calendar.weekStart(now)

    private fun weekStart(weeksAgo: Long): LocalDate = currentWeek.minusWeeks(weeksAgo)

    private data class G(val code: String, val target: Int, val current: Int, val isBonus: Boolean = false)

    private suspend fun plan(weekStart: LocalDate, vararg goals: G): WeeklyPlan {
        val template = PlanTemplate(name = "Plan $weekStart")
        store.savePlanTemplate(template)
        val plan = WeeklyPlan(planTemplateId = template.id, weekStart = weekStart)
        store.saveWeeklyPlan(plan)
        for (goal in goals) {
            store.saveWeeklyGoal(
                WeeklyGoal(
                    weeklyPlanId = plan.id, activityType = type(goal.code), targetType = TargetType.COUNT,
                    targetValue = goal.target, currentValue = goal.current, isBonus = goal.isBonus,
                ),
            )
        }
        return plan
    }

    private suspend fun activity(code: String, startedAt: Instant, duration: Int = 600, resource: Resource? = null, timeControl: String? = null) =
        store.saveActivity(
            Activity(
                activityType = type(code), resource = resource, startedAt = startedAt,
                endedAt = startedAt.plusSeconds(duration.toLong()), duration = duration, timeControlLabel = timeControl,
            ),
        )

    @Test
    fun aNewPlayerHasNoHistory() = test {
        assertEquals(PlanSuggestionHistory(), store.planSuggestionHistory())
    }

    @Test
    fun committedWeeksReportTimeAndGoalsNewestFirstExcludingThisWeek() = test {
        plan(currentWeek, G("puzzles", 10, 10))
        plan(weekStart(1), G("puzzles", 10, 10), G("playing", 3, 1), G("endgames", 3, 9, isBonus = true))
        plan(weekStart(2), G("puzzles", 10, 2))
        activity("puzzles", calendar.startOf(weekStart(1).plusDays(1)), 3600)
        activity("puzzles", calendar.startOf(weekStart(2).plusDays(1)), 1800)

        val history = store.planSuggestionHistory()
        assertEquals(listOf(PlanSuggestionHistory.Week(60, 1, 2), PlanSuggestionHistory.Week(30, 0, 1)), history.weeks)
        assertEquals(PlanSuggestionHistory.GoalOutcome(1, 2), history.goalOutcomes["puzzles"])
        assertEquals(PlanSuggestionHistory.GoalOutcome(0, 1), history.goalOutcomes["playing"])
        assertNull("Bonus goals aren't part of the commitment", history.goalOutcomes["endgames"])
    }

    @Test
    fun theLatestGamesTimeControlIsReported() = test {
        activity("playing", now.minusSeconds(7200), timeControl = "3+2")
        activity("playing", now.minusSeconds(3600), timeControl = "15+10")
        assertEquals("15+10", store.planSuggestionHistory().recentTimeControlLabel)
    }

    @Test
    fun anUnfinishedResourceIsTaggedWithTheActivityItWasMostlyStudiedUnder() = test {
        val book = store.resourceKind("book")!!
        val endgameBook = Resource(kind = book, title = "Silman's Endgames", progressPercentage = 40)
        val finished = Resource(kind = book, title = "Finished", progressPercentage = 100)
        val neverStudied = Resource(kind = book, title = "Shelf", progressPercentage = 10)
        store.saveResources(listOf(endgameBook, finished, neverStudied))
        activity("endgames", now.minusSeconds(3600), resource = endgameBook)
        activity("endgames", now.minusSeconds(7200), resource = endgameBook)
        activity("openings", now.minusSeconds(9000), resource = endgameBook)
        activity("openings", now.minusSeconds(9000), resource = finished)
        assertEquals(
            listOf(PlanSuggestionHistory.ActiveResource(endgameBook.id, "Silman's Endgames", "endgames")),
            store.planSuggestionHistory().activeResources,
        )
    }

    @Test
    fun theLichessRatingFollowsTheTimeControlThePlayerPlays() = test {
        val account = store.ensureLichessAccount("player", null, null)
        store.saveEloSnapshots(
            listOf(
                EloSnapshot(accountId = account, rating = 1500, ratingType = "rapid", capturedAt = now.minusSeconds(60)),
                EloSnapshot(accountId = account, rating = 1700, ratingType = "blitz", capturedAt = now.minusSeconds(60)),
            ),
        )
        store.planSuggestionHistory().let {
            assertEquals(1500, it.lichessRating)
            assertEquals("rapid", it.lichessRatingType)
        }
        activity("playing", now.minusSeconds(3600), timeControl = "3+2")
        store.planSuggestionHistory().let {
            assertEquals(1700, it.lichessRating)
            assertEquals("blitz", it.lichessRatingType)
        }
    }

    private suspend fun review(weeksAgo: Long, focus: String? = null, target: Pair<String, Int>? = null) {
        val plan = plan(weekStart(weeksAgo))
        store.saveWeeklyReview(
            WeeklyReview(weeklyPlanId = plan.id, nextWeekFocus = focus, nextWeekTargetCode = target?.first, nextWeekTargetValue = target?.second),
        )
    }

    @Test
    fun theLatestReviewsAcceptedAdviceIsReported() = test {
        review(2, "Old focus", "openings" to 1800)
        review(1, "Forks: replay the puzzles you miss", "puzzles" to 40)
        val history = store.planSuggestionHistory()
        assertEquals("Forks: replay the puzzles you miss", history.coachFocus)
        assertEquals(PlanSuggestionHistory.CoachTarget("puzzles", 40), history.coachTarget)
    }

    @Test
    fun olderAdviceIsNotCarriedForwardWhenTheLatestReviewAcceptedNothing() = test {
        review(2, "Old focus", "openings" to 1800)
        review(1)
        val history = store.planSuggestionHistory()
        assertNull(history.coachFocus)
        assertNull(history.coachTarget)
    }

    @Test
    fun aReviewOfTheCurrentWeekIsIgnored() = test {
        review(0, "Too early", "puzzles" to 40)
        assertNull(store.planSuggestionHistory().coachTarget)
    }

    @Test
    fun candidatesAreChessMoodCoursesWithATopicThePlayerDoesntHave() = test {
        val owned = CatalogCourse(platform = CatalogCourse.CHESSMOOD, externalId = "1", title = "Owned", topicCode = "endgames")
        val open = CatalogCourse(
            platform = CatalogCourse.CHESSMOOD, externalId = "2", title = "Opening Principles", topicCode = "openings",
            minRating = 800, maxRating = 1600, lengthMinutes = 300,
        )
        val strategy = CatalogCourse(platform = CatalogCourse.CHESSMOOD, externalId = "3", title = "Silent Strategy")
        val chessable = CatalogCourse(platform = CatalogCourse.CHESSABLE, externalId = "4", title = "Chessable", topicCode = "openings")
        store.saveCatalogCourses(listOf(owned, open, strategy, chessable))
        store.saveResource(owned.toResource(store.resourceKind("course")!!))
        assertEquals(
            listOf(PlanSuggestionHistory.CatalogCandidate(open.id, "Opening Principles", "openings", 800, 1600, 300)),
            store.planSuggestionHistory().catalogCandidates,
        )
    }
}
