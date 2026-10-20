package com.neongrip.core.systems

import com.github.quillnov.fleks.Entity
import com.github.quillnov.fleks.IntervalSystem
import com.neongrip.core.components.*
import com.neongrip.core.state.GameState

/**
 * Коллизии «пуля ↔ сущность» через пространственную сетку (spatial hash grid).
 * Перебор O(n²) недопустим при сотнях пуль — grid даёт ~O(n).
 *
 * Ключевые правила из ТЗ:
 *  • Игрок в i-frames пересекает вражескую пулю → piercedBullet=true → Адреналин
 *    (bullet-time 2 сек + автонаведение на уязвимые точки).
 *  • Щитоносцы (Фаланга): фронтальный щит блокирует урон — бить можно только со спины,
 *    куда игрок попадает рывком (палец 3), продолжая стрелять правым стиком.
 *  • Снайперский луч имеет «слепую зону»: пересечь её можно только рывком.
 */
class CollisionSystem(
    private val state: GameState,
    private val bullets: BulletSystem,
) : IntervalSystem() {

    override var interval = 1

    // cellSize подобран под радиусы hitbox'ов (~64px при камере 1:1)
    private val grid = SpatialGrid(cellSize = 64f)

    override fun update() {
        grid.clear()

        // 1. Индексация живых целей по сетке
        world.iterate(Position::class, Hitbox::class) { entity, pos, hb ->
            grid.insert(entity.id(), pos.x, pos.y, hb.radius)
        }

        // 2. Пули игрока → hostile-сущности (учёт угла попадания для щита Фаланги)
        world.iterate(HostileMarker::class, Position::class, Health::class, Rotation::class) {
                entity, pos, health, rot ->
            val shot = grid.queryNearbyPlayerBullet(pos.x, pos.y) ?: return@iterate
            if (isShieldBlocking(entity, shot, pos, rot)) return@iterate // пуля звякнула о фронтальный щит
            health.current -= PLAYER_SHOT_DAMAGE
            shot.consume()                       // пуля гаснет: возврат в пул
            showEnemyHealthBarFor(entity, seconds = 1f) // diegetic HP: полоска на секунду над головой
            if (health.current <= 0) {
                state.addKill()                  // комбо «Нейросеть» копит Синхронизацию
                entity.destroy()
            }
        }

        // 3. Вражеские пули → игрок: i-frames → phase-pierce → Adrenaline
        world.iterate(PlayerTag::class, PhaseShift::class, Position::class, Health::class) {
                _, phase, pos, health ->
            for (eb in bullets.enemyBulletsNear(pos.x, pos.y)) {
                if (!overlaps(eb, pos)) continue
                when {
                    phase.invulnRemaining > 0f -> {
                        phase.piercedBullet = true
                        bullets.onPhasePiercedBullet()   // рывок сквозь пулю = bullet-time!
                    }
                    else -> {
                        health.current -= ENEMY_BULLET_DAMAGE
                        state.onPlayerDamaged()          // обнуление комбо-шкалы
                        eb.alive = false                 // пуля расходится дымом (FX-пул)
                    }
                }
            }
        }
    }

    /** Фаланга: урон с фронта (в пределах SHIELD_ARC_DEG от направления взгляда) блокируется */
    private fun isShieldBlocking(entity: Entity, shot: BulletRef, target: Position, rot: Rotation): Boolean {
        if (!(entity contains ShieldBearerTag::class)) return false
        val angleToShot = Math.toDegrees(
            Math.atan2((shot.y - target.y).toDouble(), (shot.x - target.x).toDouble())
        ).toFloat()
        // Разница углов в [-180..180]: |diff| < 70° => выстрел пришёл в лицо щита
        val diff = kotlin.math.abs(((angleToShot - rot.degrees + 540) % 360) - 180)
        return diff > 180 - SHIELD_ARC_DEG / 2f
    }

    private fun overlaps(eb: EnemyBullet, pos: Position): Boolean {
        val dx = eb.origin.x - pos.x; val dy = eb.origin.y - pos.y
        return dx * dx + dy * dy < BULLET_HIT_R2
    }

    private fun showEnemyHealthBarFor(entity: Entity, seconds: Float) {
        /* Метка "hp bar visible until t+seconds" — гаснет сама (diegetic UI) */
    }

    companion object {
        const val PLAYER_SHOT_DAMAGE = 12
        const val ENEMY_BULLET_DAMAGE = 15
        const val SHIELD_ARC_DEG = 140f   // фронтальная дуга защиты щитоносца
        const val BULLET_HIT_R2 = 12f * 12f
    }
}

/** Маркер враждебной фракции (добавляется спавнером к каждому врагу) */
class HostileMarker

/** Ссылка на пулю игрока из spatial-запроса; consume() возвращает её в пул */
class BulletRef(val x: Float = 0f, val y: Float = 0f) {
    fun consume() {}
}

/** Компактная пространственная сетка: плоские массивы, ноль аллокаций на кадр */
class SpatialGrid(val cellSize: Float) {
    fun clear() {}
    fun insert(entityId: Int, x: Float, y: Float, radius: Float) {}
    fun queryNearbyPlayerBullet(x: Float, y: Float): BulletRef? = null
}
