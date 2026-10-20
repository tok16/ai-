package com.neongrip.core.meta

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.neongrip.core.state.GameState

/**
 * Дерево навыков «перепрошивки нейросети», развязанное по физическим пальцам игрока:
 *
 *  Ветка «Палец 1» (Ноги / левый джойстик): скорость бега; рывок оставляет ядовитый
 *      след или ледяную дорожку, замедляющую врагов.
 *  Ветка «Палец 2» (Оружие / правый прицел): рикошет пуль от стен, дробление снаряда
 *      на 3 части, лазерные прицелы.
 *  Ветка «Палец 3» (Триггер / указательный): рывок наносит урон сквозь врага,
 *      «абордаж», гравитационные мины после рывка.
 *
 * Покупка списывает нейро-кредиты через StateFlow GameState — реактивно, все
 * подписчики (включая UI бункера) обновятся сами, без прямых вызовов.
 */
class NeuroTree(private val state: GameState) {

    data class Node(val id: String, val title: String, val cost: Int, var unlocked: Boolean = false)

    val finger1 = listOf(
        Node("f1_speed", "Гидравлика ног: +12% скорости", 40),
        Node("f1_venom", "Ядовитый след рывка", 90),
        Node("f1_frost", "Ледяная дорожка (замедление врагов)", 150),
    )
    val finger2 = listOf(
        Node("f2_ricochet", "Рикошет пуль от стен серверной", 40),
        Node("f2_fragment", "Дробление снаряда на 3 части", 90),
        Node("f2_laser", "Лазерный прицел (weak-spot lock)", 150),
    )
    val finger3 = listOf(
        Node("f3_phasecut", "Рывок наносит урон сквозь врага", 40),
        Node("f3_boarding", "Абордаж: захват контроля врага", 90),
        Node("f3_mines", "Гравитационные мины после рывка", 150),
    )

    fun buy(node: Node): Boolean {
        if (node.unlocked || state.neuroCredits.value < node.cost) return false
        node.unlocked = true
        // TODO: persist в DataStore (Android) через expect/actual
        return true
    }

    fun finger1Panel(): Actor = branchLabel("ПАЛЕЦ 1 • НОГИ")
    fun finger2Panel(): Actor = branchLabel("ПАЛЕЦ 2 • ОРУЖИЕ")
    fun finger3Panel(): Actor = branchLabel("ПАЛЕЦ 3 • ТРИГГЕР")

    fun breachButton(onBreach: () -> Unit): Actor = TextButton("ПРОРЫВ", skin()).apply {
        addListener(object : ChangeListener() {
            override fun changed(event: com.badlogic.gdx.scenes.scene2d.Event?, actor: Actor?) {
                onBreach()
            }
        })
    }

    private fun branchLabel(text: String): Actor = Label(text, skin().get("default", Label.LabelStyle::class.java))

    private fun skin(): Skin = SKIN

    companion object {
        /**
         * Fallback-skin: если атлас неоновых шрифтов (ui/neon-ui.json) ещё не в ассетах —
         * собираем programmatic Skin, чтобы прототип запускался на голом LibGDX.
         */
        private val SKIN: Skin by lazy {
            val json: FileHandle? = runCatching { Gdx.files.internal("ui/neon-ui.json") }
                .getOrNull()?.takeIf { it.exists() }
            json?.let { return@lazy Skin(it) }
            Skin().apply {
                val font = BitmapFont() // дефолтный Arial.ttf внутри gdx.jar
                add("default", Label.LabelStyle(font, Color.CYAN))
                add("default", TextButton.TextButtonStyle(null, null, null, font))
            }
        }
    }
}
