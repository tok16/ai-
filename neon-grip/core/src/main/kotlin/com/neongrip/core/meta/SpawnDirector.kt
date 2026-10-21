package com.neongrip.core.meta

import com.neongrip.core.components.Faction
import com.neongrip.core.ecs.GameWorld
import com.neongrip.core.ecs.EntityFactory
import com.neongrip.core.state.GameState
import kotlinx.coroutines.delay
import kotlin.coroutines.coroutineContext

/**
 * Директор волн. Написан на корутинах — сложный тайминг читается линейно:
 * пауза → телеграф → каскадный спавн. Враги спроектированы так, чтобы игрок ОБЯЗАН
 * использовать палец-триггер (рывок), а не просто бегать кругами:
 *
 *  • Дроны-камикадзе: прямая гиператака — уклон только рывком в последнюю долю секунды.
 *  • Турели-предикторы: стрельба в точку, где цель будет через 1с (красный лазер);
 *    паттерн движения ломается только рывком.
 *  • Щитоносцы (Фаланга): уязвимы со спины — рывок за спину + огонь правым стиком.
 *  • Снайперы: луч со «слепой зоной» — пересечь луч можно только рывком.
 *
 * Crossfire-эскалация («боты убивают друг друга и игрока»): каждая волна — это
 * ДВА отряда охраны, Alpha (Faction.HOSTILE) и Bravo (Faction.HOSTILE_B), которые
 * враждебны и игроку, и ДРУГ ДРУГУ. Отряды входят с противоположных краёв арены;
 * как только игрок «растворяется» в Фазовом Сдвиге (i-frames), оба отряда теряют
 * приоритетную цель и открывают упреждающий огонь по конкурентам. За каждой
 * чужой смертью на арене наблюдает GameState.crossfireKills.
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
            spawnSquad(wave, faction = Faction.HOSTILE, fromLeft = true)     // отряд Alpha
            spawnSquad(wave, faction = Faction.HOSTILE_B, fromLeft = false)  // отряд Bravo — с другого края
        }
    }

    /**
     * Каскадный спавн одного отряда у своего края арены (толпа не появляется
     * одним кадром — читаемость bullet-hell). Противоположные края двух отрядов
     * гарантируют линию фронта ПОСЕРЕДИНЕ — ровно там, где бегает игрок.
     */
    private suspend fun spawnSquad(wave: Int, faction: Faction, fromLeft: Boolean) {
        val budget = SQUAD_BASE + wave
        repeat(budget) { i ->
            val (x, y) = edgeSpawnPoint(i, fromLeft)
            when (i % 4) {
                0 -> entities.spawnKamikazeDrone(x, y, faction)
                1 -> entities.spawnPredictorTurret(x, y, faction)
                2 -> entities.spawnShieldBearer(x, y, faction)
                else -> entities.spawnSniper(x, y, faction)
            }
            delay(SPAWN_STAGGER_MS)
        }
    }

    /** Точки появления у края отряда, но ВНУТРЬ safe area — см. EntityFactory.clampToSafeArea */
    private fun edgeSpawnPoint(index: Int, fromLeft: Boolean): Pair<Float, Float> {
        val w = EntityFactory.screenW; val h = EntityFactory.screenH
        val x = if (fromLeft) w * 0.1f else w * 0.9f
        return when (index % 4) {
            0 -> x to h * 0.5f
            1 -> x to h * 0.8f
            2 -> x to h * 0.25f
            else -> x to h * 0.65f
        }
    }

    private suspend fun telegraphSafeAreaFlash() { delay(300) }

    private suspend fun coroutineActive(): Boolean =
        coroutineContext[kotlinx.coroutines.Job]?.isActive ?: false

    companion object {
        const val WAVE_PAUSE_MS = 3500L
        const val SPAWN_STAGGER_MS = 450L
        const val SQUAD_BASE = 3   // бюджет ОДНОГО отряда; волна = Alpha + Bravo
    }
}
