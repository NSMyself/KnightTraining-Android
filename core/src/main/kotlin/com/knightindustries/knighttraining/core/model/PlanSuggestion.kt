package com.knightindustries.knighttraining.core.model

import java.util.UUID
import kotlin.math.ceil

/** A coarse rating bracket that scales suggested targets. */
enum class RatingBand {
    FOUNDATIONS, DEVELOPING, ADVANCED, EXPERT;

    companion object {
        fun forRating(rating: Int): RatingBand = when {
            rating < 1200 -> FOUNDATIONS
            rating < 1600 -> DEVELOPING
            rating < 2000 -> ADVANCED
            else -> EXPERT
        }
    }
}

/**
 * Which site a rating is from. ChessMood's course ranges use Chess.com blitz, so other scales are
 * converted through ChessMood's own published comparison table, interpolated between rows.
 */
enum class RatingScale {
    CHESS_COM_BLITZ, CHESS_COM_RAPID, LICHESS_BLITZ, LICHESS_RAPID;

    private fun column(row: IntArray): Int = row[ordinal]

    fun chessComBlitzEquivalent(rating: Int): Int {
        val first = TABLE.first()
        val last = TABLE.last()
        if (rating <= column(first)) return maxOf(0, rating + first[0] - column(first))
        if (rating >= column(last)) return rating + last[0] - column(last)
        for ((lower, upper) in TABLE.zipWithNext()) {
            if (rating <= column(upper)) {
                val fraction = (rating - column(lower)).toDouble() / (column(upper) - column(lower))
                return Math.round(lower[0] + fraction * (upper[0] - lower[0])).toInt()
            }
        }
        return rating
    }

    companion object {
        /** Columns in enum order: Chess.com blitz, Chess.com rapid, Lichess blitz, Lichess rapid. */
        private val TABLE = listOf(
            intArrayOf(500, 710, 945, 1145),
            intArrayOf(800, 1025, 1200, 1430),
            intArrayOf(1000, 1210, 1395, 1595),
            intArrayOf(1250, 1430, 1600, 1775),
            intArrayOf(1500, 1630, 1800, 1940),
            intArrayOf(1750, 1820, 1980, 2075),
            intArrayOf(2000, 2000, 2135, 2200),
            intArrayOf(2200, 2105, 2280, 2320),
            intArrayOf(2500, 2300, 2510, 2510),
            intArrayOf(3000, 2720, 2915, 2820),
        )

        fun lichess(ratingType: String): RatingScale =
            if (ratingType == RatingType.RAPID || ratingType == RatingType.CLASSICAL) LICHESS_RAPID else LICHESS_BLITZ
    }
}

/** What the player has done lately; empty for a new user, and the engine then plans from the request alone. */
data class PlanSuggestionHistory(
    /** Newest first; the current week excluded. */
    val weeks: List<Week> = emptyList(),
    /** Per activity type; bonus goals left out. */
    val goalOutcomes: Map<String, GoalOutcome> = emptyMap(),
    /** The most recent game's time control: players keep the one they already play. */
    val recentTimeControlLabel: String? = null,
    /** Most recently accessed first. */
    val activeResources: List<ActiveResource> = emptyList(),
    val lichessRating: Int? = null,
    /** `null` is read as rapid. */
    val lichessRatingType: String? = null,
    /** The coach's accepted focus phrase; only an on-device model can read it. */
    val coachFocus: String? = null,
    val coachTarget: CoachTarget? = null,
    /** ChessMood courses with a mapped topic the player doesn't already have. */
    val catalogCandidates: List<CatalogCandidate> = emptyList(),
) {
    data class Week(val actualMinutes: Int, val goalsHit: Int, val goalCount: Int)

    data class GoalOutcome(val hit: Int, val total: Int) {
        val rate: Double get() = if (total > 0) hit.toDouble() / total else 0.0
    }

    /** A resource partway through, under the activity type it's been logged under most. */
    data class ActiveResource(val resourceId: UUID, val title: String, val activityTypeCode: String)

    data class CatalogCandidate(
        val catalogCourseId: UUID,
        val title: String,
        val topicCode: String,
        val minRating: Int? = null,
        val maxRating: Int? = null,
        val lengthMinutes: Int? = null,
    ) {
        /** `null` when either end is open. */
        val ratingSpan: Int? get() = if (minRating != null && maxRating != null) maxRating - minRating else null
    }

    /** In the goal's own units: a count, or seconds. */
    data class CoachTarget(val activityTypeCode: String, val value: Int)
}

