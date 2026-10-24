package jp.linkserver.goldy

import org.junit.Assert.*
import org.junit.Test
import jp.linkserver.goldy.legacy.shutup.compareReleaseVersions as oldShutUpCompare
import jp.linkserver.goldy.legacy.shutup.detectReleaseChannel as oldShutUpChannel
import jp.linkserver.goldy.legacy.scheduler.legacyCompare as oldSchedulerCompare
import jp.linkserver.goldy.legacy.scheduler.detectReleaseChannel as oldSchedulerChannel
import jp.linkserver.goldy.legacy.glyph.legacyIsNewer as oldGlyphNewer
import jp.linkserver.goldy.legacy.glyph.detectReleaseChannel as oldGlyphChannel

class VersionCharacterizationTest {
    private val versions = listOf(
        "", "nightly", "0", "v1", "1.0", "v1.0.0", "1.0.1", "1.9.9", "1.10.0", "2.0", "10.0",
        "v100", "99", "210", "glyphbarty_210-Release", "scheduler_120-Release", "nittc_1.2.0-Beta",
        "v1.0-IntDev", "1.0-internal", "1.0-IntDev1", "1.0-IntDev10", "1.0-IntDev_rev0",
        "1.0-IntDev_rev2", "1.0-IntDev_rev10", "1.0-alpha.1", "1.0-Beta", "1.0-Beta2", "1.0-Beta10",
        "1.0-beta.2", "1.0-beta.10", "1.0-BETA_rev2", "1.0-RC1", "1.0-RC10", "1.0-Release",
        "1.0-Stable", "V1.0.0-RELEASE", "1.0-Preview", "1.0+build.2", "1.0+build.10",
        "1.0.0+beta", "v99999999999999999999999", "1.2147483648.2", " 1.0-Beta2 "
    )

    @Test fun shutUpOrderingMatchesFrozenHeadForEveryPair() {
        for (left in versions) for (right in versions) {
            assertEquals(left + " / " + right, oldShutUpCompare(left, right),
                VersionPolicies.ShutUp.compare(left, right))
        }
    }

    @Test fun schedulerOrderingMatchesFrozenHeadIncludingPrereleaseFlags() {
        for (left in versions) for (right in versions) for (lp in listOf(false, true)) for (rp in listOf(false, true)) {
            assertEquals(left + " / " + right + " flags " + lp + rp,
                oldSchedulerCompare(left, right, lp, rp), legacySchedulerPolicy.compare(left, right, lp, rp))
        }
    }

    @Test fun glyphOrderingMatchesFrozenHeadForEveryPair() {
        for (left in versions) for (right in versions) {
            assertEquals(left + " / " + right, oldGlyphNewer(left, right),
                (legacyGlyphPolicy.compare(left, right) ?: 0) > 0)
        }
    }

    @Test fun channelsMatchFrozenHeads() {
        for (version in versions) {
            assertEquals(version, oldShutUpChannel(version), VersionPolicies.ShutUp.channel(version).name)
            assertEquals(version, oldSchedulerChannel(version), legacySchedulerPolicy.channel(version).name)
            assertEquals(version, oldGlyphChannel(version), legacyGlyphPolicy.channel(version).name)
        }
    }

    @Test fun betaNumbersAndRevisionsDeliberatelyDifferByApplication() {
        assertTrue(VersionPolicies.ShutUp.compare("1.0-Beta10", "1.0-Beta2")!! > 0)
        assertTrue(legacySchedulerPolicy.compare("1.0-Beta10", "1.0-Beta2")!! > 0)
        assertEquals(0, legacyGlyphPolicy.compare("1.0-Beta10", "1.0-Beta2"))
        assertTrue(VersionPolicies.ShutUp.compare("1.0-IntDev_rev10", "1.0-IntDev_rev2")!! > 0)
        assertEquals(0, legacySchedulerPolicy.compare("1.0-IntDev_rev10", "1.0-IntDev_rev2"))
        assertEquals(0, legacyGlyphPolicy.compare("1.0-IntDev_rev10", "1.0-IntDev_rev2"))
    }

    @Test fun rcRankAndCompactVersionsAreNotSilentlyModernized() {
        assertTrue(VersionPolicies.ShutUp.compare("1.0-RC1", "1.0-Beta1")!! > 0)
        assertTrue(legacySchedulerPolicy.compare("1.0-RC1", "1.0-Beta1")!! < 0)
        assertTrue(VersionPolicies.ShutUp.compare("100", "1.0.0")!! > 0)
        assertEquals(0, legacySchedulerPolicy.compare("100", "1.0.0"))
        assertEquals(0, legacyGlyphPolicy.compare("100", "1.0.0"))
    }

    @Test fun metadataAliasesAndMalformedInputRetainLegacyBehavior() {
        assertEquals(0, VersionPolicies.ShutUp.compare("v1.0-Release", "1.0-Stable"))
        assertEquals(0, VersionPolicies.ShutUp.compare("1.0+build.10", "1.0+build.2"))
        assertNull(VersionPolicies.ShutUp.compare("nightly", "1.0"))
        assertNull(VersionPolicies.ShutUp.compare("v99999999999999999999999", "1"))
        assertEquals(0, legacySchedulerPolicy.compare("nightly", ""))
        assertEquals(ReleaseChannel.Unknown, VersionPolicies.ShutUp.channel("1.0+beta"))
        assertEquals(ReleaseChannel.Beta, legacyGlyphPolicy.channel("1.0+beta"))
        assertEquals(ReleaseChannel.PreRelease, legacySchedulerPolicy.channel("1.0-Release", true))
    }
}
