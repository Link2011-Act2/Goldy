package jp.linkserver.goldy

import jp.linkserver.goldy.legacy.shutup.compareReleaseVersions as frozenShutUp
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CommonVersionPolicyTest {
    private val common = VersionPolicies.Common
    private val actualVersions = JSONObject(javaClass.getResource("/four-app-versions.json")!!.readText())
        .getJSONObject("versions").let { apps -> apps.keys().asSequence().flatMap { key ->
            val values = apps.getJSONArray(key)
            (0 until values.length()).asSequence().map(values::getString)
        }.distinct().toList() }
    private val edgeVersions = listOf("", "nightly", "1..0", "1.0-", "1.0/Release", "1.0-Preview", "1.0-Internal",
        "1.0-IntDev", "1.0-IntDev2", "1.0-IntDev_rev0", "1.0-IntDev_rev2", "1.0-IntDev_rev10",
        "1.0-Beta2_rev3", "1.0-Beta2_rev10", "1.0-Beta-rev3", "1.0-RC1", "1.0-Stable", "1.0+build.2",
        "9223372036854775808.0", "1.0-IntDev_rev2147483648")

    @Test fun everyRealAndEdgePairRetainsExactShutUpResult() {
        for (left in actualVersions + edgeVersions) for (right in actualVersions + edgeVersions) {
            assertEquals("$left / $right", frozenShutUp(left, right), common.compare(left, right))
            assertEquals(frozenShutUp(left, right), common.compare(left, right, true, true))
        }
    }

    @Test fun requiredNumberAndChannelPromotionsAreOrdered() {
        val pairs = listOf(
            "1.0.0-IntDev1" to "1.0.0-IntDev2", "1.0.0-Beta2" to "1.0.0-Beta10",
            "1.0.0-RC1" to "1.0.0-RC2", "1.0.0-IntDev_rev2" to "1.0.0-IntDev_rev10",
            "1.0.0-Beta_rev1" to "1.0.0-Beta_rev2", "1.0.0-Beta10" to "1.0.0-RC1",
            "1.0.0-RC2" to "1.0.0-Release", "2.1.0-Release" to "2.1.1-IntDev_rev0",
            "1.0.0-Beta-rev3" to "1.0.0-Beta-rev10")
        for ((old, new) in pairs) {
            assertTrue("$old < $new", common.compare(old, new)!! < 0)
            assertTrue(common.compare(new, old)!! > 0)
        }
    }

    @Test fun ambiguousSpellingsKeepShutUpMeaningRatherThanBeastAliases() {
        assertTrue(common.compare("1.0-IntDev2", "1.0-IntDev_rev2")!! < 0)
        assertTrue(common.compare("1.0-IntDev", "1.0-IntDev_rev0")!! < 0)
        assertTrue(common.compare("1.0-Beta2_rev3", "1.0-Beta2_rev10")!! < 0)
        assertTrue(common.compare("1.0-IntDev_rev999", "1.0-Beta_rev0")!! < 0)
        assertTrue(common.compare("1.0-Beta_rev999", "1.0-RC1")!! < 0)
        assertEquals(0, common.compare("1.0-Beta_rev3", "1.0-Beta-rev3"))
    }

    @Test fun equivalenceDoesNotUsePublicationDatesOrPrereleaseFlags() {
        assertEquals(0, common.compare("v1.0.0-Release", "1.0-Stable", true, false))
        assertEquals(0, common.compare("1.0+build.2", "1.0.0+build.10"))
        assertEquals(0, common.compare(" V1.0-BETA02 ", "1.0-beta2"))
    }

    @Test fun symmetryAndReflexivityHoldAcrossRealTags() {
        val values = (actualVersions + edgeVersions).filter { common.compare(it, it) != null }
        for (a in values) {
            assertEquals(0, common.compare(a, a))
            for (b in values) assertEquals(sign(common.compare(a, b)!!), -sign(common.compare(b, a)!!))
        }
    }

    @Test fun transitivityHoldsIncludingEqualVersionsAndMixedSuffixes() {
        val values = listOf("0", "v1.0", "1.0.0", "1.0-Internal", "1.0-IntDev", "1.0-IntDev2",
            "1.0-IntDev_rev0", "1.0-IntDev_rev10", "1.0-Alpha1", "1.0-Preview", "1.0-Beta2",
            "1.0-Beta10", "1.0-RC2", "1.0-Stable", "1.0-Release", "2.1.1-IntDev_rev0")
        for (a in values) for (b in values) for (c in values) {
            if (common.compare(a, b)!! <= 0 && common.compare(b, c)!! <= 0)
                assertTrue("$a <= $b <= $c", common.compare(a, c)!! <= 0)
        }
    }

    @Test fun malformedCoreIsRejectedAndUnknownSuffixIsNotCalledStable() {
        for (bad in listOf("", "nightly", "scheduler_120-Release", "1..0", "1.0-", "1.0/a", "9223372036854775808.0")) {
            assertNull(common.compare(bad, "1.0")); assertNull(common.compare("1.0", bad))
            assertEquals(ReleaseChannel.Unknown, common.channel(bad))
        }
        assertEquals(ReleaseChannel.Unknown, common.channel("1.0-Preview", true))
        assertEquals(frozenShutUp("1.0-Preview", "1.0-Alpha"), common.compare("1.0-Preview", "1.0-Alpha"))
    }

    @Test fun channelClassificationUsesParsedTagAndIgnoresMetadataSubstrings() {
        for ((text, channel) in listOf("1.0-IntDev_rev2" to ReleaseChannel.IntDev,
            "1.0-Internal" to ReleaseChannel.IntDev, "1.0-Alpha1" to ReleaseChannel.Alpha,
            "1.0-Beta10" to ReleaseChannel.Beta, "1.0-RC2" to ReleaseChannel.Rc,
            "1.0-Stable" to ReleaseChannel.Stable, "1.0-Release" to ReleaseChannel.Release,
            "1.0+beta" to ReleaseChannel.Release, "1.0+build-beta" to ReleaseChannel.Release,
            "1.0-___" to ReleaseChannel.Unknown, "1.0-notintdev" to ReleaseChannel.Unknown))
            assertEquals(text, channel, common.channel(text, true))
    }

    @Test fun allFourPresetsAndPublicAliasesUseCommonOrdering() {
        for (policy in listOf(UpdatePolicies.shutUp(), UpdatePolicies.nittcScheduler(), UpdatePolicies.glyphBarty(), UpdatePolicies.beastLocator())) {
            assertSame(common, policy.versions)
            assertEquals(1, sign(policy.versions.compare("1.0-IntDev_rev10", "1.0-IntDev_rev2")!!))
        }
        assertSame(common, VersionPolicies.Standard)
        assertSame(common, VersionPolicies.NittcScheduler)
        assertSame(common, VersionPolicies.GlyphBarty)
        assertSame(common, VersionPolicies.BeastLocator)
    }

    @Test fun changedLegacyOrderingIsExplicit() {
        assertEquals(0, legacyGlyphPolicy.compare("1.0-Beta10", "1.0-Beta2"))
        assertTrue(common.compare("1.0-Beta10", "1.0-Beta2")!! > 0)
        assertEquals(0, legacySchedulerPolicy.compare("1.0-IntDev_rev10", "1.0-IntDev_rev2"))
        assertTrue(common.compare("1.0-IntDev_rev10", "1.0-IntDev_rev2")!! > 0)
        assertTrue(legacySchedulerPolicy.compare("1.0-RC1", "1.0-Beta10")!! < 0)
        assertTrue(common.compare("1.0-RC1", "1.0-Beta10")!! > 0)
        assertEquals(0, legacyGlyphPolicy.compare("100", "1.0.0"))
        assertTrue(common.compare("100", "1.0.0")!! > 0)
    }

    @Test fun revisionSelectionAndIntermediateNotesUseTheSharedComparator() {
        val releases = listOf(release("v1.0-IntDev_rev2"), release("v1.0-IntDev_rev10"), release("v1.0-IntDev_rev3"))
        for (policy in listOf(UpdatePolicies.shutUp(), UpdatePolicies.nittcScheduler(), UpdatePolicies.beastLocator())) {
            val selected = ReleaseSelector.select(ReleaseCatalog(releases), InstalledVersion("1.0-IntDev_rev0", 1), policy) as CheckOutcome.Available
            assertEquals("v1.0-IntDev_rev10", selected.update.release.tag)
            assertEquals(listOf("v1.0-IntDev_rev3", "v1.0-IntDev_rev2"), selected.update.intermediateReleases.map { it.tag })
        }
    }

    @Test fun appFiltersAndFeedsRemainSeparateFromOrdering() {
        assertEquals(ReleaseFeed.Latest, UpdatePolicies.glyphBarty().feed)
        assertFalse(UpdatePolicies.glyphBarty().intermediateNotes)
        assertFalse(UpdatePolicies.nittcScheduler().releaseFilter(release("ai-runtime-9")))
        assertTrue(UpdatePolicies.beastLocator().releaseFilter(release("ai-runtime-9")))
        assertEquals(ReleaseFeed.ListReleases(30, 1), UpdatePolicies.beastLocator().feed)
        val unknown = ReleaseSelector.select(ReleaseCatalog(listOf(release("nightly"))), InstalledVersion("1.0", 1), UpdatePolicies.beastLocator())
        assertEquals(CheckOutcome.UpToDate, unknown)
    }

    @Test fun orderingEqualityNeverAuthenticatesAnApkRelease() {
        val tag = release("v1.0.0-Release")
        val apk = ApkMetadata("example.app", "1.0.0-Stable", 2, ApkSigners(setOf("a".repeat(64)), false))
        assertEquals(0, common.compare(tag.tag, apk.versionName))
        assertFalse(ApkVersionBindings.ShutUp.matches(tag, apk))
    }

    private fun sign(value: Int) = value.compareTo(0)
}
