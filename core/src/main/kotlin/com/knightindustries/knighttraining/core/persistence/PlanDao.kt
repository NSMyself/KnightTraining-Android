package com.knightindustries.knighttraining.core.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.knightindustries.knighttraining.core.persistence.entities.GoalTemplateEntity
import com.knightindustries.knighttraining.core.persistence.entities.PlanTemplateEntity
import java.time.Instant
import java.util.UUID

/**
 * Plan and goal templates. Every read filters archived (deleted) templates, so "deleted" means the
 * same thing wherever a template is listed or looked up by id.
 */
@Dao
interface PlanDao {
    /** Insert-or-update, never `REPLACE`, whose delete would cascade away every committed week. */
    @Upsert
    suspend fun upsertTemplate(template: PlanTemplateEntity)

    @Query("SELECT * FROM plan_template WHERE id = :id AND isArchived = 0")
    suspend fun template(id: UUID): PlanTemplateEntity?

    @Query("SELECT * FROM plan_template WHERE isActive = 1 AND isArchived = 0 LIMIT 1")
    suspend fun activeTemplate(): PlanTemplateEntity?

    @Query("SELECT * FROM plan_template WHERE isArchived = 0 ORDER BY updatedAt DESC")
    suspend fun allTemplates(): List<PlanTemplateEntity>

    @Query("SELECT COUNT(*) FROM plan_template WHERE isArchived = 0")
    suspend fun templateCount(): Int

    /** The name stored on a template, archived or not: a committed week keeps its plan's name. */
    @Query("SELECT name FROM plan_template WHERE id = :id")
    suspend fun templateName(id: UUID): String?

    @Query("SELECT name FROM plan_template WHERE isArchived = 0 AND (:excludingId IS NULL OR id != :excludingId)")
    suspend fun visibleTemplateNames(excludingId: UUID?): List<String?>

    @Query("UPDATE plan_template SET isActive = 0, updatedAt = :now")
    suspend fun deactivateAll(now: Instant)

    @Query("UPDATE plan_template SET isActive = 1, updatedAt = :now WHERE id = :id")
    suspend fun activate(id: UUID, now: Instant)

    @Query("UPDATE plan_template SET isArchived = 1, isActive = 0, updatedAt = :now WHERE id = :id")
    suspend fun archive(id: UUID, now: Instant)

    @Query("UPDATE plan_template SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: UUID, name: String, now: Instant)

    /** Destroys every week committed from this template. No UI path; see `archive`. */
    @Query("DELETE FROM plan_template WHERE id = :id")
    suspend fun deleteTemplate(id: UUID)

    // MARK: - Goal templates

    /** Insert-or-update, never `REPLACE`, whose delete would cascade away the block's reminders. */
    @Upsert
    suspend fun upsertGoalTemplate(goal: GoalTemplateEntity)

    @Insert
    suspend fun insertGoalTemplate(goal: GoalTemplateEntity)

    @Query("SELECT * FROM goal_template WHERE id = :id")
    suspend fun goalTemplate(id: UUID): GoalTemplateEntity?

    /** In creation order (`rowid`), as iOS reads them. */
    @Query("SELECT * FROM goal_template WHERE planTemplateId IN (:planIds) ORDER BY rowid")
    suspend fun goalTemplates(planIds: List<UUID>): List<GoalTemplateEntity>

    @Query("SELECT id FROM goal_template WHERE planTemplateId = :planId")
    suspend fun goalTemplateIds(planId: UUID): List<UUID>

    @Query(
        """
        UPDATE goal_template
        SET name = :name, activityTypeId = :activityTypeId, resourceId = :resourceId,
            targetType = :targetType, targetValue = :targetValue, timeControlLabel = :timeControlLabel
        WHERE id = :id
        """,
    )
    suspend fun updateGoalTemplate(
        id: UUID,
        name: String?,
        activityTypeId: UUID,
        resourceId: UUID?,
        targetType: String,
        targetValue: Int,
        timeControlLabel: String?,
    )

    @Query("DELETE FROM goal_template WHERE id = :id")
    suspend fun deleteGoalTemplate(id: UUID)
}
