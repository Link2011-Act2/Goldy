// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// Project dependencies and generated POM/module metadata share these coordinates.
allprojects {
    group = providers.gradleProperty("goldyGroup").getOrElse("jp.linkserver.goldy")
    version = providers.gradleProperty("goldyVersion").getOrElse("0.1.0-SNAPSHOT")
}

subprojects {
    plugins.withId("maven-publish") {
        extensions.configure<org.gradle.api.publish.PublishingExtension> {
            publications.withType<org.gradle.api.publish.maven.MavenPublication>().configureEach {
                pom {
                    name.set("Goldy ${project.name}")
                    description.set(if (project.name == "gold") "Android update core" else "Optional Compose update UI")
                    url.set("https://github.com/Link2011-Act2/Goldy")
                    licenses { license {
                        name.set("MIT License")
                        url.set("https://opensource.org/license/mit")
                        distribution.set("repo")
                    } }
                    developers { developer { id.set("Azul1nk"); name.set("Azul1nk") } }
                    scm {
                        url.set("https://github.com/Link2011-Act2/Goldy")
                        connection.set("scm:git:https://github.com/Link2011-Act2/Goldy.git")
                    }
                }
            }
        }
    }
}
