package com.knightindustries.knighttraining.core.persistence

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.knightindustries.knighttraining.core.persistence.entities.AccountEntity
import com.knightindustries.knighttraining.core.persistence.entities.EloSnapshotEntity
import com.knightindustries.knighttraining.core.persistence.entities.ResourceEntity
import java.time.Instant
import java.util.UUID

data class LatestRating(val rating: Int, val ratingType: String)

/** Just what the core loop needs of resources; the library arrives in Phase 3. */
@Dao
interface ResourceDao {
    /** Insert-or-update, never `REPLACE`, whose delete would cascade away completions (iOS bug 2026-10-14). */
    @Upsert
    suspend fun upsert(resource: ResourceEntity)

    @Query("SELECT * FROM resource WHERE id = :id")
    suspend fun byId(id: UUID): ResourceEntity?

    @Query("SELECT * FROM resource WHERE id IN (:ids)")
    suspend fun byIds(ids: List<UUID>): List<ResourceEntity>

    /** "The user was here": recency only, never progress or a completion. */
    @Query("UPDATE resource SET lastAccessedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun touch(id: UUID, now: Instant)

    /** For freely draggable controls: never counts a completion. */
    @Query("UPDATE resource SET progressPercentage = :percentage, lastAccessedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun setProgress(id: UUID, percentage: Int, now: Instant)

    /** The in-progress resource touched most recently: the widget's "continue" line. */
    @Query(
        """
        SELECT * FROM resource
        WHERE progressPercentage > 0 AND progressPercentage < 100 AND lastAccessedAt IS NOT NULL
        ORDER BY lastAccessedAt DESC LIMIT 1
        """,
    )
    suspend fun continueResource(): ResourceEntity?
}

/** Just what the weekly surface needs of accounts; Lichess arrives in Phase 4. */
@Dao
interface AccountDao {
    /**
     * Insert-or-update, never `REPLACE`, whose delete would cascade away the rating history.
     * Explicit SQL for the same reason as `WeekDao.upsertPlan`: a new id for a platform that already
     * has an account throws on `platform`, instead of `@Upsert` silently doing nothing.
     */
    @Query(
        """
        INSERT INTO account (id, platform, username, accessToken, refreshToken, tokenExpiresAt, createdAt, updatedAt,
            title, lastGameSyncAt, lastPuzzleSyncAt, lastStormDayId, lastStormRunsForDay, lastStormSecondsForDay,
            lastStreakRuns, lastStreakScore, lastStreakSyncAt, flag)
        VALUES (:id, :platform, :username, :accessToken, :refreshToken, :tokenExpiresAt, :createdAt, :updatedAt,
            :title, :lastGameSyncAt, :lastPuzzleSyncAt, :lastStormDayId, :lastStormRunsForDay, :lastStormSecondsForDay,
            :lastStreakRuns, :lastStreakScore, :lastStreakSyncAt, :flag)
        ON CONFLICT(id) DO UPDATE SET
            platform = excluded.platform, username = excluded.username, accessToken = excluded.accessToken,
            refreshToken = excluded.refreshToken, tokenExpiresAt = excluded.tokenExpiresAt,
            createdAt = excluded.createdAt, updatedAt = excluded.updatedAt, title = excluded.title,
            lastGameSyncAt = excluded.lastGameSyncAt, lastPuzzleSyncAt = excluded.lastPuzzleSyncAt,
            lastStormDayId = excluded.lastStormDayId, lastStormRunsForDay = excluded.lastStormRunsForDay,
            lastStormSecondsForDay = excluded.lastStormSecondsForDay, lastStreakRuns = excluded.lastStreakRuns,
            lastStreakScore = excluded.lastStreakScore, lastStreakSyncAt = excluded.lastStreakSyncAt, flag = excluded.flag
        """,
    )
    suspend fun upsert(
        id: UUID, platform: String, username: String, accessToken: String?, refreshToken: String?,
        tokenExpiresAt: Instant?, createdAt: Instant, updatedAt: Instant, title: String?, lastGameSyncAt: Instant?,
        lastPuzzleSyncAt: Instant?, lastStormDayId: String?, lastStormRunsForDay: Int?, lastStormSecondsForDay: Int?,
        lastStreakRuns: Int?, lastStreakScore: Int?, lastStreakSyncAt: Instant?, flag: String?,
    )

    suspend fun upsert(account: AccountEntity) = with(account) {
        upsert(
            id, platform, username, accessToken, refreshToken, tokenExpiresAt, createdAt, updatedAt, title,
            lastGameSyncAt, lastPuzzleSyncAt, lastStormDayId, lastStormRunsForDay, lastStormSecondsForDay,
            lastStreakRuns, lastStreakScore, lastStreakSyncAt, flag,
        )
    }

    @Upsert
    suspend fun upsertSnapshot(snapshot: EloSnapshotEntity)

    @Query("SELECT id FROM account WHERE platform = 'lichess'")
    suspend fun lichessAccountId(): UUID?

    /** The single most recent rating across every type. */
    @Query("SELECT rating, ratingType FROM elo_snapshot WHERE accountId = :accountId ORDER BY capturedAt DESC LIMIT 1")
    suspend fun latestRating(accountId: UUID): LatestRating?
}
