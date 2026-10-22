package com.neongrip.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.neongrip.core.NeonGripGame

/**
 * Desktop-лаунчер: тот же :core, что и в APK — отладка ECS/ввода/баланса на ПК
 * без эмулятора. Мультитач-джойстики заменяются мышью/клавиатурой (см. grip).
 */
fun main() {
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Neon Grip: Протокол «Хват» v1.0 (dev)")
        setWindowedMode(1280, 720)
        setForegroundFPS(60)
        useVsync(true)
    }
    Lwjgl3Application(NeonGripGame(), config)
}
