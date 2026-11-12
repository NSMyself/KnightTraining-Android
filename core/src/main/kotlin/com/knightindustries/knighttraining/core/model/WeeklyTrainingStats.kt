package com.knightindustries.knighttraining.core.model

/**
 * The derived figures behind Home's "This week's activity" cards. Stays derived, never stored.
 * Goal completion rate isn't here: it's [WeeklyPlan.completionPercentage], which no list of
 * activities can answer.
 */
data class WeeklyTrainingStats(
    val totalSeconds: Int,
    /**
     * The code of the type the most *time* went into, `null` with no activities. A code, so the
     * grouping doesn't change with the language; the app maps it to a name.
     */
    val mostTrainedActivityCode: String?,
    /** Puzzles solved: reported quantities summed, not sessions counted. */
    val puzzleCount: Int,
    /** Games played, every time control. */
    val gameCount: Int,
    /** Games at a classical time control. Games without a recorded one don't count here. */
    val classicalGameCount: Int,
) {
    companion object {
        /** Every figure reads the same set of activities: no minimum-duration floor (iOS AGENTS.md §7). */
        fun from(activities: List<Activity>): WeeklyTrainingStats {
            // Most time, not most sessions. Ties go to the alphabetically first code, so the card
            // doesn't flicker and doesn't reorder by language.
            val mostTrained = activities
                .groupBy { it.activityType.code }
                .mapValues { (_, grouped) -> grouped.sumOf { it.duration } }
                .entries
                .maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key })
                ?.key

            val games = activities.filter { it.activityType.code == ActivityType.PLAYING }
            return WeeklyTrainingStats(
                totalSeconds = activities.sumOf { it.duration },
                mostTrainedActivityCode = mostTrained,
                // A skipped "how many?" contributes 0, never 1.
                puzzleCount = activities.filter { it.activityType.code == ActivityType.PUZZLES }.sumOf { it.count ?: 0 },
                gameCount = games.sumOf { it.count ?: 0 },
                classicalGameCount = games.filter { it.timeControl?.isClassical == true }.sumOf { it.count ?: 0 },
            )
        }
    }
}
