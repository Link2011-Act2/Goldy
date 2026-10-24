package jp.linkserver.goldy

import org.junit.Assert.*
import org.junit.Test

class ReleaseSelectorTest {
    private fun select(releases: List<GitHubRelease>, current: String, policy: UpdatePolicy = UpdatePolicies.shutUp(),
        developer: DeveloperOptions = DeveloperOptions(), complete: Boolean = true): CheckOutcome =
        ReleaseSelector.select(ReleaseCatalog(releases, complete), InstalledVersion(current, 1), policy,
            RuntimeOptions(developer = developer))

    @Test fun shutUpTracksAndDescendingIntermediateNotes() {
        val releases = listOf(release("v1.1"), release("v1.3"), release("v1.4-Beta2", true), release("v1.5-IntDev", true), release("v1.2"))
        val stable = select(releases, "1.0-Release") as CheckOutcome.Available
        assertEquals("v1.3", stable.update.release.tag)
        assertEquals(listOf("v1.2", "v1.1"), stable.update.intermediateReleases.map { it.tag })
        assertEquals("v1.4-Beta2", outcomeTag(select(releases, "1.0-Beta")))
        assertEquals("v1.5-IntDev", outcomeTag(select(releases, "1.0-IntDev")))
    }

    @Test fun schedulerKeepsAllChannelsAndExcludesAiRuntimeEverywhere() {
        val policy = UpdatePolicies.nittcScheduler()
        val updates = listOf(release("v1.1"), release("AI-RUNTIME-999.0.0", published = 9999),
            release("v1.3-IntDev", true), release("v1.2-Beta", true))
        val normal = select(updates, "1.0-Release", policy) as CheckOutcome.Available
        assertEquals("v1.3-IntDev", normal.update.release.tag)
        assertFalse(normal.update.intermediateReleases.any { it.tag.startsWith("ai-runtime-", true) })
        assertEquals("v1.3-IntDev", outcomeTag(select(updates, "9.0-IntDev", policy,
            DeveloperOptions(ignoreVersionComparison = true))))
        val withApk = release("ai-runtime-999").copy(assets = listOf(GitHubAsset(1, "scheduler.apk",
            testRepository.url + "/releases/download/ai-runtime-999/scheduler.apk", 100, null)))
        assertEquals(CheckOutcome.NoEligibleRelease, select(listOf(withApk), "0.0-IntDev", policy,
            DeveloperOptions(true, currentVersionOverride = "0")))
    }

    @Test fun glyphPreservesLatestAndNoIntermediateNotes() {
        val policy = UpdatePolicies.glyphBarty()
        assertEquals(ReleaseFeed.Latest, policy.feed)
        val update = select(listOf(release("v2.1-Release")), "2.0-IntDev", policy) as CheckOutcome.Available
        assertTrue(update.update.intermediateReleases.isEmpty())
        assertEquals(CheckOutcome.UpToDate, select(listOf(release("v2.1-Release")), "3.0-IntDev", policy))
        assertEquals("v2.1-Release", outcomeTag(select(listOf(release("v2.1-Release")), "3.0-IntDev", policy, DeveloperOptions(true))))
    }

    @Test fun appSpecificPreviewSelectionIsRetained() {
        val releases = listOf(release("v9.0", published = 100), release("v0.5", published = 200))
        assertEquals("v0.5", outcomeTag(select(releases, "2.0-IntDev", developer = DeveloperOptions(true))))
        assertEquals("v9.0", outcomeTag(select(releases, "2.0-IntDev",
            UpdatePolicies.nittcScheduler(), DeveloperOptions(true))))
        assertEquals("nightly", outcomeTag(select(listOf(release("nightly")), "1.0-IntDev", developer = DeveloperOptions(true))))
    }

    @Test fun nonIntDevCannotUsePreviewOrFakedCurrentVersion() {
        for (installed in listOf("2.0", "2.0-Release", "2.0-Stable", "2.0-Beta", "2.0-RC")) {
            assertNull(outcomeTag(select(listOf(release("v1.0")), installed,
                developer = DeveloperOptions(true, true, "0.0-IntDev"))))
        }
    }

    @Test fun commonOrderingResolvesFormerGlyphBuildNumberTies() {
        val releases = listOf(release("v1.0-Beta2", published = 100), release("v1.0-Beta10", published = 200))
        assertEquals("v1.0-Beta10", outcomeTag(select(releases, "0.0", UpdatePolicies.glyphBarty().copy(feed = ReleaseFeed.ListReleases()))))
        assertEquals("v1.0-Beta10", outcomeTag(select(releases, "0.0-IntDev", UpdatePolicies.nittcScheduler())))
    }

    @Test fun incompleteAndUnsupportedAreNotReportedAsUpToDate() {
        assertEquals(CheckOutcome.UnsupportedVersion, select(listOf(release("v1.0")), "development"))
        assertTrue(select(emptyList(), "1.0", complete = false) is CheckOutcome.Incomplete)
        assertEquals(CheckOutcome.NoPublishedReleases, select(emptyList(), "1.0"))
        assertTrue(select(listOf(release("v2.0")), "1.0", complete = false) is CheckOutcome.Incomplete)
    }

    @Test fun profilesRetainFetchAndDeveloperDefaults() {
        assertEquals(ReleaseFeed.ListReleases(100, 1), UpdatePolicies.shutUp().feed)
        assertEquals(ReleaseFeed.ListReleases(30, 1), UpdatePolicies.nittcScheduler().feed)
        assertTrue(UpdatePolicies.nittcScheduler().previewBypassesInterval)
        assertFalse(UpdatePolicies.glyphBarty().previewBypassesInterval)
        assertTrue(UpdatePolicies.glyphBarty().previewBypassesDismissal)
    }
}
