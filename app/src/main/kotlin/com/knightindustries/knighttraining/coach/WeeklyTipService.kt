package com.knightindustries.knighttraining.coach

import android.util.Log
import com.knightindustries.knighttraining.AppContainer
import com.knightindustries.knighttraining.BuildConfig
import com.knightindustries.knighttraining.core.coach.CoachTargetChange
import com.knightindustries.knighttraining.core.coach.CoachingSuggestion
import com.knightindustries.knighttraining.core.coach.LichessPuzzleStatus
import com.knightindustries.knighttraining.core.coach.ModelGate
import com.knightindustries.knighttraining.core.coach.WeeklyTip
import com.knightindustries.knighttraining.core.coach.WeeklyTipCoach
import com.knightindustries.knighttraining.core.lichess.LichessException
import com.knightindustries.knighttraining.core.model.Activity
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import com.knightindustries.knighttraining.core.model.WeeklyReview
import com.knightindustries.knighttraining.core.store.coachingGate
import com.knightindustries.knighttraining.core.store.lichessAccount
import com.knightindustries.knighttraining.core.store.recentCompletedActivities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.util.Locale

/**
 * Drives the weekly coach from the review screen (iOS `WeeklyTipService`). An ineligible build or
 * phone, a rejected tip, an error and a timeout all mean silence for the player, who never knows
 * the feature exists until it speaks; the reason comes back for debug builds to show.
 */
class WeeklyTipService(private val container: AppContainer, private val model: GeminiNano = GeminiNano()) {
    /**
     * Debug builds only for now, like iOS's DEBUG/TestFlight gate: the pipeline is unproven on
     * Gemini Nano, and its quality needs evaluating on a real phone before anyone else sees it.
     */
    val isEnabledInThisBuild: Boolean get() = BuildConfig.DEBUG

    /** A tip, or why there isn't one: developer-facing, shown in debug builds only. */
    sealed interface Result {
        data class Tip(val tip: WeeklyTip) : Result
        data class Silent(val reason: String) : Result
    }

    suspend fun generateTip(plan: WeeklyPlan, review: WeeklyReview, notedActivities: List<Activity>, lastWeekFocus: String?): Result {
        if (!isEnabledInThisBuild) return Result.Silent("off in this build")
        val result = when (val availability = model.availability(container.appScope)) {
            is GeminiNano.Availability.Ready -> try {
                withTimeoutOrNull(TIMEOUT_MILLIS) { run(plan, review, notedActivities, lastWeekFocus) }
                    ?: Result.Silent("timed out after ${TIMEOUT_MILLIS / 1000} s")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "Weekly coach failed", failure)
                Result.Silent("failed: ${failure.message ?: failure::class.simpleName}")
            }
            GeminiNano.Availability.Downloading -> Result.Silent("the model is still downloading")
            is GeminiNano.Availability.Unavailable -> Result.Silent(availability.reason)
        }
        if (result is Result.Silent) Log.i(TAG, "Weekly coach skipped: ${result.reason}")
        return result
    }

    /**
     * Asks AICore for the model ahead of the first review, while the app is in the foreground.
     * Only where the coach is on, or every install would pull gigabytes for a feature it can't use.
     */
    suspend fun prefetchModel() {
        if (isEnabledInThisBuild) model.prefetch(container.appScope)
    }

    private suspend fun run(plan: WeeklyPlan, review: WeeklyReview, notedActivities: List<Activity>, lastWeekFocus: String?): Result {
        val store = container.store
        val recent = store.recentCompletedActivities(days = 30)
        // One Lichess request at a time: the coach's month, then the streak's week.
        val puzzleStatus = puzzleStatus(LichessPuzzleStatus.DAYS)
        val weeklyPuzzles = puzzleStatus(CoachingSuggestion.WEAK_THEME_WINDOW_DAYS)
        val hasNotes = WeeklyTipCoach.noteLines(review, notedActivities).isNotEmpty()
        val gateResult = store.coachingGate(
            plan, hasNotes, weeklyPuzzles, container.preferences.weakThemeStreak(), container.preferences.lastCoachingSuggestion(),
        )
        container.preferences.setWeakThemeStreak(gateResult.streak)

        val languageName = ModelGate.languageName(Locale.getDefault())
        val outcome = WeeklyTipCoach(model).run(
            WeeklyTipCoach.Input(plan, review, notedActivities, recent, puzzleStatus, lastWeekFocus, gateResult.gate),
            languageName,
        )
        Log.i(TAG, "Weekly coach outcome: ${outcome::class.simpleName}" + if (outcome is WeeklyTipCoach.Outcome.Rejected) " (${outcome.reason})" else "")
        val approved = outcome as? WeeklyTipCoach.Outcome.Approved
            ?: return Result.Silent("tip rejected: ${(outcome as WeeklyTipCoach.Outcome.Rejected).reason}")
        val suggestion = approved.suggestion
        val target = CoachTargetChange.resolve(suggestion.activityTypeCode, suggestion.suggestedTargetValue, plan)
        return Result.Tip(
            WeeklyTip(
                text = suggestion.tip,
                focus = WeeklyTipCoach.acceptedFocus(suggestion, approved.verdict),
                targetActivityTypeCode = target?.activityTypeCode,
                targetValue = target?.value,
                suggestsCoach = gateResult.gate.suggestion != null && CoachingSuggestion.mentionsCoach(suggestion.tip, languageName),
            ),
        )
    }

    /** `null` with no Lichess account or a failed fetch; Lichess answers a month without puzzles with a 404. */
    private suspend fun puzzleStatus(days: Int): LichessPuzzleStatus? {
        if (container.store.lichessAccount() == null) return null
        return try {
            LichessPuzzleStatus.from(container.lichessClient.fetchPuzzleDashboard(days))
        } catch (failure: LichessException.Http) {
            if (failure.status == 404) LichessPuzzleStatus.NoPuzzles else null
        } catch (failure: LichessException) {
            null
        }
    }

    /** Starts the three-month wait once a "work with a coach" tip has actually been shown. */
    suspend fun recordCoachSuggestionShown() = container.preferences.setLastCoachingSuggestion(Instant.now())

    private companion object {
        const val TAG = "WeeklyCoach"

        /** Two on-device passes; a slow phone must never leave the player stuck on the review. */
        const val TIMEOUT_MILLIS = 30_000L
    }
}
