package com.knightindustries.knighttraining.core.model

import java.time.Instant
import java.util.UUID

/** The end-of-week reflection, one per committed week. Every field is optional. */
data class WeeklyReview(
    val id: UUID = UUID.randomUUID(),
    val weeklyPlanId: UUID,
    val wentWell: String? = null,
    val heldBack: String? = null,
    /** Forward-looking, unlike [heldBack]: what to actually do about it. */
    val wishIDone: String? = null,
    /** One of [NextWeekIntent]'s codes. */
    val nextWeekIntent: String? = null,
    /** The coach's focus phrase, kept in the language it was written in (iOS AGENTS.md §14). */
    val nextWeekFocus: String? = null,
    val nextWeekTargetCode: String? = null,
    val nextWeekTargetValue: Int? = null,
    /**
     * The coach's tip for the week after this one, written in the background once this review is
     * saved and shown while that week is planned. `null` until the coach has been asked.
     */
    val coachTip: CoachTip? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    /** Re-saving keeps the id, creation time, the coach's accepted advice and its tip. */
    fun updating(wentWell: String?, heldBack: String?, wishIDone: String?, nextWeekIntent: String?, now: Instant = Instant.now()) =
        copy(wentWell = wentWell, heldBack = heldBack, wishIDone = wishIDone, nextWeekIntent = nextWeekIntent, updatedAt = now)

    /**
     * The weekly coach's answer to this review, kept until the user plans the following week (iOS
     * `WeeklyReview.CoachTip`). Never shown on the review itself: it's advice for the week being
     * planned. The text and focus are the model's own words, kept in the language they were
     * written in, like [nextWeekFocus] (iOS AGENTS.md §14).
     */
    data class CoachTip(
        val status: Status,
        val text: String? = null,
        val focus: String? = null,
        /** The coach's target change, in the reviewed goal's own units. Both set or both `null`. */
        val targetCode: String? = null,
        val targetValue: Int? = null,
        /** Whether this is the gated "work with a coach" suggestion. */
        val suggestsCoach: Boolean = false,
    ) {
        enum class Status(val code: String) {
            /** A tip is waiting for the user's answer. */
            READY("ready"),

            /** The coach was asked and had nothing to show. Never retried. */
            UNAVAILABLE("unavailable"),

            /** The user took the focus and/or target ("Use it"). */
            ACCEPTED("accepted"),

            /** The user said "Not now" or "Got it". */
            DISMISSED("dismissed"),
            ;

            companion object {
                fun of(code: String?): Status? = entries.firstOrNull { it.code == code }
            }
        }

        val target: PlanSuggestionHistory.CoachTarget?
            get() = if (targetCode != null && targetValue != null) PlanSuggestionHistory.CoachTarget(targetCode, targetValue) else null

        /** Whether there's anything for "Use it" to save. */
        val offersAdvice: Boolean get() = focus != null || target != null

        companion object {
            /** The coach had nothing to show. */
            val Unavailable = CoachTip(Status.UNAVAILABLE)
        }
    }

    object NextWeekIntent {
        const val KEEP_SAME = "keep_same"
        const val MAKE_EASIER = "make_easier"
        const val PUSH_HARDER = "push_harder"
        const val CHANGE_FOCUS = "change_focus"

        val all = listOf(KEEP_SAME, MAKE_EASIER, PUSH_HARDER, CHANGE_FOCUS)
    }
}
