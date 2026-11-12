package com.knightindustries.knighttraining.core.store

import com.knightindustries.knighttraining.core.model.Activity
import com.knightindustries.knighttraining.core.model.WeeklyReview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** Port of iOS `WeeklyReviewTests` (minus the coach's focus, which arrives with the coach). */
class WeeklyReviewTest : StoreTest() {
    private val thisWeek get() = calendar.weekStart(now)
    private fun weeksAgo(n: Long) = thisWeek.minusWeeks(n)

    @Test
    fun saveAndFetch() = test {
        val plan = commitWeek(weeksAgo(1), "puzzles" to 10)
        store.saveWeeklyReview(WeeklyReview(weeklyPlanId = plan.id, wentWell = "Consistent", heldBack = "Time", wishIDone = "Earlier", nextWeekIntent = WeeklyReview.NextWeekIntent.PUSH_HARDER))

        val saved = store.weeklyReview(plan.id)!!
        assertEquals("Consistent", saved.wentWell)
        assertEquals("Time", saved.heldBack)
        assertEquals("Earlier", saved.wishIDone)
        assertEquals(WeeklyReview.NextWeekIntent.PUSH_HARDER, saved.nextWeekIntent)
    }

    @Test
    fun updatingKeepsIdCreationTimeAndTheCoachsFocus() = test {
        val plan = commitWeek(weeksAgo(1), "puzzles" to 10)
        val original = WeeklyReview(weeklyPlanId = plan.id, wentWell = "First", nextWeekFocus = "Forks", createdAt = Instant.ofEpochSecond(1_000))
        store.saveWeeklyReview(original)

        store.saveWeeklyReview(store.weeklyReview(plan.id)!!.updating("Second", null, null, null))

        val saved = store.weeklyReview(plan.id)!!
        assertEquals(original.id, saved.id)
        assertEquals(original.createdAt, saved.createdAt)
        assertEquals("Second", saved.wentWell)
        assertEquals("Forks", saved.nextWeekFocus)
    }

    @Test
    fun deletingTheWeekCascadesItsReview() = test {
        val plan = commitWeek(weeksAgo(1), "puzzles" to 10)
        store.saveWeeklyReview(WeeklyReview(weeklyPlanId = plan.id))
        store.deleteWeeklyPlan(plan.id)
        assertNull(store.weeklyReview(plan.id))
    }

    @Test
    fun allFieldsAreOptional() = test {
        val plan = commitWeek(weeksAgo(1), "puzzles" to 10)
        store.saveWeeklyReview(WeeklyReview(weeklyPlanId = plan.id))
        val saved = store.weeklyReview(plan.id)!!
        assertNull(saved.wentWell)
        assertNull(saved.nextWeekIntent)
    }

    // MARK: - Which week to review

    @Test
    fun previousWeekIsTheCommittedWeekJustBefore() = test {
        commitWeek(weeksAgo(1), "puzzles" to 10, "puzzles" to 10)
        assertEquals(weeksAgo(1), store.previousWeeklyPlan()?.weekStart)
    }

    @Test
    fun nothingCommittedMeansNothingToReview() = test {
        assertNull(store.previousWeeklyPlan())
    }

    /** An unreviewed gap is skipped, never queued: only last week is ever offered. */
    @Test
    fun olderMissedWeeksAreIgnored() = test {
        commitWeek(weeksAgo(2), "puzzles" to 10)
        assertNull(store.previousWeeklyPlan())
    }

    @Test
    fun aFirstWeekInItsGracePeriodIsNotOfferedYet() = test {
        val rollover = calendar.startOf(thisWeek.plusDays(7))
        val firstLaunch = rollover.minus(Duration.ofHours(2))
        commitWeek(thisWeek, "puzzles" to 10)

        assertNull(store.previousWeeklyPlan(rollover.plusSeconds(300), firstLaunch))
    }

    @Test
    fun aFirstWeekIsOfferedOnceItsExtensionEnds() = test {
        val firstRollover = calendar.startOf(thisWeek.plusDays(7))
        val firstLaunch = firstRollover.minus(Duration.ofHours(2))
        commitWeek(thisWeek, "puzzles" to 10)

        val afterExtension = calendar.startOf(thisWeek.plusDays(14)).plusSeconds(3600)
        assertEquals(thisWeek, store.previousWeeklyPlan(afterExtension, firstLaunch)?.weekStart)
    }

    // MARK: - Notes and week number

    @Test
    fun notesAreFilteredToNonEmptyInRangeOldestFirst() = test {
        val start = calendar.startOf(weeksAgo(1))
        val end = calendar.startOf(thisWeek)
        fun at(day: Long) = start.plus(Duration.ofDays(day)).plusSeconds(3600)
        val later = Activity(activityType = type("puzzles"), startedAt = at(3), duration = 600, notes = "Second")
        val earlier = Activity(activityType = type("puzzles"), startedAt = at(1), duration = 600, notes = "First")
        listOf(
            later, earlier,
            Activity(activityType = type("puzzles"), startedAt = at(2), duration = 600, notes = null),
            Activity(activityType = type("puzzles"), startedAt = at(4), duration = 600, notes = ""),
            Activity(activityType = type("puzzles"), startedAt = end.plusSeconds(60), duration = 600, notes = "Outside"),
        ).forEach { store.saveActivity(it) }

        assertEquals(listOf("First", "Second"), store.activitiesWithNotes(start, end).map { it.notes })
    }

    @Test
    fun weekNumberCountsCommittedWeeksUpToThisOne() = test {
        commitWeek(weeksAgo(3), "puzzles" to 10)
        val reviewed = commitWeek(weeksAgo(1), "puzzles" to 10)
        commitWeek(thisWeek, "puzzles" to 10)

        assertEquals(2, store.committedWeekCount(reviewed.weekStart))
        assertEquals(2, store.weekReviewContext(reviewed.id, null)!!.weekNumber)
    }

    @Test
    fun theContextCarriesLastWeeksComments() = test {
        val older = commitWeek(weeksAgo(2), "puzzles" to 10)
        store.saveWeeklyReview(WeeklyReview(weeklyPlanId = older.id, heldBack = "Work trip"))
        val reviewed = commitWeek(weeksAgo(1), "puzzles" to 10)

        val context = store.weekReviewContext(reviewed.id, null)!!
        assertEquals("Work trip", context.previous?.heldBack)
        assertNull(context.existing)
    }
}
