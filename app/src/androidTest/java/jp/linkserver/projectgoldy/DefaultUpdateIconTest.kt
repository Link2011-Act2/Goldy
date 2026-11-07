package jp.linkserver.projectgoldy

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import jp.linkserver.goldy.*
import jp.linkserver.goldy.compose.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** The real Demo Activity renders all default slots; no host icon/tint overrides. */
class DefaultUpdateIconTest {
    @get:Rule(order = 0) val appearance = IsolatedDemoAppearance()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    @Test fun defaultIconsUseMaterialTintInLightAndDark() {
        var dark by mutableStateOf(false)
        var prompt by mutableStateOf(false)
        lateinit var colors: ColorScheme
        var buttonTint = Color.Unspecified
        var dialogTint = Color.Unspecified
        var density = 1f
        val candidate = UpdateCandidate(GitHubRelease(1, "v2.0.0-Release",
            url = "https://github.com/Link2011-Act2/Goldy/releases/tag/v2.0.0-Release"))
        rule.activity.setContent {
            val scheme = if (dark) darkColorScheme() else lightColorScheme()
            val scale = LocalDensity.current.density
            SideEffect { colors = scheme; density = scale }
            MaterialTheme(colorScheme = scheme) {
                val alertIconColor = AlertDialogDefaults.iconContentColor
                SideEffect { dialogTint = alertIconColor }
                Surface {
                    val buttonColor = IconButtonDefaults.iconButtonColors().contentColor
                    SideEffect { buttonTint = buttonColor }
                    Column {
                        UpdateNotificationBanner(GoldyState(candidate, candidate, true), {}, {})
                        UpdateNotificationIcon(GoldyState(candidate, candidate), { prompt = true })
                    }
                }
                if (prompt) UpdateCheckDialog(GoldyState(candidate, candidate), {}, {}, {}, { prompt = false })
            }
        }
        fun pixels(dp: Int) = (dp * density).toInt()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "default-update-icon").apply { mkdirs() }
        fun image(tag: String, name: String): Bitmap {
            rule.mainClock.advanceTimeBy(600)
            rule.waitForIdle()
            val bitmap = rule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
            File(directory, name+".png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return bitmap
        }
        fun assertTint(bitmap: Bitmap, color: Int, left: Int, top: Int, width: Int, height: Int) {
            var count = 0
            for (y in top until minOf(top+height, bitmap.height)) for (x in left until minOf(left+width, bitmap.width))
                if (bitmap.getPixel(x,y) == color) count++
            assertTrue("Expected Material tint ${color.toUInt().toString(16)} in icon region, pixels=$count", count > 20)
            bitmap.recycle()
        }
        for (isDark in listOf(false, true)) {
            rule.runOnIdle { dark = isDark }
            val prefix = if (isDark) "dark" else "light"
            val banner = image("goldy-notification-banner", "$prefix-banner")
            assertTint(banner, colors.onSurface.toArgb(), pixels(16), banner.height/2-pixels(12), pixels(24), pixels(24))
            val icon = image("goldy-notification-icon", "$prefix-icon")
            assertTint(icon, buttonTint.toArgb(), 0, 0, icon.width, icon.height)
            rule.onNodeWithTag("goldy-notification-icon").performClick()
            val dialog = image("goldy-update-check-dialog", "$prefix-dialog")
            assertTint(dialog, dialogTint.toArgb(), dialog.width/2-pixels(12), pixels(24), pixels(24), pixels(24))
            rule.onNodeWithText("Not now").performClick()
        }
    }
}
