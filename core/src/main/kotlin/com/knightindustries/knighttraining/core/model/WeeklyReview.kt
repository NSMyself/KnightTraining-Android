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
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    /** Re-saving keeps the id, creation time and the coach's accepted advice. */
    fun updating(wentWell: String?, heldBack: String?, wishIDone: String?, nextWeekIntent: String?, now: Instant = Instant.now()) =
        copy(wentWell = wentWell, heldBack = heldBack, wishIDone = wishIDone, nextWeekIntent = nextWeekIntent, updatedAt = now)

    object NextWeekIntent {
        const val KEEP_SAME = "keep_same"
        const val MAKE_EASIER = "make_easier"
        const val PUSH_HARDER = "push_harder"
        const val CHANGE_FOCUS = "change_focus"

        val all = listOf(KEEP_SAME, MAKE_EASIER, PUSH_HARDER, CHANGE_FOCUS)
    }
}
