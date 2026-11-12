@file:UseSerializers(UuidSerializer::class, InstantSerializer::class)

package com.knightindustries.knighttraining.core.session

import com.knightindustries.knighttraining.core.model.ActivityType
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * A practice-timer run, from Start to the review after Stop: the counterpart of iOS's
 * `StudyingViewModel` state, minus the UI. Immutable; every transition takes the time it happens
 * at, so the whole thing is testable without a clock.
 *
 * **Elapsed time is always derived from timestamps**, never a ticking counter, so a session
 * survives the app being backgrounded or killed: [encode] it on every change and [decode] it on
 * launch (iOS `PendingSessionSnapshot`).
 *
 * "Change" closes the running segment, and each segment becomes its own activity on save, so
 * puzzles then games in one sitting log as two activities, not one blob credited to whatever was
 * running at Stop.
 */
@Serializable
data class PracticeSession(
    /** Shared by every activity this session writes: one sitting. */
    val sessionId: UUID,
    val startedAt: Instant,
    val stoppedAt: Instant? = null,
    /** Pause time accumulated so far, in milliseconds. */
    val pausedMillis: Long = 0,
    val pausedAt: Instant? = null,
    val segmentStart: Instant,
    /** [pausedMillis] when the current segment began, so only its own pauses are subtracted. */
    val pausedMillisAtSegmentStart: Long = 0,
    val completedSegments: List<Segment> = emptyList(),
    val attribution: Attribution,
    val resourceId: UUID? = null,
    /** The plan goals chosen for this session, offered by "Change". */
    val availableGoalIds: List<UUID> = emptyList(),
    val phase: Phase = Phase.Running,
    /** The user's correction to the final segment's duration at review, in seconds. */
    val durationOverrideSeconds: Int? = null,
    val goalCredit: GoalCredit = GoalCredit.Automatic,
) {
    /** What the running segment is about. */
    @Serializable
    sealed interface Attribution {
        val activityTypeCode: String?

        /** A plan goal: progress goes to it directly. */
        @Serializable
        data class Goal(
            val goalId: UUID,
            override val activityTypeCode: String,
            val targetType: TargetType,
            val timeControlLabel: String? = null,
        ) : Attribution

        /** "Something else": a real category with no goal behind it. */
        @Serializable
        data class AdHoc(override val activityTypeCode: String, val timeControlLabel: String? = null) : Attribution

        /** Quick start: the category is asked after Stop, or it's logged as general. */
        @Serializable
        data object General : Attribution {
            override val activityTypeCode: String? get() = null
        }

        val timeControl: String?
            get() = when (this) {
                is Goal -> timeControlLabel
                is AdHoc -> timeControlLabel
                General -> null
            }
    }

    /** A closed stretch of the session under one attribution. */
    @Serializable
    data class Segment(
        val attribution: Attribution,
        val resourceId: UUID?,
        val start: Instant,
        val end: Instant,
        val durationSeconds: Int,
        val count: Int?,
        /** For an ad-hoc segment: the goal picked when switching away from it. */
        val creditedGoalId: UUID? = null,
    )

    @Serializable
    sealed interface Phase {
        @Serializable
        data object Running : Phase

        /** Stopped, waiting for "what did you train?". */
        @Serializable
        data object Categorizing : Phase

        /** Stopped with a known category: confirm the duration, and the count where it applies. */
        @Serializable
        data class Reviewing(val activityTypeCode: String) : Phase
    }

    /** The review's "Counts toward" choice, for a session with no plan goal behind it. */
    @Serializable
    sealed interface GoalCredit {
        /** The only candidate when there's exactly one, otherwise nothing: never a guess. */
        @Serializable
        data object Automatic : GoalCredit

        @Serializable
        data object NotCounted : GoalCredit

        @Serializable
        data class Picked(val goalId: UUID) : GoalCredit

        fun resolve(candidates: List<WeeklyGoal>): WeeklyGoal? = when (this) {
            NotCounted -> null
            is Picked -> candidates.firstOrNull { it.id == goalId } ?: candidates.singleOrNull()
            Automatic -> candidates.singleOrNull()
        }
    }

    val isPaused: Boolean get() = pausedAt != null
    val isRunning: Boolean get() = phase == Phase.Running

    /** Session time so far, pauses excluded. */
    fun elapsedSeconds(now: Instant): Int = activeSeconds(startedAt, pausedMillis, now)

    /** The current segment's time: the big clock, which restarts on "Change". */
    fun segmentElapsedSeconds(now: Instant): Int = activeSeconds(segmentStart, pausedMillis - pausedMillisAtSegmentStart, now)

    /** What will be saved for the final segment: the correction if any, else the tracked time. */
    fun finalSegmentSeconds(now: Instant): Int = durationOverrideSeconds ?: segmentElapsedSeconds(now)

    /** Every segment's time plus the final one's. */
    fun totalSessionSeconds(now: Instant): Int = completedSegments.sumOf { it.durationSeconds } + finalSegmentSeconds(now)

    val hasMultipleSegments: Boolean get() = completedSegments.isNotEmpty()

    /**
     * Under this, Stop discards without a review: accidental-tap protection, not a minimum
     * duration (iOS AGENTS.md §7). Short on purpose: a couple of easy puzzles can take 20s.
     */
    fun isBelowAccidentalStopThreshold(now: Instant): Boolean = totalSessionSeconds(now) < ACCIDENTAL_STOP_DISCARD_SECONDS

    private fun activeSeconds(from: Instant, paused: Long, now: Instant): Int {
        val end = stoppedAt ?: now
        val ongoingPause = pausedAt?.let { Duration.between(it, end).toMillis() } ?: 0
        return maxOf(0L, (Duration.between(from, end).toMillis() - paused - ongoingPause) / 1000).toInt()
    }

    // MARK: - Transitions

    fun pause(now: Instant): PracticeSession = if (isPaused || !isRunning) this else copy(pausedAt = now)

    fun resume(now: Instant): PracticeSession =
        pausedAt?.let { copy(pausedMillis = pausedMillis + Duration.between(it, now).toMillis(), pausedAt = null) } ?: this

    /**
     * Closes the running segment under its current attribution (with [outgoingCount] where the
     * activity has a count, and [outgoingCreditedGoalId] for an ad-hoc one) and opens a new one.
     * The session clock carries on; the segment clock restarts.
     */
    fun change(
        now: Instant,
        to: Attribution,
        resourceId: UUID?,
        outgoingCount: Int? = null,
        outgoingCreditedGoalId: UUID? = null,
    ): PracticeSession {
        val resumed = resume(now)
        val closed = Segment(
            attribution = resumed.attribution,
            resourceId = resumed.resourceId,
            start = resumed.segmentStart,
            end = now,
            durationSeconds = resumed.segmentElapsedSeconds(now),
            count = outgoingCount,
            creditedGoalId = outgoingCreditedGoalId.takeIf { resumed.attribution is Attribution.AdHoc },
        )
        return resumed.copy(
            completedSegments = resumed.completedSegments + closed,
            segmentStart = now,
            pausedMillisAtSegmentStart = resumed.pausedMillis,
            attribution = to,
            resourceId = resourceId,
        )
    }

    /** Freezes the clocks and moves to the review: straight to it when the category is known. */
    fun stop(now: Instant): PracticeSession {
        val resumed = resume(now)
        val code = attribution.activityTypeCode
        return resumed.copy(stoppedAt = now, phase = if (code != null) Phase.Reviewing(code) else Phase.Categorizing)
    }

    /** The category picked after Stop for a quick-start session. */
    fun categorize(activityTypeCode: String): PracticeSession = copy(phase = Phase.Reviewing(activityTypeCode))

    fun overrideFinalDuration(minutes: Int): PracticeSession = copy(durationOverrideSeconds = maxOf(0, minutes) * 60)

    fun withResource(id: UUID?): PracticeSession = copy(resourceId = id)

    fun withGoalCredit(credit: GoalCredit): PracticeSession = copy(goalCredit = credit)

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        const val ACCIDENTAL_STOP_DISCARD_SECONDS = 10

        private val json = Json { ignoreUnknownKeys = true }

        fun start(
            now: Instant,
            attribution: Attribution,
            resourceId: UUID? = null,
            availableGoalIds: List<UUID> = emptyList(),
            sessionId: UUID = UUID.randomUUID(),
        ) = PracticeSession(
            sessionId = sessionId, startedAt = now, segmentStart = now, attribution = attribution,
            resourceId = resourceId, availableGoalIds = availableGoalIds,
        )

        /** `null` for a snapshot that no longer decodes; the caller drops it. */
        fun decode(text: String): PracticeSession? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()

        /** Attribution for a plan goal. */
        fun attributionFor(goal: WeeklyGoal): Attribution =
            Attribution.Goal(goal.id, goal.activityType.code, goal.targetType, goal.timeControlLabel)

        /** Attribution for a category picked outside the plan; only games keep a time control. */
        fun adHoc(activityTypeCode: String, timeControlLabel: String?): Attribution =
            Attribution.AdHoc(activityTypeCode, timeControlLabel.takeIf { activityTypeCode == ActivityType.PLAYING })
    }
}
