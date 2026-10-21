package jp.linkserver.goldy

import android.content.Context
import android.os.Build
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Metadata-only updater. No request runs until check(); no worker/service is registered.
 * Call suspend operations from the host's lifecycle/ViewModel scope.
 */
class Goldy internal constructor(
    private val config: GoldyConfig,
    private val installed: InstalledVersion,
    private val store: UpdateStateStore,
    private val fetchCatalog: suspend () -> ReleaseCatalog,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val mutableState = MutableStateFlow(GoldyState())
    val state: StateFlow<GoldyState> = mutableState.asStateFlow()
    private val stateMutex = Mutex()
    private val checkMutex = Mutex()
    private var options = config.initialOptions
    private var stored = StoredUpdateState()
    private var loaded = false
    private var generation = 0L
    @Volatile private var completedChecks = 0L
    private var lastCheckGeneration = -1L
    private var lastCheckOutcome: CheckOutcome? = null
    private var hiddenBannerTag: String? = null

    suspend fun restoreCachedState(): Unit = withContext(Dispatchers.IO) {
        stateMutex.withLock { load(); publish(fromCache = true) }
    }

    suspend fun check(trigger: CheckTrigger = CheckTrigger.Automatic): CheckOutcome = withContext(Dispatchers.IO) {
        val observed = completedChecks
        checkMutex.withLock {
            try {
                val early = stateMutex.withLock {
                    load()
                    publish(fromCache = true)
                    val cache = applicableCache()
                    val developer = config.policy.effectiveDeveloper(installed, options)
                    val earlyOutcome = when {
                        trigger == CheckTrigger.Automatic && !options.automaticChecksEnabled -> CheckOutcome.Disabled
                        (stored.retryAtMillis ?: 0) > now() -> CheckOutcome.Throttled(stored.retryAtMillis!!)
                        completedChecks != observed && lastCheckGeneration == generation -> lastCheckOutcome
                        trigger == CheckTrigger.Automatic && cache != null &&
                            !developer.ignoreCheckInterval &&
                            !(developer.ignoreVersionComparison && config.policy.previewBypassesInterval) &&
                            now() >= cache.checkedAtMillis &&
                            now() - cache.checkedAtMillis < config.checkInterval.inWholeMilliseconds ->
                                outcome(cache.catalog, fromCache = true)
                        else -> null
                    }
                    if (earlyOutcome != null) mutableState.value = state.value.copy(lastOutcome = earlyOutcome)
                    else mutableState.value = state.value.copy(checking = true)
                    earlyOutcome
                }
                if (early != null) return@withLock early
                val catalog = fetchCatalog()
                currentCoroutineContext().ensureActive()
                stateMutex.withLock {
                    // Re-evaluate current options after I/O; ignores/option changes during fetch win.
                    val developer = config.policy.effectiveDeveloper(installed, options)
                    val cache = UpdateCache(config.repository, config.policy.id, installed,
                        comparisonVersion(developer), developer.ignoreVersionComparison, catalog, now())
                    require(validCatalog(cache.catalog)) { "Invalid cached release metadata" }
                    val result = outcome(catalog)
                    persist(stored.copy(cache = cache, retryAtMillis = null))
                    publish(fromCache = false, checking = false, result = result)
                    complete(result)
                    result
                }
            } catch (cancel: CancellationException) {
                withContext(NonCancellable) { stateMutex.withLock {
                    mutableState.value = state.value.copy(checking = false)
                } }
                throw cancel
            } catch (error: Exception) {
                val failure = when (error) {
                    is GitHubException -> error.error
                    else -> GoldyError.Storage
                }
                stateMutex.withLock {
                    val retry = (failure as? GoldyError.Http)?.retryAtMillis
                    var reported = failure
                    if (retry != null) try { persist(stored.copy(retryAtMillis = retry)) }
                    catch (_: Exception) { reported = GoldyError.Storage }
                    val result = CheckOutcome.Failure(reported)
                    publish(fromCache = true, checking = false, result = result)
                    complete(result)
                    result
                }
            }
        }
    }

    suspend fun updateOptions(value: RuntimeOptions): Unit = withContext(Dispatchers.IO) {
        stateMutex.withLock {
            load()
            options = value
            generation++
            publish(fromCache = true)
        }
    }

    suspend fun hideBanner(): Unit = stateMutex.withLock {
        load()
        hiddenBannerTag = state.value.notificationUpdate?.release?.tag
        publish(fromCache = true)
    }

    suspend fun ignoreVersion(tag: String): Unit = withContext(Dispatchers.IO) {
        require(tag.isNotBlank() && tag.length <= 512)
        stateMutex.withLock {
            load()
            persist(stored.copy(ignoredTags = stored.ignoredTags + tag.lowercase(Locale.ROOT)))
            publish(fromCache = true)
        }
    }

    suspend fun resetIgnoredVersions(): Unit = withContext(Dispatchers.IO) {
        stateMutex.withLock {
            load()
            persist(stored.copy(ignoredTags = emptySet()))
            publish(fromCache = true)
        }
    }

    private suspend fun load() {
        if (!loaded) { stored = store.read(); loaded = true }
    }
    private suspend fun persist(next: StoredUpdateState) {
        store.write(next)
        stored = next
    }
    private fun complete(result: CheckOutcome) {
        completedChecks++
        lastCheckGeneration = generation
        lastCheckOutcome = result
    }
    private fun comparisonVersion(developer: DeveloperOptions): String =
        developer.currentVersionOverride?.trim()?.takeIf { it.isNotEmpty() } ?: installed.name

    private fun applicableCache(): UpdateCache? {
        val developer = config.policy.effectiveDeveloper(installed, options)
        return stored.cache?.takeIf {
            it.repository == config.repository && it.policyId == config.policy.id &&
                it.installed == installed && it.comparisonVersion == comparisonVersion(developer) &&
                it.preview == developer.ignoreVersionComparison && validCatalog(it.catalog)
        }
    }
    private fun validCatalog(catalog: ReleaseCatalog): Boolean = catalog.releases.all {
        it.id > 0 && it.tag.isNotBlank() && config.repository.acceptsReleaseUrl(it.url) &&
            it.assets.all { asset -> asset.id > 0 && config.repository.acceptsAssetUrl(asset.downloadUrl) }
    }
    private fun outcome(catalog: ReleaseCatalog, fromCache: Boolean = false): CheckOutcome {
        val result = ReleaseSelector.select(catalog, installed, config.policy, options)
        return if (result is CheckOutcome.Available) result.copy(fromCache = fromCache) else result
    }
    private fun publish(fromCache: Boolean, checking: Boolean = state.value.checking, result: CheckOutcome? = state.value.lastOutcome) {
        val cache = applicableCache()
        val selected = cache?.let { outcome(it.catalog, fromCache) }
        val available = when (selected) {
            is CheckOutcome.Available -> selected.update
            is CheckOutcome.Incomplete -> selected.update
            else -> null
        }
        val developer = config.policy.effectiveDeveloper(installed, options)
        val bypass = developer.ignoreVersionComparison && config.policy.previewBypassesDismissal
        val notification = available?.takeIf {
            options.notificationsEnabled && (bypass || stored.ignoredTags.none { tag -> tag.equals(it.release.tag, true) })
        }
        mutableState.value = GoldyState(available, notification,
            notification != null && !notification.release.tag.equals(hiddenBannerTag, true),
            checking, cache?.checkedAtMillis, fromCache, result)
    }

    companion object {
        /** Reads actual package metadata; never consults the library's BuildConfig. */
        fun create(context: Context, config: GoldyConfig, store: UpdateStateStore? = null): Goldy {
            val app = context.applicationContext
            val info = if (Build.VERSION.SDK_INT >= 33) app.packageManager.getPackageInfo(
                app.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0)
            ) else {
                @Suppress("DEPRECATION") app.packageManager.getPackageInfo(app.packageName, 0)
            }
            val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
                @Suppress("DEPRECATION") info.versionCode.toLong()
            }
            val installed = InstalledVersion(requireNotNull(info.versionName), code)
            val client = GitHubReleaseClient(config)
            return Goldy(config, installed, store ?: PreferencesUpdateStateStore(app, config.repository), client::fetch)
        }
    }
}
