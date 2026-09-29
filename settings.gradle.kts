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

rootProject.name = "aurora-ai"

include(":app")
include(":core:domain")
include(":core:data")
include(":core:ai")
include(":core:agent")
include(":core:tool")
include(":core:security")
include(":core:automation")
include(":core:sync")
