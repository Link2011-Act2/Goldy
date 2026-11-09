package jp.linkserver.projectgoldy

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import jp.linkserver.goldy.*
import jp.linkserver.goldy.compose.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PausedDownloadUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val candidate=UpdateCandidate(GitHubRelease(700,"v2.0-Release",notes="Release notes",
        url="https://github.com/example/app/releases/tag/v2.0-Release"))
    @Test fun retainedProgressAndSingleActionsStayDockedThenResumeUsesNormalColor() {
        val progress=ApkProgress(15_000_000,30_000_000,1)
        var transfer: UpdateTransferState by mutableStateOf(UpdateTransferState.Failed("network"))
        val actions=mutableListOf<UpdateAction>()
        var error=0; var normal=0
        compose.setContent {
            MaterialTheme {
                error=MaterialTheme.colorScheme.error.toArgb();normal=MaterialTheme.colorScheme.primary.toArgb()
                UpdateOverviewScreen(UpdateUiState(candidate,apkAvailable=true,transfer=transfer),{}, {},{
                    actions+=it
                    if(it==UpdateAction.Download) transfer=UpdateTransferState.Downloading(progress)
                },presentation=UpdatePresentation(PausedDownload(progress)))
            }
        }
        compose.onNodeWithTag("goldy-docked-actions").assertIsDisplayed()
        compose.onAllNodesWithText("Resume download").assertCountEquals(1)
        compose.onAllNodesWithText("Cancel download").assertCountEquals(1)
        compose.onNodeWithTag("update-error").assertDoesNotExist()
        compose.onNodeWithText("15.00 MB / 30.00 MB · 50.00%").assertIsDisplayed()
        fun barColor(): Int {
            val pixels=compose.onNodeWithTag("goldy-download-progress").captureToImage().toPixelMap()
            return pixels[pixels.width/4,pixels.height/2].toArgb()
        }
        assertEquals(error,barColor());assertTrue(actions.isEmpty())
        compose.onNodeWithText("Resume download").performClick()
        assertEquals(listOf(UpdateAction.Download),actions)
        assertEquals(normal,barColor()) // Stale presentation data must not make Downloading red.
        compose.onNodeWithText("Cancel download").performClick()
        assertEquals(listOf(UpdateAction.Download,UpdateAction.CancelDownload),actions)
    }
    @Test fun unknownPausedTotalIsStaticHasNoPercentageAndInlineActionsFollowNotes() {
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.8f)) {
                MaterialTheme { Box(Modifier.width(320.dp)) {
                    UpdateOverviewScreen(UpdateUiState(candidate,apkAvailable=true,
                        transfer=UpdateTransferState.Failed("network")),{}, {},{},
                        presentation=UpdatePresentation(PausedDownload(ApkProgress(15_000_000,null,1))))
                } }
            }
        }
        val notes=compose.onNodeWithTag("latest-release-notes").getUnclippedBoundsInRoot()
        val panel=compose.onNodeWithTag("goldy-inline-actions").getUnclippedBoundsInRoot()
        assertTrue(notes.bottom<=panel.top)
        compose.onNodeWithText("Resume download").performScrollTo()
        compose.onNodeWithText("15.00 MB").assertIsDisplayed()
        val bar=compose.onNodeWithTag("goldy-download-progress")
        assertFalse(bar.fetchSemanticsNode().config.contains(SemanticsProperties.ProgressBarRangeInfo))
        val before=bar.captureToImage().toPixelMap()
        compose.mainClock.advanceTimeBy(1000)
        val after=bar.captureToImage().toPixelMap()
        assertEquals(before.width,after.width);assertEquals(before.height,after.height)
        for(y in 0 until before.height) for(x in 0 until before.width)
            assertEquals(before[x,y].toArgb(),after[x,y].toArgb())
        compose.onAllNodesWithText("Resume download").assertCountEquals(1)
        compose.onAllNodesWithText("Cancel download").assertCountEquals(1)
    }
}
