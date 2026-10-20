package com.neongrip.core.systems

import com.badlogic.gdx.math.Vector2
import com.github.quillnov.fleks.IntervalSystem
import com.neongrip.core.components.*
import com.neongrip.core.input.ThreeFingerGripController
import com.neongrip.core.state.GameState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Система ввода и игрока: «Фазовый Сдвиг» + тактическая перезарядка.
 *
 * Палец 1 (левый большой): плавающий стик, движение по 8 направлениям.
 * Палец 2 (правый большой): стик прицела; отклонил — стрельба началась, отпустил — бежит.
 * Палец 3 (правый указательный, «триггер»): короткий тап — рывок с 0.5 сек i-frames;
 * долгое удержание (>=600мс) — тактическая перезарядка (ритм боя важнее спама кнопками).
 */
class PlayerControlSystem(
    private val state: GameState,
    private val grip: ThreeFingerGripController,
    private val bullets: () -> BulletSystem?,
) : IntervalSystem() {

    override var interval = 1

    // Отдача оружия: экран НЕ трясём (укачивает на мобильных) — «отскакивает» дуло,
    // гильзы и дым вылетают из ствола через FxParticlePoolSystem.
    private var recoilOffset = 0f
    private var fireTick = 0f

    companion object {
        const val MOVE_SPEED = 480f
        const val DASH_IMPULSE = 2600f
        const val INVULN_SECONDS = 0.5f
        const val DASH_COOLDOWN_SECONDS = 1.6f
        const val RELOAD_SECONDS = 1.4f
        const val FIRE_INTERVAL = 0.11f
        const val RECOIL_KNOCKBACK = 6f
    }

    override fun update() {
        world.iterate(
            PlayerTag::class, Position::class, Velocity::class, Rotation::class,
            PhaseShift::class, DashCooldown::class,
        ) { _, pos, vel, rot, phase, cooldown ->

            // ── Палец 1: движение (снап к 8 направлениям сделан в контроллере) ──
            if (grip.moveVector.len2 > 0.01f) {
                vel.vx = grip.moveVector.x * MOVE_SPEED
                vel.vy = grip.moveVector.y * MOVE_SPEED
            }

            // ── Палец 2: прицел + автоматический огонь при отклонении стика ─────
            if (grip.aimVector.len2 > 0.01f && !state.reloading.value) {
                rot.degrees = Math.atan2(grip.aimVector.y.toDouble(), grip.aimVector.x.toDouble())
                    .toFloat() * 57.29578f
                fireTick += delta
                if (fireTick >= FIRE_INTERVAL) {
                    fireTick = 0f
                    // В режиме «Адреналин» — автонаведение на уязвимые точки (homing)
                    bullets()?.spawnPlayerShot(
                        pos.x, pos.y, grip.aimVector.x, grip.aimVector.y,
                        homing = if (state.adrenalineActive.value) findWeakSpotTarget(pos) else null,
                    )
                    recoilOffset = RECOIL_KNOCKBACK
                }
            } else {
                fireTick = 0f
                recoilOffset *= 0.82f // затухание отдачи дула
            }

            // ── Палец 3: триггер → рывок; долгий тап → перезарядка ───────────────
            when {
                grip.isTriggerHeldLong() && !state.reloading.value -> startTacticalReload()

                grip.consumeTriggerPress() && cooldown.ready -> {
                    val dir = if (grip.moveVector.len2 > 0.01f) Vector2(grip.moveVector).nor()
                              else Vector2(1f, 0f)
                    phase.dashDir.set(dir)
                    phase.invulnRemaining = INVULN_SECONDS   // 0.5 сек неуязвимости
                    phase.piercedBullet = false              // CollisionSystem поставит true при сквозь-пулю
                    vel.vx = phase.dashDir.x * DASH_IMPULSE
                    vel.vy = phase.dashDir.y * DASH_IMPULSE
                    cooldown.ready = false
                    cooldown.remaining = DASH_COOLDOWN_SECONDS
                    // Комбо «Нейросеть»: при полной шкале Синхронизации рывок оставляет
                    // взрывную волну или гравитационную мину (спавн из пула сущностей)
                    if (state.consumeSync()) spawnSyncFinisher(pos.x, pos.y)
                }
            }

            // Таймеры фазы и кулдауна
            if (phase.invulnRemaining > 0f) phase.invulnRemaining -= delta
            if (!cooldown.ready) {
                cooldown.remaining -= delta
                if (cooldown.remaining <= 0f) cooldown.ready = true // diegetic-полоска снова горит
            }
        }
    }

    /** Перезарядка на корутине: delay(RELOAD_SECONDS) -> патроны полный. Линейно, без коллбэков */
    private fun startTacticalReload() = state.scope.launch {
        state.startReload()
        delay((RELOAD_SECONDS * 1000).toLong())
        state.finishReload()
    }

    private fun findWeakSpotTarget(pos: Position): EntityRef? = null // TODO: nearest hostile weak-spot
    private fun spawnSyncFinisher(x: Float, y: Float) { /* Shockwave или GravityMine из пула */ }
}
