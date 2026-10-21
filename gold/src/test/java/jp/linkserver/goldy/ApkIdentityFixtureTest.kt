package jp.linkserver.goldy

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ApkIdentityFixtureTest {
    @Test fun everyObservedPublicApkKeepsItsIdentity() {
        val fixture = JSONObject(javaClass.getResource("/release-apk-identity.json")!!.readText()).getJSONArray("observations")
        assertEquals(28, fixture.length())
        for (i in 0 until fixture.length()) {
            val row = fixture.getJSONObject(i)
            val binding = when (row.getString("repository").substringAfter('/')) {
                "ShutUp" -> ApkVersionBindings.ShutUp
                "NITTC_Scheduler" -> ApkVersionBindings.NittcScheduler
                "GlyphBarty" -> ApkVersionBindings.GlyphBarty
                else -> error("unknown fixture")
            }
            val release = release(row.getString("tag"))
            val apk = ApkMetadata(row.getString("packageName"), row.getString("versionName"), row.getLong("versionCode"), ApkSigners(setOf("a".repeat(64)), false))
            assertTrue(row.getString("tag"), binding.matches(release, apk))
            for (name in listOf(apk.versionName + "_rev0", apk.versionName.replace("Release", "Stable"), apk.versionName + "-123", apk.versionName + ".0"))
                if (name != apk.versionName) assertFalse(name, binding.matches(release, apk.copy(versionName = name)))
        }
    }
    @Test fun unconfirmedNamingRulesFailClosedInAppPolicies() {
        for (binding in listOf(ApkVersionBindings.ShutUp, ApkVersionBindings.NittcScheduler, ApkVersionBindings.GlyphBarty)) {
            for ((tag, name) in listOf("v2.1.0-Stable" to "2.1.0-Stable", "v2.1.0-IntDev" to "2.1.0-IntDev", "v2.1.0-Release_rev0" to "2.1.0-Release_rev0", "glyphbarty_v210release" to "2.1.0-Release", "scheduler_210" to "2.1.0-Release", "v2.1.0-Release-123" to "2.1.0-Release-123")) {
                assertFalse(tag, binding.matches(release(tag), ApkMetadata("app", name, 1, ApkSigners(emptySet(), false))))
            }
        }
    }
}
