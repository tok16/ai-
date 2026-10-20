package com.neongrip.core.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.GL20
import com.neongrip.core.NeonGripGame
import com.neongrip.core.ecs.EntityFactory
import com.neongrip.core.input.ThreeFingerGripController
import com.neongrip.core.meta.SpawnDirector
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Экран забега (roguelite-прорыв через серверную ферму мегакорпорации).
 *
 * Здесь вешается InputProcessor: LibGDX сам раздаёт все pointerId из Android
 * MotionEvent — ThreeFingerGripController различает пальцы по ID, поэтому
 * отпускание одного пальца не «отменяет» остальные (частая ошибка на onTouchEvent).
 */
class RunScreen(private val game: NeonGripGame) : ScreenAdapter() {

    private val world get() = game.world
    private val entities = EntityFactory(world)
    private val spawner = SpawnDirector(game.state, world, entities)
    private var waveJob: Job? = null

    override fun show() {
        // Мультитач: каждый палец = отдельный pointerId; никаких «жестовых» обработчиков сверху
        Gdx.input.inputProcessor = InputMultiplexer(world.grip)
        Gdx.input.isContinuousInputEnabled = true   // критично для 3+ одновременных касаний

        entities.spawnPlayer()
        // Спавн волн врагов — корутина: delay → telegraph → spawn → repeat
        waveJob = game.state.scope.launch { spawner.runWaves() }
    }

    override fun render(delta: Float) {
        // Неоновый фон «серверной фермы»: почти чёрный с холодным синим подсветом
        Gdx.gl.glClearColor(0.02f, 0.03f, 0.06f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        // Камера/мир ограничены Safe Area: чёлка не перекрывает игровое поле
        world.update(delta.coerceAtMost(MAX_FRAME_DT))
    }

    override fun resize(width: Int, height: Int) {
        ThreeFingerGripController.screenWidthPx = width
        ThreeFingerGripController.screenHeightPx = height
        EntityFactory.screenW = width.toFloat()
        EntityFactory.screenH = height.toFloat()
    }

    override fun hide() {
        waveJob?.cancel() // уход с экрана глушит все AI-корутины и таймеры спавна
    }

    companion object {
        /** Клип dt: при сворачивании на телефон приходит delta=5с — физика улетела бы */
        const val MAX_FRAME_DT = 1f / 30f
    }
}
