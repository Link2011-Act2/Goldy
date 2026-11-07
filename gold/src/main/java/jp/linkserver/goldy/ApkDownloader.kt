package jp.linkserver.goldy

import android.content.Context
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Transfer bounds and retry policy; validated checkpoints can survive interruption. */
data class ApkDownloadConfig(
    val repository: GitHubRepository,
    val selection: ApkSelectionPolicy = ApkSelectionPolicies.Single,
    val versionBinding: ApkVersionBinding = ApkVersionBindings.Exact,
    val maxBytes: Long = 256L * 1024 * 1024,
    val maxAttempts: Int = 3,
    val userAgent: String = "Goldy/0.1.0"
) {
    internal val transferLimit: Long get() = minOf(maxBytes, 256L * 1024 * 1024)

    init {
        require(maxBytes > 0); require(maxAttempts in 1..5)
        require(userAgent.isNotBlank() && userAgent.none { it == '\r' || it == '\n' })
    }
}
data class ApkProgress(val bytes: Long, val total: Long?, val attempt: Int)
/** The caller owns this verified transfer file and should delete it after use. Never modify it before use. */
class VerifiedApk internal constructor(val file: File, val metadata: ApkMetadata, val sha256: String, val asset: GitHubAsset)

/** No background scope or installer. All transfers in this process share one cancellable gate. */
class ApkDownloader internal constructor(
    private val config: ApkDownloadConfig,
    private val directory: File,
    private val inspector: ApkInspector,
    private val transport: AssetTransport
) {
    private var legacyDirectory: File? = null
    private var completionDirectory: File = directory

    companion object {
        private val transferGate = Mutex()
        fun create(context: Context, config: ApkDownloadConfig): ApkDownloader = createResumable(context, config)
        /** Persistent app-private transfer storage, separate from installer leases. */
        fun createResumable(context: Context, config: ApkDownloadConfig): ApkDownloader = ApkDownloader(
            config, File(context.applicationContext.filesDir, "goldy-transfers"), AndroidApkInspector(context), UrlAssetTransport).also {
                it.legacyDirectory = File(context.applicationContext.cacheDir, "goldy-apks")
                it.completionDirectory = File(context.applicationContext.cacheDir, "goldy-apks")
            }
    }

    /** OS/coroutine interruption retains checkpoints. Explicit cancellation must call discardPartial afterwards. */
    suspend fun downloadResumable(release: GitHubRelease, onProgress: (ApkProgress) -> Unit = {}): VerifiedApk = transferGate.withLock {
        val asset = config.selection.select(release) ?: throw ApkException(ApkFailure.NoAsset)
        if (asset !in release.assets || asset.id <= 0 || !asset.name.endsWith(".apk", true)) throw ApkException(ApkFailure.NoAsset)
        if (!config.repository.acceptsReleaseUrl(release.url) || !acceptsInitial(release, asset)) throw ApkException(ApkFailure.InvalidUrl)
        if (asset.size != null && (asset.size <= 0 || asset.size > config.transferLimit)) throw ApkException(ApkFailure.TooLarge)
        if (asset.digest != null && !asset.digest.matches(Regex("sha256:[0-9a-fA-F]{64}"))) throw ApkException(ApkFailure.InvalidDigest)
        ResumableTransfer(config, directory, inspector, transport, completionDirectory).download(release, asset, onProgress)
    }

    suspend fun discardPartial(release: GitHubRelease) = transferGate.withLock {
        withResumeStorage(directory) {
            config.selection.select(release)?.let { ResumeCheckpoint(directory, config.transferLimit).discard(resumeIdentity(config, release, it)) }
        }
    }

    suspend fun savedProgress(release: GitHubRelease): ApkProgress? = transferGate.withLock {
        withResumeStorage(directory) {
            config.selection.select(release)?.let { asset ->
                ResumeCheckpoint(directory, config.transferLimit).load(resumeIdentity(config, release, asset), true)?.let { ApkProgress(it.bytes, it.total ?: asset.size, 1) }
            }
        }
    }

    /** Main-safe. Interruption retains resumable checkpoints; explicit cancel uses discardPartial. */
    suspend fun download(release: GitHubRelease, onProgress: (ApkProgress) -> Unit = {}): VerifiedApk =
        downloadResumable(release, onProgress)

    private fun acceptsInitial(release: GitHubRelease, asset: GitHubAsset): Boolean = runCatching {
        val assetPrefix = "/" + config.repository.owner + "/" + config.repository.name + "/releases/download/"
        val releasePrefix = "/" + config.repository.owner + "/" + config.repository.name + "/releases/tag/"
        val assetPath = URI(asset.downloadUrl).path
        val releasePath = URI(release.url).path
        config.repository.acceptsAssetUrl(asset.downloadUrl) &&
            '/' !in asset.name && '\\' !in asset.name &&
            assetPath.startsWith(assetPrefix, true) && assetPath.substring(assetPrefix.length) == release.tag + "/" + asset.name &&
            releasePath.startsWith(releasePrefix, true) && releasePath.substring(releasePrefix.length) == release.tag
    }.getOrDefault(false)

    /** Removes only this component's abandoned partial files, serialized against active transfers. */
    suspend fun cleanPartialFiles() = transferGate.withLock {
        withResumeStorage(directory) {
            cleanParts(directory)
            legacyDirectory?.takeIf { it != directory }?.let { cleanParts(it) }
            ResumeCheckpoint(directory, config.transferLimit).clean()
        }
    }
    private fun cleanParts(target: File) {
        if (!target.exists()) return
        val files = target.listFiles() ?: throw ApkException(ApkFailure.Storage)
        for (file in files) if (file.name.matches(Regex("goldy-[0-9a-f-]{36}\\.part")) && !file.delete())
            throw ApkException(ApkFailure.Storage)
    }


}

internal object AssetUrls {
    private val hosts = setOf("release-assets.githubusercontent.com", "objects.githubusercontent.com", "github-releases.githubusercontent.com")
    fun acceptsRedirect(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme.equals("https", true) && uri.host?.lowercase() in hosts && uri.userInfo == null &&
            uri.port in setOf(-1, 443) && uri.rawFragment == null && safePath(uri.rawPath) && uri.rawPath.startsWith('/')
    }.getOrDefault(false)
}
internal interface AssetConnection : Closeable {
    val status: Int
    fun header(name: String): String?
    fun input(): InputStream
}
internal fun interface AssetTransport { fun open(url: String, userAgent: String): AssetConnection }
internal interface RangeAssetTransport : AssetTransport {
    fun openRange(url: String, userAgent: String, offset: Long, validator: String?): AssetConnection
}
internal object UrlAssetTransport : RangeAssetTransport {
    override fun open(url: String, userAgent: String) = openRange(url, userAgent, 0, null)
    override fun openRange(url: String, userAgent: String, offset: Long, validator: String?): AssetConnection {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 15_000; connection.readTimeout = 30_000
        connection.setRequestProperty("User-Agent", userAgent)
        connection.setRequestProperty("Accept-Encoding", "identity")
        connection.useCaches = false
        if (offset > 0) {
            connection.setRequestProperty("Range", "bytes=$offset-")
            validator?.let { connection.setRequestProperty("If-Range", it) }
        }
        return object : AssetConnection {
            override val status get() = connection.responseCode
            override fun header(name: String): String? = connection.getHeaderField(name)
            override fun input(): InputStream = connection.inputStream
            override fun close() = connection.disconnect()
        }
    }
}
