package jp.linkserver.goldy.compose

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import jp.linkserver.goldy.GitHubRelease
import jp.linkserver.goldy.VersionPolicies
import jp.linkserver.goldy.ApkProgress

/** Retained transfer display only; never resumes or changes a download. */
data class PausedDownload(
    val progress: ApkProgress? = null,
    val message: String = "Download paused",
    val resumeLabel: String = "Resume download"
)

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
) {
    private var paused: PausedDownload? = null
    val pausedDownload: PausedDownload? get() = paused

    /** Additive overload; the original constructor/default JVM signatures stay intact. */
    constructor(
        pausedDownload: PausedDownload?,
        versionLabel: (GitHubRelease) -> String = { it.tag },
        historyChannelLabel: (GitHubRelease) -> String = {
            VersionPolicies.Common.channel(it.tag, it.prerelease).name
        },
        backIcon: @Composable () -> Unit = {
            Icon(painterResource(R.drawable.elief_arrow_back), contentDescription = null)
        }
    ) : this(versionLabel, historyChannelLabel, backIcon) {
        paused = pausedDownload
    }
}
