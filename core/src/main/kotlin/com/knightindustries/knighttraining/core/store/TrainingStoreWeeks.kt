package com.knightindustries.knighttraining.core.store

import com.knightindustries.knighttraining.core.model.ActivityType
import com.knightindustries.knighttraining.core.model.PlanTemplate
import com.knightindustries.knighttraining.core.model.Resource
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.WeekCalendar
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import com.knightindustries.knighttraining.core.persistence.entities.WeeklyGoalEntity
import com.knightindustries.knighttraining.core.persistence.entities.WeeklyPlanEntity
import com.knightindustries.knighttraining.core.persistence.toDomain
import com.knightindustries.knighttraining.core.persistence.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

suspend fun TrainingStore.weeklyPlan(id: UUID): WeeklyPlan? = read { withGoals(weekDao.plan(id)) }

suspend fun TrainingStore.weeklyPlan(weekStart: LocalDate): WeeklyPlan? = read { withGoals(weekDao.plan(weekStart)) }

/**
 * The committed plan for the effective week [instant] falls in, first-week grace included. This is
 * the week a session counts toward: the one it *started* in, so a session begun Saturday 23:40
 * credits the week the user was finishing, not the new one (iOS, 2026-10-25).
 */
suspend fun TrainingStore.weeklyPlanContaining(
    instant: Instant,
    firstLaunch: Instant? = null,
    calendar: WeekCalendar = calendar(),
): WeeklyPlan? = weeklyPlan(WeeklyPlan.effectiveWeekStart(instant, firstLaunch, calendar))

/**
 * The plan for the effective week just before [reference]'s, or `null`. Steps to one moment before
 * the current effective week and re-resolves, rather than going back a flat 7 days, so it reaches
 * across a 14-day extended first week correctly.
 */
suspend fun TrainingStore.previousWeeklyPlan(
    reference: Instant = now(),
    firstLaunch: Instant? = null,
    calendar: WeekCalendar = calendar(),
): WeeklyPlan? {
    val currentStart = WeeklyPlan.effectiveWeekStart(reference, firstLaunch, calendar)
    val justBefore = calendar.startOf(currentStart).minusSeconds(1)
    return weeklyPlan(WeeklyPlan.effectiveWeekStart(justBefore, firstLaunch, calendar))
}

/** This week's ordinal among every committed week ("Week N"). */
suspend fun TrainingStore.committedWeekCount(upTo: LocalDate): Int = weekDao.committedWeekCount(upTo)

suspend fun TrainingStore.weeklyPlans(limit: Int = 10): List<WeeklyPlan> = read { withGoals(weekDao.plans(limit)) }

/** Live-updates on any week or goal change: the Training tab's week list stays mounted. */
fun TrainingStore.observeWeeklyPlans(limit: Int = 10): Flow<List<WeeklyPlan>> =
    db.invalidationTracker.createFlow("weekly_plan", "weekly_goal", "resource")
        .map { weeklyPlans(limit) }
        .distinctUntilChanged()

suspend fun TrainingStore.saveWeeklyPlan(plan: WeeklyPlan) = weekDao.upsertPlan(plan.toEntity())

suspend fun TrainingStore.deleteWeeklyPlan(id: UUID) = weekDao.deletePlan(id)

suspend fun TrainingStore.saveWeeklyGoal(goal: WeeklyGoal) = weekDao.upsertGoal(goal.toEntity())

suspend fun TrainingStore.deleteWeeklyGoal(id: UUID) = weekDao.deleteGoal(id)

suspend fun TrainingStore.weeklyGoal(id: UUID): WeeklyGoal? = read {
    weekDao.goal(id)?.let { goal -> goal.toDomain(resourcesById(listOf(goal.resourceId))) }
}

suspend fun TrainingStore.weeklyGoals(weeklyPlanId: UUID): List<WeeklyGoal> = read {
    val goals = weekDao.goals(listOf(weeklyPlanId))
    val resources = resourcesById(goals.map { it.resourceId })
    goals.map { it.toDomain(resources) }
}

// MARK: - Committing a week

/** Commits whichever template is active to the current effective week. */
suspend fun TrainingStore.createWeeklyPlanFromTemplate(
    firstLaunch: Instant? = null,
    calendar: WeekCalendar = calendar(),
): WeeklyPlan? = activePlanTemplate()?.let { createWeeklyPlan(it, firstLaunch, calendar) }

/** Commits a specific saved plan to the current effective week (the user picked it). */
suspend fun TrainingStore.createWeeklyPlan(
    templateId: UUID,
    firstLaunch: Instant? = null,
    calendar: WeekCalendar = calendar(),
): WeeklyPlan? = planTemplate(templateId)?.let { createWeeklyPlan(it, firstLaunch, calendar) }

/**
 * Two iOS bugs this is shaped by:
 * - **The week must be the effective week** Home reads, or a plan committed during the first-week
 *   grace lands on next week and stays invisible (2026-09-22).
 * - **The plan row and its goals are one transaction, and a hollow existing row is completed**
 *   rather than returned as-is. A partial write once left a goal-less week that read as "still
 *   needs a plan" forever, and every retry returned the same hollow row (2026-09-24).
 */
