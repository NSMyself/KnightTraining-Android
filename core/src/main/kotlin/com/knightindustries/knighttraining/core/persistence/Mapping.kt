package com.knightindustries.knighttraining.core.persistence

import com.knightindustries.knighttraining.core.model.Account
import com.knightindustries.knighttraining.core.model.Activity
import com.knightindustries.knighttraining.core.model.ActivitySource
import com.knightindustries.knighttraining.core.model.ActivityType
import com.knightindustries.knighttraining.core.model.CoachingTopic
import com.knightindustries.knighttraining.core.model.EloSnapshot
import com.knightindustries.knighttraining.core.model.GoalTemplate
import com.knightindustries.knighttraining.core.model.LichessActivityBatch
import com.knightindustries.knighttraining.core.model.Milestone
import com.knightindustries.knighttraining.core.model.PlanTemplate
import com.knightindustries.knighttraining.core.model.Resource
import com.knightindustries.knighttraining.core.model.ResourceKind
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import com.knightindustries.knighttraining.core.persistence.entities.AccountEntity
import com.knightindustries.knighttraining.core.persistence.entities.ActivityEntity
import com.knightindustries.knighttraining.core.persistence.entities.EloSnapshotEntity
import com.knightindustries.knighttraining.core.persistence.entities.GoalTemplateEntity
import com.knightindustries.knighttraining.core.persistence.entities.LichessActivityBatchEntity
import com.knightindustries.knighttraining.core.persistence.entities.MilestoneEntity
import com.knightindustries.knighttraining.core.persistence.entities.PlanTemplateEntity
import com.knightindustries.knighttraining.core.persistence.entities.ResourceEntity
import com.knightindustries.knighttraining.core.persistence.entities.WeeklyGoalEntity
import com.knightindustries.knighttraining.core.persistence.entities.WeeklyPlanEntity
import java.util.UUID

/**
 * Entity ↔ domain conversion. Lookup rows are seeded once and never change, so activity types and
 * resource kinds resolve from [Presets] in memory instead of a join; the foreign keys guarantee
 * every stored id is one of them.
 */
internal object Lookups {
    val activityTypes: Map<UUID, ActivityType> = Presets.activityTypes.associate { it.id to ActivityType(it.id, it.code) }
    val resourceKinds: Map<UUID, ResourceKind> = Presets.resourceKinds.associate { it.id to ResourceKind(it.id, it.code) }

    fun activityType(id: UUID): ActivityType = activityTypes.getValue(id)

    fun activityType(code: String): ActivityType? = activityTypes.values.firstOrNull { it.code == code }

    fun resourceKind(id: UUID): ResourceKind = resourceKinds.getValue(id)

    fun resourceKind(code: String): ResourceKind? = resourceKinds.values.firstOrNull { it.code == code }
}

/** An unknown stored code is a bug, so fail loudly rather than guess. */
private fun targetType(code: String): TargetType =
    TargetType.fromCode(code) ?: error("Unknown targetType '$code'")

private fun source(code: String): ActivitySource =
    ActivitySource.fromCode(code) ?: error("Unknown activity source '$code'")

internal fun ResourceEntity.toDomain() = Resource(
    id = id, kind = Lookups.resourceKind(kindId), title = title, author = author, url = url, imageUrl = imageUrl,
    description = description, isOwned = isOwned, isAutoImported = isAutoImported,
    progressPercentage = progressPercentage, completionCount = completionCount, catalogCourseId = catalogCourseId,
    notes = notes, edition = edition, publisher = publisher, publicationDate = publicationDate,
    createdAt = createdAt, updatedAt = updatedAt, lastAccessedAt = lastAccessedAt,
)

internal fun Resource.toEntity() = ResourceEntity(
    id = id, kindId = kind.id, title = title, author = author, url = url, description = description,
    imageUrl = imageUrl, isOwned = isOwned, progressPercentage = progressPercentage, completionCount = completionCount,
    catalogCourseId = catalogCourseId, notes = notes, edition = edition, publisher = publisher,
    publicationDate = publicationDate, lastAccessedAt = lastAccessedAt, createdAt = createdAt, updatedAt = updatedAt,
    isAutoImported = isAutoImported,
)

internal fun ActivityEntity.toDomain(resources: Map<UUID, Resource>) = Activity(
    id = id, activityType = Lookups.activityType(activityTypeId), resource = resourceId?.let(resources::get),
    startedAt = startedAt, endedAt = endedAt, duration = duration.toInt(), count = count, source = source(source),
    externalId = externalId, notes = notes, sessionId = sessionId, timeControlLabel = timeControlLabel,
    weeklyPlanId = weeklyPlanId, weeklyGoalId = weeklyGoalId, coachingTopics = CoachingTopic.decode(coachingTopics),
    createdAt = createdAt,
)

internal fun Activity.toEntity() = ActivityEntity(
    id = id, activityTypeId = activityType.id, resourceId = resource?.id, startedAt = startedAt, endedAt = endedAt,
    duration = duration.toLong(), count = count, source = source.code, externalId = externalId, notes = notes,
    createdAt = createdAt, sessionId = sessionId, timeControlLabel = timeControlLabel, weeklyPlanId = weeklyPlanId,
    weeklyGoalId = weeklyGoalId, coachingTopics = CoachingTopic.encode(coachingTopics),
)

