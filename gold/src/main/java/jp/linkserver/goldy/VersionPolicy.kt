package jp.linkserver.goldy

import jp.linkserver.goldy.internal.GlyphRules
import jp.linkserver.goldy.internal.SchedulerRules
import jp.linkserver.goldy.internal.compareShutUpVersions
import java.util.Locale

/**
 * Release ordering only. Equality here MUST NOT be used to authenticate an APK versionName.
 * Legacy policies intentionally preserve permissive parsing and ignored revision behavior.
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

    val Standard: VersionPolicy = ShutUp

    val NittcScheduler: VersionPolicy = object : VersionPolicy {
        override fun compare(left: String, right: String, leftPrerelease: Boolean, rightPrerelease: Boolean): Int =
            SchedulerRules.compare(left, right, leftPrerelease, rightPrerelease)
        override fun channel(version: String, prerelease: Boolean): ReleaseChannel =
            ReleaseChannel.valueOf(SchedulerRules.channel(version, prerelease))
    }

    val GlyphBarty: VersionPolicy = object : VersionPolicy {
        override fun compare(left: String, right: String, leftPrerelease: Boolean, rightPrerelease: Boolean): Int =
            GlyphRules.compare(left, right)
        override fun channel(version: String, prerelease: Boolean): ReleaseChannel =
            ReleaseChannel.valueOf(GlyphRules.channel(version))
    }
}
