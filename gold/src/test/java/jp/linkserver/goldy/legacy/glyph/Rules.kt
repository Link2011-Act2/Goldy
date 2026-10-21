// Derived from the MIT-licensed app implementations; see META-INF/goldy/LICENSE and NOTICE.
package jp.linkserver.goldy.legacy.glyph
import java.util.Locale
fun legacyIsNewer(left:String,right:String) = isNewerRelease(left,right)
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
