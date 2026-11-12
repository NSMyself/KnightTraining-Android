package com.knightindustries.knighttraining.feature.plan

import androidx.annotation.StringRes
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.model.ActivityType
import com.knightindustries.knighttraining.core.model.PlanEstimate
import com.knightindustries.knighttraining.core.model.PlanEstimator
import com.knightindustries.knighttraining.core.model.PlanEstimatorBlock
import com.knightindustries.knighttraining.core.model.TargetType

/**
 * The five hand-written presets for 1000–2000 players, mirroring iOS `PlanTemplatePreset.all`
 * (2026-11-03, awaiting a coach's review). Keep the two in step.
 */
data class PlanPreset(
    @StringRes val title: Int,
    @StringRes val description: Int,
    val items: List<Item>,
) {
    data class Item(
        @StringRes val title: Int,
        val activityTypeCode: String,
        val targetType: TargetType,
        val targetValue: Int,
        val timeControlLabel: String? = null,
    )

    val estimate: PlanEstimate?
        get() = PlanEstimator.estimate(items.map { PlanEstimatorBlock(it.activityTypeCode, it.targetType, it.targetValue, it.timeControlLabel) })

    companion object {
        private fun puzzles(@StringRes title: Int, count: Int) = Item(title, ActivityType.PUZZLES, TargetType.COUNT, count)
        private fun games(@StringRes title: Int, count: Int, timeControl: String) = Item(title, ActivityType.PLAYING, TargetType.COUNT, count, timeControl)
        private fun minutes(@StringRes title: Int, code: String, minutes: Int) = Item(title, code, TargetType.DURATION, minutes * 60)

        val all = listOf(
            PlanPreset(
                R.string.preset_tactics_title, R.string.preset_tactics_description,
                listOf(
                    puzzles(R.string.preset_item_puzzles_tactical, 60),
                    games(R.string.preset_item_playing_rapid, 2, "15+10"),
                    minutes(R.string.preset_item_analysis_own, ActivityType.ANALYZING, 30),
                ),
            ),
            PlanPreset(
                R.string.preset_slow_title, R.string.preset_slow_description,
                listOf(
                    games(R.string.preset_item_playing_classical, 2, "30 min"),
                    minutes(R.string.preset_item_analysis_own, ActivityType.ANALYZING, 60),
                    puzzles(R.string.preset_item_puzzles_calculation, 20),
                ),
            ),
            PlanPreset(
                R.string.preset_endgames_title, R.string.preset_endgames_description,
                listOf(
                    minutes(R.string.preset_item_endgames_rook, ActivityType.ENDGAMES, 45),
                    minutes(R.string.preset_item_endgames_king_pawn, ActivityType.ENDGAMES, 30),
                    puzzles(R.string.preset_item_puzzles_endgame, 20),
                    games(R.string.preset_item_playing_rapid, 1, "15+10"),
                ),
            ),
            PlanPreset(
                R.string.preset_openings_title, R.string.preset_openings_description,
                listOf(
                    minutes(R.string.preset_item_openings_repertoire, ActivityType.OPENINGS, 45),
                    games(R.string.preset_item_playing_rapid, 3, "10+5"),
                    minutes(R.string.preset_item_analysis_opening, ActivityType.ANALYZING, 20),
                ),
            ),
            PlanPreset(
                R.string.preset_busy_title, R.string.preset_busy_description,
                listOf(
                    puzzles(R.string.preset_item_puzzles_tactical, 30),
                    games(R.string.preset_item_playing_rapid, 1, "10 min"),
                    minutes(R.string.preset_item_analysis_own, ActivityType.ANALYZING, 15),
                ),
            ),
        )

        /**
         * The picker's sections; each preset must classify into the section it's shown under
         * (`TimeControlTest.pickerPresetsMatchTheirDisplayedSection`).
         */
        val timeControlSections: List<Pair<Int, List<String>>> = listOf(
            R.string.time_control_bullet to listOf("1 min", "1+1", "2+1"),
            R.string.time_control_blitz to listOf("3 min", "3+2", "5 min"),
            R.string.time_control_rapid to listOf("10 min", "10+5", "15+10"),
            R.string.time_control_classical to listOf("30 min", "60 min", "90+30"),
        )
    }
}
