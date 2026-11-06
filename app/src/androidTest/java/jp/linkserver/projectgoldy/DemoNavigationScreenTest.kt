package jp.linkserver.projectgoldy

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class DemoNavigationScreenTest {
    @get:Rule(order = 0) val appearance = IsolatedDemoAppearance()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    private fun go(label: String, key: String) {
        rule.onNodeWithText(label).performScrollTo().performClick()
        rule.onNodeWithTag("demo-screen-$key").assertIsDisplayed()
    }

    private fun slide(key: String, forward: Boolean, action: () -> Unit) {
        // Settle the current window (including a newly opened Dialog) before freezing frames.
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        try {
            action()
            rule.mainClock.advanceTimeBy(64)
            val left = rule.onNodeWithTag("demo-screen-$key").getUnclippedBoundsInRoot().left.value
            assertTrue("$key enters ${if (forward) "from right" else "from left"}: $left",
                if (forward) left > 0 else left < 0)
        } finally {
            rule.mainClock.autoAdvance = true
        }
        rule.onNodeWithTag("demo-screen-$key").assertIsDisplayed()
    }

    @Test fun allTabsUseTheSameHostAndSlideInBothDirections() {
        rule.onNodeWithTag("demo-screen-showcase").assertIsDisplayed()
        for ((label, key) in listOf("Live Demo" to "live", "Update Demo" to "update",
            "Style reference" to "style-reference", "Notifications" to "notifications")) {
            val tab = rule.onNodeWithText(label).performScrollTo()
            slide(key, true) { tab.performClick() }
        }
        for ((label, key) in listOf("Style reference" to "style-reference", "Update Demo" to "update",
            "Live Demo" to "live", "UI Showcase" to "showcase")) {
            val tab = rule.onNodeWithText(label).performScrollTo()
            slide(key, false) { tab.performClick() }
        }
        rule.onNodeWithText("v2.4.0-Beta").assertIsDisplayed()
        assertEquals(0, rule.activity.model.downloadStarts)
    }

    @Test fun detailDirectionPinnedCandidateAndScrollSurviveBackAndRecreation() {
        go("Notifications", "notifications")
        val model = ViewModelProvider(rule.activity)[NotificationDemoViewModel::class.java]
        rule.onNodeWithText("Run offline check").performScrollTo().performClick()
        rule.waitUntil(15_000) { !model.busy && model.engine.state.value.availableUpdate != null }
        val engine = model.engine
        val candidate = engine.state.value.availableUpdate
        rule.onNodeWithTag("notification-manual-check").performScrollTo().performClick()
        rule.onNodeWithTag("goldy-update-check-dialog").assertIsDisplayed()
        val details = rule.onNodeWithTag("goldy-notification-details").performScrollTo()
        slide("notification-details", true) {
            details.performClick()
        }
        rule.onNodeWithTag("latest-release-notes").performScrollTo().assertIsDisplayed()
        val before = detailScroll()
        assertTrue(before > 0)
        capture("notification-details")
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("demo-screen-notification-details").assertIsDisplayed()
        assertSame(engine, ViewModelProvider(rule.activity)[NotificationDemoViewModel::class.java].engine)
        assertEquals(candidate, model.selected)
        assertEquals(before, detailScroll(), 2f)
        slide("notifications", false) { rule.onNodeWithContentDescription("Back").performClick() }
        rule.onNodeWithTag("notification-manual-check").performScrollTo().performClick()
        rule.onNodeWithTag("goldy-notification-details").performScrollTo().performClick()
        rule.onNodeWithTag("demo-screen-notification-details").assertIsDisplayed()
        assertEquals(before, detailScroll(), 2f)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithTag("notification-showcase").assertIsDisplayed()
        assertEquals(1, model.checks)
    }

    @Test fun tabStateAndHistoryAreSavedIndependently() {
        rule.onNodeWithTag("history-toggle").performScrollTo().performClick()
        rule.onNodeWithTag("history-19").performScrollTo().assertIsDisplayed()
        val before = detailScroll()
        val fixtureModel = rule.activity.model
        go("Live Demo", "live")
        rule.onNodeWithText("NITTC Scheduler").performScrollTo().performClick()
        go("Style reference", "style-reference")
        rule.onNodeWithText("Preview action").performScrollTo().performClick()
        go("Notifications", "notifications")
        go("Style reference", "style-reference")
        rule.onNodeWithText("Preview actions: 1").performScrollTo().assertIsDisplayed()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("Preview actions: 1").performScrollTo().assertIsDisplayed()
        go("Live Demo", "live")
        assertEquals(LiveTarget.Scheduler, ViewModelProvider(rule.activity)[LiveDemoViewModel::class.java].target)
        go("UI Showcase", "showcase")
        assertSame(fixtureModel, rule.activity.model)
        rule.onNodeWithTag("history-toggle").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Hide notes"))
        rule.onNodeWithTag("history-19").assertIsDisplayed()
        assertEquals(before, detailScroll(), 2f)
    }

    @Test fun interruptedTransitionsAndBackDuringEntryRestoreTheTargetOnly() {
        rule.onNodeWithTag("demo-screen-showcase").assertIsDisplayed()
        rule.mainClock.autoAdvance = false
        try {
            for (label in listOf("Live Demo", "Style reference", "Notifications", "Update Demo", "Notifications")) {
                // Accessibility activation avoids waiting for a horizontal scroll animation
                // while this test deliberately freezes the frame clock mid-transition.
                rule.onNodeWithText(label).performSemanticsAction(SemanticsActions.OnClick) { it() }
                rule.mainClock.advanceTimeBy(32)
            }
            rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
            rule.mainClock.advanceTimeBy(32)
            rule.activityRule.scenario.recreate()
        } finally {
            rule.mainClock.autoAdvance = true
        }
        rule.onNodeWithTag("demo-screen-showcase").assertIsDisplayed()
        rule.onNodeWithTag("demo-screen-notifications").assertDoesNotExist()
        rule.onNodeWithTag("demo-screen-update").assertDoesNotExist()
        go("Notifications", "notifications")
        rule.onNodeWithTag("notification-manual-check").performScrollTo().assertIsEnabled()
        assertEquals(0, ViewModelProvider(rule.activity)[NotificationDemoViewModel::class.java].checks)
    }

    private fun detailScroll() = rule.onNodeWithTag("goldy-content").fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun capture(name: String) {
        val directory = File(rule.activity.getExternalFilesDir(null), "dialog-navigation").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
