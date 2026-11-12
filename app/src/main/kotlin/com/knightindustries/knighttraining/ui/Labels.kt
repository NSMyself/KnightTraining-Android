package com.knightindustries.knighttraining.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.ManageSearch
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.knightindustries.knighttraining.R
import com.knightindustries.knighttraining.core.model.ActivityType
import com.knightindustries.knighttraining.core.model.RatingType
import com.knightindustries.knighttraining.core.model.ResourceKind
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.WeeklyGoal

/**
 * Display text for the codes `core` deals in. Names derive from the code at display time, never
 * from the database (iOS AGENTS.md §14).
 */
@StringRes
fun activityNameRes(code: String): Int = when (code) {
    ActivityType.PUZZLES -> R.string.activity_puzzles
    ActivityType.PLAYING -> R.string.activity_playing
    ActivityType.ANALYZING -> R.string.activity_analyzing
    ActivityType.OPENINGS -> R.string.activity_openings
    ActivityType.ENDGAMES -> R.string.activity_endgames
    ActivityType.COACHING -> R.string.activity_coaching
    else -> R.string.activity_general
}

@Composable
fun activityName(code: String): String = stringResource(activityNameRes(code))

fun activityIcon(code: String): ImageVector = when (code) {
    ActivityType.PUZZLES -> Icons.Rounded.Extension
    ActivityType.PLAYING -> Icons.Rounded.Timer
    ActivityType.ANALYZING -> Icons.Rounded.ManageSearch
    ActivityType.OPENINGS -> Icons.Rounded.AccountTree
    ActivityType.ENDGAMES -> Icons.Rounded.Flag
    ActivityType.COACHING -> Icons.Rounded.School
    else -> Icons.Rounded.AllInclusive
}

@StringRes
fun kindNameRes(code: String): Int = when (code) {
    ResourceKind.BOOK -> R.string.kind_book
    ResourceKind.COURSE -> R.string.kind_course
    ResourceKind.VIDEO -> R.string.kind_video
    ResourceKind.LICHESS_STUDY -> R.string.kind_lichess_study
    else -> R.string.kind_article
}

/** Section headers: "Books", "Lichess Studies". */
@StringRes
fun kindPluralRes(code: String): Int = when (code) {
    ResourceKind.BOOK -> R.string.kinds_book
    ResourceKind.COURSE -> R.string.kinds_course
    ResourceKind.VIDEO -> R.string.kinds_video
    ResourceKind.LICHESS_STUDY -> R.string.kinds_lichess_study
    else -> R.string.kinds_article
}

fun kindIcon(code: String): ImageVector = when (code) {
    ResourceKind.BOOK -> Icons.AutoMirrored.Rounded.MenuBook
    ResourceKind.COURSE -> Icons.Rounded.School
    ResourceKind.VIDEO -> Icons.Rounded.PlayCircle
    ResourceKind.LICHESS_STUDY -> Icons.Rounded.GridOn
    else -> Icons.AutoMirrored.Rounded.Article
}

/** The order kinds appear in the library and pickers. */
val kindOrder = listOf(ResourceKind.BOOK, ResourceKind.COURSE, ResourceKind.VIDEO, ResourceKind.ARTICLE, ResourceKind.LICHESS_STUDY)

/** A goal's own name, or its activity's. */
@Composable
fun goalTitle(goal: WeeklyGoal): String = blockTitle(goal.name, goal.activityType.code, goal.timeControlLabel)

/**
 * A block's title: its own name when it has one (typed, or a preset's), else derived from the
 * activity, games with their time control: "Playing (15+10)". Derived rather than stored, so it
 * follows the language (iOS AGENTS.md §14; iOS stores the composed text instead).
 */
@Composable
fun blockTitle(name: String?, activityTypeCode: String, timeControlLabel: String?): String =
    name ?: activityName(activityTypeCode).let { base -> timeControlLabel?.let { "$base ($it)" } ?: base }

/** "2 h 30 min", "45 min", "2 h". */
@Composable
fun durationText(seconds: Int): String {
    val minutes = seconds / 60
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours > 0 && rest > 0 -> stringResource(R.string.duration_hours_minutes, hours, rest)
        hours > 0 -> stringResource(R.string.duration_hours, hours)
        else -> stringResource(R.string.duration_minutes, minutes)
    }
}

/** "50 puzzles", "2 games", "1 h 30 min", "20%". */
@Composable
fun targetText(activityTypeCode: String, targetType: TargetType, value: Int): String = when (targetType) {
    TargetType.DURATION -> durationText(value)
    TargetType.PROGRESS -> stringResource(R.string.progress_percent, value)
    TargetType.COUNT -> when (activityTypeCode) {
        ActivityType.PUZZLES -> pluralStringResource(R.plurals.target_puzzles, value, value)
        ActivityType.PLAYING -> pluralStringResource(R.plurals.target_games, value, value)
        else -> value.toString()
    }
}

/** "12/50", "45/60 min": a value short of its target never reads as the target. */
@Composable
fun progressText(goal: WeeklyGoal): String = when (goal.targetType) {
    TargetType.DURATION -> stringResource(
        R.string.progress_minutes,
        WeeklyGoal.displayMinutes(goal.currentValue, goal.targetValue),
        goal.targetValue / 60,
    )
    TargetType.PROGRESS -> stringResource(R.string.progress_percent, goal.currentValue)
    TargetType.COUNT -> stringResource(R.string.progress_count, goal.currentValue, goal.targetValue)
}

/** `MM:SS`, or `H:MM:SS` from an hour. */
fun clockText(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** Rating type names; [RatingType] codes never reach the screen. */
fun ratingTypeNameRes(type: String): Int = when (type) {
    RatingType.BULLET -> R.string.rating_bullet
    RatingType.BLITZ -> R.string.rating_blitz
    RatingType.RAPID -> R.string.rating_rapid
    RatingType.CLASSICAL -> R.string.rating_classical
    else -> R.string.rating_puzzle
}

/** Slowest first, the way a club player ranks them; puzzles last. */
val ratingTypeOrder = listOf(RatingType.CLASSICAL, RatingType.RAPID, RatingType.BLITZ, RatingType.BULLET, RatingType.PUZZLE)
