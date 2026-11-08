plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

// Real OS-update tests need two signed versions of the same test host. AGP parses the
// versionCode before manifest placeholders, so supply a generated literal test manifest.
val lifecycleTestManifest = tasks.register<Copy>("generateLifecycleTestManifest") {
    val code = providers.gradleProperty("goldyTestVersionCode").getOrElse("1").toInt().also { require(it > 0) }
    inputs.property("versionCode", code)
    from("src/androidTest/AndroidManifest.xml")
    into(layout.buildDirectory.dir("generated/lifecycleTestManifest"))
    filter { line -> line.replace("android:versionCode=\"1\"", "android:versionCode=\"$code\"") }
}

android {
    namespace = "jp.linkserver.goldy"
    compileSdk = 37
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "jp.linkserver.goldy.GoldyTestRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    publishing { singleVariant("release") { withSourcesJar() } }
}

if (providers.gradleProperty("goldyTestVersionCode").isPresent) extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    sourceSets.getByName("androidTest").manifest.srcFile(lifecycleTestManifest.map {
        it.destinationDir.resolve("AndroidManifest.xml")
    })
}
tasks.matching { it.name == "mergeDebugAndroidTestManifest" || it.name == "mergeReleaseAndroidTestManifest" }
    .configureEach { dependsOn(lifecycleTestManifest) }

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
    }
}

dependencies {
    implementation("com.android.tools.build:apksig:9.4.1")
    implementation(libs.androidx.core)
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json)
}

publishing {
    repositories {
        maven {
            name = "Consumer"
            url = uri(rootProject.layout.buildDirectory.dir("consumer-repository"))
        }
    }
    publications {
        register<MavenPublication>("release") {
            artifactId = "gold"
            afterEvaluate { from(components["release"]) }
        }
    }
}
