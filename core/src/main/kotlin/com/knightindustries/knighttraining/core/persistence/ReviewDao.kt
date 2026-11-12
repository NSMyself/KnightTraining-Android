package com.knightindustries.knighttraining.core.persistence

import androidx.room.Dao
import androidx.room.Query
import com.knightindustries.knighttraining.core.persistence.entities.ActivityEntity
import com.knightindustries.knighttraining.core.persistence.entities.WeeklyReviewEntity
import java.time.Instant
import java.util.UUID

@Dao
interface ReviewDao {
    @Query("SELECT * FROM weekly_review WHERE weeklyPlanId = :weeklyPlanId")
    suspend fun review(weeklyPlanId: UUID): WeeklyReviewEntity?

    /**
     * Explicit SQL, not `@Upsert`: `weeklyPlanId` is a second unique key, and Room's upsert would
     * answer a conflict there by silently doing nothing (see `WeekDao.upsertPlan`).
     */
    @Query(
        """
        INSERT INTO weekly_review (id, weeklyPlanId, wentWell, heldBack, nextWeekIntent, createdAt, updatedAt,
            wishIDone, nextWeekFocus, nextWeekTargetCode, nextWeekTargetValue)
        VALUES (:id, :weeklyPlanId, :wentWell, :heldBack, :nextWeekIntent, :createdAt, :updatedAt,
            :wishIDone, :nextWeekFocus, :nextWeekTargetCode, :nextWeekTargetValue)
        ON CONFLICT(id) DO UPDATE SET
            weeklyPlanId = excluded.weeklyPlanId, wentWell = excluded.wentWell, heldBack = excluded.heldBack,
            nextWeekIntent = excluded.nextWeekIntent, createdAt = excluded.createdAt, updatedAt = excluded.updatedAt,
            wishIDone = excluded.wishIDone, nextWeekFocus = excluded.nextWeekFocus,
            nextWeekTargetCode = excluded.nextWeekTargetCode, nextWeekTargetValue = excluded.nextWeekTargetValue
        """,
    )
    suspend fun upsert(
        id: UUID, weeklyPlanId: UUID, wentWell: String?, heldBack: String?, nextWeekIntent: String?,
        createdAt: Instant, updatedAt: Instant, wishIDone: String?, nextWeekFocus: String?,
        nextWeekTargetCode: String?, nextWeekTargetValue: Int?,
    )

    suspend fun upsert(review: WeeklyReviewEntity) = with(review) {
        upsert(id, weeklyPlanId, wentWell, heldBack, nextWeekIntent, createdAt, updatedAt, wishIDone, nextWeekFocus, nextWeekTargetCode, nextWeekTargetValue)
    }

    /** Sessions in `[start, end)` that carry a note, oldest first: the review's "This week's notes". */
    @Query("SELECT * FROM activity WHERE startedAt >= :start AND startedAt < :end AND notes IS NOT NULL AND notes != '' ORDER BY startedAt ASC")
    suspend fun activitiesWithNotes(start: Instant, end: Instant): List<ActivityEntity>
}
