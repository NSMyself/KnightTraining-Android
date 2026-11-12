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
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.knightindustries.knighttraining.core.model.EloSnapshot
import com.knightindustries.knighttraining.core.model.RatingType
import com.knightindustries.knighttraining.core.store.deleteAllUserData
import com.knightindustries.knighttraining.core.store.ensureLichessAccount
import com.knightindustries.knighttraining.core.store.lichessAccount
import com.knightindustries.knighttraining.core.store.saveEloSnapshots
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/**
 * Profile's Lichess card: offer to connect, show a connected account with its ratings, and
 * disconnect. The login is seeded through the real Keystore-backed store; no network is needed.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class LichessProfileTest {
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
    }

    @After
    fun signOut() = runBlocking { container.lichessCredentials.clear() }

    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun openProfile() {
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.tab_profile)), 10_000)
        compose.onNodeWithText(text(R.string.tab_profile)).performClick()
    }

    @Test
    fun connectShowAndDisconnect() {
        openProfile()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.lichess_connect_body)), 5_000)
        screenshot("40_lichess_connect")

        // What a finished sign-in leaves behind: a login, an account row, ratings.
        runBlocking {
            container.lichessCredentials.save("test-token")
            val id = container.store.ensureLichessAccount("Tester", "FM", null)
            val at = Instant.now()
            container.store.saveEloSnapshots(
                listOf(
                    EloSnapshot(accountId = id, rating = 1810, ratingType = RatingType.RAPID, capturedAt = at),
                    EloSnapshot(accountId = id, rating = 2050, ratingType = RatingType.PUZZLE, capturedAt = at),
                ),
            )
        }
        // The Keystore round trip: a fresh reader decrypts what was saved.
        assertEquals("test-token", runBlocking { com.knightindustries.knighttraining.lichess.LichessCredentials(context).token() })

        compose.waitUntilAtLeastOneExists(hasText("FM Tester"), 5_000)
        compose.waitUntilAtLeastOneExists(hasText("${text(R.string.rating_rapid)} 1810"), 5_000)
        // The chart follows rapid first; picking puzzles switches it.
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.rating_chart_caption)), 5_000)
        compose.onNodeWithText("${text(R.string.rating_puzzle)} 2050").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.rating_chart_caption)), 5_000)
        screenshot("41_lichess_connected")

        compose.onNode(hasText(text(R.string.lichess_disconnect)) and hasClickAction()).performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.lichess_disconnect_title)), 5_000)
        compose.onNodeWithText(text(R.string.lichess_disconnect_body)).assertExists()
        screenshot("42_lichess_disconnect")
        compose.onAllNodes(hasText(text(R.string.lichess_disconnect)) and hasClickAction()).let { nodes ->
            // The dialog's button is the last one composed.
            nodes.fetchSemanticsNodes().size.let { count -> nodes[count - 1].performClick() }
        }

        compose.waitUntilAtLeastOneExists(hasText(text(R.string.lichess_connect_body)), 5_000)
        assertNull(runBlocking { container.store.lichessAccount() })
        assertNull(runBlocking { container.lichessCredentials.token() })
    }

    @Test
    fun deletingAllDataSignsOutOfLichess() {
        runBlocking {
            container.lichessCredentials.save("test-token")
            container.store.ensureLichessAccount("Tester", null, null)
        }
        openProfile()
        compose.waitUntilAtLeastOneExists(hasText("Tester"), 5_000)

        compose.onNodeWithText(text(R.string.delete_all_data)).performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.delete_all_title)), 5_000)
        compose.onAllNodes(hasText(text(R.string.delete)) and hasClickAction()).let { nodes ->
            // The dialog's button is the last one composed.
            nodes[nodes.fetchSemanticsNodes().size - 1].performClick()
        }

        compose.waitUntil(5_000) { runBlocking { container.lichessCredentials.token() } == null }
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.lichess_connect_body)), 5_000)
        assertNull(runBlocking { container.store.lichessAccount() })
        assertNull("A fresh reader finds no login either", runBlocking { com.knightindustries.knighttraining.lichess.LichessCredentials(context).token() })
    }
}
