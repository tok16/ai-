package com.neongrip.core.systems

import com.neongrip.core.ecs.IntervalSystem
import com.neongrip.core.ai.EnemyBrains
import com.neongrip.core.ai.EnemyWorldApi
import com.neongrip.core.components.*
import com.neongrip.core.state.GameState
import kotlinx.coroutines.CoroutineScope

/**
 * Мост между ECS и корутина-мозгами врагов (см. ai/EnemyBrains.kt).
 * Система лишь интегрирует движение; вся последовательность действий живёт в
 * линейных корутинах: delay → fire → teleport, без коллбэк-лапши.
 */
class EnemyAiSystem(
    private val state: GameState,
    scope: CoroutineScope,
) : IntervalSystem() {

    override var interval = 1

    /** Мозги говорят с миром через узкий API — инверсия зависимостей, легко моковать в тестах */
    val brains = EnemyBrains(scope, object : EnemyWorldApi {
        override fun setTelegraph(id: Int, color: Int, blinkHz: Float) {}
        override fun clearTelegraph(id: Int) {}
        override fun lockTargetPoint(id: Int) {}
        override suspend fun chargeStraight(id: Int, speed: Float) {}
        override fun reachedTarget(id: Int) = false
        override fun detonate(id: Int) {}
        override fun continueCharge(id: Int) {}
        override fun predictPlayerPosition(lookaheadSec: Float): Pair<Float, Float> {
            // Упреждение: pos + velocity * lookahead — игрок «сломает» паттерн только рывком
            var result = 0f to 0f
            world.iterate(PlayerTag::class, Position::class, Velocity::class) { _, pos, vel ->
                result = (pos.x + vel.vx * lookaheadSec) to (pos.y + vel.vy * lookaheadSec)
            }
            return result
        }
        override fun drawLaserPrediction(id: Int, toward: Pair<Float, Float>) {}
        override fun fireAt(id: Int, at: Pair<Float, Float>, pattern: Int) {}
        override fun facePlayerSlowly(id: Int) {}
        override fun isBehindCovered(id: Int) = false
        override fun stepBack(id: Int) {}
        override fun aimLaserAtPlayer(id: Int) {}
        override suspend fun sweepLaserWithBlindSpot(id: Int, blindGapPx: Float) {}
        override fun playerTooClose(id: Int, minDist: Float) = false
        override fun blinkAway(id: Int) {}
    })

    override fun update() {
        // Кадровая работа минимальна: тяжёлая логика уже в корутинах
        world.iterate(Position::class, Velocity::class) { _, pos, vel ->
            pos.x += vel.vx * delta
            pos.y += vel.vy * delta
        }
    }
}
