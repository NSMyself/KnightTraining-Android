package com.knightindustries.knighttraining.core.store

import com.knightindustries.knighttraining.core.model.Activity
import com.knightindustries.knighttraining.core.model.PlanTemplate
import com.knightindustries.knighttraining.core.model.Resource
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import com.knightindustries.knighttraining.core.persistence.entities.AccountEntity
import com.knightindustries.knighttraining.core.persistence.entities.EloSnapshotEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.util.Collections
import java.util.UUID

/**
 * Port of iOS `WeeklySurfaceTests`: the one value Home, the widgets and the reminder scheduler
 * derive from, and the observation that delivers it without anyone asking for a refresh.
 */
class WeeklySurfaceTest : StoreTest() {
    private val weekStart get() = calendar.startOf(calendar.weekStart(now))

    private suspend fun activity(
        code: String = "puzzles",
        startedAt: Instant,
        duration: Int = 600,
        count: Int? = null,
        resource: Resource? = null,
        sessionId: UUID? = null,
    ): Activity = Activity(
        activityType = type(code), resource = resource, startedAt = startedAt,
        endedAt = startedAt.plusSeconds(duration.toLong()), duration = duration, count = count, sessionId = sessionId,
    ).also { store.saveActivity(it) }

    private suspend fun resource(progress: Int, lastAccessedAt: Instant?, title: String = "My System", kind: String = "book") =
        Resource(kind = store.resourceKind(kind)!!, title = title, progressPercentage = progress, lastAccessedAt = lastAccessedAt)
            .also { store.saveResource(it) }

    private suspend fun lichessAccount(): AccountEntity = AccountEntity(
        id = UUID.randomUUID(), platform = "lichess", username = "tester", accessToken = null, refreshToken = null,
        tokenExpiresAt = null, createdAt = now, updatedAt = now, title = null, lastGameSyncAt = null,
        lastPuzzleSyncAt = null, lastStormDayId = null, lastStormRunsForDay = null, lastStormSecondsForDay = null,
        lastStreakRuns = null, lastStreakScore = null, lastStreakSyncAt = null, flag = null,
    ).also { store.accountDao.upsert(it) }

    private fun daysAgo(days: Long, from: Instant = now) = from.minus(Duration.ofDays(days))

    // MARK: - Fetch

    @Test
    fun anEmptyDatabaseAssertsNothing() = test {
        val surface = store.weeklySurface()
        assertNull(surface.plan)
        assertNull(surface.planName)
        assertFalse(surface.hasPlanTemplates)
        assertTrue(surface.recentActivities.isEmpty())
        assertFalse(surface.trainedToday)
        assertEquals(0, surface.allTimeStudySeconds)
        assertNull(surface.continueResource)
    }

    /** A saved template with no week committed is not the same as never having made a plan. */
    @Test
    fun aPlanTemplateWithNoCommittedWeekIsVisibleAsSuch() = test {
        store.savePlanTemplate(PlanTemplate(name = "Saved"))
        val surface = store.weeklySurface()
        assertTrue(surface.hasPlanTemplates)
        assertNull("saving a template must not imply a committed week", surface.plan)
    }

    @Test
    fun aCommittedWeekCarriesItsGoalsAndTemplateName() = test {
        commitWeek(goals = arrayOf("puzzles" to 10, "puzzles" to 10))
        val surface = store.weeklySurface()
        assertEquals(2, surface.goals.size)
        assertEquals("Plan", surface.planName)
        assertTrue(surface.hasPlanTemplates)
    }

    /** The name stays raw; the app applies "My Training Plan" with [PlanTemplate.displayName]. */
    @Test
    fun anUnnamedTemplateHasNoNameForTheAppToFallBackFrom() = test {
        val template = PlanTemplate(name = null)
        store.savePlanTemplate(template)
        store.saveWeeklyPlan(WeeklyPlan(planTemplateId = template.id, weekStart = calendar.weekStart(now)))

        val surface = store.weeklySurface()
        assertNull(surface.planName)
        assertEquals("My Training Plan", PlanTemplate.displayName(surface.planName, "My Training Plan"))
    }

