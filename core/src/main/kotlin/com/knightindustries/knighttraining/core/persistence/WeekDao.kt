package com.knightindustries.knighttraining.core.persistence

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.knightindustries.knighttraining.core.persistence.entities.WeeklyGoalEntity
import com.knightindustries.knighttraining.core.persistence.entities.WeeklyPlanEntity
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class GoalPlanLink(val id: UUID, val weeklyPlanId: UUID)

data class GoalAccountingRow(val weeklyPlanId: UUID, val targetType: String)

/** Committed weeks and their goals. */
@Dao
interface WeekDao {
    /**
     * Insert-or-update, never `REPLACE`, whose delete would cascade away the week's goals and
     * review. Explicit SQL rather than `@Upsert`: Room's upsert answers *any* unique conflict with
     * an update by id, so a new id for an already-committed week silently did nothing. Here only
     * an `id` conflict updates, and a `weekStart` conflict throws, as on iOS.
     */
    @Query(
        """
        INSERT INTO weekly_plan (id, planTemplateId, weekStart, createdAt)
        VALUES (:id, :planTemplateId, :weekStart, :createdAt)
        ON CONFLICT(id) DO UPDATE SET
            planTemplateId = excluded.planTemplateId, weekStart = excluded.weekStart, createdAt = excluded.createdAt
        """,
    )
    suspend fun upsertPlan(id: UUID, planTemplateId: UUID, weekStart: LocalDate, createdAt: Instant)

    suspend fun upsertPlan(plan: WeeklyPlanEntity) = upsertPlan(plan.id, plan.planTemplateId, plan.weekStart, plan.createdAt)

    @Query("SELECT * FROM weekly_plan WHERE id = :id")
    suspend fun plan(id: UUID): WeeklyPlanEntity?

    @Query("SELECT * FROM weekly_plan WHERE weekStart = :weekStart")
    suspend fun plan(weekStart: LocalDate): WeeklyPlanEntity?

    @Query("SELECT * FROM weekly_plan ORDER BY weekStart DESC LIMIT :limit")
    suspend fun plans(limit: Int): List<WeeklyPlanEntity>

    @Query("SELECT COUNT(*) FROM weekly_plan WHERE weekStart <= :weekStart")
    suspend fun committedWeekCount(weekStart: LocalDate): Int

    @Query("DELETE FROM weekly_plan WHERE id = :id")
    suspend fun deletePlan(id: UUID)

    // MARK: - Goals

    /** Insert-or-update, never `REPLACE`, whose delete would detach every activity crediting it. */
    @Upsert
    suspend fun upsertGoal(goal: WeeklyGoalEntity)

    @Query("SELECT * FROM weekly_goal WHERE id = :id")
    suspend fun goal(id: UUID): WeeklyGoalEntity?

    /** In creation order (`rowid`), as iOS reads them. */
    @Query("SELECT * FROM weekly_goal WHERE weeklyPlanId IN (:planIds) ORDER BY rowid")
    suspend fun goals(planIds: List<UUID>): List<WeeklyGoalEntity>

    @Query("SELECT id, weeklyPlanId FROM weekly_goal WHERE id IN (:goalIds)")
    suspend fun planLinks(goalIds: List<UUID>): List<GoalPlanLink>

    @Query("SELECT weeklyPlanId, targetType FROM weekly_goal WHERE id = :goalId")
    suspend fun accounting(goalId: UUID): GoalAccountingRow?

    /** Bonus goals never count as outstanding. */
    @Query("SELECT COUNT(*) FROM weekly_goal WHERE weeklyPlanId = :planId AND completedAt IS NULL AND isBonus = 0")
    suspend fun incompleteCount(planId: UUID): Int

    @Query("SELECT COUNT(*) FROM weekly_goal WHERE weeklyPlanId = :planId AND isBonus = 0")
    suspend fun nonBonusGoalCount(planId: UUID): Int

    /**
     * Adds [delta] to the stored value (never to one the UI captured earlier) and stamps
     * `completedAt` once the target is cleared. `COALESCE` keeps the moment it was first hit.
     */
    @Query(
        """
        UPDATE weekly_goal
        SET currentValue = currentValue + :delta,
            completedAt = COALESCE(completedAt, CASE WHEN currentValue + :delta >= targetValue THEN :now END)
        WHERE id = :goalId
        """,
    )
    suspend fun advance(goalId: UUID, delta: Int, now: Instant)

    /** For corrections only: a goal an edit took back under its target is no longer completed. */
    @Query("UPDATE weekly_goal SET completedAt = NULL WHERE id = :goalId AND currentValue < targetValue")
    suspend fun reopenIfBelowTarget(goalId: UUID)

    @Query("UPDATE weekly_goal SET currentValue = :value, completedAt = COALESCE(completedAt, :completedAt) WHERE id = :goalId")
    suspend fun setProgress(goalId: UUID, value: Int, completedAt: Instant?)

    /** Upward only, checked inside the write so a stale UI value or a double tap can't lower it. */
    @Query("UPDATE weekly_goal SET targetValue = :target WHERE id = :goalId AND targetValue < :target")
    suspend fun raiseTarget(goalId: UUID, target: Int)

    @Query("DELETE FROM weekly_goal WHERE id = :id")
    suspend fun deleteGoal(id: UUID)

    @Query("DELETE FROM weekly_goal WHERE weeklyPlanId = :planId AND goalTemplateId = :goalTemplateId")
    suspend fun deleteGoal(planId: UUID, goalTemplateId: UUID)

    @Query(
        """
        UPDATE weekly_goal
        SET name = :name, activityTypeId = :activityTypeId, resourceId = :resourceId, targetType = :targetType,
            targetValue = :targetValue, timeControlLabel = :timeControlLabel, completedAt = :completedAt
        WHERE id = :id
        """,
    )
    suspend fun updateGoal(
        id: UUID,
        name: String?,
        activityTypeId: UUID,
        resourceId: UUID?,
        targetType: String,
        targetValue: Int,
        timeControlLabel: String?,
        completedAt: Instant?,
    )

    // MARK: - Streaks

    /** Weeks whose every non-bonus goal is complete, newest first. */
    @Query(
        """
        SELECT wp.weekStart FROM weekly_plan wp
        WHERE NOT EXISTS (
            SELECT 1 FROM weekly_goal wg
            WHERE wg.weeklyPlanId = wp.id AND wg.completedAt IS NULL AND wg.isBonus = 0
        )
        ORDER BY wp.weekStart DESC
        """,
    )
    suspend fun completedWeekStarts(): List<LocalDate>

    /** Every committed week and whether its non-bonus goals are all complete. */
    @Query(
        """
        SELECT wp.weekStart, NOT EXISTS (
            SELECT 1 FROM weekly_goal wg
            WHERE wg.weeklyPlanId = wp.id AND wg.completedAt IS NULL AND wg.isBonus = 0
        ) AS isComplete
        FROM weekly_plan wp
        """,
    )
    suspend fun weekCompletions(): List<WeekCompletion>
}

data class WeekCompletion(val weekStart: LocalDate, val isComplete: Boolean)
