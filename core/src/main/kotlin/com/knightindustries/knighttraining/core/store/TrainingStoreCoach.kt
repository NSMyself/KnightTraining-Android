package com.knightindustries.knighttraining.core.store

import com.knightindustries.knighttraining.core.coach.CoachTargetChange
import com.knightindustries.knighttraining.core.coach.CoachingSuggestion
import com.knightindustries.knighttraining.core.coach.LichessPuzzleStatus
import com.knightindustries.knighttraining.core.coach.WeakThemeStreak
import com.knightindustries.knighttraining.core.model.ActivityType
import com.knightindustries.knighttraining.core.model.PlanSuggestionHistory
import com.knightindustries.knighttraining.core.model.WeekCalendar
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import com.knightindustries.knighttraining.core.model.WeeklyReview
import com.knightindustries.knighttraining.core.persistence.toDomain
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// MARK: - The coach's tip, stored on the reviewed week

/**
 * Stores the coach's answer to the review of [weeklyPlanId] (a tip, or [WeeklyReview.CoachTip.Unavailable]),
 * but only the first time: two screens asking at once can't overwrite each other, and an accepted
 * tip stays accepted. Returns whether it was stored; `false` too when there's no review.
 */
suspend fun TrainingStore.saveCoachTip(tip: WeeklyReview.CoachTip, weeklyPlanId: UUID): Boolean =
    db.reviewDao().saveCoachTip(weeklyPlanId, tip.status.code, tip.text, tip.focus, tip.targetCode, tip.targetValue, tip.suggestsCoach, now()) > 0

/** The user's answer to a stored tip ("Use it", "Not now"). Only a tip still waiting for one changes. */
suspend fun TrainingStore.setCoachTipStatus(status: WeeklyReview.CoachTip.Status, weeklyPlanId: UUID) =
    db.reviewDao().setCoachTipStatus(weeklyPlanId, WeeklyReview.CoachTip.Status.READY.code, status.code, now())

/** Remembers an accepted target on the reviewed week's review, without touching any plan. */
suspend fun TrainingStore.setNextWeekTarget(target: PlanSuggestionHistory.CoachTarget, weeklyPlanId: UUID) =
    db.reviewDao().setTarget(weeklyPlanId, target.activityTypeCode, target.value, now())

/**
 * The coach's target the user accepted for the week [reference] falls in, from the review of the
 * week before, in that review's goal units. `null` with no review, no tip, a tip not accepted, or
 * one without a target.
 */
suspend fun TrainingStore.acceptedCoachTarget(
    reference: Instant = now(),
    firstLaunch: Instant? = null,
    calendar: WeekCalendar = calendar(),
): CoachTargetChange.TemplateTarget? {
    val reviewed = previousWeeklyPlan(reference, firstLaunch, calendar) ?: return null
    val tip = weeklyReview(reviewed.id)?.coachTip ?: return null
    if (tip.status != WeeklyReview.CoachTip.Status.ACCEPTED) return null
    return CoachTargetChange.templateTarget(tip.target ?: return null, reviewed)
}

/** The focus the player accepted on the tip card, saved on the review already written. */
suspend fun TrainingStore.setNextWeekFocus(focus: String?, weeklyPlanId: UUID) = db.reviewDao().setFocus(weeklyPlanId, focus, now())

/**
 * Accepts the coach's target change (iOS `applyCoachTarget`), in one transaction: saves it on the
 * reviewed week's review (for "Suggest a plan"), retargets the template goal it came from, and
 * retargets this week's goal too while that week is still editable. A goal with no template only
 * updates the review.
 */
suspend fun TrainingStore.applyCoachTarget(
    target: PlanSuggestionHistory.CoachTarget,
    reviewedWeeklyPlanId: UUID,
    goalTemplateId: UUID?,
    currentWeekStart: LocalDate,
) = write {
    val dao = db.reviewDao()
    val at = now()
    dao.setTarget(reviewedWeeklyPlanId, target.activityTypeCode, target.value, at)
    if (goalTemplateId == null) return@write
    dao.setTemplateTarget(goalTemplateId, target.value)
    for (goal in dao.goalsFromTemplate(goalTemplateId, currentWeekStart)) {
        if (activityDao.sessionCount(goal.weeklyPlanId) >= WeeklyPlan.EDITABLE_SESSION_LIMIT) continue
        val completedAt = if (goal.currentValue >= target.value) goal.completedAt ?: at else null
        dao.retargetGoal(goal.id, target.value, completedAt)
    }
}

/** The gate, plus the streak to save for next week's review. */
data class CoachingGateResult(val gate: CoachingSuggestion.Gate, val streak: WeakThemeStreak?)

/**
 * Whether this week's tip may suggest a coach (iOS `CoachingSuggestion.loadGate`). Someone already
 * logging coaching may hear about their lessons, but is never pitched one.
 *
 * @param weeklyPuzzleStatus the last [CoachingSuggestion.WEAK_THEME_WINDOW_DAYS] days of puzzles, for the streak.
 */
suspend fun TrainingStore.coachingGate(
    plan: WeeklyPlan,
    hasNotes: Boolean,
    weeklyPuzzleStatus: LichessPuzzleStatus?,
    savedStreak: WeakThemeStreak?,
    lastSuggestedAt: Instant?,
    at: Instant = now(),
    calendar: WeekCalendar = calendar(),
): CoachingGateResult {
    val streak = WeakThemeStreak.advance(savedStreak, weeklyPuzzleStatus, plan.weekStart)
    val hasCoachingHistory = hasActivity(ActivityType.COACHING) || plan.weeklyGoals.any { it.activityType.code == ActivityType.COACHING }
    val recent = weeklyPlans(CoachingSuggestion.STEADY_WEEKS_REQUIRED + 2).filter { !it.weekStart.isAfter(plan.weekStart) }
    val completedWeeks = CoachingSuggestion.consecutiveCompletedWeeks(recent)

    var ratingChange: CoachingSuggestion.RatingChange? = null
    val account = lichessAccount()
    if (completedWeeks >= CoachingSuggestion.STEADY_WEEKS_REQUIRED && account != null) {
        val from = calendar.startOf(plan.weekStart.minusWeeks(CoachingSuggestion.STEADY_WEEKS_REQUIRED - 1L))
        val to = calendar.startOf(plan.weekStart.plusWeeks(1))
        val snapshots = db.lichessDao().snapshotsBetween(account.id, from, to).map { it.toDomain() }
        val games = activities(from, to).filter { it.activityType.code == ActivityType.PLAYING }
        ratingChange = CoachingSuggestion.ratingChange(snapshots, games)
    }

    val signals = CoachingSuggestion.Signals(
        hasCoachingHistory = hasCoachingHistory,
        committedWeeks = committedWeekCount(plan.weekStart),
        reviewedWeekCompleted = plan.isFullyCompleted,
        lastSuggestedAt = lastSuggestedAt,
        weakThemeStreak = streak,
        consecutiveCompletedWeeks = completedWeeks,
        ratingChange = ratingChange,
        hasNotes = hasNotes,
    )
    val suggestion = CoachingSuggestion.evaluate(signals, at, calendar.zone)
    return CoachingGateResult(CoachingSuggestion.Gate(suggestion, suggestion != null || hasCoachingHistory), streak)
}
