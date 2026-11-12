package com.knightindustries.knighttraining.core.model

import java.time.Instant
import java.util.UUID

/**
 * A long-term goal, deliberately distinct from the weekly commitment: no week, no streak, no due
 * date. Elo and title milestones complete themselves off Lichess data
 * (`TrainingStore.reevaluateMilestones`); free text is completed by hand. Its title is derived at
 * display time (the app's `milestoneTitle`), never stored, so it follows the app's language.
 */
data class Milestone(
    val id: UUID = UUID.randomUUID(),
    val kind: String,
    /** Elo only: one of [RatingType]'s constants. */
    val ratingType: String? = null,
    /** Elo only. */
    val targetRating: Int? = null,
    /** Title only; `null` means "any title". */
    val targetTitle: String? = null,
    /** Free text only: the user's own words. */
    val text: String? = null,
    /** `null` means active. */
    val completedAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
) {
    val isCompleted: Boolean get() = completedAt != null

    /**
     * How close [currentRating] is to the target, as a plain ratio: proximity to the goal, not
     * movement since it was set (which left a nearly-there player at an empty bar, iOS 2026-09-21).
     * `null` with no rating to compare or a non-positive target.
     */
    fun eloProgress(currentRating: Int?): Double? {
        val target = targetRating ?: return null
        if (currentRating == null || target <= 0) return null
        return (currentRating.toDouble() / target).coerceIn(0.0, 1.0)
    }

    companion object {
        const val ELO = "elo"
        const val TITLE = "title"
        const val FREE_TEXT = "free_text"
    }
}

/** Everything the milestones card shows, read in one transaction. */
data class MilestonesSnapshot(
    val milestones: List<Milestone>,
    /** Latest rating per [RatingType], for the connected Lichess account; empty when none. */
    val latestRatings: Map<String, Int>,
    val currentLichessTitle: String?,
)
