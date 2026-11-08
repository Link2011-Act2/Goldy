package jp.linkserver.projectgoldy

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import jp.linkserver.goldy.ApkMetadata
import jp.linkserver.goldy.ApkSigners
import jp.linkserver.goldy.compose.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class EliefLayoutTest {
    @get:Rule(order = 0) val appearance = IsolatedDemoAppearance()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    @Test fun restoredInstallationNeedsNeitherReleaseMetadataNorFileManagement() {
        var installs = 0
        var transfer by mutableStateOf<UpdateTransferState>(UpdateTransferState.Ready)
        val apk = ApkMetadata("example.app", "2.0.0-IntDev_rev10", 2,
            ApkSigners(setOf("a".repeat(64)), false))
        rule.activity.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.8f)) {
                MaterialTheme {
                    Box(Modifier.width(320.dp).fillMaxHeight()) {
                        UpdateOverviewScreen(preparedApk = apk, transfer = transfer, onBack = {},
                            onAction = { if (it == UpdateAction.Install) { installs++; transfer = UpdateTransferState.HandedOff } })
                    }
                }
            }
        }
        rule.onNodeWithText(apk.versionName).assertIsDisplayed()
        rule.onNodeWithText("Install").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, installs)
        rule.onNodeWithText("Installer opened.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Install").assertDoesNotExist()
        rule.onNodeWithText("Download APK").assertDoesNotExist()
        rule.onNodeWithText("Release notes").assertDoesNotExist()
        rule.onNodeWithText("No update is available.").assertDoesNotExist()
        // Only the host's foreground-return/reconciliation can enable another explicit install.
        rule.runOnIdle { transfer = UpdateTransferState.Ready }
        rule.onNodeWithText("Install").performScrollTo().performClick()
        assertEquals(2, installs)
    }

    private fun screen(state: UpdateUiState = fixture(0), heightLimited: Boolean = false) {
        rule.activity.setContent {
            MaterialTheme {
                Box(if (heightLimited) Modifier.fillMaxWidth().height(300.dp) else Modifier.fillMaxSize()) {
                    UpdateOverviewScreen(state, {}, {}, {})
                }
            }
        }
    }

    @Test fun fixedActionsStayReachableAndLargeFontsAdaptToOneScroll() {
        screen()
        val large = rule.activity.resources.configuration.fontScale >= 1.5f
        if (large) {
            rule.onNodeWithTag("goldy-inline-actions").assertExists()
            rule.onNodeWithTag("goldy-docked-actions").assertDoesNotExist()
            rule.onNodeWithText("Download APK").performScrollTo().assertIsDisplayed()
        } else {
            rule.onNodeWithTag("goldy-docked-actions").assertIsDisplayed()
            val before = rule.onNodeWithText("Download APK").fetchSemanticsNode().boundsInRoot
            rule.onNodeWithTag("goldy-content").performTouchInput { swipeUp() }
            val after = rule.onNodeWithText("Download APK").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertEquals(before.top, after.top, 1f)
        }
        capture(if (large) "adaptive-large-font" else "docked-actions")
    }

    @Test fun reducedHeightStillAllowsTheDownloadAction() {
        screen(heightLimited = true)
        rule.onNodeWithTag("goldy-inline-actions").assertExists()
        rule.onNodeWithText("Download APK").performScrollTo().assertIsDisplayed()
    }

    @Test fun missingHistoryDoesNotLeaveAnEmptySection() {
        screen(fixture(12))
        rule.onNodeWithTag("history-toggle").assertDoesNotExist()
        rule.onNodeWithText("Download APK").performScrollTo().assertIsDisplayed()
    }

    @Test fun missingApkAfterFailureStillOffersTheReleasePage() {
        var opened: String? = null
        rule.activity.setContent {
            MaterialTheme {
                UpdateOverviewScreen(fixture(6).copy(transfer = UpdateTransferState.Failed("Unavailable", retry = null)),
                    {}, { opened = it }, {})
            }
        }
        rule.onNodeWithText("Download APK").assertDoesNotExist()
        rule.onNodeWithText("Open on GitHub").performScrollTo().performClick()
        assertEquals(fixture(6).update!!.release.url, opened)
    }

    @Test fun displayCustomizationPreservesRawMetadataAndBackCallback() {
        var backs = 0
        rule.activity.setContent {
            MaterialTheme {
                UpdateOverviewScreen(fixture(0), { backs++ }, {}, {},
                    strings = UpdateStrings(title = "Host title", back = "Host back"),
                    presentation = UpdatePresentation(versionLabel = { "Build " + it.tag.removePrefix("v") }, historyChannelLabel = { "Host channel" },
                        backIcon = { Text("←") }))
            }
        }
        rule.onNodeWithText("Host title").assertIsDisplayed()
        rule.onNodeWithText("Build 2.4.0-Beta").assertIsDisplayed()
        rule.onNodeWithContentDescription("Host back").performClick()
        assertEquals(1, backs)
        rule.onNodeWithTag("history-toggle").performScrollTo().performClick()
        rule.onAllNodesWithText("Host channel").assertCountEquals(2)
        assertEquals("v2.4.0-Beta", fixture(0).update!!.release.tag)
    }

    @Test fun notesAndActionsRespectTheHostWidthAndSystemInsets() {
        screen()
        val density = rule.activity.resources.displayMetrics.density
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val content = rule.onNodeWithTag("goldy-content").fetchSemanticsNode().boundsInRoot
        assertTrue(content.width <= 840f * density + 1)
        assertTrue(content.left >= root.left && content.right <= root.right)
        rule.onNodeWithText("Download APK").performScrollTo().assertIsDisplayed()
        val button = rule.onNodeWithText("Download APK").fetchSemanticsNode().boundsInRoot
        assertTrue(button.top >= root.top && button.bottom <= root.bottom)
        capture("width-and-insets")
    }

    @Test fun imeKeepsActionsAccessibleWithoutCoveringThem() {
        var imeBottom = 0
        var keyboard: androidx.compose.ui.platform.SoftwareKeyboardController? = null
        rule.activity.setContent {
            MaterialTheme {
                val density = LocalDensity.current
                keyboard = LocalSoftwareKeyboardController.current
                imeBottom = WindowInsets.ime.getBottom(density)
                var text by remember { mutableStateOf("") }
                Column(Modifier.fillMaxSize()) {
                    OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().testTag("ime-input"))
                    UpdateOverviewScreen(fixture(0), {}, {}, {}, modifier = Modifier.weight(1f))
                }
            }
        }
        rule.onNodeWithTag("ime-input").performClick().performTextInput("Demo")
        rule.runOnIdle { keyboard?.show() }
        rule.waitUntil(15_000) { imeBottom > 0 }
        rule.onNodeWithTag("goldy-inline-actions").assertExists()
        rule.onNodeWithText("Download APK").performScrollTo().assertIsDisplayed()
        capture("ime-actions")
        rule.runOnIdle { keyboard?.hide() }
        rule.waitUntil(15_000) { imeBottom == 0 }
    }

    private fun capture(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val destination = File(rule.activity.getExternalFilesDir(null), "elief-ui-review").apply { mkdirs() }
        File(destination, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
