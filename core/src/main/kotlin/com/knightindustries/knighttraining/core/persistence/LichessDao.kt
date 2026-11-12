package com.knightindustries.knighttraining.core.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.knightindustries.knighttraining.core.persistence.entities.AccountEntity
import com.knightindustries.knighttraining.core.persistence.entities.EloSnapshotEntity
import com.knightindustries.knighttraining.core.persistence.entities.LichessActivityBatchEntity
import com.knightindustries.knighttraining.core.persistence.entities.MilestoneEntity
import java.time.Instant
import java.util.UUID

data class TypedRating(val ratingType: String, val rating: Int)

/**
 * Accounts, ratings, detected batches and milestones. Each sync's high-water mark has its own
 * narrow `UPDATE`: re-saving the whole row has wiped a sync column three times on iOS when a caller
 * rebuilt it by hand and missed one.
 */
@Dao
interface LichessDao {
    @Query("SELECT * FROM account WHERE platform = :platform")
    suspend fun account(platform: String): AccountEntity?

    @Query("UPDATE account SET username = :username, title = :title, flag = :flag, updatedAt = :now WHERE id = :id")
    suspend fun updateIdentity(id: UUID, username: String, title: String?, flag: String?, now: Instant)

    @Query("UPDATE account SET lastGameSyncAt = :at, updatedAt = :now WHERE id = :id")
    suspend fun updateLastGameSyncAt(id: UUID, at: Instant, now: Instant)

    @Query("UPDATE account SET lastPuzzleSyncAt = :at, updatedAt = :now WHERE id = :id")
    suspend fun updateLastPuzzleSyncAt(id: UUID, at: Instant, now: Instant)

    @Query(
        """
        UPDATE account SET lastStormDayId = :dayId, lastStormRunsForDay = :runs, lastStormSecondsForDay = :seconds,
            updatedAt = :now WHERE id = :id
        """,
    )
    suspend fun updateStormState(id: UUID, dayId: String, runs: Int, seconds: Int, now: Instant)

    @Query(
        """
        UPDATE account SET lastStreakRuns = :runs, lastStreakScore = :score, lastStreakSyncAt = :syncedAt,
            updatedAt = :now WHERE id = :id
        """,
    )
    suspend fun updateStreakState(id: UUID, runs: Int, score: Int, syncedAt: Instant, now: Instant)

    /** Cascades to the account's rating history and batches. */
    @Query("DELETE FROM account WHERE id = :id")
    suspend fun deleteAccount(id: UUID)

    // MARK: - Ratings

    @Query("SELECT COUNT(*) FROM elo_snapshot WHERE accountId = :accountId")
    suspend fun snapshotCount(accountId: UUID): Int

    @Query("SELECT EXISTS(SELECT 1 FROM elo_snapshot WHERE accountId = :accountId AND capturedAt >= :from AND capturedAt < :to)")
    suspend fun hasSnapshotBetween(accountId: UUID, from: Instant, to: Instant): Boolean

    @Insert
    suspend fun insertSnapshots(snapshots: List<EloSnapshotEntity>)

    @Query("SELECT * FROM elo_snapshot WHERE accountId = :accountId AND ratingType = :ratingType ORDER BY capturedAt")
    suspend fun snapshots(accountId: UUID, ratingType: String): List<EloSnapshotEntity>

    @Query(
        """
        SELECT rating FROM elo_snapshot WHERE accountId = :accountId AND ratingType = :ratingType AND capturedAt < :before
        ORDER BY capturedAt DESC LIMIT 1
        """,
    )
    suspend fun latestRatingBefore(accountId: UUID, ratingType: String, before: Instant): Int?

    @Query("SELECT * FROM elo_snapshot WHERE accountId = :accountId AND capturedAt >= :from AND capturedAt < :to ORDER BY capturedAt")
    suspend fun snapshotsBetween(accountId: UUID, from: Instant, to: Instant): List<EloSnapshotEntity>

    /** The latest rating of each type, in one query. */
    @Query(
        """
        SELECT ratingType, rating FROM elo_snapshot AS e
        WHERE accountId = :accountId AND capturedAt = (
            SELECT MAX(capturedAt) FROM elo_snapshot WHERE accountId = e.accountId AND ratingType = e.ratingType
        )
        GROUP BY ratingType
        """,
    )
    suspend fun latestRatings(accountId: UUID): List<TypedRating>

    // MARK: - Batches

    @Query("SELECT externalIds FROM lichess_activity_batch WHERE accountId = :accountId AND kind = :kind")
    suspend fun batchedExternalIds(accountId: UUID, kind: String): List<String>

    @Insert
    suspend fun insertBatch(batch: LichessActivityBatchEntity)

    @Query(
        """
        SELECT b.* FROM lichess_activity_batch AS b JOIN account AS a ON a.id = b.accountId
        WHERE a.platform = 'lichess' AND b.status = 'pending' ORDER BY b.firstItemAt
        """,
    )
    suspend fun pendingBatches(): List<LichessActivityBatchEntity>

    @Query("SELECT * FROM lichess_activity_batch WHERE id = :id")
    suspend fun batch(id: UUID): LichessActivityBatchEntity?

    @Query("UPDATE lichess_activity_batch SET status = :status, activityId = :activityId WHERE id = :id")
    suspend fun setBatchStatus(id: UUID, status: String, activityId: UUID?)

    @Query("SELECT EXISTS(SELECT 1 FROM activity WHERE externalId = :externalId)")
    suspend fun activityExists(externalId: String): Boolean

    // MARK: - Milestones

    @Query("SELECT * FROM milestone ORDER BY createdAt")
    suspend fun milestones(): List<MilestoneEntity>

    /** Nothing references `milestone` and `id` is its only unique key, so `@Upsert` is safe here. */
    @Upsert
    suspend fun upsertMilestone(milestone: MilestoneEntity)

    @Query("DELETE FROM milestone WHERE id = :id")
    suspend fun deleteMilestone(id: UUID)

    @Query("UPDATE milestone SET completedAt = :at WHERE id = :id")
    suspend fun setMilestoneCompletedAt(id: UUID, at: Instant?)
}
