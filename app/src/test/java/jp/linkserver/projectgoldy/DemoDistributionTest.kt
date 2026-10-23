package jp.linkserver.projectgoldy

import jp.linkserver.goldy.*
import org.junit.Assert.*
import org.junit.Test

class DemoDistributionTest {
    private val asset = GitHubAsset(1, "goldy-demo-0.2.0-release.apk",
        "https://github.com/Link2011-Act2/Goldy/releases/download/v0.2.0-Release/goldy-demo-0.2.0-release.apk", 100, null)
    private val release = GitHubRelease(1, "v0.2.0-Release",
        url = "https://github.com/Link2011-Act2/Goldy/releases/tag/v0.2.0-Release", assets = listOf(asset))
    private val installed = ApkMetadata("jp.linkserver.projectgoldy", "0.1.0-Release", 1, ApkSigners(setOf("a".repeat(64)), false))

    @Test fun onlyUnambiguousDemoAssetsAndExactReleaseVersionsAreAccepted() {
        assertEquals(asset, DemoDistribution.selection.select(release))
        assertNull(DemoDistribution.selection.select(release.copy(assets = listOf(asset, asset.copy(id = 2)))))
        assertFalse(DemoDistribution.policy.releaseFilter(release.copy(assets = listOf(asset.copy(name = "other-app.apk")))))
        val candidate = installed.copy(versionName = "0.2.0-Release", versionCode = 2)
        assertEquals(candidate, ApkVerifier.verify(installed, candidate, release, DemoDistribution.binding))
        assertFalse(DemoDistribution.binding.matches(release, candidate.copy(versionName = "0.2.0-Beta")))
    }

    @Test fun foreignPackageSignerAndDowngradeRemainRejected() {
        val candidate = installed.copy(versionName = "0.2.0-Release", versionCode = 2)
        listOf(
            candidate.copy(packageName = "another.app") to ApkFailure.PackageMismatch,
            candidate.copy(signers = ApkSigners(setOf("b".repeat(64)), false)) to ApkFailure.SignerMismatch,
            candidate.copy(versionCode = 0) to ApkFailure.VersionCodeTooLow
        ).forEach { (archive, expected) ->
            try {
                ApkVerifier.verify(installed, archive, release, DemoDistribution.binding)
                fail("Expected rejection: $expected")
            } catch (error: ApkException) { assertEquals(expected, error.reason) }
        }
    }
}
