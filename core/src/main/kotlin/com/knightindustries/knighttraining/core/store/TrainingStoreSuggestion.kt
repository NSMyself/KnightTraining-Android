package com.knightindustries.knighttraining.core.store

import androidx.room.useReaderConnection
import androidx.sqlite.SQLiteStatement
import com.knightindustries.knighttraining.core.model.Account
import com.knightindustries.knighttraining.core.model.CatalogCourse
import com.knightindustries.knighttraining.core.model.PlanSuggestionEngine
import com.knightindustries.knighttraining.core.model.PlanSuggestionHistory
import com.knightindustries.knighttraining.core.model.RatingType
import com.knightindustries.knighttraining.core.model.TimeControl
import com.knightindustries.knighttraining.core.model.WeekCalendar
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * What the plan suggester reads about the player, in one read: the last [weeks] committed weeks
 * before this one, the latest game's time control, resources in progress, the Lichess rating, the
 * last review's accepted coach advice and the ChessMood courses they don't have. Port of iOS
 * `AppDatabase.planSuggestionHistory`.
 */
suspend fun TrainingStore.planSuggestionHistory(
    weeks: Int = 4,
    at: Instant = now(),
    calendar: WeekCalendar = calendar(),
): PlanSuggestionHistory {
    val currentWeek = calendar.weekStart(at)
    return db.useReaderConnection { connection ->
        suspend fun <T> query(sql: String, vararg args: Any?, row: (SQLiteStatement) -> T): List<T> = connection.usePrepared(sql) { statement ->
            args.forEachIndexed { index, value ->
                when (value) {
                    null -> statement.bindNull(index + 1)
                    is Long -> statement.bindLong(index + 1, value)
                    is Int -> statement.bindLong(index + 1, value.toLong())
                    else -> statement.bindText(index + 1, value.toString())
                }
            }
            buildList { while (statement.step()) add(row(statement)) }
        }
        fun SQLiteStatement.intOrNull(column: Int): Int? = if (isNull(column)) null else getLong(column).toInt()
        fun SQLiteStatement.textOrNull(column: Int): String? = if (isNull(column)) null else getText(column)

        // Committed weeks, newest first, with the minutes trained and their (non-bonus) goals.
        val outcomes = mutableMapOf<String, PlanSuggestionHistory.GoalOutcome>()
        val plans = query("SELECT id, weekStart FROM weekly_plan WHERE weekStart < ? ORDER BY weekStart DESC LIMIT ?", currentWeek, weeks) {
            it.getText(0) to LocalDate.parse(it.getText(1))
        }
        val committedWeeks = plans.map { (planId, start) ->
            val seconds = query(
                "SELECT COALESCE(SUM(duration), 0) FROM activity WHERE startedAt >= ? AND startedAt < ? AND endedAt IS NOT NULL",
                calendar.startOf(start).toEpochMilli(), calendar.startOf(start.plusDays(7)).toEpochMilli(),
            ) { it.getLong(0) }.single()
            val goals = query(
                """
                SELECT t.code, g.currentValue >= g.targetValue FROM weekly_goal g JOIN activity_type t ON t.id = g.activityTypeId
                WHERE g.weeklyPlanId = ? AND g.isBonus = 0
                """,
                planId,
            ) { it.getText(0) to (it.getLong(1) != 0L) }
            for ((code, hit) in goals) {
                val previous = outcomes[code] ?: PlanSuggestionHistory.GoalOutcome(0, 0)
                outcomes[code] = PlanSuggestionHistory.GoalOutcome(previous.hit + if (hit) 1 else 0, previous.total + 1)
            }
            PlanSuggestionHistory.Week((seconds / 60).toInt(), goals.count { it.second }, goals.size)
        }

        val timeControl = query(
            """
            SELECT a.timeControlLabel FROM activity a JOIN activity_type t ON t.id = a.activityTypeId
            WHERE t.code = 'playing' AND a.timeControlLabel IS NOT NULL AND a.endedAt IS NOT NULL
            ORDER BY a.startedAt DESC LIMIT 1
            """,
        ) { it.getText(0) }.firstOrNull()

        // Each unfinished resource under the activity it was logged under most, most recent first.
        val codes = PlanSuggestionEngine.activityCodesOrder
        val resourceRows = query(
            """
            SELECT r.id, r.title, t.code, COUNT(*) AS sessions FROM resource r
            JOIN activity a ON a.resourceId = r.id JOIN activity_type t ON t.id = a.activityTypeId
            WHERE r.progressPercentage < 100 AND t.code IN (${codes.joinToString { "?" }})
            GROUP BY r.id, t.code
            ORDER BY COALESCE(r.lastAccessedAt, r.updatedAt) DESC, sessions DESC
            """,
            *codes.toTypedArray(),
        ) { Triple(it.getText(0), it.getText(1) to it.getText(2), it.getLong(3)) }
        val best = linkedMapOf<String, Pair<Pair<String, String>, Long>>()
        for ((id, titleAndCode, sessions) in resourceRows) {
            val current = best[id]
            if (current == null || sessions > current.second) best[id] = titleAndCode to sessions
        }
        val activeResources = best.map { (id, value) -> PlanSuggestionHistory.ActiveResource(UUID.fromString(id), value.first.first, value.first.second) }

        // The rating in the time control the player plays, else rapid, classical, blitz, bullet; from the last 90 days.
        val latest = linkedMapOf<String, Int>()
        query(
            """
            SELECT s.ratingType, s.rating FROM elo_snapshot s JOIN account acc ON acc.id = s.accountId
            WHERE acc.platform = ? AND s.capturedAt >= ? ORDER BY s.capturedAt DESC
            """,
            Account.LICHESS, at.minus(90, ChronoUnit.DAYS).toEpochMilli(),
        ) { it.getText(0) to it.getLong(1).toInt() }.forEach { (type, rating) -> latest.putIfAbsent(type, rating) }
        val preferred = timeControl?.let(TimeControl::parse)?.category?.name?.lowercase()
        val ratingType = (listOfNotNull(preferred) + listOf(RatingType.RAPID, RatingType.CLASSICAL, RatingType.BLITZ, RatingType.BULLET))
            .firstOrNull { it in latest }

        // Only the latest review before this week counts: older advice isn't carried forward.
        val coach = query(
            """
            SELECT r.nextWeekFocus, r.nextWeekTargetCode, r.nextWeekTargetValue FROM weekly_review r
            JOIN weekly_plan p ON p.id = r.weeklyPlanId WHERE p.weekStart < ? ORDER BY p.weekStart DESC LIMIT 1
            """,
            currentWeek,
        ) { Triple(it.textOrNull(0), it.textOrNull(1), it.intOrNull(2)) }.firstOrNull()

        val candidates = query(
            """
            SELECT c.id, c.title, c.topicCode, c.minRating, c.maxRating, c.lengthMinutes FROM catalog_course c
            WHERE c.platform = ? AND c.topicCode IS NOT NULL
              AND NOT EXISTS (SELECT 1 FROM resource r WHERE r.catalogCourseId = c.id)
            ORDER BY c.title
            """,
            CatalogCourse.CHESSMOOD,
        ) {
            PlanSuggestionHistory.CatalogCandidate(UUID.fromString(it.getText(0)), it.getText(1), it.getText(2), it.intOrNull(3), it.intOrNull(4), it.intOrNull(5))
        }

        PlanSuggestionHistory(
            weeks = committedWeeks,
            goalOutcomes = outcomes,
            recentTimeControlLabel = timeControl,
            activeResources = activeResources,
            lichessRating = ratingType?.let(latest::get),
            lichessRatingType = ratingType,
            coachFocus = coach?.first?.takeIf { it.isNotEmpty() },
            coachTarget = coach?.let { (_, code, value) -> if (code != null && value != null) PlanSuggestionHistory.CoachTarget(code, value) else null },
            catalogCandidates = candidates,
        )
    }
}
