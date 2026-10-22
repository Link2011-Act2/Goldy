package jp.linkserver.goldy

import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import java.io.FileNotFoundException

/** Dedicated provider prevents collisions with a host's existing androidx FileProvider declaration. */
class GoldyFileProvider : FileProvider() {
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Goldy APKs are read-only")
        val context = context ?: throw FileNotFoundException("Provider unavailable")
        return synchronized(InstallFiles.lock) {
            try {
                val segments = uri.pathSegments
                if (uri.scheme != "content" || uri.authority != context.packageName + ".goldy.fileprovider" ||
                    segments.size != 2 || segments[0] != "goldy_install" || !segments[1].endsWith(".apk"))
                    throw FileNotFoundException("Invalid APK URI")
                val id = segments[1].removeSuffix(".apk")
                val files = InstallFiles(context.filesDir)
                val record = files.read(id)
                ApkRevalidator.verify(files.apk(id), record.metadata, record.sha256, record.size, AndroidApkInspector(context))
                super.openFile(uri, mode) ?: throw FileNotFoundException("APK could not be opened")
            } catch (error: Exception) {
                throw FileNotFoundException("APK unavailable or no longer valid").apply { initCause(error) }
            }
        }
    }
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw SecurityException("Goldy APKs are read-only; the host must release the request")
}
