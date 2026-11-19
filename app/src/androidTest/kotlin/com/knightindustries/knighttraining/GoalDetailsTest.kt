package com.knightindustries.knighttraining

import android.Manifest
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.knightindustries.knighttraining.core.model.PlanTemplate
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.WeekCalendar
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import com.knightindustries.knighttraining.core.store.deleteAllUserData
import com.knightindustries.knighttraining.core.store.savePlanTemplate
import com.knightindustries.knighttraining.core.store.saveWeeklyGoal
import com.knightindustries.knighttraining.core.store.saveWeeklyPlan
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/** Tapping a goal on Home opens its details; Start there opens the timer on that goal. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class GoalDetailsTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)).around(compose)

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = AppContainer.from(context.applicationContext as KnightTrainingApplication)
    private lateinit var goal: WeeklyGoal

    @Before
    fun seedThisWeek() = runBlocking {
        container.store.deleteAllUserData()
        container.preferences.completeOnboarding()
        container.sessions.clear()
        val template = PlanTemplate(name = "Plan")
        container.store.savePlanTemplate(template)
        val plan = WeeklyPlan(planTemplateId = template.id, weekStart = WeekCalendar.system().weekStart(Instant.now()))
        container.store.saveWeeklyPlan(plan)
        goal = WeeklyGoal(
            weeklyPlanId = plan.id, name = "Endgame drills", activityType = container.store.activityType("puzzles")!!,
            targetType = TargetType.COUNT, targetValue = 30, currentValue = 12,
        )
        container.store.saveWeeklyGoal(goal)
    }

    @After
    fun clearSession() = container.sessions.clear()

    private fun text(id: Int) = context.getString(id)

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun tapAGoalAndStartFromItsDetails() {
        compose.waitUntilAtLeastOneExists(hasText("Endgame drills") and hasClickAction(), 10_000)
        compose.onNode(hasText("Endgame drills") and hasClickAction()).performClick()

        compose.waitUntilAtLeastOneExists(hasText(text(R.string.start_training)), 5_000)
        compose.onNodeWithText("30 puzzles").assertExists()
        screenshot("72_goal_details")
        compose.onNodeWithText(text(R.string.start_training)).performClick()

        compose.waitUntilAtLeastOneExists(hasContentDescription(text(R.string.stop)), 10_000)
        val attribution = container.sessions.session.value!!.attribution
        assertEquals(goal.id, (attribution as com.knightindustries.knighttraining.core.session.PracticeSession.Attribution.Goal).goalId)
    }
}
