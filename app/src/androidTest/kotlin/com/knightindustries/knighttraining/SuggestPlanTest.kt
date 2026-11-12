package com.knightindustries.knighttraining

import android.Manifest
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.knightindustries.knighttraining.core.model.PlanSuggestionEngine
import com.knightindustries.knighttraining.core.model.PlanSuggestionRequest
import com.knightindustries.knighttraining.core.store.deleteAllUserData
import com.knightindustries.knighttraining.core.store.weeklySurface
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File

/** "Suggest a plan" from the builder: the engine's blocks join the plan and commit like any others. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SuggestPlanTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)).around(compose)

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = AppContainer.from(context.applicationContext as KnightTrainingApplication)

    @Before
    fun startClean() = runBlocking {
        container.lichessCredentials.clear()
        container.store.deleteAllUserData()
        container.preferences.completeOnboarding()
        container.sessions.clear()
    }

    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun suggestAddAndCommit() {
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.no_plan_title)), 10_000)
        compose.onNodeWithText(text(R.string.create_plan_title)).performClick()
        compose.onNodeWithText(text(R.string.suggest_plan)).performClick()
        // The rating field appears once the player's history has loaded (no Lichess rating here).
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.suggest_rating)), 5_000)

        compose.onNode(hasText(text(R.string.activity_endgames)) and hasClickAction()).performClick()
        compose.onNode(hasSetTextAction() and hasText(text(R.string.suggest_rating_hint))).performScrollTo().performTextInput("1400")
        screenshot("60_suggest_form")
        compose.onNodeWithText(text(R.string.suggest_go)).performScrollTo().performClick()

        compose.waitUntilAtLeastOneExists(hasText(text(R.string.suggest_result_title)), 5_000)
        compose.onNodeWithText(text(R.string.suggest_reason_focus, text(R.string.activity_endgames)), substring = true).assertExists()
        screenshot("61_suggest_result")
        compose.onNodeWithText(text(R.string.suggest_add)).performScrollTo().performClick()

        compose.waitUntilDoesNotExist(hasText(text(R.string.suggest_result_title)), 5_000)
        screenshot("62_builder_with_suggestion")
        compose.onNodeWithText(text(R.string.save)).performClick()

        val expected = PlanSuggestionEngine.suggest(PlanSuggestionRequest(4 * 60, 1400, focusCodes = listOf("endgames"))).goals
        compose.waitUntil(10_000) { runBlocking { container.store.weeklySurface().plan } != null }
        val goals = runBlocking { container.store.weeklySurface().plan!!.weeklyGoals }
        assertEquals(expected.map { it.activityTypeCode to it.targetValue }.toSet(), goals.map { it.activityType.code to it.targetValue }.toSet())
    }
}
