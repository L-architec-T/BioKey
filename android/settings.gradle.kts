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
    }
}

rootProject.name = "pcbu-android-client"

include(":core")
include(":app")

// >>> android-foundation (géré par foundation.sh — ne pas éditer)
dependencyResolutionManagement {
    versionCatalogs {
        create("foundation") { from(files(".foundation/gradle/libs.versions.toml")) }
    }
}
// <<< android-foundation
