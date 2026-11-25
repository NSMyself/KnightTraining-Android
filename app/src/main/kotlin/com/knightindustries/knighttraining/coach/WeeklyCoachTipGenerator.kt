package com.knightindustries.knighttraining.coach

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.knightindustries.knighttraining.AppContainer
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import com.knightindustries.knighttraining.core.model.WeeklyReview
import com.knightindustries.knighttraining.core.store.saveCoachTip
import com.knightindustries.knighttraining.core.store.weekReviewContext
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Writes the weekly coach's tip for a reviewed week and stores it on that week's review (iOS
 * `WeeklyCoachTipGenerator`), for Plan This Week (or Home's Last week tile) to show.
 *
 * The tip is advice for the week being planned, so the review never shows it: the review starts
 * this the moment it's saved and closes, and whichever screen shows the tip later waits for the same
 * run (or starts one, if the app was closed before it finished). It runs in the app's scope, so
 * leaving the review doesn't cancel it.
 */
class WeeklyCoachTipGenerator(private val container: AppContainer, private val context: Context) {
    private val lock = Mutex()
    private val inFlight = mutableMapOf<UUID, Deferred<Unit>>()

    /** Whether this build asks the coach at all. */
    val isEnabled: Boolean get() = container.weeklyTipService.isEnabledInThisBuild

    /**
     * Asks the coach about [plan]'s review unless it already has an answer, and returns once the
     * answer is stored. Calls for the same week share one run. Does nothing where the coach is off,
     * or when the week has no saved review.
     */
    suspend fun generateIfNeeded(plan: WeeklyPlan) {
        if (!isEnabled) return
        val run = lock.withLock {
            // The run removes itself when done; it can't get there first, since that needs this lock.
            inFlight[plan.id] ?: container.appScope.async {
                try {
                    generate(plan)
                } finally {
                    lock.withLock { inFlight.remove(plan.id) }
                }
            }.also { inFlight[plan.id] = it }
        }
        run.await()
    }

    private suspend fun generate(plan: WeeklyPlan) {
        val store = container.store
        val reviewContext = store.weekReviewContext(plan.id, container.preferences.firstLaunch()) ?: return
        val review = reviewContext.existing?.takeIf { it.coachTip == null } ?: return
        val result = container.weeklyTipService.generateTip(reviewContext.plan, review, reviewContext.notedActivities, reviewContext.previous?.nextWeekFocus)
        val tip = when (result) {
            is WeeklyTipService.Result.Tip -> WeeklyReview.CoachTip(
                status = WeeklyReview.CoachTip.Status.READY, text = result.tip.text, focus = result.tip.focus,
                targetCode = result.tip.targetActivityTypeCode, targetValue = result.tip.targetValue, suggestsCoach = result.tip.suggestsCoach,
            )
            is WeeklyTipService.Result.Silent -> {
                // Debug builds only (the coach runs nowhere else): say why it stayed quiet.
                withContext(Dispatchers.Main) { Toast.makeText(context, "Coach skipped: ${result.reason}", Toast.LENGTH_LONG).show() }
                WeeklyReview.CoachTip.Unavailable
            }
        }
        try {
            store.saveCoachTip(tip, plan.id)
        } catch (failure: Exception) {
            Log.e("WeeklyCoach", "Failed to store the weekly coach tip", failure)
        }
    }
}
