package com.knightindustries.knighttraining

import android.Manifest
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
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
import com.knightindustries.knighttraining.core.model.LichessActivityBatch
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.store.NewBlock
import com.knightindustries.knighttraining.core.store.createPlan
import com.knightindustries.knighttraining.core.store.deleteAllUserData
import com.knightindustries.knighttraining.core.store.ensureLichessAccount
import com.knightindustries.knighttraining.core.store.lichessBatchedExternalIds
import com.knightindustries.knighttraining.core.store.milestonesSnapshot
import com.knightindustries.knighttraining.core.store.pendingLichessActivityBatches
import com.knightindustries.knighttraining.core.store.saveLichessActivityBatch
import com.knightindustries.knighttraining.core.store.weeklySurface
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/** Home's Lichess offers and milestones card, driven through the semantics tree. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class LichessHomeTest {
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
    fun acceptAnOfferAndDismissAnother() {
        val store = container.store
        val rapid = runBlocking {
            store.createPlan("Week", listOf(NewBlock(null, store.activityType("playing")!!, TargetType.COUNT, 5)), "Untitled", { "Untitled ($it)" })
            val account = store.ensureLichessAccount("Tester", null, null)
            val now = Instant.now()
            val rapid = LichessActivityBatch(
                accountId = account, kind = LichessActivityBatch.GAME, category = "rapid", externalIds = listOf("g1", "g2", "g3"),
                itemCount = 3, totalDurationSeconds = 45 * 60, timeControlLabel = "10 min",
                firstItemAt = now.minusSeconds(3600), lastItemAt = now.minusSeconds(600),
            )
            store.saveLichessActivityBatch(rapid)
            store.saveLichessActivityBatch(
                LichessActivityBatch(
                    accountId = account, kind = LichessActivityBatch.STORM, externalIds = listOf("storm:x"), itemCount = 2,
                    totalDurationSeconds = 360, firstItemAt = now.minusSeconds(300), lastItemAt = now,
                ),
            )
            rapid
        }

        compose.waitUntilAtLeastOneExists(hasText(context.resources.getQuantityString(R.plurals.lichess_rapid_detected, 3)), 10_000)
        compose.onNodeWithText("10 min · 3 games").assertExists()
        screenshot("50_lichess_offer")

        compose.onNodeWithText(text(R.string.add_to_history)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.track_title)), 5_000)
        compose.onNodeWithText(text(R.string.track_quantity)).assertExists()
        screenshot("51_track_sheet")
        compose.onAllNodes(hasText(text(R.string.add_to_history)) and hasClickAction()).let { nodes ->
            nodes[nodes.fetchSemanticsNodes().size - 1].performScrollTo().performClick()
        }

        // The Storm offer is next in line; dismissing it is remembered.
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.lichess_storm_detected)), 5_000)
        val goal = runBlocking { store.weeklySurface().plan!!.weeklyGoals.single() }
        assertEquals("The single Playing goal is credited with the batch's games", 3, goal.currentValue)
        compose.onNode(hasContentDescription(text(R.string.dismiss))).performClick()
        compose.waitUntil(5_000) { runBlocking { store.pendingLichessActivityBatches().isEmpty() } }
        assertTrue(runBlocking { store.lichessBatchedExternalIds(rapid.accountId, LichessActivityBatch.STORM) }.isNotEmpty())
    }

    @Test
    fun addAndCompleteAMilestone() {
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.milestones_empty_title)), 10_000)
        compose.onNode(hasText(text(R.string.milestone_add)) and hasClickAction()).performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.milestone_kind_text)), 5_000)
        screenshot("52_milestone_kinds")
        compose.onNodeWithText(text(R.string.milestone_kind_text)).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Play a tournament abroad")
        compose.onNodeWithText(text(R.string.save)).performClick()

        compose.waitUntilAtLeastOneExists(hasText("Play a tournament abroad"), 5_000)
        screenshot("53_milestones_card")
        compose.onNodeWithText("Play a tournament abroad").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.milestones_all_done)), 5_000)
        assertTrue(runBlocking { container.store.milestonesSnapshot() }.milestones.single().isCompleted)
        compose.onNodeWithText(text(R.string.milestones_see_all)).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Play a tournament abroad"), 5_000)
        screenshot("54_milestones_all")
    }
}
