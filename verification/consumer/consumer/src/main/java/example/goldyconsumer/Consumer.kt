package example.goldyconsumer

import android.content.Context
import jp.linkserver.goldy.*
import kotlinx.coroutines.flow.StateFlow

/** Compiled against the published AAR, not a project dependency or included source. */
class Consumer(context: Context) {
    val shutUp = Goldy.create(context, GoldyConfig(
        GitHubRepository("Link2011-Act2", "ShutUp"), UpdatePolicies.shutUp()))
    val scheduler = Goldy.create(context, GoldyConfig(
        GitHubRepository("Link2011-Act2", "NITTC_Scheduler"), UpdatePolicies.nittcScheduler()))
    val glyphBarty = Goldy.create(context, GoldyConfig(
        GitHubRepository("Link2011-Act2", "GlyphBarty"), UpdatePolicies.glyphBarty(),
        initialOptions = RuntimeOptions(automaticChecksEnabled = false)))
    val state: StateFlow<GoldyState> = shutUp.state
    val beast = Goldy.create(context, GoldyConfig(
        GitHubRepository("Link2011-Act2", "BeastLocator"), UpdatePolicies.beastLocator()))
    val commonComparison = VersionPolicies.Common.compare("1.0-IntDev_rev2", "1.0-IntDev_rev10")

    suspend fun checkManually(): CheckOutcome = shutUp.check(CheckTrigger.Manual)
    suspend fun ignore(tag: String) = shutUp.ignoreVersion(tag)
    suspend fun showBanner() = shutUp.showBanner()
}

// Download policy compilation against only the published AAR.
fun apkDownloader(context: android.content.Context, repository: jp.linkserver.goldy.GitHubRepository) =
    jp.linkserver.goldy.ApkDownloader.create(context, jp.linkserver.goldy.ApkDownloadConfig(
        repository, jp.linkserver.goldy.ApkSelectionPolicies.ShutUp, jp.linkserver.goldy.ApkVersionBindings.ShutUp))

fun installer(context: android.content.Context): jp.linkserver.goldy.ApkInstaller = jp.linkserver.goldy.ApkInstaller.create(context)
