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
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.store.NewBlock
import com.knightindustries.knighttraining.core.store.createPlan
import com.knightindustries.knighttraining.core.store.deleteAllUserData
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File

/**
 * The core loop end to end on a device. Acts through the semantics tree (what TalkBack reads),
 * never screen coordinates. Screenshots of each step land in the app's external files dir under
 * `screenshots/`.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class CoreLoopTest {
    private val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(permissions).around(compose)

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = AppContainer.from(context.applicationContext as KnightTrainingApplication)

    @Before
    fun startClean() {
        runBlocking {
            container.store.deleteAllUserData()
            container.preferences.completeOnboarding()
        }
        container.sessions.clear()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    /** Build a plan from a preset, commit it, time a session against a goal, save, see Home move. */
    @Test
    fun planCommitTimeAndSave() {
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.no_plan_title)), 10_000)
        screenshot("01_home_empty")

        compose.onNodeWithText(text(R.string.create_plan_title)).performClick()
        compose.onNodeWithText(text(R.string.import_template)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.preset_tactics_title)), 5_000)
        compose.onNodeWithText(text(R.string.preset_tactics_title)).performClick()
        screenshot("02_presets")
        compose.onNodeWithText(text(R.string.import_action)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.preset_item_puzzles_tactical)), 5_000)
        screenshot("03_plan_builder")
        compose.onNodeWithText(text(R.string.save)).performClick()

        compose.waitUntilAtLeastOneExists(hasText("0/60"), 10_000)
        screenshot("04_home_committed")

        compose.onNode(hasContentDescription(text(R.string.start_goal, text(R.string.preset_item_puzzles_tactical)))).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.stop)), 5_000)
        // Past the 10-second accidental-stop window, so Stop reviews instead of discarding.
        Thread.sleep(11_000)
        screenshot("05_timer")
        compose.onNodeWithText(text(R.string.stop)).performClick()

        compose.waitUntilAtLeastOneExists(hasText(text(R.string.session_complete)), 5_000)
        compose.onNodeWithText(text(R.string.how_many_completed)).performTextInput("12")
        screenshot("06_review")
        compose.onNodeWithText(text(R.string.save)).performClick()

        compose.waitUntilAtLeastOneExists(hasText("12/60"), 10_000)
        screenshot("07_home_after_session")
    }

    /**
     * The week log: log a session that wasn't timed, see the week complete, then correct it and see
     * the goal reopen (a correction below target un-completes, iOS 2026-10-16).
     */
    @Test
    fun logCompleteAndCorrectAPastSession() {
        runBlocking {
            container.store.createPlan(
                "Puzzle week", listOf(NewBlock(null, container.store.activityType("puzzles")!!, TargetType.COUNT, 10)),
                "Untitled", { "Untitled ($it)" }, commit = true, firstLaunch = container.preferences.firstLaunch(),
            )
        }
        compose.waitUntilAtLeastOneExists(hasText("0/10"), 10_000)

        compose.onNodeWithText(text(R.string.tab_training)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.current_plan)), 5_000)
        screenshot("10_training_tab")
        compose.onNodeWithText(text(R.string.current_plan)).performClick()
        // The FAB's label must be in the semantics tree: this is what caught it missing.
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.add_session)), 5_000)

        compose.onNodeWithText(text(R.string.add_session)).performClick()
        val puzzlesCard = hasText(text(R.string.activity_puzzles)) and hasClickAction()
        compose.waitUntilAtLeastOneExists(puzzlesCard, 5_000)
        compose.onNode(puzzlesCard).performClick()
        compose.onNodeWithText(text(R.string.how_many_completed)).performScrollTo().performTextInput("10")
        screenshot("11_log_past_session")
        compose.onNodeWithText(text(R.string.save)).performScrollTo().performClick()

        // The week's only goal is now met: the celebration fires.
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.week_complete_title)), 10_000)
        // Into the confetti, which waits (in real time) for the sound's mating move. The clock is
        // held, or waiting for idle would play the confetti to its end before the capture.
        compose.mainClock.autoAdvance = false
        Thread.sleep(1_500)
        compose.mainClock.advanceTimeBy(1_200)
        screenshot("12_week_complete")
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText(text(R.string.continue_action)).performClick()
        compose.waitUntilAtLeastOneExists(hasText("10/10"), 5_000)
        screenshot("13_week_log")

        // Correct the count down: the goal reopens.
        compose.onNode(hasText(text(R.string.activity_puzzles)) and hasClickAction()).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.edit_session)), 5_000)
        // By its label, not "10": around 10 o'clock the start-time picker's hour field reads 10 too.
        compose.onNode(hasText(text(R.string.how_many_completed)) and hasText("10")).performScrollTo().performTextClearance()
        compose.onNodeWithText(text(R.string.how_many_completed)).performTextInput("4")
        compose.onNodeWithText(text(R.string.save)).performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("4/10"), 5_000)
        screenshot("14_after_correction")
    }

    /** Onboarding shows until finished; Skip appears on the middle pages only. */
    @Test
    fun onboardingRunsOnceToTheEnd() {
        runBlocking { container.preferences.setOnboardingCompleted(false) }
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.onboarding_brand_subtitle)), 10_000)
        screenshot("20_onboarding_1")
        compose.onNodeWithText(text(R.string.next)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.skip)), 5_000)
        screenshot("21_onboarding_2")
        compose.onNodeWithText(text(R.string.next)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.onboarding_commit_title)), 5_000)
        compose.onNodeWithText(text(R.string.next)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.get_started)), 5_000)
        compose.onNodeWithText(text(R.string.get_started)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.no_plan_title)), 10_000)
    }
}