internal fun GoalTemplateEntity.toDomain(resources: Map<UUID, Resource>) = GoalTemplate(
    id = id, planTemplateId = planTemplateId, name = name, activityType = Lookups.activityType(activityTypeId),
    resource = resourceId?.let(resources::get), targetType = targetType(targetType), targetValue = targetValue,
    timeControlLabel = timeControlLabel, createdAt = createdAt,
)

internal fun GoalTemplate.toEntity() = GoalTemplateEntity(
    id = id, planTemplateId = planTemplateId, name = name, activityTypeId = activityType.id, resourceId = resource?.id,
    targetType = targetType.code, targetValue = targetValue, createdAt = createdAt, timeControlLabel = timeControlLabel,
)

internal fun PlanTemplateEntity.toDomain(goals: List<GoalTemplate>) = PlanTemplate(
    id = id, name = name, isActive = isActive, isArchived = isArchived, goalTemplates = goals,
    createdAt = createdAt, updatedAt = updatedAt,
)

internal fun PlanTemplate.toEntity() = PlanTemplateEntity(
    id = id, name = name, isActive = isActive, createdAt = createdAt, updatedAt = updatedAt, isArchived = isArchived,
)

internal fun WeeklyGoalEntity.toDomain(resources: Map<UUID, Resource>) = WeeklyGoal(
    id = id, weeklyPlanId = weeklyPlanId, name = name, activityType = Lookups.activityType(activityTypeId),
    resource = resourceId?.let(resources::get), targetType = targetType(targetType), targetValue = targetValue,
    currentValue = currentValue, timeControlLabel = timeControlLabel, completedAt = completedAt,
    goalTemplateId = goalTemplateId, isBonus = isBonus,
)

internal fun WeeklyGoal.toEntity() = WeeklyGoalEntity(
    id = id, weeklyPlanId = weeklyPlanId, name = name, activityTypeId = activityType.id, resourceId = resource?.id,
    targetType = targetType.code, targetValue = targetValue, currentValue = currentValue, completedAt = completedAt,
    timeControlLabel = timeControlLabel, goalTemplateId = goalTemplateId, isBonus = isBonus,
)

internal fun WeeklyPlanEntity.toDomain(goals: List<WeeklyGoal>) = WeeklyPlan(
    id = id, planTemplateId = planTemplateId, weekStart = weekStart, weeklyGoals = goals, createdAt = createdAt,
)

internal fun WeeklyPlan.toEntity() = WeeklyPlanEntity(
    id = id, planTemplateId = planTemplateId, weekStart = weekStart, createdAt = createdAt,
)

internal fun AccountEntity.toDomain() = Account(
    id, platform, username, title, flag, lastGameSyncAt, lastPuzzleSyncAt, lastStormDayId, lastStormRunsForDay,
    lastStormSecondsForDay, lastStreakRuns, lastStreakScore, lastStreakSyncAt, createdAt, updatedAt,
)

/** Tokens stay out of the database on Android (see [Account]); the columns are always written empty. */
internal fun Account.toEntity() = AccountEntity(
    id = id, platform = platform, username = username, accessToken = null, refreshToken = null, tokenExpiresAt = null,
    createdAt = createdAt, updatedAt = updatedAt, title = title, lastGameSyncAt = lastGameSyncAt,
    lastPuzzleSyncAt = lastPuzzleSyncAt, lastStormDayId = lastStormDayId, lastStormRunsForDay = lastStormRunsForDay,
    lastStormSecondsForDay = lastStormSecondsForDay, lastStreakRuns = lastStreakRuns, lastStreakScore = lastStreakScore,
    lastStreakSyncAt = lastStreakSyncAt, flag = flag,
)

internal fun EloSnapshot.toEntity() = EloSnapshotEntity(id, accountId, rating, ratingType, capturedAt)

internal fun EloSnapshotEntity.toDomain() = EloSnapshot(id, accountId, rating, ratingType, capturedAt)

internal fun MilestoneEntity.toDomain() = Milestone(id, kind, ratingType, targetRating, targetTitle, text, completedAt, createdAt)

internal fun Milestone.toEntity() = MilestoneEntity(id, kind, ratingType, targetRating, targetTitle, text, completedAt, createdAt)

internal fun LichessActivityBatchEntity.toDomain() = LichessActivityBatch(
    id = id, accountId = accountId, kind = kind, category = category,
    externalIds = externalIds.split(',').filter { it.isNotEmpty() }, itemCount = itemCount,
    totalDurationSeconds = totalDurationSeconds, timeControlLabel = timeControlLabel, firstItemAt = firstItemAt,
    lastItemAt = lastItemAt, bestScore = bestScore, status = status, activityId = activityId, createdAt = createdAt,
)

internal fun LichessActivityBatch.toEntity() = LichessActivityBatchEntity(
    id = id, accountId = accountId, kind = kind, category = category, externalIds = externalIds.joinToString(","),
    itemCount = itemCount, totalDurationSeconds = totalDurationSeconds, timeControlLabel = timeControlLabel,
    firstItemAt = firstItemAt, lastItemAt = lastItemAt, status = status, activityId = activityId, createdAt = createdAt,
    bestScore = bestScore,
)
