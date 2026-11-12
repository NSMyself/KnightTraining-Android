package com.knightindustries.knighttraining.core.lichess

import com.knightindustries.knighttraining.core.model.ActivitySource
import com.knightindustries.knighttraining.core.model.LichessActivityBatch
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.core.store.StoreTest
import com.knightindustries.knighttraining.core.store.acceptLichessActivityBatch
import com.knightindustries.knighttraining.core.store.activities
import com.knightindustries.knighttraining.core.store.declineLichessActivityBatch
import com.knightindustries.knighttraining.core.store.deleteLichessAccount
import com.knightindustries.knighttraining.core.store.ensureLichessAccount
import com.knightindustries.knighttraining.core.store.lichessAccount
import com.knightindustries.knighttraining.core.store.lichessBatchedExternalIds
import com.knightindustries.knighttraining.core.store.matchingGoal
import com.knightindustries.knighttraining.core.store.pendingLichessActivityBatches
import com.knightindustries.knighttraining.core.store.saveLichessActivityBatch
import com.knightindustries.knighttraining.core.store.test
import com.knightindustries.knighttraining.core.store.updateStormState
import com.knightindustries.knighttraining.core.store.updateStreakState
import com.knightindustries.knighttraining.core.store.weeklyPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

/** Ports of iOS `LichessActivityBatchTests`, `LichessBatchGoalMatchingTests` and `LichessBatchWeekCreditTests`. */
class LichessBatchTest : StoreTest() {
    private suspend fun logged() = store.activities(Instant.EPOCH, Instant.parse("2100-01-01T00:00:00Z"))

    private suspend fun account(): UUID = store.ensureLichessAccount("tester", null, null)

    private fun batch(
        accountId: UUID,
        kind: String,
        ids: List<String> = listOf("x"),
        category: String? = null,
        first: Instant = now,
        last: Instant = now,
        items: Int = ids.size,
    ) = LichessActivityBatch(
        accountId = accountId, kind = kind, category = category, externalIds = ids, itemCount = items,
        totalDurationSeconds = 600, firstItemAt = first, lastItemAt = last,
    )

    // MARK: - Persistence

    @Test
    fun batchedIdsUnionWithinAKindAndNeverMixKinds() = test {
        val account = account()
        store.saveLichessActivityBatch(batch(account, LichessActivityBatch.PUZZLE, listOf("p1", "p2")))
        store.saveLichessActivityBatch(batch(account, LichessActivityBatch.PUZZLE, listOf("p3")))
        store.saveLichessActivityBatch(batch(account, LichessActivityBatch.GAME, listOf("g1")))
        assertEquals(setOf("p1", "p2", "p3"), store.lichessBatchedExternalIds(account, LichessActivityBatch.PUZZLE))
        assertEquals(setOf("g1"), store.lichessBatchedExternalIds(account, LichessActivityBatch.GAME))
        assertTrue(store.lichessBatchedExternalIds(account, LichessActivityBatch.STORM).isEmpty())
    }

    @Test
    fun streakStateLeavesStormStateAlone() = test {
        val account = account()
        store.updateStormState(account, "2026/10/8", 3, 540)
        val syncedAt = Instant.ofEpochSecond(1_790_000_000)
        store.updateStreakState(account, 189, 34, syncedAt)
        val reloaded = store.lichessAccount()!!
        assertEquals(189, reloaded.lastStreakRuns)
        assertEquals(34, reloaded.lastStreakScore)
        assertEquals(syncedAt, reloaded.lastStreakSyncAt)
        assertEquals(3, reloaded.lastStormRunsForDay)
    }

    @Test
    fun aRenameKeepsTheAccountIdAndItsSyncState() = test {
        val id = account()
        store.updateStormState(id, "2026/10/8", 3, 540)
        assertEquals(id, store.ensureLichessAccount("renamed", "FM", "PT"))
        val reloaded = store.lichessAccount()!!
        assertEquals("renamed", reloaded.username)
        assertEquals("FM", reloaded.title)
        assertEquals(3, reloaded.lastStormRunsForDay)
    }

