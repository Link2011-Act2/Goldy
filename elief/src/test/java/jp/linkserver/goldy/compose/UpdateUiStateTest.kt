package jp.linkserver.goldy.compose

import jp.linkserver.goldy.*
import org.junit.Assert.*
import org.junit.Test

class UpdateUiStateTest {
    private val release = GitHubRelease(1, "v1.2.0-Beta", url = "https://github.com/example/app/releases/tag/v1.2.0-Beta",
        assets = listOf(GitHubAsset(1, "app.apk", "https://github.com/example/app/releases/download/v1.2.0-Beta/app.apk", 1, null)))
    @Test fun metadataAndHistoryRetained() {
        val update = UpdateCandidate(release, listOf(release.copy(id = 2)), false)
        val ui = GoldyState(availableUpdate = update).toUpdateUiState(VersionPolicies.NittcScheduler)
        assertEquals(update, ui.update)
        assertEquals(ReleaseChannel.Beta, ui.channel)
        assertTrue(ui.apkAvailable)
    }
    @Test fun noUpdateIsEmpty() { assertNull(GoldyState().toUpdateUiState().update) }
    @Test fun sameSelectionPolicyUsed() {
        val ui = GoldyState(availableUpdate = UpdateCandidate(release)).toUpdateUiState(apkSelection = ApkSelectionPolicy { null })
        assertFalse(ui.apkAvailable)
    }
    @Test fun ambiguousApksHaveNoDownload() {
        val r = release.copy(assets = release.assets + release.assets[0].copy(id = 2, name = "other.apk"))
        assertFalse(GoldyState(availableUpdate = UpdateCandidate(r)).toUpdateUiState().apkAvailable)
    }
    @Test fun schedulerRuntimeStillExcluded() {
        val r = release.copy(tag = "ai-runtime-1")
        assertFalse(GoldyState(availableUpdate = UpdateCandidate(r)).toUpdateUiState(apkSelection = ApkSelectionPolicies.NittcScheduler).apkAvailable)
    }
    @Test fun callerOperationAndErrorPreserved() {
        val transfer = UpdateTransferState.Failed("Localized", UpdateAction.Install)
        val ui = GoldyState(checking = true).toUpdateUiState(transfer = transfer, message = "Metadata error")
        assertEquals(transfer, ui.transfer); assertTrue(ui.checking); assertEquals("Metadata error", ui.message)
    }
    @Test fun ignoredNotificationDoesNotHideDetailCandidate() {
        val update = UpdateCandidate(release)
        val state = GoldyState(availableUpdate = update, notificationUpdate = null, showBanner = false)
        val ui = state.toUpdateUiState()
        assertEquals(update, ui.update)
        assertTrue(ui.apkAvailable)
        assertEquals(UpdateTransferState.Idle, ui.transfer)
    }
    @Test fun safeAbsoluteWebUrls() {
        assertTrue(safeWebUrl("https://github.com/o/r/releases#notes"))
        assertTrue(safeWebUrl("http://example.org/page?q=1"))
    }
    @Test fun unsafeSchemesBlocked() {
        listOf("javascript:alert(1)", "intent://host/#Intent;end", "file:///data/data/private", "content://a", "data:text/html,x").forEach { assertFalse(it, safeWebUrl(it)) }
    }
    @Test fun malformedAndCredentialLinksBlocked() {
        listOf("//example.org", "/relative", "https:", "https://user:pass@example.org", "https://a\n.example.org", "https://a b").forEach { assertFalse(it, safeWebUrl(it)) }
    }
}
