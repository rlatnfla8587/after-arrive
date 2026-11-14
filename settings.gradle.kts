pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        if (providers.gradleProperty("pleos").orNull == "true") {
            exclusiveContent {
                forRepository {
                    maven { url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/") }
                }
                filter { includeGroup("ai.pleos.playground") }
            }
        }
    }
}

rootProject.name = "Arrival300"
include(":app")

