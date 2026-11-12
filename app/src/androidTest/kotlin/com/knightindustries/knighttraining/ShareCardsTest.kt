package com.knightindustries.knighttraining

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.knightindustries.knighttraining.core.model.Activity
import com.knightindustries.knighttraining.core.model.Resource
import com.knightindustries.knighttraining.core.model.TargetType
import com.knightindustries.knighttraining.core.model.WeeklyReview
import com.knightindustries.knighttraining.core.store.NewBlock
import com.knightindustries.knighttraining.core.store.createPlan
import com.knightindustries.knighttraining.core.store.deleteAllUserData
import com.knightindustries.knighttraining.core.store.saveActivity
import com.knightindustries.knighttraining.core.store.saveResource
import com.knightindustries.knighttraining.core.store.saveWeeklyReview
import com.knightindustries.knighttraining.core.store.weeklySurface
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/** The two share cards: preview, toggles, and the PNG handed to the share sheet. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ShareCardsTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)).around(compose)

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = AppContainer.from(context.applicationContext as KnightTrainingApplication)
    private val exported get() = File(context.cacheDir, "shares/knight-training.png")

    @Before
    fun seed() = runBlocking {
        container.store.deleteAllUserData()
        container.preferences.completeOnboarding()
        container.sessions.clear()
        exported.delete()
        val store = container.store
        store.createPlan("Endgame week", listOf(NewBlock(null, store.activityType("endgames")!!, TargetType.DURATION, 3600)), "Untitled", { "Untitled ($it)" })
        val book = Resource(kind = store.resourceKind("book")!!, title = "Silman's Complete Endgame Course", author = "Jeremy Silman", progressPercentage = 35)
        store.saveResource(book)
        val start = Instant.now().minusSeconds(3600)
        store.saveActivity(
            Activity(
                activityType = store.activityType("endgames")!!, resource = book, startedAt = start, endedAt = start.plusSeconds(1800),
                duration = 1800, notes = "Lucena position, finally",
            ),
        )
        store.saveWeeklyReview(WeeklyReview(weeklyPlanId = store.weeklySurface().plan!!.id, wentWell = "Kept to the plan"))
        Unit
    }

    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun shareMyWeek() {
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.tab_training)), 10_000)
        compose.onNodeWithText(text(R.string.tab_training)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.current_plan)), 5_000)
        compose.onNodeWithText(text(R.string.current_plan)).performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription(text(R.string.share_week_title)), 5_000)
        compose.onNode(hasContentDescription(text(R.string.share_week_title))).performClick()

        compose.waitUntilAtLeastOneExists(hasText("Lucena position, finally"), 5_000)
        compose.onNodeWithText("Kept to the plan").assertExists()
        compose.onNodeWithText("Silman's Complete Endgame Course").assertExists()
        screenshot("80_share_week")

        // Notes off: the preview drops them.
        compose.onAllNodes(isToggleable())[0].performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Lucena position, finally")).fetchSemanticsNodes().isEmpty() }

        compose.onNodeWithText(text(R.string.share)).performScrollTo().performClick()
        compose.waitUntil(10_000) { exported.exists() }
        val image = BitmapFactory.decodeFile(exported.path)
        assertTrue("A real image, card-sized", image.width > 300 && image.height > image.width)
    }

    @Test
    fun shareMyLibrary() {
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.tab_resources)), 10_000)
        compose.onNodeWithText(text(R.string.tab_resources)).performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription(text(R.string.share_library_title)), 5_000)
        compose.onNode(hasContentDescription(text(R.string.share_library_title))).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.share_library_heading)), 5_000)
        compose.onNodeWithText(text(R.string.share_total_invested)).assertExists()
        screenshot("81_share_library")
    }
}
