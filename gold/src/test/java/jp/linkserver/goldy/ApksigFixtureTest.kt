package jp.linkserver.goldy

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Actual APK contents, signatures, SDK targeting and PoR flags; no fabricated cert evidence. */
class ApksigFixtureTest {
    private fun inspect(name: String, sdk: Int): ApkSigners? {
        val bytes = javaClass.getResourceAsStream("/signatures/$name.apk")!!.use { it.readBytes() }
        val file = File.createTempFile("goldy-signed-", ".apk")
        return try { file.writeBytes(bytes); ApkSignatureAuthenticator.inspect(file, sdk) } finally { file.delete() }
    }
    @Test fun realSignatureLineageAllowsOnlyAuthenticatedForwardInstalledData() {
        for (sdk in listOf(28,29,32,33,36)) {
            val a = inspect("a", sdk)!!
            val ab = inspect("ab-v3", sdk)!!
            val abc = inspect("abc-v3", sdk)!!
            assertNotEquals(a, ab)
            assertTrue("A-B sdk=$sdk", acceptsSigningUpdate(a, ab))
            assertTrue("A-C sdk=$sdk", acceptsSigningUpdate(a, abc))
            assertTrue("B-C sdk=$sdk", acceptsSigningUpdate(ab, abc))
            assertFalse("B-A sdk=$sdk", acceptsSigningUpdate(ab, a))
            assertFalse("No PoR sdk=$sdk", acceptsSigningUpdate(a, inspect("b-unrelated", sdk)!!))
            assertFalse("capability revoked sdk=$sdk", acceptsSigningUpdate(a, inspect("ab-denied", sdk)!!))
            assertEquals(2, ab.certificateHistorySha256.size)
            assertEquals(3, abc.certificateHistorySha256.size)
        }
    }
    @Test fun v31UsesOriginalSignerBefore33AndRotatedSignerFrom33() {
        for (sdk in listOf(26,27,28,32,33,36)) {
            val a = inspect("a", sdk)!!
            val targeted = inspect("ab-v31", sdk)!!
            if (sdk < 33) assertEquals(a, targeted) else {
                assertNotEquals(a, targeted)
                assertTrue(acceptsSigningUpdate(a, targeted))
            }
        }
    }
    @Test fun pre28UsesV2FallbackWithoutAllowingDifferentSigners() {
        for (sdk in listOf(26,27)) {
            assertEquals(inspect("a", sdk), inspect("ab-v3", sdk))
            assertFalse(acceptsSigningUpdate(inspect("a", sdk)!!, inspect("b-unrelated", sdk)!!))
        }
    }
    @Test fun cryptographicContentAndLineageTamperingRejected() {
        for (sdk in listOf(28,32,33,36)) {
            assertNull("content $sdk", inspect("tampered", sdk))
            assertNull("lineage $sdk", inspect("tampered-lineage", sdk))
        }
    }
    @Test fun realMultipleSignerSetsNeedExactEquality() {
        val ab=inspect("multi-ab",28)!!;val ac=inspect("multi-ac",28)!!
        assertTrue(ab.multiple);assertTrue(ac.multiple)
        assertTrue(acceptsSigningUpdate(ab, inspect("multi-ab",28)!!))
        assertFalse(acceptsSigningUpdate(ab, ac))
        assertFalse(acceptsSigningUpdate(ab, inspect("ab-v3",28)!!))
    }
}
