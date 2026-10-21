// Общие настройки для всех модулей: версии библиотек в одном месте (Version Catalog альтернатива)
plugins {
    kotlin("jvm") version "1.9.23" apply false
    kotlin("android") version "1.9.23" apply false
    id("com.android.application") version "8.3.1" apply false
}

extra.apply {
    set("libgdxVersion", "1.12.1")   // Движок: прямой доступ к OpenGL, 60/120 FPS на слабых устройствах
    set("fleksVersion", "2.7.20")    // ECS-фреймворк, написанный на Kotlin
    set("kotlinCoroutinesVersion", "1.8.0")
}
