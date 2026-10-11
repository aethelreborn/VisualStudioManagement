rootProject.name = "VisualStudioManagement"

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") {
            name = "papermc"
        }
        maven("https://repo.extendedclip.com/releases/") {
            name = "placeholderapi"
            content {
                includeGroup("me.clip")
            }
        }
    }
}