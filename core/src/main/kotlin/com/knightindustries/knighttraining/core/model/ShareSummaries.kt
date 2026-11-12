package com.knightindustries.knighttraining.core.model

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Who's sharing: the connected account's name and its slow-chess ratings, for a share card. */
data class SharePlayer(val username: String, val platform: String, val flag: String?, val ratings: List<Rating>) {
    data class Rating(val ratingType: String, val rating: Int)

    companion object {
        /** Bullet and puzzles are left off: a card for a coach is about the games that count. */
        val ratingTypes = listOf(RatingType.BLITZ, RatingType.RAPID, RatingType.CLASSICAL)
    }
}

/** Everything the "Share my week" card shows, derived from one week's plan and activities. */
data class WeekShareSummary(
    val plan: WeeklyPlan,
    val nextWeekStart: LocalDate,
    val planName: String?,
    val totalSeconds: Int,
    /** Sittings, not segments: a session's segments share a `sessionId`. */
    val sessionCount: Int,
    /** Most time first, then by title. */
    val resourceTimes: List<ResourceTime>,
    /** Oldest first. */
    val notes: List<Note>,
    /** `null` unless the review says something. */
    val review: WeeklyReview?,
    val player: SharePlayer?,
) {
    data class ResourceTime(val id: UUID, val title: String, val seconds: Int)

    data class Note(val id: UUID, val activityTypeCode: String, val startedAt: Instant, val text: String)

    val goals: List<WeeklyGoal> get() = plan.weeklyGoals

    companion object {
        fun from(
            plan: WeeklyPlan,
            nextWeekStart: LocalDate,
            planName: String?,
            activities: List<Activity>,
            review: WeeklyReview?,
            player: SharePlayer?,
        ): WeekShareSummary {
            val completed = activities.filter { it.endedAt != null }
            val byResource = completed.mapNotNull { activity -> activity.resource?.let { it to activity.duration } }
                .groupBy({ it.first.id }) { it }
                .map { (id, entries) -> ResourceTime(id, entries.first().first.title, entries.sumOf { it.second }) }
                .sortedWith(compareByDescending<ResourceTime> { it.seconds }.thenBy { it.title })
            val notes = completed.mapNotNull { activity ->
                activity.notes?.trim()?.takeIf { it.isNotEmpty() }?.let { Note(activity.id, activity.activityType.code, activity.startedAt, it) }
            }.sortedBy { it.startedAt }
            val hasReflection = review != null &&
                listOf(review.wentWell, review.heldBack, review.wishIDone, review.nextWeekIntent).any { !it.isNullOrBlank() }
            return WeekShareSummary(
                plan = plan,
                nextWeekStart = nextWeekStart,
                planName = planName,
                totalSeconds = completed.sumOf { it.duration },
                sessionCount = completed.map { it.sessionId ?: it.id }.toSet().size,
                resourceTimes = byResource,
                notes = notes,
                review = if (hasReflection) review else null,
                player = player,
            )
        }
    }
}

/** Everything the "Share my library" card shows. */
data class LibraryShareSummary(
    /** Most recently studied first. */
    val inProgress: List<Item>,
    val finished: List<Item>,
    /** Newest first; Lichess studies never touched are left out (they're often bookmarks). */
    val notStarted: List<Item>,
    val hasUnstartedStudies: Boolean,
    val totalSeconds: Int,
    val player: SharePlayer?,
) {
    data class Item(val resource: Resource, val seconds: Int)

    val isEmpty: Boolean get() = inProgress.isEmpty() && finished.isEmpty() && notStarted.isEmpty()

    companion object {
        fun from(resources: List<Resource>, secondsByResource: Map<UUID, Int>, player: SharePlayer?): LibraryShareSummary {
            val items = resources.map { Item(it, secondsByResource[it.id] ?: 0) }
            val byRecency = compareByDescending<Item> { it.resource.lastAccessedAt ?: Instant.MIN }.thenBy { it.resource.title }
            val byNewest = compareByDescending<Item> { it.resource.createdAt }.thenBy { it.resource.title }
            val untouched = items.filter { !it.resource.isInProgress && !it.resource.isCompleted }
            val notStarted = untouched.filter { it.resource.kind.code != ResourceKind.LICHESS_STUDY }.sortedWith(byNewest)
            return LibraryShareSummary(
                inProgress = items.filter { it.resource.isInProgress }.sortedWith(byRecency),
                finished = items.filter { it.resource.isCompleted }.sortedWith(byRecency),
                notStarted = notStarted,
                hasUnstartedStudies = notStarted.size < untouched.size,
                totalSeconds = items.sumOf { it.seconds },
                player = player,
            )
        }
    }
}
