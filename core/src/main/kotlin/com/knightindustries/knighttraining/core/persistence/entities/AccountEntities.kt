package com.knightindustries.knighttraining.core.persistence.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * A connected Lichess or Chess.com account. The token columns exist for schema parity with iOS but
 * are always `NULL` on Android: the login lives in a Keystore-encrypted file instead (see the app's
 * `LichessCredentials`).
 */
@Entity(tableName = "account", indices = [Index(value = ["platform"], unique = true)])
data class AccountEntity(
    @PrimaryKey val id: UUID,
    /** `lichess` | `chesscom`. */
    val platform: String,
    val username: String,
    val accessToken: String?,
    val refreshToken: String?,
    val tokenExpiresAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** Lichess federation title, e.g. `IM`. */
    val title: String?,
    val lastGameSyncAt: Instant?,
    val lastPuzzleSyncAt: Instant?,
    /** Puzzle Storm's own day-bucket id, `"YYYY/M/D"`. */
    val lastStormDayId: String?,
    val lastStormRunsForDay: Int?,
    val lastStormSecondsForDay: Int?,
    /** Puzzle Streak: the run count and best score of the day containing `lastStreakSyncAt`. */
    val lastStreakRuns: Int?,
    val lastStreakScore: Int?,
    val lastStreakSyncAt: Instant?,
    val flag: String?,
)

@Entity(
    tableName = "elo_snapshot",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["accountId", "ratingType", "capturedAt"])],
)
data class EloSnapshotEntity(
    @PrimaryKey val id: UUID,
    val accountId: UUID,
    val rating: Int,
    /** `bullet` | `blitz` | `rapid` | `classical`. */
    val ratingType: String,
    val capturedAt: Instant,
)

/** Long-term goals: an Elo target, a title, or free text. `completedAt == null` means active. */
@Entity(tableName = "milestone")
data class MilestoneEntity(
    @PrimaryKey val id: UUID,
    /** `elo` | `title` | `free_text`. */
    val kind: String,
    val ratingType: String?,
    val targetRating: Int?,
    /** `null` on a title milestone means "any title". */
    val targetTitle: String?,
    val text: String?,
    val completedAt: Instant?,
    val createdAt: Instant,
)

/** A detected batch of Lichess games/puzzles/Storm/Streak runs awaiting the user's answer. */
@Entity(
    tableName = "lichess_activity_batch",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"],
            childColumns = ["activityId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["accountId", "status"]),
        Index(value = ["activityId"]),
    ],
)
data class LichessActivityBatchEntity(
    @PrimaryKey val id: UUID,
    val accountId: UUID,
    val kind: String,
    val category: String?,
    val externalIds: String,
    val itemCount: Int,
    val totalDurationSeconds: Int,
    val timeControlLabel: String?,
    val firstItemAt: Instant,
    val lastItemAt: Instant,
    val status: String,
    val activityId: UUID?,
    val createdAt: Instant,
    /** Puzzle Streak only. */
    val bestScore: Int?,
)
