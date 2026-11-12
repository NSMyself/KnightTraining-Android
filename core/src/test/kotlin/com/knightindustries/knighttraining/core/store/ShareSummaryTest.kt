package com.knightindustries.knighttraining.core.store

import com.knightindustries.knighttraining.core.model.Activity
import com.knightindustries.knighttraining.core.model.EloSnapshot
import com.knightindustries.knighttraining.core.model.RatingType
import com.knightindustries.knighttraining.core.model.Resource
import com.knightindustries.knighttraining.core.model.SharePlayer
import com.knightindustries.knighttraining.core.model.WeekShareSummary
import com.knightindustries.knighttraining.core.model.WeeklyReview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

/** The share cards' summaries: what a week and a library reduce to. */
class ShareSummaryTest : StoreTest() {
    private suspend fun log(code: String, at: Instant, seconds: Int, resource: Resource? = null, notes: String? = null, sessionId: UUID? = null) =
        store.saveActivity(
            Activity(
                activityType = type(code), resource = resource, startedAt = at, endedAt = at.plusSeconds(seconds.toLong()),
                duration = seconds, notes = notes, sessionId = sessionId,
            ),
        )

    @Test
    fun aWeekCountsSittingsSortsResourcesAndKeepsNotes() = test {
        val plan = commitWeek(calendar.weekStart(now), "puzzles" to 10)
        val book = Resource(kind = store.resourceKind("book")!!, title = "Silman")
        val course = Resource(kind = store.resourceKind("course")!!, title = "Endgame Roadmap")
        store.saveResources(listOf(book, course))
        val sitting = UUID.randomUUID()
        log("puzzles", now.minusSeconds(7200), 600, sessionId = sitting)
        log("endgames", now.minusSeconds(6600), 1200, resource = book, sessionId = sitting, notes = "  Lucena, finally  ")
        log("endgames", now.minusSeconds(3600), 1800, resource = course)
        log("puzzles", calendar.startOf(calendar.weekStart(now)).minusSeconds(60), 999) // last week: excluded

        val summary = store.weekShareSummary(plan, firstLaunch = null)
        assertEquals(3600, summary.totalSeconds)
        assertEquals("Two segments of one sitting count once", 2, summary.sessionCount)
        assertEquals(listOf("Endgame Roadmap" to 1800, "Silman" to 1200), summary.resourceTimes.map { it.title to it.seconds })
        assertEquals(listOf("Lucena, finally"), summary.notes.map { it.text })
        assertNull("No review, no reflection", summary.review)
        assertNull(summary.player)
    }

    @Test
    fun anEmptyReviewIsLeftOut() {
        val plan = com.knightindustries.knighttraining.core.model.WeeklyPlan(planTemplateId = UUID.randomUUID(), weekStart = calendar.weekStart(now))
        val blank = WeeklyReview(weeklyPlanId = plan.id, wentWell = "  ")
        val said = blank.copy(heldBack = "Blitz binge")
        assertNull(WeekShareSummary.from(plan, plan.weekStart.plusDays(7), null, emptyList(), blank, null).review)
        assertEquals(said, WeekShareSummary.from(plan, plan.weekStart.plusDays(7), null, emptyList(), said, null).review)
    }

    @Test
    fun thePlayersRatingsAreTheOnesAtTheWeeksEnd() = test {
        val plan = commitWeek(calendar.weekStart(now).minusWeeks(1), "puzzles" to 10)
        val account = store.ensureLichessAccount("Tester", null, "PT")
        val weekEnd = calendar.startOf(plan.weekStart.plusDays(7))
        store.saveEloSnapshots(
            listOf(
                EloSnapshot(accountId = account, rating = 1500, ratingType = RatingType.RAPID, capturedAt = weekEnd.minusSeconds(86_400)),
                EloSnapshot(accountId = account, rating = 1600, ratingType = RatingType.RAPID, capturedAt = weekEnd.plusSeconds(86_400)),
                EloSnapshot(accountId = account, rating = 1200, ratingType = RatingType.BULLET, capturedAt = weekEnd.minusSeconds(60)),
            ),
        )
        val player = store.weekShareSummary(plan, firstLaunch = null).player!!
        assertEquals("Tester", player.username)
        assertEquals("PT", player.flag)
        assertEquals("Bullet is left off; rapid is as it stood that week", listOf(SharePlayer.Rating(RatingType.RAPID, 1500)), player.ratings)
    }

    @Test
    fun aLibraryGroupsByStatusAndLeavesOutUntouchedStudies() = test {
        val book = store.resourceKind("book")!!
        val study = store.resourceKind("lichess_study")!!
        val reading = Resource(kind = book, title = "Reading", progressPercentage = 40, lastAccessedAt = now)
        val older = Resource(kind = book, title = "Older", progressPercentage = 10, lastAccessedAt = now.minusSeconds(86_400))
        val done = Resource(kind = book, title = "Done", progressPercentage = 100)
        val shelf = Resource(kind = book, title = "Shelf", createdAt = now)
        val bookmark = Resource(kind = study, title = "Bookmarked study")
        store.saveResources(listOf(reading, older, done, shelf, bookmark))
        log("endgames", now.minusSeconds(3600), 900, resource = reading)

        val summary = store.libraryShareSummary()
        assertEquals(listOf("Reading", "Older"), summary.inProgress.map { it.resource.title })
        assertEquals(listOf("Done"), summary.finished.map { it.resource.title })
        assertEquals(listOf("Shelf"), summary.notStarted.map { it.resource.title })
        assertTrue(summary.hasUnstartedStudies)
        assertEquals(900, summary.totalSeconds)
        assertFalse(summary.isEmpty)
    }
}
