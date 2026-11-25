package com.knightindustries.knighttraining

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.knightindustries.knighttraining.core.model.Activity
import com.knightindustries.knighttraining.core.model.PlanTemplate
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.WeekCalendar
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import com.knightindustries.knighttraining.core.model.WeeklyReview
import com.knightindustries.knighttraining.core.store.deleteAllUserData
import com.knightindustries.knighttraining.core.store.saveActivity
import com.knightindustries.knighttraining.core.store.savePlanTemplate
import com.knightindustries.knighttraining.core.store.saveWeeklyGoal
import com.knightindustries.knighttraining.core.store.saveWeeklyPlan
import com.knightindustries.knighttraining.core.store.saveWeeklyReview
import com.knightindustries.knighttraining.designsystem.KnightTrainingTheme
import com.knightindustries.knighttraining.feature.review.WeeklyReviewScreen
import com.knightindustries.knighttraining.feature.review.WeeklyReviewViewModel
import com.knightindustries.knighttraining.feature.training.WeekLogScreen
import com.knightindustries.knighttraining.feature.training.WeekLogViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * The weekly review's goal-by-goal results and expandable notes, and the week log's saved
 * reflection and delete-by-swipe (driven here through its TalkBack action).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ReviewAndLogTest {
    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = AppContainer.from(context.applicationContext as KnightTrainingApplication)
    private val calendar = WeekCalendar.system()

    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    @Before
    fun clean() = runBlocking { container.store.deleteAllUserData() }

    private fun plan(weekStart: LocalDate): WeeklyPlan = runBlocking {
        val template = PlanTemplate(name = "Plan")
        container.store.savePlanTemplate(template)
        WeeklyPlan(planTemplateId = template.id, weekStart = weekStart).also { plan ->
            container.store.saveWeeklyPlan(plan)
            container.store.saveWeeklyGoal(WeeklyGoal(weeklyPlanId = plan.id, name = "Endgame drills", activityType = container.store.activityType("endgames")!!, targetType = TargetType.COUNT, targetValue = 10, currentValue = 4))
        }
    }

    private fun session(start: Instant, notes: String? = null) = runBlocking {
        Activity(activityType = container.store.activityType("puzzles")!!, startedAt = start, endedAt = start.plusSeconds(600), duration = 600, notes = notes)
            .also { container.store.saveActivity(it) }
    }

    @Test
    fun theReviewListsEachGoalAndFoldsLongNotes() {
        val lastWeek = calendar.weekStart(Instant.now()).minusWeeks(1)
        val plan = plan(lastWeek)
        (1..4).forEach { session(calendar.startOf(lastWeek).plus(Duration.ofHours(10L + it)), notes = "Note $it") }
        val viewModel = WeeklyReviewViewModel(container, plan.id)
        compose.setContent { KnightTrainingTheme { WeeklyReviewScreen(viewModel, onDone = {}) } }

        compose.waitUntilAtLeastOneExists(hasText("Endgame drills"), 10_000)
        compose.onNodeWithText("4/10").assertExists()
        compose.onNodeWithText("Note 4", substring = true).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.show_all_count, 4)).performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Note 4", substring = true), 5_000)
    }

    @Test
    fun aFinishedWeekShowsItsReflection() {
        val lastWeek = calendar.weekStart(Instant.now()).minusWeeks(1)
        val plan = plan(lastWeek)
        runBlocking { container.store.saveWeeklyReview(WeeklyReview(weeklyPlanId = plan.id, wentWell = "Daily tactics stuck")) }
        val viewModel = WeekLogViewModel(container, lastWeek)
        compose.setContent { KnightTrainingTheme { WeekLogScreen(viewModel, onBack = {}, onReflect = {}) } }

        compose.waitUntilAtLeastOneExists(hasText("Daily tactics stuck"), 10_000)
        compose.onNodeWithText(text(R.string.reflection)).assertExists()
        compose.onNodeWithText(text(R.string.reflect_on_week)).assertDoesNotExist()
    }

    @Test
    fun aSessionThisWeekCanBeDeletedAfterConfirming() {
        val thisWeek = calendar.weekStart(Instant.now())
        plan(thisWeek)
        session(Instant.now().minus(Duration.ofMinutes(30)), notes = "Swipe me")
        val viewModel = WeekLogViewModel(container, thisWeek)
        compose.setContent { KnightTrainingTheme { WeekLogScreen(viewModel, onBack = {}, onReflect = {}) } }

        compose.waitUntilAtLeastOneExists(hasText("Swipe me", substring = true), 10_000)
        compose.onNodeWithText("Swipe me", substring = true).performCustomAccessibilityActionWithLabel(text(R.string.delete_session))
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.delete_session_title)), 5_000)
        compose.onNodeWithText(text(R.string.delete)).performClick()
        compose.waitUntilDoesNotExist(hasText("Swipe me", substring = true), 5_000)
    }
}
