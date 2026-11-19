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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The widgets' layouts against a real weekly surface: the week widget at each size, with a plan
 * and with none, and the stat widget's states.
 */
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

    private fun render(
        surface: WeeklySurface,
        size: DpSize = WeekWidget.LARGE,
        check: androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest.() -> Unit,
    ) = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(size)
        provideComposable { WeekWidgetContent(context, surface, size) }
        check()
    }

    private fun renderStat(
        surface: WeeklySurface,
        stat: WidgetStat?,
        sdk: Int = 36,
        check: androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest.() -> Unit,
    ) = runGlanceAppWidgetUnitTest {
            setContext(context)
            setAppWidgetSize(DpSize(160.dp, 160.dp))
            provideComposable { StatWidgetContent(context, surface, stat, sdk = sdk) }
            check()
        }

    private fun planSurface() = runBlocking {
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

    @Test
    fun smallShowsTheCountAndMediumWhatIsLeft() {
        val surface = planSurface()
        render(surface, WeekWidget.SMALL) {
            onNode(hasText("0/2 goals")).assertExists()
            onNode(hasText("Puzzles")).assertDoesNotExist()
        }
        render(surface, WeekWidget.MEDIUM) {
            onNode(hasText("0/2 goals")).assertExists()
            onNode(hasText("Puzzles")).assertExists()
            onNode(hasText("0/60 min")).assertExists()
        }
    }

    @Test
    fun smallWithoutAPlanSaysNothingIsLogged() {
        val surface = runBlocking { store.weeklySurface() }
        render(surface, WeekWidget.SMALL) { onNode(hasText("No training logged this week")).assertExists() }
    }

    @Test
    fun theStatWidgetAsksForAStatThenShowsIt() {
        val surface = runBlocking { store.weeklySurface() }
        renderStat(surface, null) { onNode(hasText("Long-press to choose a stat")).assertExists() }
        // Android 9–11 can't reopen a widget's settings: the hint asks for a tap instead.
        renderStat(surface, null, sdk = 30) {
            onNode(hasText("Tap to choose a stat")).assertExists()
            onNode(hasText("Long-press to choose a stat")).assertDoesNotExist()
        }
        renderStat(surface, WidgetStat.RATING) { onNode(hasText("Connect Lichess to track your rating")).assertExists() }
        renderStat(surface, WidgetStat.STREAK) { onNode(hasText("Complete a full week to start a streak")).assertExists() }
        renderStat(surface, WidgetStat.SESSIONS_THIS_WEEK) { onNode(hasText("Sessions this week")).assertExists() }
        renderStat(surface, WidgetStat.TOTAL_HOURS) { onNode(hasText("Hours studied")).assertExists() }
    }

    @Test
    fun piecesClimbWithTheStreakAndTheHours() {
        assertEquals(com.knightindustries.knighttraining.R.drawable.chess_piece_pawn, streakPiece(1))
        assertEquals(com.knightindustries.knighttraining.R.drawable.chess_piece_knight, streakPiece(2))
        assertEquals(com.knightindustries.knighttraining.R.drawable.chess_piece_king, streakPiece(14))
        assertEquals(com.knightindustries.knighttraining.R.drawable.chess_piece_pawn, hoursPiece(24))
        assertEquals(com.knightindustries.knighttraining.R.drawable.chess_piece_queen, hoursPiece(599))
        assertEquals(com.knightindustries.knighttraining.R.drawable.chess_piece_king, hoursPiece(600))
    }

    /** The ring and grid are bitmaps no semantics can see: written out for a look. */
    @Test
    fun drawsTheRingAndTheGrid() {
        val dir = java.io.File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        fun save(name: String, bitmap: android.graphics.Bitmap) =
            java.io.File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        save("95_widget_ring", WidgetStyle.ring(198, 30f, 0.6f))
        val today = java.time.LocalDate.of(2026, 11, 19)
        val days = (0L until 60L).associate { today.minusDays(it) to listOf(0, 900, 4200, 12000, 0, 300)[(it % 6).toInt()] }
        save("96_widget_grid", WidgetStyle.grid(900, 360, 9f, today.minusDays(4), today, days))
    }
}
