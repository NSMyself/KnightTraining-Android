package com.knightindustries.knighttraining.core.store

import com.knightindustries.knighttraining.core.model.Activity
import com.knightindustries.knighttraining.core.model.WeekCalendar
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import com.knightindustries.knighttraining.core.model.WeeklyReview
import com.knightindustries.knighttraining.core.persistence.entities.WeeklyReviewEntity
import java.time.Instant
import java.util.UUID

private fun WeeklyReviewEntity.toDomain() = WeeklyReview(
    id, weeklyPlanId, wentWell, heldBack, wishIDone, nextWeekIntent, nextWeekFocus, nextWeekTargetCode, nextWeekTargetValue,
    coachTip = WeeklyReview.CoachTip.Status.of(coachTipStatus)?.let {
        WeeklyReview.CoachTip(it, coachTipText, coachTipFocus, coachTipTargetCode, coachTipTargetValue, coachTipSuggestsCoach)
    },
    createdAt = createdAt, updatedAt = updatedAt,
)

private fun WeeklyReview.toEntity() = WeeklyReviewEntity(
    id = id, weeklyPlanId = weeklyPlanId, wentWell = wentWell, heldBack = heldBack, nextWeekIntent = nextWeekIntent,
    createdAt = createdAt, updatedAt = updatedAt, wishIDone = wishIDone, nextWeekFocus = nextWeekFocus,
    nextWeekTargetCode = nextWeekTargetCode, nextWeekTargetValue = nextWeekTargetValue,
    coachTipStatus = coachTip?.status?.code, coachTipText = coachTip?.text, coachTipFocus = coachTip?.focus,
    coachTipTargetCode = coachTip?.targetCode, coachTipTargetValue = coachTip?.targetValue,
    coachTipSuggestsCoach = coachTip?.suggestsCoach ?: false,
)

suspend fun TrainingStore.weeklyReview(weeklyPlanId: UUID): WeeklyReview? = db.reviewDao().review(weeklyPlanId)?.toDomain()

suspend fun TrainingStore.saveWeeklyReview(review: WeeklyReview) = db.reviewDao().upsert(review.toEntity())

/** Sessions in `[start, end)` with a note, oldest first. */
suspend fun TrainingStore.activitiesWithNotes(start: Instant, end: Instant): List<Activity> =
    read { toActivities(db.reviewDao().activitiesWithNotes(start, end)) }

/** Everything the review screen shows about one past week, read together. */
data class WeekReviewContext(
    val plan: WeeklyPlan,
    /** Exclusive end, an extended first week included. */
    val weekEnd: java.time.LocalDate,
    /** "Week N": this week's position among every committed week. */
    val weekNumber: Int,
    val timeInvestedSeconds: Int,
    val notedActivities: List<Activity>,
    val existing: WeeklyReview?,
    /** The review before this one, for "With last week's comments". */
    val previous: WeeklyReview?,
)

suspend fun TrainingStore.weekReviewContext(weeklyPlanId: UUID, firstLaunch: Instant?, calendar: WeekCalendar = calendar()): WeekReviewContext? {
    val plan = weeklyPlan(weeklyPlanId) ?: return null
    val end = plan.effectiveNextWeekStart(firstLaunch, calendar)
    val start = calendar.startOf(plan.weekStart)
    val previousPlan = previousWeeklyPlan(start, firstLaunch, calendar)
    return WeekReviewContext(
        plan = plan,
        weekEnd = end,
        weekNumber = committedWeekCount(plan.weekStart),
        timeInvestedSeconds = totalDuration(start, calendar.startOf(end)),
        notedActivities = activitiesWithNotes(start, calendar.startOf(end)),
        existing = weeklyReview(plan.id),
        previous = previousPlan?.let { weeklyReview(it.id) },
    )
}
