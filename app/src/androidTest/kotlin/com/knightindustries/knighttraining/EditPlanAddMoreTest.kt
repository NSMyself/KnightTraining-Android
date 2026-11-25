package com.knightindustries.knighttraining

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.knightindustries.knighttraining.core.model.GoalTemplate
import com.knightindustries.knighttraining.core.model.PlanTemplate
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import com.knightindustries.knighttraining.core.store.createWeeklyPlan
import com.knightindustries.knighttraining.core.store.deleteAllUserData
import com.knightindustries.knighttraining.core.store.goalTemplate
import com.knightindustries.knighttraining.core.store.saveGoalTemplate
import com.knightindustries.knighttraining.core.store.savePlanTemplate
import com.knightindustries.knighttraining.core.store.updateWeeklyGoalProgress
import com.knightindustries.knighttraining.core.store.weeklyPlan
import com.knightindustries.knighttraining.designsystem.KnightTrainingTheme
import com.knightindustries.knighttraining.feature.plan.EditPlanScreen
import com.knightindustries.knighttraining.feature.plan.EditPlanViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * "Add more" from the plan editor (iOS `EditTrainingPlanScreen`): offered once a goal is done this
 * week, staged until Save, week-only (the template keeps its number), and discarded by Cancel.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class EditPlanAddMoreTest {
    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = AppContainer.from(context.applicationContext as KnightTrainingApplication)
    private lateinit var template: PlanTemplate
    private lateinit var week: WeeklyPlan
    private var closed = false

    private fun text(id: Int) = context.getString(id)

    @Before
    fun seedACompletedGoal() = runBlocking {
        container.store.deleteAllUserData()
        val saved = PlanTemplate(name = "Tactics week", isActive = true)
        container.store.savePlanTemplate(saved)
        container.store.saveGoalTemplate(
            GoalTemplate(planTemplateId = saved.id, activityType = container.store.activityType("puzzles")!!, targetType = TargetType.COUNT, targetValue = 10),
        )
        week = container.store.createWeeklyPlan(saved.id, container.preferences.firstLaunch())!!
        container.store.updateWeeklyGoalProgress(week.weeklyGoals.single(), 10)
        template = container.store.weeklyPlan(week.id)!!.let { saved.copy(goalTemplates = listOf(container.store.goalTemplate(it.weeklyGoals.single().goalTemplateId!!)!!)) }
    }

    private fun show() {
        val viewModel = EditPlanViewModel(container, template.id)
        compose.setContent { KnightTrainingTheme { EditPlanScreen(viewModel, onClose = { closed = true }) } }
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.add_more)), 10_000)
    }

    private fun raiseTo(value: String) {
        compose.onNodeWithText(text(R.string.add_more)).performClick()
        compose.onNodeWithText(text(R.string.raise_target)).performClick()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.new_target))).performTextInput(value)
        compose.onNodeWithText(text(R.string.save_new_target)).performClick()
        // Raised past what's done, the goal no longer offers "Add more".
        compose.waitUntilDoesNotExist(hasText(text(R.string.add_more)), 5_000)
    }

    private fun goalTarget(): Int = runBlocking { container.store.weeklyPlan(week.id)!!.weeklyGoals.single().targetValue }

    @Test
    fun aRaiseIsSavedForThisWeekOnly() {
        show()
        raiseTo("15")
        assertEquals("nothing is written before Save", 10, goalTarget())
        compose.onNodeWithText(text(R.string.save)).performClick()
        compose.waitUntil(5_000) { closed }
        assertEquals(15, goalTarget())
        assertEquals("the template keeps its number", 10, runBlocking { container.store.goalTemplate(template.goalTemplates.single().id) }!!.targetValue)
    }

    @Test
    fun cancelDiscardsAStagedRaise() {
        show()
        raiseTo("15")
        compose.onNodeWithContentDescription(text(R.string.cancel)).performClick()
        compose.waitUntil(5_000) { closed }
        assertEquals(10, goalTarget())
    }
}
