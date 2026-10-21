package jp.linkserver.goldy

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal class PreferencesUpdateStateStore(
    private val prefs: SharedPreferences,
    private val repository: GitHubRepository
) : UpdateStateStore {
    constructor(context: Context, repository: GitHubRepository) : this(createPreferences(context, repository), repository)

    override suspend fun read(): StoredUpdateState = withContext(Dispatchers.IO) {
        val cache = runCatching { prefs.getString("cache", null)?.let { CacheJson.decode(repository, it) } }.getOrNull()
        val ignored = runCatching {
            val json = JSONArray(prefs.getString("ignored", "[]"))
            buildSet { for (index in 0 until json.length()) add(json.getString(index)) }
        }.getOrDefault(emptySet())
        val retry = runCatching { prefs.getLong("retry_at", 0).takeIf { it > 0 } }.getOrNull()
        StoredUpdateState(cache, ignored, retry)
    }

    override suspend fun write(state: StoredUpdateState): Unit = withContext(Dispatchers.IO) {
        val editor = prefs.edit().putString("ignored", JSONArray(state.ignoredTags.toList()).toString())
        if (state.cache == null) editor.remove("cache") else editor.putString("cache", CacheJson.encode(state.cache))
        if (state.retryAtMillis == null) editor.remove("retry_at") else editor.putLong("retry_at", state.retryAtMillis)
        check(editor.commit()) { "Could not persist Goldy state" }
    }
}

internal object CacheJson {
    fun encode(cache: UpdateCache): String = JSONObject().apply {
        put("schema", 1); put("owner", cache.repository.owner); put("repo", cache.repository.name)
        put("policy", cache.policyId); put("installed", cache.installed.name); put("code", cache.installed.code)
        put("comparison", cache.comparisonVersion); put("preview", cache.preview)
        put("checkedAt", cache.checkedAtMillis); put("complete", cache.catalog.complete)
        put("releases", JSONArray().apply {
            cache.catalog.releases.forEach { release ->
                put(JSONObject().apply {
                    put("id", release.id); put("tag_name", release.tag); put("name", release.name)
                    put("body", release.notes); put("html_url", release.url); put("prerelease", release.prerelease)
                    put("published_at", release.publishedAt?.toString())
                    put("assets", JSONArray().apply {
                        release.assets.forEach { asset -> put(JSONObject().apply {
                            put("id", asset.id); put("name", asset.name); put("browser_download_url", asset.downloadUrl)
                            put("size", asset.size); put("digest", asset.digest)
                        }) }
                    })
                })
            }
        })
    }.toString()

    fun decode(repository: GitHubRepository, value: String): UpdateCache {
        require(value.toByteArray().size <= 8 * 1024 * 1024)
        val json = JSONObject(value)
        require(json.getInt("schema") == 1 && json.getString("owner") == repository.owner &&
            json.getString("repo") == repository.name)
        val checked = json.getLong("checkedAt")
        require(checked >= 0)
        return UpdateCache(repository, json.getString("policy"), InstalledVersion(
            json.getString("installed"), json.getLong("code")), json.getString("comparison"),
            json.getBoolean("preview"), ReleaseCatalog(GitHubReleaseParser.parse(repository,
                json.getJSONArray("releases").toString()), json.getBoolean("complete")), checked)
    }
}

private fun createPreferences(context: Context, repository: GitHubRepository): SharedPreferences {
    val name = MessageDigest.getInstance("SHA-256").digest(
        (repository.owner.lowercase(java.util.Locale.ROOT) + "/" + repository.name.lowercase(java.util.Locale.ROOT)).toByteArray()
    ).joinToString("") { "%02x".format(it) }
    return context.applicationContext.getSharedPreferences("goldy_updates_" + name, Context.MODE_PRIVATE)
}
