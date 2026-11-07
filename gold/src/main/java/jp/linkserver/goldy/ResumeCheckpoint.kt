package jp.linkserver.goldy

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.LinkOption
import java.security.MessageDigest
import java.util.Properties

/** On-disk data is untrusted and is not a VerifiedApk. Caller must revalidate the complete APK. */
internal class ResumeCheckpoint(private val directory: File, private val maxBytes: Long) {
    data class Identity(val owner: String, val repository: String, val releaseId: Long, val tag: String,
                        val assetId: Long, val name: String, val url: String, val size: Long?, val digest: String?)
    data class Saved(val file: File, val bytes: Long, val validator: ResumeHttp.Validator, val total: Long?)

    private fun key(identity: Identity): String {
        require(identity.releaseId > 0 && identity.assetId > 0 && maxBytes > 0)
        val hash = MessageDigest.getInstance("SHA-256")
        listOf(identity.owner, identity.repository, identity.releaseId.toString(), identity.tag,
            identity.assetId.toString(), identity.name, identity.url, identity.size?.toString(), identity.digest).forEach {
            val bytes = it?.toByteArray(Charsets.UTF_8)
            hash.update(ByteBuffer.allocate(4).putInt(bytes?.size ?: -1).array())
            if (bytes != null) hash.update(bytes)
        }
        return hash.digest().hex()
    }
    private fun part(key: String) = File(directory, "goldy-resume-$key.part")
    private fun meta(key: String) = File(directory, "goldy-resume-$key.meta")
    fun file(identity: Identity): File = part(key(identity)).also { require(!Files.isSymbolicLink(it.toPath())) }
    private fun validValidator(value: ResumeHttp.Validator) = when (value.header) {
        "ETag" -> ResumeHttp.validator(value.value, null, null) == value
        "Last-Modified" -> ResumeHttp.validator(null, value.value, value.observedDate) == value
        else -> false
    }

