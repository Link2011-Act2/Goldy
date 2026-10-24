package jp.linkserver.goldy

import org.junit.Assert.assertEquals
import org.junit.Test
import jp.linkserver.goldy.legacy.shutup.GitHubRelease as OldShutUpRelease
import jp.linkserver.goldy.legacy.shutup.selectReleaseUpdate as oldShutUpSelect
import jp.linkserver.goldy.legacy.scheduler.AppUpdateInfo as OldSchedulerUpdate
import jp.linkserver.goldy.legacy.scheduler.legacySelect as oldSchedulerSelect

class SelectionCharacterizationTest {
    private val catalogs = listOf(
        listOf("v1.0-Stable", "v1.0-Release", "v0.9", "v1.1-Beta2", "v1.2-IntDev"),
        listOf("v1.0-Beta2", "v1.0-Beta10", "v1.0-IntDev_rev2", "v1.0-IntDev_rev10"),
        listOf("nightly", "v9.0", "v0.5", "v1.0+build.1", "v1.0+build.2"),
        listOf("scheduler_120-Release", "nittc_1.3.0-Beta", "glyphbarty_210-Release"),
        emptyList()
    )
    private val installed = listOf("0.0", "0.5-Release", "0.5-Beta", "0.5-IntDev",
        "1.0-IntDev", "1.0-Release", "2.0-Release")

    @Test fun shutUpCandidateAndAllIntermediateNotesMatchFrozenImplementation() {
        for (tags in catalogs) for (current in installed) for (preview in listOf(false, true)) {
            val releases = tags.mapIndexed { index, tag -> release(tag, "Beta" in tag || "IntDev" in tag, (index * 100).toLong()) }
            val old = oldShutUpSelect(releases.map { OldShutUpRelease(
                it.tag, it.notes, it.url, it.prerelease, publishedAt = it.publishedAt!!.toEpochMilli()
            ) }, current, preview)
            val outcome = ReleaseSelector.select(ReleaseCatalog(releases), InstalledVersion(current, 1),
                UpdatePolicies.shutUp(), RuntimeOptions(developer = DeveloperOptions(preview)))
            val actual = (outcome as? CheckOutcome.Available)?.update
            val label = tags.toString() + " from " + current + " preview=" + preview
            assertEquals(label, old?.tag, actual?.release?.tag)
            assertEquals(label, old?.intermediateNotes?.map { it.tag }, actual?.intermediateReleases?.map { it.tag })
        }
    }

    @Test fun schedulerCandidateAndNotesMatchFrozenImplementation() {
        for (tags in catalogs) for (current in installed) for (preview in listOf(false, true)) {
            val releases = tags.mapIndexed { index, tag -> release(tag, "Beta" in tag || "IntDev" in tag, (index * 100).toLong()) }
            val effectivePreview = preview && legacySchedulerPolicy.channel(current) == ReleaseChannel.IntDev
            val old = oldSchedulerSelect(releases.map { OldSchedulerUpdate(it.tag,
                legacySchedulerPolicy.channel(it.tag, it.prerelease).name,
                it.notes, it.url, null, null, it.prerelease) }, current, effectivePreview)
            val outcome = ReleaseSelector.select(ReleaseCatalog(releases), InstalledVersion(current, 1),
                UpdatePolicies.nittcScheduler().copy(versions = legacySchedulerPolicy), RuntimeOptions(developer = DeveloperOptions(preview)))
            val actual = (outcome as? CheckOutcome.Available)?.update
            val label = tags.toString() + " from " + current + " preview=" + preview
            assertEquals(label, old?.tagName, actual?.release?.tag)
            assertEquals(label, old?.intermediateReleaseNotes?.map { it.tagName }, actual?.intermediateReleases?.map { it.tag })
        }
    }
}
