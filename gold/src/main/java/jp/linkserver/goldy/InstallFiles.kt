package jp.linkserver.goldy

import android.os.Build
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.CoroutineContext

internal data class InstallRecord(val id: String, val sha256: String, val size: Long, val metadata: ApkMetadata) {
    // Body property keeps the existing constructor/component JVM signatures intact.
    var lifecycle: InstallLifecycle = InstallLifecycle()
}

internal enum class InstallBacking { Retained, Installed, Obsolete }
internal data class InstallLifecycle(
    val backing: InstallBacking = InstallBacking.Retained,
    val launchedFromCode: Long? = null,
    val launchedFromUpdateTime: Long? = null,
    val confirmedInstalled: Boolean = false
)

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
            val lifecycle = record.lifecycle
            json.put("lifecycle", JSONObject().put("backing", lifecycle.backing.name)
                .put("fromCode", lifecycle.launchedFromCode ?: JSONObject.NULL)
                .put("fromUpdateTime", lifecycle.launchedFromUpdateTime ?: JSONObject.NULL)
                .put("confirmed", lifecycle.confirmedInstalled))
            FileOutputStream(temporary).use { it.write(json.toString().toByteArray(Charsets.UTF_8)); it.fd.sync() }
            Files.move(temporary.toPath(), recordFile(record.id).toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            // Persist the directory entry before a caller removes a redundant backing copy.
            // A sync failure propagates, so the original APK remains available for retry.
            if (Build.VERSION.SDK_INT >= 26) {
                val descriptor = Os.open(records.path, OsConstants.O_RDONLY, 0)
                try { Os.fsync(descriptor) } finally { Os.close(descriptor) }
            }
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
            json.getLong("versionCode"), ApkSigners(certificates, json.getBoolean("multiple")))).also { record ->
            json.optJSONObject("lifecycle")?.let { value ->
                fun optionalLong(key: String): Long? = if(value.isNull(key)) null else value.getLong(key).also { require(it >= 0) }
                record.lifecycle = InstallLifecycle(InstallBacking.valueOf(value.getString("backing")),
                    optionalLong("fromCode"), optionalLong("fromUpdateTime"), value.getBoolean("confirmed"))
            }
        }
    }
    fun ids(): List<String> = (records.listFiles() ?: emptyArray())
        .map { it.name }.filter { it.endsWith(".json") && validId(it.removeSuffix(".json")) }
        .map { it.removeSuffix(".json") }
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
