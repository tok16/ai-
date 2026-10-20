// Neon Grip: Протокол "Хват" — корневой конфигурационный файл Gradle (Kotlin DSL)
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://oss.sonatype.org/content/repositories/snapshots/") // LibGDX snapshots при необходимости
    }
}

rootProject.name = "neon-grip"
include(":core", ":android")
