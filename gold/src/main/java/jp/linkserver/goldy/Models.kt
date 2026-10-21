package jp.linkserver.goldy

import java.time.Instant

/** Metadata only. No downloaded APK or installation authority is represented by this model. */
data class GitHubAsset(
    val id: Long,
    val name: String,
    val downloadUrl: String,
    val size: Long?,
    val digest: String?
)

data class GitHubRelease(
    val id: Long,
    val tag: String,
    val name: String = tag,
    val notes: String = "",
    val url: String,
    val prerelease: Boolean = false,
    val publishedAt: Instant? = null,
    val assets: List<GitHubAsset> = emptyList()
)

enum class ReleaseChannel { IntDev, Alpha, Beta, Rc, PreRelease, Stable, Release, Unknown }

/** Always the actual installed version; developer overrides affect comparison only. */
data class InstalledVersion(val name: String, val code: Long) {
    init { require(name.isNotBlank()); require(code >= 0) }
}

data class UpdateCandidate(
    val release: GitHubRelease,
    val intermediateReleases: List<GitHubRelease> = emptyList(),
    val catalogComplete: Boolean = true
)

data class ReleaseCatalog(val releases: List<GitHubRelease>, val complete: Boolean = true)

enum class CheckTrigger { Automatic, Manual }
enum class SelectionMode { HighestVersion, MostRecentlyPublished, GitHubLatest }

data class DeveloperOptions(
    val ignoreVersionComparison: Boolean = false,
    val ignoreCheckInterval: Boolean = false,
    val currentVersionOverride: String? = null
)

data class RuntimeOptions(
    val automaticChecksEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val developer: DeveloperOptions = DeveloperOptions()
)

sealed interface CheckOutcome {
    data class Available(val update: UpdateCandidate, val fromCache: Boolean = false) : CheckOutcome
    data object UpToDate : CheckOutcome
    data object NoPublishedReleases : CheckOutcome
    data object NoEligibleRelease : CheckOutcome
    data object UnsupportedVersion : CheckOutcome
    data object Disabled : CheckOutcome
    data class Throttled(val retryAtMillis: Long) : CheckOutcome
    data class Incomplete(val update: UpdateCandidate?) : CheckOutcome
    data class Failure(val error: GoldyError) : CheckOutcome
}

sealed interface GoldyError {
    data object Network : GoldyError
    data object InvalidResponse : GoldyError
    data object InvalidUrl : GoldyError
    data object Storage : GoldyError
    data class Http(val status: Int, val retryAtMillis: Long? = null) : GoldyError
}

data class GoldyState(
    val availableUpdate: UpdateCandidate? = null,
    val notificationUpdate: UpdateCandidate? = null,
    val showBanner: Boolean = false,
    val checking: Boolean = false,
    val lastSuccessMillis: Long? = null,
    val fromCache: Boolean = false,
    val lastOutcome: CheckOutcome? = null
)
