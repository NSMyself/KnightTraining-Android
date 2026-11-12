package com.knightindustries.knighttraining.core.model

/** A rough "how long will this plan take", shown while building a plan. Never stored. */
data class PlanEstimate(
    val minutes: Int,
    /** Some block couldn't be estimated and was left out: show [minutes] as a floor ("~2h+"). */
    val isLowerBound: Boolean,
)

/** One block, reduced to what estimation needs; runs against unsaved plan-builder state. */
data class PlanEstimatorBlock(
    val activityTypeCode: String,
    val targetType: TargetType,
    val targetValue: Int,
    val timeControlLabel: String?,
)

object PlanEstimator {
    /** Minutes per puzzle for a count-based Puzzles block, tuned for ~1000–2000 players. */
    const val PUZZLE_MINUTES_PER_UNIT = 2

    /** Moves per player the increment is added for: the usual 40-move convention. */
    private const val INCREMENT_MOVES = 40

    /**
     * One full-length game from the player's week: both clocks plus both increments over 40
     * moves. Unlike [TimeControl.category], the increment counts here: it's real time spent.
     */
    fun gameMinutes(timeControl: TimeControl): Int {
        val incrementSeconds = 2 * INCREMENT_MOVES * timeControl.increment
        return 2 * timeControl.minutes + Math.round(incrementSeconds / 60.0).toInt()
    }

    /** `null` only when nothing could be estimated at all. */
    fun estimate(blocks: List<PlanEstimatorBlock>): PlanEstimate? {
        var minutes = 0
        var excludedAny = false

        for (block in blocks) {
            when {
                block.targetType == TargetType.DURATION -> minutes += block.targetValue / 60
                block.targetType == TargetType.COUNT && block.activityTypeCode == ActivityType.PUZZLES ->
                    minutes += block.targetValue * PUZZLE_MINUTES_PER_UNIT
                block.targetType == TargetType.COUNT && block.activityTypeCode == ActivityType.PLAYING -> {
                    val timeControl = block.timeControlLabel?.let(TimeControl::parse)
                    if (timeControl != null) minutes += block.targetValue * gameMinutes(timeControl) else excludedAny = true
                }
                // Other count types and progress blocks aren't reachable from the builder; left
                // out rather than guessed.
                else -> excludedAny = true
            }
        }

        return if (minutes > 0) PlanEstimate(minutes, excludedAny) else null
    }
}
