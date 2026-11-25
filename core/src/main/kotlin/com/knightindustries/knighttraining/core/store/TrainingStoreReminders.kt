package com.knightindustries.knighttraining.core.store

import com.knightindustries.knighttraining.core.model.ActivityReminder
import com.knightindustries.knighttraining.core.model.ReminderSchedule
import com.knightindustries.knighttraining.core.persistence.entities.ActivityReminderEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.UUID

private fun ActivityReminderEntity.toDomain() = ActivityReminder(
    id, goalTemplateId, ReminderSchedule(isEnabled, ActivityReminder.decode(weekdays), hour, minute), createdAt,
)

private fun ActivityReminder.toEntity() = ActivityReminderEntity(
    id, goalTemplateId, ActivityReminder.encode(schedule.weekdays), schedule.hour, schedule.minute, schedule.isEnabled, createdAt,
)

/** One block's reminders, oldest first (iOS `activityReminders(goalTemplateId:)`). */
suspend fun TrainingStore.activityReminders(goalTemplateId: UUID): List<ActivityReminder> =
    db.reminderDao().reminders(goalTemplateId).map { it.toDomain() }

fun TrainingStore.observeActivityReminders(goalTemplateId: UUID): Flow<List<ActivityReminder>> =
    db.invalidationTracker.createFlow("activity_reminder").map { activityReminders(goalTemplateId) }.distinctUntilChanged()

/** Every block's reminders: rescheduled after a reboot or clock change, cancelled by "Delete all data". */
suspend fun TrainingStore.allActivityReminders(): List<ActivityReminder> = db.reminderDao().all().map { it.toDomain() }

suspend fun TrainingStore.activityReminder(id: UUID): ActivityReminder? = db.reminderDao().reminder(id)?.toDomain()

/** Adds a reminder, or edits the one with the same id in place. */
suspend fun TrainingStore.saveActivityReminder(reminder: ActivityReminder) = db.reminderDao().upsert(reminder.toEntity())

suspend fun TrainingStore.deleteActivityReminder(id: UUID) = db.reminderDao().delete(id)
