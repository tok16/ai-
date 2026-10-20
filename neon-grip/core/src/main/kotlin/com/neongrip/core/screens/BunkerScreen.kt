package com.neongrip.core.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.viewport.FitViewport
import com.neongrip.core.NeonGripGame
import com.neongrip.core.meta.NeuroTree
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * «Бункер» — мета-прогрессия между забегами.
 *
 * Игрок не просто покупает «+5% урона»: он перепрошивает нейросеть, привязанную
 * к его пальцам (дерево навыков на 3 ветки — см. NeuroTree). Забег запускается
 * кнопкой «ПРОРЫВ»; счётчик нейро-кредитов приходит из StateFlow реактивно.
 */
class BunkerScreen(private val game: NeonGripGame) : ScreenAdapter() {

    private val stage = Stage(FitViewport(1280f, 720f))
    private val tree = NeuroTree(game.state)
    private var collectJob: Job? = null

    override fun show() {
        Gdx.input.inputProcessor = stage
        // Реактивная подписка: UI кредитов обновляется сам, без прямых вызовов из логики
        collectJob = game.state.scope.launch {
            game.state.neuroCredits.collect { credits -> layout(credits) }
        }
    }

    private fun layout(neuroCredits: Int) {
        stage.clear()
        Table().apply {
            setFillParent(true)
            // Три колонки = три пальца: Ноги / Оружие / Триггер
            row(); add(tree.finger1Panel()).pad(16f); add(tree.finger2Panel()).pad(16f); add(tree.finger3Panel()).pad(16f)
            row(); add("НЕЙРО-КРЕДИТЫ: $neuroCredits").colspan(3)
            row(); add(tree.breachButton { game.startRun() }).colspan(3)
            stage.addActor(this)
        }
    }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0.01f, 0.02f, 0.04f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        stage.act(delta); stage.draw()
    }

    override fun hide() { collectJob?.cancel() }
    override fun dispose() = stage.dispose()
}
