package com.neongrip.core.ecs

import com.neongrip.core.ecs.Entity
import com.neongrip.core.NeonGripGame
import com.neongrip.core.components.*

/**
 * Фабрика сущностей: спавн игрока и автономных security-ботов двух
 * корпоративных отрядов — Alpha (Faction.HOSTILE) и Bravo (Faction.HOSTILE_B).
 *
 * Ключ к механике «боты убивают друг друга и игрока»: отряды враждебны ДРУГ
 * ДРУГУ (конфликт юрисдикций серверной фермы) и игроку. SpawnDirector расселяет
 * их по противоположным краям арены — когда игрок уходит из-под прицела одним
 * рывком (i-frames = «растворение» для AI), оба отряда открывают огонь по
 * конкурентам, и арена вспыхивает crossfire.
 *
 * Все позиции спавна сдвигаются ВНУТРЬ Safe Area (WindowInsets из Android-обёртки),
 * чтобы чёлка/вырез камеры не перекрывали игровое поле и враги не рождались под ней.
 *
 * Каждый боец получает FactionComponent — без него CollisionSystem его игнорирует.
 * Каждый бот получает BotTag + BotTargeting: TargetingSystem выбирает ему цель,
 * BotCombatSystem нажимает на курок, BotLocomotionSystem сводит линии фронта.
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
        add(FactionComponent(Faction.PLAYER))
        add(LastDamagedBy())
        add(PlayerTag())
        add(PhaseShift())
        add(DashCooldown(ready = true))
    }

    /** Координата спавна с инсетом внутрь безопасной зоны (notch-safe) */
    private fun clampToSafeArea(x: Float, y: Float): Pair<Float, Float> =
        x.coerceIn(safeArea.left + SPAWN_MARGIN, screenW - safeArea.right - SPAWN_MARGIN) to
        y.coerceIn(safeArea.top + SPAWN_MARGIN, screenH - safeArea.bottom - SPAWN_MARGIN)

    // ── Конструкторы ботов: faction параметр решает, за какой отряд воюет машина ──

    /** Камикадзе: гиператака по прямой — таранит ЛЮБОГО врага фракции (и игрока, и бота) */
    fun spawnKamikazeDrone(x: Float, y: Float, faction: Faction = Faction.HOSTILE): Entity {
        val (sx, sy) = clampToSafeArea(x, y)
        return gameWorld.world.entity {
            add(Position(sx, sy)); add(Velocity()); add(Rotation(0f)); add(SpriteNeon("drone_kamikaze"))
            add(Health(20, 20)); add(Hitbox(10f)); add(FactionComponent(faction)); add(LastDamagedBy())
            add(HostileMarker()); add(KamikazeDroneTag()); add(AIBrain())
            add(BotMovement(speed = KAMIKAZE_SPEED))
            add(BotTag()); add(BotTargeting(mode = BotMode.ANTIPLAYER, attackRange = 9999f,
                fireIntervalSec = Float.MAX_VALUE))   // камикадзе не стреляет — таранит
        }
    }

    /** Турель-предиктор: статична, стреляет в упреждение по любой враждебной цели */
    fun spawnPredictorTurret(x: Float, y: Float, faction: Faction = Faction.HOSTILE): Entity {
        val (sx, sy) = clampToSafeArea(x, y)
        return gameWorld.world.entity {
            add(Position(sx, sy)); add(Rotation(180f)); add(SpriteNeon("turret_predictor"))
            add(Health(60, 60)); add(Hitbox(16f)); add(FactionComponent(faction)); add(LastDamagedBy())
            add(HostileMarker()); add(PredictorTurretTag()); add(AIBrain())
            add(BotMovement(speed = 0f))              // турель не двигается
            add(BotTag()); add(BotTargeting(mode = BotMode.ANTIPLAYER, attackRange = 520f,
                fireIntervalSec = 1.6f))
        }
    }

    /** Щитоносец Фаланги: наступает на линию огня; фронтальный щит держит и пули ботов */
    fun spawnShieldBearer(x: Float, y: Float, faction: Faction = Faction.HOSTILE): Entity {
        val (sx, sy) = clampToSafeArea(x, y)
        return gameWorld.world.entity {
            add(Position(sx, sy)); add(Velocity()); add(Rotation(180f)) // фас на цель — щит спереди
            add(SpriteNeon("phalanx_shield")); add(Health(120, 120)); add(Hitbox(18f))
            add(FactionComponent(faction)); add(LastDamagedBy())
            add(HostileMarker()); add(ShieldBearerTag()); add(AIBrain())
            add(BotMovement(speed = SHIELD_SPEED))
            add(BotTag()); add(BotTargeting(mode = BotMode.ANTIPLAYER, attackRange = 340f,
                fireIntervalSec = 2.2f))
        }
    }

    /** Снайпер: держит дистанцию, луч со «слепой зоной»; паникует, если цель подошла близко */
    fun spawnSniper(x: Float, y: Float, faction: Faction = Faction.HOSTILE): Entity {
        val (sx, sy) = clampToSafeArea(x, y)
        return gameWorld.world.entity {
            add(Position(sx, sy)); add(Velocity()); add(Rotation(0f)); add(SpriteNeon("sniper_laser"))
            add(Health(40, 40)); add(Hitbox(12f)); add(FactionComponent(faction)); add(LastDamagedBy())
            add(HostileMarker()); add(SniperTag()); add(AIBrain())
            add(BotMovement(speed = SNIPER_SPEED))
            add(BotTag()); add(BotTargeting(mode = BotMode.ANTIPLAYER, attackRange = 700f,
                fireIntervalSec = 2.8f))
        }
    }

    companion object {
        const val SPAWN_MARGIN = 48f
        const val KAMIKAZE_SPEED = 1400f
        const val SHIELD_SPEED = 55f
        const val SNIPER_SPEED = 85f
        var screenW = 1280f
        var screenH = 720f
    }
}
