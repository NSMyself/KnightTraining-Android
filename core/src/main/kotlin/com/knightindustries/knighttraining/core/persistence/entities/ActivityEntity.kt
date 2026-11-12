package com.knightindustries.knighttraining.core.persistence.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/** The atomic "I trained" record. `endedAt == null` means the activity is ongoing. */
@Entity(
    tableName = "activity",
    foreignKeys = [
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
            entity = WeeklyPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["weeklyPlanId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = WeeklyGoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["weeklyGoalId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["startedAt"]),
        Index(value = ["activityTypeId"]),
        Index(value = ["resourceId"]),
        Index(value = ["sessionId"]),
        Index(value = ["weeklyPlanId"]),
        Index(value = ["weeklyGoalId"]),
    ],
)
data class ActivityEntity(
    @PrimaryKey val id: UUID,
    val activityTypeId: UUID,
    val resourceId: UUID?,
    val startedAt: Instant,
    val endedAt: Instant?,
    /** Seconds. */
    val duration: Long,
    val count: Int?,
    /** `manual` | `lichess_sync`. */
    val source: String,
    /** Dedup key for synced rows. */
    val externalId: String?,
    val notes: String?,
    val createdAt: Instant,
    /** Groups one practice run's segments. Written, currently unread; keep it (AGENTS.md §7). */
    val sessionId: UUID?,
    val timeControlLabel: String?,
    /** Which committed week this counted toward, even if its goal is later edited away. */
    val weeklyPlanId: UUID?,
    /** The exact goal this advanced, so an edit can apply an exact delta. */
    val weeklyGoalId: UUID?,
    /** Comma-separated `CoachingTopic` codes. Coaching only, never part of goal crediting. */
    val coachingTopics: String?,
)
