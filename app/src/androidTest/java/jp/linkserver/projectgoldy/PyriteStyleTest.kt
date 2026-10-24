package jp.linkserver.projectgoldy

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import jp.linkserver.goldy.compose.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class PyriteStyleTest {
    @get:Rule(order = 0) val appearance = IsolatedDemoAppearance()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    private data class Variant(val name: String, val nothing: Boolean = false, val dark: Boolean = false, val dynamic: Boolean = false)
    private val variants = listOf(
        Variant("m3e-light"), Variant("m3e-dark", dark = true),
        Variant("nothing-light", nothing = true), Variant("nothing-dark", nothing = true, dark = true),
        Variant("m3e-dynamic-light", dynamic = true), Variant("m3e-dynamic-dark", dark = true, dynamic = true)
    )
    private val comparisonState = fixture(0).let { state -> state.copy(update = state.update!!.let { candidate ->
        candidate.copy(release = candidate.release.copy(notes = "## A clearer update\n\nRead the changes before downloading.\n\n- Clear progress and cancellation.\n- Your theme and typography.\n\n[Release details](https://example.org/release)"))
    }) }

    @Test fun publicThemesMatchPyriteCardsAndKeepReadableRoles() {
        var variant by mutableStateOf(variants.first())
        var reference by mutableStateOf(false)
        var colors = MaterialRoles()
        rule.activity.setContent {
            DemoTheme(if (variant.nothing) DemoStyle.Nothing else DemoStyle.Material3Expressive, variant.dark, variant.dynamic, false) {
                val scheme = MaterialTheme.colorScheme
                colors = MaterialRoles(scheme.surface, LocalUpdateCardStyle.current.containerColor, scheme.onSurface,
                    scheme.onSurfaceVariant, scheme.primary, scheme.onPrimary, scheme.errorContainer, scheme.onErrorContainer)
                if (reference) StyleReferenceScreen {} else UpdateOverviewScreen(comparisonState, {}, {}, {})
            }
        }
        val metrics = JSONArray()
        for (selected in variants) {
            rule.runOnIdle { variant = selected; reference = false }
            rule.onNodeWithText("v2.4.0-Beta").assertIsDisplayed()
            rule.onNodeWithText("Beta").assertHasNoClickAction()
            rule.onNodeWithText("Download APK").performScrollTo().assertIsDisplayed()
            rule.runOnIdle {
                val bars = WindowCompat.getInsetsController(rule.activity.window, rule.activity.window.decorView)
                assertEquals("Status bar icons ${selected.name}", !selected.dark, bars.isAppearanceLightStatusBars)
                assertEquals("Navigation bar icons ${selected.name}", !selected.dark, bars.isAppearanceLightNavigationBars)
            }
            assertTrue("Body text contrast ${selected.name}", contrast(colors.foreground, colors.card) >= 4.5)
            assertTrue("Status contrast ${selected.name}", contrast(colors.secondaryText, colors.canvas) >= 4.5)
            assertTrue("Action contrast ${selected.name}", contrast(colors.onPrimary, colors.primary) >= 4.5)
            assertTrue("Error contrast ${selected.name}", contrast(colors.onError, colors.error) >= 4.5)
            if (selected.nothing) {
                assertEquals(if (selected.dark) Color.Black else Color(0xFFEDEDED), colors.canvas)
                assertEquals(if (selected.dark) Color.White else Color.Black, colors.primary)
            }
            capture(selected.name)
            rule.onNodeWithTag("latest-release-notes").performScrollTo().assertIsDisplayed()
            val eliefFill = cardPixel("latest-release-notes")
            rule.onNodeWithTag("history-toggle").performScrollTo().performClick()
            rule.onNodeWithTag("history-19").performScrollTo().assertIsDisplayed()
            capture(selected.name + "-history")
            rule.onNodeWithTag("history-toggle").performScrollTo().performClick()
            rule.runOnIdle { reference = true }
            rule.onNodeWithTag("pyrite-reference-card").performScrollTo().assertIsDisplayed()
            val pyriteFill = cardPixel("pyrite-reference-card")
            assertEquals("Shared card fill ${selected.name}", pyriteFill, eliefFill)
            capture(selected.name + "-pyrite")
            metrics.put(JSONObject().put("theme", selected.name).put("canvas", hex(colors.canvas)).put("card", hex(colors.card))
                .put("primary", hex(colors.primary)).put("bodyContrast", contrast(colors.foreground, colors.card))
                .put("actionContrast", contrast(colors.onPrimary, colors.primary)).put("errorContrast", contrast(colors.onError, colors.error))
                .put("eliefCardPixel", eliefFill).put("pyriteCardPixel", pyriteFill))
        }
        rule.runOnIdle { variant = Variant("nothing-dynamic-ignored", nothing = true, dynamic = true); reference = false }
        rule.waitForIdle()
        assertEquals(Color.Black, colors.primary)
        assertEquals(Color(0xFFEDEDED), colors.canvas)
        File(directory(), "theme-metrics.json").writeText(metrics.toString(2))
    }

    @Test fun demoStyleSwitchAndRecreationPreserveHistory() {
        rule.chooseDemoStyle(DemoStyle.Material3Expressive)
        rule.onNodeWithTag("history-toggle").performScrollTo().performClick()
        rule.onNodeWithTag("history-19").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Dynamic").performScrollTo().performClick().assertIsSelected()
        rule.chooseDemoStyle(DemoStyle.Nothing)
        rule.onNodeWithTag("history-toggle").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Hide notes"))
        rule.onNodeWithTag("history-19").performScrollTo().assertIsDisplayed()
        val before = scrollOffset()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("history-toggle").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Hide notes"))
        rule.onNodeWithTag("history-19").assertIsDisplayed()
        assertEquals(before, scrollOffset(), 2f)
        rule.onNodeWithText("Dynamic").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("Dynamic").assertIsNotSelected()
        capture("nothing-demo-restored")
        rule.chooseDemoStyle(DemoStyle.Material3Expressive)
        rule.onNodeWithText("Dynamic").performScrollTo().assertIsEnabled().assertIsSelected()
        rule.onNodeWithTag("history-toggle").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Hide notes"))
    }

    @Test fun nothingStatesAndChannelsKeepActionsReachable() {
        var dark by mutableStateOf(false)
        var state by mutableStateOf(fixture(0))
        var action: UpdateAction? = null
        rule.activity.setContent {
            DemoTheme(DemoStyle.Nothing, dark, false, false) { UpdateOverviewScreen(state, {}, {}, { action = it }) }
        }
        for (isDark in listOf(false, true)) {
            rule.runOnIdle { dark = isDark; state = fixture(2) }
            rule.onNodeWithText("Cancel download").performScrollTo().performClick()
            assertEquals(UpdateAction.CancelDownload, action)
            rule.runOnIdle { state = fixture(11) }
            rule.onNodeWithText("Try again").performScrollTo().performClick()
            assertEquals(UpdateAction.Download, action)
            rule.runOnIdle { state = fixture(4) }
            rule.onNodeWithTag("update-error").performScrollTo().assertIsDisplayed()
            capture(if (isDark) "nothing-dark-error" else "nothing-light-error")
            rule.runOnIdle { state = fixture(5) }
            rule.onNodeWithText("Allow installation").performScrollTo().performClick()
            assertEquals(UpdateAction.OpenInstallPermissionSettings, action)
            rule.runOnIdle { state = fixture(6) }
            rule.onNodeWithText("Open on GitHub").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("Download APK").assertDoesNotExist()
            for ((index, label) in listOf(13 to "Release", 14 to "IntDev")) {
                rule.runOnIdle { state = fixture(index) }
                rule.onNodeWithText(label).assertHasNoClickAction()
                rule.onNodeWithText("Download APK").performScrollTo().assertIsDisplayed()
            }
            rule.runOnIdle { state = fixture(1) }
            rule.onNodeWithText("No update is available.").assertIsDisplayed()
        }
    }

    @Test fun actualPyriteReferenceActionRestoresWithoutStartingATransfer() {
        rule.chooseDemoStyle(DemoStyle.Nothing)
        rule.onNodeWithText("Style reference").performScrollTo().performClick()
        rule.onNodeWithText("Preview action").performScrollTo().performClick()
        rule.onNodeWithText("Preview actions: 1").performScrollTo().assertIsDisplayed()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("Preview actions: 1").performScrollTo().assertIsDisplayed()
        assertEquals(0, rule.activity.model.downloadStarts)
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("v2.4.0-Beta").assertIsDisplayed()
    }

    private data class MaterialRoles(val canvas: Color = Color.Black, val card: Color = Color.Black,
        val foreground: Color = Color.White, val secondaryText: Color = Color.White,
        val primary: Color = Color.White, val onPrimary: Color = Color.Black,
        val error: Color = Color.Black, val onError: Color = Color.White)
    private fun contrast(a: Color, b: Color): Double {
        val first = a.luminance().toDouble(); val second = b.luminance().toDouble()
        return (maxOf(first, second) + .05) / (minOf(first, second) + .05)
    }
    private fun hex(color: Color) = String.format("#%08X", color.toArgb())
    private fun scrollOffset() = rule.onNodeWithTag("goldy-content").fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange].value()
    private fun directory() = File(rule.activity.getExternalFilesDir(null), "pyrite-style-review").apply { mkdirs() }
    private fun capture(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(directory(), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun cardPixel(tag: String): Int {
        rule.waitForIdle()
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        return bitmap.getPixel(bounds.center.x.toInt(), (bounds.top + 8 * rule.activity.resources.displayMetrics.density).toInt())
    }
}
