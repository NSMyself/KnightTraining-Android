package com.knightindustries.knighttraining.core.coach

import com.knightindustries.knighttraining.core.lichess.LichessPuzzleDashboard
import com.knightindustries.knighttraining.core.model.Activity
import com.knightindustries.knighttraining.core.model.ActivityType
import com.knightindustries.knighttraining.core.model.EloSnapshot
import com.knightindustries.knighttraining.core.model.PlanSuggestionHistory
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** Ports of iOS `CoachTargetChangeTests`, `LichessPuzzleWeaknessTests` and the pure half of `CoachingSuggestionTests`. */
class CoachSignalsTest {
    private val puzzles = ActivityType(UUID.randomUUID(), "puzzles")
    private val openings = ActivityType(UUID.randomUUID(), "openings")
    private val playing = ActivityType(UUID.randomUUID(), "playing")
    private val utc = ZoneOffset.UTC
    private val now = Instant.parse("2026-11-12T12:00:00Z")

    // MARK: - CoachTargetChange

    private fun plan(bonusPuzzles: Boolean = false): WeeklyPlan {
        val id = UUID.randomUUID()
        return WeeklyPlan(
            id = id, planTemplateId = UUID.randomUUID(), weekStart = LocalDate.of(2026, 11, 8),
            weeklyGoals = listOf(
                WeeklyGoal(weeklyPlanId = id, activityType = puzzles, targetType = TargetType.COUNT, targetValue = 30, isBonus = bonusPuzzles),
                WeeklyGoal(weeklyPlanId = id, activityType = openings, targetType = TargetType.DURATION, targetValue = 3600),
            ),
        )
    }

    @Test
    fun aCountIsKeptAsIsAndADurationReadAsMinutes() {
        assertEquals(PlanSuggestionHistory.CoachTarget("puzzles", 42), CoachTargetChange.resolve("puzzles", 42, plan()))
        assertEquals(PlanSuggestionHistory.CoachTarget("openings", 5400), CoachTargetChange.resolve("openings", 90, plan()))
    }

    @Test
    fun anImplausibleUnchangedOrUnmatchedTargetIsDropped() {
        assertNull("2 hours read as 2 minutes", CoachTargetChange.resolve("openings", 2, plan()))
        assertNull("Seconds read as minutes", CoachTargetChange.resolve("openings", 3600, plan()))
        assertNull(CoachTargetChange.resolve("puzzles", 100, plan()))
        assertNull(CoachTargetChange.resolve("puzzles", 30, plan()))
        assertNull(CoachTargetChange.resolve("endgames", 30, plan()))
        assertNull(CoachTargetChange.resolve("puzzles", 40, plan(bonusPuzzles = true)))
        assertNull(CoachTargetChange.resolve(null, 40, plan()))
        assertNull(CoachTargetChange.resolve("puzzles", null, plan()))
    }

    @Test
    fun anotherGoalsNumberIsAMisread() {
        val id = UUID.randomUUID()
        val mixed = WeeklyPlan(
            id = id, planTemplateId = UUID.randomUUID(), weekStart = LocalDate.of(2026, 11, 8),
            weeklyGoals = listOf(
                WeeklyGoal(weeklyPlanId = id, activityType = playing, targetType = TargetType.COUNT, targetValue = 3),
                WeeklyGoal(weeklyPlanId = id, activityType = puzzles, targetType = TargetType.DURATION, targetValue = 1800),
            ),
        )
        assertTrue(CoachTargetChange.isMisread("puzzles", 3, mixed))
        assertTrue(CoachTargetChange.isMisread("playing", 30, mixed))
    }

    @Test
    fun easingOffOrAnyOtherNumberIsNotAMisread() {
        assertFalse("30 puzzles down to 10 after a rough week", CoachTargetChange.isMisread("puzzles", 10, plan()))
        assertFalse(CoachTargetChange.isMisread("openings", 90, plan()))
        assertFalse(CoachTargetChange.isMisread("puzzles", 30, plan()))
        assertFalse(CoachTargetChange.isMisread("endgames", 3, plan()))
        assertFalse(CoachTargetChange.isMisread("puzzles", null, plan()))
        assertFalse(CoachTargetChange.isMisread(null, 3, plan()))
    }

    // MARK: - Puzzle weakness

    private fun results(nb: Int, performance: Int, firstWins: Int = nb) = LichessPuzzleDashboard.Results(nb, firstWins, 0, 1500, performance)

    private fun dashboard(global: Int, vararg themes: Triple<String, Int, Int>) = LichessPuzzleDashboard(
        days = 7, global = results(100, global),
        themes = themes.associate { (name, nb, perf) -> name.lowercase() to LichessPuzzleDashboard.Theme(name, results(nb, perf)) },
    )

