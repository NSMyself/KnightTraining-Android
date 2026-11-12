package com.knightindustries.knighttraining

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
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
import com.knightindustries.knighttraining.core.coach.WeeklyTip
import com.knightindustries.knighttraining.core.model.PlanTemplate
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.WeekCalendar
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import com.knightindustries.knighttraining.core.store.deleteAllUserData
import com.knightindustries.knighttraining.core.store.savePlanTemplate
import com.knightindustries.knighttraining.core.store.saveWeeklyGoal
import com.knightindustries.knighttraining.core.store.saveWeeklyPlan
import com.knightindustries.knighttraining.core.store.weeklyReview
import com.knightindustries.knighttraining.designsystem.KnightTrainingTheme
import com.knightindustries.knighttraining.feature.review.WeeklyReviewScreen
import com.knightindustries.knighttraining.feature.review.WeeklyReviewViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/**
 * The weekly review's coach on a device without Gemini Nano: saving closes as before, and a tip
 * card (fed by `showTipForPreview`, since no emulator runs the model) saves only what's accepted.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class WeeklyCoachTest {
    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = AppContainer.from(context.applicationContext as KnightTrainingApplication)
    private lateinit var plan: WeeklyPlan
    private var done = false

    @Before
    fun seedLastWeek() = runBlocking {
        container.store.deleteAllUserData()
        val template = PlanTemplate(name = "Plan")
        container.store.savePlanTemplate(template)
        plan = WeeklyPlan(planTemplateId = template.id, weekStart = WeekCalendar.system().weekStart(Instant.now()).minusWeeks(1))
        container.store.saveWeeklyPlan(plan)
        container.store.saveWeeklyGoal(
            WeeklyGoal(weeklyPlanId = plan.id, activityType = container.store.activityType("puzzles")!!, targetType = TargetType.COUNT, targetValue = 30, currentValue = 12),
        )
    }

    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun show(): WeeklyReviewViewModel {
        val viewModel = WeeklyReviewViewModel(container, plan.id)
        compose.setContent { KnightTrainingTheme { WeeklyReviewScreen(viewModel, onDone = { done = true }) } }
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.review_held_back)), 10_000)
        return viewModel
    }

    private fun saveReflection() {
        compose.onNode(hasText(text(R.string.review_held_back)) and hasSetTextAction()).performTextInput("Missed a lot of forks")
        compose.onNodeWithText(text(R.string.save)).performScrollTo().performClick()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun withoutGeminiNanoSavingClosesAsBefore() {
        show()
        saveReflection()
        compose.waitUntil(15_000) { done }
        assertEquals("Missed a lot of forks", runBlocking { container.store.weeklyReview(plan.id) }?.heldBack)
    }

    @Test
    fun acceptingSavesTheFocusAndTheTarget() {
        val viewModel = show()
        saveReflection()
        compose.waitUntil(15_000) { done }
        compose.runOnIdle { viewModel.showTipForPreview(WeeklyTip("Spend ten minutes on Lichess's Fork theme first.", "Forks", "puzzles", 40)) }
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.review_tip_title)), 5_000)
        compose.onNodeWithText("Forks").assertExists()
        screenshot("90_coach_tip")

        compose.onNodeWithText(text(R.string.review_tip_use)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.review_tip_continue)), 5_000)
        val review = runBlocking { container.store.weeklyReview(plan.id) }!!
        assertEquals("Forks", review.nextWeekFocus)
        assertEquals("puzzles" to 40, review.nextWeekTargetCode to review.nextWeekTargetValue)
        assertEquals("Missed a lot of forks", review.heldBack)
    }

    @Test
    fun notNowSavesNothing() {
        val viewModel = show()
        saveReflection()
        compose.waitUntil(15_000) { done }
        done = false
        compose.runOnIdle { viewModel.showTipForPreview(WeeklyTip("Spend ten minutes on Lichess's Fork theme first.", "Forks")) }
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.review_tip_make_focus)), 5_000)
        compose.onNodeWithText(text(R.string.review_tip_not_now)).performClick()
        compose.waitUntil(5_000) { done }
        assertNull(runBlocking { container.store.weeklyReview(plan.id) }!!.nextWeekFocus)
    }
}
