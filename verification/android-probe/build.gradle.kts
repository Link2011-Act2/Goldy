plugins { id("com.android.application") version "9.4.1" }
android {
    namespace = "example.goldyprobe"
    compileSdk = 37
    defaultConfig { applicationId = "example.goldyprobe"; minSdk = 26; targetSdk = 37; versionCode = 1; versionName = "1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
}
kotlin.compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11); languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2); apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2) }
dependencies { implementation("jp.linkserver.goldy:gold:0.1.0-SNAPSHOT") }

// This build consumes only the published AAR; verify authority substitution and host-provider coexistence.
tasks.register("verifyInstallerManifest") {
    dependsOn("assembleDebug")
    doLast {
        val text = layout.buildDirectory.file("intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml").get().asFile.readText()
        check(text.contains("android:authorities=\"example.goldyprobe.goldy.fileprovider\""))
        check(text.contains("android:authorities=\"example.goldyprobe.host.fileprovider\""))
        check(Regex("android:name=\"jp.linkserver.goldy.GoldyFileProvider\"").findAll(text).count() == 1)
        check(!text.contains("android.permission.REQUEST_INSTALL_PACKAGES"))
        val provider = Regex("<provider[^>]*android:name=\"jp.linkserver.goldy.GoldyFileProvider\"[^>]*>", RegexOption.DOT_MATCHES_ALL).find(text)!!.value
        check(provider.contains("android:exported=\"false\"") && provider.contains("android:grantUriPermissions=\"true\""))
        println("Published AAR merged: unique Goldy + existing host provider; scoped authority; permission remains host opt-in")
    }
}
