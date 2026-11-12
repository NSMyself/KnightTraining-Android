package com.knightindustries.knighttraining.core.store

import com.knightindustries.knighttraining.core.model.LibraryShareSummary
import com.knightindustries.knighttraining.core.model.SharePlayer
import com.knightindustries.knighttraining.core.model.WeekCalendar
import com.knightindustries.knighttraining.core.model.WeekShareSummary
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import java.time.Instant

/** One week, as the share card shows it: the plan, its activities, the review, the player then. */
suspend fun TrainingStore.weekShareSummary(plan: WeeklyPlan, firstLaunch: Instant?, calendar: WeekCalendar = calendar()): WeekShareSummary {
    val next = plan.effectiveNextWeekStart(firstLaunch, calendar)
    return WeekShareSummary.from(
        plan = plan,
        nextWeekStart = next,
        planName = planTemplate(plan.planTemplateId)?.name,
        activities = activities(calendar.startOf(plan.weekStart), calendar.startOf(next)),
        review = weeklyReview(plan.id),
        player = sharePlayer(before = calendar.startOf(next)),
    )
}

suspend fun TrainingStore.libraryShareSummary(): LibraryShareSummary = LibraryShareSummary.from(
    resources = allResources(),
    secondsByResource = db.libraryDao().secondsByResource().associate { it.resourceId to it.seconds },
    player = sharePlayer(before = null),
)

/** The connected Lichess account with its ratings as they stood [before] (the week's end for a past week). */
private suspend fun TrainingStore.sharePlayer(before: Instant?): SharePlayer? {
    val account = lichessAccount() ?: return null
    val cutoff = before ?: FAR_FUTURE
    val ratings = SharePlayer.ratingTypes.mapNotNull { type ->
        db.lichessDao().latestRatingBefore(account.id, type, cutoff)?.let { SharePlayer.Rating(type, it) }
    }
    return SharePlayer(account.username, account.platform, account.flag, ratings)
}

private val FAR_FUTURE: Instant = Instant.parse("9999-01-01T00:00:00Z")
