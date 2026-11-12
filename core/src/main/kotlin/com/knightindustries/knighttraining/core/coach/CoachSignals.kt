package com.knightindustries.knighttraining.core.coach

import com.knightindustries.knighttraining.core.lichess.LichessPuzzleDashboard
import com.knightindustries.knighttraining.core.model.Activity
import com.knightindustries.knighttraining.core.model.ActivityType
import com.knightindustries.knighttraining.core.model.EloSnapshot
import com.knightindustries.knighttraining.core.model.PlanSuggestionHistory
import com.knightindustries.knighttraining.core.model.RatingType
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.TimeControl
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** The one Lichess puzzle theme worth naming to the coach (iOS `LichessPuzzleWeakness`). */
data class LichessPuzzleWeakness(val themeName: String, val attempts: Int, val performanceDelta: Int) {
    companion object {
        /**
         * The theme furthest below the player's own overall performance, among themes with at
         * least [minimumAttempts] and at least [minimumDeltaBelowGlobal] points below. Compared to
         * `global.performance`, not puzzle rating (that's difficulty faced, not how well they did).
         */
        fun mostNotable(dashboard: LichessPuzzleDashboard, minimumAttempts: Int = 4, minimumDeltaBelowGlobal: Int = 100): LichessPuzzleWeakness? {
            val global = dashboard.global.performance
            return dashboard.themes.values
                .filter { it.results.nb >= minimumAttempts }
                .map { LichessPuzzleWeakness(it.theme, it.results.nb, it.results.performance - global) }
                .filter { it.performanceDelta <= -minimumDeltaBelowGlobal }
                .minByOrNull { it.performanceDelta }
        }
    }
}

/** How the player's Lichess puzzles went over a window; `null` (no account, failed fetch) leaves it out. */
sealed interface LichessPuzzleStatus {
    data object NoPuzzles : LichessPuzzleStatus

    data class Played(
        val attempts: Int,
        val firstTryPercent: Int,
        val performance: Int,
        val averagePuzzleRating: Int,
        val weakness: LichessPuzzleWeakness?,
    ) : LichessPuzzleStatus

    companion object {
        const val DAYS = 30

        fun from(dashboard: LichessPuzzleDashboard): LichessPuzzleStatus {
            val global = dashboard.global
            if (global.nb <= 0) return NoPuzzles
            return Played(
                attempts = global.nb,
                firstTryPercent = (global.firstWins.toDouble() / global.nb * 100).roundToInt(),
                performance = global.performance,
                averagePuzzleRating = global.puzzleRatingAvg,
                weakness = LichessPuzzleWeakness.mostNotable(dashboard),
            )
        }
    }
}

/**
 * Reads the coach's suggested number against the plan (iOS `CoachTargetChange`): counts as-is,
 * durations as minutes, only within half to double the goal's own target.
 */
object CoachTargetChange {
    private const val LOW = 0.5
    private const val HIGH = 2.0

    fun goal(code: String, plan: WeeklyPlan): WeeklyGoal? = plan.weeklyGoals.firstOrNull { it.activityType.code == code && !it.isBonus }

    fun resolve(code: String?, suggestedValue: Int?, plan: WeeklyPlan): PlanSuggestionHistory.CoachTarget? {
        if (code == null) return null
        val (goal, value) = converted(code, suggestedValue, plan) ?: return null
        val ratio = value.toDouble() / goal.targetValue
        if (value == goal.targetValue || ratio < LOW || ratio > HIGH) return null
        return PlanSuggestionHistory.CoachTarget(code, value)
    }

    /** Far out of range *and* exactly another goal's number as the prompt shows it: the coach mixed two goals up. */
    fun isMisread(code: String?, suggestedValue: Int?, plan: WeeklyPlan): Boolean {
        if (code == null || suggestedValue == null) return false
        val (goal, value) = converted(code, suggestedValue, plan) ?: return false
        val ratio = value.toDouble() / goal.targetValue
        if (ratio in LOW..HIGH) return false
        return plan.weeklyGoals.any { it.id != goal.id && promptNumber(it) == suggestedValue }
    }

    private fun promptNumber(goal: WeeklyGoal): Int? = when (goal.targetType) {
        TargetType.COUNT -> goal.targetValue
        TargetType.DURATION -> goal.targetValue / 60
        TargetType.PROGRESS -> null
    }

    private fun converted(code: String, suggestedValue: Int?, plan: WeeklyPlan): Pair<WeeklyGoal, Int>? {
        if (suggestedValue == null || suggestedValue <= 0) return null
        val goal = goal(code, plan)?.takeIf { it.targetValue > 0 } ?: return null
        return when (goal.targetType) {
            TargetType.COUNT -> goal to suggestedValue
            TargetType.DURATION -> goal to suggestedValue * 60
            TargetType.PROGRESS -> null
        }
    }
}

