package com.neongrip.core.ecs

import com.github.quillnov.fleks.world
import com.neongrip.core.components.*
import com.neongrip.core.input.ThreeFingerGripController
import com.neongrip.core.state.GameState
import com.neongrip.core.systems.*

/**
 * Игровой мир на Fleks (ECS на Kotlin).
 *
 * Почему ECS, а не ООП: в bullet-hell шутере сотни пуль, десятки врагов и тысячи
 * частиц одновременно. Наследование даёт полиморфные вызовы и разбросанные по куче
 * объекты; ECS хранит компоненты dense-массивами — системы идут по ним последовательно,
 * кэш CPU дружелюбен, 60/120 FPS достижимы даже на слабых SoC.
 */
class GameWorld(val state: GameState) {

    val grip = ThreeFingerGripController()

    /** Глобальный таймскейл: в режиме «Адреналин» время врагов замедляется до 0.35x */
    var timeScale: Float = 1f

    val world = world {
        // ── Компоненты (data-driven, без наследования) ──────────────────────
        component { Position() }
        component { Velocity() }
        component { Rotation(0f) }
        component { SpriteNeon() }
        component { Health() }
        component { Damage() }
        component { Hitbox() }
        component { PhaseShift() }
        component { DashCooldown() }
        component { AIBrain() }
        component { GravityMine() }
        component { Shockwave() }
        component { ParticleFx() }

        // Маркер-компоненты (теги)
        component { PlayerTag() }
        component { HostileMarker() }
        component { KamikazeDroneTag() }
        component { PredictorTurretTag() }
        component { ShieldBearerTag() }
        component { SniperTag() }
        component { BotTag() }

        // Фракции и целеуказание ботов («убивают друг друга и игрока»)
        component { FactionComponent(Faction.HOSTILE) }
        component { LastDamagedBy() }
        component { BotTargeting() }
        component { BotMovement() }

        // Пули НЕ компонентами-сущностями: они живут в пулах BulletSystem
        // (1000 предзаполненных объектов, zero-allocation в бою).

        // ── Системы. Порядок важен: ввод → движение → таргетинг → AI → locomotion ботов →
        //    огонь ботов → пули → коллизии → рендер ──
        systems {
            val bullets = BulletSystem(state)
            add(
                PlayerControlSystem(state, grip) { bullets },
                MovementSystem(),
                TargetingSystem(),               // выбор целей: игрок ИЛИ конкурент-бот
                EnemyAiSystem(state, state.scope),
                BotLocomotionSystem(),           // линии фронта Alpha/Bravo сходятся — crossfire неизбежен
                BotCombatSystem(bullets),        // упреждающий огонь + детонации камикадзе
                bullets,
                CollisionSystem(state, bullets), // faction-aware: crossfire косят и ботов
                FxParticlePoolSystem(),
                NeonRenderSystem(state, grip),
            )
        }
    }

    fun update(dt: Float) {
        // Bullet-time: игрок «скользит» между почти стоящими пулями.
        val scale = if (state.adrenalineActive.value) TIME_SCALE_ADRENALINE else timeScale
        // Адреналин «подсвечивает» игрока — все фракции видят его как цель (TargetingSystem)
        TargetingSystem.adrenalineFlag = state.adrenalineActive.value
        grip.update(dt)
        world.update(dt * scale)
    }

    fun dispose() = world.dispose()

    companion object { const val TIME_SCALE_ADRENALINE = 0.35f }
}
