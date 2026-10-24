package jp.linkserver.goldy

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class GoldyStateTest {
    @Test fun explicitBannerRestoreNeverResetsPersistentIgnoreOrNotificationOptions() = runTest {
        val store = InMemoryUpdateStateStore()
        val goldy = updater(store)
        goldy.check(); goldy.hideBanner()
        goldy.ignoreVersion("v2.0"); goldy.showBanner()
        assertNull(goldy.state.value.notificationUpdate)
        assertFalse(goldy.state.value.showBanner)
        goldy.resetIgnoredVersions()
        assertTrue(goldy.state.value.showBanner)
        goldy.hideBanner(); goldy.ignoreVersion("v2.0"); goldy.resetIgnoredVersions()
        assertNotNull(goldy.state.value.notificationUpdate)
        assertFalse(goldy.state.value.showBanner)
        goldy.showBanner()
        assertTrue(goldy.state.value.showBanner)
        goldy.updateOptions(RuntimeOptions(notificationsEnabled = false)); goldy.showBanner()
        assertFalse(goldy.state.value.showBanner)
        assertNotNull(goldy.state.value.availableUpdate)
    }

    @Test fun commonPolicyMigrationInvalidatesOldCacheWithoutDeletingIgnoredVersions() = runTest {
        val cache = UpdateCache(testRepository, "nittc-scheduler-v1", installed, installed.name, false,
            ReleaseCatalog(listOf(release("v2.0"))), clock)
        val store = InMemoryUpdateStateStore(StoredUpdateState(cache, setOf("v2.0")))
        val goldy = updater(store, config = config.copy(policy = UpdatePolicies.nittcScheduler()))
        goldy.restoreCachedState()
        assertNull(goldy.state.value.availableUpdate)
        goldy.check(CheckTrigger.Manual)
        assertNotNull(goldy.state.value.availableUpdate)
        assertNull(goldy.state.value.notificationUpdate)
        assertEquals(setOf("v2.0"), store.read().ignoredTags)
    }

    @Test fun customMetadataSourceCannotPublishAnotherRepositoryOrUnsafeAsset() = runTest {
        val unsafe = release("v2.0").copy(url = "https://github.com/attacker/other/releases/tag/v2.0")
        val goldy = updater { ReleaseCatalog(listOf(unsafe)) }
        assertTrue(goldy.check(CheckTrigger.Manual) is CheckOutcome.Failure)
        assertNull(goldy.state.value.availableUpdate)
    }
    private val config = GoldyConfig(testRepository, policy = UpdatePolicies.shutUp())
    private val installed = InstalledVersion("1.0-Release", 1)
    private var clock = 100_000L
    private fun updater(store: UpdateStateStore = InMemoryUpdateStateStore(),
        version: InstalledVersion = installed, config: GoldyConfig = this.config,
        fetch: suspend () -> ReleaseCatalog = { ReleaseCatalog(listOf(release("v2.0"))) }): Goldy =
        Goldy(config, version, store, fetch) { clock }

    @Test fun cacheAvoidsNetworkAndManualForcesRefresh() = runTest {
        var calls = 0
        val goldy = updater { calls++; ReleaseCatalog(listOf(release("v2.0"))) }
        assertTrue(goldy.check() is CheckOutcome.Available)
        assertTrue((goldy.check() as CheckOutcome.Available).fromCache)
        assertEquals(1, calls)
        goldy.check(CheckTrigger.Manual)
        assertEquals(2, calls)
    }

    @Test fun bannerHideIsTemporaryAndIgnoreSurvivesRestartWithManualAccess() = runTest {
        val store = InMemoryUpdateStateStore()
        val first = updater(store)
        first.check()
        first.hideBanner()
        assertFalse(first.state.value.showBanner)
        assertNotNull(first.state.value.notificationUpdate)
        val next = updater(store)
        next.restoreCachedState()
        assertTrue(next.state.value.showBanner)
        next.ignoreVersion("V2.0")
        assertNull(next.state.value.notificationUpdate)
        assertNotNull(next.state.value.availableUpdate)
        val restart = updater(store)
        assertEquals("v2.0", outcomeTag(restart.check(CheckTrigger.Manual)))
        assertNull(restart.state.value.notificationUpdate)
        restart.resetIgnoredVersions()
        assertNotNull(restart.state.value.notificationUpdate)
    }

    @Test fun failedChecksKeepCacheAndSuccessfulTimestamp() = runTest {
        val store = InMemoryUpdateStateStore()
        updater(store).check()
        val before = store.read()
        val failed = updater(store) { throw GitHubException(GoldyError.Network) }
        assertEquals(CheckOutcome.Failure(GoldyError.Network), failed.check(CheckTrigger.Manual))
        assertEquals(before, store.read())
        assertEquals("v2.0", failed.state.value.availableUpdate?.release?.tag)
    }

    @Test fun noUpdateClearsCandidateButNotIgnoredTags() = runTest {
        val store = InMemoryUpdateStateStore()
        val first = updater(store)
        first.check(); first.ignoreVersion("v2.0")
        updater(store) { ReleaseCatalog(emptyList()) }.check(CheckTrigger.Manual)
        val next = updater(store)
        next.restoreCachedState()
        assertNull(next.state.value.availableUpdate)
        assertEquals(setOf("v2.0"), store.read().ignoredTags)
    }

    @Test fun cancellationDoesNotPersistAndClearsChecking() = runTest {
        val store = InMemoryUpdateStateStore()
        updater(store).check()
        val before = store.read()
        val started = CompletableDeferred<Unit>()
        val goldy = updater(store) { started.complete(Unit); awaitCancellation() }
        val pending = async { goldy.check(CheckTrigger.Manual) }
        started.await()
        pending.cancelAndJoin()
        assertEquals(before, store.read())
        assertFalse(goldy.state.value.checking)
        assertNotNull(goldy.state.value.availableUpdate)
    }

    @Test fun ignoreAndOptionsDuringInFlightFetchWin() = runTest {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val goldy = updater { started.complete(Unit); finish.await(); ReleaseCatalog(listOf(release("v2.0"))) }
        val pending = async { goldy.check() }
        started.await()
        goldy.ignoreVersion("v2.0")
        goldy.updateOptions(RuntimeOptions(notificationsEnabled = false))
        finish.complete(Unit); pending.await()
        assertNotNull(goldy.state.value.availableUpdate)
        assertNull(goldy.state.value.notificationUpdate)
    }

    @Test fun changingPreviewDuringCheckCannotPublishOlderCandidate() = runTest {
        val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val goldy = updater(version = InstalledVersion("2.0-IntDev", 1),
            config = config.copy(initialOptions = RuntimeOptions(developer = DeveloperOptions(true)))) {
            started.complete(Unit); finish.await(); ReleaseCatalog(listOf(release("v1.0", published = 100)))
        }
        val pending = async { goldy.check() }
        started.await(); goldy.updateOptions(RuntimeOptions()); finish.complete(Unit); pending.await()
        assertNull(goldy.state.value.availableUpdate)
    }

    @Test fun intervalBoundaryClockRollbackAndInstalledChangesTriggerChecks() = runTest {
        val store = InMemoryUpdateStateStore()
        var calls = 0
        val goldy = updater(store) { calls++; ReleaseCatalog(listOf(release("v2.0"))) }
        goldy.check()
        clock += config.checkInterval.inWholeMilliseconds - 1
        goldy.check(); assertEquals(1, calls)
        clock++
        goldy.check(); assertEquals(2, calls)
        clock--
        goldy.check(); assertEquals(3, calls)
        updater(store, InstalledVersion("1.0-Release", 2)) { calls++; ReleaseCatalog(emptyList()) }.check()
        assertEquals(4, calls)
    }

    @Test fun automaticOffStillRestoresCacheAndManualWorks() = runTest {
        val store = InMemoryUpdateStateStore()
        updater(store).check()
        var calls = 0
        val goldy = updater(store, config = config.copy(initialOptions = RuntimeOptions(automaticChecksEnabled = false))) {
            calls++; ReleaseCatalog(listOf(release("v2.0")))
        }
        assertEquals(CheckOutcome.Disabled, goldy.check())
        assertNotNull(goldy.state.value.availableUpdate)
        goldy.check(CheckTrigger.Manual)
        assertEquals(1, calls)
    }

    @Test fun rateLimitSurvivesRestartAndManualCannotBypassIt() = runTest {
        val store = InMemoryUpdateStateStore()
        val limited = updater(store) { throw GitHubException(GoldyError.Http(429, clock + 60_000)) }
        assertTrue(limited.check() is CheckOutcome.Failure)
        var calls = 0
        val next = updater(store) { calls++; ReleaseCatalog(emptyList()) }
        assertEquals(CheckOutcome.Throttled(clock + 60_000), next.check(CheckTrigger.Manual))
        assertEquals(0, calls)
        clock += 60_000
        next.check(CheckTrigger.Manual)
        assertEquals(1, calls)
    }

    @Test fun compatibilityDeveloperIntervalAndDismissalRules() = runTest {
        for (policy in listOf(UpdatePolicies.shutUp(), UpdatePolicies.nittcScheduler(), UpdatePolicies.glyphBarty())) {
            var calls = 0
            val goldy = updater(version = InstalledVersion("1.0-IntDev", 1),
                config = config.copy(policy = policy, initialOptions = RuntimeOptions(developer = DeveloperOptions(true)))) {
                calls++; ReleaseCatalog(listOf(release("v2.0", published = 1)))
            }
            goldy.check(); goldy.ignoreVersion("v2.0"); goldy.check()
            assertEquals(policy.id, if (policy.previewBypassesInterval) 2 else 1, calls)
            assertEquals(policy.id, policy.previewBypassesDismissal, goldy.state.value.notificationUpdate != null)
        }
    }

    @Test fun simultaneousManualRequestsShareTheCompletedFetch() = runTest {
        val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        var calls = 0
        val goldy = updater { calls++; started.complete(Unit); finish.await(); ReleaseCatalog(listOf(release("v2.0"))) }
        val first = async { goldy.check(CheckTrigger.Manual) }
        started.await()
        withContext(Dispatchers.IO) {
            val second = async(start = CoroutineStart.UNDISPATCHED) { goldy.check(CheckTrigger.Manual) }
            finish.complete(Unit)
            first.await(); second.await()
        }
        assertEquals(1, calls)
    }

    @Test fun storedRuntimeReleaseIsFilteredAgainOnRestore() = runTest {
        val policy = UpdatePolicies.nittcScheduler()
        val cache = UpdateCache(testRepository, policy.id, installed, installed.name, false,
            ReleaseCatalog(listOf(release("ai-runtime-999"))), clock)
        val goldy = updater(InMemoryUpdateStateStore(StoredUpdateState(cache)), config = config.copy(policy = policy))
        goldy.restoreCachedState()
        assertNull(goldy.state.value.availableUpdate)
    }

    @Test fun failedStorageCannotAdvanceLastSuccessOrPublishNewCandidate() = runTest {
        var value = StoredUpdateState()
        val store = object : UpdateStateStore {
            override suspend fun read() = value
            override suspend fun write(state: StoredUpdateState) { throw java.io.IOException() }
        }
        val goldy = updater(store)
        assertEquals(CheckOutcome.Failure(GoldyError.Storage), goldy.check())
        assertNull(goldy.state.value.availableUpdate)
        assertNull(goldy.state.value.lastSuccessMillis)
        assertNull(value.cache)
    }
}
