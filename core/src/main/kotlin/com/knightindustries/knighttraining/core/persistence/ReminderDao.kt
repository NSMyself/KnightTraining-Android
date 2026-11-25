package com.knightindustries.knighttraining.core.persistence

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.knightindustries.knighttraining.core.persistence.entities.ActivityReminderEntity
import java.util.UUID

@Dao
interface ReminderDao {
    @Query("SELECT * FROM activity_reminder WHERE goalTemplateId = :goalTemplateId ORDER BY createdAt")
    suspend fun reminders(goalTemplateId: UUID): List<ActivityReminderEntity>

    @Query("SELECT * FROM activity_reminder")
    suspend fun all(): List<ActivityReminderEntity>

    @Query("SELECT * FROM activity_reminder WHERE id = :id")
    suspend fun reminder(id: UUID): ActivityReminderEntity?

    /** `id` is the table's only unique key, so Room's upsert is safe here (AGENTS.md). */
    @Upsert
    suspend fun upsert(reminder: ActivityReminderEntity)

    @Query("DELETE FROM activity_reminder WHERE id = :id")
    suspend fun delete(id: UUID)
}
