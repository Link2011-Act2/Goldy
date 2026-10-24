package jp.linkserver.projectgoldy

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import jp.linkserver.goldy.compose.LocalUpdateCardStyle
import jp.linkserver.goldy.compose.UpdateCardStyle
import jp.linkserver.pyrite.theme.PyriteFonts
import jp.linkserver.pyrite.theme.PyriteStyle
import jp.linkserver.pyrite.theme.PyriteTheme

/** The host bridges Pyrite's private card roles without changing Elief's dependencies or palette. */
@Composable
internal fun DemoTheme(
    style: DemoStyle, dark: Boolean, dynamic: Boolean, customFont: Boolean,
    content: @Composable () -> Unit
) {
    val activity = LocalActivity.current
    val view = LocalView.current
    SideEffect {
        activity?.let {
            WindowCompat.getInsetsController(it.window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val currentContent = rememberUpdatedState(content)
    val stableContent = remember { movableContentOf { currentContent.value() } }
    val family = if (customFont) FontFamily.Monospace else null
    val nothing = style == DemoStyle.Nothing
    if (style != DemoStyle.Material3) {
        PyriteTheme(fonts = PyriteFonts(title = family, accent = family, body = family),
            style = if (nothing) PyriteStyle.Nothing else PyriteStyle.Material3Expressive,
            darkTheme = dark, dynamicColor = dynamic && !nothing) {
            val colors = MaterialTheme.colorScheme
            CompositionLocalProvider(LocalUpdateCardStyle provides UpdateCardStyle(
                containerColor = if (!nothing && !dark) colors.surfaceContainerLowest else colors.surfaceContainerHigh,
                shape = RoundedCornerShape(22.dp)
            ), content = stableContent)
        }
    } else {
        val context = LocalContext.current
        val colors = when {
            dynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dark -> darkColorScheme()
            else -> lightColorScheme()
        }
        MaterialTheme(colorScheme = colors, typography = if (family == null) Typography() else monoTypography(), content = stableContent)
    }
}

private fun monoTypography() = Typography().let { base -> base.copy(
    displayLarge = base.displayLarge.copy(fontFamily = FontFamily.Monospace),
    displayMedium = base.displayMedium.copy(fontFamily = FontFamily.Monospace),
    displaySmall = base.displaySmall.copy(fontFamily = FontFamily.Monospace),
    headlineLarge = base.headlineLarge.copy(fontFamily = FontFamily.Monospace),
    headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Monospace),
    headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.Monospace),
    titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Monospace),
    titleMedium = base.titleMedium.copy(fontFamily = FontFamily.Monospace),
    titleSmall = base.titleSmall.copy(fontFamily = FontFamily.Monospace),
    bodyLarge = base.bodyLarge.copy(fontFamily = FontFamily.Monospace),
    bodyMedium = base.bodyMedium.copy(fontFamily = FontFamily.Monospace),
    bodySmall = base.bodySmall.copy(fontFamily = FontFamily.Monospace),
    labelLarge = base.labelLarge.copy(fontFamily = FontFamily.Monospace),
    labelMedium = base.labelMedium.copy(fontFamily = FontFamily.Monospace),
    labelSmall = base.labelSmall.copy(fontFamily = FontFamily.Monospace)
) }
