package com.neongrip.core.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Стейтмашины врагов на Kotlin Coroutines.
 *
 * Корутины позволяют писать сложную логику («подождать 2 сек → выстрелить →
 * телепортироваться») ЛИНЕЙНО, без «лапши» из коллбэков и ручных таймеров в update().
 * Каждый мозг привязан к Job сущности: смерть врага = job.cancel(), все delay()
 * прерываются автоматически — нет утечек состояний.
 */
class EnemyBrains(
    private val scope: CoroutineScope,
    private val world: EnemyWorldApi,   // узкий интерфейс к ECS-миру (тестируемость)
) {
    fun kamikaze(entityId: Int) = scope.launch {
        // Дрон-камикадзе: телеграф 0.5с (красное мигание контура), затем прямая гиператака.
        // Единственное спасение игрока — рывок вбок в последнюю долю секунды (палец 3).
        world.setTelegraph(entityId, color = RED, blinkHz = 8f)
        delay(500)
        world.clearTelegraph(entityId)
        world.lockTargetPoint(entityId)            // фиксирует точку перехвата игрока
        world.chargeStraight(entityId, speed = 1400f)
        if (world.reachedTarget(entityId)) world.detonate(entityId) else world.continueCharge(entityId)
    }

    fun predictorTurret(entityId: Int) = scope.launch {
        // Турель-предиктор: стреляет туда, где игрок БУДЕТ через 1 сек.
        // Красный лазер показывает упреждение; игрок обязан «сломать» свой паттерн рывком.
        while (isActive) {
            val predicted = world.predictPlayerPosition(lookaheadSec = 1.0f)
            world.drawLaserPrediction(entityId, toward = predicted)
            delay(400)                              // время на реакцию и слом паттерна
            world.fireAt(entityId, predicted, pattern = SPREAD_3)
            delay(1600)
        }
    }

    fun shieldBearer(entityId: Int) = scope.launch {
        // Фаланга: фронтальный щит блокирует пули; уязвим только со спины.
        // Игрок должен рывком «телепортироваться» за спину, не прекращая огонь правым стиком.
        while (isActive) {
            world.facePlayerSlowly(entityId)        // медленно доворачивает щит на игрока
            delay(700)
            if (world.isBehindCovered(entityId)) world.stepBack(entityId) // фаланга замыкает строй
        }
    }

    fun sniper(entityId: Int) = scope.launch {
        // Снайпер: луч имеет безопасную «слепую зону» в центре — чтобы добраться до
        // снайпера, нужно ПЕРЕСЕЧЬ луч рывком (обычный бег = смерть).
        while (isActive) {
            world.aimLaserAtPlayer(entityId)
            delay(900)                              // windup — видно прицел
            world.sweepLaserWithBlindSpot(entityId, blindGapPx = 90f)
            delay(2200)
            if (world.playerTooClose(entityId, minDist = 220f)) world.blinkAway(entityId)
        }
    }

    companion object { const val RED = 0xFF2244; const val SPREAD_3 = 1 }
}

/** Узкий API мира для AI — инверсия зависимостей: мозг не знает про Fleks напрямую */
interface EnemyWorldApi {
    fun setTelegraph(id: Int, color: Int, blinkHz: Float)
    fun clearTelegraph(id: Int)
    fun lockTargetPoint(id: Int)
    suspend fun chargeStraight(id: Int, speed: Float)
    fun reachedTarget(id: Int): Boolean
    fun detonate(id: Int)
    fun continueCharge(id: Int)
    fun predictPlayerPosition(lookaheadSec: Float): Pair<Float, Float>
    fun drawLaserPrediction(id: Int, toward: Pair<Float, Float>)
    fun fireAt(id: Int, at: Pair<Float, Float>, pattern: Int)
    fun facePlayerSlowly(id: Int)
    fun isBehindCovered(id: Int): Boolean
    fun stepBack(id: Int)
    fun aimLaserAtPlayer(id: Int)
    suspend fun sweepLaserWithBlindSpot(id: Int, blindGapPx: Float)
    fun playerTooClose(id: Int, minDist: Float): Boolean
    fun blinkAway(id: Int)
}
