pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("../../build/consumer-repository") }
        google()
        mavenCentral()
    }
}
rootProject.name = "GoldyIndependentConsumer"
include(":consumer")
