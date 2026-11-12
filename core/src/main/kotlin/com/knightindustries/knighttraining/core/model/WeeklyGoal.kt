package com.knightindustries.knighttraining.core.model

import java.time.Instant
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/** The week's concrete goal, frozen from its template. */
data class WeeklyGoal(
    val id: UUID = UUID.randomUUID(),
    val weeklyPlanId: UUID,
    val name: String? = null,
    val activityType: ActivityType,
    val resource: Resource? = null,
    val targetType: TargetType,
    val targetValue: Int,
    val currentValue: Int = 0,
    /** Frozen from the template, like every other field here. */
    val timeControlLabel: String? = null,
    /** Set when the target is hit. */
    val completedAt: Instant? = null,
    /** The template this was copied from, so editing a committed week keeps progress. */
    val goalTemplateId: UUID? = null,
    /**
     * Added mid-week by "add more". A normal goal everywhere except "is this week done", which
     * ignores it. Never written back to the plan.
     */
    val isBonus: Boolean = false,
) {
    val timeControl: TimeControl?
        get() = timeControlLabel?.let(TimeControl::parse)

    val isCompleted: Boolean get() = completedAt != null

    val progressPercentage: Double
        get() = if (targetValue <= 0) 0.0 else min(100.0, currentValue.toDouble() / targetValue * 100)

    val remainingValue: Int get() = max(0, targetValue - currentValue)

    val isExceeded: Boolean get() = currentValue > targetValue

    val hasResource: Boolean get() = resource != null

    /**
     * How much an activity moves this goal: seconds for a duration goal, items for a count goal
     * (nothing without a count), `null` for a progress goal, which neither can measure.
     */
    fun creditDelta(durationSeconds: Int, count: Int?): Int? = when (targetType) {
        TargetType.DURATION -> durationSeconds
        TargetType.COUNT -> count?.let { max(0, it) }
        TargetType.PROGRESS -> null
    }

    companion object {
        /**
         * Whole minutes for display, **never rounding a non-zero value down to zero** (50 seconds
         * of real training showed "0m" beside a bar with a visible sliver).
         *
         * Pass [targetSeconds] wherever the value sits next to its target: rounding showed 119m30s
         * as "120/120" beside "In Progress" (iOS beta report 2026-11-09), so a value short of its
         * target never displays as the target.
         */
        fun displayMinutes(seconds: Int, targetSeconds: Int? = null): Int {
            if (seconds <= 0) return 0
            // Math.round rounds halves up, like Swift's `.rounded()`; kotlin.math.round would round
            // them to even.
            val rounded = max(1, Math.round(seconds / 60.0).toInt())
            if (targetSeconds == null || seconds >= targetSeconds) return rounded
            return min(rounded, max(1, targetSeconds / 60 - 1))
        }

        /**
         * Which of the week's goals an activity logged without one could count toward: the rule
         * behind both the timer's "Counts toward" row and Lichess crediting. Candidates, never a
         * decision; nothing here picks between two goals.
         *
         * - Same activity type, and a count or duration goal (a progress goal has nothing to
         *   measure).
         * - A goal on a different resource is ruled out; goals on exactly this resource win over
         *   open ones. An activity without a resource fits every goal.
         * - Playing only, when the category is known: a goal frozen to a different category is
         *   ruled out, and same-category goals win over goals without a time control.
         * - Completed and bonus goals stay in: a weekly target is a floor, not a ceiling.
         *
         * Ordered as [goals] is.
         */
        fun creditCandidates(
            activityTypeCode: String,
            resourceId: UUID?,
            timeControlCategory: TimeControl.Category?,
            goals: List<WeeklyGoal>,
        ): List<WeeklyGoal> {
            val category = if (activityTypeCode == ActivityType.PLAYING) timeControlCategory else null

            var candidates = goals.filter { goal ->
                if (goal.activityType.code != activityTypeCode) return@filter false
                if (goal.targetType == TargetType.PROGRESS) return@filter false
                val goalResource = goal.resource?.id
                if (goalResource != null && resourceId != null && goalResource != resourceId) return@filter false
                val goalCategory = goal.timeControl?.category
                if (category != null && goalCategory != null && goalCategory != category) return@filter false
                true
            }

            if (resourceId != null) {
                candidates.filter { it.resource?.id == resourceId }.takeIf { it.isNotEmpty() }?.let { candidates = it }
            }
            if (category != null) {
                candidates.filter { it.timeControl?.category == category }.takeIf { it.isNotEmpty() }?.let { candidates = it }
            }
            return candidates
        }
    }
}
