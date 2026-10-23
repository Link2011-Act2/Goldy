package jp.linkserver.projectgoldy

import android.os.Build
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.graphics.asAndroidBitmap
import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.linkserver.goldy.compose.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateScreenTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun select(index: Int) = rule.runOnIdle { rule.activity.model.select(index) }

    @Test fun updateBackAndReleaseCallbacks() {
        rule.onNodeWithText("v2.4.0-Beta").assertIsDisplayed()
        rule.runOnIdle { assertEquals("v2.4.0-Beta", rule.activity.model.state.update?.release?.tag) }
        rule.onNodeWithContentDescription("Back").performClick()
        rule.runOnIdle { assertEquals("Back requested by host", rule.activity.model.event) }
        rule.onAllNodesWithText("Open on GitHub")[0].performScrollTo().performClick()
        rule.runOnIdle { assertTrue(rule.activity.model.event.startsWith("Host received link: https://github.com/")) }
    }
    @Test fun systemBackStaysHostOwned() {
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.runOnIdle { assertEquals("Back requested by host", rule.activity.model.event) }
    }
    @Test fun noUpdateAndNoApk() {
        select(1)
        rule.onNodeWithText("No update is available.").assertIsDisplayed()
        rule.onNodeWithText("Download APK").assertDoesNotExist()
        select(6)
        rule.onNodeWithText("No compatible APK", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Download APK").assertDoesNotExist()
    }
    @Test fun cancelUnknownLengthTransfer() {
        select(3)
        rule.onNodeWithText("42000 B", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Cancel download").performScrollTo().performClick()
        rule.onNodeWithText("Download cancelled.").assertIsDisplayed()
    }
    @Test fun retryAndDuplicateTapGuard() {
        select(4)
        rule.onNodeWithText("Connection interrupted.", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Try again").performScrollTo().performClick()
        rule.runOnIdle {
            // A second event delivered before recomposition must still be rejected by the host.
            rule.activity.model.action(UpdateAction.Download)
            assertEquals(1, rule.activity.model.downloadStarts)
        }
        rule.onNodeWithText("Cancel download").performScrollTo().performClick()
    }
    @Test fun permissionIsHostOwned() {
        select(5)
        rule.onNodeWithText("Allow installation").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("Fixture permission guide · no settings Activity launched", rule.activity.model.event) }
        rule.onNodeWithText("Try again").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(UpdateTransferState.HandedOff, rule.activity.model.state.transfer) }
        rule.onNodeWithText("Installer opened.", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Open installer").assertDoesNotExist()
    }
    @Test fun emptyNotes() {
        select(7)
        rule.onNodeWithTag("goldy-content").performTouchInput { swipeUp() }
        rule.runOnIdle {
            assertTrue(textViews(rule.activity.window.decorView).any { it.text.toString() == "No release notes were provided." })
        }
    }
    @Test fun expandedHistoryAndScrollSurviveActivityRecreation() {
        rule.onNodeWithTag("history-19").assertDoesNotExist()
        rule.onNodeWithTag("history-toggle").performScrollTo().performClick()
        rule.onNodeWithTag("history-19").performScrollTo().assertIsDisplayed()
        val before = rule.onNodeWithTag("goldy-content").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(before > 0f)
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("history-toggle").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Hide notes"))
        rule.onNodeWithTag("history-19").assertIsDisplayed()
        capture("history-restored")
        val after = rule.onNodeWithTag("goldy-content").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals(before, after, 2f)
    }
    @Test fun hostFontAndThemes() {
        var originalTypeface: android.graphics.Typeface? = null
        rule.runOnIdle { originalTypeface = textViews(rule.activity.window.decorView).first { it.text.toString().contains("A little more clarity") }.typeface }
        capture("light")
        rule.onNodeWithText("Dark", useUnmergedTree = true).performScrollTo().performClick()
        capture("dark")
        rule.onNodeWithText("Expressive", useUnmergedTree = true).performScrollTo().performClick()
        capture("expressive-dark")
        rule.onNodeWithText("Dark", useUnmergedTree = true).performScrollTo().performClick()
        capture("expressive-light")
        if (Build.VERSION.SDK_INT >= 31) {
            rule.onNodeWithText("Dynamic", useUnmergedTree = true).performScrollTo().performClick()
            capture("dynamic-light")
            rule.onNodeWithText("Dark", useUnmergedTree = true).performScrollTo().performClick()
            capture("dynamic-dark")
        } else rule.onNodeWithText("Dynamic").assertIsNotEnabled()
        rule.onNodeWithText("Custom font", useUnmergedTree = true).performScrollTo().performClick()
        rule.runOnIdle {
            val markdown = textViews(rule.activity.window.decorView).first { it.text.toString().contains("A little more clarity") }
            assertNotEquals(originalTypeface, markdown.typeface)
        }
        capture("custom-font")
        rule.onNodeWithText("Download APK").performScrollTo().assertIsDisplayed()
    }
    @Test fun markdownLinkDelegatesToHost() {
        rule.onNodeWithText("Release notes").performScrollTo()
        capture("markdown-start")
        rule.onNodeWithTag("goldy-content").performTouchInput { swipeUp() }
        capture("markdown-scroll")
        rule.runOnIdle {
            val view = textViews(rule.activity.window.decorView).first { it.text.toString().contains("A little more clarity") }
            val spans = view.text as Spanned
            val link = spans.getSpans(0, spans.length, ClickableSpan::class.java).first()
            link.onClick(view)
            assertTrue(rule.activity.model.event.startsWith("Host received link:"))
            assertTrue(spans.getSpans(0, spans.length, Any::class.java).any { it.javaClass.name.contains("TableRowSpan") })
        }
    }
    @Test fun untrustedMarkdownCannotLaunchOrLoadImages() {
        select(10)
        rule.runOnIdle {
            val view = textViews(rule.activity.window.decorView).first { it.text.toString().contains("Safe") }
            val spans = view.text as Spanned
            val links = spans.getSpans(0, spans.length, ClickableSpan::class.java).sortedBy { spans.getSpanStart(it) }
            assertEquals(2, links.size)
            val before = rule.activity.model.event
            links[1].onClick(view)
            assertEquals(before, rule.activity.model.event)
            links[0].onClick(view)
            assertEquals("Host received link: https://example.org/safe", rule.activity.model.event)
            assertTrue(spans.getSpans(0, spans.length, Any::class.java).none { it.javaClass.name.contains("AsyncDrawable") })
            assertFalse(view.text.toString().contains("bad()"))
        }
    }
    @Test fun fixtureStateScreenshots() {
        listOf(0, 2, 3, 4, 5, 6, 8, 9).forEach { index -> select(index); capture("state-" + index) }
    }
    private fun capture(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val directory = File(rule.activity.getExternalFilesDir(null), "phase-2-4").apply { mkdirs() }
        File(directory, name + ".png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun textViews(root: View): List<TextView> = when (root) {
        is TextView -> listOf(root)
        is ViewGroup -> (0 until root.childCount).flatMap { textViews(root.getChildAt(it)) }
        else -> emptyList()
    }
}
