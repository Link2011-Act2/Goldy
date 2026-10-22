package example.goldyprobe

import android.app.Instrumentation
import android.os.Bundle
import jp.linkserver.goldy.*
import java.io.File

/** Installs only this disposable probe; official APKs are read as data files. */
class ApkProbe : Instrumentation() {
    private lateinit var arguments: Bundle
    override fun onCreate(arguments: Bundle) { super.onCreate(arguments); this.arguments = arguments; start() }
    override fun onStart() {
        val report = Bundle()
        try {
            if (arguments.getString("mode") == "missing-permission") {
                check(ApkInstaller.create(targetContext).permissionStatus() == InstallPermission.MissingManifestPermission)
                report.putString("result", "PASS: explicit REQUEST_INSTALL_PACKAGES declaration required")
                finish(0, report); return
            }
            val inspector = AndroidApkInspector(targetContext)
            val installed = inspector.installed()
            val self = inspector.inspect(File(targetContext.applicationInfo.sourceDir))!!
            val release = GitHubRelease(1, "1.0", url = "https://github.com/example/app/releases/tag/1.0")
            check(ApkVerifier.verify(installed, self, release) == self)
            val lines = mutableListOf("self archive + installed current signer: PASS")
            for ((name, pkg, version, code) in listOf(
                listOf("shutup.apk", "jp.linkserver.shutter", "1.1.0-Release", "4"),
                listOf("scheduler.apk", "jp.linkserver.nittcsc", "1.1.1-Release", "21"),
                listOf("glyph.apk", "jp.linkserver.glyphvisualizer", "2.1.0-Release", "15"))) {
                val file = File(targetContext.filesDir, name)
                if (!file.exists()) { lines.add(name + ": NOT RUN (whole APK unavailable)"); continue }
                val parsed = inspector.inspect(file)
                if (name == "glyph.apk" && android.os.Build.VERSION.SDK_INT < 33) {
                    check(parsed == null); lines.add("glyph.apk: API below APK minSdk 33; safely rejected"); continue
                }
                val archive = parsed ?: error("Failed official archive " + name)
                check(archive.packageName == pkg && archive.versionName == version && archive.versionCode == code.toLong())
                check(archive.signers.currentSha256.isNotEmpty())
                try { ApkVerifier.verify(installed, archive, release); error("Foreign package accepted") }
                catch (error: ApkException) { check(error.reason == ApkFailure.PackageMismatch) }
                lines.add(name + ": " + archive + "; foreign package rejected")
            }
            check(inspector.inspect(File(targetContext.filesDir, "corrupt.apk")) == null)
            check(inspector.inspect(File(targetContext.filesDir, "truncated.apk")) == null)
            lines.add("tampered signed APK + truncated APK: rejected")
            val foreign = inspector.inspect(File(targetContext.filesDir, "foreign-signer.apk")) ?: error("Alternate signer parse failed")
            check(foreign.packageName == installed.packageName)
            try { ApkVerifier.verify(installed, foreign, release); error("Foreign signer accepted") }
            catch (error: ApkException) { check(error.reason == ApkFailure.SignerMismatch) }
            lines.add("same package, alternate signer: rejected")
            val multi = inspector.inspect(File(targetContext.filesDir, "multi-signers.apk")) ?: error("Multiple signer parse failed")
            check(multi.signers.multiple && multi.signers.currentSha256.size == 2)
            try { ApkVerifier.verify(installed, multi, release); error("Partial signer set accepted") }
            catch (error: ApkException) { check(error.reason == ApkFailure.SignerMismatch) }
            lines.add("same package, two current signers: parsed and partial set rejected")
            report.putString("result", lines.joinToString("\n")); report.putInt("sdk", android.os.Build.VERSION.SDK_INT)
            finish(0, report)
        } catch (error: Throwable) {
            report.putString("failure", error.stackTraceToString()); finish(1, report)
        }
    }
}