/**
 * Whether the weekly coach may suggest working with a coach (iOS `CoachingSuggestion`). The app
 * decides, never the model: no coaching ever logged, 4+ committed weeks, the reviewed week fully
 * done, none in the last 3 months, plus one reason.
 */
data class CoachingSuggestion(val reason: Reason) {
    sealed interface Reason {
        data class RecurringWeakTheme(val theme: String, val reviews: Int) : Reason
        data class FlatRating(val change: RatingChange) : Reason
        data object NotesMayShowBeingStuck : Reason
    }

    data class RatingChange(val ratingType: String, val change: Int, val games: Int)

    data class Signals(
        val hasCoachingHistory: Boolean,
        val committedWeeks: Int,
        val reviewedWeekCompleted: Boolean,
        val lastSuggestedAt: Instant?,
        val weakThemeStreak: WeakThemeStreak?,
        val consecutiveCompletedWeeks: Int,
        val ratingChange: RatingChange?,
        val hasNotes: Boolean,
    )

    /** Closed by default, so nothing that forgets to set it can produce a coach tip. */
    data class Gate(val suggestion: CoachingSuggestion?, val mayMentionCoach: Boolean) {
        companion object {
            val CLOSED = Gate(null, false)
        }
    }

    val contextLines: List<String>
        get() {
            val lines = mutableListOf("\n$SECTION_HEADING:", "- They have never logged a coaching session in this app.")
            val howTo = "Tie it to that reason, never name a coaching service, website or person, and don't push: " +
                "a different tip is fine if it fits the week better."
            when (val reason = reason) {
                is Reason.RecurringWeakTheme -> {
                    lines += "- Their weakest Lichess puzzle theme has been ${reason.theme} at ${reason.reviews} weekly reviews in a row."
                    lines += "- You may gently suggest that working with a coach, even one lesson a month, could help them get past it. $howTo"
                }
                is Reason.FlatRating -> {
                    val rating = reason.change
                    val change = if (rating.change > 0) "+${rating.change}" else "${rating.change}"
                    lines += "- They completed every goal for $STEADY_WEEKS_REQUIRED weeks straight and played ${rating.games} " +
                        "${rating.ratingType} games on Lichess, but their ${rating.ratingType} rating moved only $change in that time."
                    lines += "- You may gently suggest that working with a coach, even one lesson a month, could help turn that work into progress. $howTo"
                }
                Reason.NotesMayShowBeingStuck -> lines += "- Only if their notes above say they feel stuck, don't know what to study, or keep making the same mistakes, " +
                    "you may gently suggest that working with a coach, even one lesson a month, could help, tying it to what " +
                    "they wrote. Never name a coaching service, website or person. Otherwise don't mention a coach at all."
            }
            return lines
        }

