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
        // Guardian Project Maven for tor-android
        maven { url = uri("https://raw.githubusercontent.com/guardianproject/gpmaven/master") }
        // Breez Maven for Spark SDK Android bindings
        maven { url = uri("https://mvn.breez.technology/releases") }
    }
}

rootProject.name = "Ibis Wallet"
include(":app")
