package example.goldyconsumer

import jp.linkserver.goldy.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ConsumerTest {
    @Test fun allThreePoliciesAreUsableFromAnIndependentArtifact() {
        assertTrue(VersionPolicies.ShutUp.compare("1.0-Beta10", "1.0-Beta2")!! > 0)
        assertTrue(VersionPolicies.NittcScheduler.compare("1.0-Beta10", "1.0-Beta2")!! > 0)
        assertTrue(VersionPolicies.GlyphBarty.compare("1.0-Beta10", "1.0-Beta2")!! > 0)
        assertEquals(ReleaseFeed.Latest, UpdatePolicies.glyphBarty().feed)
    }

    @Test fun releaseMetadataAndSelectionWorkOutsideTheProducerBuild() {
        val repo = GitHubRepository("owner", "app")
        val releases = GitHubReleaseParser.parse(repo,
            """[{"id":1,"tag_name":"v2.0","html_url":"https://github.com/owner/app/releases/tag/v2.0","body":null}]""")
        val outcome = ReleaseSelector.select(ReleaseCatalog(releases), InstalledVersion("1.0-Release", 1),
            UpdatePolicies.shutUp())
        assertEquals("v2.0", (outcome as CheckOutcome.Available).update.release.tag)
    }

    @Test fun storeAndCoroutinesAreAvailableTransitively() = runBlocking {
        val store: UpdateStateStore = InMemoryUpdateStateStore()
        store.write(StoredUpdateState(ignoredTags = setOf("v2.0")))
        assertEquals(setOf("v2.0"), store.read().ignoredTags)
    }
    @Test fun apkIdentityVerificationIsUsableFromPublishedGold() {
        val signers = ApkSigners(setOf("a".repeat(64)), false)
        val apk = ApkMetadata("example.app", "1.1.0-Release", 4, signers)
        val release = GitHubRelease(1, "v1.1.0-Release", url = "https://github.com/owner/app/releases/tag/v1.1.0-Release")
        assertEquals(apk, ApkVerifier.verify(apk.copy(versionCode = 3), apk, release, ApkVersionBindings.ShutUp))
        assertFalse(ApkVersionBindings.ShutUp.matches(release, apk.copy(versionName = "1.1.0-Stable")))
        val config = ApkDownloadConfig(GitHubRepository("owner", "app"), ApkSelectionPolicies.ShutUp, ApkVersionBindings.ShutUp)
        assertEquals(3, config.maxAttempts)
    }

}
