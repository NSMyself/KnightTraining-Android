package com.knightindustries.knighttraining.core.model

import java.time.Duration
import java.time.Instant

/**
 * The streak milestones a store review may be asked at (iOS `ReviewMilestone`): 2, 8 and 26
 * completed weeks in a row. Each is asked at most once.
 */
enum class ReviewMilestone(val streakThreshold: Int) {
    FIRST(2),
    SECOND(8),
    THIRD(26),
    ;

    companion object {
        /**
         * The highest milestone [streak] has reached, or `null`. `>=`, not `==`: a streak that
         * jumps from 1 to 9 resolves to [SECOND], never [FIRST] after the fact.
         */
        fun highestReached(streak: Int): ReviewMilestone? = entries.lastOrNull { streak >= it.streakThreshold }
    }
}

/** What's been asked so far: a device preference, not user data (kept out of the database). */
data class ReviewState(
    val lastAskedVersion: String? = null,
    val lastAskedAt: Instant? = null,
    val highestMilestoneAsked: ReviewMilestone? = null,
) {
    /** Recorded only once the review flow has actually been launched. */
    fun asked(milestone: ReviewMilestone, version: String, at: Instant) = ReviewState(version, at, milestone)
}

/**
 * Whether completing a week should ask for a store review (iOS `ReviewRequestCoordinator`). Pure:
 * a milestone is only used up by [ReviewState.asked] after the ask, so a suppressed week (phone
 * moving, app just opened) leaves it armed for the next completed week.
 *
 * iOS also waits 30 minutes after a paywall; Android has no paywall, so that gate is absent.
 */
object ReviewRequestPolicy {
    val minimumTimeSinceLastAsk: Duration = Duration.ofDays(90)
    val minimumTimeInApp: Duration = Duration.ofSeconds(90)
    val calmSampling: Duration = Duration.ofSeconds(2)

    sealed interface Decision {
        data class Request(val milestone: ReviewMilestone) : Decision

        data class Suppressed(val reason: Reason) : Decision
    }

    enum class Reason { NO_MILESTONE_REACHED, ALREADY_ASKED_THIS_VERSION, WITHIN_DATE_WINDOW, SESSION_TOO_SHORT, DEVICE_IN_MOTION }

    /**
     * [isCalm] runs last, only when every cheap check has passed: it samples the motion sensors
     * for [calmSampling], the same moment the celebration needs before a system sheet covers it.
     */
    suspend fun evaluate(
        streak: Int,
        state: ReviewState,
        currentVersion: String,
        now: Instant,
        timeInApp: Duration,
        isCalm: suspend () -> Boolean,
    ): Decision {
        val milestone = ReviewMilestone.highestReached(streak) ?: return Decision.Suppressed(Reason.NO_MILESTONE_REACHED)
        val asked = state.highestMilestoneAsked
        if (asked != null && asked >= milestone) return Decision.Suppressed(Reason.NO_MILESTONE_REACHED)
        if (state.lastAskedVersion == currentVersion) return Decision.Suppressed(Reason.ALREADY_ASKED_THIS_VERSION)
        if (state.lastAskedAt != null && Duration.between(state.lastAskedAt, now) < minimumTimeSinceLastAsk) {
            return Decision.Suppressed(Reason.WITHIN_DATE_WINDOW)
        }
        if (timeInApp < minimumTimeInApp) return Decision.Suppressed(Reason.SESSION_TOO_SHORT)
        if (!isCalm()) return Decision.Suppressed(Reason.DEVICE_IN_MOTION)
        return Decision.Request(milestone)
    }

    /**
     * iOS's calm test: the mean absolute deviation of the acceleration magnitudes (gravity
     * removed) at or under [threshold]. Too few samples (no sensor, an emulator) passes: a sensor
     * fault must never suppress the ask for good.
     */
    fun isCalm(magnitudes: List<Double>, threshold: Double = 0.05): Boolean {
        if (magnitudes.size < 2) return true
        val mean = magnitudes.average()
        return magnitudes.sumOf { kotlin.math.abs(it - mean) } / magnitudes.size <= threshold
    }
}
