package jp.linkserver.goldy

/** Selection never grants trust: the selected member is still fully verified. */
fun interface ApkSelectionPolicy {
    fun select(release: GitHubRelease): GitHubAsset?
}

object ApkSelectionPolicies {
    /** Ambiguous APK sets require an explicit host policy (for example ABI selection). */
    val Single = ApkSelectionPolicy { release -> release.assets.filter { it.name.endsWith(".apk", true) }.singleOrNull() }
    private fun prefixed(prefix: String) = ApkSelectionPolicy { release ->
        val apks = release.assets.filter { it.name.endsWith(".apk", true) }
        apks.filter { it.name.startsWith(prefix, true) }.singleOrNull()
            ?: apks.singleOrNull()
    }
    val ShutUp = prefixed("shutup_")
    val NittcScheduler = ApkSelectionPolicy { release ->
        if (release.tag.startsWith("ai-runtime-", true)) null else prefixed("nittcsc_").select(release)
    }
    val GlyphBarty = prefixed("glyphbarty_")
}

/** Identity, independent of the ordering-only VersionPolicy. Unknown mappings must return false. */
fun interface ApkVersionBinding {
    fun matches(release: GitHubRelease, apk: ApkMetadata): Boolean
}

object ApkVersionBindings {
    val Exact = ApkVersionBinding { release, apk -> release.tag == apk.versionName }
    /** Observed public APK syntax only; unsupported suffixes and aliases are rejected. */
    private fun published(channels: String) = ApkVersionBinding { release, apk ->
        release.tag.matches(Regex("v[0-9]+\\.[0-9]+\\.[0-9]+-(" + channels + ")")) &&
            release.tag.removePrefix("v") == apk.versionName
    }
    val ShutUp = published("Release")
    val NittcScheduler = published("Release|Beta|RC[0-9]+")
    val GlyphBarty = published("Release")
}

/** SHA-256 of each current signing certificate. History intersection is deliberately insufficient. */
data class ApkSigners(val currentSha256: Set<String>, val multiple: Boolean)
data class ApkMetadata(val packageName: String, val versionName: String, val versionCode: Long, val signers: ApkSigners)

/** Implementations must authenticate the archive's signatures, not merely extract certificate bytes. */
interface ApkInspector {
    fun installed(): ApkMetadata
    fun inspect(file: java.io.File): ApkMetadata?
}

enum class ApkFailure {
    NoAsset, InvalidUrl, RedirectLimit, Http, Network, TooLarge, SizeMismatch, DigestMismatch,
    InvalidDigest, InvalidApk, PackageMismatch, SignerMismatch, VersionCodeTooLow, VersionMismatch, Storage
}
class ApkException(val reason: ApkFailure, val status: Int? = null, cause: Throwable? = null) :
    Exception(reason.name + (status?.let { " (HTTP $it)" } ?: ""), cause)

/** Fail-closed checks shared by Android production code and JVM tests. */
object ApkVerifier {
    fun verify(installed: ApkMetadata, archive: ApkMetadata?, release: GitHubRelease,
               binding: ApkVersionBinding = ApkVersionBindings.Exact): ApkMetadata {
        val apk = archive ?: throw ApkException(ApkFailure.InvalidApk)
        if (apk.packageName != installed.packageName || apk.packageName.isBlank()) throw ApkException(ApkFailure.PackageMismatch)
        fun valid(s: ApkSigners): Boolean = s.currentSha256.isNotEmpty() &&
            s.currentSha256.all { it.matches(Regex("[0-9a-f]{64}")) } &&
            s.multiple == (s.currentSha256.size > 1)
        if (!valid(apk.signers) || !valid(installed.signers) || apk.signers != installed.signers)
            throw ApkException(ApkFailure.SignerMismatch)
        if (installed.versionCode < 0 || apk.versionCode < installed.versionCode) throw ApkException(ApkFailure.VersionCodeTooLow)
        if (apk.versionName.isBlank() || !binding.matches(release, apk)) throw ApkException(ApkFailure.VersionMismatch)
        return apk
    }
}
