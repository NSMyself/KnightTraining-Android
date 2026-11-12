package com.knightindustries.knighttraining.core.lichess

import com.knightindustries.knighttraining.core.model.EloSnapshot
import com.knightindustries.knighttraining.core.model.LichessActivityBatch
import com.knightindustries.knighttraining.core.model.LichessStreakDetection
import com.knightindustries.knighttraining.core.model.Milestone
import com.knightindustries.knighttraining.core.model.RatingType
import com.knightindustries.knighttraining.core.model.Resource
import com.knightindustries.knighttraining.core.model.ResourceKind
import com.knightindustries.knighttraining.core.store.TrainingStore
import com.knightindustries.knighttraining.core.store.allResources
import com.knightindustries.knighttraining.core.store.ensureLichessAccount
import com.knightindustries.knighttraining.core.store.hasEloHistory
import com.knightindustries.knighttraining.core.store.hasEloSnapshot
import com.knightindustries.knighttraining.core.store.lichessAccount
import com.knightindustries.knighttraining.core.store.lichessBatchedExternalIds
import com.knightindustries.knighttraining.core.store.reevaluateMilestones
import com.knightindustries.knighttraining.core.store.saveEloSnapshots
import com.knightindustries.knighttraining.core.store.saveLichessActivityBatch
import com.knightindustries.knighttraining.core.store.saveResources
import com.knightindustries.knighttraining.core.store.updateLastGameSyncAt
import com.knightindustries.knighttraining.core.store.updateLastPuzzleSyncAt
import com.knightindustries.knighttraining.core.store.updateStormState
import com.knightindustries.knighttraining.core.store.updateStreakState
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/**
 * Every Lichess sync, ported from iOS's five services. Each one fails silently (a failed sync just
 * means nothing new this time) and **never backfills**: the first run for an account only records
 * a baseline, so years of history are never offered as "just played".
 *
 * Calls go one after another, never in parallel: Lichess allows one request at a time, and
 * [LichessRequestGate] is a safety net, not the plan.
 */
