package jp.linkserver.goldy

import java.net.URLEncoder
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

internal val testRepository = GitHubRepository("owner", "app")
internal fun release(tag: String, prerelease: Boolean = false, published: Long? = null): GitHubRelease =
    GitHubRelease((tag.hashCode().toLong() and 0x7fffffff) + 1, tag, notes = "notes " + tag,
        url = testRepository.url + "/releases/tag/" + URLEncoder.encode(tag, "UTF-8"),
        prerelease = prerelease, publishedAt = published?.let(Instant::ofEpochMilli))

internal fun releaseJson(tag: String, draft: Boolean = false, prerelease: Boolean = false): JSONObject =
    JSONObject().put("id", (tag.hashCode().toLong() and 0x7fffffff) + 1)
        .put("tag_name", tag).put("name", tag).put("body", "notes")
        .put("html_url", testRepository.url + "/releases/tag/" + URLEncoder.encode(tag, "UTF-8"))
        .put("draft", draft).put("prerelease", prerelease).put("published_at", "2026-01-01T00:00:00Z")
        .put("assets", JSONArray())

internal fun response(vararg tags: String): String = JSONArray(tags.map { releaseJson(it) }).toString()

internal fun outcomeTag(outcome: CheckOutcome): String? = when (outcome) {
    is CheckOutcome.Available -> outcome.update.release.tag
    is CheckOutcome.Incomplete -> outcome.update?.release?.tag
    else -> null
}
