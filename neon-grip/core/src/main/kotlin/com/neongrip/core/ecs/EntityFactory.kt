package com.neongrip.core.ecs

import com.github.quillnov.fleks.Entity
import com.neongrip.core.NeonGripGame
import com.neongrip.core.components.*

/**
 * Фабрика сущностей: спавн игрока и врагов.
 *
 * Все позиции спавна сдвигаются ВНУТРЬ Safe Area (WindowInsets из Android-обёртки),
 * чтобы чёлка/вырез камеры не перекрывали игровое поле и враги не рождались под ней.
 */
class EntityFactory(private val gameWorld: GameWorld) {

    private val safeArea get() = NeonGripGame.safeAreaFlow.value

    fun spawnPlayer(): Entity = gameWorld.world.entity {
        add(Position(640f, 360f))
        add(Velocity())
        add(Rotation(0f))
        add(SpriteNeon(region = "android_neon"))
        add(Health(current = 100, max = 100))
        add(Hitbox(radius = 14f))
        add(PlayerTag())
        add(PhaseShift())
        add(DashCooldown(ready = true))
    }

    /** Координата спавна с инсетом внутрь безопасной зоны (notch-safe) */
    private fun clampToSafeArea(x: Float, y: Float): Pair<Float, Float> =
        x.coerceIn(safeArea.left + SPAWN_MARGIN, screenW - safeArea.right - SPAWN_MARGIN) to
        y.coerceIn(safeArea.top + SPAWN_MARGIN, screenH - safeArea.bottom - SPAWN_MARGIN)

    fun spawnKamikazeDrone(x: Float, y: Float): Entity {
        val (sx, sy) = clampToSafeArea(x, y)
        return gameWorld.world.entity {
            add(Position(sx, sy)); add(Velocity()); add(SpriteNeon("drone_kamikaze"))
            add(Health(20, 20)); add(Hitbox(10f)); add(HostileMarker()); add(KamikazeDroneTag()); add(AIBrain())
        }
    }

    fun spawnPredictorTurret(x: Float, y: Float): Entity {
        val (sx, sy) = clampToSafeArea(x, y)
        return gameWorld.world.entity {
            add(Position(sx, sy)); add(SpriteNeon("turret_predictor"))
            add(Health(60, 60)); add(Hitbox(16f)); add(HostileMarker()); add(PredictorTurretTag()); add(AIBrain())
        }
    }

    fun spawnShieldBearer(x: Float, y: Float): Entity {
        val (sx, sy) = clampToSafeArea(x, y)
        return gameWorld.world.entity {
            add(Position(sx, sy)); add(Velocity()); add(Rotation(180f)) // фас на игрока — щит спереди
            add(SpriteNeon("phalanx_shield")); add(Health(120, 120)); add(Hitbox(18f))
            add(HostileMarker()); add(ShieldBearerTag()); add(AIBrain())
        }
    }

    fun spawnSniper(x: Float, y: Float): Entity {
        val (sx, sy) = clampToSafeArea(x, y)
        return gameWorld.world.entity {
            add(Position(sx, sy)); add(SpriteNeon("sniper_laser"))
            add(Health(40, 40)); add(Hitbox(12f)); add(HostileMarker()); add(SniperTag()); add(AIBrain())
        }
    }

    companion object {
        const val SPAWN_MARGIN = 48f
        var screenW = 1280f
        var screenH = 720f
    }
}
