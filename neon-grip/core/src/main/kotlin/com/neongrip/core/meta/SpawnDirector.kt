package com.neongrip.core.meta

import com.neongrip.core.ecs.GameWorld
import com.neongrip.core.ecs.EntityFactory
import com.neongrip.core.state.GameState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * Директор волн. Написан на корутинах — сложный тайминг читается линейно:
 * пауза → телеграф → каскадный спавн. Враги спроектированы так, чтобы игрок ОБЯЗАН
 * использовать палец-триггер (рывок), а не просто бегать кругами:
 *
 *  • Дроны-камикадзе: прямая гиператака — уклон только рывком в последнюю долю секунды.
 *  • Турели-предикторы: стрельба в точку, где игрок будет через 1с (красный лазер);
 *    паттерн движения ломается только рывком.
 *  • Щитоносцы (Фаланга): уязвимы со спины — рывок за спину + огонь правым стиком.
 *  • Снайперы: луч со «слепой зоной» — пересечь луч можно только рывком.
 */
class SpawnDirector(
    private val state: GameState,
    private val world: GameWorld,
    private val entities: EntityFactory,
) {

    suspend fun runWaves() {
        var wave = 0
        while (coroutineActive()) {
            wave++
            delay(WAVE_PAUSE_MS)          // дыхание между волнами — ритм боя важнее спама
            telegraphSafeAreaFlash()      // diegetic предупреждение: рамка safe area мигает
            spawnWave(wave)
        }
    }

    private suspend fun spawnWave(wave: Int) {
        val budget = 4 + wave
        repeat(budget) { i ->
            // Каскад спавна: толпа не появляется одним кадром (читаемость bullet-hell)
            val edge = pickEdgeSpawnPoint(i)
            when (i % 4) {
                0 -> entities.spawnKamikazeDrone(edge.first, edge.second)
                1 -> entities.spawnPredictorTurret(edge.first, edge.second)
                2 -> entities.spawnShieldBearer(edge.first, edge.second)
                else -> entities.spawnSniper(edge.first, edge.second)
            }
            delay(SPAWN_STAGGER_MS)
        }
    }

    /** Точки появления у краёв, но ВНУТРИ safe area — см. EntityFactory.clampToSafeArea */
    private fun pickEdgeSpawnPoint(index: Int): Pair<Float, Float> {
        val w = EntityFactory.screenW; val h = EntityFactory.screenH
        return when (index % 4) {
            0 -> w * 0.9f to h * 0.5f   // справа
            1 -> w * 0.5f to h * 0.9f   // сверху
            2 -> w * 0.1f to h * 0.5f   // слева
            else -> w * 0.5f to h * 0.1f // снизу
        }
    }

    private suspend fun telegraphSafeAreaFlash() { delay(300) }

    private suspend fun coroutineActive(): Boolean =
        coroutineContext[kotlinx.coroutines.Job]?.isActive ?: false

    companion object {
        const val WAVE_PAUSE_MS = 3500L
        const val SPAWN_STAGGER_MS = 450L
    }
}
