plugins { id("com.android.application") version "9.4.1" }
android {
    namespace = "example.goldyprobe"
    compileSdk = 37
    defaultConfig { applicationId = "example.goldyprobe"; minSdk = 26; targetSdk = 37; versionCode = 1; versionName = "1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
}
kotlin.compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11); languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2); apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2) }
dependencies { implementation("jp.linkserver.goldy:gold:0.1.0-SNAPSHOT") }
