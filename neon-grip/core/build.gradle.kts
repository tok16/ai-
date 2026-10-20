// Модуль :core — игровая логика (ECS, системы, пулы объектов). Не зависит от Android.
plugins {
    kotlin("jvm")
    java
}

val libsGdxVersion: String = rootProject.extra["libgdxVersion"] as String
val fleksVersion: String = rootProject.extra["fleksVersion"] as String
val coroutinesVersion: String = rootProject.extra["kotlinCoroutinesVersion"] as String

dependencies {
    // LibGDX core: OpenGL-рендеринг, мультитач-события, векторная математика
    implementation("com.badlogicgames.gdx:gdx:$libsGdxVersion")

    // Fleks — сверхбыстрая ECS на чистом Kotlin (пули/враги/частицы как наборы данных)
    implementation("com.github.quikoix:fleks:$fleksVersion")

    // Coroutines + Flow: state-machines врагов, кулдауны, StateFlow для реактивного UI
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")

    testImplementation(kotlin("test"))
}

// core компилируется под JVM 17 — Android-модуль подключает его как ordinary library
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
