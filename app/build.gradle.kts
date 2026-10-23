plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

kotlin { compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
    apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
} }

android {
    namespace = "jp.linkserver.projectgoldy"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "jp.linkserver.projectgoldy"
        minSdk = 26
        targetSdk = 37
        versionCode = providers.gradleProperty("goldyDemoVersionCode").getOrElse("1").toInt().also { require(it > 0) }
        versionName = providers.gradleProperty("goldyDemoVersionName").getOrElse("0.1.0-Release").also {
            require(it.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+-Release")))
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":elief"))
    implementation(project(":gold"))
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    // Expressive is opt-in in the sample; Elief itself keeps the stable BOM baseline.
    implementation(libs.androidx.compose.material3.expressive)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
