package jp.linkserver.goldy.compose

import jp.linkserver.goldy.GitHubRelease
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
}
