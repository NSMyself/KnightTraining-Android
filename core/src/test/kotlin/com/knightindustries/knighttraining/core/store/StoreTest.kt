package com.knightindustries.knighttraining.core.store

import androidx.room.Room
import com.knightindustries.knighttraining.core.model.ActivityType
import com.knightindustries.knighttraining.core.model.PlanTemplate
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.WeekCalendar
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import com.knightindustries.knighttraining.core.persistence.AppDatabase
import com.knightindustries.knighttraining.core.persistence.AppDatabase.Companion.configure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A fresh in-memory database per test, seeded like the app's. The clock is pinned to [now] and the
 * calendar to [calendar]; tests move them by assigning.
 */
abstract class StoreTest {
    lateinit var db: AppDatabase
    lateinit var store: TrainingStore

    /** New York, Sunday-first: a real DST zone, so week math isn't only exercised in UTC. */
    var calendar = WeekCalendar(ZoneId.of("America/New_York"), DayOfWeek.SUNDAY)

    /** Wednesday 2026-11-11, noon: mid-week, so "a day ago" never crosses a rollover. */
    var now: Instant = at(2026, 11, 11, 12)

    @Before
    fun openDatabase() = runTest {
        db = Room.inMemoryDatabaseBuilder<AppDatabase>().configure().build()
        store = TrainingStore(db, now = { now }, calendar = { calendar })
        store.seedPresetsIfNeeded()
    }

    @After
    fun closeDatabase() = db.close()

    fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0, zone: ZoneId = calendar.zone): Instant =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant()

    fun type(code: String): ActivityType = store.activityType(code)!!

    /** Saves a template and commits a week for it directly, the way the iOS fixtures do. */
    suspend fun commitWeek(
        weekStart: LocalDate = calendar.weekStart(now),
        vararg goals: Pair<String, Int>,
        targetType: TargetType = TargetType.COUNT,
    ): WeeklyPlan {
        val template = PlanTemplate(name = "Plan")
        store.savePlanTemplate(template)
        val plan = WeeklyPlan(planTemplateId = template.id, weekStart = weekStart)
        store.saveWeeklyPlan(plan)
        for ((code, target) in goals) {
            store.saveWeeklyGoal(WeeklyGoal(weeklyPlanId = plan.id, activityType = type(code), targetType = targetType, targetValue = target))
        }
        return store.weeklyPlan(plan.id)!!
    }

    /**
     * Every [WeeklyPlanCompleted] emitted while [body] runs. Subscribes first: the events aren't
     * replayed, so a late subscriber legitimately sees nothing.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun TestScope.eventsDuring(body: suspend () -> Unit): List<WeeklyPlanCompleted> {
        val events = mutableListOf<WeeklyPlanCompleted>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            store.weeklyPlanCompletedEvents.collect { events += it }
        }
        // Room runs on real threads, so give the writes real time rather than virtual time.
        withContext(Dispatchers.Default) { body() }
        collector.cancel()
        return events
    }
}

/** Runs [block] with the receiver available as a [CoroutineScope] in a test. */
fun StoreTest.test(block: suspend TestScope.() -> Unit) = runTest { block() }