    companion object {
        const val MINIMUM_COMMITTED_WEEKS = 4
        const val COOLDOWN_MONTHS = 3L
        const val WEAK_THEME_REVIEWS_REQUIRED = 3

        /** The streak reads one week of puzzles, not the coach's month: overlapping windows would carry one bad stretch. */
        const val WEAK_THEME_WINDOW_DAYS = 7
        const val STEADY_WEEKS_REQUIRED = 4
        const val FLAT_RATING_GAIN = 25
        const val MINIMUM_GAMES_FOR_RATING = 12
        const val SECTION_HEADING = "Working with a coach"

        fun evaluate(signals: Signals, now: Instant, zone: ZoneId = ZoneId.systemDefault()): CoachingSuggestion? {
            if (signals.hasCoachingHistory || signals.committedWeeks < MINIMUM_COMMITTED_WEEKS || !signals.reviewedWeekCompleted) return null
            signals.lastSuggestedAt?.let { last ->
                if (now.isBefore(last.atZone(zone).plusMonths(COOLDOWN_MONTHS).toInstant())) return null
            }
            signals.weakThemeStreak?.takeIf { it.reviews >= WEAK_THEME_REVIEWS_REQUIRED }?.let {
                return CoachingSuggestion(Reason.RecurringWeakTheme(it.theme, it.reviews))
            }
            val rating = signals.ratingChange
            if (signals.consecutiveCompletedWeeks >= STEADY_WEEKS_REQUIRED && rating != null &&
                rating.games >= MINIMUM_GAMES_FOR_RATING && rating.change < FLAT_RATING_GAIN
            ) {
                return CoachingSuggestion(Reason.FlatRating(rating))
            }
            return if (signals.hasNotes) CoachingSuggestion(Reason.NotesMayShowBeingStuck) else null
        }

        /** Fully completed weeks in a row, newest first, stopping at a miss or a gap. */
        fun consecutiveCompletedWeeks(plans: List<WeeklyPlan>): Int {
            var count = 0
            var expected: LocalDate? = null
            for (plan in plans.sortedByDescending { it.weekStart }) {
                if (expected != null && plan.weekStart != expected) break
                if (!plan.isFullyCompleted) break
                count++
                expected = plan.weekStart.minusWeeks(1)
            }
            return count
        }

        /** Rapid or classical, whichever had more games; blitz never counts (the coach must never bring it up). */
        fun ratingChange(snapshots: List<EloSnapshot>, games: List<Activity>): RatingChange? {
            val types = listOf(RatingType.RAPID, RatingType.CLASSICAL)
            val gamesByType = types.associateWith { type ->
                games.filter { it.timeControlLabel?.let(TimeControl::parse)?.category?.name?.lowercase() == type }
                    .sumOf { maxOf(it.count ?: 1, 1) }
            }
            val type = types.maxByOrNull { gamesByType.getValue(it) } ?: return null
            val ofType = snapshots.filter { it.ratingType == type }.sortedBy { it.capturedAt }
            if (ofType.size < 2) return null
            return RatingChange(type, ofType.last().rating - ofType.first().rating, gamesByType.getValue(type))
        }

        private val coachTermsByLanguage = mapOf(
            "English" to listOf("coach", "coaches"),
            "Portuguese" to listOf("coach", "treinador", "treinadora", "treinadores"),
            "Spanish" to listOf("coach", "entrenador", "entrenadora", "entrenadores"),
            "French" to listOf("coach", "coachs", "entraîneur", "entraîneuse", "entraîneurs"),
            "Italian" to listOf("coach", "allenatore", "allenatrice", "allenatori", "istruttore", "istruttrice", "istruttori"),
            "German" to listOf("coach", "trainer", "trainerin"),
            "Dutch" to listOf("coach", "trainer", "trainers"),
        )

        /**
         * The deterministic backstop: whole words in the tip's own language only, so "the Lichess
         * puzzle trainer" isn't a coach. An unknown language checks every list.
         */
        fun mentionsCoach(text: String, languageName: String? = null): Boolean {
            val name = languageName ?: "English"
            val terms = coachTermsByLanguage.entries.firstOrNull { name.startsWith(it.key) }?.value
                ?: coachTermsByLanguage.values.flatten().toSet().toList()
            return terms.any { Regex("(?<![\\p{L}\\p{N}])${Regex.escape(it)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).containsMatchIn(text) }
        }
    }
}

/** The same weakest theme across consecutive weekly reviews (iOS `WeakThemeStreak`). Saved between reviews. */
@Serializable
data class WeakThemeStreak(val theme: String, val reviews: Int, val lastWeekStart: String) {
    val lastWeek: LocalDate get() = LocalDate.parse(lastWeekStart)

    companion object {
        fun of(theme: String, reviews: Int, weekStart: LocalDate) = WeakThemeStreak(theme, reviews, weekStart.toString())

        /**
         * Advances with this review's puzzles: the same theme the week after extends it, a gap or
         * another theme starts over, no weak theme ends it. A failed fetch (`null`) changes nothing,
         * and reviewing the same week twice doesn't count twice.
         */
        fun advance(current: WeakThemeStreak?, puzzleStatus: LichessPuzzleStatus?, weekStart: LocalDate): WeakThemeStreak? {
            if (puzzleStatus == null) return current
            val weakness = (puzzleStatus as? LichessPuzzleStatus.Played)?.weakness ?: return null
            if (current == null) return of(weakness.themeName, 1, weekStart)
            if (current.lastWeek == weekStart) return current
            if (current.theme == weakness.themeName && current.lastWeek.plusWeeks(1) == weekStart) {
                return of(current.theme, current.reviews + 1, weekStart)
            }
            return of(weakness.themeName, 1, weekStart)
        }
    }
}

/** English names for the prompt; the coach writes the tip in the app's language either way. */
internal fun promptActivityName(code: String): String = when (code) {
    ActivityType.PUZZLES -> "Puzzles"
    ActivityType.PLAYING -> "Playing"
    ActivityType.ANALYZING -> "Game analysis"
    ActivityType.OPENINGS -> "Openings"
    ActivityType.ENDGAMES -> "Endgames"
    ActivityType.COACHING -> "Coaching"
    else -> "General"
}
