pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
    }
}
plugins {
    id("com.gradle.enterprise").version("3.3.4")
}
include(":lib")
rootProject.name = "awaladroid"
