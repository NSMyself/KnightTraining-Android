package com.knightindustries.knighttraining.core.persistence

import androidx.room.Dao
import androidx.room.Query
import com.knightindustries.knighttraining.core.persistence.entities.ActivityEntity
import com.knightindustries.knighttraining.core.persistence.entities.WeeklyGoalEntity
import com.knightindustries.knighttraining.core.persistence.entities.WeeklyReviewEntity
import java.time.Instant
import java.time.LocalDate
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

    // MARK: - The coach's advice, once accepted

    @Query("UPDATE weekly_review SET nextWeekFocus = :focus, updatedAt = :now WHERE weeklyPlanId = :weeklyPlanId")
    suspend fun setFocus(weeklyPlanId: UUID, focus: String?, now: Instant)

    @Query("UPDATE weekly_review SET nextWeekTargetCode = :code, nextWeekTargetValue = :value, updatedAt = :now WHERE weeklyPlanId = :weeklyPlanId")
    suspend fun setTarget(weeklyPlanId: UUID, code: String, value: Int, now: Instant)

    @Query("UPDATE goal_template SET targetValue = :value WHERE id = :goalTemplateId")
    suspend fun setTemplateTarget(goalTemplateId: UUID, value: Int)

    /** Weekly goals committed from [goalTemplateId] in the week starting [weekStart] or later. */
    @Query(
        """
        SELECT g.* FROM weekly_goal AS g JOIN weekly_plan AS p ON p.id = g.weeklyPlanId
        WHERE g.goalTemplateId = :goalTemplateId AND p.weekStart >= :weekStart
        """,
    )
    suspend fun goalsFromTemplate(goalTemplateId: UUID, weekStart: LocalDate): List<WeeklyGoalEntity>

    @Query("UPDATE weekly_goal SET targetValue = :value, completedAt = :completedAt WHERE id = :id")
    suspend fun retargetGoal(id: UUID, value: Int, completedAt: Instant?)
}
