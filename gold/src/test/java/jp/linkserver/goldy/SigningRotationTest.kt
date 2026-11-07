package jp.linkserver.goldy

import org.junit.Assert.*
import org.junit.Test

class SigningRotationTest {
    private val a = "a".repeat(64)
    private val b = "b".repeat(64)
    private val c = "c".repeat(64)
    private fun signer(current: String, history: List<String>? = null, allowed: Set<String> = emptySet(), sdk: Int = 28) =
        ApkSigners(setOf(current), false).also { s -> history?.let { s.authenticatedHistory = AuthenticatedSigningHistory(it, allowed, sdk) } }
    @Test fun equalSignerAndContinuousForwardRotationsAccepted() {
        assertTrue(acceptsSigningUpdate(signer(a), signer(a)))
        assertTrue(acceptsSigningUpdate(signer(a), signer(b, listOf(a,b), setOf(a))))
        assertTrue(acceptsSigningUpdate(signer(a), signer(c, listOf(a,b,c), setOf(a,b))))
        assertTrue(acceptsSigningUpdate(signer(b, listOf(a,b)), signer(c, listOf(a,b,c), setOf(b))))
    }
    @Test fun mereIntersectionMissingCapabilityReverseForkAndUnknownSdkRejected() {
        assertFalse(acceptsSigningUpdate(signer(a), signer(b)))
        assertFalse(acceptsSigningUpdate(signer(a), signer(b, listOf(a,b))))
        assertFalse(acceptsSigningUpdate(signer(b, listOf(a,b)), signer(c, listOf(a,b,c), setOf(a))))
        assertFalse(acceptsSigningUpdate(signer(b, listOf(a,b)), signer(a)))
        assertFalse(acceptsSigningUpdate(signer(b, listOf(a,b)), signer(c, listOf(c,b,c), setOf(b))))
        assertFalse(acceptsSigningUpdate(signer(b, listOf(a,b)), signer(c, listOf(b,c), setOf(b))))
        assertFalse(acceptsSigningUpdate(signer(a), signer(b, listOf(a,b), setOf(a), 27)))
        assertFalse(acceptsSigningUpdate(signer(a), signer(b, listOf(a,c), setOf(a))))
    }
    @Test fun copiesDoNotCarryRotationAuthorityAndMultipleSignersRequireFullEquality() {
        val rotated = signer(b, listOf(a,b), setOf(a))
        assertFalse(acceptsSigningUpdate(signer(a), rotated.copy()))
        val multi = ApkSigners(setOf(a,b), true)
        assertTrue(acceptsSigningUpdate(multi, multi.copy()))
        assertFalse(acceptsSigningUpdate(multi, ApkSigners(setOf(a,c), true)))
        assertFalse(acceptsSigningUpdate(multi, rotated))
    }
}
