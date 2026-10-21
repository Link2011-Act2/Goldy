// Frozen from ShutUp HEAD 1a18497; only package/import and the gate helper changed. MIT license retained.
package jp.linkserver.goldy.legacy.shutup

internal data class ReleaseNote(val tag: String, val notes: String)
internal data class GitHubRelease(
    val tag: String, val notes: String, val url: String,
    val prerelease: Boolean = false, val apkName: String? = null,
    val apkUrl: String? = null, val apkSha256: String? = null,
    val intermediateNotes: List<ReleaseNote> = emptyList(),
    val publishedAt: Long = 0
)

private fun isIntDevUpdateBuild(installed:String) = detectReleaseChannel(installed) == "IntDev"

internal fun acceptsRelease(release: GitHubRelease, installed: String): Boolean {
    val suffix = release.tag.substringAfter('-', "").substringBefore('+').lowercase(java.util.Locale.ROOT)
    val isInternal = suffix.startsWith("intdev") || suffix.startsWith("internal")
    val isBeta = suffix.startsWith("beta")
    val stableSuffix = suffix.isEmpty() || suffix == "stable" || suffix == "release"
    return when (detectReleaseChannel(installed)) {
        "IntDev" -> true
        "Beta" -> !isInternal && (isBeta || (!release.prerelease && stableSuffix))
        else -> !release.prerelease && stableSuffix
    }
}

internal fun selectReleaseUpdate(releases: List<GitHubRelease>, installed: String,
    ignoreVersionComparison: Boolean = false): GitHubRelease? {
    if (ignoreVersionComparison && isIntDevUpdateBuild(installed)) {
        // Test the most recently published release, including same/older or non-version tags.
        return releases.filter { acceptsRelease(it, installed) }.maxByOrNull { it.publishedAt }
            ?.copy(intermediateNotes = emptyList())
    }
    require(compareReleaseVersions(installed, installed) != null) { "Unrecognized installed version" }
    val candidates = releases.filter {
        acceptsRelease(it, installed) && (compareReleaseVersions(it.tag, installed) ?: -1) > 0
    }.sortedWith { a, b -> compareReleaseVersions(b.tag, a.tag) ?: 0 }
    val latest = candidates.firstOrNull() ?: return null
    return latest.copy(intermediateNotes = candidates.drop(1).map { ReleaseNote(it.tag, it.notes) })
}

