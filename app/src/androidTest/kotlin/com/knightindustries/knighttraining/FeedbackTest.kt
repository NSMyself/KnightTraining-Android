package com.knightindustries.knighttraining

import android.Manifest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.knightindustries.knighttraining.core.store.deleteAllUserData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Profile's Sounds and Haptics switches: on by default, and each remembered on its own (iOS
 * `FeedbackServiceTests`). How the feedback itself feels is a device check.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class FeedbackTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)).around(compose)

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = AppContainer.from(context.applicationContext as KnightTrainingApplication)

    @Before
    fun startClean() = runBlocking {
        container.store.deleteAllUserData()
        container.preferences.completeOnboarding()
        container.preferences.setSoundsEnabled(true)
        container.preferences.setHapticsEnabled(true)
    }

    @After
    fun restore() = runBlocking {
        container.preferences.setSoundsEnabled(true)
        container.preferences.setHapticsEnabled(true)
    }

    private fun text(id: Int) = context.getString(id)

    private fun sounds() = runBlocking { container.preferences.soundsEnabled.first() }

    private fun haptics() = runBlocking { container.preferences.hapticsEnabled.first() }

    @Test
    fun switchesAreOnByDefaultAndEachIsRemembered() {
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.tab_profile)), 10_000)
        compose.onNodeWithText(text(R.string.tab_profile)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.haptics)), 5_000)

        compose.onNode(isToggleable() and hasText(text(R.string.haptics))).performScrollTo().performClick()
        compose.waitUntil(5_000) { !haptics() }
        assert(sounds()) { "turning haptics off left sounds alone" }

        compose.onNode(isToggleable() and hasText(text(R.string.sounds))).performScrollTo().performClick()
        compose.waitUntil(5_000) { !sounds() }

        compose.onNode(isToggleable() and hasText(text(R.string.haptics))).performScrollTo().performClick()
        compose.waitUntil(5_000) { haptics() }
        assert(!sounds())
    }
}