data class PlanSuggestionRequest(
    val weeklyMinutes: Int,
    /** Typed; ignored when the history has a Lichess rating. */
    val rating: Int? = null,
    val ratingScale: RatingScale = RatingScale.CHESS_COM_BLITZ,
    /** Activity codes to lean into. */
    val focusCodes: List<String> = emptyList(),
)

data class SuggestedGoal(
    val activityTypeCode: String,
    val targetType: TargetType,
    val targetValue: Int,
    val timeControlLabel: String? = null,
    val resourceId: UUID? = null,
)

/** Why the plan looks the way it does: structured, so the app words (and translates) each one. */
sealed interface PlanSuggestionReason {
    data class BudgetCalibrated(val requestedMinutes: Int, val plannedMinutes: Int, val averageMinutes: Int, val completionRate: Double) : PlanSuggestionReason
    data class Rating(val rating: Int, val band: RatingBand, val fromLichess: Boolean) : PlanSuggestionReason
    data object NoRating : PlanSuggestionReason
    data class Focus(val codes: List<String>) : PlanSuggestionReason
    data class LighterOn(val code: String, val completionRate: Double) : PlanSuggestionReason
    data class LinkedResource(val title: String, val activityTypeCode: String) : PlanSuggestionReason
    data class CoachTargetApplied(val code: String, val targetType: TargetType, val value: Int) : PlanSuggestionReason
}

data class CourseSuggestion(val catalogCourseId: UUID, val title: String, val activityTypeCode: String, val weeksAtThisPace: Int?)

data class PlanSuggestion(
    val budgetMinutes: Int,
    val goals: List<SuggestedGoal>,
    val reasons: List<PlanSuggestionReason>,
    val course: CourseSuggestion? = null,
)

/**
 * Sizes a weekly plan to a time budget. Deterministic on purpose: every number comes from here; a
 * language model may only turn the player's note into focus codes. Ported from iOS
 * `PlanSuggestionEngine`, whose doc comment walks through the six steps: budget, shares, blocks,
 * coach target and coaching lesson, resources, one ChessMood course.
 */
object PlanSuggestionEngine {
    val activityCodesOrder = listOf(ActivityType.PUZZLES, ActivityType.PLAYING, ActivityType.ANALYZING, ActivityType.OPENINGS, ActivityType.ENDGAMES)

    private val sharesByBand = mapOf(
        RatingBand.FOUNDATIONS to mapOf("puzzles" to 0.35, "playing" to 0.30, "analyzing" to 0.15, "openings" to 0.05, "endgames" to 0.15),
        RatingBand.DEVELOPING to mapOf("puzzles" to 0.30, "playing" to 0.30, "analyzing" to 0.15, "openings" to 0.10, "endgames" to 0.15),
        RatingBand.ADVANCED to mapOf("puzzles" to 0.25, "playing" to 0.25, "analyzing" to 0.20, "openings" to 0.15, "endgames" to 0.15),
        RatingBand.EXPERT to mapOf("puzzles" to 0.20, "playing" to 0.25, "analyzing" to 0.25, "openings" to 0.15, "endgames" to 0.15),
    )

    /** Study, practice, review: most focuses pull a little extra of the activity that closes the loop. */
    private val reinforcingCode = mapOf("puzzles" to "analyzing", "playing" to "analyzing", "openings" to "analyzing", "endgames" to "playing")

