package com.knightindustries.knighttraining.core.lichess

import com.knightindustries.knighttraining.core.model.EloSnapshot
import com.knightindustries.knighttraining.core.model.Milestone
import com.knightindustries.knighttraining.core.model.RatingType
import com.knightindustries.knighttraining.core.store.StoreTest
import com.knightindustries.knighttraining.core.store.completeMilestone
import com.knightindustries.knighttraining.core.store.deleteMilestone
import com.knightindustries.knighttraining.core.store.ensureLichessAccount
import com.knightindustries.knighttraining.core.store.milestonesSnapshot
import com.knightindustries.knighttraining.core.store.reevaluateMilestones
import com.knightindustries.knighttraining.core.store.reopenMilestone
import com.knightindustries.knighttraining.core.store.saveEloSnapshots
import com.knightindustries.knighttraining.core.store.saveMilestone
import com.knightindustries.knighttraining.core.store.test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/** Port of iOS `MilestoneTests`. */
class MilestoneTest : StoreTest() {
    private suspend fun lichess(title: String? = null): UUID = store.ensureLichessAccount("tester", title, null)

    private var minutesLater = 0L

    /** Each snapshot a minute after the last, so "latest" is unambiguous. */
    private suspend fun snapshot(accountId: UUID, type: String, rating: Int) =
        store.saveEloSnapshots(listOf(EloSnapshot(accountId = accountId, rating = rating, ratingType = type, capturedAt = now.plusSeconds(60 * ++minutesLater))))

    private suspend fun all() = store.milestonesSnapshot().milestones

    private fun elo(target: Int, type: String = RatingType.RAPID) = Milestone(kind = Milestone.ELO, ratingType = type, targetRating = target)

    @Test
    fun saveAndDelete() = test {
        val milestone = Milestone(kind = Milestone.FREE_TEXT, text = "Play a tournament abroad")
        store.saveMilestone(milestone)
        assertEquals(listOf("Play a tournament abroad"), all().map { it.text })
        assertFalse(all().single().isCompleted)
        store.deleteMilestone(milestone.id)
        assertTrue(all().isEmpty())
    }

    @Test
    fun freeTextCompletesAndReopensByHandOnly() = test {
        val milestone = Milestone(kind = Milestone.FREE_TEXT, text = "Play a tournament abroad")
        store.saveMilestone(milestone)
        assertTrue(store.reevaluateMilestones().isEmpty())
        store.completeMilestone(milestone.id)
        assertTrue(all().single().isCompleted)
        store.reopenMilestone(milestone.id)
        assertFalse(all().single().isCompleted)
    }

    @Test
    fun eloCompletesWhenASnapshotMeetsTheTarget() = test {
        val account = lichess()
        snapshot(account, RatingType.RAPID, 1800)
        val milestone = elo(2000)
        store.saveMilestone(milestone)
        assertFalse(all().single().isCompleted)
        snapshot(account, RatingType.RAPID, 2005)
        assertEquals(listOf(milestone.id), store.reevaluateMilestones().map { it.id })
        assertTrue(all().single().isCompleted)
    }

    @Test
    fun eloAlreadyMetCompletesAtCreation() = test {
        snapshot(lichess(), RatingType.RAPID, 2100)
        val milestone = elo(2000)
        assertEquals(listOf(milestone.id), store.saveMilestone(milestone).map { it.id })
    }

    @Test
    fun eloBelowTargetOrOfAnotherTypeStaysOpen() = test {
        val account = lichess()
        snapshot(account, RatingType.RAPID, 1900)
        snapshot(account, RatingType.BLITZ, 2200)
        store.saveMilestone(elo(2000))
        assertTrue(store.reevaluateMilestones().isEmpty())
        assertFalse(all().single().isCompleted)
    }

    @Test
    fun theLatestRatingCountsNotTheBest() = test {
        val account = lichess()
        snapshot(account, RatingType.RAPID, 2050)
        snapshot(account, RatingType.RAPID, 1950)
        assertTrue(store.saveMilestone(elo(2000)).isEmpty())
    }

    @Test
    fun titleMilestones() = test {
        lichess(title = "IM")
        val specific = Milestone(kind = Milestone.TITLE, targetTitle = "IM")
        val other = Milestone(kind = Milestone.TITLE, targetTitle = "GM")
        val any = Milestone(kind = Milestone.TITLE, targetTitle = null)
        assertEquals(listOf(specific.id), store.saveMilestone(specific).map { it.id })
        assertTrue(store.saveMilestone(other).isEmpty())
        assertEquals(listOf(any.id), store.saveMilestone(any).map { it.id })
    }

    @Test
    fun noTitleCompletesNothing() = test {
        lichess(title = null)
        assertTrue(store.saveMilestone(Milestone(kind = Milestone.TITLE)).isEmpty())
    }

    @Test
    fun reevaluatingIsIdempotent() = test {
        snapshot(lichess(), RatingType.RAPID, 2005)
        assertEquals(1, store.saveMilestone(elo(2000)).size)
        assertTrue(store.reevaluateMilestones().isEmpty())
    }

    @Test
    fun eloProgressIsProximityToTheTarget() {
        assertEquals(0.84, elo(2000).eloProgress(1680)!!, 0.001)
        assertEquals(1.0, elo(2000).eloProgress(2200)!!, 0.0)
        assertNull(elo(2000).eloProgress(null))
    }

    @Test
    fun snapshotCarriesLatestRatingsAndTitle() = test {
        snapshot(lichess(title = "WIM"), RatingType.RAPID, 1850)
        store.saveMilestone(Milestone(kind = Milestone.FREE_TEXT, text = "Something"))
        val snapshot = store.milestonesSnapshot()
        assertEquals(1, snapshot.milestones.size)
        assertEquals(1850, snapshot.latestRatings[RatingType.RAPID])
        assertEquals("WIM", snapshot.currentLichessTitle)
    }
}
