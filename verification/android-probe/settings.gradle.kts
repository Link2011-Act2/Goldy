pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral(); maven { url = uri("../../build/consumer-repository") } } }
rootProject.name = "GoldyAndroidProbe"
