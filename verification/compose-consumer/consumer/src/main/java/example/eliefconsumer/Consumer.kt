package example.eliefconsumer

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import jp.linkserver.goldy.*
import jp.linkserver.goldy.compose.*

/** Compiles exclusively against published AARs, with stable Material3. */
@Composable
fun Consumer(state: GoldyState, transfer: UpdateTransferState, back: () -> Unit,
             openUrl: (String) -> Unit, action: (UpdateAction) -> Unit, modifier: Modifier = Modifier) {
    MaterialTheme {
        UpdateOverviewScreen(state.toUpdateUiState(VersionPolicies.NittcScheduler,
            ApkSelectionPolicies.NittcScheduler, transfer), back, openUrl, action, modifier,
            strings = UpdateStrings(title = "更新内容", back = "戻る"),
            loadingIndicator = { androidx.compose.material3.CircularProgressIndicator() })
    }
}

/** Additive overload; raw tags and host-specific history labels remain display choices. */
@Composable
fun CustomizedConsumer(state: UpdateUiState, back: () -> Unit) {
    MaterialTheme {
        CompositionLocalProvider(LocalUpdateCardStyle provides UpdateCardStyle(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = MaterialTheme.shapes.large
        )) {
        UpdateOverviewScreen(state, back, {}, {},
            strings = UpdateStrings(title = "更新内容", back = "戻る"),
            presentation = UpdatePresentation(versionLabel = { it.tag.removePrefix("v") }, historyChannelLabel = { "リリース" }))
        }
    }
}

/** All notification operations stay with the host, including navigation and persistence. */
@Composable
fun Notifications(state: GoldyState, check: () -> Unit, details: (UpdateCandidate) -> Unit,
                  hide: () -> Unit, ignore: (String) -> Unit, dismiss: () -> Unit) {
    UpdateNotificationBanner(state, details, hide)
    UpdateNotificationIcon(state, details)
    UpdateCheckDialog(state, check, details, ignore, dismiss,
        strings = UpdateNotificationStrings(title = "更新確認"))
}
