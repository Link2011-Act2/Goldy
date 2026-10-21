package jp.linkserver.goldy

import org.junit.Assert.*
import org.junit.Test

class ApkVerifierTest {
    private val signers = ApkSigners(setOf("a".repeat(64)), false)
    private val installed = ApkMetadata("app", "1.0", 10, signers)
    private val archive = installed.copy(versionName = "2.0", versionCode = 11)
    private val release = release("2.0")
    private fun rejects(reason: ApkFailure, apk: ApkMetadata?, binding: ApkVersionBinding = ApkVersionBindings.Exact, current: ApkMetadata = installed) {
        try { ApkVerifier.verify(current, apk, release, binding); fail("Expected " + reason) }
        catch (error: ApkException) { assertEquals(reason, error.reason) }
    }
    @Test fun sameCurrentSignerAndExactIdentityAccepted() { assertEquals(archive, ApkVerifier.verify(installed, archive, release)) }
    @Test fun brokenArchiveRejected() = rejects(ApkFailure.InvalidApk, null)
    @Test fun anotherPackageRejected() = rejects(ApkFailure.PackageMismatch, archive.copy(packageName = "other"))
    @Test fun anotherSignerRejected() = rejects(ApkFailure.SignerMismatch, archive.copy(signers = ApkSigners(setOf("b".repeat(64)), false)))
    @Test fun missingAndMalformedSignersRejected() {
        for (s in listOf(ApkSigners(emptySet(), false), ApkSigners(setOf("not a hash"), false), ApkSigners(setOf("a".repeat(64)), true)))
            rejects(ApkFailure.SignerMismatch, archive.copy(signers = s))
    }
    @Test fun multiSignerRequiresEntireCurrentSet() {
        val multiple = ApkSigners(setOf("a".repeat(64), "b".repeat(64)), true)
        val current = installed.copy(signers = multiple)
        assertEquals(multiple, ApkVerifier.verify(current, archive.copy(signers = multiple), release).signers)
        rejects(ApkFailure.SignerMismatch, archive, current = current)
        rejects(ApkFailure.SignerMismatch, archive.copy(signers = ApkSigners(setOf("a".repeat(64), "c".repeat(64)), true)), current = current)
    }
    @Test fun downgradeRejectedEvenIfTagMatches() = rejects(ApkFailure.VersionCodeTooLow, archive.copy(versionCode = 9))
    @Test fun equalVersionCodeAllowedForExistingAppCompatibility() { assertEquals(10, ApkVerifier.verify(installed, archive.copy(versionCode = 10), release).versionCode) }
    @Test fun comparisonEqualityNeverImpliesIdentity() {
        for (name in listOf("2.0.0", "2.0.0-Release", "2.0.0-Stable", "2.0_rev1", "2.0-IntDev", "2.0-Beta", "2.0-RC1", "app_20"))
            rejects(ApkFailure.VersionMismatch, archive.copy(versionName = name))
    }
    @Test fun explicitLeadingVPreservesEveryOtherCharacter() {
        assertTrue(ApkVersionBindings.ShutUp.matches(release("v2.0.0-Release"), archive.copy(versionName = "2.0.0-Release")))
        assertFalse(ApkVersionBindings.ShutUp.matches(release("v2.0.0-Release"), archive.copy(versionName = "2.0.0-Stable")))
        assertFalse(ApkVersionBindings.ShutUp.matches(release("v2.0.0-IntDev_rev1"), archive.copy(versionName = "2.0.0-IntDev_rev2")))
        assertFalse(ApkVersionBindings.ShutUp.matches(release("glyphbarty_210release"), archive.copy(versionName = "2.1.0-Release")))
    }
    @Test fun appSelectorsRejectAmbiguityAndNonAppRuntime() {
        fun asset(name: String) = GitHubAsset(name.hashCode().toLong(), name, "", null, null)
        assertNull(ApkSelectionPolicies.Single.select(release.copy(assets = listOf(asset("arm.apk"), asset("x86.apk")))))
        val preferred = asset("shutup_v200release.apk")
        assertEquals(preferred, ApkSelectionPolicies.ShutUp.select(release.copy(assets = listOf(asset("other.apk"), preferred))))
        assertNull(ApkSelectionPolicies.NittcScheduler.select(release.copy(tag = "ai-runtime-1", assets = listOf(asset("runtime.apk")))))
    }
}
