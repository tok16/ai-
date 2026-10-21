package com.neongrip.core.systems

import com.neongrip.core.ecs.IntervalSystem
import com.neongrip.core.components.ParticleFx
import com.neongrip.core.pool.Pool

/**
 * Частицы (гильзы, дым от ствола, искры корпуса, следы рывка) — тоже из пула.
 * 2000 слотов предзаполнены при загрузке уровня; GC не видит ни одной аллокации в бою.
 */
class FxParticlePoolSystem : IntervalSystem() {

    override var interval = 1
    private val particles = Pool(
        capacity = PARTICLE_POOL_SIZE,
        factory = ::ParticleFx,
        reset = { p -> p.ttl = 0f },
        isAlive = { it.ttl > 0f },
    )

    override fun update() {
        particles.forEachAlive { p -> p.ttl -= delta } // отрисовка пакетом в NeonRenderSystem
    }

    fun emit(x: Float, y: Float, ttl: Float, colorHex: Int) {
        val p = particles.obtain() ?: return // переполнение пула — тихо отбрасываем, без фризов
        p.ttl = ttl; p.colorHex = colorHex
    }

    companion object { const val PARTICLE_POOL_SIZE = 2000 }
}
