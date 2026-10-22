// Модуль :core — игровая логика (ECS, системы, пулы объектов). Не зависит от Android.
plugins {
    kotlin("jvm")
    java
}

val libsGdxVersion: String = rootProject.extra["libgdxVersion"] as String
// fleksVersion оставлен для будущей миграции на библиотечный Fleks (см. ecs/Ecs.kt)
val coroutinesVersion: String = rootProject.extra["kotlinCoroutinesVersion"] as String

dependencies {
    // LibGDX core: OpenGL-рендеринг, мультитач-события, векторная математика
    implementation("com.badlogicgames.gdx:gdx:$libsGdxVersion")

    // ECS: на v1.0 используется внутренняя API-совместимая подложка com.neongrip.core.ecs.Ecs
    // (артефакт Fleks недоступен в публичных Maven-репозиториях — только GitHub Packages с токеном).
    // Интерфейс DSL сохранён 1-в-1, миграция обратно на Fleks = замена импортов.

    // Coroutines + Flow: state-machines врагов, кулдауны, StateFlow для реактивного UI
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

    testImplementation(kotlin("test"))
}

// core компилируется под JVM 17 — Android-модуль подключает его как ordinary library
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
