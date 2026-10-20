package com.neongrip.core.systems

import com.github.quillnov.fleks.IntervalSystem
import com.neongrip.core.components.Position
import com.neongrip.core.components.Velocity

/** Интеграция Velocity → Position для всех сущностей одним dense-проходом (cache-friendly) */
class MovementSystem : IntervalSystem() {
    override var interval = 1
    override fun update() {
        world.iterate(Position::class, Velocity::class) { _, pos, vel ->
            pos.x += vel.vx * delta
            pos.y += vel.vy * delta
        }
    }
}
