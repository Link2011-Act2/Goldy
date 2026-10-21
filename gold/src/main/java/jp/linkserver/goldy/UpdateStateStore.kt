package jp.linkserver.goldy

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class UpdateCache(
    val repository: GitHubRepository,
    val policyId: String,
    val installed: InstalledVersion,
    val comparisonVersion: String,
    val preview: Boolean,
    val catalog: ReleaseCatalog,
    val checkedAtMillis: Long
)

data class StoredUpdateState(
    val cache: UpdateCache? = null,
    val ignoredTags: Set<String> = emptySet(),
    val retryAtMillis: Long? = null
)

/** One store per Goldy instance/repository. Do not clear unrelated application preferences. */
interface UpdateStateStore {
    suspend fun read(): StoredUpdateState
    suspend fun write(state: StoredUpdateState)
}

class InMemoryUpdateStateStore(initial: StoredUpdateState = StoredUpdateState()) : UpdateStateStore {
    private var value = initial
    private val mutex = Mutex()
    override suspend fun read(): StoredUpdateState = mutex.withLock { value }
    override suspend fun write(state: StoredUpdateState) { mutex.withLock { value = state } }
}
