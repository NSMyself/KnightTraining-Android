package com.knightindustries.knighttraining.core.store

import androidx.room.execSQL
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import com.knightindustries.knighttraining.core.model.Activity
import com.knightindustries.knighttraining.core.model.ActivitySource
import com.knightindustries.knighttraining.core.model.GoalTemplate
import com.knightindustries.knighttraining.core.model.PlanTemplate
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

/**
 * Ports of iOS `ArchivePlanTemplateTests`, `ResaveKeepsChildRowsTests`, `DeleteAllUserDataTests`
 * and the database half of `UntitledPlanNameTests`.
 */
class PlanLifecycleTest : StoreTest() {
    private val puzzles get() = type("puzzles")

    private suspend fun count(sql: String): Long = db.useReaderConnection { connection ->
        connection.usePrepared(sql) { statement -> statement.step(); statement.getLong(0) }
    }

    private suspend fun exec(vararg statements: String) = db.useWriterConnection { connection ->
        statements.forEach { connection.execSQL(it) }
    }

    private suspend fun templateWithBlock(name: String = "Test Plan", targetValue: Int = 20): Pair<PlanTemplate, GoalTemplate> {
        val template = PlanTemplate(name = name)
        store.savePlanTemplate(template)
        val goal = GoalTemplate(planTemplateId = template.id, name = "Puzzles", activityType = puzzles, targetType = TargetType.COUNT, targetValue = targetValue)
        store.saveGoalTemplate(goal)
        return template to goal
    }

    // MARK: - Archiving ("Delete Plan")

    @Test
    fun archivingRemovesItFromTheListAndTheCount() = test {
        val (template, _) = templateWithBlock()
        assertTrue(store.weeklySurface().hasPlanTemplates)

        store.archivePlanTemplate(template.id)

        assertTrue(store.allPlanTemplates().none { it.id == template.id })
        assertFalse("deleting the only plan reads as no plan yet", store.weeklySurface().hasPlanTemplates)
    }

    @Test
    fun archivingClearsIsActive() = test {
        val (template, _) = templateWithBlock()
        store.setActivePlanTemplate(template.id)
        assertNotNull(store.activePlanTemplate())

        store.archivePlanTemplate(template.id)
        assertNull(store.activePlanTemplate())
    }

    /** The whole point: weeks already committed from the template keep running. */
    @Test
    fun archivingNeverTouchesACommittedWeekOrItsHistory() = test {
        val (template, _) = templateWithBlock()
        val plan = store.createWeeklyPlan(template.id)!!
        val goal = plan.weeklyGoals.single()
        val activity = Activity(activityType = puzzles, startedAt = now, endedAt = now, duration = 600, count = 5)
        store.logSession(listOf(SessionSegmentLog(activity, SessionSegmentLog.GoalProgress(goal.id, 5))))

        store.archivePlanTemplate(template.id)

        assertNotNull(store.weeklyPlan(plan.id))
        assertEquals(5, store.weeklyGoal(goal.id)!!.currentValue)
        assertNotNull(store.activity(activity.id))
        assertEquals("the week still shows its plan's name", "Test Plan", store.weeklySurface().planName)
    }

    @Test
    fun anArchivedTemplateIsExcludedWhileOthersRemain() = test {
        val (keep, _) = templateWithBlock("Keep Me")
        val (deleteMe, _) = templateWithBlock("Delete Me")
        store.archivePlanTemplate(deleteMe.id)
        assertEquals(listOf(keep.id), store.allPlanTemplates().map { it.id })
    }

    /** A stale reference to a deleted plan can never commit it. */
    @Test
    fun anArchivedTemplateCanNeitherBeFetchedNorCommitted() = test {
        val (template, _) = templateWithBlock()
        store.archivePlanTemplate(template.id)
        assertNull(store.planTemplate(template.id))
        assertNull(store.createWeeklyPlan(template.id))
    }

    // MARK: - Committing a week

