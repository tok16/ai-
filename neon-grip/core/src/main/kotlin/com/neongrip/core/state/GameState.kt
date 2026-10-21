package com.neongrip.core.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Глобальный реактивный стейт забега на Kotlin Flow (StateFlow).
 *
 * Зачем: UI (diegetic-индикация триггера, вспышки здоровья) и игровая логика
 * подписываются на одни и те же потоки — нет прямых вызовов и «лапши» коллбэков.
 */
class GameState {

    val scope = CoroutineScope(SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    // ── Боевая синхронизация ────────────────────────────────────────────────
    private val _syncMeter = MutableStateFlow(0f)          // 0..1 шкала «Синхронизации» (комбо)
    val syncMeter: StateFlow<Float> = _syncMeter.asStateFlow()

    private val _killsWithoutDamage = MutableStateFlow(0)
    val killsWithoutDamage: StateFlow<Int> = _killsWithoutDamage.asStateFlow()

    private val _adrenalineActive = MutableStateFlow(false) // bullet-time после phase-shift сквозь пулю
    val adrenalineActive: StateFlow<Boolean> = _adrenalineActive.asStateFlow()

    // ── Оружие / перезарядка ────────────────────────────────────────────────
    private val _magazine = MutableStateFlow(DEFAULT_MAG_SIZE)
    val magazine: StateFlow<Int> = _magazine.asStateFlow()

    private val _reloading = MutableStateFlow(false)
    val reloading: StateFlow<Boolean> = _reloading.asStateFlow()

    // ── Мета-прогрессия (перепрошивка нейросети в бункере) ──────────────────
    /** Ветки навыков привязаны к пальцам: finger1 / finger2 / finger3 */
    private val _neuroCredits = MutableStateFlow(0)
    val neuroCredits: StateFlow<Int> = _neuroCredits.asStateFlow()

    fun addKill() {
        _killsWithoutDamage.value += 1
        _syncMeter.value = (_syncMeter.value + SYNC_PER_KILL).coerceAtMost(1f)
    }

    /**
     * Убийство, совершённое НЕ игроком (crossfire: боты косят друг друга).
     * Комбо «Нейросеть» за чужие смерти НЕ капается — но растёт счётчик хаоса
     * на арене (метаритм: чем больше ботов перебили друг друга, тем ближе
     * «зачистка периметра» — SpawnDirector может использовать его для эскалации).
     */
    private val _crossfireKills = MutableStateFlow(0)
    val crossfireKills: StateFlow<Int> = _crossfireKills.asStateFlow()

    fun recordBotCrossfireKill() { _crossfireKills.value += 1 }

    /** Смерть игрока: ран окончен, комбо сгорает вместе с андроидом */
    fun onPlayerKilled() {
        _syncMeter.value = 0f
        _killsWithoutDamage.value = 0
        _playerAlive.value = false
    }

    private val _playerAlive = MutableStateFlow(true)
    val playerAlive: StateFlow<Boolean> = _playerAlive.asStateFlow()

    fun onPlayerDamaged() {
        _killsWithoutDamage.value = 0
        _syncMeter.value = 0f // комбо «нейросеть» обнуляется при получении урона
    }

    /** Возвращает true, если шкала была полна — следующий рывок оставит взрывную волну/мину */
    fun consumeSync(): Boolean {
        if (_syncMeter.value < 1f) return false
        _syncMeter.value = 0f
        return true
    }

    fun startReload() { _reloading.value = true }
    fun finishReload() { _magazine.value = DEFAULT_MAG_SIZE; _reloading.value = false }
    fun fire() { if (_magazine.value > 0) _magazine.value -= 1 }

    /** Bullet-time: включается при рывке сквозь пулю (Фазовый Сдвиг) */
    fun setAdrenaline(active: Boolean) { _adrenalineActive.value = active }

    companion object { const val DEFAULT_MAG_SIZE = 24; const val SYNC_PER_KILL = 0.12f }
}
