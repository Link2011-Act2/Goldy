package jp.linkserver.goldy.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import jp.linkserver.goldy.CheckOutcome
import jp.linkserver.goldy.GoldyState
import jp.linkserver.goldy.UpdateCandidate

/** Host-localizable text. Tags are displayed verbatim; none of these components compare versions. */
@Immutable
data class UpdateNotificationStrings(
    val title: String = "App update",
    val available: String = "An update is available.",
    val openDetails: String = "View update",
    val hideBanner: String = "Hide update banner for now",
    val ignoreVersion: String = "Ignore this version",
    val close: String = "Not now",
    val iconDescription: String = "Update available",
    val check: String = "Check for updates",
    val retry: String = "Retry check",
    val checking: String = "Checking for updates…",
    val idle: String = "Check for a new version.",
    val upToDate: String = "You are up to date.",
    val noReleases: String = "No releases have been published.",
    val noEligible: String = "No release matches your update policy.",
    val unsupported: String = "The installed version cannot be compared safely.",
    val disabled: String = "Automatic checks are off. You can check manually.",
    val failure: String = "Could not check for updates. Saved information is retained.",
    val throttled: String = "Requests are temporarily limited. Try again later.",
    val incomplete: String = "The release history is incomplete.",
    val cached: String = "Saved update information"
)

/** Scheduler-style entry point. Hiding the banner never implies ignoring a release. */
@Composable
fun UpdateNotificationBanner(
    state: GoldyState,
    onOpenDetails: (UpdateCandidate) -> Unit,
    onHideBanner: () -> Unit,
    modifier: Modifier = Modifier,
    strings: UpdateNotificationStrings = UpdateNotificationStrings(),
    icon: @Composable () -> Unit = { Icon(painterResource(R.drawable.elief_update), null) }
) {
    val update = state.notificationUpdate
    AnimatedVisibility(state.showBanner && update != null,
        modifier = modifier, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        if (update != null) {
            val card = LocalUpdateCardStyle.current
            Surface(onClick = { onOpenDetails(update) },
                modifier = Modifier.fillMaxWidth().testTag("goldy-notification-banner"),
                shape = card.shape ?: MaterialTheme.shapes.extraLarge,
                color = card.containerColor.takeUnless { it == Color.Unspecified } ?: MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    icon()
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(strings.available, style = MaterialTheme.typography.titleMedium)
                        Text(update.release.tag, style = MaterialTheme.typography.bodyMedium)
                        Text(strings.openDetails, style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onHideBanner) { Icon(painterResource(R.drawable.elief_close), strings.hideBanner) }
                }
            }
        }
    }
}

/** The badge follows notificationUpdate, independently of showBanner and availableUpdate. */
@Composable
fun UpdateNotificationIcon(
    state: GoldyState,
    onOpenPrompt: (UpdateCandidate) -> Unit,
    modifier: Modifier = Modifier,
    strings: UpdateNotificationStrings = UpdateNotificationStrings(),
    icon: @Composable () -> Unit = { Icon(painterResource(R.drawable.elief_update), null) }
) {
    val update = state.notificationUpdate ?: return
    IconButton(onClick = { onOpenPrompt(update) }, modifier = modifier.testTag("goldy-notification-icon")
        .semantics { contentDescription = strings.iconDescription }) {
        BadgedBox(badge = { Badge(containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary) }) { icon() }
    }
}

/**
 * The host controls visibility, checking, navigation and ignoring. Manual access deliberately uses
 * availableUpdate so an ignored release can still be inspected in UpdateOverviewScreen.
 */
@Composable
fun UpdateCheckDialog(
    state: GoldyState,
    onCheck: () -> Unit,
    onOpenDetails: (UpdateCandidate) -> Unit,
    onIgnoreVersion: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    strings: UpdateNotificationStrings = UpdateNotificationStrings(),
    icon: @Composable () -> Unit = { Icon(painterResource(R.drawable.elief_update), null) }
) {
    val update = state.availableUpdate
    AlertDialog(onDismissRequest = onDismiss, modifier = modifier.testTag("goldy-update-check-dialog"),
        icon = icon, title = { Text(strings.title) },
        text = {
            // One bounded dialog scroll keeps every action reachable at 320dp / fontScale 1.8.
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(notificationStatus(state, strings), Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    color = if (state.lastOutcome is CheckOutcome.Failure) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.checking) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (update != null) Text(update.release.tag, style = MaterialTheme.typography.titleMedium)
                if (state.fromCache && update != null) Text(strings.cached, style = MaterialTheme.typography.labelMedium)
                Button(onClick = onCheck, enabled = !state.checking,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("goldy-notification-check")) {
                    Text(if (state.lastOutcome is CheckOutcome.Failure) strings.retry else strings.check)
                }
                if (update != null) {
                    Button(onClick = { onOpenDetails(update) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("goldy-notification-details")) {
                        Text(strings.openDetails)
                    }
                    OutlinedButton(onClick = { onIgnoreVersion(update.release.tag) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("goldy-notification-ignore")) { Text(strings.ignoreVersion) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(strings.close) } }
    )
}

internal fun notificationStatus(state: GoldyState, strings: UpdateNotificationStrings): String = when {
    state.checking -> strings.checking
    state.lastOutcome is CheckOutcome.Failure -> strings.failure
    state.lastOutcome is CheckOutcome.Throttled -> strings.throttled
    state.lastOutcome is CheckOutcome.Incomplete -> strings.incomplete
    state.availableUpdate != null -> strings.available
    state.lastOutcome == CheckOutcome.UpToDate -> strings.upToDate
    state.lastOutcome == CheckOutcome.NoPublishedReleases -> strings.noReleases
    state.lastOutcome == CheckOutcome.NoEligibleRelease -> strings.noEligible
    state.lastOutcome == CheckOutcome.UnsupportedVersion -> strings.unsupported
    state.lastOutcome == CheckOutcome.Disabled -> strings.disabled
    else -> strings.idle
}
