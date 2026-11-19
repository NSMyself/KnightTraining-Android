package com.knightindustries.knighttraining

import android.Manifest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

/** Tabs keep their state when switched away from, and re-tapping the current tab scrolls it to the top. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TabStateTest {
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

    /** The bottom bar's item, not the screen's title of the same name. */
    private fun tab(id: Int): SemanticsMatcher = hasText(text(id)) and hasClickAction()

    @Test
    fun aTabKeepsItsSearchAcrossASwitch() {
        compose.waitUntilAtLeastOneExists(tab(R.string.tab_resources), 10_000)
        compose.onNode(tab(R.string.tab_resources)).performClick()
        compose.onNodeWithContentDescription(text(R.string.search_resources)).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("endgame")

        compose.onNode(tab(R.string.tab_training)).performClick()
        compose.waitUntilDoesNotExist(hasSetTextAction(), 5_000)
        compose.onNode(tab(R.string.tab_resources)).performClick()

        compose.waitUntilAtLeastOneExists(hasSetTextAction() and hasText("endgame"), 5_000)
    }

    @Test
    fun reselectingTheCurrentTabScrollsToTheTop() {
        compose.waitUntilAtLeastOneExists(tab(R.string.tab_profile), 10_000)
        compose.onNode(tab(R.string.tab_profile)).performClick()
        compose.onNodeWithText(text(R.string.delete_all_data)).performScrollTo()
        compose.onAllNodesWithText(text(R.string.your_training)).onFirst().assertIsNotDisplayed()

        compose.onNode(tab(R.string.tab_profile)).performClick()

        compose.waitUntil(5_000) { compose.onAllNodesWithText(text(R.string.your_training)).onFirst().isDisplayed() }
        compose.onAllNodesWithText(text(R.string.your_training)).onFirst().assertIsDisplayed()
    }
}
