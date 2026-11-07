package jp.linkserver.goldy

import org.junit.Assert.*
import org.junit.Test

class PublishedBindingTest {
    private fun matches(tag: String, name: String, binding: ApkVersionBinding = ApkVersionBindings.Published) =
        binding.matches(release(tag), ApkMetadata("app", name, 1, ApkSigners(emptySet(), false)))
    @Test fun commonBindingPreservesAllIdentityCharactersAcrossFourApps() {
        val names = listOf("1.2.0-Release", "1.2.0-Beta2", "1.2.0-RC6", "1.2.0-IntDev_rev10", "1.2.0-Release-123", "1.2.0+build.4", "1.2.0")
        for (binding in listOf(ApkVersionBindings.ShutUp, ApkVersionBindings.NittcScheduler, ApkVersionBindings.GlyphBarty, ApkVersionBindings.BeastLocator)) {
            for (name in names) { assertTrue(matches("v$name", name, binding)); assertTrue(matches(name, name, binding)) }
        }
        assertFalse(matches("v1.2.0", "1.2.0", ApkVersionBindings.Exact))
        assertTrue(matches("v1.2.0", "v1.2.0", ApkVersionBindings.Exact))
        assertTrue(matches("V1.2.0", "V1.2.0"))
        assertFalse(matches("V1.2.0", "1.2.0"))
    }
    @Test fun noCaseSeparatorDigitRevisionOrWhitespaceNormalization() {
        for ((tag,name) in listOf("v1.2.0" to "1.2.0.0", "v1.2.0-Release" to "1.2.0-Stable", "v1.2.0-Beta2" to "1.2.0-Beta3", "v1.2.0-RC6" to "1.2.0-rc6", "v1.2.0-IntDev_rev10" to "1.2.0-IntDev-rev10", "v1.2.0-Release" to "1.2.1-Release")) assertFalse(matches(tag,name))
        for (tag in listOf("", "v", "vv1.2", "vV1.2", " v1.2", "v1.2 ", "v1..2", "v1.2/Release", "v1.2-", "v1.2\n")) assertFalse(matches(tag,tag.removePrefix("v")))
    }
}
