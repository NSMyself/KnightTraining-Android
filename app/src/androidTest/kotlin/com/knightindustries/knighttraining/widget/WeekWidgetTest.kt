package com.knightindustries.knighttraining.widget

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.persistence.AppDatabase
import com.knightindustries.knighttraining.core.persistence.AppDatabase.Companion.configure
import com.knightindustries.knighttraining.core.store.NewBlock
import com.knightindustries.knighttraining.core.store.TrainingStore
import com.knightindustries.knighttraining.core.store.WeeklySurface
import com.knightindustries.knighttraining.core.store.createPlan
import com.knightindustries.knighttraining.core.store.weeklySurface
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The widget's layout against a real weekly surface: with a committed plan, and with none. */
@RunWith(AndroidJUnit4::class)
class WeekWidgetTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var store: TrainingStore

    @Before
    fun open() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).configure().build()
        store = TrainingStore(db)
        store.seedPresetsIfNeeded()
    }

    @After
    fun close() = db.close()

    private fun render(surface: WeeklySurface, check: androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest.() -> Unit) =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable { WeekWidgetContent(context, surface) }
            check()
        }

    @Test
    fun showsThePlanAndItsGoals() {
        val surface = runBlocking {
            store.createPlan(
                "Endgame week",
                listOf(
                    NewBlock(null, store.activityType("puzzles")!!, TargetType.COUNT, 50),
                    NewBlock(null, store.activityType("endgames")!!, TargetType.DURATION, 3600),
                ),
                "Untitled training plan",
                { "Untitled training plan ($it)" },
            )
            store.weeklySurface()
        }
        render(surface) {
            onNode(hasText("Endgame week")).assertExists()
            onNode(hasText("0/2 goals · 0 min")).assertExists()
            onNode(hasText("Puzzles  0/50")).assertExists()
            onNode(hasText("Endgames  0/60 min")).assertExists()
        }
    }

    @Test
    fun asksForAPlanWhenThereIsNone() {
        val surface = runBlocking { store.weeklySurface() }
        render(surface) {
            onNode(hasText("Knight Training")).assertExists()
            onNode(hasText("Commit to a plan to start tracking this week.")).assertExists()
        }
    }
}
