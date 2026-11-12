package com.knightindustries.knighttraining.core.persistence.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** "How I train." Evolves freely; changes apply to future weeks only. */
@Entity(tableName = "plan_template")
data class PlanTemplateEntity(
    @PrimaryKey val id: UUID,
    val name: String?,
    @ColumnInfo(defaultValue = "0") val isActive: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** "Delete Plan" archives rather than deletes, since `weekly_plan` cascades from this row. */
    @ColumnInfo(defaultValue = "0") val isArchived: Boolean = false,
)

/** A recurring goal within a plan template. */
@Entity(
    tableName = "goal_template",
    foreignKeys = [
        ForeignKey(
            entity = PlanTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["planTemplateId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ActivityTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["activityTypeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ResourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["resourceId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["planTemplateId"]),
        Index(value = ["activityTypeId"]),
        Index(value = ["resourceId"]),
    ],
)
data class GoalTemplateEntity(
    @PrimaryKey val id: UUID,
    val planTemplateId: UUID,
    val name: String?,
    val activityTypeId: UUID,
    val resourceId: UUID?,
    /** `count` | `duration` | `progress`. */
    val targetType: String,
    val targetValue: Int,
    val createdAt: Instant,
    /** The picker's label (`"60 min"`, `"3+2"`), classified on read. Playing only. */
    val timeControlLabel: String?,
)

/** An immutable snapshot of a plan template for one week. */
@Entity(
    tableName = "weekly_plan",
    foreignKeys = [
        ForeignKey(
            entity = PlanTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["planTemplateId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["weekStart"], unique = true),
        Index(value = ["planTemplateId"]),
    ],
)
data class WeeklyPlanEntity(
    @PrimaryKey val id: UUID,
    val planTemplateId: UUID,
    val weekStart: LocalDate,
    val createdAt: Instant,
)

/** The week's concrete goal, frozen from its template. */
@Entity(
    tableName = "weekly_goal",
    foreignKeys = [
        ForeignKey(
            entity = WeeklyPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["weeklyPlanId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ActivityTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["activityTypeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ResourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["resourceId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = GoalTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalTemplateId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["weeklyPlanId"]),
        Index(value = ["completedAt"]),
        Index(value = ["goalTemplateId"]),
        Index(value = ["activityTypeId"]),
        Index(value = ["resourceId"]),
    ],
)
data class WeeklyGoalEntity(
    @PrimaryKey val id: UUID,
    val weeklyPlanId: UUID,
    val name: String?,
    val activityTypeId: UUID,
    val resourceId: UUID?,
    val targetType: String,
    val targetValue: Int,
    @ColumnInfo(defaultValue = "0") val currentValue: Int = 0,
    /** Set when the target is hit; a one-way ratchet. */
    val completedAt: Instant?,
    val timeControlLabel: String?,
    /** The template this goal was copied from, so editing a committed week keeps progress. */
    val goalTemplateId: UUID?,
    /** Added mid-week by "add more"; never counts toward "is this week done". */
    @ColumnInfo(defaultValue = "0") val isBonus: Boolean = false,
)

/** The end-of-week reflection, one per weekly plan. */
@Entity(
    tableName = "weekly_review",
    foreignKeys = [
        ForeignKey(
            entity = WeeklyPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["weeklyPlanId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["weeklyPlanId"], unique = true)],
)
data class WeeklyReviewEntity(
    @PrimaryKey val id: UUID,
    val weeklyPlanId: UUID,
    val wentWell: String?,
    val heldBack: String?,
    val nextWeekIntent: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val wishIDone: String?,
    /** The coach's focus phrase, stored in the language it was written in (AGENTS.md §14). */
    val nextWeekFocus: String?,
    val nextWeekTargetCode: String?,
    val nextWeekTargetValue: Int?,
)

/** A per-block reminder schedule. Lives on the template so it persists across weeks. */
@Entity(
    tableName = "activity_reminder",
    foreignKeys = [
        ForeignKey(
            entity = GoalTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalTemplateId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["goalTemplateId"])],
)
data class ActivityReminderEntity(
    @PrimaryKey val id: UUID,
    val goalTemplateId: UUID,
    val weekdays: String,
    val hour: Int,
    val minute: Int,
    @ColumnInfo(defaultValue = "1") val isEnabled: Boolean = true,
    val createdAt: Instant,
)
