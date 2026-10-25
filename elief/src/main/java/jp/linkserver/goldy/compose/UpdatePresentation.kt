package jp.linkserver.goldy.compose

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import jp.linkserver.goldy.GitHubRelease
import jp.linkserver.goldy.VersionPolicies

/** Display-only customization. Raw tags, comparison and APK verification are never changed. */
class UpdatePresentation(
    val versionLabel: (GitHubRelease) -> String = { it.tag },
    val historyChannelLabel: (GitHubRelease) -> String = {
        VersionPolicies.Common.channel(it.tag, it.prerelease).name
    },
    /** Decorative icon; the surrounding IconButton supplies UpdateStrings.back as its description. */
    val backIcon: @Composable () -> Unit = {
        Icon(painterResource(R.drawable.elief_arrow_back), contentDescription = null)
    }
)