    @Test
    fun aStreakBatchRoundTripsItsBestScore() = test {
        store.saveLichessActivityBatch(batch(account(), LichessActivityBatch.STREAK, listOf("streak:1-2")).copy(bestScore = 34))
        val pending = store.pendingLichessActivityBatches().single()
        assertEquals(34, pending.bestScore)
        assertTrue(pending.isStreak)
        assertEquals("puzzles", pending.activityTypeCode)
    }

    @Test
    fun answeredBatchesLeaveThePendingList() = test {
        val account = account()
        val declined = batch(account, LichessActivityBatch.PUZZLE, listOf("p1"))
        val pending = batch(account, LichessActivityBatch.PUZZLE, listOf("p2"), first = now.minusSeconds(60))
        store.saveLichessActivityBatch(declined)
        store.saveLichessActivityBatch(pending)
        store.declineLichessActivityBatch(declined.id)
        assertEquals(listOf(pending.id), store.pendingLichessActivityBatches().map { it.id })
        // Still remembered, so a later sync won't offer the same puzzle again.
        assertEquals(setOf("p1", "p2"), store.lichessBatchedExternalIds(account, LichessActivityBatch.PUZZLE))
    }

    @Test
    fun disconnectingDropsOffersButKeepsLoggedTraining() = test {
        val account = account()
        val accepted = batch(account, LichessActivityBatch.PUZZLE, listOf("p1"))
        store.saveLichessActivityBatch(accepted)
        store.saveLichessActivityBatch(batch(account, LichessActivityBatch.PUZZLE, listOf("p2")))
        store.acceptLichessActivityBatch(accepted, 600)
        store.deleteLichessAccount()
        assertNull(store.lichessAccount())
        assertTrue(store.pendingLichessActivityBatches().isEmpty())
        assertEquals(1, logged().size)
    }

    // MARK: - Goal matching

    private val planId = UUID.randomUUID()

    private fun goal(code: String, timeControl: String? = null) =
        WeeklyGoal(weeklyPlanId = planId, activityType = type(code), targetType = TargetType.COUNT, targetValue = 10, timeControlLabel = timeControl)

    @Test
    fun aGameBatchCreditsTheGoalWithTheSameCategory() {
        val rapid = goal("playing", "15+10")
        val goals = listOf(goal("playing", "3+2"), goal("playing"), rapid)
        assertEquals(rapid.id, matchingGoal(batch(UUID.randomUUID(), LichessActivityBatch.GAME, category = "rapid"), goals)?.id)
    }

    @Test
    fun aGameBatchFallsBackToTheOneOpenPlayingGoal() {
        val open = goal("playing")
        val goals = listOf(goal("playing", "3+2"), open)
        assertEquals(open.id, matchingGoal(batch(UUID.randomUUID(), LichessActivityBatch.GAME, category = "classical"), goals)?.id)
    }

    @Test
    fun aPuzzleBatchCreditsOnlyASinglePuzzlesGoal() {
        val puzzles = goal("puzzles")
        assertEquals(puzzles.id, matchingGoal(batch(UUID.randomUUID(), LichessActivityBatch.STORM), listOf(goal("playing"), puzzles))?.id)
        assertNull(matchingGoal(batch(UUID.randomUUID(), LichessActivityBatch.PUZZLE), listOf(puzzles, goal("puzzles"))))
    }

    // MARK: - Accepting credits the week the batch started in

    private val thisWeekStart: Instant get() = calendar.startOf(calendar.weekStart(now))

    private suspend fun puzzlesWeek(weeksAgo: Long): WeeklyGoal =
        commitWeek(calendar.weekStart(now).minusWeeks(weeksAgo), "puzzles" to 50).weeklyGoals.single()

    private suspend fun valueOf(goal: WeeklyGoal): Int = store.weeklyPlan(goal.weeklyPlanId)!!.weeklyGoals.single { it.id == goal.id }.currentValue

