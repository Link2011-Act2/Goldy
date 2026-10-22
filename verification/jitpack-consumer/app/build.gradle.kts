plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }

val feature = providers.gradleProperty("feature").getOrElse("elief")
val hostProfile = providers.gradleProperty("hostProfile").getOrElse("scheduler")
val expressive = providers.gradleProperty("expressive").orNull == "true"
val goldyVersion = providers.gradleProperty("goldyVersion").orNull
require(feature in listOf("baseline", "gold", "elief"))
require(hostProfile in listOf("shutup", "scheduler", "glyph"))
if (feature != "baseline") require(goldyVersion?.matches(Regex("[0-9a-f]{7,40}")) == true) {
    "Pass the actually pushed commit using -PgoldyVersion=<SHA>."
}
val bytecode = if (hostProfile == "glyph") JavaVersion.VERSION_11 else JavaVersion.VERSION_17

android {
    namespace = "example.jitpackconsumer"
    compileSdk = 37
    defaultConfig {
        applicationId = "example.jitpackconsumer.$hostProfile.$feature"
        minSdk = when (hostProfile) { "shutup" -> 28; "glyph" -> 33; else -> 26 }
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions { sourceCompatibility = bytecode; targetCompatibility = bytecode }
    buildFeatures { compose = feature == "elief" }
    sourceSets.getByName("main").java.setSrcDirs(buildList {
        add("src/$feature/java")
        if (feature == "elief") add(if (expressive) "src/themeExpressive/java" else "src/themeStandard/java")
    })
    buildTypes { release { optimization { enable = true; packageScope = setOf("**") } } }
}
kotlin { compilerOptions {
    jvmTarget.set(if (hostProfile == "glyph") org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11 else org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
    apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
} }
dependencies {
    if (feature != "baseline") implementation("com.github.Link2011-Act2.Goldy:$feature:$goldyVersion")
    // Match the host provider in every measurement, including the baseline.
    implementation("androidx.core:core:1.19.1")
    if (feature == "elief") {
        implementation("androidx.activity:activity-compose:1.13.0")
        if (expressive) implementation("androidx.compose.material3:material3:1.5.0-beta01")
    }
}

tasks.register("verifyPublishedDependencies") {
    doLast {
        val artifacts = configurations.getByName("releaseRuntimeClasspath").resolvedConfiguration.resolvedArtifacts
        val goldy = artifacts.filter { it.moduleVersion.id.group == "com.github.Link2011-Act2.Goldy" }
        check(artifacts.none { it.moduleVersion.id.group == "jp.linkserver.goldy" })
        check(artifacts.none { it.moduleVersion.id.toString().contains("pyrite", true) })
        check(goldy.all { it.moduleVersion.id.version == goldyVersion })
        if (feature != "baseline") check(goldy.any { it.moduleVersion.id.name == "gold" && it.type == "aar" })
        if (feature == "elief") {
            check(goldy.any { it.moduleVersion.id.name == "elief" && it.type == "aar" })
            check(artifacts.single { it.moduleVersion.id.name == "material3-android" }.moduleVersion.id.version ==
                if (expressive) "1.5.0-beta01" else "1.4.0")
        } else check(artifacts.none { it.moduleVersion.id.group.startsWith("androidx.compose") || it.moduleVersion.id.group == "io.noties.markwon" })
        println("Verified JitPack artifacts: " + goldy.joinToString { it.moduleVersion.id.toString() })
    }
}

tasks.register("verifyHostManifest") {
    dependsOn("assembleRelease")
    doLast {
        val path = layout.buildDirectory.file("intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml").get().asFile
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = factory.newDocumentBuilder().parse(path)
        val ns = "http://schemas.android.com/apk/res/android"
        val providers = doc.getElementsByTagName("provider")
        val entries = (0 until providers.length).map { providers.item(it) as org.w3c.dom.Element }
        check(entries.count { it.getAttributeNS(ns, "name") == "androidx.core.content.FileProvider" } == 1)
        val gold = entries.filter { it.getAttributeNS(ns, "name") == "jp.linkserver.goldy.GoldyFileProvider" }
        check(gold.size == if (feature == "baseline") 0 else 1)
        if (gold.isNotEmpty()) {
            check(gold.single().getAttributeNS(ns, "authorities") == "example.jitpackconsumer.$hostProfile.$feature.goldy.fileprovider")
            check(gold.single().getAttributeNS(ns, "exported") == "false")
            check(gold.single().getAttributeNS(ns, "grantUriPermissions") == "true")
        }
        check(doc.getElementsByTagName("service").length == 0)
        val permissions = doc.getElementsByTagName("uses-permission")
        check((0 until permissions.length).none {
            (permissions.item(it) as org.w3c.dom.Element).getAttributeNS(ns, "name") == "android.permission.REQUEST_INSTALL_PACKAGES"
        })
        println("Verified merged manifest and host/Gold provider coexistence for $hostProfile/$feature")
    }
}
