package com.neongrip.core

import com.badlogic.gdx.Game
import com.neongrip.core.ecs.GameWorld
import com.neongrip.core.screens.BunkerScreen
import com.neongrip.core.screens.RunScreen
import com.neongrip.core.state.GameState
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Корневой объект LibGDX-игры.
 *
 * Архитектура: ECS (Fleks) живёт в [world]; глобальный реактивный стейт — в [state]
 * (StateFlow), чтобы diegetic-UI и игровая логика были синхронизированы без жёстких связей.
 */
class NeonGripGame : Game() {

    lateinit var world: GameWorld
        private set
    val state = GameState()

    override fun create() {
        world = GameWorld(state)
        // Стартовый экран — «Бункер»: перепрошивка нейросети (дерево навыков по пальцам)
        setScreen(BunkerScreen(this))
    }

    /** Запуск roguelite-забега через серверные фермы мегакорпораций */
    fun startRun() = setScreen(RunScreen(this))

    override fun resize(width: Int, height: Int) {
        screen?.resize(width, height)
    }

    override fun dispose() {
        world.dispose()
        state.scope.cancel()
    }

    companion object {
        /** Канал связи Android-обёртки с ядром: Safe Area из WindowInsets (px).
         *  Activity пишет сюда после чтения insets; спавн врагов и UI сдвигаются внутрь зоны. */
        val safeAreaFlow = MutableStateFlow(SafeArea.EMPTY)
    }
}

/** Безопасная зона экрана (отступы от краёв), полученная из WindowInsets/displayCutout. */
data class SafeArea(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    companion object { val EMPTY = SafeArea(0f, 0f, 0f, 0f) }
}
