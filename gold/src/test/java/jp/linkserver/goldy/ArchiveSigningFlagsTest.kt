package jp.linkserver.goldy

import android.content.pm.PackageManager
import org.junit.Assert.*
import org.junit.Test

@Suppress("DEPRECATION")
class ArchiveSigningFlagsTest {
    @Test fun androidNineAndTenRequireBothCollectionAndResultFlags() {
        for (sdk in listOf(28, 29)) {
            val flags = archiveSigningFlags(sdk)
            assertTrue(flags and PackageManager.GET_SIGNATURES != 0)
            assertTrue(flags and PackageManager.GET_SIGNING_CERTIFICATES != 0)
        }
    }
    @Test fun legacyAndModernPlatformsUseTheirSupportedFlags() {
        for (sdk in listOf(26, 27)) assertEquals(PackageManager.GET_SIGNATURES, archiveSigningFlags(sdk))
        for (sdk in listOf(30, 33, 36, 37)) assertEquals(PackageManager.GET_SIGNING_CERTIFICATES, archiveSigningFlags(sdk))
    }
}