private suspend fun TrainingStore.createWeeklyPlan(
    template: PlanTemplate,
    firstLaunch: Instant?,
    calendar: WeekCalendar,
): WeeklyPlan? {
    val at = now()
    val weekStart = WeeklyPlan.effectiveWeekStart(at, firstLaunch, calendar)

    return write {
        val existing = weekDao.plan(weekStart)
        val planId = if (existing != null) {
            if (weekDao.goals(listOf(existing.id)).isNotEmpty()) return@write withGoals(existing)
            // A stuck row from an earlier partial write: finish committing it.
            weekDao.upsertPlan(existing.copy(planTemplateId = template.id))
            existing.id
        } else {
            UUID.randomUUID().also { weekDao.upsertPlan(WeeklyPlanEntity(it, template.id, weekStart, at)) }
        }

        for (goal in template.goalTemplates) {
            weekDao.upsertGoal(
                WeeklyGoalEntity(
                    id = UUID.randomUUID(), weeklyPlanId = planId, name = goal.name,
                    activityTypeId = goal.activityType.id, resourceId = goal.resource?.id,
                    targetType = goal.targetType.code, targetValue = goal.targetValue, currentValue = 0,
                    completedAt = null, timeControlLabel = goal.timeControlLabel, goalTemplateId = goal.id,
                    isBonus = false,
                ),
            )
        }
        withGoals(weekDao.plan(planId))
    }
}

// MARK: - Mid-week "add more"

/**
 * A one-week-only extra goal: always a bonus (never gates "is this week done") and never written
 * back to the plan.
 */
suspend fun TrainingStore.addBonusGoal(
    weeklyPlanId: UUID,
    activityType: ActivityType,
    targetType: TargetType,
    targetValue: Int,
    name: String? = null,
    resource: Resource? = null,
    timeControlLabel: String? = null,
): WeeklyGoal {
    val goal = WeeklyGoal(
        weeklyPlanId = weeklyPlanId, name = name, activityType = activityType, resource = resource,
        targetType = targetType, targetValue = targetValue, timeControlLabel = timeControlLabel, isBonus = true,
    )
    saveWeeklyGoal(goal)
    return goal
}

/**
 * Raises a goal's target, upward only. Never touches `completedAt`: a goal completed at its old
 * target stays completed, so an added extra can't un-complete the week.
 */
suspend fun TrainingStore.increaseGoalTarget(goalId: UUID, target: Int) = weekDao.raiseTarget(goalId, target)

/**
 * Sets a goal's progress outright and, if this call completes the week's last outstanding goal,
 * emits [WeeklyPlanCompleted]. Session logging goes through [logSession] instead.
 */
suspend fun TrainingStore.updateWeeklyGoalProgress(goal: WeeklyGoal, currentValue: Int) {
    val completedAt = if (currentValue >= goal.targetValue) now() else null
    val didCompletePlan = write {
        val before = weekDao.incompleteCount(goal.weeklyPlanId)
        weekDao.setProgress(goal.id, currentValue, completedAt)
        before > 0 && weekDao.incompleteCount(goal.weeklyPlanId) == 0
    }
    if (didCompletePlan) emitWeeklyPlanCompleted(goal.weeklyPlanId)
}

/** Broadcasts after the transaction commits, so a subscriber reading the database sees the result. */
internal suspend fun TrainingStore.emitWeeklyPlanCompleted(weeklyPlanId: UUID) {
    val (weekStart, goalCount) = read {
        weekDao.plan(weeklyPlanId)?.weekStart to weekDao.nonBonusGoalCount(weeklyPlanId)
    }
    weekStart ?: return
    emit(WeeklyPlanCompleted(weekStart, currentStreak(), goalCount))
}

// MARK: - Streaks (derived, never stored)

/**
 * Consecutive completed weeks walking back from [at]'s week. The week in progress may still be
 * incomplete, a leniency that applies to the first comparison only: iOS once let it skip a gap
 * anywhere in the chain (`StreakTests.testCurrentStreak_BreaksOnAGapWeek`).
 */
suspend fun TrainingStore.currentStreak(at: Instant = now(), calendar: WeekCalendar = calendar()): Int =
    currentStreak(weekDao.completedWeekStarts(), at, calendar)

internal fun currentStreak(completedWeekStarts: List<LocalDate>, at: Instant, calendar: WeekCalendar): Int {
    var streak = 0
    var expected = calendar.weekStart(at)
    var isFirstComparison = true

    // Compared by calendar week, so a 14-day first week stored under its own start still matches.
    for (weekStart in completedWeekStarts.map(calendar::weekStart)) {
        when {
            weekStart == expected -> {
                streak += 1
                expected = expected.minusWeeks(1)
            }
            isFirstComparison && weekStart == expected.minusWeeks(1) -> {
                streak += 1
                expected = expected.minusWeeks(2)
            }
            else -> break
        }
        isFirstComparison = false
    }
    return streak
}

data class WeekCompletionTally(val completed: Int, val committed: Int)

/**
 * Profile's "Weeks completed": completed weeks out of committed ones. A week never committed
 * doesn't count against the user, and the week in progress (an extended first week included)
 * counts only once it's complete, so a fresh Monday doesn't read "0 of 1".
 */
suspend fun TrainingStore.weekCompletionTally(
    at: Instant = now(),
    firstLaunch: Instant? = null,
    calendar: WeekCalendar = calendar(),
): WeekCompletionTally {
    val openWeek = WeeklyPlan.effectiveWeekStart(at, firstLaunch, calendar)
    val weeks = weekDao.weekCompletions()
    return WeekCompletionTally(
        completed = weeks.count { it.isComplete },
        committed = weeks.count { it.isComplete || it.weekStart < openWeek },
    )
}

/** The longest run of consecutive completed weeks ever. */
suspend fun TrainingStore.longestStreak(calendar: WeekCalendar = calendar()): Int {
    val weeks = weekDao.completedWeekStarts().map(calendar::weekStart).sorted()
    if (weeks.isEmpty()) return 0
    var longest = 1
    var run = 1
    for ((previous, week) in weeks.zipWithNext()) {
        run = if (week == previous.plusWeeks(1)) run + 1 else 1
        longest = maxOf(longest, run)
    }
    return longest
}
