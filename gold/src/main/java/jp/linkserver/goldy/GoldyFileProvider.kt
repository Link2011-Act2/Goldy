package jp.linkserver.goldy

import android.net.Uri
import android.os.ParcelFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.FileNotFoundException

/** Dedicated provider prevents collisions with a host's existing androidx FileProvider declaration. */
class GoldyFileProvider : FileProvider() {
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Goldy APKs are read-only")
        val context = context ?: throw FileNotFoundException("Provider unavailable")
        return synchronized(InstallFiles.lock) {
            try {
                val files = InstallFiles(context.filesDir)
                val record = files.read(requestId(uri, context.packageName))
                val inspector = AndroidApkInspector(context)
                val source = resolveInstallSource(files, record, installedApkSnapshot(context, inspector))
                ApkRevalidator.verify(source, record.metadata, record.sha256, record.size, inspector)
                ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
            } catch (error: Exception) {
                throw FileNotFoundException("APK unavailable or no longer valid").apply { initCause(error) }
            }
        }
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val context = context ?: throw FileNotFoundException("Provider unavailable")
        return synchronized(InstallFiles.lock) {
            val record = InstallFiles(context.filesDir).read(requestId(uri, context.packageName))
            val columns = (projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
                .filter { it == OpenableColumns.DISPLAY_NAME || it == OpenableColumns.SIZE }.toTypedArray()
            MatrixCursor(columns, 1).apply { addRow(columns.map {
                if(it == OpenableColumns.SIZE) record.size else record.id + ".apk"
            }) }
        }
    }
    private fun requestId(uri: Uri, packageName: String): String {
        val segments = uri.pathSegments
        if(uri.scheme != "content" || uri.authority != packageName + ".goldy.fileprovider" ||
            uri.query != null || uri.fragment != null || segments.size != 2 ||
            segments[0] != "goldy_install" || !segments[1].endsWith(".apk")) throw FileNotFoundException("Invalid APK URI")
        return segments[1].removeSuffix(".apk").also { if(!InstallFiles.validId(it)) throw FileNotFoundException("Invalid APK URI") }
    }
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw SecurityException("Goldy APKs are read-only; the host must release the request")
}
