package com.neongrip.core.systems

import com.github.quillnov.fleks.IntervalSystem
import com.neongrip.core.components.Bullet
import com.neongrip.core.components.EnemyBullet
import com.neongrip.core.components.EntityRef
import com.neongrip.core.pool.Pool
import com.neongrip.core.state.GameState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Пули и их жизненный цикл.
 *
 * Object Pooling: пул из 500 пуль создаётся при загрузке уровня; в бою объекты
 * только переиспользуются — ноль аллокаций, ноль микро-фризов GC, меньше нагрева SoC.
 */
class BulletSystem(private val state: GameState) : IntervalSystem() {

    override var interval = 1

    // Пул игрока и пул врагов разделены — разные фракции, разные батчи отрисовки
    private val playerBullets = Pool(
        capacity = BULLET_POOL_SIZE,
        factory = ::Bullet,
        reset = { b -> b.alive = false; b.homingTarget = null; b.lifetime = 2f },
        isAlive = { it.alive },
    )

    private val enemyBullets = Pool(
        capacity = BULLET_POOL_SIZE,
        factory = ::EnemyBullet,
        reset = { b -> b.alive = false; b.lifetime = 4f },
        isAlive = { it.alive },
    )

    override fun update() {
        // Интеграция полёта + срок жизни; auto-aim в режиме «Адреналина» подкручивает dir
        playerBullets.forEachAlive { b ->
            b.lifetime -= delta
            if (b.lifetime <= 0f) b.alive = false
            b.origin.x += b.dir.x * b.speed * delta
            b.origin.y += b.dir.y * b.speed * delta
        }
        enemyBullets.forEachAlive { b ->
            b.lifetime -= delta
            if (b.lifetime <= 0f) b.alive = false
            b.origin.x += b.dir.x * b.speed * delta
            b.origin.y += b.dir.y * b.speed * delta
        }
    }

    fun spawnPlayerShot(x: Float, y: Float, dirX: Float, dirY: Float, homing: EntityRef? = null) {
        // Пул исчерпан → пропускаем выстрел, но НЕ аллоцируем новый объект (гарантия zero-GC)
        val b = playerBullets.obtain() ?: return
        b.origin.set(x, y); b.dir.set(dirX, dirY); b.alive = true; b.homingTarget = homing
        state.fire()
    }

    fun spawnEnemyShot(x: Float, y: Float, dirX: Float, dirY: Float, patternId: Int) {
        val b = enemyBullets.obtain() ?: return
        b.origin.set(x, y); b.dir.set(dirX, dirY); b.alive = true; b.patternId = patternId
    }

    /** Быстрый отбор живых вражеских пуль рядом с точкой — для CollisionSystem (без аллокаций списка на кадр используется реальный список-скан) */
    fun enemyBulletsNear(x: Float, y: Float): List<EnemyBullet> {
        scanBuffer.clear()
        enemyBullets.forEachAlive { b ->
            val dx = b.origin.x - x; val dy = b.origin.y - y
            if (dx * dx + dy * dy < NEAR_RADIUS2) scanBuffer.add(b)
        }
        return scanBuffer
    }
    private val scanBuffer = ArrayList<EnemyBullet>(64)

    /** Вызывается CollisionSystem при пересечении i-frames игрока с вражеской пулей */
    fun onPhasePiercedBullet() {
        if (!state.adrenalineActive.value) AdrenalineRunner.activate(state) // 2 сек bullet-time
    }

    companion object {
        const val BULLET_POOL_SIZE = 500
        const val NEAR_RADIUS2 = 900f // 30px радиус грубого отбора
    }
}

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
