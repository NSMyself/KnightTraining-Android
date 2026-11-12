package com.knightindustries.knighttraining.core.model

import java.time.Instant
import java.util.UUID

/** The atomic "I trained" record. `endedAt == null` means it's ongoing. */
data class Activity(
    val id: UUID = UUID.randomUUID(),
    val activityType: ActivityType,
    val resource: Resource? = null,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    /** Seconds. */
    val duration: Int,
    val count: Int? = null,
    val source: ActivitySource = ActivitySource.MANUAL,
    val externalId: String? = null,
    val notes: String? = null,
    /**
     * Groups every row one practice-timer run writes. Currently written and never read; kept
     * because it's the only way to reconstruct a sitting later (iOS AGENTS.md §7). Defaults to
     * `null`, never a fresh id per row.
     */
    val sessionId: UUID? = null,
    /** A [TimeControl.label]. Playing only. */
    val timeControlLabel: String? = null,
    /** The committed week this counted toward, stamped by `logSession` from the goal it advanced. */
    val weeklyPlanId: UUID? = null,
    /** The exact goal this advanced, so an edit can correct it by an exact delta. */
    val weeklyGoalId: UUID? = null,
    /** Coaching only, and never part of goal crediting. */
    val coachingTopics: List<CoachingTopic> = emptyList(),
    val createdAt: Instant = Instant.now(),
) {
    val timeControl: TimeControl?
        get() = timeControlLabel?.let(TimeControl::parse)

    val durationMinutes: Int get() = duration / 60

    val isFromSync: Boolean get() = source == ActivitySource.LICHESS_SYNC

    val isManual: Boolean get() = source == ActivitySource.MANUAL
}