    /** Slow enough to think in: training games, not blitz. */
    private val defaultTimeControlByBand = mapOf(
        RatingBand.FOUNDATIONS to "15+10",
        RatingBand.DEVELOPING to "15+10",
        RatingBand.ADVANCED to "30 min",
        RatingBand.EXPERT to "30 min",
    )

    private const val FOCUS_MULTIPLIER = 2.0
    private const val REINFORCING_MULTIPLIER = 1.25
    private const val MISSED_MULTIPLIER = 0.7
    private const val MISSED_RATE_THRESHOLD = 0.5
    private const val MINIMUM_OUTCOMES = 2
    private const val CALIBRATION_MINIMUM_WEEKS = 2
    private const val CALIBRATION_RATE_THRESHOLD = 0.8
    private const val CALIBRATION_HEADROOM = 1.25
    const val MINIMUM_BUDGET_MINUTES = 60
    const val MINIMUM_BLOCK_MINUTES = 20
    const val COACHING_LESSON_MINUTES = 60

    /** Planned as when no rating is known: the middle of the developing band. */
    const val ASSUMED_RATING = 1400

    fun suggest(request: PlanSuggestionRequest, history: PlanSuggestionHistory = PlanSuggestionHistory()): PlanSuggestion {
        val reasons = mutableListOf<PlanSuggestionReason>()
        val (band, ratingReason) = band(request, history)
        reasons += ratingReason

        val (budget, budgetReasons) = budgetMinutes(request, history)
        reasons += budgetReasons

        val focus = request.focusCodes.filter { it in activityCodesOrder }
        if (focus.isNotEmpty()) reasons += PlanSuggestionReason.Focus(focus)

        val weights = sharesByBand.getValue(band).toMutableMap()
        for (code in focus) {
            weights[code] = (weights[code] ?: 0.0) * FOCUS_MULTIPLIER
            reinforcingCode[code]?.takeIf { it !in focus }?.let { weights[it] = (weights[it] ?: 0.0) * REINFORCING_MULTIPLIER }
        }
        for (code in activityCodesOrder) {
            if (code in focus) continue
            val outcome = history.goalOutcomes[code] ?: continue
            if (outcome.total < MINIMUM_OUTCOMES || outcome.rate >= MISSED_RATE_THRESHOLD) continue
            weights[code] = (weights[code] ?: 0.0) * MISSED_MULTIPLIER
            reasons += PlanSuggestionReason.LighterOn(code, outcome.rate)
        }

        val timeControl = timeControlLabel(band, history)

        var coachGoal: SuggestedGoal? = null
        var allocatable = budget
        history.coachTarget?.takeIf { it.activityTypeCode in activityCodesOrder && it.value > 0 }?.let { target ->
            val fixed = SuggestedGoal(
                target.activityTypeCode, targetType(target.activityTypeCode), target.value,
                timeControlLabel = if (target.activityTypeCode == ActivityType.PLAYING) timeControl else null,
            )
            coachGoal = fixed
            weights.remove(target.activityTypeCode)
            allocatable = maxOf(0, budget - estimatedMinutes(fixed))
            reasons += PlanSuggestionReason.CoachTargetApplied(fixed.activityTypeCode, fixed.targetType, fixed.targetValue)
        }

        var coachingGoal: SuggestedGoal? = null
        if (ActivityType.COACHING in request.focusCodes) {
            val roomForTheRest = Math.floorDiv(allocatable - MINIMUM_BLOCK_MINUTES, 5) * 5
            val minutes = maxOf(MINIMUM_BLOCK_MINUTES, minOf(COACHING_LESSON_MINUTES, roomForTheRest))
            coachingGoal = SuggestedGoal(ActivityType.COACHING, TargetType.DURATION, minutes * 60)
            allocatable = maxOf(0, allocatable - minutes)
        }

        // Too little left after the fixed blocks for anything else to be worth a block.
        val minutesByCode = if ((coachGoal != null || coachingGoal != null) && allocatable < MINIMUM_BLOCK_MINUTES) {
            emptyMap()
        } else {
            allocate(allocatable, weights, focus.toSet(), mapOf(ActivityType.PLAYING to maxOf(MINIMUM_BLOCK_MINUTES, gameMinutes(timeControl))))
        }

        val unusedResources = history.activeResources.toMutableList()
        val goals = mutableListOf<SuggestedGoal>()
        for (code in activityCodesOrder) {
            val planned = coachGoal?.takeIf { it.activityTypeCode == code }
                ?: minutesByCode[code]?.let { goal(code, it, timeControl) }
                ?: continue
            val resource = unusedResources.firstOrNull { it.activityTypeCode == code }?.also { unusedResources.remove(it) }
            resource?.let { reasons += PlanSuggestionReason.LinkedResource(it.title, code) }
            goals += planned.copy(resourceId = resource?.resourceId)
        }
        coachingGoal?.let { goals += it }

        val course = courseSuggestion(goals, focus, courseRating(request, history), history.catalogCandidates)
        return PlanSuggestion(budget, goals, reasons, course)
    }

