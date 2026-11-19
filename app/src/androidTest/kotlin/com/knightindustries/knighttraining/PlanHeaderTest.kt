package com.knightindustries.knighttraining

import android.Manifest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.time.Instant

/** Home's plan header (its menu and "Well done!") and the one-time reminders offer after committing. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class PlanHeaderTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)).around(compose)

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = AppContainer.from(context.applicationContext as KnightTrainingApplication)

    private fun text(id: Int) = context.getString(id)

    private fun seed(current: Int) = runBlocking {
        container.store.deleteAllUserData()
        container.preferences.completeOnboarding()
        container.preferences.resetRemindersOffer()
        container.sessions.clear()
        val template = PlanTemplate(name = "Endgame week")
        container.store.savePlanTemplate(template)
        val plan = WeeklyPlan(planTemplateId = template.id, weekStart = WeekCalendar.system().weekStart(Instant.now()))
        container.store.saveWeeklyPlan(plan)
        container.store.saveWeeklyGoal(
            WeeklyGoal(weeklyPlanId = plan.id, activityType = container.store.activityType("puzzles")!!, targetType = TargetType.COUNT, targetValue = 30, currentValue = current,
                completedAt = if (current >= 30) Instant.now() else null,
            ),
        )
    }

    @Before
    fun clean() = seed(current = 0)

    @After
    fun resetOffer() = runBlocking { container.preferences.resetRemindersOffer() }

    @Test
    fun theMenuEditsThePlanAndSharesTheWeek() {
        compose.waitUntilAtLeastOneExists(hasText("Endgame week"), 10_000)
        compose.onNodeWithContentDescription(text(R.string.more_options)).performClick()
        compose.onNodeWithText(text(R.string.edit_plan)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.edit_plan_unlocked)), 5_000)
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        compose.waitUntilAtLeastOneExists(hasContentDescription(text(R.string.more_options)), 5_000)
        compose.onNodeWithContentDescription(text(R.string.more_options)).performClick()
        compose.onNodeWithText(text(R.string.share_week_title)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.share_week_title)), 5_000)
    }

    @Test
    fun aCompletedWeekSaysWellDone() {
        seed(current = 30)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.well_done)), 10_000)
    }

    @Test
    fun remindMeTurnsRemindersOnOnce() {
        compose.waitUntilAtLeastOneExists(hasText("Endgame week"), 10_000)
        runBlocking { container.preferences.queueRemindersOffer() }
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.reminder_offer_title)), 5_000)
        compose.onNodeWithText(text(R.string.remind_me)).performClick()
        compose.waitUntilDoesNotExist(hasText(text(R.string.reminder_offer_title)), 5_000)
        compose.waitUntil(5_000) { runBlocking { container.preferences.reminderSchedule.first().isEnabled } }

        // Answered: committing again doesn't ask a second time.
        runBlocking { container.preferences.queueRemindersOffer() }
        compose.waitForIdle()
        assertTrue(runBlocking { !container.preferences.remindersOfferDue.first() })
        compose.onNodeWithText(text(R.string.reminder_offer_title)).assertDoesNotExist()
    }
}
