package com.knightindustries.knighttraining.core.store

import com.knightindustries.knighttraining.core.model.Activity
import com.knightindustries.knighttraining.core.model.CoachingTopic
import com.knightindustries.knighttraining.core.model.GoalTemplate
import com.knightindustries.knighttraining.core.model.PlanTemplate
import com.knightindustries.knighttraining.core.model.Resource
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Ports of iOS `OngoingActivityTests`, `RecentCompletedActivitiesTests` and the persistence half
 * of `CoachingTopicTests`.
 */
class ActivityQueriesTest : StoreTest() {
    private suspend fun save(
        code: String = "puzzles",
        startedAt: Instant,
        endedAt: Instant?,
        duration: Int,
        count: Int? = null,
        notes: String? = null,
        resource: Resource? = null,
        sessionId: UUID? = null,
        topics: List<CoachingTopic> = emptyList(),
    ) = Activity(
        activityType = type(code), resource = resource, startedAt = startedAt, endedAt = endedAt, duration = duration,
        count = count, notes = notes, sessionId = sessionId, coachingTopics = topics,
    ).also { store.saveActivity(it) }

    private fun ago(seconds: Long) = now.minusSeconds(seconds)

    // MARK: - Ongoing

    @Test
    fun noActivitiesMeansNothingOngoing() = test {
        assertNull(store.findOngoingActivityId())
        assertNull(store.findOngoingActivity())
    }

    @Test
    fun completedActivitiesAreNotOngoing() = test {
        save(startedAt = ago(3600), endedAt = now, duration = 3600, count = 25)
        assertNull(store.findOngoingActivityId())
    }

    @Test
    fun anOngoingActivityIsFoundInFull() = test {
        val ongoing = save(startedAt = now, endedAt = null, duration = 0, count = 10, notes = "In progress")
        val found = store.findOngoingActivity()!!
        assertEquals(ongoing.id, found.id)
        assertEquals("puzzles", found.activityType.code)
        assertEquals(10, found.count)
        assertEquals("In progress", found.notes)
        assertNull(found.endedAt)
    }

    /** iOS picks arbitrarily among several; this port takes the most recently started. */
    @Test
    fun theMostRecentlyStartedOngoingActivityWins() = test {
        save(startedAt = ago(3600), endedAt = null, duration = 0)
        val newer = save("openings", startedAt = now, endedAt = null, duration = 0)
        assertEquals(newer.id, store.findOngoingActivityId())
    }

    @Test
    fun anOngoingActivityCarriesItsResource() = test {
        val book = Resource(kind = store.resourceKind("book")!!, title = "My System", author = "Nimzowitsch")
        store.saveResource(book)
        save("openings", startedAt = now, endedAt = null, duration = 0, resource = book)
        assertEquals("My System", store.findOngoingActivity()!!.resource?.title)
    }

    @Test
    fun completingWithNothingOngoingChangesNothing() = test {
        val done = save(startedAt = ago(3600), endedAt = now, duration = 3600)
        store.completeOngoingActivityIfNeeded()
        assertEquals(now, store.activity(done.id)!!.endedAt)
    }

    @Test
    fun completingEndsItNowWithItsElapsedTime() = test {
        val ongoing = save(startedAt = ago(1800), endedAt = null, duration = 0)
        store.completeOngoingActivityIfNeeded()

        val after = store.activity(ongoing.id)!!
        assertEquals(now, after.endedAt)
        assertEquals(1800, after.duration)
        assertNull(store.findOngoingActivity())
    }

    @Test
    fun completingOnlyEndsOne() = test {
        save(startedAt = ago(3600), endedAt = null, duration = 0)
        save(startedAt = ago(1800), endedAt = null, duration = 0)
        store.completeOngoingActivityIfNeeded()
        assertNotNull(store.findOngoingActivity())
    }

    // MARK: - Recent completed

    @Test
    fun recentIsEmptyWithNothingOrOnlyOngoing() = test {
        assertTrue(store.recentCompletedActivities().isEmpty())
        save(startedAt = now, endedAt = null, duration = 0)
        assertTrue(store.recentCompletedActivities().isEmpty())
    }

    @Test
    fun recentExcludesActivitiesOlderThanTheWindow() = test {
        val recent = save(startedAt = ago(3600), endedAt = ago(1800), duration = 1800)
        save(startedAt = now.minus(Duration.ofDays(15)), endedAt = now.minus(Duration.ofDays(15)).plusSeconds(3600), duration = 3600)
        assertEquals(listOf(recent.id), store.recentCompletedActivities(days = 10).map { it.id })
    }

    @Test
    fun recentRespectsCustomDays() = test {
        val threeDays = save(startedAt = now.minus(Duration.ofDays(3)), endedAt = now.minus(Duration.ofDays(3)).plusSeconds(3600), duration = 3600)
        save(startedAt = now.minus(Duration.ofDays(6)), endedAt = now.minus(Duration.ofDays(6)).plusSeconds(3600), duration = 3600)
        assertEquals(listOf(threeDays.id), store.recentCompletedActivities(days = 5).map { it.id })
        assertEquals(2, store.recentCompletedActivities(days = 10).size)
    }

