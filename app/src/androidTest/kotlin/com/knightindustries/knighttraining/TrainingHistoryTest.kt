package com.knightindustries.knighttraining

import android.Manifest
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.knightindustries.knighttraining.core.model.Activity
import com.knightindustries.knighttraining.core.model.PlanTemplate
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.WeekCalendar
import com.knightindustries.knighttraining.core.model.WeeklyGoal
import com.knightindustries.knighttraining.core.model.WeeklyPlan
import com.knightindustries.knighttraining.core.store.deleteAllUserData
import com.knightindustries.knighttraining.core.store.saveActivity
import com.knightindustries.knighttraining.core.store.savePlanTemplate
import com.knightindustries.knighttraining.core.store.saveWeeklyGoal
import com.knightindustries.knighttraining.core.store.saveWeeklyPlan
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import java.time.Duration
import java.time.Instant

/** Training History from Home's weekly activity tile: the trend once there are two weeks, and its empty state before. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TrainingHistoryTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)).around(compose)

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = AppContainer.from(context.applicationContext as KnightTrainingApplication)

    @Before
    fun startClean() = runBlocking {
        container.store.deleteAllUserData()
        container.preferences.completeOnboarding()
        container.sessions.clear()
    }

    private fun text(id: Int) = context.getString(id)

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** A plan of an hour's openings a week, and some training in each of the last three weeks. */
    private fun seedWeeks() = runBlocking {
        val store = container.store
        val calendar = WeekCalendar.system()
        val thisWeek = calendar.weekStart(Instant.now())
        for (weeksAgo in 0L..2L) {
            val weekStart = thisWeek.minusWeeks(weeksAgo)
            val template = PlanTemplate(name = "Plan $weeksAgo")
            store.savePlanTemplate(template)
            val plan = WeeklyPlan(planTemplateId = template.id, weekStart = weekStart)
            store.saveWeeklyPlan(plan)
            store.saveWeeklyGoal(WeeklyGoal(weeklyPlanId = plan.id, activityType = store.activityType("openings")!!, targetType = TargetType.DURATION, targetValue = 3600))
            val start = calendar.startOf(weekStart).plus(Duration.ofHours(9))
            // Only already-past sessions: this week's lands on its first morning, so it's never in the future.
            if (start.isBefore(Instant.now())) {
                val seconds = listOf(4200, 1800, 3000)[weeksAgo.toInt()]
                store.saveActivity(Activity(activityType = store.activityType("openings")!!, startedAt = start, endedAt = start.plusSeconds(seconds.toLong()), duration = seconds))
            }
        }
    }

    private fun openFromHome() {
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.this_week_activity)) and hasClickAction(), 10_000)
        compose.onNode(hasText(text(R.string.this_week_activity)) and hasClickAction()).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.training_history)), 5_000)
    }

    @Test
    fun weeksOfTrainingShowTheTrend() {
        seedWeeks()
        openFromHome()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.weekly_report)), 5_000)
        compose.onNodeWithText(text(R.string.commitment_streak)).assertDoesNotExist()
        screenshot("90_history")
        compose.onNodeWithText(text(R.string.daily_activity)).performScrollTo()
        compose.onNodeWithText(text(R.string.usual_study_time)).performScrollTo()
        compose.onNodeWithText(text(R.string.time_mornings)).assertExists()
        screenshot("91_history_stats")
    }

    @Test
    fun withoutTwoWeeksItSaysNothingIsTrackedYet() {
        openFromHome()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.history_empty_title)), 5_000)
        compose.onNodeWithText(text(R.string.weekly_report)).assertDoesNotExist()
    }
}
