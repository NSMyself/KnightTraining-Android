package com.knightindustries.knighttraining.core.model

import java.time.Instant
import java.util.UUID

/**
 * Lichess games, puzzles, Storm or Streak runs detected close together, offered as one "we noticed
 * you…" banner. Persisted so the offer survives a relaunch and an answer is remembered: nothing is
 * ever offered twice. It only becomes training history once accepted, through `logSession`.
 *
 * One type for every kind (iOS docs/lichess.md, "One generalized pipeline"); detection stays per
 * kind because each kind's data is genuinely different.
 */
data class LichessActivityBatch(
    val id: UUID = UUID.randomUUID(),
    val accountId: UUID,
    val kind: String,
    /** Games only: a [TimeControl.Category] name in lower case (`rapid`, `classical`, `blitz`). */
    val category: String? = null,
    /** Lichess ids in order, and the dedup key for games and puzzles. Storm/Streak use a placeholder. */
    val externalIds: List<String>,
    val itemCount: Int,
    /**
     * Games: measured, the sum of each game's own span. Puzzles: the batch's bounding box, an
     * estimate. Storm: Lichess's own time delta. Streak: 0, the popup asks.
     */
    val totalDurationSeconds: Int,
    /** Games only: the most common exact time control, in [TimeControl.label] form. */
    val timeControlLabel: String? = null,
    val firstItemAt: Instant,
    val lastItemAt: Instant,
    /** Streak only: a best score one of the new runs set, which pre-fills the puzzle count. */
    val bestScore: Int? = null,
    val status: String = PENDING,
    val activityId: UUID? = null,
    val createdAt: Instant = Instant.now(),
) {
    val isGame: Boolean get() = kind == GAME
    val isStorm: Boolean get() = kind == STORM
    val isStreak: Boolean get() = kind == STREAK

    /** Storm and Streak are still puzzle-solving: credited as Puzzles, never Playing. */
    val activityTypeCode: String get() = if (kind == GAME) ActivityType.PLAYING else ActivityType.PUZZLES

    val timeControlCategory: TimeControl.Category?
        get() = category?.let { name -> TimeControl.Category.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }

    /**
     * When the logged activity starts. A Streak batch's window is just the gap between two syncs,
     * possibly days wide, so it's anchored at the sync that saw the runs and backdated by the
     * confirmed duration instead.
     */
    fun startedAt(durationSeconds: Int): Instant =
        if (isStreak) lastItemAt.minusSeconds(durationSeconds.toLong()) else firstItemAt

    companion object {
        const val GAME = "game"
        const val PUZZLE = "puzzle"
        const val STORM = "storm"
        const val STREAK = "streak"

        const val PENDING = "pending"
        const val ACCEPTED = "accepted"
        const val DECLINED = "declined"

        /** How long a pause still reads as the same sitting, for games and puzzles alike. */
        const val BATCH_GAP_SECONDS = 60L * 60
    }
}

/** One UTC day of Puzzle Streak activity from Lichess's activity feed. `start` inclusive, `end` exclusive. */
data class LichessStreakDay(val start: Instant, val end: Instant, val runs: Int, val score: Int) {
    fun contains(instant: Instant): Boolean = !instant.isBefore(start) && instant.isBefore(end)
}

/**
 * Which Puzzle Streak runs are new since the last sync. The saved state is the run count and best
 * score of the day that contained the last sync; Lichess's all-time totals missed runs (iOS
 * 2026-10-11).
 */
object LichessStreakDetection {
    data class Result(
        val newRuns: Int,
        /** A best one of the new runs set, when it can be pinned on them; `null` otherwise. */
        val bestScore: Int?,
        /** What to save as the new state, for the day containing `now` (0 with no runs yet). */
        val runsForCurrentDay: Int,
        val scoreForCurrentDay: Int,
    )

    fun detect(days: List<LichessStreakDay>, lastSyncAt: Instant, runsAccounted: Int, scoreAccounted: Int, now: Instant): Result {
        var newRuns = 0
        var bestScore: Int? = null
        for (day in days) {
            if (day.contains(lastSyncAt)) {
                // Same day as the last sync: only the growth since then is new; a drop counts as nothing.
                newRuns += maxOf(0, day.runs - runsAccounted)
                if (day.runs > runsAccounted && day.score > scoreAccounted) bestScore = maxOf(bestScore ?: 0, day.score)
            } else if (day.start.isAfter(lastSyncAt)) {
                newRuns += day.runs
                if (day.runs > 0) bestScore = maxOf(bestScore ?: 0, day.score)
            }
        }
        val currentDay = days.firstOrNull { it.contains(now) }
        return Result(newRuns, bestScore, currentDay?.runs ?: 0, currentDay?.score ?: 0)
    }
}
