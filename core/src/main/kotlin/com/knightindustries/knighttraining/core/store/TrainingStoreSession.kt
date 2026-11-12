package com.knightindustries.knighttraining.core.store

import com.knightindustries.knighttraining.core.model.Activity
import com.knightindustries.knighttraining.core.model.ActivityType
import com.knightindustries.knighttraining.core.model.CoachingTopic
import com.knightindustries.knighttraining.core.model.Resource
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.persistence.entities.GoalTemplateEntity
import com.knightindustries.knighttraining.core.persistence.entities.WeeklyGoalEntity
import com.knightindustries.knighttraining.core.persistence.toEntity
import java.time.Instant
import java.util.UUID

/**
 * One segment of a practice session, ready to write: the activity, plus how much it moves the goal
 * it was attributed to. An input to a write, not an entity: there's still no session table.
 */
data class SessionSegmentLog(val activity: Activity, val goalProgress: GoalProgress? = null) {
    /**
     * @param delta seconds for a duration goal, items for a count goal, added to the goal's stored
     *   value at write time, never to one the UI captured earlier.
     */
    data class GoalProgress(val goalId: UUID, val delta: Int)
}

/**
 * Writes a whole practice session (every segment's activity, resource touch and goal advance) in
 * **one transaction**, so the weekly surface sees one change and never a state where the minutes
 * count toward the week but not the goal.
 *
 * Each activity's `weeklyPlanId`/`weeklyGoalId` are stamped here from the goal it advanced, never
 * trusted from the caller. Returns the weeks this completed, already broadcast.
 */
suspend fun TrainingStore.logSession(segments: List<SessionSegmentLog>): List<UUID> {
    if (segments.isEmpty()) return emptyList()
    val at = now()

    val completed = write {
        val goalToPlan = weekDao.planLinks(segments.mapNotNull { it.goalProgress?.goalId }.distinct())
            .associate { it.id to it.weeklyPlanId }

        // Sampled before anything lands: afterwards "was already complete" and "just became
        // complete" look the same, and the celebration fires only on the transition.
        val incompleteBefore = goalToPlan.values.toSet().associateWith { weekDao.incompleteCount(it) }

        for (segment in segments) {
            val goalId = segment.goalProgress?.goalId
            activityDao.upsert(segment.activity.copy(weeklyPlanId = goalId?.let(goalToPlan::get), weeklyGoalId = goalId).toEntity())
            segment.activity.resource?.let { resourceDao.touch(it.id, at) }

            val progress = segment.goalProgress ?: continue
            if (progress.delta > 0) weekDao.advance(progress.goalId, progress.delta, at)
        }

        incompleteBefore.filter { (planId, outstanding) -> outstanding > 0 && weekDao.incompleteCount(planId) == 0 }.keys.toList()
    }

    completed.forEach { emitWeeklyPlanCompleted(it) }
    return completed
}

// MARK: - Editing a logged session

/** A field left as stored, or set to a new value (which may itself be `null`). */
sealed interface Change<out T> {
    data object Unchanged : Change<Nothing>

    data class Set<T>(val value: T) : Change<T>
}

private fun <T> Change<T>.resolve(current: T): T = if (this is Change.Set) value else current

/**
 * What can be corrected on a logged activity. The later fields default to [Change.Unchanged], so
 * a duration-only correction leaves the rest as stored.
 */
data class ActivityEdit(
    val duration: Int,
    val count: Int? = null,
    val resource: Resource? = null,
    val notes: String? = null,
    val startedAt: Change<Instant> = Change.Unchanged,
    val timeControlLabel: Change<String?> = Change.Unchanged,
    val weeklyGoalId: Change<UUID?> = Change.Unchanged,
    val coachingTopics: Change<List<CoachingTopic>> = Change.Unchanged,
    val activityTypeId: Change<UUID> = Change.Unchanged,
)

private data class GoalAccounting(val id: UUID, val weeklyPlanId: UUID, val targetType: TargetType?) {
    /** What an activity gives this goal; `null` for a progress goal. */
    fun contribution(duration: Int, count: Int?): Int? = when (targetType) {
        TargetType.DURATION -> duration
        TargetType.COUNT -> count ?: 0
        else -> null
    }
}

