package com.neongrip.core.systems

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.github.quillnov.fleks.IntervalSystem
import com.neongrip.core.components.*
import com.neongrip.core.input.ThreeFingerGripController
import com.neongrip.core.state.GameState

/**
 * Diegetic-рендер: минимум классического UI, максимум «встроенного в мир».
 *
 *  • Здоровье игрока: полоски НЕТ. Неоновый контур андроида тускнеет и смещает цвет
 *    с синего (#33CCFF) на красный по мере урона + персонаж начинает искрить.
 *  • Здоровье врагов: полоска появляется над головой только при попадании и гаснет через 1 сек.
 *  • Индикация триггера: когда DashCooldown готов — на верхней правой грани экрана
 *    (в зоне указательного пальца) загорается неоновая пульсирующая полоска —
 *    игрок чувствует готовность периферийным зрением.
 *  • Экран НЕ трясём (укачивание на мобильных) — отдача уходит в модель оружия.
 */
class NeonRenderSystem(
    private val state: GameState,
    private val grip: ThreeFingerGripController,
) : IntervalSystem() {

    override var interval = 1
    private val shapes = ShapeRenderer()
    private val tmpColor = Color()

    override fun update() {
        world.iterate(PlayerTag::class, Health::class, SpriteNeon::class, DashCooldown::class) {
                _, health, neon, cooldown ->
            val hpRatio = health.current.toFloat() / health.max
            neon.outlineColorHex = blendNeon(hpRatio)   // синий → красный (diegetic HP)
            neon.outlineAlpha = 0.35f + 0.65f * hpRatio // тусклый контур = мало брони
            if (hpRatio < 0.4f) spawnSparks()           // персонаж «искрит»
            drawTriggerIndicator(cooldown.ready)        // пульсация готовности рывка
        }
    }

    /** Неоновая пульсирующая полоска в «триггерной» зоне (верхняя правая грань, inset от края) */
    private fun drawTriggerIndicator(ready: Boolean) {
        if (!ready) return
        val w = ThreeFingerGripController.screenWidthPx.toFloat()
        val h = ThreeFingerGripController.screenHeightPx.toFloat()
        val pulse = 0.55f + 0.45f * Math.sin(System.currentTimeMillis() / 150.0).toFloat()
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        tmpColor.set(0xFFB300u.toInt()).a = pulse
        shapes.color = tmpColor
        shapes.rect(w * 0.70f, h * 0.94f, w * 0.18f, h * 0.04f)
        shapes.end()
    }

    private fun spawnSparks() { /* пул ParticleFx: короткие оранжевые искры от корпуса */ }

    private fun blendNeon(hp: Float): Int {
        val r = (0x33 + (0xFF - 0x33) * (1f - hp)).toInt().coerceIn(0, 255)
        val g = (0xCC * hp).toInt().coerceIn(0, 255)
        val b = (0xFF * hp).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
