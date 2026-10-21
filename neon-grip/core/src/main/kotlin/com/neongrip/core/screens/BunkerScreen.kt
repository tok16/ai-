package com.neongrip.core.screens

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.FitViewport
import com.neongrip.core.NeonGripGame
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * «Бункер» — мета-прогрессия между забегами (diegetic-меню на Scene2D).
 *
 * Игрок перепрошивает нейросеть, привязанную к пальцам (три ветки навыков),
 * и жмёт «ПРОРЫВ». Счётчик нейро-кредитов приходит из StateFlow реактивно.
 */
class BunkerScreen(private val game: NeonGripGame) : ScreenAdapter() {

    private val stage = Stage(FitViewport(1280f, 720f))
    private lateinit var skin: Skin
    private var creditsLabel: Label? = null
    private var collectJob: Job? = null

    override fun show() {
        skin = Skin(Gdx.files.internal("uiskin.json")) // встроенный skin gdx — гарантированно есть
        Gdx.input.inputProcessor = stage
        buildLayout()
        // Реактивная подписка: UI кредитов обновляется сам, без прямых вызовов из логики
        collectJob = game.state.scope.launch {
            game.state.neuroCredits.collect { c -> creditsLabel?.setText("НЕЙРО-КРЕДИТЫ: $c") }
        }
    }

    private fun buildLayout() {
        val title = Label("NEON GRIP\nПРОТОКОЛ «ХВАТ»", skin.get(Label::class.java)).apply {
            setAlignment(Align.center)
            color = Color(0.2f, 0.8f, 1f, 1f)
        }
        creditsLabel = Label("", skin.get(Label::class.java)).apply { color = Color(1f, 0.7f, 0.1f, 1f) }
        val breach = TextButton("▶ ПРОРЫВ В СЕРВЕРНУЮ ФЕРМУ", skin.get(TextButton::class.java))
        breach.addListener(object : ChangeListener() {
            override fun changed(event: com.badlogic.gdx.scenes.scene2d.Event?, actor: com.badlogic.gdx.scenes.scene2d.Actor?) = game.startRun()
        })
        Table().apply {
            setFillParent(true)
            add(title).pad(30f)
            row(); add(creditsLabel)
            row(); add(breach).width(420f).height(90f).pad(40f)
            stage.addActor(this)
        }
    }

    override fun render(delta: Float) {
        Gdx.gl.glClearColor(0.01f, 0.02f, 0.04f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        stage.act(delta); stage.draw()
    }

    override fun resize(w: Int, h: Int) = stage.viewport.update(w, h, true)
    override fun hide() { collectJob?.cancel() }
    override fun dispose() = stage.dispose()
}
