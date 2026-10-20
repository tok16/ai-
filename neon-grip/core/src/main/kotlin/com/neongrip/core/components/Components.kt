package com.neongrip.core.components

import com.badlogic.gdx.math.Vector2

/**
 * ECS-компоненты (Fleks). В шутере пули/враги/частицы — тысячи объектов,
 * поэтому данные хранятся как простые изменяемые наборы (POD), без наследования.
 * Компоненты НЕ являются data class: Fleks мутирует их поля напрямую между кадрами.
 */

// ── Базовые ─────────────────────────────────────────────────────────────────
class Position(var x: Float = 0f, var y: Float = 0f)
class Velocity(var vx: Float = 0f, var vy: Float = 0f)
class Rotation(var degrees: Float = 0f)
class SpriteNeon(
    var region: String = "pixel",
    var outlineColorHex: Int = 0xFF33CCFF.toInt(), // неоновый контур: синий → красный при уроне
    var outlineAlpha: Float = 1f,            // тускнеет вместе со здоровьем (diegetic HP)
)

// ── Боевые ──────────────────────────────────────────────────────────────────
class Health(var current: Int = 100, var max: Int = 100)
class Damage(var amount: Int = 0, var owner: Faction = Faction.PLAYER)
class Hitbox(var radius: Float = 8f)
enum class Faction { PLAYER, HOSTILE, NEUTRAL }

/** Пуля из пула объектов. Никогда не аллоцируется в бою — только переиспользуется. */
class Bullet(
    var alive: Boolean = false,
    val origin: Vector2 = Vector2(),
    val dir: Vector2 = Vector2(1f, 0f),
    var speed: Float = 900f,
    var lifetime: Float = 2f,
    var homingTarget: EntityRef? = null, // автонаведение в режиме «Адреналин»
)

/** Вражеская пуля bullet-hell паттерна (тоже из пула) */
class EnemyBullet(
    var alive: Boolean = false,
    val origin: Vector2 = Vector2(),
    val dir: Vector2 = Vector2(1f, 0f),
    var speed: Float = 420f,
    var lifetime: Float = 4f,
    var patternId: Int = 0,
)

// ── Игрок ───────────────────────────────────────────────────────────────────
class PlayerTag

/** Фаза неуязвимости после «Фазового Сдвига» (рывка) */
class PhaseShift(
    var invulnRemaining: Float = 0f,   // 0.5 сек i-frames
    val dashDir: Vector2 = Vector2(),
    var piercedBullet: Boolean = false, // рывок сквозь пулю → Адреналин
)

/** Кулдаун тактического триггера (указательный палец) */
class DashCooldown(var remaining: Float = 0f, var ready: Boolean = true)

// ── Враги (дизайн заставляет использовать третий палец) ─────────────────────
class KamikazeDroneTag    // летит по прямой с огромной скоростью — уклонение только рывком
class PredictorTurretTag  // стреляет в упреждение, показывает красный лазер-предсказание
class ShieldBearerTag     // фаланга: уязвим только со спины — нужен рывок за спину
class SniperTag           // лазер с «слепой зоной» в центре — луч надо пересечь рывком

class AIBrain(var stateTime: Float = 0f) // корутина-стейтмашина привязана к entity

// ── Эффекты ─────────────────────────────────────────────────────────────────
class GravityMine(var armingTime: Float = 0.4f, var radius: Float = 140f)
class Shockwave(var radius: Float = 0f, var maxRadius: Float = 260f, var damage: Int = 30)
class ParticleFx(var ttl: Float = 0f, var colorHex: Int = 0)

/** Ссылка на Fleks-entity (обёртка, чтобы компоненты не зависели от internals библиотеки) */
@JvmInline
value class EntityRef(val id: Int)
