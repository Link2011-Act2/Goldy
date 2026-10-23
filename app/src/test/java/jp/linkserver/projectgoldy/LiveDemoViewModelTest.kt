package jp.linkserver.projectgoldy

import jp.linkserver.goldy.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveDemoViewModelTest {
    private fun release(tag: String = "v2.0.0-Release") = GitHubRelease(1, tag,
        url = "https://github.com/Link2011-Act2/ShutUp/releases/tag/$tag")

    @Test fun duplicateFetchesAndRepositorySwitchAreGuardedWhileWaiting() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val response = CompletableDeferred<ReleaseCatalog>()
            var calls = 0
            val model = LiveDemoViewModel(LiveTarget.entries.associateWith { { calls++; response.await() } })
            model.fetch(); model.fetch(); model.select(LiveTarget.Glyph)
            runCurrent()
            assertEquals(1, calls)
            assertEquals(LiveTarget.ShutUp, model.target)
            assertTrue(model.snapshot.checking)
            response.complete(ReleaseCatalog(listOf(release())))
            advanceUntilIdle()
            assertFalse(model.snapshot.checking)
            assertTrue(model.comparison() is CheckOutcome.Available)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun failureRetainsCachedNotesAndRetryDeadlinePreventsAnotherRequest() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var calls = 0
            val deadline = System.currentTimeMillis() + 60_000
            val model = LiveDemoViewModel(LiveTarget.entries.associateWith { {
                calls++
                if (calls == 1) ReleaseCatalog(listOf(release()))
                else throw GitHubException(GoldyError.Http(429, deadline))
            } })
            model.fetch(); advanceUntilIdle()
            model.fetch(); advanceUntilIdle()
            assertTrue(model.snapshot.fromCache)
            assertEquals("v2.0.0-Release", model.selectedRelease()?.tag)
            assertEquals(deadline, model.snapshot.retryAt)
            model.fetch(); advanceUntilIdle()
            assertEquals(2, calls)
            assertTrue(model.snapshot.message.contains("Rate limited"))
        } finally { Dispatchers.resetMain() }
    }

    @Test fun cancellationKeepsThePreviousCatalogAndAllowsRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var calls = 0
            val model = LiveDemoViewModel(LiveTarget.entries.associateWith { {
                calls++
                if (calls == 2) awaitCancellation() else ReleaseCatalog(listOf(release()))
            } })
            model.fetch(); advanceUntilIdle()
            model.fetch(); runCurrent(); model.cancel(); advanceUntilIdle()
            assertFalse(model.snapshot.checking)
            assertTrue(model.snapshot.fromCache)
            assertNotNull(model.selectedRelease())
            model.fetch(); advanceUntilIdle()
            assertEquals(3, calls)
        } finally { Dispatchers.resetMain() }
    }
}
