package com.knightindustries.knighttraining

import android.Manifest
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.knightindustries.knighttraining.core.store.deleteAllUserData
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File

/**
 * The core loop end to end on a device: build a plan from a preset, commit it, time a session
 * against a goal, review and save it, and see Home's progress move. Acts through the semantics
 * tree (what TalkBack reads), never screen coordinates. Screenshots of each step land in the app's
 * external files dir under `screenshots/`.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class CoreLoopTest {
    private val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(permissions).around(compose)

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun startClean() {
        val container = AppContainer.from(context.applicationContext as KnightTrainingApplication)
        runBlocking { container.store.deleteAllUserData() }
        container.sessions.clear()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

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

        // Saved and committed: Home shows the week's goals.
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
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.this_week_activity)), 5_000)
        screenshot("07_home_after_session")
    }
}