    private suspend fun accepted(kind: String, first: Instant, last: Instant, duration: Int, puzzleCount: Int? = null): Boolean {
        val batch = batch(account(), kind, items = 6, first = first, last = last)
        store.saveLichessActivityBatch(batch)
        return store.acceptLichessActivityBatch(batch, duration, puzzleCount)
    }

    @Test
    fun aBatchPlayedBeforeMidnightCreditsLastWeek() = test {
        val lastWeek = puzzlesWeek(1)
        val thisWeek = puzzlesWeek(0)
        assertTrue(accepted(LichessActivityBatch.PUZZLE, thisWeekStart.minusSeconds(30 * 60), thisWeekStart.plusSeconds(10 * 60), 40 * 60))
        assertEquals(6, valueOf(lastWeek))
        assertEquals(0, valueOf(thisWeek))
    }

    @Test
    fun aBatchPlayedThisWeekCreditsThisWeek() = test {
        val lastWeek = puzzlesWeek(1)
        val thisWeek = puzzlesWeek(0)
        accepted(LichessActivityBatch.PUZZLE, thisWeekStart.plusSeconds(60), thisWeekStart.plusSeconds(20 * 60), 19 * 60)
        assertEquals(0, valueOf(lastWeek))
        assertEquals(6, valueOf(thisWeek))
    }

    @Test
    fun aBatchFromAnUncommittedWeekCreditsNothingButIsStillLogged() = test {
        val thisWeek = puzzlesWeek(0)
        assertTrue(accepted(LichessActivityBatch.PUZZLE, thisWeekStart.minusSeconds(7200), thisWeekStart.minusSeconds(3600), 3600))
        assertEquals(0, valueOf(thisWeek))
        val activity = logged().single()
        assertEquals(ActivitySource.LICHESS_SYNC, activity.source)
        assertEquals(6, activity.count)
    }

    @Test
    fun aStreakBatchUsesItsBackdatedStartToPickTheWeek() = test {
        val lastWeek = puzzlesWeek(1)
        val thisWeek = puzzlesWeek(0)
        // The sync window reaches days back, but 10 minutes of play ending 5 minutes into this week started last week.
        accepted(LichessActivityBatch.STREAK, thisWeekStart.minusSeconds(3 * 86_400), thisWeekStart.plusSeconds(5 * 60), 10 * 60, puzzleCount = 14)
        assertEquals(14, valueOf(lastWeek))
        assertEquals(0, valueOf(thisWeek))
    }

    @Test
    fun aStreakBatchWhosePlayFitsInThisWeekCreditsThisWeek() = test {
        val lastWeek = puzzlesWeek(1)
        val thisWeek = puzzlesWeek(0)
        accepted(LichessActivityBatch.STREAK, thisWeekStart.minusSeconds(3 * 86_400), thisWeekStart.plusSeconds(30 * 60), 10 * 60, puzzleCount = 14)
        assertEquals(0, valueOf(lastWeek))
        assertEquals(14, valueOf(thisWeek))
    }

    @Test
    fun aStreakBatchWithNoPuzzleCountMovesNoCountGoal() = test {
        val thisWeek = puzzlesWeek(0)
        accepted(LichessActivityBatch.STREAK, thisWeekStart, now, 10 * 60, puzzleCount = null)
        assertEquals(0, valueOf(thisWeek))
        assertNull(logged().single().count)
    }

    @Test
    fun aBatchCanOnlyBeAcceptedOnce() = test {
        val thisWeek = puzzlesWeek(0)
        val batch = batch(account(), LichessActivityBatch.PUZZLE, items = 6, first = now.minusSeconds(600), last = now)
        store.saveLichessActivityBatch(batch)
        assertTrue(store.acceptLichessActivityBatch(batch, 600))
        assertFalse(store.acceptLichessActivityBatch(batch, 600))
        assertEquals(6, valueOf(thisWeek))
        assertEquals(1, logged().size)
        assertTrue(store.pendingLichessActivityBatches().isEmpty())
    }
}
