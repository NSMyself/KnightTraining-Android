package com.knightindustries.knighttraining.core.persistence

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.knightindustries.knighttraining.core.persistence.entities.ActivityEntity
import java.time.Instant
import java.util.UUID

@Dao
interface ActivityDao {
    /** Insert-or-update, never `REPLACE`, whose delete would detach a Lichess batch pointing here. */
    @Upsert
    suspend fun upsert(activity: ActivityEntity)

    @Update
    suspend fun update(activity: ActivityEntity)

    @Query("SELECT * FROM activity WHERE id = :id")
    suspend fun byId(id: UUID): ActivityEntity?

    /** Every activity that started in `[start, end)`, newest first, ongoing ones included. */
    @Query("SELECT * FROM activity WHERE startedAt >= :start AND startedAt < :end ORDER BY startedAt DESC")
    suspend fun startedBetween(start: Instant, end: Instant): List<ActivityEntity>

    /** Completed activities that ended at or after [since], most recently ended first. No length filter. */
    @Query("SELECT * FROM activity WHERE endedAt IS NOT NULL AND endedAt >= :since ORDER BY endedAt DESC")
    suspend fun completedEndingSince(since: Instant): List<ActivityEntity>

    @Query("SELECT COUNT(*) FROM activity WHERE startedAt >= :start AND startedAt < :end")
    suspend fun countStartedBetween(start: Instant, end: Instant): Int

    /** Lifetime seconds across every completed session. */
    @Query("SELECT COALESCE(SUM(duration), 0) FROM activity WHERE endedAt IS NOT NULL")
    suspend fun totalCompletedDuration(): Long

    @Query("SELECT COALESCE(SUM(duration), 0) FROM activity WHERE startedAt >= :start AND startedAt < :end")
    suspend fun totalDurationBetween(start: Instant, end: Instant): Long

    /**
     * The ongoing activity, if any. iOS picks an arbitrary one when several are ongoing (no
     * `ORDER BY`); this takes the most recently started.
     */
    @Query("SELECT id FROM activity WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun ongoingId(): UUID?

    @Query("UPDATE activity SET endedAt = :endedAt, duration = :duration WHERE id = :id")
    suspend fun complete(id: UUID, endedAt: Instant, duration: Long)

    @Query("DELETE FROM activity WHERE id = :id")
    suspend fun delete(id: UUID)

    @Query("DELETE FROM activity WHERE startedAt >= :start AND startedAt < :end")
    suspend fun deleteStartedBetween(start: Instant, end: Instant): Int

    /** Distinct sittings stamped with this week: what the plan's edit lock counts. */
    @Query("SELECT COUNT(DISTINCT sessionId) FROM activity WHERE weeklyPlanId = :weeklyPlanId AND sessionId IS NOT NULL")
    suspend fun sessionCount(weeklyPlanId: UUID): Int

    @Query(
        """
        SELECT EXISTS(SELECT 1 FROM activity a JOIN activity_type t ON t.id = a.activityTypeId WHERE t.code = :code)
        """,
    )
    suspend fun hasActivityOfType(code: String): Boolean
}