    @Test
    fun recentIsOrderedByEndNewestFirst() = test {
        save(startedAt = ago(5400), endedAt = ago(3600), duration = 1800, notes = "middle")
        save(startedAt = ago(7200), endedAt = ago(5400), duration = 1800, notes = "oldest")
        save(startedAt = ago(3600), endedAt = ago(1800), duration = 1800, notes = "newest")
        assertEquals(listOf("newest", "middle", "oldest"), store.recentCompletedActivities().map { it.notes })
    }

    @Test
    fun recentCarriesTheFullResource() = test {
        val book = Resource(kind = store.resourceKind("book")!!, title = "Endgame Manual", author = "Dvoretsky", description = "The classic", isAutoImported = true)
        store.saveResource(book)
        save("openings", startedAt = ago(3600), endedAt = now, duration = 3600, resource = book)

        val resource = store.recentCompletedActivities().single().resource!!
        assertEquals("Endgame Manual", resource.title)
        assertEquals("Dvoretsky", resource.author)
        assertEquals("book", resource.kind.code)
        // iOS's joined resource drops these; the port maps the whole row.
        assertEquals("The classic", resource.description)
        assertTrue(resource.isAutoImported)
    }

    /** Nothing is discarded for being short: a minute and an hour differ only in how much they add. */
    @Test
    fun shortAndLongSessionsBothCountInFull() = test {
        val brief = save(startedAt = now, endedAt = now, duration = 60)
        val long = save(startedAt = now, endedAt = now, duration = 3600)
        assertEquals(setOf(brief.id, long.id), store.recentCompletedActivities().map { it.id }.toSet())
        assertEquals(3660, store.totalDuration())
    }

    @Test
    fun aSegmentedSessionIsFullyCounted() = test {
        val sessionId = UUID.randomUUID()
        for (offset in listOf(0L, 1500L)) {
            save(startedAt = ago(offset + 1500), endedAt = ago(offset), duration = 1500, sessionId = sessionId)
        }
        assertEquals(3000, store.recentCompletedActivities().sumOf { it.duration })
    }

    // MARK: - Coaching topics

    @Test
    fun topicsSurviveASaveAndRead() = test {
        val lesson = save("coaching", startedAt = now, endedAt = now, duration = 3600, topics = listOf(CoachingTopic.ENDGAMES, CoachingTopic.STRATEGY))
        val untagged = save("coaching", startedAt = now, endedAt = now, duration = 3600)
        assertEquals(listOf(CoachingTopic.ENDGAMES, CoachingTopic.STRATEGY), store.activity(lesson.id)!!.coachingTopics)
        assertEquals(emptyList<CoachingTopic>(), store.activity(untagged.id)!!.coachingTopics)
    }

    @Test
    fun editingSetsKeepsOrClearsTopics() = test {
        val lesson = save("coaching", startedAt = now, endedAt = now, duration = 3600, topics = listOf(CoachingTopic.OPENINGS))

        store.updateActivity(lesson.id, ActivityEdit(duration = 3600, coachingTopics = Change.Set(listOf(CoachingTopic.TACTICS, CoachingTopic.MIDDLEGAME))))
        assertEquals(listOf(CoachingTopic.MIDDLEGAME, CoachingTopic.TACTICS), store.activity(lesson.id)!!.coachingTopics)

        store.updateActivity(lesson.id, ActivityEdit(duration = 2700))
        assertEquals(listOf(CoachingTopic.MIDDLEGAME, CoachingTopic.TACTICS), store.activity(lesson.id)!!.coachingTopics)

        store.updateActivity(lesson.id, ActivityEdit(duration = 2700, coachingTopics = Change.Set(emptyList())))
        assertEquals(emptyList<CoachingTopic>(), store.activity(lesson.id)!!.coachingTopics)
    }

    /** Topics are informational: a lesson on endgames credits the Coaching goal, never Endgames. */
    @Test
    fun aLessonOnEndgamesNeverCreditsTheEndgamesGoal() = test {
        val template = PlanTemplate(name = "Plan")
        store.savePlanTemplate(template)
        for (code in listOf("coaching", "endgames")) {
            store.saveGoalTemplate(GoalTemplate(planTemplateId = template.id, activityType = type(code), targetType = TargetType.DURATION, targetValue = 3600))
        }
        val plan = store.createWeeklyPlan(template.id)!!
        val candidates = WeeklyGoal.creditCandidates("coaching", null, null, store.weeklyGoals(plan.id))
        assertEquals(listOf("coaching"), candidates.map { it.activityType.code })

        val lesson = Activity(activityType = type("coaching"), startedAt = now, endedAt = now, duration = 3600, coachingTopics = listOf(CoachingTopic.ENDGAMES))
        store.logSession(listOf(SessionSegmentLog(lesson, SessionSegmentLog.GoalProgress(candidates.single().id, 3600))))

        val after = store.weeklyGoals(plan.id)
        assertEquals(3600, after.first { it.activityType.code == "coaching" }.currentValue)
        assertEquals(0, after.first { it.activityType.code == "endgames" }.currentValue)
    }
}
