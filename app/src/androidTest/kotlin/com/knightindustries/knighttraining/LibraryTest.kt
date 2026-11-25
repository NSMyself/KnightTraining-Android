package com.knightindustries.knighttraining

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import com.knightindustries.knighttraining.core.store.saveResources
import com.knightindustries.knighttraining.core.model.Resource
import androidx.compose.ui.test.hasContentDescription
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
import com.knightindustries.knighttraining.core.store.deleteAllUserData
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File

/** The library on a device: add a book by hand, finish it, see its history. No network involved. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class LibraryTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)).around(compose)

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

    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun addABookAndFinishIt() {
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.tab_resources)), 10_000)
        compose.onNodeWithText(text(R.string.tab_resources)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.library_empty_title)), 5_000)
        screenshot("30_library_empty")

        compose.onNodeWithText(text(R.string.add_resource)).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.resource_title)), 5_000)
        compose.onNode(hasText(text(R.string.resource_title)) and hasSetTextAction()).performTextInput("My System")
        compose.onNode(hasText(text(R.string.resource_author)) and hasSetTextAction()).performTextInput("Aron Nimzowitsch")
        screenshot("31_add_resource")
        // The keyboard is up, so the button sits below the sheet's visible area until scrolled to.
        compose.onNode(hasText(text(R.string.add)) and hasClickAction()).performScrollTo().performClick()

        compose.waitUntilAtLeastOneExists(hasText("My System"), 5_000)
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.kinds_book)), 5_000)
        screenshot("32_library")

        compose.onNodeWithText("My System").performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.mark_finished)), 5_000)
        screenshot("33_detail")
        compose.onNodeWithText(text(R.string.mark_finished)).performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.finish_title)), 5_000)
        compose.onNode(hasText(text(R.string.finish_notes)) and hasSetTextAction()).performTextInput("Overprotection clicked")
        compose.onNodeWithText(text(R.string.save)).performClick()

        compose.waitUntilAtLeastOneExists(hasText(text(R.string.start_again)), 5_000)
        compose.waitUntilAtLeastOneExists(hasText("Overprotection clicked"), 5_000)
        compose.waitUntilAtLeastOneExists(hasText("100%"), 5_000)
        screenshot("34_detail_finished")
    }

    /** A kind's header, not its filter chip (a checkbox) of the same name. */
    private fun header(id: Int) = hasText(text(id)) and hasClickAction() and SemanticsMatcher.keyNotDefined(SemanticsProperties.Role)

    /** iOS: a chevron on Books opens the shelf; on studies, the full list behind the library's four. */
    @Test
    fun headersOpenEveryResourceOfTheirKind() {
        runBlocking {
            val book = container.store.resourceKind("book")!!
            val study = container.store.resourceKind("lichess_study")!!
            container.store.saveResources(
                listOf("My System", "Endgame Strategy").map { Resource(kind = book, title = it) } +
                    (1..6).map { Resource(kind = study, title = "Study $it") },
            )
        }
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.tab_resources)), 10_000)
        compose.onNodeWithText(text(R.string.tab_resources)).performClick()
        compose.waitUntilAtLeastOneExists(hasText("Study 1"), 5_000)
        compose.onNodeWithText("Study 5").assertDoesNotExist()

        compose.onNode(header(R.string.kinds_lichess_study)).performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasText("Study 6"), 5_000)
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        compose.waitUntilAtLeastOneExists(header(R.string.kinds_book), 5_000)
        compose.onNode(header(R.string.kinds_book)).performClick()
        compose.waitUntilAtLeastOneExists(hasContentDescription("Endgame Strategy"), 5_000)
        screenshot("35_book_shelf")
        compose.onNode(hasContentDescription("My System")).performClick()
        compose.waitUntilAtLeastOneExists(hasText(text(R.string.mark_finished)), 5_000)
    }
}