    /**
     * "This week" is the goals' week, judged on when the user sat down. A trailing seven days
     * still counted last Friday on a Wednesday (iOS 2026-10-09).
     */
    @Test
    fun onlyThisWeeksActivitiesCountTowardsTheWeek() = test {
        activity(startedAt = weekStart.plusSeconds(3600), duration = 600)
        activity(startedAt = daysAgo(2, weekStart), duration = 3600) // last week, within 7 days of now
        activity(startedAt = weekStart.minusSeconds(300), duration = 900) // began before the week

        val surface = store.weeklySurface()
        assertEquals(1, surface.recentActivities.size)
        assertEquals(600, surface.weeklyTrackedSeconds)
        assertEquals(10, surface.weeklyTrackedMinutes)
        assertEquals("all-time is not windowed", 5100, surface.allTimeStudySeconds)
    }

    /** An extended first week can be 14 days wide, and the weekly total covers all of it. */
    @Test
    fun anExtendedFirstWeekCountsEverythingSinceItBegan() = test {
        val previousWeekStart = calendar.weekStart(now).minusWeeks(1)
        val firstLaunch = weekStart.minus(Duration.ofHours(47))
        val later = calendar.startOf(calendar.weekStart(now).plusDays(6))
        activity(startedAt = firstLaunch.plusSeconds(3600), duration = 1200)
        activity(startedAt = later.minusSeconds(3600), duration = 600)

        val surface = store.weeklySurface(at = later, firstLaunch = firstLaunch)
        assertEquals(previousWeekStart, surface.weekStart)
        assertEquals(1800, surface.weeklyTrackedSeconds)
    }

    @Test
    fun aShortSessionCountsInFull() = test {
        activity(startedAt = now.minusSeconds(3600), duration = 60)
        val surface = store.weeklySurface()
        assertEquals(1, surface.recentActivities.size)
        assertEquals(60, surface.weeklyTrackedSeconds)
    }

    @Test
    fun trainedTodayIsAboutTodayNotTheWeek() = test {
        activity(startedAt = daysAgo(2))
        var surface = store.weeklySurface()
        assertFalse(surface.trainedToday)
        assertEquals("…even though the week counts it", 1, surface.recentActivities.size)

        activity(startedAt = now.minusSeconds(60))
        surface = store.weeklySurface()
        assertTrue(surface.trainedToday)
    }

    @Test
    fun completionPercentageComesFromGoalsNotActivities() = test {
        val plan = commitWeek(goals = arrayOf("puzzles" to 10, "puzzles" to 10))
        store.updateWeeklyGoalProgress(plan.weeklyGoals[0], 10)
        assertEquals(50, store.weeklySurface().completionPercentage)
    }

    // MARK: - Widgets

    @Test
    fun currentStreakReflectsCompletedWeeks() = test {
        for (offset in listOf(2L, 1L)) {
            val plan = commitWeek(calendar.weekStart(now).minusWeeks(offset), "puzzles" to 10)
            store.updateWeeklyGoalProgress(plan.weeklyGoals.single(), 10)
        }
        assertEquals(2, store.weeklySurface().currentStreak)
    }

    /** The one field wider than the week: 16 weeks, for the Daily Activity grid. */
    @Test
    fun recentDailyDurationsCoverTheWidgetGridWindowNotJustTheWeek() = test {
        activity(startedAt = daysAgo(2), duration = 600)
        activity(startedAt = daysAgo(40), duration = 1200)
        activity(startedAt = daysAgo(120), duration = 999)

        val daily = store.weeklySurface().recentDailyDurationSeconds
        assertEquals(600, daily[calendar.localDate(daysAgo(2))])
        assertEquals(1200, daily[calendar.localDate(daysAgo(40))])
        assertNull(daily[calendar.localDate(daysAgo(120))])
    }

    // MARK: - Continue resource

    /** A physical book moved by hand this week, with no timed session, still counts. */
    @Test
    fun aResourceWithOnlyHandSetProgressThisWeekIsWorthSurfacing() = test {
        resource(68, daysAgo(1))
        assertTrue(store.weeklySurface().continueResource!!.isWorthSurfacing)
    }