    /** iOS 2026-09-24: a hollow week left by a partial write is completed, not returned as-is forever. */
    @Test
    fun aHollowCommittedWeekIsCompletedForTheTemplateBeingCommitted() = test {
        val (abandoned, _) = templateWithBlock("Abandoned")
        val hollow = WeeklyPlan(planTemplateId = abandoned.id, weekStart = calendar.weekStart(now))
        store.saveWeeklyPlan(hollow)
        val (picked, _) = templateWithBlock("Picked")

        val committed = store.createWeeklyPlan(picked.id)!!

        assertEquals(hollow.id, committed.id)
        assertEquals(picked.id, committed.planTemplateId)
        assertEquals(1, committed.weeklyGoals.size)
    }

    @Test
    fun committingTwiceReturnsTheSameWeek() = test {
        val (template, _) = templateWithBlock()
        val first = store.createWeeklyPlan(template.id)!!
        val second = store.createWeeklyPlan(template.id)!!
        assertEquals(first.id, second.id)
        assertEquals(1, second.weeklyGoals.size)
    }

    /** iOS 2026-09-22: during the first-week grace the plan lands on the week Home reads. */
    @Test
    fun committingDuringTheFirstWeekGraceLandsOnTheExtendedWeek() = test {
        val extendedWeek = calendar.weekStart(now).minusWeeks(1)
        val firstLaunch = calendar.startOf(calendar.weekStart(now)).minusSeconds(12 * 3600)
        val (template, _) = templateWithBlock()

        val committed = store.createWeeklyPlan(template.id, firstLaunch)!!

        assertEquals(extendedWeek, committed.weekStart)
        assertEquals(committed.id, store.weeklySurface(firstLaunch = firstLaunch).plan?.id)
    }

    // MARK: - Re-saving keeps child rows

    @Test
    fun resavingAPlanTemplateKeepsItsBlocksAndWeeks() = test {
        val (template, _) = templateWithBlock()
        store.createWeeklyPlan(template.id)
        store.savePlanTemplate(template)
        assertEquals(1, count("SELECT COUNT(*) FROM goal_template"))
        assertEquals(1, count("SELECT COUNT(*) FROM weekly_plan"))
    }

    @Test
    fun resavingAGoalTemplateKeepsTheWeeklyGoalsLink() = test {
        val (template, goalTemplate) = templateWithBlock()
        store.createWeeklyPlan(template.id)
        store.saveGoalTemplate(goalTemplate)
        assertEquals(1, count("SELECT COUNT(*) FROM weekly_goal WHERE goalTemplateId = '${goalTemplate.id}'"))
    }

    @Test
    fun resavingAWeeklyPlanKeepsItsGoalsReviewAndActivities() = test {
        val (template, _) = templateWithBlock()
        val plan = store.createWeeklyPlan(template.id)!!
        exec("INSERT INTO weekly_review (id, weeklyPlanId, wentWell, createdAt, updatedAt) VALUES ('${UUID.randomUUID()}', '${plan.id}', 'Lots', 0, 0)")
        store.saveActivity(Activity(activityType = puzzles, startedAt = now, duration = 600, weeklyPlanId = plan.id))

        store.saveWeeklyPlan(plan)

        assertEquals(1, count("SELECT COUNT(*) FROM weekly_goal WHERE weeklyPlanId = '${plan.id}'"))
        assertEquals(1, count("SELECT COUNT(*) FROM weekly_review WHERE weeklyPlanId = '${plan.id}'"))
        assertEquals(1, count("SELECT COUNT(*) FROM activity WHERE weeklyPlanId = '${plan.id}'"))
    }

    /** iOS throws here; it must never replace the committed week and its history. */
    @Test
    fun aNewPlanIdForACommittedWeekNeverReplacesIt() = test {
        val (template, _) = templateWithBlock()
        val plan = store.createWeeklyPlan(template.id)!!

        try {
            store.saveWeeklyPlan(WeeklyPlan(planTemplateId = template.id, weekStart = plan.weekStart))
            fail("expected the weekStart unique constraint to throw")
        } catch (expected: Exception) {
        }

        assertEquals(1, count("SELECT COUNT(*) FROM weekly_plan"))
        assertEquals(1, count("SELECT COUNT(*) FROM weekly_goal WHERE weeklyPlanId = '${plan.id}'"))
    }

