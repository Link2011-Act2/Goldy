package jp.linkserver.goldy

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PreferencesUpdateStateStoreTest {
    private class Preferences {
        val values = mutableMapOf<String, Any>()
        var commitsSucceed = true
        val prefs: SharedPreferences = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getString" -> values[args!![0]]?.let { it as String } ?: args!![1]
                "getLong" -> values[args!![0]]?.let { it as Long } ?: args!![1]
                "edit" -> editor()
                else -> throw UnsupportedOperationException(method.name)
            }
        } as SharedPreferences

        private fun editor(): SharedPreferences.Editor {
            val pending = mutableMapOf<String, Any?>()
            return Proxy.newProxyInstance(
                SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java)
            ) { proxy, method, args ->
                when (method.name) {
                    "putString", "putLong" -> { pending[args!![0] as String] = args[1]; proxy }
                    "remove" -> { pending[args!![0] as String] = null; proxy }
                    "commit" -> {
                        if (commitsSucceed) pending.forEach { (key, value) ->
                            if (value == null) values.remove(key) else values[key] = value
                        }
                        commitsSucceed
                    }
                    else -> throw UnsupportedOperationException(method.name)
                }
            } as SharedPreferences.Editor
        }
    }

    private val cache = UpdateCache(testRepository, "shutup-v1", InstalledVersion("1.0", 1),
        "1.0", false, ReleaseCatalog(listOf(release("v2.0"))), 1000)

    @Test fun persistenceSurvivesNewStoreInstanceAndDoesNotTouchOtherKeys() = runTest {
        val prefs = Preferences()
        prefs.values["other-setting"] = "keep"
        val state = StoredUpdateState(cache, setOf("v2.0"), 2000)
        PreferencesUpdateStateStore(prefs.prefs, testRepository).write(state)
        assertEquals(state, PreferencesUpdateStateStore(prefs.prefs, testRepository).read())
        assertEquals("keep", prefs.values["other-setting"])
    }

    @Test fun corruptCacheDoesNotEraseIgnoredTagsOrRateLimit() = runTest {
        val prefs = Preferences()
        val store = PreferencesUpdateStateStore(prefs.prefs, testRepository)
        store.write(StoredUpdateState(cache, setOf("v2.0"), 2000))
        prefs.values["cache"] = "{broken"
        assertEquals(StoredUpdateState(null, setOf("v2.0"), 2000), store.read())
    }

    @Test fun removingCacheKeepsDismissalAndCleansExpiredBackoff() = runTest {
        val prefs = Preferences()
        val store = PreferencesUpdateStateStore(prefs.prefs, testRepository)
        store.write(StoredUpdateState(cache, setOf("v2.0"), 2000))
        store.write(StoredUpdateState(ignoredTags = setOf("v2.0")))
        assertEquals(StoredUpdateState(ignoredTags = setOf("v2.0")), store.read())
        assertFalse(prefs.values.containsKey("cache"))
        assertFalse(prefs.values.containsKey("retry_at"))
    }

    @Test fun failedCommitIsReportedAndLeavesPreviousData() = runTest {
        val prefs = Preferences()
        val store = PreferencesUpdateStateStore(prefs.prefs, testRepository)
        val before = StoredUpdateState(cache, setOf("v2.0"))
        store.write(before)
        prefs.commitsSucceed = false
        try { store.write(StoredUpdateState()); fail() }
        catch (_: IllegalStateException) { }
        assertEquals(before, store.read())
    }

    @Test fun mismatchedRepositoryAndInvalidPreferenceTypesFailSafely() = runTest {
        val prefs = Preferences()
        val store = PreferencesUpdateStateStore(prefs.prefs, testRepository)
        store.write(StoredUpdateState(cache, setOf("v2.0")))
        assertNull(PreferencesUpdateStateStore(prefs.prefs, GitHubRepository("other", "app")).read().cache)
        prefs.values["cache"] = 123L
        prefs.values["retry_at"] = "wrong type"
        assertEquals(StoredUpdateState(ignoredTags = setOf("v2.0")), store.read())
    }
}
