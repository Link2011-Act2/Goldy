package jp.linkserver.projectgoldy

import android.content.Context
import androidx.core.content.edit

internal enum class DemoStyle(val label: String) {
    Material3Expressive("Material 3 Expressive"),
    Nothing("Nothing風"),
    Material3("通常Material 3")
}

internal data class DemoAppearance(
    val style: DemoStyle = DemoStyle.Material3Expressive,
    val dark: Boolean = false,
    val dynamic: Boolean = false,
    val customFont: Boolean = false
)

/** Demo-only preferences. Never shares the updater's pending-install or cache storage. */
internal class DemoAppearancePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load() = DemoAppearance(
        style = DemoStyle.entries.firstOrNull { it.name == preferences.getString("style", null) }
            ?: DemoStyle.Material3Expressive,
        dark = preferences.getBoolean("dark", false),
        dynamic = preferences.getBoolean("dynamic", false),
        customFont = preferences.getBoolean("customFont", false)
    )

    fun save(value: DemoAppearance) {
        preferences.edit {
            putString("style", value.style.name)
            putBoolean("dark", value.dark)
            putBoolean("dynamic", value.dynamic)
            putBoolean("customFont", value.customFont)
        }
    }

    companion object { const val FILE_NAME = "demo-appearance" }
}
