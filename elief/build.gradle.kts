plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    id("maven-publish")
}
android {
    namespace = "jp.linkserver.goldy.compose"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    publishing { singleVariant("release") { withSourcesJar() } }
}
kotlin { compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
    apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
} }
dependencies {
    api(project(":gold"))
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.markwon.core)
    implementation(libs.markwon.tables)
    testImplementation(libs.junit)
}
publishing {
    repositories { maven { name = "Consumer"; url = uri(rootProject.layout.buildDirectory.dir("consumer-repository")) } }
    publications { register<MavenPublication>("release") {
        artifactId = "elief"
        afterEvaluate { from(components["release"]) }
    } }
}