private suspend fun TrainingStore.accounting(goalId: UUID): GoalAccounting? =
    weekDao.accounting(goalId)?.let { GoalAccounting(goalId, it.weeklyPlanId, TargetType.fromCode(it.targetType)) }

/**
 * Updates a logged activity and corrects the goal(s) it counts toward, in one transaction, through
 * the `weeklyGoalId` link, never a re-match by type or resource:
 * - Same goal: it moves by the difference on the dimension it tracks.
 * - Another goal (or none): the old one loses what this gave it, the new one gains what it gives
 *   now, and `weeklyPlanId` follows the new goal.
 *
 * A goal left below its target is no longer completed (an edit corrects a mistake); what its
 * completion already triggered isn't taken back. Returns the week this completed, if any.
 */
suspend fun TrainingStore.updateActivity(id: UUID, edit: ActivityEdit): UUID? {
    val at = now()

    val completedPlanId = write {
        val existing = activityDao.byId(id) ?: return@write null

        val oldGoalId = existing.weeklyGoalId
        val newGoalId = edit.weeklyGoalId.resolve(oldGoalId)
        val oldGoal = oldGoalId?.let { accounting(it) }
        val newGoal = newGoalId?.let { accounting(it) }

        val touchedPlans = listOfNotNull(oldGoal?.weeklyPlanId, newGoal?.weeklyPlanId).toSet()
        val incompleteBefore = touchedPlans.associateWith { weekDao.incompleteCount(it) }

        // Moving the start moves the end with it; otherwise the stored end stays.
        val startedAt = edit.startedAt.resolve(existing.startedAt)
        val endedAt = existing.endedAt?.let { if (startedAt == existing.startedAt) it else startedAt.plusSeconds(edit.duration.toLong()) }
        val goalChanged = newGoalId != oldGoalId

        activityDao.update(
            existing.copy(
                duration = edit.duration.toLong(),
                count = edit.count,
                resourceId = edit.resource?.id,
                notes = edit.notes,
                startedAt = startedAt,
                endedAt = endedAt,
                timeControlLabel = edit.timeControlLabel.resolve(existing.timeControlLabel),
                weeklyGoalId = newGoalId,
                weeklyPlanId = if (goalChanged) newGoal?.weeklyPlanId else existing.weeklyPlanId,
                coachingTopics = CoachingTopic.encode(edit.coachingTopics.resolve(CoachingTopic.decode(existing.coachingTopics))),
                activityTypeId = edit.activityTypeId.resolve(existing.activityTypeId),
            ),
        )

        val oldCount = existing.count
        val oldDuration = existing.duration.toInt()
        if (oldGoal != null && newGoal != null && oldGoal.id == newGoal.id) {
            val delta = (newGoal.contribution(edit.duration, edit.count) ?: 0) - (oldGoal.contribution(oldDuration, oldCount) ?: 0)
            if (delta != 0) weekDao.advance(newGoal.id, delta, at)
        } else {
            oldGoal?.contribution(oldDuration, oldCount)?.takeIf { it != 0 }?.let { weekDao.advance(oldGoal.id, -it, at) }
            newGoal?.contribution(edit.duration, edit.count)?.takeIf { it != 0 }?.let { weekDao.advance(newGoal.id, it, at) }
        }

        listOfNotNull(oldGoal, newGoal).forEach { weekDao.reopenIfBelowTarget(it.id) }

        touchedPlans.firstOrNull { (incompleteBefore[it] ?: 0) > 0 && weekDao.incompleteCount(it) == 0 }
    }

    completedPlanId?.let { emitWeeklyPlanCompleted(it) }
    return completedPlanId
}

/**
 * Deletes a logged session and takes back what it gave its goal, reopening the goal if that drops
 * it below target. [deleteActivity] removes only the row.
 */
suspend fun TrainingStore.deleteLoggedActivity(id: UUID) {
    val at = now()
    write {
        val existing = activityDao.byId(id) ?: return@write
        existing.weeklyGoalId?.let { accounting(it) }?.let { goal ->
            goal.contribution(existing.duration.toInt(), existing.count)?.takeIf { it != 0 }?.let { weekDao.advance(goal.id, -it, at) }
            weekDao.reopenIfBelowTarget(goal.id)
        }
        activityDao.delete(id)
    }
}

