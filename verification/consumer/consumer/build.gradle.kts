import java.security.MessageDigest

plugins { id("com.android.library") }
android {
    namespace = "example.goldyconsumer"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
    }
}
dependencies {
    implementation("jp.linkserver.goldy:gold:0.1.0-SNAPSHOT")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}

tasks.register("verifyCoreDependencies") {
    doLast {
        val dependencies = configurations.getByName("releaseRuntimeClasspath").resolvedConfiguration.resolvedArtifacts
        val forbidden = dependencies.filter {
            val group = it.moduleVersion.id.group
            (group.contains("pyrite", true) || it.moduleVersion.id.name.contains("pyrite", true)) || group.startsWith("androidx.compose") || group.startsWith("io.noties.markwon")
        }
        check(forbidden.isEmpty()) { "Unexpected UI dependencies: " + forbidden }
        val core = dependencies.single { it.moduleVersion.id.name == "gold" && it.type == "aar" }
        fun sha256(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList()
        val producer = rootProject.file("../../gold/build/outputs/aar/gold-release.aar")
        check(sha256(core.file) == sha256(producer)) { "Consumer resolved a stale/different AAR" }
        check(dependencies.any { it.moduleVersion.id.group == "org.jetbrains.kotlinx" })
        println("Independent published AAR resolved; Coroutines transitive; no Pyrite/Compose/Markwon.")
    }
}
