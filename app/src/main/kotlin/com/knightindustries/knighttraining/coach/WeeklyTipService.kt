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
 * Drives the weekly coach from the review screen (iOS `WeeklyTipService`). Returns a tip or
 * nothing: an ineligible build or phone, a rejected tip, an error and a timeout all mean silence,
 * since the player never knows the feature exists until it speaks.
 */
class WeeklyTipService(private val container: AppContainer, private val model: GeminiNano = GeminiNano()) {
    /**
     * Debug builds only for now, like iOS's DEBUG/TestFlight gate: the pipeline is unproven on
     * Gemini Nano, and its quality needs evaluating on a real phone before anyone else sees it.
     */
    val isEnabledInThisBuild: Boolean get() = BuildConfig.DEBUG

    suspend fun generateTip(plan: WeeklyPlan, review: WeeklyReview, notedActivities: List<Activity>, lastWeekFocus: String?): WeeklyTip? {
        if (!isEnabledInThisBuild) return null
        val availability = model.availability(container.appScope)
        if (availability !is GeminiNano.Availability.Ready) {
            Log.i(TAG, "Weekly coach skipped: $availability")
            return null
        }
        return try {
            withTimeoutOrNull(TIMEOUT_MILLIS) { run(plan, review, notedActivities, lastWeekFocus) }.also { if (it == null) Log.i(TAG, "Weekly coach timed out or said nothing") }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w(TAG, "Weekly coach failed", failure)
            null
        }
    }

    private suspend fun run(plan: WeeklyPlan, review: WeeklyReview, notedActivities: List<Activity>, lastWeekFocus: String?): WeeklyTip? {
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
        val approved = outcome as? WeeklyTipCoach.Outcome.Approved ?: return null
        val suggestion = approved.suggestion
        val target = CoachTargetChange.resolve(suggestion.activityTypeCode, suggestion.suggestedTargetValue, plan)
        return WeeklyTip(
            text = suggestion.tip,
            focus = WeeklyTipCoach.acceptedFocus(suggestion, approved.verdict),
            targetActivityTypeCode = target?.activityTypeCode,
            targetValue = target?.value,
            suggestsCoach = gateResult.gate.suggestion != null && CoachingSuggestion.mentionsCoach(suggestion.tip, languageName),
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
