package com.knightindustries.knighttraining.core.store

import com.knightindustries.knighttraining.core.model.Account
import com.knightindustries.knighttraining.core.model.Activity
import com.knightindustries.knighttraining.core.model.ActivitySource
import com.knightindustries.knighttraining.core.model.EloSnapshot
import com.knightindustries.knighttraining.core.model.LichessActivityBatch
import com.knightindustries.knighttraining.core.model.WeekCalendar
import com.knightindustries.knighttraining.core.model.WeeklyElo
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.core.persistence.toDomain
import com.knightindustries.knighttraining.core.persistence.toEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// MARK: - Account

suspend fun TrainingStore.lichessAccount(): Account? = db.lichessDao().account(Account.LICHESS)?.toDomain()

/**
 * The local Lichess row for a signed-in user, keeping an existing row's id: a new id would cascade
 * away its rating history. Only the identity columns move, and only when they changed.
 */
suspend fun TrainingStore.ensureLichessAccount(username: String, title: String?, flag: String?): UUID = write {
    val dao = db.lichessDao()
    val existing = dao.account(Account.LICHESS)
    if (existing == null) {
        val account = Account(platform = Account.LICHESS, username = username, title = title, flag = flag, createdAt = now())
        accountDao.upsert(account.toEntity())
        account.id
    } else {
        if (existing.username != username || existing.title != title || existing.flag != flag) {
            dao.updateIdentity(existing.id, username, title, flag, now())
        }
        existing.id
    }
}

suspend fun TrainingStore.updateLastGameSyncAt(accountId: UUID, at: Instant) = db.lichessDao().updateLastGameSyncAt(accountId, at, now())

suspend fun TrainingStore.updateLastPuzzleSyncAt(accountId: UUID, at: Instant) = db.lichessDao().updateLastPuzzleSyncAt(accountId, at, now())

suspend fun TrainingStore.updateStormState(accountId: UUID, dayId: String, runs: Int, seconds: Int) =
    db.lichessDao().updateStormState(accountId, dayId, runs, seconds, now())

suspend fun TrainingStore.updateStreakState(accountId: UUID, runs: Int, score: Int, syncedAt: Instant) =
    db.lichessDao().updateStreakState(accountId, runs, score, syncedAt, now())

/** Disconnect: the row and, by cascade, its rating history and pending offers. Logged activities stay. */
suspend fun TrainingStore.deleteLichessAccount() = write {
    db.lichessDao().account(Account.LICHESS)?.let { db.lichessDao().deleteAccount(it.id) }
}

// MARK: - Ratings

suspend fun TrainingStore.hasEloHistory(accountId: UUID): Boolean = db.lichessDao().snapshotCount(accountId) > 0

suspend fun TrainingStore.hasEloSnapshot(accountId: UUID, day: LocalDate, calendar: WeekCalendar = calendar()): Boolean =
    db.lichessDao().hasSnapshotBetween(accountId, calendar.startOf(day), calendar.startOf(day.plusDays(1)))

/** A whole backfill in one transaction, so the surface sees one change. */
suspend fun TrainingStore.saveEloSnapshots(snapshots: List<EloSnapshot>) {
    if (snapshots.isEmpty()) return
    write { db.lichessDao().insertSnapshots(snapshots.map { it.toEntity() }) }
}

suspend fun TrainingStore.eloSnapshots(accountId: UUID, ratingType: String): List<EloSnapshot> =
    db.lichessDao().snapshots(accountId, ratingType).map { it.toDomain() }

/** Latest rating per type for the connected Lichess account; empty when none is connected. */
suspend fun TrainingStore.latestLichessRatings(): Map<String, Int> = read { latestRatingsInTransaction() }

private suspend fun TrainingStore.latestRatingsInTransaction(): Map<String, Int> {
    val accountId = db.lichessDao().account(Account.LICHESS)?.id ?: return emptyMap()
    return db.lichessDao().latestRatings(accountId).associate { it.ratingType to it.rating }
}

/**
 * The last [weeks] weeks, oldest first and ending with the week containing [at], each with the
 * rating current at that week's end. A week with no snapshot keeps its place with `null`.
 */
suspend fun TrainingStore.weeklyEloHistory(
    accountId: UUID,
    ratingType: String,
    weeks: Int = 8,
    at: Instant = now(),
    calendar: WeekCalendar = calendar(),
): List<WeeklyElo> {
    if (weeks <= 0) return emptyList()
    val current = calendar.weekStart(at)
    return read {
        (0 until weeks).map { index ->
            val weekStart = current.minusWeeks((weeks - 1 - index).toLong())
            WeeklyElo(weekStart, db.lichessDao().latestRatingBefore(accountId, ratingType, calendar.startOf(weekStart.plusDays(7))))
        }
    }
}

