package jp.linkserver.goldy

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CacheJsonTest {
    private val cache = UpdateCache(testRepository, "shutup-v1", InstalledVersion("1.0-IntDev", 1),
        "0.5-Release", true, ReleaseCatalog(listOf(release("v2.0", true, 123))), 1000)

    @Test fun snapshotRoundTripKeepsScopeAndCatalog() {
        assertEquals(cache, CacheJson.decode(testRepository, CacheJson.encode(cache)))
    }

    @Test fun wrongRepositorySchemaAndUntrustedCacheUrlAreRejected() {
        val source = CacheJson.encode(cache)
        assertThrows(IllegalArgumentException::class.java) {
            CacheJson.decode(testRepository, JSONObject(source).put("schema", 99).toString())
        }
        assertThrows(IllegalArgumentException::class.java) {
            CacheJson.decode(GitHubRepository("different", "app"), source)
        }
        assertThrows(GitHubException::class.java) {
            CacheJson.decode(testRepository, source.replace("https://github.com/owner/app/releases", "https://evil.example/releases"))
        }
    }

    @Test fun negativeTimestampAndOversizedCacheAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CacheJson.decode(testRepository, JSONObject(CacheJson.encode(cache)).put("checkedAt", -1).toString())
        }
        assertThrows(IllegalArgumentException::class.java) {
            CacheJson.decode(testRepository, " ".repeat(8 * 1024 * 1024 + 1))
        }
    }
}
