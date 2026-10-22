plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
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

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
    }
}

dependencies {
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
