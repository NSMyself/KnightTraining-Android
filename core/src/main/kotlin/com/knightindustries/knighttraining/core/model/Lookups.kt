package com.knightindustries.knighttraining.core.model

import java.util.UUID

/**
 * A training category. `code` is the stable identity every query, statistic and preset match keys
 * on. There's no title: the app derives the display name from `code` (iOS AGENTS.md §14).
 */
data class ActivityType(val id: UUID, val code: String) {
    companion object {
        const val PUZZLES = "puzzles"
        const val PLAYING = "playing"
        const val ANALYZING = "analyzing"
        const val OPENINGS = "openings"
        const val ENDGAMES = "endgames"
        const val COACHING = "coaching"

        /** Fallback for uncategorized activities; never offered as a choice. */
        const val GENERAL = "general"

        /**
         * Every category a user can pick: the presets minus [GENERAL]. Unsorted: pickers sort by
         * the translated name, which only the app knows.
         */
        val pickableCodes: List<String> = listOf(PUZZLES, PLAYING, ANALYZING, OPENINGS, ENDGAMES, COACHING)
    }
}

/** What kind of thing a [Resource] is. Keyed on `code` for the same reasons as [ActivityType]. */
data class ResourceKind(val id: UUID, val code: String) {
    val supportsOwnership: Boolean
        get() = code == BOOK || code == COURSE

    companion object {
        const val BOOK = "book"
        const val COURSE = "course"
        const val VIDEO = "video"
        const val ARTICLE = "article"
        const val LICHESS_STUDY = "lichess_study"
    }
}

/** `count` | `duration` | `progress`, stored as those codes. */
enum class TargetType(val code: String) {
    /** "Solve 50 puzzles." */
    COUNT("count"),

    /** "Watch 2 hours of content." Values are seconds. */
    DURATION("duration"),

    /** "Complete 20% of this book." Values are percentages. */
    PROGRESS("progress"),
    ;

    companion object {
        fun fromCode(code: String): TargetType? = entries.firstOrNull { it.code == code }
    }
}

/** Where an [Activity] came from, stored as `manual` | `lichess_sync`. */
enum class ActivitySource(val code: String) {
    MANUAL("manual"),
    LICHESS_SYNC("lichess_sync"),
    ;

    companion object {
        fun fromCode(code: String): ActivitySource? = entries.firstOrNull { it.code == code }
    }
}
