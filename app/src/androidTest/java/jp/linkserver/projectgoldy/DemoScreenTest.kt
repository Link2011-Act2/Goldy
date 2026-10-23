package jp.linkserver.projectgoldy

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.linkserver.goldy.compose.UpdateTransferState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoScreenTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun liveIsMetadataOnlyAndNavigationSurvivesRecreation() {
        rule.onNodeWithText("Live Demo").performScrollTo().performClick()
        rule.onNodeWithText("Metadata only.", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Download APK").assertDoesNotExist()
        rule.onNodeWithText("Open installer").assertDoesNotExist()
        rule.onNodeWithText("NITTC Scheduler").performScrollTo().performClick()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("Metadata only.", substring = true).assertIsDisplayed()
        rule.runOnIdle {
            val model = ViewModelProvider(rule.activity)[LiveDemoViewModel::class.java]
            assertEquals(LiveTarget.Scheduler, model.target)
            assertNull(model.snapshot.catalog)
        }
        capture("live-metadata-only")
    }

    @Test fun selfUpdateUsesActualInstalledIdentityWithoutPretendingDistributionExists() {
        rule.onNodeWithText("Update Demo").performScrollTo().performClick()
        rule.waitUntil(15_000) { !ViewModelProvider(rule.activity)[DemoUpdateViewModel::class.java].restoring }
        rule.runOnIdle {
            val model = ViewModelProvider(rule.activity)[DemoUpdateViewModel::class.java]
            assertFalse(model.restorationFailed)
            assertEquals(rule.activity.packageName, model.identity?.packageName)
            assertTrue(model.identity!!.signers.currentSha256.isNotEmpty())
            assertNull(model.metadata.lastOutcome) // restore may have a cache; no automatic request
        }
        rule.onNodeWithTag("demo-identity").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Download APK").assertDoesNotExist()
        rule.onNodeWithText("Open installer").assertDoesNotExist()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("Goldy Demo · self update").performScrollTo().assertIsDisplayed()
        capture("self-update-unconfigured")
    }

    @Test fun explicitCancelledFixtureCanRetryWithoutNetwork() {
        rule.runOnIdle { rule.activity.model.select(11) }
        rule.onNodeWithText("Download cancelled.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Try again").performScrollTo().performClick()
        rule.runOnIdle { assertTrue(rule.activity.model.state.transfer is UpdateTransferState.Downloading) }
        rule.onNodeWithText("Cancel download").performScrollTo().performClick()
    }

    private fun capture(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val directory = java.io.File(rule.activity.getExternalFilesDir(null), "phase-2-6").apply { mkdirs() }
        java.io.File(directory, "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
