package jp.linkserver.goldy

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.CoroutineContext

internal data class InstallRecord(val id: String, val sha256: String, val size: Long, val metadata: ApkMetadata)

/** Persists handoff files outside cache; no age-based deletion of possibly active requests. */
internal class InstallFiles(private val filesDir: File) {
    val directory = File(filesDir, "goldy-apks/install")
    private val records = File(filesDir, "goldy-apks/records")
    companion object {
        val lock = Any()
        private val idPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        fun validId(id: String) = id.matches(idPattern)
    }
    fun apk(id: String): File {
        require(validId(id))
        val root = directory.canonicalFile
        val file = File(root, id + ".apk")
        require(file.canonicalFile == file) { "Symbolic link outside the handoff directory" }
        return file
    }
    private fun recordFile(id: String): File {
        require(validId(id))
        val file = File(records.canonicalFile, id + ".json")
        require(file.canonicalFile == file)
        return file
    }
    fun initialize() {
        if ((!directory.exists() && !directory.mkdirs()) || (!records.exists() && !records.mkdirs()))
            throw IOException("Could not create handoff directories")
        // Serialized by the installer gate. Completed leases are never expired automatically.
        for (root in listOf(directory, records)) for (file in root.listFiles() ?: emptyArray())
            if (file.name.endsWith(".part") && validId(file.name.removeSuffix(".part")) && !file.delete())
                throw IOException("Could not remove abandoned staging file")
    }
    fun write(record: InstallRecord) {
        val temporary = File(records, record.id + ".part")
        try {
            val apk = record.metadata
            val json = JSONObject().put("schema", 1).put("id", record.id).put("sha256", record.sha256).put("size", record.size)
                .put("packageName", apk.packageName).put("versionName", apk.versionName).put("versionCode", apk.versionCode)
                .put("multiple", apk.signers.multiple).put("signers", JSONArray(apk.signers.currentSha256.toList()))
            FileOutputStream(temporary).use { it.write(json.toString().toByteArray(Charsets.UTF_8)); it.fd.sync() }
            if (!temporary.renameTo(recordFile(record.id))) throw IOException("Could not commit handoff record")
        } finally { temporary.delete() }
    }
    fun read(id: String): InstallRecord {
        val file = recordFile(id)
        if (!file.isFile || file.length() !in 1..65536) throw IOException("Missing or invalid handoff record")
        val json = JSONObject(file.readText())
        require(json.getInt("schema") == 1 && json.getString("id") == id)
        val sha = json.getString("sha256")
        val size = json.getLong("size")
        require(sha.matches(Regex("[0-9a-f]{64}")) && size > 0)
        val signers = json.getJSONArray("signers")
        val certificates = (0 until signers.length()).map { signers.getString(it) }.toSet()
        require(certificates.size == signers.length())
        return InstallRecord(id, sha, size, ApkMetadata(json.getString("packageName"), json.getString("versionName"),
            json.getLong("versionCode"), ApkSigners(certificates, json.getBoolean("multiple"))))
    }
    fun all(): List<InstallRecord> = (records.listFiles() ?: emptyArray())
        .filter { it.name.endsWith(".json") && validId(it.name.removeSuffix(".json")) }
        .map { read(it.name.removeSuffix(".json")) }
    fun delete(id: String) {
        val apk = apk(id)
        if (apk.exists() && !apk.delete()) throw IOException("Could not remove handoff APK")
        val record = recordFile(id)
        if (record.exists() && !record.delete()) throw IOException("Could not remove handoff record")
    }
}

/** Hash the actual file and authenticate its archive again against the current installation. */
internal object ApkRevalidator {
    fun verify(file: File, expected: ApkMetadata, sha: String, size: Long, inspector: ApkInspector,
               coroutine: CoroutineContext? = null): ApkMetadata {
        if (!file.isFile) throw InstallOperationException(InstallFailure.FileMissing)
        if (size <= 0 || file.length() != size || !sha.matches(Regex("[0-9a-f]{64}")))
            throw InstallOperationException(InstallFailure.FileChanged)
        if (digest(file, size, coroutine) != sha) throw InstallOperationException(InstallFailure.FileChanged)
        coroutine?.ensureActive()
        val current = inspector.installed()
        val apk = ApkVerifier.verifyInstalled(current, inspector.inspect(file))
        if (apk != expected) throw InstallOperationException(InstallFailure.FileChanged)
        return apk
    }
    fun digest(file: File, expectedSize: Long, coroutine: CoroutineContext? = null): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var bytes = 0L
        file.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                coroutine?.ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                bytes += count
                if (bytes > expectedSize) throw InstallOperationException(InstallFailure.FileChanged)
                digest.update(buffer, 0, count)
            }
        }
        if (bytes != expectedSize) throw InstallOperationException(InstallFailure.FileChanged)
        return digest.digest().hex()
    }
}
