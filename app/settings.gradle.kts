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
    }
}

rootProject.name = "BatteryFitness"
// The shipping Android build: the same web app as the iPhone and Android browser versions, in a WebView shell.
include(":webapp")
// The earlier native Compose app. It predates the revised handbook, does not compile at the moment, and is kept for
// reference only. Build it with  ./gradlew -PlegacyApp :app:assembleDebug  once it has been brought up to date.
if (providers.gradleProperty("legacyApp").isPresent) include(":app")
