package jp.linkserver.goldy

import jp.linkserver.goldy.internal.compareShutUpVersions
import jp.linkserver.goldy.internal.commonReleaseChannel
import java.util.Locale

/**
 * Release ordering only. Equality here MUST NOT be used to authenticate an APK versionName.
 * Common ordering preserves ShutUp's natural suffix ordering, including explicit rev tokens.
 */
interface VersionPolicy {
    fun compare(left: String, right: String, leftPrerelease: Boolean = false, rightPrerelease: Boolean = false): Int?
    fun channel(version: String, prerelease: Boolean = false): ReleaseChannel
}

object VersionPolicies {
    val ShutUp: VersionPolicy = object : VersionPolicy {
        override fun compare(left: String, right: String, leftPrerelease: Boolean, rightPrerelease: Boolean): Int? =
            compareShutUpVersions(left, right)

        override fun channel(version: String, prerelease: Boolean): ReleaseChannel {
            val suffix = version.substringAfter('-', "").lowercase(Locale.US)
            return when {
                "intdev" in suffix || "internal" in suffix -> ReleaseChannel.IntDev
                "beta" in suffix -> ReleaseChannel.Beta
                "stable" in suffix -> ReleaseChannel.Stable
                "release" in suffix -> ReleaseChannel.Release
                else -> ReleaseChannel.Unknown
            }
        }
    }

    /** Shared by all four presets. GitHub prerelease flags never override tag ordering. */
    val Common: VersionPolicy = object : VersionPolicy {
        override fun compare(left: String, right: String, leftPrerelease: Boolean, rightPrerelease: Boolean): Int? =
            compareShutUpVersions(left, right)
        override fun channel(version: String, prerelease: Boolean): ReleaseChannel =
            commonReleaseChannel(version)
    }
    val Standard: VersionPolicy = Common
    val NittcScheduler: VersionPolicy = Common
    val GlyphBarty: VersionPolicy = Common
    val BeastLocator: VersionPolicy = Common
}