// MARK: - Detected batches

/** Every id already in a batch of [kind], whatever its answer: never considered again. */
suspend fun TrainingStore.lichessBatchedExternalIds(accountId: UUID, kind: String): Set<String> =
    db.lichessDao().batchedExternalIds(accountId, kind).flatMap { it.split(',') }.filter { it.isNotEmpty() }.toSet()

suspend fun TrainingStore.saveLichessActivityBatch(batch: LichessActivityBatch) = db.lichessDao().insertBatch(batch.toEntity())

suspend fun TrainingStore.pendingLichessActivityBatches(): List<LichessActivityBatch> =
    db.lichessDao().pendingBatches().map { it.toDomain() }

/** Pending offers for the connected account, oldest first. Its own observation, apart from the weekly surface. */
@OptIn(ExperimentalCoroutinesApi::class)
fun TrainingStore.observePendingLichessActivityBatches(): Flow<List<LichessActivityBatch>> =
    db.invalidationTracker.createFlow("lichess_activity_batch", "account")
        .mapLatest { pendingLichessActivityBatches() }
        .distinctUntilChanged()

suspend fun TrainingStore.declineLichessActivityBatch(id: UUID) =
    db.lichessDao().setBatchStatus(id, LichessActivityBatch.DECLINED, null)

/**
 * The one goal a batch credits, or `null`: games prefer a Playing goal of the same time-control
 * category, puzzle kinds need exactly one Puzzles goal. Never guesses between two.
 */
fun matchingGoal(batch: LichessActivityBatch, goals: List<WeeklyGoal>): WeeklyGoal? =
    WeeklyGoal.creditCandidates(batch.activityTypeCode, null, batch.timeControlCategory, goals).singleOrNull()

/**
 * Logs an accepted batch as one activity through [logSession] (so the week sees one change),
 * crediting the single matching goal of the week the batch started in, then marks it accepted.
 *
 * @param puzzleCount Streak only: what the user typed. Runs aren't puzzles, so a blank count logs
 *   none and moves no count goal; other kinds count their items.
 * @return `false` if the batch was already answered or the write failed.
 */
suspend fun TrainingStore.acceptLichessActivityBatch(
    batch: LichessActivityBatch,
    durationSeconds: Int,
    puzzleCount: Int? = null,
    firstLaunch: Instant? = null,
): Boolean {
    val dao = db.lichessDao()
    if (dao.batch(batch.id)?.status != LichessActivityBatch.PENDING) return false
    val externalId = "lichess_batch:${batch.id}"
    val activityType = activityType(batch.activityTypeCode) ?: return false
    // Belt and braces: an activity from this batch already exists if a previous accept stopped
    // between logging it and marking the batch.
    val existing = dao.activityExists(externalId)
    var activityId: UUID? = null
    if (!existing) {
        val count = if (batch.isStreak) puzzleCount else batch.itemCount
        val startedAt = batch.startedAt(durationSeconds)
        val goal = weeklyPlanContaining(startedAt, firstLaunch)?.let { matchingGoal(batch, it.weeklyGoals) }
        val progress = goal?.creditDelta(durationSeconds, count)?.let { SessionSegmentLog.GoalProgress(goal.id, it) }
        val activity = Activity(
            activityType = activityType,
            startedAt = startedAt,
            endedAt = batch.lastItemAt,
            duration = durationSeconds,
            count = count,
            source = ActivitySource.LICHESS_SYNC,
            externalId = externalId,
            timeControlLabel = batch.timeControlLabel,
        )
        logSession(listOf(SessionSegmentLog(activity, progress)))
        activityId = activity.id
    }
    dao.setBatchStatus(batch.id, LichessActivityBatch.ACCEPTED, activityId)
    return true
}

// MARK: - Profile

/** The connected account and its latest rating per type, for Profile. */
data class LichessProfile(val account: Account, val ratings: Map<String, Int>)

@OptIn(ExperimentalCoroutinesApi::class)
fun TrainingStore.observeLichessProfile(): Flow<LichessProfile?> =
    db.invalidationTracker.createFlow("account", "elo_snapshot")
        .mapLatest { read { db.lichessDao().account(Account.LICHESS)?.toDomain()?.let { LichessProfile(it, latestRatingsInTransaction()) } } }
        .distinctUntilChanged()