    @Test
    fun theWeakestQualifyingThemeIsPicked() {
        assertNull(LichessPuzzleWeakness.mostNotable(dashboard(1500)))
        assertNull("Too few attempts", LichessPuzzleWeakness.mostNotable(dashboard(1500, Triple("Fork", 3, 1200))))
        assertNull("Only 50 below", LichessPuzzleWeakness.mostNotable(dashboard(1500, Triple("Fork", 10, 1450))))
        assertNull("Strong", LichessPuzzleWeakness.mostNotable(dashboard(1500, Triple("Opening", 20, 1800))))
        assertEquals(LichessPuzzleWeakness("Fork", 6, -200), LichessPuzzleWeakness.mostNotable(dashboard(1500, Triple("Fork", 6, 1300))))
        assertEquals(
            LichessPuzzleWeakness("Pin", 8, -400),
            LichessPuzzleWeakness.mostNotable(dashboard(1500, Triple("Fork", 6, 1300), Triple("Pin", 8, 1100), Triple("Endgame", 5, 1350))),
        )
        assertEquals("Fork", LichessPuzzleWeakness.mostNotable(dashboard(1500, Triple("Fork", 2, 1420)), 2, 50)?.themeName)
    }

    @Test
    fun theStatusSummarizesTheDashboard() {
        val dashboard = LichessPuzzleDashboard(
            30, LichessPuzzleDashboard.Results(80, 51, 10, 1700, 1750),
            mapOf("fork" to LichessPuzzleDashboard.Theme("Fork", results(12, 1550))),
        )
        assertEquals(
            LichessPuzzleStatus.Played(80, 64, 1750, 1700, LichessPuzzleWeakness("Fork", 12, -200)),
            LichessPuzzleStatus.from(dashboard),
        )
        assertEquals(LichessPuzzleStatus.NoPuzzles, LichessPuzzleStatus.from(LichessPuzzleDashboard(30, LichessPuzzleDashboard.Results(0, 0, 0, 0, 0))))
    }

    // MARK: - Coaching suggestion

    private val forks = WeakThemeStreak.of("Fork", 3, LocalDate.of(2026, 11, 1))

    private fun signals(
        hasCoachingHistory: Boolean = false,
        committedWeeks: Int = 6,
        completed: Boolean = true,
        lastSuggestedAt: Instant? = null,
        streak: WeakThemeStreak? = null,
        completedWeeks: Int = 0,
        rating: CoachingSuggestion.RatingChange? = null,
        hasNotes: Boolean = false,
    ) = CoachingSuggestion.Signals(hasCoachingHistory, committedWeeks, completed, lastSuggestedAt, streak, completedWeeks, rating, hasNotes)

    private fun evaluate(signals: CoachingSuggestion.Signals) = CoachingSuggestion.evaluate(signals, now, utc)

    @Test
    fun aRecurringWeakThemeIsAReasonWithinTheGate() {
        assertEquals(CoachingSuggestion.Reason.RecurringWeakTheme("Fork", 3), evaluate(signals(streak = forks))?.reason)
        assertNull("Already coached", evaluate(signals(hasCoachingHistory = true, streak = forks)))
        assertNull("3 weeks", evaluate(signals(committedWeeks = 3, streak = forks)))
        assertNotNull("4 weeks", evaluate(signals(committedWeeks = 4, streak = forks)))
        assertNull("A missed goal", evaluate(signals(completed = false, streak = forks)))
        assertNull("Twice", evaluate(signals(streak = WeakThemeStreak.of("Fork", 2, LocalDate.of(2026, 11, 1)))))
    }

    @Test
    fun atMostOnceEveryThreeMonths() {
        val twoMonthsAgo = now.atZone(utc).minusMonths(2).toInstant()
        val justOverThree = now.atZone(utc).minusMonths(3).minusDays(1).toInstant()
        assertNull(evaluate(signals(lastSuggestedAt = twoMonthsAgo, streak = forks)))
        assertNotNull(evaluate(signals(lastSuggestedAt = justOverThree, streak = forks)))
    }

    @Test
    fun aFlatRatingNeedsFourCompletedWeeksAndTwelveGames() {
        val flat = CoachingSuggestion.RatingChange("rapid", 24, 12)
        assertEquals(CoachingSuggestion.Reason.FlatRating(flat), evaluate(signals(completedWeeks = 4, rating = flat))?.reason)
        assertNull(evaluate(signals(completedWeeks = 4, rating = CoachingSuggestion.RatingChange("rapid", 0, 11))))
        assertNull("+25 isn't flat", evaluate(signals(completedWeeks = 4, rating = CoachingSuggestion.RatingChange("classical", 25, 20))))
        assertNull(evaluate(signals(completedWeeks = 3, rating = CoachingSuggestion.RatingChange("rapid", 0, 20))))
    }

    @Test
    fun notesAloneLetTheModelJudgeButAStructuredReasonWins() {
        assertEquals(CoachingSuggestion.Reason.NotesMayShowBeingStuck, evaluate(signals(hasNotes = true))?.reason)
        assertNull(evaluate(signals()))
        assertEquals(CoachingSuggestion.Reason.RecurringWeakTheme("Fork", 3), evaluate(signals(streak = forks, hasNotes = true))?.reason)
    }

