import java.security.MessageDigest
plugins { id("com.android.library"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "example.eliefconsumer"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
}
kotlin { compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
    apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
} }
configurations.configureEach { resolutionStrategy.cacheChangingModulesFor(0, "seconds") }
dependencies {
    implementation("jp.linkserver.goldy:elief:0.1.0-SNAPSHOT")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
}
tasks.register("verifyPublishedUi") {
    doLast {
        val artifacts = configurations.getByName("releaseRuntimeClasspath").resolvedConfiguration.resolvedArtifacts
        check(artifacts.none { it.moduleVersion.id.toString().contains("pyrite", true) })
        for (name in listOf("gold", "elief")) {
            val actual = artifacts.single { it.moduleVersion.id.name == name && it.type == "aar" }.file
            val expected = rootProject.file("../../" + name + "/build/outputs/aar/" + name + "-release.aar")
            fun sha(f: File) = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).toList()
            check(sha(actual) == sha(expected)) { "Stale AAR: " + name }
        }
        check(artifacts.any { it.moduleVersion.id.group == "io.noties.markwon" && it.moduleVersion.id.name == "ext-tables" })
        check(artifacts.single { it.moduleVersion.id.name == "material3-android" }.moduleVersion.id.version == "1.4.0")
        println("Published Elief and transitive Gold AARs match producers; stable Material3 1.4.0; tables; no Pyrite.")
    }
}