    /** Same shape for accounts: a second Lichess account throws rather than replacing the first. */
    @Test
    fun aNewAccountIdForAnExistingPlatformNeverReplacesIt() = test {
        fun account(id: UUID, username: String) = com.knightindustries.knighttraining.core.persistence.entities.AccountEntity(
            id = id, platform = "lichess", username = username, accessToken = null, refreshToken = null, tokenExpiresAt = null,
            createdAt = now, updatedAt = now, title = null, lastGameSyncAt = null, lastPuzzleSyncAt = null, lastStormDayId = null,
            lastStormRunsForDay = null, lastStormSecondsForDay = null, lastStreakRuns = null, lastStreakScore = null,
            lastStreakSyncAt = null, flag = null,
        )
        val first = account(UUID.randomUUID(), "player")
        store.accountDao.upsert(first)
        exec("INSERT INTO elo_snapshot (id, accountId, rating, ratingType, capturedAt) VALUES ('${UUID.randomUUID()}', '${first.id}', 1500, 'blitz', 0)")

        store.accountDao.upsert(first.copy(username = "renamed"))
        assertEquals("re-saving keeps the rating history", 1, count("SELECT COUNT(*) FROM elo_snapshot"))

        try {
            store.accountDao.upsert(account(UUID.randomUUID(), "other"))
            fail("expected the platform unique constraint to throw")
        } catch (expected: Exception) {
        }
        assertEquals(1, count("SELECT COUNT(*) FROM account"))
        assertEquals(1, count("SELECT COUNT(*) FROM elo_snapshot"))
    }

    @Test
    fun resavingAWeeklyGoalKeepsItsActivities() = test {
        val (template, _) = templateWithBlock()
        val goal = store.createWeeklyPlan(template.id)!!.weeklyGoals.single()
        store.saveActivity(Activity(activityType = puzzles, startedAt = now, duration = 600, weeklyGoalId = goal.id))
        store.saveWeeklyGoal(goal)
        assertEquals(1, count("SELECT COUNT(*) FROM activity WHERE weeklyGoalId = '${goal.id}'"))
    }

    @Test
    fun resavingAnActivityKeepsTheLichessBatchLinkedToIt() = test {
        val accountId = UUID.randomUUID()
        exec("INSERT INTO account (id, platform, username, createdAt, updatedAt) VALUES ('$accountId', 'lichess', 'player', 0, 0)")
        val activity = Activity(activityType = puzzles, startedAt = now, duration = 600, source = ActivitySource.LICHESS_SYNC)
        store.saveActivity(activity)
        exec(
            """
            INSERT INTO lichess_activity_batch (id, accountId, kind, externalIds, itemCount, totalDurationSeconds,
                firstItemAt, lastItemAt, status, activityId, createdAt)
            VALUES ('${UUID.randomUUID()}', '$accountId', 'puzzle', '[]', 5, 600, 0, 0, 'accepted', '${activity.id}', 0)
            """,
        )

        store.saveActivity(activity)
        assertEquals(1, count("SELECT COUNT(*) FROM lichess_activity_batch WHERE activityId = '${activity.id}'"))
    }

    @Test
    fun resavingAResourceKeepsItsCompletionsAndActivities() = test {
        val book = com.knightindustries.knighttraining.core.model.Resource(kind = store.resourceKind("book")!!, title = "My System")
        store.saveResource(book)
        exec("INSERT INTO resource_completion (id, resourceId, completedAt) VALUES ('${UUID.randomUUID()}', '${book.id}', 0)")
        store.saveActivity(Activity(activityType = puzzles, resource = book, startedAt = now, duration = 600))

        store.saveResource(book.copy(notes = "Edited"))

        assertEquals(1, count("SELECT COUNT(*) FROM resource_completion"))
        assertEquals(1, count("SELECT COUNT(*) FROM activity WHERE resourceId = '${book.id}'"))
    }

    // MARK: - Delete all data

