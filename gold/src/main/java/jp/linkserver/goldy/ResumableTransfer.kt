package jp.linkserver.goldy

import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.*

internal fun resumeIdentity(config: ApkDownloadConfig, release: GitHubRelease, asset: GitHubAsset) =
    ResumeCheckpoint.Identity(config.repository.owner, config.repository.name, release.id, release.tag,
        asset.id, asset.name, asset.downloadUrl, asset.size, asset.digest)

/** Transfer checkpoints are metadata only; every completion traverses the full APK verifier. */
internal class ResumableTransfer(private val config: ApkDownloadConfig, private val directory: File,
    private val inspector: ApkInspector, private val transport: AssetTransport,
    private val completionDirectory: File = directory) {
    suspend fun download(release: GitHubRelease, asset: GitHubAsset, progress: (ApkProgress) -> Unit): VerifiedApk {
        var completed: File? = null
        try { return coroutineScope {
            var active: AssetConnection? = null
            val monitor = Any()
            val task = async(Dispatchers.IO) {
                withResumeStorage(directory) {
                        val store = ResumeCheckpoint(directory, config.transferLimit)
                        val identity = resumeIdentity(config, release, asset)
                        val partial = store.file(identity)
                        var saved = store.load(identity, recoverTail = true)
                        if (saved == null || transport !is RangeAssetTransport) { store.discard(identity); saved = null }
                        var bytes = saved?.bytes ?: 0L
                        var validator = saved?.validator
                        var total = saved?.total ?: asset.size
                        var attempts = 0
                        var segments = 0
                        var fallbackUsed = false
                        var refreshedRedirect = false
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            attempts++
                            var keep = false
                            var preserveDeniedCheckpoint = false
                            try {
                                var url = asset.downloadUrl
                                var redirects = 0
                                var response: AssetConnection
                                while (true) {
                                    response = if (transport is RangeAssetTransport)
                                        transport.openRange(url, config.userAgent, bytes, validator?.value)
                                    else transport.open(url, config.userAgent)
                                    synchronized(monitor) { active = response }
                                    currentCoroutineContext().ensureActive()
                                    if (response.status !in setOf(301, 302, 303, 307, 308)) break
                                    if (++redirects > 5) throw ApkException(ApkFailure.RedirectLimit)
                                    val next = response.header("Location")?.let { URI(url).resolve(it).toString() }
                                        ?: throw ApkException(ApkFailure.InvalidUrl)
                                    if (!AssetUrls.acceptsRedirect(next)) throw ApkException(ApkFailure.InvalidUrl)
                                    response.close(); url = next
                                }
                                if (redirects > 0 && response.status in setOf(401,403)) {
                                    // A cached/expired signed CDN URL grants no authority. Refresh it once
                                    // from GitHub, without treating denial as APK data or erasing the prefix.
                                    preserveDeniedCheckpoint = true
                                    if (!refreshedRedirect) {
                                        refreshedRedirect = true; keep = true; continue
                                    }
                                    throw ApkException(ApkFailure.Http,response.status)
                                }
                                if (bytes > 0 && response.status == 416) {
                                    val declared = ResumeHttp.unsatisfiedTotal(response.header("Content-Range"))
                                    if (declared == null || declared > config.transferLimit) throw ApkException(ApkFailure.SizeMismatch)
                                    // Even a matching length is not proof of a complete or authentic APK.
                                    if (fallbackUsed) throw ApkException(ApkFailure.SizeMismatch)
                                    response.close(); store.discard(identity); bytes = 0; validator = null
                                    fallbackUsed = true; progress(ApkProgress(0, asset.size, attempts)); continue
                                }
                                if (response.header("Content-Encoding")?.equals("identity", true) == false)
                                    throw ApkException(ApkFailure.SizeMismatch)
                                val rawLength = response.header("Content-Length")
                                val length = rawLength?.takeIf { it.matches(Regex("[0-9]+")) }?.toLongOrNull()
                                if (rawLength != null && (length == null || length <= 0)) throw ApkException(ApkFailure.SizeMismatch)
                                val start: Long
                                val expectedEnd: Long?
                                when (response.status) {
                                    200 -> {
                                        if (length != null && length > config.transferLimit) throw ApkException(ApkFailure.TooLarge)
                                        if (length != null && asset.size != null && asset.size != length)
                                            throw ApkException(ApkFailure.SizeMismatch)
                                        store.discard(identity); bytes = 0; start = 0
                                        total = length ?: asset.size; expectedEnd = total
                                        validator = ResumeHttp.validator(response.header("ETag"), response.header("Last-Modified"), response.header("Date"))
                                    }
                                    206 -> {
                                        val current = validator ?: throw ApkException(ApkFailure.SizeMismatch)
                                        val range = ResumeHttp.partial(response.header("Content-Range"), rawLength,
                                            response.header("Content-Encoding"), bytes, total, config.transferLimit,
                                            current, response.header(current.header)) ?: throw ApkException(ApkFailure.SizeMismatch)
                                        if (partial.length() != bytes) throw ApkException(ApkFailure.Storage)
                                        start = bytes; total = range.total; expectedEnd = range.end + 1
                                    }
                                    else -> throw ApkException(ApkFailure.Http, response.status)
                                }
                                progress(ApkProgress(bytes, total, attempts))
                                var checkpointAt = bytes
                                response.input().use { input ->
                                    val output = try { FileOutputStream(partial, start > 0) }
                                        catch (error: IOException) { throw ApkException(ApkFailure.Storage, cause = error) }
                                    output.use {
                                        val buffer = ByteArray(32768)
                                        while (true) {
                                            currentCoroutineContext().ensureActive()
                                            val count = input.read(buffer)
                                            if (count < 0) break
                                            if (count == 0) continue
                                            if (bytes > config.transferLimit - count) throw ApkException(ApkFailure.TooLarge)
                                            if (expectedEnd != null && bytes + count > expectedEnd)
                                                throw ApkException(ApkFailure.SizeMismatch)
                                            try { output.write(buffer, 0, count) }
                                            catch (error: IOException) { throw ApkException(ApkFailure.Storage, cause = error) }
                                            bytes += count
                                            if (validator != null && bytes - checkpointAt >= 1024 * 1024) {
                                                storageIo { output.flush() }; store.save(identity, validator!!, total); checkpointAt = bytes
                                            }
                                            progress(ApkProgress(bytes, total, attempts))
                                        }
                                        storageIo { output.flush(); output.fd.sync() }
                                    }
                                }
                                if (bytes == 0L || expectedEnd != null && bytes != expectedEnd)
                                    throw EOFException("Incomplete range transfer")
                                if (total != null && bytes < total!!) {
                                    if (++segments > 128) throw ApkException(ApkFailure.SizeMismatch)
                                    validator?.let { store.save(identity, it, total) } ?: throw ApkException(ApkFailure.SizeMismatch)
                                    keep = true; continue
                                }
                                // Never reuse a previous digest accumulator, including after a 416 or restart.
                                val hash = MessageDigest.getInstance("SHA-256")
                                storageIo { partial.inputStream().use { input ->
                                    val buffer = ByteArray(32768)
                                    while (true) {
                                        currentCoroutineContext().ensureActive()
                                        val count = input.read(buffer); if (count < 0) break; hash.update(buffer, 0, count)
                                    }
                                } }
                                val digest = hash.digest().hex()
                                if (partial.length() != bytes || asset.size != null && bytes != asset.size) throw ApkException(ApkFailure.SizeMismatch)
                                if (asset.digest != null && !asset.digest.substringAfter(':').equals(digest, true)) throw ApkException(ApkFailure.DigestMismatch)
                                val metadata = try { ApkVerifier.verify(inspector.installed(), inspector.inspect(partial), release, config.versionBinding) }
                                    catch (error: ApkException) { throw error }
                                    catch (error: Exception) { throw ApkException(ApkFailure.InvalidApk, cause = error) }
                                currentCoroutineContext().ensureActive()
                                storageIo {
                                    if (!completionDirectory.isDirectory && !completionDirectory.mkdirs())
                                        throw IOException("Cannot create completed transfer directory")
                                }
                                val destination = File(completionDirectory, "goldy-${UUID.randomUUID()}.apk")
                                if (!partial.renameTo(destination)) throw ApkException(ApkFailure.Storage)
                                completed = destination
                                store.discard(identity)
                                return@withResumeStorage VerifiedApk(destination, metadata, digest, asset)
                            } catch (cancel: CancellationException) { keep = true; throw cancel
                            } catch (error: IOException) {
                                keep = true
                                currentCoroutineContext().ensureActive()
                                if (attempts >= config.maxAttempts) throw ApkException(
                                    if (error is EOFException) ApkFailure.SizeMismatch else ApkFailure.Network, cause = error)
                            } catch (error: ApkException) {
                                val retryable = error.reason == ApkFailure.Network || error.reason == ApkFailure.Http &&
                                    (error.status == 408 || error.status in 500..599)
                                // Disk pressure must not erase an otherwise reusable checkpoint or spin retries.
                                keep = retryable || error.reason == ApkFailure.Storage || preserveDeniedCheckpoint
                                if (!retryable || attempts >= config.maxAttempts) throw error
                            } finally {
                                synchronized(monitor) { active?.close(); active = null }
                                if (keep && bytes > 0 && validator != null && partial.isFile) store.save(identity, validator!!, total)
                                else if (completed == null) {
                                    store.discard(identity)
                                    bytes = 0; validator = null; total = asset.size
                                }
                            }
                            delay(250L * attempts)
                        }
                        @Suppress("UNREACHABLE_CODE") error("unreachable")
                }
            }
            try { task.await() } finally {
                task.cancel()
                synchronized(monitor) { active?.close() }
            }
        } } catch (error: Throwable) { completed?.delete(); throw error }
    }
}
