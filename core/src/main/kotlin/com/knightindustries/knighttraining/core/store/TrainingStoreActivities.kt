package com.knightindustries.knighttraining.core.store

import com.knightindustries.knighttraining.core.model.Activity
import com.knightindustries.knighttraining.core.model.Resource
import com.knightindustries.knighttraining.core.model.WeekCalendar
import com.knightindustries.knighttraining.core.persistence.entities.ActivityEntity
import com.knightindustries.knighttraining.core.persistence.toDomain
import com.knightindustries.knighttraining.core.persistence.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

internal suspend fun TrainingStore.toActivities(entities: List<ActivityEntity>): List<Activity> {
    val resources = resourcesById(entities.map { it.resourceId })
    return entities.map { it.toDomain(resources) }
}

suspend fun TrainingStore.saveActivity(activity: Activity) = activityDao.upsert(activity.toEntity())

suspend fun TrainingStore.activity(id: UUID): Activity? =
    read { activityDao.byId(id)?.let { toActivities(listOf(it)).single() } }

/** Every activity that started in `[start, end)`, newest first, ongoing ones included. */
suspend fun TrainingStore.activities(start: Instant, end: Instant): List<Activity> =
    read { toActivities(activityDao.startedBetween(start, end)) }

/**
 * Live-updates whenever an activity (or a resource it shows) changes: a week's Activity Log, which
 * stays mounted while sessions are logged elsewhere.
 */
fun TrainingStore.observeActivities(start: Instant, end: Instant): Flow<List<Activity>> =
    db.invalidationTracker.createFlow("activity", "resource")
        .map { activities(start, end) }
        .distinctUntilChanged()

/**
 * Completed activities that ended within the last [days] days of [from], most recently ended
 * first. Every one, however short: there's no minimum-duration rule (iOS AGENTS.md §7).
 */
suspend fun TrainingStore.recentCompletedActivities(from: Instant = now(), days: Int = 10): List<Activity> =
    read { recentCompletedActivitiesInTransaction(from, days) }

internal suspend fun TrainingStore.recentCompletedActivitiesInTransaction(from: Instant, days: Int): List<Activity> {
    val calendar = calendar()
    // Calendar days back, like iOS's `date(byAdding: .day, value: -days)`, so a DST change doesn't
    // shift the window by an hour.
    val since = from.atZone(calendar.zone).minusDays(days.toLong()).toInstant()
    return toActivities(activityDao.completedEndingSince(since))
}

/**
 * Whether anything was logged on [day], ongoing sessions included. No minimum duration: someone
 * who put in ten minutes has trained today, and reminding them would be nagging.
 */
suspend fun TrainingStore.hasActivity(day: LocalDate, calendar: WeekCalendar = calendar()): Boolean =
    activityDao.countStartedBetween(calendar.startOf(day), calendar.startOf(day.plusDays(1))) > 0

/** Debug menu only: clears a day so the reminder gate can be exercised again. Leaves goal progress alone. */
suspend fun TrainingStore.deleteActivities(day: LocalDate, calendar: WeekCalendar = calendar()): Int =
    activityDao.deleteStartedBetween(calendar.startOf(day), calendar.startOf(day.plusDays(1)))

/** Removes only the row. To take back what it gave its goal, use [deleteLoggedActivity]. */
suspend fun TrainingStore.deleteActivity(id: UUID) = activityDao.delete(id)

/** Lifetime seconds across every completed session, however short. */
suspend fun TrainingStore.totalDuration(): Int = activityDao.totalCompletedDuration().toInt()

suspend fun TrainingStore.totalDuration(start: Instant, end: Instant): Int =
    activityDao.totalDurationBetween(start, end).toInt()

suspend fun TrainingStore.hasActivity(activityTypeCode: String): Boolean = activityDao.hasActivityOfType(activityTypeCode)

// MARK: - Ongoing

suspend fun TrainingStore.findOngoingActivityId(): UUID? = activityDao.ongoingId()

suspend fun TrainingStore.findOngoingActivity(): Activity? = findOngoingActivityId()?.let { activity(it) }

/** Ends any ongoing activity now, with its elapsed time as the duration. */
suspend fun TrainingStore.completeOngoingActivityIfNeeded() {
    val ongoing = findOngoingActivity() ?: return
    val end = now()
    activityDao.complete(ongoing.id, end, ChronoUnit.SECONDS.between(ongoing.startedAt, end))
}

// MARK: - Resources (the core loop's share; the library is Phase 3)

suspend fun TrainingStore.saveResource(resource: Resource) = resourceDao.upsert(resource.toEntity())

/** A bulk import (a playlist's videos) in one transaction: one surface update, not one per row. */
suspend fun TrainingStore.saveResources(resources: List<Resource>) = write { resources.forEach { resourceDao.upsert(it.toEntity()) } }

suspend fun TrainingStore.resource(id: UUID): Resource? = resourceDao.byId(id)?.toDomain()

/** For freely draggable controls: clamps to 0–100 and never counts a completion. */
suspend fun TrainingStore.setResourceProgress(id: UUID, percentage: Int) =
    resourceDao.setProgress(id, percentage.coerceIn(0, 100), now())

/** "The user was here" after a session: recency only. */
suspend fun TrainingStore.touchResourceAccessed(id: UUID) = resourceDao.touch(id, now())