    @Test
    fun ratingChangeIgnoresBlitzAndUsesTheMorePlayedSlowTimeControl() {
        val account = UUID.randomUUID()
        val start = now.minusSeconds(28L * 86_400)
        val snapshots = listOf(
            EloSnapshot(accountId = account, rating = 1500, ratingType = "rapid", capturedAt = start),
            EloSnapshot(accountId = account, rating = 1510, ratingType = "rapid", capturedAt = now),
            EloSnapshot(accountId = account, rating = 1400, ratingType = "blitz", capturedAt = start),
            EloSnapshot(accountId = account, rating = 1300, ratingType = "blitz", capturedAt = now),
            EloSnapshot(accountId = account, rating = 1600, ratingType = "classical", capturedAt = start),
            EloSnapshot(accountId = account, rating = 1700, ratingType = "classical", capturedAt = now),
        )
        fun game(count: Int, tc: String) = Activity(activityType = playing, startedAt = now, duration = 3600, count = count, timeControlLabel = tc)
        val games = listOf(game(10, "15+10"), game(4, "15+10"), game(30, "3+2"), game(2, "60 min"))
        assertEquals(CoachingSuggestion.RatingChange("rapid", 10, 14), CoachingSuggestion.ratingChange(snapshots, games))
        assertNull(CoachingSuggestion.ratingChange(snapshots.take(1), games))
    }

    @Test
    fun aMissedGoalOrAGapEndsTheRun() {
        fun week(weeksAgo: Long, completed: Boolean): WeeklyPlan {
            val id = UUID.randomUUID()
            val goal = WeeklyGoal(
                weeklyPlanId = id, activityType = puzzles, targetType = TargetType.COUNT, targetValue = 10,
                currentValue = if (completed) 10 else 3, completedAt = if (completed) now else null,
            )
            return WeeklyPlan(id = id, planTemplateId = UUID.randomUUID(), weekStart = LocalDate.of(2026, 11, 8).minusWeeks(weeksAgo), weeklyGoals = listOf(goal))
        }
        assertEquals(2, CoachingSuggestion.consecutiveCompletedWeeks(listOf(week(1, true), week(2, true), week(3, false))))
        assertEquals(1, CoachingSuggestion.consecutiveCompletedWeeks(listOf(week(1, true), week(3, true), week(4, true))))
        assertEquals(4, CoachingSuggestion.consecutiveCompletedWeeks((1L..4L).map { week(it, true) }))
    }

    @Test
    fun theStreakAdvancesOnlyOnTheNextWeeksSameTheme() {
        fun status(theme: String?) = LichessPuzzleStatus.Played(100, 70, 1600, 1550, theme?.let { LichessPuzzleWeakness(it, 10, -150) })
        val w1 = LocalDate.of(2026, 10, 25)
        val w2 = w1.plusWeeks(1)
        val first = WeakThemeStreak.advance(null, status("Fork"), w1)
        assertEquals(1, first?.reviews)
        assertEquals(2, WeakThemeStreak.advance(first, status("Fork"), w2)?.reviews)
        assertEquals("Same week twice", 1, WeakThemeStreak.advance(first, status("Fork"), w1)?.reviews)
        assertEquals("A gap starts over", 1, WeakThemeStreak.advance(first, status("Fork"), w2.plusWeeks(1))?.reviews)
        assertEquals(WeakThemeStreak.of("Pin", 1, w2), WeakThemeStreak.advance(first, status("Pin"), w2))
        assertNull(WeakThemeStreak.advance(first, status(null), w2))
        assertNull(WeakThemeStreak.advance(first, LichessPuzzleStatus.NoPuzzles, w2))
        assertEquals("A failed fetch changes nothing", first, WeakThemeStreak.advance(first, null, w2))
    }

    @Test
    fun coachMentionsAreSpottedInEveryShippedLanguageButNotLookalikes() {
        listOf(
            "A coach could help with your endgames." to null,
            "Um treinador podia ajudar." to "Portuguese (Portugal)",
            "Um treinador pode ajudar." to "Portuguese (Brazil)",
            "Un entrenador te ayudaría." to "Spanish",
            "Un allenatore potrebbe aiutarti." to "Italian",
            "Gli istruttori possono aiutarti." to "Italian",
            "Un entraîneur pourrait t'aider." to "French",
            "Un coach pourrait t'aider sur les finales." to "French",
            "Ein Trainer könnte helfen." to "German",
            "Een trainer kan je helpen." to "Dutch",
            "Een coach kan je helpen." to "Dutch",
        ).forEach { (tip, language) -> assertTrue(tip, CoachingSuggestion.mentionsCoach(tip, language)) }
        listOf(
            "Try ten minutes on the Lichess puzzle trainer before work." to null,
            "Keep your training sessions to 30 minutes of rapid games." to null,
            "Nimm dir zehn Minuten im Taktiktrainer." to "German",
            "Treina finais durante 20 minutos." to "Portuguese (Portugal)",
            "Entraîne-toi sur les finales pendant 20 minutes." to "French",
        ).forEach { (tip, language) -> assertFalse(tip, CoachingSuggestion.mentionsCoach(tip, language)) }
        assertTrue(CoachingSuggestion.mentionsCoach("Ein Trainer könnte helfen.", "Klingon"))
    }
}
