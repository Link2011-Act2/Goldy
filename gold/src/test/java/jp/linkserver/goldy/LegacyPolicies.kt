package jp.linkserver.goldy

import jp.linkserver.goldy.internal.GlyphRules
import jp.linkserver.goldy.internal.SchedulerRules

// Test-only adapters retain the old characterization suites without shipping old comparators.
internal val legacySchedulerPolicy = object : VersionPolicy {
    override fun compare(left: String, right: String, leftPrerelease: Boolean, rightPrerelease: Boolean) =
        SchedulerRules.compare(left, right, leftPrerelease, rightPrerelease)
    override fun channel(version: String, prerelease: Boolean) = ReleaseChannel.valueOf(SchedulerRules.channel(version, prerelease))
}
internal val legacyGlyphPolicy = object : VersionPolicy {
    override fun compare(left: String, right: String, leftPrerelease: Boolean, rightPrerelease: Boolean) = GlyphRules.compare(left, right)
    override fun channel(version: String, prerelease: Boolean) = ReleaseChannel.valueOf(GlyphRules.channel(version))
}
