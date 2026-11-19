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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.knightindustries.knighttraining.core.session.PracticeSession
import com.knightindustries.knighttraining.core.store.deleteAllUserData
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * The timer's mesh background: on for a session's first ten running minutes, then dimmed with a
 * notice that brings it back; no notice while paused. How the motion looks is a device check.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TimerBackgroundTest {
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

    @After
    fun clearSession() = container.sessions.clear()

    private fun text(id: Int) = context.getString(id)

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun openSession(session: PracticeSession) {
        container.sessions.start(session)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.session_in_progress)), 10_000)
        compose.onNode(hasText(text(R.string.session_in_progress)) and hasClickAction()).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.stop)), 5_000)
    }

    private fun startedAgo(minutes: Long) = PracticeSession.start(Instant.now().minus(Duration.ofMinutes(minutes)), PracticeSession.Attribution.General)

    @Test
    fun aFreshSessionShowsTheMeshWithNoNotice() {
        openSession(startedAgo(0))
        // Past the mesh's 3 s fade-in.
        compose.mainClock.advanceTimeBy(3_500)
        screenshot("80_timer_mesh")
        compose.onNodeWithText(text(R.string.mesh_dimmed)).assertDoesNotExist()
    }

    @Test
    fun pastTheBudgetItDimsAndTheNoticeBringsItBack() {
        openSession(startedAgo(11))
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.mesh_dimmed)), 10_000)
        screenshot("81_timer_dimmed")
        compose.onNodeWithText(text(R.string.mesh_dimmed)).performClick()
        compose.waitUntilDoesNotExist(hasText(text(R.string.mesh_dimmed)), 5_000)
    }

    /** A pause explains itself, and resuming restores the mesh anyway: no notice. */
    @Test
    fun aPausedSessionHasNoNotice() {
        openSession(startedAgo(11).pause(Instant.now()))
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.resume)), 5_000)
        compose.onNodeWithText(text(R.string.mesh_dimmed)).assertDoesNotExist()
    }
}
