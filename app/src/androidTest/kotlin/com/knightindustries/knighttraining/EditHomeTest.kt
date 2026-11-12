package com.knightindustries.knighttraining

import android.Manifest
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.knightindustries.knighttraining.core.store.deleteAllUserData
import com.knightindustries.knighttraining.feature.home.HomeLayout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File

/** Edit Home: remove and add tiles, see Home follow, and reset. Drag reordering is a device check. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class EditHomeTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)).around(compose)

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = AppContainer.from(context.applicationContext as KnightTrainingApplication)

    @Before
    fun startClean() = runBlocking {
        container.store.deleteAllUserData()
        container.preferences.completeOnboarding()
        container.preferences.resetHomeLayout()
        container.sessions.clear()
    }

    @After
    fun resetLayout() = runBlocking { container.preferences.resetHomeLayout() }

    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun savedIds() = runBlocking { container.preferences.homeLayout.first() }

    @Test
    fun removeAddAndReset() {
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.milestones)), 10_000)
        compose.onNodeWithContentDescription(text(R.string.edit_home)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.home_pinned_plan)), 5_000)
        screenshot("70_edit_home")

        compose.onNode(hasContentDescription(text(R.string.home_remove_tile, text(R.string.milestones)))).performClick()
        compose.onNode(hasTestTag("editHomeList")).performScrollToKey("hidden-currentRating")
        compose.onNode(hasContentDescription(text(R.string.home_add_tile, text(R.string.tile_current_rating)))).performClick()
        compose.waitUntil(5_000) { savedIds() == listOf("weeklyActivity", "completionRate", "puzzles", "mostTrained", "currentRating") }

        compose.onNode(hasTestTag("editHomeList")).performScrollToKey("title")
        compose.onNodeWithText(text(R.string.done)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.tile_current_rating)), 5_000)
        compose.onNodeWithText(text(R.string.tile_current_rating)).performScrollTo()
        assertEquals(0, compose.onAllNodes(hasText(text(R.string.milestones_empty_title))).fetchSemanticsNodes().size)
        screenshot("71_home_edited")

        compose.onNodeWithContentDescription(text(R.string.edit_home)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.home_pinned_plan)), 5_000)
        compose.onNode(hasTestTag("editHomeList")).performScrollToKey("reset")
        compose.onNodeWithText(text(R.string.home_reset)).performClick()
        compose.waitUntil(5_000) { savedIds() == null }
        assertEquals(HomeLayout.DEFAULT, HomeLayout.fromIds(savedIds() ?: HomeLayout.DEFAULT.ids))
    }
}
