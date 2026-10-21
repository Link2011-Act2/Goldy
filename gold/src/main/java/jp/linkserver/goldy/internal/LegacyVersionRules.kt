// Derived from the MIT-licensed app implementations; see META-INF/goldy/LICENSE and NOTICE.
package jp.linkserver.goldy.internal

import java.util.Locale

internal object SchedulerRules {
fun compare(left:String,right:String,leftPrerelease:Boolean,rightPrerelease:Boolean):Int = compareReleaseVersions(left,leftPrerelease,right,rightPrerelease)
fun channel(value:String,prerelease:Boolean):String = detectChannel(value,prerelease)
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

}

internal object GlyphRules {
fun compare(left:String,right:String):Int = when { isNewerRelease(left,right) -> 1; isNewerRelease(right,left) -> -1; else -> 0 }
fun channel(value:String):String = detectChannel(value)
private fun isNewerRelease(remoteTag: String, currentVersion: String): Boolean {
    val remote = parseVersionNumbers(remoteTag)
    val current = parseVersionNumbers(currentVersion)
    val maxSize = maxOf(remote.size, current.size)
    if (maxSize == 0) return false
    for (index in 0 until maxSize) {
        val remotePart = remote.getOrElse(index) { 0 }
        val currentPart = current.getOrElse(index) { 0 }
        if (remotePart != currentPart) return remotePart > currentPart
    }
    return channelPriority(detectChannel(remoteTag)) > channelPriority(detectChannel(currentVersion))
}

private fun parseVersionNumbers(value: String): List<Int> {
    val normalized = value
        .lowercase(Locale.US)
        .substringAfter("glyphbarty_", value.lowercase(Locale.US))
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

private fun detectChannel(value: String): String {
    val lower = value.lowercase(Locale.US)
    return when {
        "intdev" in lower || "internal" in lower -> "IntDev"
        "beta" in lower -> "Beta"
        "stable" in lower -> "Stable"
        "release" in lower -> "Release"
        else -> "Unknown"
    }
}

fun detectReleaseChannel(value: String): String = detectChannel(value)

private fun channelPriority(channel: String): Int {
    return when (channel.lowercase(Locale.US)) {
        "intdev" -> 0
        "beta" -> 1
        "release", "stable" -> 2
        else -> -1
    }
}

}
