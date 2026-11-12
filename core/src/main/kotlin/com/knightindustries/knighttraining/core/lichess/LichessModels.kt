package com.knightindustries.knighttraining.core.lichess

import com.knightindustries.knighttraining.core.model.LichessStreakDay
import com.knightindustries.knighttraining.core.model.TimeControl
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

// Lichess payloads, decoded against the shapes iOS verified on real responses (iOS
// docs/lichess.md: three models were broken until checked against real payloads). Only the fields
// the app reads; unknown keys are ignored.

/** `/api/account` and `/api/user/{name}`. */
@Serializable
data class LichessUser(
    val id: String,
    val username: String,
    val title: String? = null,
    val profile: Profile? = null,
    val perfs: Perfs? = null,
) {
    @Serializable
    data class Profile(val flag: String? = null)

    @Serializable
    data class Perfs(
        val bullet: Perf? = null,
        val blitz: Perf? = null,
        val rapid: Perf? = null,
        val classical: Perf? = null,
        val puzzle: Perf? = null,
    )

    @Serializable
    data class Perf(val rating: Int, val games: Int = 0, val prov: Boolean? = null)
}

/** One row of `/api/games/user/{name}` (ndjson). Times are epoch milliseconds. */
@Serializable
data class LichessGame(
    val id: String,
    val rated: Boolean = false,
    /** Lichess's own speed bucket; the server-side `perfType` filter limits it to rapid/classical/blitz. */
    val speed: String,
    val status: String,
    val createdAt: Long,
    val lastMoveAt: Long,
    val clock: Clock? = null,
) {
    @Serializable
    data class Clock(val initial: Int, val increment: Int)

    val createdInstant: Instant get() = Instant.ofEpochMilli(createdAt)
    val lastMoveInstant: Instant get() = Instant.ofEpochMilli(lastMoveAt)

    /** Aborted or never started: nobody really played it, so it never counts. */
    val wasReallyPlayed: Boolean get() = status != "aborted" && status != "noStart"

    /** Measured, not estimated from the clock; never negative. */
    val durationSeconds: Int get() = maxOf(0L, (lastMoveAt - createdAt) / 1000).toInt()

    /** Rounded to the nearest minute, so a 3.5-minute clock reads as 4, not 3. */
    val timeControlLabel: String?
        get() = clock?.let { TimeControl(maxOf(1, (it.initial + 30) / 60), it.increment).label }

    /** The app's category for [speed], or `null` for anything outside rapid/classical/blitz. */
    val category: TimeControl.Category?
        get() = when (speed) {
            "rapid" -> TimeControl.Category.RAPID
            "classical" -> TimeControl.Category.CLASSICAL
            "blitz" -> TimeControl.Category.BLITZ
            else -> null
        }
}

/**
 * One row of `/api/puzzle/activity` (ndjson, not a JSON array). The real shape nests the puzzle:
 * `{"date":…, "win":…, "puzzle":{"id":…, "fen":…, "rating":…}}`.
 */
@Serializable
data class LichessPuzzleActivity(val date: Long, val win: Boolean, val puzzle: Puzzle) {
    @Serializable
    data class Puzzle(val id: String, val rating: Int = 0)

    val instant: Instant get() = Instant.ofEpochMilli(date)
}

/** `/api/storm/dashboard/{name}`: day totals only. Days without runs are left out entirely. */
@Serializable
data class LichessStormDashboard(val days: List<Day> = emptyList()) {
    @Serializable
    data class Day(
        /** `"YYYY/M/D"`, no zero padding. Compared as an opaque id, never parsed as a date. */
        @SerialName("_id") val id: String,
        val runs: Int,
        val score: Int = 0,
        /** Seconds played that day. */
        val time: Int = 0,
    )
}

/**
 * One day of `/api/user/{name}/activity` (a JSON array, newest first). Only Streak is read;
 * `streak` is absent on days without a run.
 */
@Serializable
data class LichessActivityFeedDay(val interval: Interval, val streak: Streak? = null) {
    @Serializable
    data class Interval(val start: Long, val end: Long)

    @Serializable
    data class Streak(val runs: Int, val score: Int)

    val streakDay: LichessStreakDay?
        get() = streak?.let {
            LichessStreakDay(Instant.ofEpochMilli(interval.start), Instant.ofEpochMilli(interval.end), it.runs, it.score)
        }
}

/** One series of `/api/user/{name}/rating-history`. Each point is `[year, month (0-11), day, rating]`. */
@Serializable
data class LichessRatingHistory(val name: String, val points: List<List<Int>> = emptyList()) {
    data class Point(val date: LocalDate, val rating: Int)

    val dataPoints: List<Point>
        get() = points.mapNotNull { point ->
            if (point.size != 4) return@mapNotNull null
            runCatching { Point(LocalDate.of(point[0], point[1] + 1, point[2]), point[3]) }.getOrNull()
        }
}

/** `/api/puzzle/dashboard/{days}`, for the weekly coach. No puzzles in the window comes back as a 404. */
@Serializable
data class LichessPuzzleDashboard(val days: Int, val global: Results, val themes: Map<String, Theme> = emptyMap()) {
    @Serializable
    data class Results(val nb: Int, val firstWins: Int, val replayWins: Int, val puzzleRatingAvg: Int, val performance: Int)

    @Serializable
    data class Theme(val theme: String, val results: Results)
}

/** One row of `/api/study/by/{name}` (ndjson): name and dates only, no chapters. */
@Serializable
data class LichessStudy(val id: String, val name: String, val createdAt: Long = 0, val updatedAt: Long = 0) {
    val url: String get() = "https://lichess.org/study/$id"
}
