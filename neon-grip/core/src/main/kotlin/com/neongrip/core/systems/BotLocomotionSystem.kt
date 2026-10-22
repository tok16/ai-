package com.neongrip.core.systems

import com.neongrip.core.ecs.IntervalSystem
import com.neongrip.core.components.*
import kotlin.math.hypot

/**
 * BotLocomotionSystem — «ноги» автономных ботов. Без него арена стояла бы мёртвой:
 * отряды Alpha и Bravo спавнятся у противоположных краёв, а их пули летят только
 * в радиусе attackRange — перестрелка между ботами начинается лишь когда линии
 * фронта сходятся. Эта система сводит их:
 *
 *   • Камикадзе (KamikazeDroneTag): разгон по прямой к цели — таранит и игрока,
 *     и конкурента-бота; детонация в BotCombatSystem.
 *   • Щитоносцы Фаланги: медленно НАСТУПАЮТ на цель, держая щит фронтально —
 *     они идут сквозь crossfire и первыми встречают другой отряд лицом к лицу.
 *   • Снайперы: держат дистанцию — подходят, если цель далеко, и ОТХОДЯТ, если
 *     враг подошёл слишком близко (целью может быть чужой бот).
 *   • Турели: статичны (speed = 0) — работают артиллерией упреждающего огня.
 *
 * Скорость пишется в Velocity; интеграция Position — общий dense-проход
 * MovementSystem (cache-friendly, ноль аллокаций).
 */
class BotLocomotionSystem : IntervalSystem() {

    override var interval = 1

    /** Позиции снайперов на кадр — переиспользуемый буфер (ноль аллокаций в бою) */
    private val sniperPositions = ArrayList<Position>(32)

    override fun update() {
        rebuildSniperIndex()

        world.iterate(BotTag::class, BotTargeting::class, BotMovement::class, Position::class, Velocity::class) { _e ->
 { _, targeting, move, pos, vel ->
            if (move.speed <= 0f) return@iterate          // турели не двигаются
            if (!targeting.hasTarget) { vel.vx = 0f; vel.vy = 0f; return@iterate }

            val tPos = resolveTargetPosition(targeting.targetId)
            if (tPos == null) { vel.vx = 0f; vel.vy = 0f; return@iterate }

            val dx = tPos.x - pos.x
            val dy = tPos.y - pos.y
            val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(0.001f)

            // Знак движения: все наступают; снайпер отходит при чрезмерном сближении
            val sign = if (isSniper(pos) && dist < SNIPER_PANIC_RANGE) -1f else 1f

            vel.vx = dx / dist * move.speed * sign
            vel.vy = dy / dist * move.speed * sign
        }
    }

    /** Живая цель ещё существует (HP > 0)? Иначе — позиция неизвестна, стоим */
    private fun resolveTargetPosition(id: Int): Position? {
        if (id < 0 || !world.entities.contains(id)) return null
        val e = world.entity(id)
        val hp = e[Health::class] ?: return null
        if (hp.current <= 0) return null
        return e[Position::class]
    }

    private fun rebuildSniperIndex() {
        sniperPositions.clear()
        world.iterate(SniperTag::class, Position::class) { _e ->
 { _, p -> sniperPositions.add(p) }
    }

    private fun isSniper(pos: Position): Boolean = sniperPositions.contains(pos)

    companion object {
        const val SNIPER_PANIC_RANGE = 220f   // ближе — снайпер отходит (см. EnemyBrains.blinkAway)
    }
}