class LichessSync(
    private val store: TrainingStore,
    private val client: LichessClient,
    private val now: () -> Instant = Instant::now,
    private val log: (String, Throwable) -> Unit = { _, _ -> },
) {
    /**
     * Ratings, account identity, then milestones. Creates the local row for a signed-in user, so
     * it's also how the activity syncs get an account to work from.
     *
     * @return the milestones that just completed.
     */
    suspend fun syncRatings(user: LichessUser? = null): List<Milestone> = quietly("ratings", emptyList()) {
        val current = user ?: client.fetchAccount()
        val accountId = store.ensureLichessAccount(current.username, current.title, current.profile?.flag)
        if (!store.hasEloHistory(accountId)) {
            backfillRatings(accountId, current.username)
        } else if (!store.hasEloSnapshot(accountId, store.calendar().localDate(now()))) {
            captureRatings(accountId, current)
        }
        store.reevaluateMilestones()
    }

    /** Games, puzzles, Storm, Streak: each offer kind in turn. */
    suspend fun syncActivity() {
        syncGames()
        syncPuzzles()
        syncStorm()
        syncStreak()
    }

    suspend fun syncGames() = quietly("games", Unit) {
        val account = store.lichessAccount() ?: return@quietly
        val since = account.lastGameSyncAt ?: run {
            store.updateLastGameSyncAt(account.id, now())
            return@quietly
        }
        val games = client.fetchGames(account.username, since).filter { it.wasReallyPlayed }
        if (games.isEmpty()) return@quietly
        val seen = store.lichessBatchedExternalIds(account.id, LichessActivityBatch.GAME)
        groupGames(games.filter { it.id !in seen }.sortedBy { it.createdAt }, account.id).forEach { store.saveLichessActivityBatch(it) }
        // Up to the latest game seen, never "now": a game still running at sync time would
        // otherwise fall behind the mark once it finished and never be offered.
        store.updateLastGameSyncAt(account.id, games.maxOf { it.createdInstant })
    }

    suspend fun syncPuzzles() = quietly("puzzles", Unit) {
        val account = store.lichessAccount() ?: return@quietly
        val since = account.lastPuzzleSyncAt ?: run {
            store.updateLastPuzzleSyncAt(account.id, now())
            return@quietly
        }
        val entries = client.fetchPuzzleActivity(since)
        if (entries.isEmpty()) return@quietly
        val seen = store.lichessBatchedExternalIds(account.id, LichessActivityBatch.PUZZLE)
        groupPuzzles(entries.filter { it.puzzle.id !in seen }.sortedBy { it.date }, account.id).forEach { store.saveLichessActivityBatch(it) }
        store.updateLastPuzzleSyncAt(account.id, entries.maxOf { it.instant })
    }

    /**
     * Storm has no per-run data, only day totals that grow until the day rolls over, so a batch is
     * the growth of the latest day since the last look. A new day id means nothing on it is counted.
     */
    suspend fun syncStorm() = quietly("storm", Unit) {
        val account = store.lichessAccount() ?: return@quietly
        // Two days, so a sync just after midnight still sees yesterday's late growth.
        val latest = client.fetchStormDashboard(account.username, days = 2).days.firstOrNull() ?: return@quietly
        val lastDay = account.lastStormDayId
        val previousRuns = account.lastStormRunsForDay
        val previousSeconds = account.lastStormSecondsForDay
        if (lastDay == null || previousRuns == null || previousSeconds == null) {
            store.updateStormState(account.id, latest.id, latest.runs, latest.time)
            return@quietly
        }
        val runsCounted = if (latest.id == lastDay) previousRuns else 0
        val secondsCounted = if (latest.id == lastDay) previousSeconds else 0
        val newRuns = latest.runs - runsCounted
        val newSeconds = maxOf(0, latest.time - secondsCounted)
        if (newRuns > 0) {
            val at = now()
            store.saveLichessActivityBatch(
                LichessActivityBatch(
                    accountId = account.id,
                    kind = LichessActivityBatch.STORM,
                    // No real run ids: a placeholder for the shared schema, not a dedup key.
                    externalIds = listOf("storm:${latest.id}:$runsCounted-${latest.runs}"),
                    itemCount = newRuns,
                    totalDurationSeconds = newSeconds,
                    // Backdated by the measured time, so the span agrees with the duration.
                    firstItemAt = at.minusSeconds(newSeconds.toLong()),
                    lastItemAt = at,
                    createdAt = at,
                ),
            )
        }
        store.updateStormState(account.id, latest.id, latest.runs, latest.time)
    }

    /** Streak has no endpoint; the public activity feed's per-day runs are the only signal. */
    suspend fun syncStreak() = quietly("streak", Unit) {
        val account = store.lichessAccount() ?: return@quietly
        val days = client.fetchActivity(account.username).mapNotNull { it.streakDay }
        val at = now()
        val previousRuns = account.lastStreakRuns
        val previousScore = account.lastStreakScore
        val previousSync = account.lastStreakSyncAt
        if (previousRuns == null || previousScore == null || previousSync == null) {
            val baseline = LichessStreakDetection.detect(days, at, 0, 0, at)
            store.updateStreakState(account.id, baseline.runsForCurrentDay, baseline.scoreForCurrentDay, at)
            return@quietly
        }
        val result = LichessStreakDetection.detect(days, previousSync, previousRuns, previousScore, at)
        if (result.newRuns > 0) {
            store.saveLichessActivityBatch(
                LichessActivityBatch(
                    accountId = account.id,
                    kind = LichessActivityBatch.STREAK,
                    externalIds = listOf("streak:${previousSync.epochSecond}-${at.epochSecond}"),
                    itemCount = result.newRuns,
                    // Lichess records no time for Streak; the popup asks.
                    totalDurationSeconds = 0,
                    firstItemAt = previousSync,
                    lastItemAt = at,
                    bestScore = result.bestScore,
                    createdAt = at,
                ),
            )
        }
        store.updateStreakState(account.id, result.runsForCurrentDay, result.scoreForCurrentDay, at)
    }

    /**
     * Adds studies the library doesn't have yet as Lichess Study resources, matched by the study id
     * in their link, so a study added by hand isn't duplicated. Never removes or renames one.
     */
    suspend fun syncStudies() = quietly("studies", Unit) {
        val account = store.lichessAccount() ?: return@quietly
        val kind = store.resourceKind(ResourceKind.LICHESS_STUDY) ?: return@quietly
        val studies = client.fetchStudies(account.username)
        if (studies.isEmpty()) return@quietly
        val known = store.allResources().mapNotNull { studyId(it.url) }.toSet()
        val new = studies.filter { it.id !in known }.map {
            Resource(kind = kind, title = it.name, url = it.url, isOwned = true, isAutoImported = true, lastAccessedAt = null, createdAt = now())
        }
        store.saveResources(new)
    }

    private suspend fun backfillRatings(accountId: UUID, username: String) {
        val calendar = store.calendar()
        val snapshots = client.fetchRatingHistory(username).flatMap { history ->
            val type = ratingType(history.name) ?: return@flatMap emptyList()
            history.dataPoints.map { EloSnapshot(accountId = accountId, rating = it.rating, ratingType = type, capturedAt = calendar.startOf(it.date)) }
        }
        store.saveEloSnapshots(snapshots)
    }

    private suspend fun captureRatings(accountId: UUID, user: LichessUser) {
        val at = now()
        val perfs = user.perfs ?: return
        val snapshots = listOf(
            RatingType.BULLET to perfs.bullet,
            RatingType.BLITZ to perfs.blitz,
            RatingType.RAPID to perfs.rapid,
            RatingType.CLASSICAL to perfs.classical,
            RatingType.PUZZLE to perfs.puzzle,
        ).mapNotNull { (type, perf) -> perf?.let { EloSnapshot(accountId = accountId, rating = it.rating, ratingType = type, capturedAt = at) } }
        store.saveEloSnapshots(snapshots)
    }

    private suspend fun <T> quietly(what: String, fallback: T, block: suspend () -> T): T = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        log("Lichess $what sync failed", failure)
        fallback
    }

    companion object {
        /** The study id in a lichess.org study link, or `null` for anything else. */
        fun studyId(url: String?): String? = url?.let { STUDY_LINK.find(it) }?.groupValues?.get(1)

        private val STUDY_LINK = Regex("""^https?://(?:[a-z]+\.)?lichess\.org/study/([A-Za-z0-9]{8})""")

        fun ratingType(lichessName: String): String? = when (lichessName.lowercase()) {
            "bullet" -> RatingType.BULLET
            "blitz" -> RatingType.BLITZ
            "rapid" -> RatingType.RAPID
            "classical" -> RatingType.CLASSICAL
            "puzzles", "puzzle" -> RatingType.PUZZLE
            else -> null
        }

        /**
         * Splits sorted games into sittings: a new batch whenever the category changes or the gap
         * after the previous game's last move exceeds [LichessActivityBatch.BATCH_GAP_SECONDS].
         */
        fun groupGames(games: List<LichessGame>, accountId: UUID): List<LichessActivityBatch> =
            split(games.filter { it.category != null }) { previous, next ->
                previous.category != next.category ||
                    (next.createdAt - previous.lastMoveAt) / 1000 > LichessActivityBatch.BATCH_GAP_SECONDS
            }.map { sitting ->
                LichessActivityBatch(
                    accountId = accountId,
                    kind = LichessActivityBatch.GAME,
                    category = sitting.first().category!!.name.lowercase(),
                    externalIds = sitting.map { it.id },
                    itemCount = sitting.size,
                    totalDurationSeconds = sitting.sumOf { it.durationSeconds },
                    timeControlLabel = sitting.mapNotNull { it.timeControlLabel }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key,
                    firstItemAt = sitting.minOf { it.createdInstant },
                    lastItemAt = sitting.maxOf { it.lastMoveInstant },
                )
            }

        /** Puzzles have one timestamp each, so a batch's duration is its bounding box: an estimate. */
        fun groupPuzzles(entries: List<LichessPuzzleActivity>, accountId: UUID): List<LichessActivityBatch> =
            split(entries) { previous, next -> (next.date - previous.date) / 1000 > LichessActivityBatch.BATCH_GAP_SECONDS }
                .map { sitting ->
                    val first = sitting.minOf { it.instant }
                    val last = sitting.maxOf { it.instant }
                    LichessActivityBatch(
                        accountId = accountId,
                        kind = LichessActivityBatch.PUZZLE,
                        externalIds = sitting.map { it.puzzle.id },
                        itemCount = sitting.size,
                        totalDurationSeconds = maxOf(0L, last.epochSecond - first.epochSecond).toInt(),
                        firstItemAt = first,
                        lastItemAt = last,
                    )
                }

        private fun <T> split(items: List<T>, breaksBetween: (T, T) -> Boolean): List<List<T>> {
            val sittings = mutableListOf<MutableList<T>>()
            for (item in items) {
                val current = sittings.lastOrNull()
                if (current == null || breaksBetween(current.last(), item)) sittings.add(mutableListOf(item)) else current.add(item)
            }
            return sittings
        }
    }
}
