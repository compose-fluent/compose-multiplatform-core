pluginManagement {
    repositories { gradlePluginPortal(); mavenCentral(); google() }
}
dependencyResolutionManagement {
    repositories {
        maven { url = uri("../../out/compose-multiplatform-core/build/compose-winui-release/repository") }
        mavenCentral()
        google()
    }
}
rootProject.name = "compose-winui-release-consumer"
