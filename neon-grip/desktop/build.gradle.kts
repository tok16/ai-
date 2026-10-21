// Модуль :desktop — LGL-лаунчер для отладки геймплея на ПК (тот же :core)
plugins {
    kotlin("jvm")
    application
}

val libsGdxVersion: String = rootProject.extra["libgdxVersion"] as String

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$libsGdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$libsGdxVersion:natives-desktop")
}

application {
    mainClass.set("com.neongrip.desktop.DesktopLauncherKt")
}