    private suspend fun populate() {
        val (template, goalTemplate) = templateWithBlock()
        val plan = store.createWeeklyPlan(template.id)!!
        store.saveActivity(Activity(activityType = puzzles, startedAt = now, duration = 600, weeklyPlanId = plan.id, weeklyGoalId = plan.weeklyGoals.single().id))
        val book = com.knightindustries.knighttraining.core.model.Resource(kind = store.resourceKind("book")!!, title = "My System", progressPercentage = 100)
        store.saveResource(book)
        val accountId = UUID.randomUUID()
        exec(
            "INSERT INTO resource_completion (id, resourceId, completedAt) VALUES ('${UUID.randomUUID()}', '${book.id}', 0)",
            "INSERT INTO account (id, platform, username, createdAt, updatedAt) VALUES ('$accountId', 'lichess', 'player', 0, 0)",
            "INSERT INTO elo_snapshot (id, accountId, rating, ratingType, capturedAt) VALUES ('${UUID.randomUUID()}', '$accountId', 1500, 'blitz', 0)",
            "INSERT INTO milestone (id, kind, text, createdAt) VALUES ('${UUID.randomUUID()}', 'free_text', 'Play abroad', 0)",
            "INSERT INTO catalog_course (id, platform, externalId, title, fetchedAt) VALUES ('${UUID.randomUUID()}', 'chessmood', '12', 'Endgame Roadmap', 0)",
            "INSERT INTO activity_reminder (id, goalTemplateId, weekdays, hour, minute, createdAt) VALUES ('${UUID.randomUUID()}', '${goalTemplate.id}', '2,5', 19, 0, 0)",
        )
    }

    private suspend fun tableCounts(): Map<String, Long> {
        val tables = db.useReaderConnection { connection ->
            connection.usePrepared("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'room_%' AND name NOT LIKE 'android_%'") { statement ->
                buildList { while (statement.step()) add(statement.getText(0)) }
            }
        }
        return tables.associateWith { count("SELECT COUNT(*) FROM `$it`") }
    }

    @Test
    fun deleteAllEmptiesEveryUserTableAndKeepsTheRest() = test {
        populate()
        val before = tableCounts()
        assertTrue(before.getValue("activity") > 0)

        store.deleteAllUserData()

        for ((table, rows) in tableCounts()) {
            if (table in tablesKeptByDeleteAllUserData) {
                assertEquals("$table should be untouched", before[table], rows)
                assertTrue(rows > 0)
            } else {
                assertEquals("$table still has rows", 0L, rows)
            }
        }
    }

    @Test
    fun theAppKeepsWorkingAfterDeleteAll() = test {
        populate()
        store.deleteAllUserData()

        val surface = store.weeklySurface()
        assertNull(surface.plan)
        assertFalse(surface.hasPlanTemplates)
        store.savePlanTemplate(PlanTemplate(name = "Fresh start"))
        assertEquals(1, store.allPlanTemplates().size)
    }

    // MARK: - Untitled names

    private suspend fun nextUntitled(excludingId: UUID? = null) =
        store.nextUntitledPlanTemplateName("Untitled training plan", { "Untitled training plan ($it)" }, excludingId)

    @Test
    fun untitledNamesNumberAgainstSavedPlans() = test {
        store.savePlanTemplate(PlanTemplate(name = "Untitled training plan"))
        assertEquals("Untitled training plan (1)", nextUntitled())
    }

    @Test
    fun aPlanBeingRenamedDoesNotBlockItsOwnName() = test {
        val plan = PlanTemplate(name = "Untitled training plan")
        store.savePlanTemplate(plan)
        assertEquals("Untitled training plan", nextUntitled(plan.id))
    }

    @Test
    fun aDeletedPlanFreesItsName() = test {
        val deleted = PlanTemplate(name = "Untitled training plan")
        store.savePlanTemplate(deleted)
        store.archivePlanTemplate(deleted.id)
        assertEquals("Untitled training plan", nextUntitled())
    }

    @Test
    fun goalsAndTemplatesKeepTheirCreationOrder() = test {
        val template = PlanTemplate(name = "Ordered")
        store.savePlanTemplate(template)
        val codes = listOf("puzzles", "playing", "openings", "endgames", "analyzing")
        for (code in codes) {
            store.saveGoalTemplate(GoalTemplate(planTemplateId = template.id, activityType = type(code), targetType = TargetType.COUNT, targetValue = 1))
        }
        val plan = store.createWeeklyPlan(template.id)!!
        assertEquals(codes, store.planTemplate(template.id)!!.goalTemplates.map { it.activityType.code })
        assertEquals(codes, plan.weeklyGoals.map(WeeklyGoal::activityType).map { it.code })
    }
}
