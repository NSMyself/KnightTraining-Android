package com.knightindustries.knighttraining

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.knightindustries.knighttraining.core.model.GoalTemplate
import com.knightindustries.knighttraining.core.model.PlanTemplate
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.WeekCalendar
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import com.knightindustries.knighttraining.core.model.WeeklyReview
import com.knightindustries.knighttraining.core.model.WeeklyReview.CoachTip
import com.knightindustries.knighttraining.core.store.deleteAllUserData
import com.knightindustries.knighttraining.core.store.saveCoachTip
import com.knightindustries.knighttraining.core.store.saveGoalTemplate
import com.knightindustries.knighttraining.core.store.savePlanTemplate
import com.knightindustries.knighttraining.core.store.saveWeeklyGoal
import com.knightindustries.knighttraining.core.store.saveWeeklyPlan
import com.knightindustries.knighttraining.core.store.saveWeeklyReview
import com.knightindustries.knighttraining.core.store.weeklyPlan
import com.knightindustries.knighttraining.core.store.weeklyReview
import com.knightindustries.knighttraining.designsystem.KnightTrainingTheme
import com.knightindustries.knighttraining.feature.plan.PlanThisWeekScreen
import com.knightindustries.knighttraining.feature.plan.PlanThisWeekViewModel
import com.knightindustries.knighttraining.feature.review.WeeklyReviewScreen
import com.knightindustries.knighttraining.feature.review.WeeklyReviewViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/**
 * The weekly coach on a device without Gemini Nano. The review only saves (the coach is asked in
 * the background and, with no model, stores "nothing to show"); a stored tip appears on Plan This
 * Week, where "Use it" saves the focus and target and the next commit applies the target.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class WeeklyCoachTest {
    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = AppContainer.from(context.applicationContext as KnightTrainingApplication)
    private lateinit var plan: WeeklyPlan
    private lateinit var template: PlanTemplate
    private var done = false

    @Before
    fun seedLastWeek() = runBlocking {
        container.store.deleteAllUserData()
        template = PlanTemplate(name = "Plan", isActive = true)
        container.store.savePlanTemplate(template)
        val puzzles = container.store.activityType("puzzles")!!
        container.store.saveGoalTemplate(GoalTemplate(planTemplateId = template.id, activityType = puzzles, targetType = TargetType.COUNT, targetValue = 30))
        plan = WeeklyPlan(planTemplateId = template.id, weekStart = WeekCalendar.system().weekStart(Instant.now()).minusWeeks(1))
        container.store.saveWeeklyPlan(plan)
        container.store.saveWeeklyGoal(
            WeeklyGoal(weeklyPlanId = plan.id, activityType = puzzles, targetType = TargetType.COUNT, targetValue = 30, currentValue = 12),
        )
    }

    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun showReview() {
        val viewModel = WeeklyReviewViewModel(container, plan.id)
        compose.setContent { KnightTrainingTheme { WeeklyReviewScreen(viewModel, onDone = { done = true }) } }
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.review_held_back)), 10_000)
    }

    /** Last week reviewed, with [tip] waiting, then Plan This Week. */
    private fun showPlanThisWeek(tip: CoachTip) {
        runBlocking {
            container.store.saveWeeklyReview(WeeklyReview(weeklyPlanId = plan.id, heldBack = "Missed a lot of forks"))
            container.store.saveCoachTip(tip, plan.id)
        }
        val viewModel = PlanThisWeekViewModel(container)
        compose.setContent { KnightTrainingTheme { PlanThisWeekScreen(viewModel, onBack = {}, onCreatePlan = {}, onCommitted = { done = true }) } }
        compose.waitUntilAtLeastOneExists(hasText(tip.text!!), 10_000)
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun savingTheReviewClosesItAndTheCoachAnswersInTheBackground() {
        showReview()
        compose.onNode(hasText(text(R.string.review_held_back)) and hasSetTextAction()).performTextInput("Missed a lot of forks")
        compose.onNodeWithText(text(R.string.save)).performScrollTo().performClick()
        compose.waitUntil(15_000) { done }
        assertEquals("Missed a lot of forks", runBlocking { container.store.weeklyReview(plan.id) }?.heldBack)
        // No emulator runs Gemini Nano: the coach's answer is "nothing to show", stored so it isn't asked again.
        compose.waitUntil(40_000) { runBlocking { container.store.weeklyReview(plan.id) }?.coachTip != null }
        assertEquals(CoachTip.Status.UNAVAILABLE, runBlocking { container.store.weeklyReview(plan.id) }?.coachTip?.status)
    }

    @Test
    fun usingItSavesTheFocusAndTheCommittedWeekGetsTheTarget() {
        showPlanThisWeek(CoachTip(CoachTip.Status.READY, "Spend ten minutes on Lichess's Fork theme first.", "Forks", "puzzles", 40))
        compose.onNodeWithText("Forks").assertExists()
        screenshot("90_coach_tip")

        compose.onNodeWithText(text(R.string.review_tip_use)).performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription(text(R.string.coach_tip_saved)), 5_000)
        compose.waitUntilDoesNotExist(hasText(text(R.string.review_tip_use)), 5_000)
        val review = runBlocking { container.store.weeklyReview(plan.id) }!!
        assertEquals("Forks", review.nextWeekFocus)
        assertEquals("puzzles" to 40, review.nextWeekTargetCode to review.nextWeekTargetValue)
        assertEquals(CoachTip.Status.ACCEPTED, review.coachTip?.status)

        compose.onNodeWithText(text(R.string.commit_plan)).performClick()
        compose.waitUntil(10_000) { done }
        val week = runBlocking { container.store.weeklyPlan(WeekCalendar.system().weekStart(Instant.now())) }
        assertNotNull(week)
        assertEquals(40, week!!.weeklyGoals.single().targetValue)
    }

    @Test
    fun notNowSavesNothingAndHidesTheTip() {
        val tip = CoachTip(CoachTip.Status.READY, "Spend ten minutes on Lichess's Fork theme first.", "Forks")
        showPlanThisWeek(tip)
        compose.onNodeWithText(text(R.string.review_tip_make_focus)).assertExists()
        compose.onNodeWithText(text(R.string.review_tip_not_now)).performClick()
        compose.waitUntilDoesNotExist(hasText(tip.text!!), 5_000)
        val review = runBlocking { container.store.weeklyReview(plan.id) }!!
        assertNull(review.nextWeekFocus)
        assertEquals(CoachTip.Status.DISMISSED, review.coachTip?.status)
    }
}
