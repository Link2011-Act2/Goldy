package jp.linkserver.goldy.compose

import jp.linkserver.goldy.GitHubRelease
import jp.linkserver.goldy.VersionPolicies
import org.junit.Assert.*
import org.junit.Test

class UpdatePresentationTest {
    private val release = GitHubRelease(1, "v2.4.0-Beta", url = "https://github.com/example/app/releases/tag/v2.4.0-Beta")
    @Test fun formattingNeverChangesTheRawTagOrReleaseUrl() {
        val presentation = UpdatePresentation(versionLabel = { it.tag.removePrefix("v") })
        assertEquals("2.4.0-Beta", presentation.versionLabel(release))
        assertEquals("v2.4.0-Beta", release.tag)
        assertTrue(release.url.endsWith("/v2.4.0-Beta"))
        assertEquals("Beta", presentation.historyChannelLabel(release))
        assertEquals(release.tag, UpdatePresentation().versionLabel(release))
    }
    @Test fun hostsCanPreserveRawTagsAndLocalizeHistoryChannels() {
        val presentation = UpdatePresentation(versionLabel = { it.tag }, historyChannelLabel = { "テスト版" })
        assertEquals(release.tag, presentation.versionLabel(release))
        assertEquals("テスト版", presentation.historyChannelLabel(release))
    }

    @Test fun defaultHistoryChannelsPreserveThePreviousPolicyForAllTagForms() {
        assertSame(VersionPolicies.NittcScheduler, VersionPolicies.Common)
        val presentation = UpdatePresentation()
        listOf("v1.0", "1.0-Release", "1.0-Stable", "1.0-IntDev2",
            "1.0-Internal_rev0", "1.0-Alpha", "1.0-Beta10", "1.0-RC2",
            "1.0-Beta_rev2", "1.0-Beta-rev3", "1.0-Beta.rev10",
            "1.0-unknown", "invalid", "9223372036854775808.0").forEach { tag ->
            listOf(false, true).forEach { prerelease ->
                val metadata = release.copy(tag = tag, prerelease = prerelease)
                assertEquals(tag, VersionPolicies.NittcScheduler.channel(tag, prerelease).name,
                    presentation.historyChannelLabel(metadata))
                assertEquals(tag, presentation.versionLabel(metadata))
            }
        }
    }
}
