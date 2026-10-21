package jp.linkserver.goldy

import java.net.URI

data class GitHubRepository(val owner: String, val name: String) {
    init {
        require(owner.matches(Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})")))
        require(name.matches(Regex("[A-Za-z0-9_.-]{1,100}")) && name !in setOf(".", ".."))
    }

    val url: String get() = "https://github.com/" + owner + "/" + name
    internal val apiUrl: String get() = "https://api.github.com/repos/" + owner + "/" + name + "/releases"

    internal fun acceptsReleaseUrl(value: String): Boolean = runCatching {
        val uri = secureUri(value, "github.com")
        val path = uri.rawPath
        val prefix = "/" + owner + "/" + name + "/releases/tag/"
        path.startsWith(prefix, ignoreCase = true) &&
            path.substring(prefix.length).isNotBlank() && safePath(path) &&
            uri.rawQuery == null && uri.rawFragment == null
    }.getOrDefault(false)

    internal fun acceptsAssetUrl(value: String): Boolean = runCatching {
        val uri = secureUri(value, "github.com")
        val prefix = "/" + owner + "/" + name + "/releases/download/"
        uri.rawPath.startsWith(prefix, ignoreCase = true) &&
            uri.rawPath.substring(prefix.length).count { it == '/' } >= 1 &&
            safePath(uri.rawPath) && uri.rawQuery == null && uri.rawFragment == null
    }.getOrDefault(false)

    companion object {
        fun fromUrl(value: String): GitHubRepository {
            val uri = secureUri(value, "github.com")
            require(safePath(uri.rawPath) && uri.rawQuery == null && uri.rawFragment == null)
            val parts = uri.path.trim('/').split('/')
            require(parts.size == 2 || (parts.size == 3 && parts[2] == "releases"))
            return GitHubRepository(parts[0], parts[1].removeSuffix(".git"))
        }
    }
}

internal fun secureUri(value: String, host: String): URI = URI(value).also {
    require(it.scheme.equals("https", true) && it.host.equals(host, true))
    require(it.userInfo == null && it.port in setOf(-1, 443))
}

internal fun safePath(path: String): Boolean =
    '\\' !in path && !Regex("%(?:2e|2f|5c|25)", RegexOption.IGNORE_CASE).containsMatchIn(path) &&
        path.split('/').none { it == "." || it == ".." }

