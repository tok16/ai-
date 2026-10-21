package com.neongrip.core.systems

import com.github.quillnov.fleks.IntervalSystem
import com.neongrip.core.components.Bullet
import com.neongrip.core.components.EntityRef
import com.neongrip.core.components.Faction
import com.neongrip.core.pool.Pool
import com.neongrip.core.state.GameState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Пули и их жизненный цикл — ЕДИНЫЙ пул для всех фракций.
 *
 * Object Pooling: пул из 1000 пуль создаётся при загрузке уровня; в бою объекты
 * только переиспользуются — ноль аллокаций, ноль микро-фризов GC, меньше нагрева SoC.
 *
 * Фракция на каждой пуле нужна для crossfire-механики «боты убивают друг друга
 * и игрока»: CollisionSystem сверяет bullet.faction с FactionComponent цели
 * через Faction.isHostileTo() — дружественный огонь выключен, враждебный — включён.
 */
class BulletSystem(private val state: GameState) : IntervalSystem() {

    override var interval = 1

    private val bullets = Pool(
        capacity = BULLET_POOL_SIZE,
        factory = ::Bullet,
        reset = { b -> b.alive = false; b.homingTarget = null; b.lifetime = 2f },
        isAlive = { it.alive },
    )

    override fun update() {
        // Интеграция полёта + срок жизни; auto-aim в режиме «Адреналина» подкручивает dir
        bullets.forEachAlive { b ->
            b.lifetime -= delta
            if (b.lifetime <= 0f) b.alive = false
            b.origin.x += b.dir.x * b.speed * delta
            b.origin.y += b.dir.y * b.speed * delta
        }
    }

    /** Выстрел игрока (правый стик). В режиме Адреналина — автонаведение на уязвимые точки */
    fun spawnPlayerShot(x: Float, y: Float, dirX: Float, dirY: Float, homing: EntityRef? = null) {
        fire(BulletTemplate(fx = x, fy = y, dx = dirX, dy = dirY), Faction.PLAYER,
             damage = PLAYER_SHOT_DAMAGE, speed = 900f, lifetime = 2f, homing = homing)
        state.fire()
    }

    /**
     * Универсальный залп любой фракции. Возвращает true, если пуля взята из пула
     * (пул исчерпан → пропускаем выстрел, но НЕ аллоцируем новый объект).
     */
    fun fire(t: BulletTemplate, faction: Faction, damage: Int, speed: Float,
             lifetime: Float, homing: EntityRef? = null): Boolean {
        val b = bullets.obtain() ?: return false
        b.origin.set(t.fx, t.fy)
        b.dir.set(t.dx, t.dy)
        b.faction = faction
        b.damage = damage
        b.speed = speed
        b.lifetime = lifetime
        b.alive = true
        b.homingTarget = homing
        return true
    }

    /** Совместимость со старым API: вражеский bullet-hell выстрел = fire(HOSTILE) */
    fun spawnEnemyShot(x: Float, y: Float, dirX: Float, dirY: Float, patternId: Int) {
        fire(BulletTemplate(x, y, dirX, dirY), Faction.HOSTILE,
             damage = ENEMY_BULLET_DAMAGE, speed = 420f, lifetime = 4f)
    }

    /** Все живые пули рядом с точкой — переиспользуемый буфер (ноль аллокаций на кадр) */
    fun bulletsNear(x: Float, y: Float, radius2: Float = NEAR_RADIUS2): ArrayList<Bullet> {
        scanBuffer.clear()
        bullets.forEachAlive { b ->
            val dx = b.origin.x - x; val dy = b.origin.y - y
            if (dx * dx + dy * dy < radius2) scanBuffer.add(b)
        }
        return scanBuffer
    }

    /** Грубый скан всей живой популяции — используется CollisionSystem за один проход */
    inline fun forEachLive(action: (Bullet) -> Unit) = bullets.forEachAlive(action)

    private val scanBuffer = ArrayList<Bullet>(128)

    /** Вызывается CollisionSystem при пересечении i-frames игрока с чужой пулей */
    fun onPhasePiercedBullet() {
        if (!state.adrenalineActive.value) AdrenalineRunner.activate(state) // 2 сек bullet-time
    }

    companion object {
        const val BULLET_POOL_SIZE = 1000      // игрок + два отряда ботов + bullet-hell
        const val NEAR_RADIUS2 = 900f          // 30px радиус грубого отбора
        const val PLAYER_SHOT_DAMAGE = 12
        const val ENEMY_BULLET_DAMAGE = 15
    }
}

/** Плоский набор параметров залпа — без аллокаций в кадровом цикле */
class BulletTemplate(
    val fx: Float, val fy: Float,   // дуло (world coords)
    val dx: Float, val dy: Float,   // нормализованное направление
)

/**
 * Корутина «Адреналина»: delay(2000) -> выключить bullet-time.
 * Линейный код вместо машины состояний из коллбэков — главное преимущество Coroutines.
 */
object AdrenalineRunner {
    fun activate(state: GameState) {
        state.scope.launch {
            state.setAdrenaline(true)
            delay(ADRENALINE_MS)
            state.setAdrenaline(false)
        }
    }
    private const val ADRENALINE_MS = 2000L
}
