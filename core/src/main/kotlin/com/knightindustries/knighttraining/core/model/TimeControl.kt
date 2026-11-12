package com.knightindustries.knighttraining.core.model

/**
 * A chess time control, stored as the label the picker produced (`"60+0"`, `"3+2"`, `"30 min"`)
 * and classified on read, so retuning the boundaries never needs a data migration.
 */
data class TimeControl(
    /** Base minutes on the clock. */
    val minutes: Int,
    /** Increment per move, in seconds. */
    val increment: Int = 0,
) {
    /** Round-trips with [parse] and matches what the picker displays. */
    val label: String
        get() = if (increment > 0) "$minutes+$increment" else "$minutes min"

    /**
     * Boundaries, in **base minutes**: bullet under 3, blitz under 10, rapid under 30, classical
     * up to just under a full day, daily from 24h.
     *
     * The increment is deliberately ignored: folding it in would let 25+10 land in classical,
     * contradicting "30 minutes or longer is classical" and the picker section the user chose it
     * from (iOS AGENTS.md §12).
     */
    val category: Category
        get() = when {
            minutes < 3 -> Category.BULLET
            minutes < 10 -> Category.BLITZ
            minutes < 30 -> Category.RAPID
            minutes < MINUTES_PER_DAY -> Category.CLASSICAL
            else -> Category.DAILY
        }

    val isClassical: Boolean
        get() = category == Category.CLASSICAL

    enum class Category { BULLET, BLITZ, RAPID, CLASSICAL, DAILY }

    companion object {
        private const val MINUTES_PER_DAY = 24 * 60

        /**
         * Parses `"10+5"` and `"30 min"`. Anything else is `null`, so callers degrade to "unknown
         * time control" rather than guessing a category.
         */
        fun parse(label: String): TimeControl? {
            val trimmed = label.trim()
            if (trimmed.isEmpty()) return null

            val plus = trimmed.indexOf('+')
            if (plus >= 0) {
                val minutes = trimmed.substring(0, plus).trim().toIntOrNull() ?: return null
                val increment = trimmed.substring(plus + 1).trim().toIntOrNull() ?: return null
                if (minutes <= 0 || increment < 0) return null
                return TimeControl(minutes, increment)
            }

            val minutes = trimmed.replace("min", "").trim().toIntOrNull() ?: return null
            return if (minutes > 0) TimeControl(minutes, 0) else null
        }
    }
}
