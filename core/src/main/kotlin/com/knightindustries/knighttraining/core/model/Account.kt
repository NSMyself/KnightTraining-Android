package com.knightindustries.knighttraining.core.model

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * A connected chess account. Unlike iOS, no token lives here: Android keeps the Lichess login in
 * the app's Keystore-encrypted credential file, so the database never holds a usable secret.
 *
 * The `last*` fields are each sync's own high-water mark (iOS `Account`), and are written only by
 * the narrow `updateLast*` store calls, never by re-saving the whole row.
 */
data class Account(
    val id: UUID = UUID.randomUUID(),
    val platform: String,
    val username: String,
    /** Lichess federation title, e.g. `IM`. */
    val title: String? = null,
    val flag: String? = null,
    val lastGameSyncAt: Instant? = null,
    val lastPuzzleSyncAt: Instant? = null,
    /** Puzzle Storm's own day id, `"YYYY/M/D"`, and how much of that day is already accounted for. */
    val lastStormDayId: String? = null,
    val lastStormRunsForDay: Int? = null,
    val lastStormSecondsForDay: Int? = null,
    /** Puzzle Streak: the run count and best score of the UTC day containing [lastStreakSyncAt]. */
    val lastStreakRuns: Int? = null,
    val lastStreakScore: Int? = null,
    val lastStreakSyncAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt,
) {
    companion object {
        const val LICHESS = "lichess"
        const val CHESSCOM = "chesscom"
    }
}

data class EloSnapshot(
    val id: UUID = UUID.randomUUID(),
    val accountId: UUID,
    val rating: Int,
    /** One of [RatingType]'s constants. */
    val ratingType: String,
    val capturedAt: Instant,
)

object RatingType {
    const val BULLET = "bullet"
    const val BLITZ = "blitz"
    const val RAPID = "rapid"
    const val CLASSICAL = "classical"
    const val PUZZLE = "puzzle"

    val all = listOf(BULLET, BLITZ, RAPID, CLASSICAL, PUZZLE)
}

/**
 * One point on the weekly rating chart. `rating` is `null` for a week with no snapshot yet rather
 * than the week being skipped, so the x-axis stays a continuous timeline.
 */
data class WeeklyElo(val weekStart: LocalDate, val rating: Int?)
