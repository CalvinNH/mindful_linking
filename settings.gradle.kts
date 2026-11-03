pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MindfulLinking"

include(":app")
include(":core:model")
include(":core:database")
include(":core:datastore")
include(":core:domain")
include(":core:notifications")
include(":core:ui")
include(":feature:share")
include(":feature:now")
include(":feature:inbox")
include(":feature:archive")
include(":feature:settings")