    @Test
    fun aResourceTrainedAgainstLongAgoIsNotWorthSurfacing() = test {
        val book = resource(40, daysAgo(30))
        activity(startedAt = daysAgo(30), resource = book)
        // Logging touches the resource, so restore the old recency the iOS fixture has.
        store.resourceDao.upsert(store.resourceDao.byId(book.id)!!.copy(lastAccessedAt = daysAgo(30)))

        assertFalse(store.weeklySurface().continueResource!!.isWorthSurfacing)
    }

    @Test
    fun aResourceTrainedAgainstThisWeekIsWorthSurfacing() = test {
        val book = resource(40, daysAgo(1))
        activity(startedAt = daysAgo(1), duration = 900, resource = book)

        val continueResource = store.weeklySurface().continueResource!!
        assertEquals("My System", continueResource.title)
        assertEquals(40, continueResource.progressPercentage)
        assertTrue(continueResource.isWorthSurfacing)
    }

    // MARK: - Rating + sittings

    @Test
    fun noLichessAccountMeansNotConfigured() = test {
        val surface = store.weeklySurface()
        assertFalse(surface.hasLichessAccount)
        assertNull(surface.currentRating)
        assertNull(surface.currentRatingType)
    }

    @Test
    fun aConnectedAccountWithNoSnapshotsHasNoRatingYet() = test {
        lichessAccount()
        val surface = store.weeklySurface()
        assertTrue(surface.hasLichessAccount)
        assertNull(surface.currentRating)
    }

    /** One rating fits a small widget: whichever type was captured most recently. */
    @Test
    fun currentRatingIsTheMostRecentlyCapturedAcrossTypes() = test {
        val account = lichessAccount()
        store.accountDao.upsertSnapshot(EloSnapshotEntity(UUID.randomUUID(), account.id, 1500, "blitz", daysAgo(1)))
        store.accountDao.upsertSnapshot(EloSnapshotEntity(UUID.randomUUID(), account.id, 1650, "rapid", now))

        val surface = store.weeklySurface()
        assertEquals(1650, surface.currentRating)
        assertEquals("rapid", surface.currentRatingType)
    }

    @Test
    fun sessionsThisWeekCountsSittingsNotSegments() = test {
        val shared = UUID.randomUUID()
        activity("openings", now.minusSeconds(3600), sessionId = shared)
        activity("endgames", now.minusSeconds(3000), sessionId = shared)
        activity("puzzles", now.minusSeconds(1800), sessionId = null)
        assertEquals(2, store.weeklySurface().sessionsThisWeekCount)
    }

    // MARK: - Observation

    /**
     * Values [flow] delivers while [body] runs: subscribes first, waits for [expecting] values, then
     * a grace period so an extra unwanted value is caught, not merely missed.
     */
    private suspend fun <T> delivered(flow: Flow<T>, expecting: Int, body: suspend () -> Unit): List<T> =
        withContext(Dispatchers.Default) {
            val values = Collections.synchronizedList(mutableListOf<T>())
            val collector = launch { flow.collect { values += it } }
            withTimeout(5_000) { while (values.isEmpty()) delay(10) }
            body()
            withTimeout(5_000) { while (values.size < expecting) delay(10) }
            delay(250)
            collector.cancel()
            values.toList()
        }

    /** The whole point: nobody asked for a refresh, and a new surface arrived anyway. */
    @Test
    fun aWriteProducesANewSurfaceWithNoRefreshCall() = test {
        val surfaces = delivered(store.observeWeeklySurface(), 2) { activity(startedAt = now, duration = 300) }
        assertEquals(0, surfaces.first().weeklyTrackedSeconds)
        assertEquals(300, surfaces.last().weeklyTrackedSeconds)
    }

    @Test
    fun committingAWeeklyPlanProducesANewSurface() = test {
        val surfaces = delivered(store.observeWeeklySurface(), 2) { commitWeek(goals = arrayOf("puzzles" to 10, "puzzles" to 10, "puzzles" to 10)) }
        assertNull(surfaces.first().plan)
        assertEquals(3, surfaces.last().goals.size)
    }

