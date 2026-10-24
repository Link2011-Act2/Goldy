package jp.linkserver.projectgoldy

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import jp.linkserver.goldy.*
import jp.linkserver.goldy.compose.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class NotificationScreenTest {
    @get:Rule(order = 0) val appearance = IsolatedDemoAppearance()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()
    private fun model() = ViewModelProvider(rule.activity)[NotificationDemoViewModel::class.java]
    private fun open() { rule.onNodeWithText("Notifications").performScrollTo().performClick() }
    private fun settled() { rule.waitUntil(15_000) { !model().busy } }
    private fun check() {
        rule.onNodeWithText("Run offline check").performScrollTo().performClick()
        settled()
    }
    private fun button(text: String) = rule.onNodeWithText(text).let {
        if (text == "Not now") it else it.performScrollTo()
    }

    @Test fun hideIgnoreRestoreAndManualDetailsHaveSeparateMeanings() {
        open(); check()
        rule.onNodeWithTag("goldy-notification-banner").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Hide update banner for now").performClick(); settled()
        rule.onNodeWithTag("goldy-notification-banner").assertDoesNotExist()
        rule.onNodeWithTag("goldy-notification-icon").assertExists()
        val checks = model().checks
        button("Restore cached session").performClick(); settled()
        assertEquals(checks, model().checks)
        assertTrue(model().engine.state.value.fromCache)
        rule.onNodeWithTag("goldy-notification-banner").assertExists()
        rule.onNodeWithContentDescription("Hide update banner for now").performScrollTo().performClick(); settled()
        button("Show banner again").performClick(); settled()
        rule.onNodeWithTag("goldy-notification-banner").assertExists()
        rule.onNodeWithTag("goldy-notification-icon").performScrollTo().performClick()
        rule.onNodeWithTag("goldy-notification-ignore").performScrollTo().performClick(); settled()
        assertNull(model().engine.state.value.notificationUpdate)
        assertNotNull(model().engine.state.value.availableUpdate)
        rule.onNodeWithTag("goldy-notification-details").performScrollTo().performClick()
        rule.onNodeWithText("v2.1.1-IntDev_rev10").assertIsDisplayed()
        rule.onNodeWithText("Download APK").assertDoesNotExist()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithTag("goldy-notification-icon").assertDoesNotExist()
        button("Restore cached session").performClick(); settled()
        rule.onNodeWithTag("goldy-notification-icon").assertDoesNotExist()
        button("Reset ignored versions").performClick(); settled()
        rule.onNodeWithTag("goldy-notification-icon").assertExists()
        rule.onNodeWithTag("goldy-notification-banner").performScrollTo().assertIsDisplayed()
        capture("restored-banner")
    }

    @Test fun failedRefreshKeepsCandidateAndRejectsDuplicateChecks() {
        open(); check()
        val before = model().checks
        button("Network failure").performClick()
        rule.runOnIdle { model().check(); model().check() }
        settled()
        assertEquals(before + 1, model().checks)
        assertTrue(model().engine.state.value.lastOutcome is CheckOutcome.Failure)
        assertNotNull(model().engine.state.value.availableUpdate)
        rule.onNodeWithTag("goldy-notification-icon").performScrollTo().performClick()
        rule.onNodeWithTag("goldy-notification-check").performScrollTo().assertIsEnabled()
        rule.onNodeWithTag("goldy-notification-details").performScrollTo().assertIsDisplayed()
        capture("cached-failure-dialog", dialog = true)
        button("Not now").performClick()
        button("Available").performClick(); check()
        assertTrue(model().engine.state.value.lastOutcome is CheckOutcome.Available)
    }

    @Test fun openPromptAndIgnoreSurviveActivityRecreation() {
        open(); check()
        val engine = model().engine
        rule.onNodeWithTag("goldy-notification-icon").performScrollTo().performClick()
        rule.activityRule.scenario.recreate()
        assertSame(engine, model().engine)
        rule.onNodeWithTag("goldy-update-check-dialog").assertIsDisplayed()
        rule.onNodeWithTag("goldy-notification-ignore").performScrollTo().performClick(); settled()
        rule.activityRule.scenario.recreate()
        assertNull(model().engine.state.value.notificationUpdate)
        rule.onNodeWithTag("goldy-notification-details").performScrollTo().assertIsDisplayed()
        button("Not now").performClick()
        rule.onNodeWithTag("goldy-notification-icon").assertDoesNotExist()
    }

    @Test fun noUpdateStillOffersManualCheckWithoutInventingNotifications() {
        open(); button("Up to date").performClick(); check()
        assertEquals(CheckOutcome.UpToDate, model().engine.state.value.lastOutcome)
        rule.onNodeWithTag("goldy-notification-banner").assertDoesNotExist()
        rule.onNodeWithTag("goldy-notification-icon").assertDoesNotExist()
        button("Manual update check").performClick()
        rule.onNodeWithText("You are up to date.").assertIsDisplayed()
        rule.onNodeWithTag("goldy-notification-check").assertIsEnabled()
        rule.onNodeWithTag("goldy-notification-details").assertDoesNotExist()
        rule.onNodeWithTag("goldy-notification-ignore").assertDoesNotExist()
        capture("no-update-dialog", dialog = true)
    }

    @Test fun stylesThemesFontsAndSmallWindowsKeepAllActionsReachable() {
        open(); check()
        button("Custom font").performClick()
        for (style in DemoStyle.entries) {
            rule.chooseDemoStyle(style)
            for (dark in listOf(false, true)) {
                if (DemoAppearancePreferences(rule.activity).load().dark != dark) button("Dark").performClick()
                rule.onNodeWithTag("goldy-notification-banner").performScrollTo().assertIsDisplayed()
                capture("${style.name}-$dark-banner")
                rule.onNodeWithTag("goldy-notification-icon").performScrollTo().performClick()
                rule.onNodeWithTag("goldy-notification-check").performScrollTo().assertIsDisplayed()
                rule.onNodeWithTag("goldy-notification-ignore").performScrollTo().assertIsDisplayed()
                rule.onNodeWithTag("goldy-notification-details").performScrollTo().assertIsDisplayed()
                capture("${style.name}-$dark-dialog", dialog = true)
                button("Not now").performClick()
            }
        }
        rule.chooseDemoStyle(DemoStyle.Material3Expressive)
        button("Dynamic").performClick()
        rule.onNodeWithTag("goldy-notification-banner").performScrollTo().assertIsDisplayed()
        capture("dynamic-banner")
        assertEquals(1, model().checks)
    }

    @Test fun cancellingARealGoldCheckKeepsCacheAndAllowsRetry() {
        open(); check()
        button("Slow check").performClick()
        button("Run offline check").performClick()
        rule.waitUntil(5_000) { model().engine.state.value.checking }
        button("Cancel check").performClick(); settled()
        assertFalse(model().engine.state.value.checking)
        assertNotNull(model().engine.state.value.availableUpdate)
        button("Available").performClick(); check()
        assertTrue(model().engine.state.value.lastOutcome is CheckOutcome.Available)
    }

    @Test fun customStringsAndIconsOnlyEmitHostCallbacks() {
        val candidate = fixture(0).update!!
        var open = 0; var checks = 0; var ignored: String? = null
        var visible by mutableStateOf(true)
        rule.activity.setContent {
            MaterialTheme {
                Column {
                    UpdateNotificationBanner(GoldyState(candidate, candidate, true), { open++ }, {})
                    UpdateNotificationIcon(GoldyState(candidate, candidate), { open++ })
                }
                if (visible) UpdateCheckDialog(GoldyState(candidate, candidate), { checks++ }, { open++ },
                    { ignored = it }, { visible = false },
                    strings = UpdateNotificationStrings(title = "ホスト更新", openDetails = "詳細を表示"), icon = { Text("◎") })
            }
        }
        rule.onNodeWithText("ホスト更新").assertIsDisplayed()
        rule.onNodeWithTag("goldy-notification-check").performScrollTo().performClick()
        rule.onNodeWithTag("goldy-notification-details").performScrollTo().performClick()
        rule.onNodeWithTag("goldy-notification-ignore").performScrollTo().performClick()
        assertEquals(1, checks); assertEquals(1, open); assertEquals(candidate.release.tag, ignored)
        button("Not now").performClick()
        rule.onNodeWithTag("goldy-update-check-dialog").assertDoesNotExist()
    }

    private fun capture(name: String, dialog: Boolean = false) {
        rule.waitForIdle()
        val bitmap = (if (dialog) rule.onNodeWithTag("goldy-update-check-dialog") else rule.onRoot()).captureToImage().asAndroidBitmap()
        val directory = File(rule.activity.getExternalFilesDir(null), "unified-updates").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
