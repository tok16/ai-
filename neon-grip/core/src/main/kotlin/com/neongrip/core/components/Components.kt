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
enum class Faction {
    /** Сбежавший боевой андроид (игрок) */
    PLAYER,

    /** Охрана периметра Alpha — squadId 0 */
    HOSTILE,

    /** Охрана серверных стоек Bravo — squadId 1. Воюет с Alpha из-за сектора! */
    HOSTILE_B;

    companion object {
        /** Все боевые фракции корпорации (роги волн спавнят отряды по очереди) */
        val CORP_FACTIONS = listOf(HOSTILE, HOSTILE_B)
    }
}

/**
 * Враждебна ли фракция владельца другой фракции — ядро механики
 * «боты убивают друг друга и игрока».
 *
 *   • Игрок враждебен ЛЮБОЙ корпоративной фракции.
 *   • Отряды охраны Alpha и Bravo враждебны ДРУГ ДРУГУ (конфликт юрисдикций
 *     внутри серверной фермы) — поэтому два отряда, накрывая игрока огнём,
 *     косят друг друга на его глазах.
 *   • Дружественный огонь внутри одного отряда выключен — волна не стирает
 *     себя сама, перестрелка имеет структуру, а не превращается в кашу.
 */
fun Faction.isHostileTo(other: Faction): Boolean = when {
    this == Faction.PLAYER || other == Faction.PLAYER -> this != other
    else -> this != other   // HOSTILE ↔ HOSTILE_B — враги; внутри отряда — нет
}

/** Фракция сущности — ядро механики «боты убивают друг друга и игрока» */
class FactionComponent(var faction: Faction = Faction.HOSTILE)

/** Скорость автономного бота (пикс/сек). Камикадзе таранит, стрелки держат дистанцию */
class BotMovement(var speed: Float = 0f)

/** Последний источник урона (для статистики и корректного сброса комбо-шкалы) */
class LastDamagedBy(var faction: Faction? = null)

/**
 * Единый тип пули для всех фракций. Живёт в пуле BulletSystem и НИКОГДА
 * не аллоцируется во время боя — только переиспользуется (zero-GC гарантия).
 * Фракция владельца нужна для crossfire: пули HOSTILE-ботов убивают и игрока,
 * и других ботов (нейтралы/соплеменники — нет, чтобы не стирать волну подчистую).
 */
class Bullet(
    var alive: Boolean = false,
    var faction: Faction = Faction.PLAYER,
    val origin: Vector2 = Vector2(),
    val dir: Vector2 = Vector2(1f, 0f),
    var speed: Float = 900f,
    var damage: Int = 12,
    var lifetime: Float = 2f,
    var homingTarget: EntityRef? = null, // автонаведение в режиме «Адреналин»
)

/** Совместимость со старым кодом: вражеская пуля bullet-hell паттерна = Bullet(HOSTILE) */
typealias EnemyBullet = Bullet

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

/** Маркер автономного security-бота (корпоративные ЧОПы серверных ферм) */
class BotTag

/**
 * Целеуказание бота — ядро механики «боты убивают друг друга и игрока».
 *   • ANTIPLAYER — корпоративная охрана (отряды Alpha/Bravo): игрок приоритет №1,
 *     но при его «растворении» в Фазовом Сдвиге или вне радиуса бот открывает
 *     огонь по конкурентам — другому отряду охраны.
 * targetId = -1 → цель не выбрана; ротация целей — в TargetingSystem.
 */
class BotTargeting(
    var mode: BotMode = BotMode.ANTIPLAYER,
    var targetId: Int = -1,
    var retargetCooldown: Float = 0f,
    var attackRange: Float = 420f,
    var fireIntervalSec: Float = 1.2f,
    var fireTimer: Float = 0f,
    var burstRemaining: Int = 0,        // очередь выстрелов, назначенная мозгом-корутиной
    var burstFireInterval: Float = 0.1f,
    var burstTimer: Float = 0f,
) {
    val hasTarget: Boolean get() = targetId >= 0
}

enum class BotMode { ANTIPLAYER }

// ── Эффекты ─────────────────────────────────────────────────────────────────
class GravityMine(var armingTime: Float = 0.4f, var radius: Float = 140f)
class Shockwave(var radius: Float = 0f, var maxRadius: Float = 260f, var damage: Int = 30)
class ParticleFx(var ttl: Float = 0f, var colorHex: Int = 0)

/** Ссылка на Fleks-entity (обёртка, чтобы компоненты не зависели от internals библиотеки) */
@JvmInline
value class EntityRef(val id: Int)
