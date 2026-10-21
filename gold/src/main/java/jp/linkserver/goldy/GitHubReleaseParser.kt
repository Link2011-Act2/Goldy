package jp.linkserver.goldy

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Parses release metadata only; this is not an APK verification API. */
object GitHubReleaseParser {
    fun parse(repository: GitHubRepository, body: String, latest: Boolean = false, allowNameAsTag: Boolean = false): List<GitHubRelease> =
        try {
            val array = if (latest) JSONArray().put(JSONObject(body)) else JSONArray(body)
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.getJSONObject(index)
                    if (json.optBoolean("draft")) continue
                    val id = json.getLong("id")
                    val url = json.getString("html_url")
                    require(id > 0 && repository.acceptsReleaseUrl(url))
                    val tag = json.text("tag_name").trim().ifBlank {
                        if (allowNameAsTag) json.text("name") else ""
                    }
                    if (tag.isBlank()) continue
                    require(tag.length <= 512)
                    val notes = json.text("body")
                    require(notes.toByteArray(Charsets.UTF_8).size <= 256 * 1024)
                    val assets = json.optJSONArray("assets") ?: JSONArray()
                    val parsedAssets = buildList {
                        for (assetIndex in 0 until assets.length()) {
                            val asset = assets.getJSONObject(assetIndex)
                            val assetId = asset.getLong("id")
                            val name = asset.getString("name")
                            val downloadUrl = asset.getString("browser_download_url")
                            require(assetId > 0 && name.isNotBlank() && name.length <= 1024)
                            require(repository.acceptsAssetUrl(downloadUrl))
                            val size = if (asset.isNull("size")) null else asset.getLong("size").also { require(it >= 0) }
                            add(GitHubAsset(assetId, name, downloadUrl, size, asset.optionalText("digest")))
                        }
                    }
                    add(GitHubRelease(id, tag, json.text("name"), notes, url,
                        json.optBoolean("prerelease"), json.optionalText("published_at")?.let {
                            runCatching { Instant.parse(it) }.getOrNull()
                        }, parsedAssets))
                }
            }
        } catch (error: Exception) {
            if (error is GitHubException) throw error
            throw GitHubException(GoldyError.InvalidResponse)
        }
}

internal fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key)
internal fun JSONObject.optionalText(key: String): String? = text(key).takeIf { it.isNotBlank() }

class GitHubException(val error: GoldyError) : Exception(error.toString())
