package jp.linkserver.projectgoldy

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import java.io.File

/** Isolates only appearance preferences, preserving the updater's storage and prior user settings. */
class IsolatedDemoAppearance : ExternalResource() {
    private val preferences = InstrumentationRegistry.getInstrumentation().targetContext
        .getSharedPreferences(DemoAppearancePreferences.FILE_NAME, Context.MODE_PRIVATE)
    private var previous: Map<String, *> = emptyMap<String, Any?>()
    override fun before() {
        previous = preferences.all.toMap()
        check(preferences.edit().clear().commit())
    }
    override fun after() {
        val editor = preferences.edit().clear()
        previous.forEach { (key, value) -> when (value) {
            is String -> editor.putString(key, value)
            is Boolean -> editor.putBoolean(key, value)
        } }
        check(editor.commit())
    }
}

internal fun ComposeTestRule.chooseDemoStyle(style: DemoStyle) {
    onNodeWithTag("demo-style-picker").performScrollTo().performClick()
    onNodeWithTag("demo-style-${style.name}").performScrollTo().performClick()
    onNodeWithTag("demo-style-picker").assertTextContains("Style: ${style.label}")
}

class DemoAppearanceTest {
    @get:Rule(order = 0) val appearance = IsolatedDemoAppearance()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    @Test fun firstLaunchAndEachStyleAreExclusiveAndSurviveRecreation() {
        assertEquals(DemoAppearance(), saved())
        assertStyle(DemoStyle.Material3Expressive)
        for (style in DemoStyle.entries) {
            rule.chooseDemoStyle(style)
            for (dark in listOf(false, true)) {
                if (saved().dark != dark) rule.onNodeWithText("Dark").performScrollTo().performClick()
                assertStyle(style)
                rule.onNodeWithText("Download APK").performScrollTo().assertIsDisplayed()
                capture("${style.name}-${if (dark) "dark" else "light"}")
                rule.activityRule.scenario.recreate()
                assertStyle(style)
                assertEquals(dark, saved().dark)
                rule.onNodeWithText("Dark").performScrollTo().let {
                    if (dark) it.assertIsSelected() else it.assertIsNotSelected()
                }
                rule.onNodeWithText("Download APK").performScrollTo().assertIsDisplayed()
            }
        }
    }

    @Test @SdkSuppress(minSdkVersion = 31)
    fun preferencesSurviveFreshActivityAndNothingKeepsDynamicPreference() {
        rule.onNodeWithText("Dynamic").performScrollTo().performClick()
        rule.onNodeWithText("Dark").performScrollTo().performClick()
        rule.onNodeWithText("Custom font").performScrollTo().performClick()
        rule.chooseDemoStyle(DemoStyle.Nothing)
        rule.onNodeWithText("Dynamic").performScrollTo().assertIsNotEnabled().assertIsNotSelected()
        assertEquals(DemoAppearance(DemoStyle.Nothing, dark = true, dynamic = true, customFont = true), saved())
        // Close the old Activity, then launch without its saved instance state.
        rule.activityRule.scenario.close()
        ActivityScenario.launch(MainActivity::class.java).use { fresh ->
            assertStyle(DemoStyle.Nothing)
            rule.onNodeWithText("Dark").performScrollTo().assertIsSelected()
            rule.onNodeWithText("Custom font").performScrollTo().assertIsSelected()
            rule.onNodeWithText("Dynamic").performScrollTo().assertIsNotEnabled().assertIsNotSelected()
            rule.chooseDemoStyle(DemoStyle.Material3)
            rule.onNodeWithText("Dynamic").performScrollTo().assertIsEnabled().assertIsSelected()
            fresh.recreate()
            assertStyle(DemoStyle.Material3)
            rule.onNodeWithText("Custom font").performScrollTo().assertIsSelected()
            rule.chooseDemoStyle(DemoStyle.Material3Expressive)
            rule.onNodeWithText("Dynamic").performScrollTo().assertIsEnabled().assertIsSelected()
        }
    }

    @Test fun unknownStoredStyleFallsBackToExpressiveWithoutResettingOtherSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.getSharedPreferences(DemoAppearancePreferences.FILE_NAME, Context.MODE_PRIVATE)
            .edit().putString("style", "future-style").putBoolean("customFont", true).commit())
        rule.activityRule.scenario.recreate()
        assertStyle(DemoStyle.Material3Expressive)
        rule.onNodeWithText("Custom font").performScrollTo().assertIsSelected()
    }

    private fun assertStyle(expected: DemoStyle) {
        rule.onNodeWithTag("demo-style-picker").performScrollTo().assertTextContains("Style: ${expected.label}").performClick()
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertCountEquals(3)
        for (style in DemoStyle.entries) {
            val node = rule.onNodeWithTag("demo-style-${style.name}")
            if (style == expected) node.assertIsSelected() else node.assertIsNotSelected()
        }
        // Selecting the current radio option must not turn the style off.
        rule.onNodeWithTag("demo-style-${expected.name}").performScrollTo().performClick()
        assertEquals(expected, saved().style)
    }

    private fun saved() = DemoAppearancePreferences(InstrumentationRegistry.getInstrumentation().targetContext).load()
    private fun capture(name: String) {
        rule.waitForIdle()
        val directory = File(rule.activity.getExternalFilesDir(null), "demo-style-selection").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
