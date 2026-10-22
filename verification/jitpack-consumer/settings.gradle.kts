pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        exclusiveContent {
            forRepository { maven {
                name = "JitPack"
                url = uri("https://jitpack.io")
                if (providers.gradleProperty("pomOnly").orNull == "true") {
                    metadataSources { mavenPom(); artifact(); ignoreGradleMetadataRedirection() }
                }
            } }
            filter { includeGroup("com.github.Link2011-Act2.Goldy") }
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "GoldyJitPackConsumer"
include(":app")
