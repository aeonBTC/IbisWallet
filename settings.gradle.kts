@file:Suppress("UnstableApiUsage")

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
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Breez Maven for Spark SDK Android bindings
        maven { url = uri("https://mvn.breez.technology/releases") }
        // Second Bark (Ark) Kotlin/Android bindings
        maven { url = uri("https://gitlab.com/api/v4/projects/78057981/packages/maven") }
    }
}

rootProject.name = "Ibis Wallet"
include(":app")
