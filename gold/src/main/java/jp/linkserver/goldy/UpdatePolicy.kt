package jp.linkserver.goldy

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

enum class ChannelPolicy { InstalledTrack, StableOnly, AllPublished }

sealed interface ReleaseFeed {
    data class ListReleases(val perPage: Int = 100, val maxPages: Int = 5) : ReleaseFeed {
        init { require(perPage in 1..100); require(maxPages in 1..100) }
    }
    data object Latest : ReleaseFeed
}

/** A small, immutable policy. Change id when changing custom filtering/comparison behavior. */
data class UpdatePolicy(
    val id: String,
    val versions: VersionPolicy = VersionPolicies.Standard,
    val channels: ChannelPolicy = ChannelPolicy.InstalledTrack,
    val feed: ReleaseFeed = ReleaseFeed.ListReleases(),
    val releaseFilter: (GitHubRelease) -> Boolean = { true },
    val allowNameAsTag: Boolean = false,
    val intermediateNotes: Boolean = true,
    val intermediateNotesIncludeEquivalentVersions: Boolean = false,
    val developerSelection: SelectionMode = SelectionMode.MostRecentlyPublished,
    val previewBypassesInterval: Boolean = false,
    val previewBypassesDismissal: Boolean = false,
    val publicationTieBreak: Boolean = true
) {
    init { require(id.isNotBlank()) }
}

object UpdatePolicies {
    val Standard: UpdatePolicy = UpdatePolicy("standard-shutup-v2")

    fun shutUp(): UpdatePolicy = Standard.copy(
        id = "shutup-common-v2", versions = VersionPolicies.Common,
        feed = ReleaseFeed.ListReleases(100, 1), publicationTieBreak = false,
        intermediateNotesIncludeEquivalentVersions = true
    )

    fun nittcScheduler(): UpdatePolicy = UpdatePolicy(
        id = "nittc-scheduler-common-v2", versions = VersionPolicies.Common,
        channels = ChannelPolicy.AllPublished, feed = ReleaseFeed.ListReleases(30, 1),
        releaseFilter = { !it.tag.startsWith("ai-runtime-", true) },
        allowNameAsTag = true, developerSelection = SelectionMode.HighestVersion,
        previewBypassesInterval = true, publicationTieBreak = false
    )

    fun glyphBarty(): UpdatePolicy = UpdatePolicy(
        id = "glyph-barty-common-v2", versions = VersionPolicies.Common,
        channels = ChannelPolicy.AllPublished, feed = ReleaseFeed.Latest,
        allowNameAsTag = true, intermediateNotes = false,
        developerSelection = SelectionMode.GitHubLatest,
        previewBypassesDismissal = true, publicationTieBreak = false
    )

    /** Beast's feed/selection rules, with common ShutUp ordering; no Scheduler runtime exclusion. */
    fun beastLocator(): UpdatePolicy = UpdatePolicy(
        id = "beastlocator-common-v1", versions = VersionPolicies.Common,
        channels = ChannelPolicy.AllPublished, feed = ReleaseFeed.ListReleases(30, 1),
        allowNameAsTag = true, developerSelection = SelectionMode.HighestVersion,
        previewBypassesInterval = true, publicationTieBreak = false
    )
}

data class GoldyConfig(
    val repository: GitHubRepository,
    val policy: UpdatePolicy = UpdatePolicies.Standard,
    val checkInterval: Duration = 8.hours,
    val initialOptions: RuntimeOptions = RuntimeOptions(),
    val userAgent: String = "Goldy/0.1.0",
    val maxResponseBytes: Int = 2 * 1024 * 1024,
    val maxCatalogBytes: Int = 8 * 1024 * 1024
) {
    init {
        require(checkInterval.isFinite() && checkInterval.isPositive())
        require(userAgent.isNotBlank() && '\r' !in userAgent && '\n' !in userAgent)
        require(maxResponseBytes in 1..16 * 1024 * 1024)
        require(maxCatalogBytes >= maxResponseBytes && maxCatalogBytes <= 64 * 1024 * 1024)
    }
}

internal fun UpdatePolicy.effectiveDeveloper(installed: InstalledVersion, options: RuntimeOptions): DeveloperOptions =
    if (versions.channel(installed.name) == ReleaseChannel.IntDev) options.developer else DeveloperOptions()
