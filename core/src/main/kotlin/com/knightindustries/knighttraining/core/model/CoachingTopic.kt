package com.knightindustries.knighttraining.core.model

/**
 * What a coaching lesson covered. Informational only: topics never credit a goal (a lesson on
 * endgames is still `coaching`). [ANALYZING] reuses the activity type's own code.
 */
enum class CoachingTopic(val code: String) {
    OPENINGS("openings"),
    MIDDLEGAME("middlegame"),
    ENDGAMES("endgames"),
    TACTICS("tactics"),
    STRATEGY("strategy"),
    ANALYZING("analyzing"),
    ;

    companion object {
        /** The stored form: codes in declaration order, deduplicated, `null` when empty. */
        fun encode(topics: Collection<CoachingTopic>): String? =
            entries.filter { it in topics }.takeIf { it.isNotEmpty() }?.joinToString(",") { it.code }

        /** Unknown codes (a topic removed in a later version) are dropped, not fatal. */
        fun decode(stored: String?): List<CoachingTopic> {
            if (stored == null) return emptyList()
            val codes = stored.split(",").toSet()
            return entries.filter { it.code in codes }
        }
    }
}

/**
 * A coarse part of day from a local start hour. Deliberately not a clock time: a handful of
 * sessions can't support "around 7 PM".
 */
enum class TimeOfDayBucket {
    MORNING,
    AFTERNOON,
    EVENING,
    NIGHT,
    ;

    companion object {
        /** [hour] is local, 0..23. */
        fun forHour(hour: Int): TimeOfDayBucket = when (hour) {
            in 5..11 -> MORNING
            in 12..16 -> AFTERNOON
            in 17..20 -> EVENING
            else -> NIGHT
        }
    }
}
