package jp.linkserver.goldy

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.IOException

@Suppress("DEPRECATION")
internal fun installedApkSnapshot(context: Context, inspector: ApkInspector): InstalledApkSnapshot {
    val pm=context.packageManager
    val info=if(Build.VERSION.SDK_INT>=33) pm.getPackageInfo(context.packageName,PackageManager.PackageInfoFlags.of(0))
        else pm.getPackageInfo(context.packageName,0)
    val code=if(Build.VERSION.SDK_INT>=28) info.longVersionCode else info.versionCode.toLong()
    val metadata=inspector.installed()
    if(metadata.packageName!=context.packageName || metadata.versionCode!=code || metadata.versionName!=info.versionName)
        throw IOException("Installed package changed while reading it")
    val source=info.applicationInfo?.sourceDir?.takeIf { it.isNotBlank() } ?: throw IOException("Installed APK unavailable")
    return InstalledApkSnapshot(metadata,File(source).canonicalFile,info.lastUpdateTime)
}