    @Test
    fun committingAWeeklyPlanReachesTheObservedWeekList() = test {
        lateinit var plan: WeeklyPlan
        val lists = delivered(store.observeWeeklyPlans(52), 2) { plan = commitWeek(goals = arrayOf("puzzles" to 10, "puzzles" to 10)) }
        assertEquals(0, lists.first().size)
        assertEquals(listOf(plan.id), lists.last().map { it.id })
        assertEquals(2, lists.last().first().weeklyGoals.size)
    }

    @Test
    fun loggingASessionReachesTheObservedWeekActivities() = test {
        lateinit var logged: Activity
        val lists = delivered(store.observeActivities(weekStart, weekStart.plus(Duration.ofDays(7))), 2) {
            logged = activity(startedAt = now, count = 10)
        }
        assertEquals(0, lists.first().size)
        assertEquals(listOf(logged.id), lists.last().map { it.id })
    }

    /** A write that moves nothing the surface reports must not wake the widget writer or scheduler. */
    @Test
    fun aWriteThatChangesNothingObservableDeliversNoNewSurface() = test {
        val book = resource(40, now)
        val surfaces = delivered(store.observeWeeklySurface(), 1) { store.saveResource(book) }
        assertEquals("only the initial value", 1, surfaces.size)
    }

    /** One transaction, one notification: not one per row written. */
    @Test
    fun aMultiSegmentSessionDeliversExactlyOneNewSurface() = test {
        val goal = commitWeek(goals = arrayOf("puzzles" to 30)).weeklyGoals.single()
        val book = resource(10, now)
        val sessionId = UUID.randomUUID()
        val segments = (0 until 3).map { index ->
            SessionSegmentLog(
                Activity(
                    activityType = type("puzzles"), resource = book, startedAt = now.plusSeconds(index * 600L),
                    endedAt = now.plusSeconds((index + 1) * 600L), duration = 600, count = 5, sessionId = sessionId,
                ),
                SessionSegmentLog.GoalProgress(goal.id, 5),
            )
        }

        val surfaces = delivered(store.observeWeeklySurface(), 2) { store.logSession(segments) }
        assertEquals("one initial value, one for the session", 2, surfaces.size)
        assertEquals(3, surfaces.last().recentActivities.size)
        assertEquals(1800, surfaces.last().weeklyTrackedSeconds)
        assertEquals(15, surfaces.last().goals.single().currentValue)
    }

    @Test
    fun aBulkResourceSaveDeliversExactlyOneNewSurface() = test {
        val videos = (1..20).map { index ->
            Resource(
                kind = store.resourceKind("video")!!, title = "Endgame Lecture $index",
                url = "https://www.youtube.com/watch?v=video$index", progressPercentage = 10,
                lastAccessedAt = now.plusSeconds(index.toLong()),
            )
        }
        val surfaces = delivered(store.observeWeeklySurface(), 2) { store.saveResources(videos) }
        assertEquals("one initial value, one for the whole import", 2, surfaces.size)
        assertEquals("Endgame Lecture 20", surfaces.last().continueResource?.title)
    }

    @Test
    fun aFailedSessionWriteLeavesNothingBehind() = test {
        val goal = commitWeek(goals = arrayOf("puzzles" to 30)).weeklyGoals.single()
        val good = SessionSegmentLog(
            Activity(activityType = type("puzzles"), startedAt = now, endedAt = now, duration = 600),
            SessionSegmentLog.GoalProgress(goal.id, 600),
        )
        // A resource that was never saved trips the foreign key and rolls the transaction back.
        val bad = SessionSegmentLog(
            Activity(
                activityType = type("puzzles"), resource = Resource(kind = store.resourceKind("book")!!, title = "Never saved"),
                startedAt = now, endedAt = now, duration = 600,
            ),
        )

        try {
            store.logSession(listOf(good, bad))
            fail("expected the foreign key violation to propagate")
        } catch (expected: Exception) {
        }

        val surface = store.weeklySurface()
        assertTrue("the good segment must not survive on its own", surface.recentActivities.isEmpty())
        assertEquals("nor its goal progress", 0, surface.goals.single().currentValue)
    }
}