    /** Called under the common storage lock. Names outside our exact namespace and completed APKs are untouched. */
    fun clean() = storageIo {
        val files = directory.listFiles() ?: throw java.io.IOException("Cannot list transfer directory")
        val pattern = Regex("goldy-resume-([0-9a-f]{64})\\.(part|meta|meta\\.tmp)")
        for (file in files) {
            val match = pattern.matchEntire(file.name) ?: continue
            val key = match.groupValues[1]
            if (match.groupValues[2] == "meta.tmp") {
                if (!file.delete()) throw java.io.IOException("Cannot remove abandoned metadata write")
                continue
            }
            if (match.groupValues[2] == "part" && !meta(key).exists()) {
                if (file.exists() && !file.delete()) throw java.io.IOException("Cannot remove orphan partial")
            }
            if (match.groupValues[2] != "meta") continue
            val identity = runCatching {
                require(Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) && file.length() in 1..16384)
                val p = Properties().apply { file.inputStream().use(::load) }
                Identity(p.getProperty("owner"), p.getProperty("repository"), p.getProperty("release_id").toLong(),
                    p.getProperty("tag"), p.getProperty("asset_id").toLong(), p.getProperty("asset_name"),
                    p.getProperty("original_url"), p.getProperty("asset_size")?.toLong(), p.getProperty("asset_digest"))
            }.getOrElse { if (it is java.io.IOException) throw it else null }
            val valid = identity != null && runCatching { key(identity) == key }.getOrDefault(false) && load(identity, recoverTail = true) != null
            if (!valid) listOf(file, part(key)).forEach {
                if (it.exists() && !it.delete()) throw java.io.IOException("Cannot remove invalid checkpoint")
            }
        }
    }

    /** Publish metadata only after part bytes and metadata are synced; a mismatch fails closed. */
    fun save(identity: Identity, validator: ResumeHttp.Validator, total: Long? = identity.size) {
      storageIo {
        val key = key(identity)
        val file = part(key)
        require(Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) && file.length() in 1..maxBytes)
        require(identity.size == null || file.length() <= identity.size)
        require(validValidator(validator))
        require(total == null || total in file.length()..maxBytes && (identity.size == null || total == identity.size))
        RandomAccessFile(file, "rw").use { it.fd.sync() }
        val props = Properties().apply {
            setProperty("schema", "1"); setProperty("identity", key)
            setProperty("owner", identity.owner); setProperty("repository", identity.repository)
            setProperty("release_id", identity.releaseId.toString()); setProperty("tag", identity.tag)
            setProperty("asset_id", identity.assetId.toString()); setProperty("asset_name", identity.name)
            setProperty("original_url", identity.url); setProperty("state", "partial")
            identity.size?.let { setProperty("asset_size", it.toString()) }
            identity.digest?.let { setProperty("asset_digest", it) }
            total?.let { setProperty("total", it.toString()) }
            setProperty("bytes", file.length().toString()); setProperty("sha256", digest(file))
            setProperty("validator_header", validator.header); setProperty("validator_value", validator.value)
            validator.observedDate?.let { setProperty("validator_date", it) }
        }
        val tmp = File(directory, "goldy-resume-$key.meta.tmp")
        require(!Files.isSymbolicLink(tmp.toPath()))
        FileOutputStream(tmp).use { props.store(it, null); it.fd.sync() }
        Files.move(tmp.toPath(), meta(key).toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      }
    }

    fun load(identity: Identity, recoverTail: Boolean = false): Saved? = runCatching {
        val key = key(identity)
        val file = part(key)
        val metadata = meta(key)
        require(Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) &&
            Files.isRegularFile(metadata.toPath(), LinkOption.NOFOLLOW_LINKS) && metadata.length() in 1..16384)
        val props = Properties().apply { metadata.inputStream().use(::load) }
        require(props.getProperty("schema") == "1" && props.getProperty("identity") == key)
        require(props.getProperty("state") == "partial" && props.getProperty("owner") == identity.owner &&
            props.getProperty("repository") == identity.repository && props.getProperty("release_id") == identity.releaseId.toString() &&
            props.getProperty("tag") == identity.tag && props.getProperty("asset_id") == identity.assetId.toString() &&
            props.getProperty("asset_name") == identity.name && props.getProperty("original_url") == identity.url &&
            props.getProperty("asset_size") == identity.size?.toString() && props.getProperty("asset_digest") == identity.digest)
        val bytes = props.getProperty("bytes").toLong()
        val total = props.getProperty("total")?.toLong()
        require(total == null || total in bytes..maxBytes && (identity.size == null || total == identity.size))
        require(bytes in 1..maxBytes && (bytes == file.length() || recoverTail && bytes < file.length()) &&
            file.length() <= maxBytes && (identity.size == null || file.length() <= identity.size))
        val header = props.getProperty("validator_header")
        val value = props.getProperty("validator_value")
        require(!value.isNullOrEmpty() && value.none { it == '\r' || it == '\n' })
        val validator = ResumeHttp.Validator(header, value, props.getProperty("validator_date"))
        require(validValidator(validator))
        require(props.getProperty("sha256") == digest(file, bytes))
        if (file.length() != bytes) RandomAccessFile(file, "rw").use { it.setLength(bytes); it.fd.sync() }
        Saved(file, bytes, validator, total)
    }.getOrElse { error ->
        if (error is java.io.IOException) throw ApkException(ApkFailure.Storage, cause = error)
        null
    }

    /** Only the exact selected asset's partial/metadata are removed; never a lease or APK. */
    fun discard(identity: Identity) {
      storageIo {
        val key = key(identity)
        listOf(part(key), meta(key), File(directory, "goldy-resume-$key.meta.tmp")).forEach {
            if (it.exists() && !it.delete()) throw java.io.IOException("Cannot remove transfer checkpoint")
        }
      }
    }
    private fun digest(file: File, limit: Long = file.length()): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(32768)
            var remaining = limit
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read < 0) throw java.io.EOFException()
                hash.update(buffer, 0, read); remaining -= read
            }
        }
        return hash.digest().hex()
    }
}
