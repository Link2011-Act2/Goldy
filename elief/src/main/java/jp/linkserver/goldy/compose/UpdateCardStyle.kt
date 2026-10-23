package jp.linkserver.goldy.compose

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/** Optional host surface roles. Unspecified values inherit MaterialTheme; no palette is installed. */
@Immutable
data class UpdateCardStyle(
    val containerColor: Color = Color.Unspecified,
    val shape: Shape? = null
)

/** Bridge for hosts whose card surface differs from the standard surfaceContainerHigh role. */
val LocalUpdateCardStyle = staticCompositionLocalOf { UpdateCardStyle() }