    /** The rating on ChessMood's scale (Chess.com blitz); bands keep the player's own number. */
    fun courseRating(request: PlanSuggestionRequest, history: PlanSuggestionHistory): Int {
        history.lichessRating?.let { return RatingScale.lichess(history.lichessRatingType ?: RatingType.RAPID).chessComBlitzEquivalent(it) }
        request.rating?.takeIf { it > 0 }?.let { return request.ratingScale.chessComBlitzEquivalent(it) }
        return ASSUMED_RATING
    }

    /**
     * At most one course, only for a block left without a resource: focus blocks first, then by
     * time. A fitting range beats an open one, a narrower range a wider one.
     */
    fun courseSuggestion(
        goals: List<SuggestedGoal>,
        focus: List<String>,
        rating: Int,
        candidates: List<PlanSuggestionHistory.CatalogCandidate>,
    ): CourseSuggestion? {
        val ordered = goals
            .filter { it.resourceId == null && it.activityTypeCode in CatalogCourse.suggestableTopicCodes }
            .sortedWith(compareByDescending<SuggestedGoal> { it.activityTypeCode in focus }.thenByDescending { estimatedMinutes(it) })
        for (goal in ordered) {
            val best = candidates
                .filter { it.topicCode == goal.activityTypeCode && rating >= (it.minRating ?: Int.MIN_VALUE) && rating <= (it.maxRating ?: Int.MAX_VALUE) }
                .minWithOrNull(compareBy<PlanSuggestionHistory.CatalogCandidate> { it.ratingSpan ?: Int.MAX_VALUE }.thenBy { it.title })
                ?: continue
            val weekly = estimatedMinutes(goal)
            val weeks = best.lengthMinutes?.takeIf { weekly > 0 }?.let { ceil(it.toDouble() / weekly).toInt() }
            return CourseSuggestion(best.catalogCourseId, best.title, goal.activityTypeCode, weeks)
        }
        return null
    }

    private fun band(request: PlanSuggestionRequest, history: PlanSuggestionHistory): Pair<RatingBand, PlanSuggestionReason> {
        history.lichessRating?.let { return RatingBand.forRating(it) to PlanSuggestionReason.Rating(it, RatingBand.forRating(it), fromLichess = true) }
        request.rating?.takeIf { it > 0 }?.let { return RatingBand.forRating(it) to PlanSuggestionReason.Rating(it, RatingBand.forRating(it), fromLichess = false) }
        return RatingBand.DEVELOPING to PlanSuggestionReason.NoRating
    }

