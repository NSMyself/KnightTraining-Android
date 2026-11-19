package com.knightindustries.knighttraining.core.store

import java.util.UUID

/**
 * Which of the week's goals are done, compared from one weekly surface to the next to hear a goal
 * cross its target (iOS `WeeklyViewModel.GoalCompletionSnapshot`). Every way a goal moves is a
 * write, and every write lands on the surface, so this hears all of them.
 */
data class GoalCompletionSnapshot(
    val planId: UUID?,
    val completedGoalIds: Set<UUID>,
    /** The week's own moment (the celebration), which a goal's moment gives way to. */
    val isWholeWeekComplete: Boolean,
) {
    companion object {
        fun of(surface: WeeklySurface) = GoalCompletionSnapshot(
            planId = surface.plan?.id,
            completedGoalIds = surface.plan?.weeklyGoals.orEmpty().filter { it.isCompleted }.map { it.id }.toSet(),
            // The celebration's own rule, so the two moments never both fire or both stay silent.
            // iOS counts bonus goals here; it fires its celebration without them.
            isWholeWeekComplete = surface.plan?.isFullyCompleted == true,
        )

        /**
         * Whether a goal just crossed its target. Only within the same committed week: the first
         * surface, a week rolling over or a plan being committed are not moments. Nor is the write
         * that completed the whole week, which has its own, bigger moment.
         */
        fun isMoment(previous: GoalCompletionSnapshot?, current: GoalCompletionSnapshot): Boolean {
            if (previous == null || current.planId == null || previous.planId != current.planId) return false
            if ((current.completedGoalIds - previous.completedGoalIds).isEmpty()) return false
            return !(current.isWholeWeekComplete && !previous.isWholeWeekComplete)
        }
    }
}
