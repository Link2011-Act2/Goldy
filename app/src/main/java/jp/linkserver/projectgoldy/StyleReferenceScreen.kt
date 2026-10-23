package jp.linkserver.projectgoldy

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import jp.linkserver.pyrite.card.SettingsActionCard
import jp.linkserver.pyrite.card.SettingsInfoCard
import jp.linkserver.pyrite.screen.PyriteBackButton
import jp.linkserver.pyrite.settings.SettingsScreen

/** Real public Pyrite components, so comparison does not depend on a copied palette or card. */
@Composable
internal fun StyleReferenceScreen(onBack: () -> Unit) {
    var presses by rememberSaveable { mutableIntStateOf(0) }
    SettingsScreen(title = "Style reference", navigationIcon = { PyriteBackButton(onBack, "Back") }) {
        SettingsInfoCard("Release notes", "A clearer update experience.", Modifier.testTag("pyrite-reference-card")) {
            Text("Read the changes, review intermediate releases, then download the verified update.")
        }
        SettingsActionCard("Update action", "Reference only. No APK is downloaded.", "Preview action", { presses++ },
            supportingContent = { Text("Preview actions: $presses") })
    }
}