    /**
     * The request, unless recent committed weeks say otherwise: asking for 4h after training 2h and
     * missing goals gets a plan nearer what's actually done. Never raised above the request.
     */
    fun budgetMinutes(request: PlanSuggestionRequest, history: PlanSuggestionHistory): Pair<Int, List<PlanSuggestionReason>> {
        val requested = maxOf(request.weeklyMinutes, MINIMUM_BUDGET_MINUTES)
        val weeks = history.weeks
        if (weeks.size < CALIBRATION_MINIMUM_WEEKS) return requested to emptyList()
        val goalCount = weeks.sumOf { it.goalCount }
        val rate = if (goalCount > 0) weeks.sumOf { it.goalsHit }.toDouble() / goalCount else 0.0
        val average = weeks.sumOf { it.actualMinutes } / weeks.size
        if (rate >= CALIBRATION_RATE_THRESHOLD || average >= requested) return requested to emptyList()
        val calibrated = minOf(requested, maxOf(MINIMUM_BUDGET_MINUTES, roundedToQuarterHour(average * CALIBRATION_HEADROOM)))
        if (calibrated >= requested) return requested to emptyList()
        return calibrated to listOf(PlanSuggestionReason.BudgetCalibrated(requested, calibrated, average, rate))
    }

    /**
     * Splits [budget] by [weights], dropping the smallest share under its minimum (never a focus
     * area) and redistributing until every block is worth having.
     */
    fun allocate(budget: Int, weights: Map<String, Double>, focus: Set<String>, minimumMinutes: Map<String, Int> = emptyMap()): Map<String, Int> {
        val codes = activityCodesOrder.filter { (weights[it] ?: 0.0) > 0 }.toMutableList()
        fun minimum(code: String) = minimumMinutes[code] ?: MINIMUM_BLOCK_MINUTES
        while (true) {
            val total = codes.sumOf { weights[it] ?: 0.0 }
            if (total <= 0) return emptyMap()
            val minutes = codes.associateWith { Math.round(budget * (weights[it] ?: 0.0) / total).toInt() }
            val drop = codes
                .filter { it !in focus && minutes.getValue(it) < minimum(it) }
                .minByOrNull { minutes.getValue(it).toDouble() / minimum(it) }
            if (drop == null || codes.size <= 1) return minutes
            codes.remove(drop)
        }
    }

    /** The player's own time control when it's rapid or slower; faster ones would mean dozens of games. */
    private fun timeControlLabel(band: RatingBand, history: PlanSuggestionHistory): String {
        history.recentTimeControlLabel?.let(TimeControl::parse)
            ?.takeIf { it.category == TimeControl.Category.RAPID || it.category == TimeControl.Category.CLASSICAL }
            ?.let { return it.label }
        return defaultTimeControlByBand.getValue(band)
    }

    private fun goal(code: String, minutes: Int, timeControlLabel: String): SuggestedGoal = when (code) {
        ActivityType.PUZZLES -> SuggestedGoal(
            code, TargetType.COUNT,
            maxOf(5, Math.round(minutes.toDouble() / PlanEstimator.PUZZLE_MINUTES_PER_UNIT / 5).toInt() * 5),
        )
        ActivityType.PLAYING -> SuggestedGoal(
            code, TargetType.COUNT, maxOf(1, Math.round(minutes.toDouble() / gameMinutes(timeControlLabel)).toInt()), timeControlLabel,
        )
        else -> SuggestedGoal(code, TargetType.DURATION, maxOf(10, Math.round(minutes / 5.0).toInt() * 5) * 60)
    }

    /** Puzzles and games are counted, everything else timed: the plan builder's split. */
    fun targetType(code: String): TargetType =
        if (code == ActivityType.PUZZLES || code == ActivityType.PLAYING) TargetType.COUNT else TargetType.DURATION

    private fun estimatedMinutes(goal: SuggestedGoal): Int =
        PlanEstimator.estimate(listOf(PlanEstimatorBlock(goal.activityTypeCode, goal.targetType, goal.targetValue, goal.timeControlLabel)))?.minutes ?: 0

    private fun gameMinutes(label: String): Int = TimeControl.parse(label)?.let(PlanEstimator::gameMinutes) ?: 30

    private fun roundedToQuarterHour(minutes: Double): Int = Math.round(minutes / 15).toInt() * 15
}
