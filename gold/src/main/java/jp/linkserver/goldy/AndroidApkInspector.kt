package jp.linkserver.goldy

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

/** Uses certificate-collecting PackageManager parsing; never installs or launches the archive. */
class AndroidApkInspector(context: Context) : ApkInspector {
    private val context = context.applicationContext
    private val pm = this.context.packageManager
    @Suppress("DEPRECATION")
    private val flags: Int get() = if (Build.VERSION.SDK_INT >= 28)
        PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    override fun installed(): ApkMetadata {
        val info = if (Build.VERSION.SDK_INT >= 33)
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        else @Suppress("DEPRECATION") pm.getPackageInfo(context.packageName, flags)
        return metadata(info) ?: throw ApkException(ApkFailure.InvalidApk)
    }
    @Suppress("DEPRECATION")
    override fun inspect(file: File): ApkMetadata? {
        // Android 9/10 collect archive certificates only when the legacy flag is also present.
        val archiveFlags = archiveSigningFlags(Build.VERSION.SDK_INT)
        val info = if (Build.VERSION.SDK_INT >= 33)
            pm.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(archiveFlags.toLong()))
        else @Suppress("DEPRECATION") pm.getPackageArchiveInfo(file.absolutePath, archiveFlags)
        return info?.let(::metadata)
    }
    @Suppress("DEPRECATION")
    private fun metadata(info: PackageInfo): ApkMetadata? {
        // A split APK alone is never a complete update.
        if (!info.splitNames.isNullOrEmpty()) return null
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        if (signatures.isNullOrEmpty()) return null
        val multiple = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.hasMultipleSigners() == true else signatures.size > 1
        val hashes = signatures.map { sha256(it.toByteArray()) }.toSet()
        if (hashes.size != signatures.size) return null
        return ApkMetadata(info.packageName ?: return null, info.versionName ?: return null,
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong(), ApkSigners(hashes, multiple))
    }
}

internal fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).hex()
internal fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }

// Pure, tested flag selection; constants are inlined, and the caller passes the actual SDK level.
@android.annotation.SuppressLint("InlinedApi")
@Suppress("DEPRECATION")
internal fun archiveSigningFlags(sdk: Int): Int = when {
    sdk < 28 -> PackageManager.GET_SIGNATURES
    sdk < 30 -> PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
    else -> PackageManager.GET_SIGNING_CERTIFICATES
}
