// Derived from the MIT-licensed app implementations; see META-INF/goldy/LICENSE and NOTICE.
package jp.linkserver.goldy.legacy.scheduler
import java.util.Locale
fun legacyCompare(left:String,right:String,leftPrerelease:Boolean=false,rightPrerelease:Boolean=false) = compareReleaseVersions(left,leftPrerelease,right,rightPrerelease)
private fun compareReleaseVersions(
    leftTag: String,
    leftIsPrerelease: Boolean,
    rightTag: String,
    rightIsPrerelease: Boolean
): Int {
    val left = parseVersionNumbers(leftTag)
    val right = parseVersionNumbers(rightTag)
    val maxSize = maxOf(left.size, right.size)
    for (index in 0 until maxSize) {
        val leftPart = left.getOrElse(index) { 0 }
        val rightPart = right.getOrElse(index) { 0 }
        if (leftPart != rightPart) return leftPart.compareTo(rightPart)
    }
    val leftPriority = channelPriority(detectChannel(leftTag, leftIsPrerelease))
    val rightPriority = channelPriority(detectChannel(rightTag, rightIsPrerelease))
    if (leftPriority != rightPriority) return leftPriority.compareTo(rightPriority)

    val leftChannelBuild = parseSimpleChannelBuild(leftTag)
    val rightChannelBuild = parseSimpleChannelBuild(rightTag)
    if (leftChannelBuild != null &&
        rightChannelBuild != null &&
        leftChannelBuild.channel == rightChannelBuild.channel
    ) {
        return leftChannelBuild.number.compareTo(rightChannelBuild.number)
    }
    return 0
}

private fun parseVersionNumbers(value: String): List<Int> {
    val normalizedLower = value.lowercase(Locale.US)
    val normalized = normalizedLower
        .substringAfter("scheduler_", normalizedLower)
        .substringAfter("nittc_", normalizedLower)
        .removePrefix("v")
        .substringBefore("-")
    val dotted = Regex("""\d+(?:\.\d+)+""").find(normalized)?.value
    if (dotted != null) {
        return dotted.split('.').mapNotNull { it.toIntOrNull() }
    }
    val compact = Regex("""\d{3,}""").find(normalized)?.value
    if (compact != null) {
        return compact.map { it.digitToInt() }
    }
    return Regex("""\d+""").findAll(normalized).mapNotNull { it.value.toIntOrNull() }.toList()
}

private data class ChannelBuild(val channel: String, val number: Long)

private fun parseSimpleChannelBuild(value: String): ChannelBuild? {
    val match = Regex(
        """(?:^|[-_.])(intdev|internal|beta|alpha|rc|stable|release)(\d*)$""",
        RegexOption.IGNORE_CASE
    ).find(value.trim()) ?: return null
    val channel = when (match.groupValues[1].lowercase(Locale.US)) {
        "internal" -> "intdev"
        else -> match.groupValues[1].lowercase(Locale.US)
    }
    val numberText = match.groupValues[2]
    val number = if (numberText.isEmpty()) 0L else numberText.toLongOrNull() ?: return null
    return ChannelBuild(channel = channel, number = number)
}

private fun detectChannel(value: String, isPrerelease: Boolean = false): String {
    val lower = value.lowercase(Locale.US)
    return when {
        "intdev" in lower || "internal" in lower -> "IntDev"
        "beta" in lower -> "Beta"
        "alpha" in lower || "rc" in lower || isPrerelease -> "PreRelease"
        "stable" in lower || "release" in lower -> "Release"
        else -> "Unknown"
    }
}

fun detectReleaseChannel(value: String): String = detectChannel(value)

fun isIntDevBuild(currentVersion: String): Boolean {
    return detectReleaseChannel(currentVersion).equals("IntDev", ignoreCase = true)
}

private fun channelPriority(channel: String): Int {
    return when (channel.lowercase(Locale.US)) {
        "intdev" -> 0
        "prerelease" -> 1
        "beta" -> 2
        "release", "stable" -> 3
        else -> -1
    }
}

data class AppReleaseNoteInfo(
    val tagName: String,
    val channel: String,
    val releaseNotes: String,
    val releaseUrl: String,
    val isPrerelease: Boolean
)

data class AppUpdateInfo(
    val tagName: String,
    val channel: String,
    val releaseNotes: String,
    val releaseUrl: String,
    val apkAssetName: String?,
    val apkDownloadUrl: String?,
    val isPrerelease: Boolean,
    val intermediateReleaseNotes: List<AppReleaseNoteInfo> = emptyList()
)


fun legacySelect(releaseInfos:List<AppUpdateInfo>,currentVersion:String,showLatestForTesting:Boolean=false):AppUpdateInfo? {
        val candidates = releaseInfos
            .filter { showLatestForTesting || isNewerRelease(it.tagName, currentVersion, it.isPrerelease) }
        val latest = candidates.maxWithOrNull(::compareUpdateInfo) ?: return null
        val intermediateReleaseNotes = releaseInfos
            .asSequence()
            .filter { !it.tagName.equals(latest.tagName, ignoreCase = true) }
            .filter { isNewerRelease(it.tagName, currentVersion, it.isPrerelease) }
            .filter { compareUpdateInfo(it, latest) < 0 }
            .sortedWith { left, right -> compareUpdateInfo(right, left) }
            .map { it.toReleaseNoteInfo() }
            .toList()
        return latest.copy(intermediateReleaseNotes = intermediateReleaseNotes)
}
private fun AppUpdateInfo.toReleaseNoteInfo(): AppReleaseNoteInfo {
    return AppReleaseNoteInfo(
        tagName = tagName,
        channel = channel,
        releaseNotes = releaseNotes,
        releaseUrl = releaseUrl,
        isPrerelease = isPrerelease
    )
}

private fun compareUpdateInfo(left: AppUpdateInfo, right: AppUpdateInfo): Int {
    return compareReleaseVersions(left.tagName, left.isPrerelease, right.tagName, right.isPrerelease)
}

internal fun isNewerRelease(remoteTag: String, currentVersion: String, remoteIsPrerelease: Boolean): Boolean {
    return compareReleaseVersions(remoteTag, remoteIsPrerelease, currentVersion, false) > 0
}