// MARK: - Editing a committed week's plan

/**
 * How many real sessions have been logged against a week: distinct sessions stamped with it, even
 * if the goal each advanced was later edited away. Compare with
 * [com.knightindustries.knighttraining.core.model.WeeklyPlan.EDITABLE_SESSION_LIMIT].
 */
suspend fun TrainingStore.qualifyingSessionCount(weeklyPlanId: UUID): Int = activityDao.sessionCount(weeklyPlanId)

/**
 * One training block as the edit screen writes it. [id] is the existing goal template's id, or
 * `null` for a new block: the only thing that says "same block, retargeted", never inferred from
 * matching type or resource, which two different blocks can share.
 */
data class GoalEdit(
    val id: UUID? = null,
    val name: String? = null,
    val activityType: ActivityType,
    val resource: Resource? = null,
    val targetType: TargetType,
    val targetValue: Int,
    val timeControlLabel: String? = null,
)

/**
 * Applies [blocks] as the template's full set of goal templates and, when [weeklyPlanId] names
 * the committed week, keeps its goals in step in the same transaction.
 *
 * A kept block is an `UPDATE`, never a delete-and-reinsert: its weekly goal (found through
 * `goalTemplateId`) keeps `currentValue`, with `completedAt` re-derived against the new target.
 * New blocks insert both rows. Removed blocks take their weekly goal with them. Logged activities
 * are never touched.
 */
suspend fun TrainingStore.updatePlanBlocks(planTemplateId: UUID, weeklyPlanId: UUID?, blocks: List<GoalEdit>) {
    val at = now()
    write {
        val existingIds = planDao.goalTemplateIds(planTemplateId).toSet()
        val keptIds = blocks.mapNotNull { it.id }.toSet()

        for (removed in existingIds - keptIds) {
            // The weekly goal first: deleting the template would null `goalTemplateId` (SET NULL)
            // out from under this match.
            weeklyPlanId?.let { weekDao.deleteGoal(it, removed) }
            planDao.deleteGoalTemplate(removed)
        }

        val weeklyGoalsByTemplate: Map<UUID, WeeklyGoalEntity> = weeklyPlanId
            ?.let { weekDao.goals(listOf(it)) }.orEmpty()
            .filter { it.goalTemplateId != null }
            .associateBy { it.goalTemplateId!! }

        for (block in blocks) {
            val existingId = block.id?.takeIf { it in existingIds }
            if (existingId != null) {
                planDao.updateGoalTemplate(
                    existingId, block.name, block.activityType.id, block.resource?.id,
                    block.targetType.code, block.targetValue, block.timeControlLabel,
                )
                val goal = weeklyGoalsByTemplate[existingId] ?: continue
                val completedAt = if (goal.currentValue >= block.targetValue) goal.completedAt ?: at else null
                weekDao.updateGoal(
                    goal.id, block.name, block.activityType.id, block.resource?.id, block.targetType.code,
                    block.targetValue, block.timeControlLabel, completedAt,
                )
            } else {
                val templateId = UUID.randomUUID()
                planDao.insertGoalTemplate(
                    GoalTemplateEntity(
                        id = templateId, planTemplateId = planTemplateId, name = block.name,
                        activityTypeId = block.activityType.id, resourceId = block.resource?.id,
                        targetType = block.targetType.code, targetValue = block.targetValue, createdAt = at,
                        timeControlLabel = block.timeControlLabel,
                    ),
                )
                weeklyPlanId ?: continue
                weekDao.upsertGoal(
                    WeeklyGoalEntity(
                        id = UUID.randomUUID(), weeklyPlanId = weeklyPlanId, name = block.name,
                        activityTypeId = block.activityType.id, resourceId = block.resource?.id,
                        targetType = block.targetType.code, targetValue = block.targetValue, currentValue = 0,
                        completedAt = null, timeControlLabel = block.timeControlLabel, goalTemplateId = templateId,
                        isBonus = false,
                    ),
                )
            }
        }
    }
}
