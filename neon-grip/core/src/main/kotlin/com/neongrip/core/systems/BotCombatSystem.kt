package com.neongrip.core.systems

import com.github.quillnov.fleks.Entity
import com.github.quillnov.fleks.IntervalSystem
import com.neongrip.core.components.*
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * BotCombatSystem — «руки» security-ботов: огонь по выбранной цели.
 *
 * Каждый кадр для каждого бота с живой целью (BotTargeting.targetId):
 *  1. Валидация цели: цель умерла/исчезла → targetId = -1, следующий тик
 *     TargetingSystem подберёт новую. Боты не «добивают» мёртвых и мгновенно
 *     переключаются на живых конкурентов — перестрелка на арене не затухает.
 *  2. Разворот корпуса к цели (Rotation) — нужно и щитоносцам Фаланги, чей
 *     фронтальный щит теперь держит удар и от других ботов (crossfire).
 *  3. Огневой цикл: fireIntervalSec запускает burst (короткую очередь),
 *     пули летят в точку УПРЕЖДЕНИЯ — туда, где цель будет через время подлёта.
 *  4. Камикадзе вместо стрельбы таранят: контакт с любым врагом фракции =
 *     детонация с уроном по площади — гибнут и игрок, и другие дроны рядом.
 *
 * Итог механики из ТЗ: HOSTILE-пули долетают до других HOSTILE-сущностей
 * (CollisionSystem фильтрует только дружественный огонь внутри фракции) —
 * два отряда охраны, стреляя в игрока, косят друг друга на его глазах.
 */
class BotCombatSystem(
    private val bullets: BulletSystem,
) : IntervalSystem() {

    override var interval = 1

    override fun update() {
        world.iterate(BotTag::class, BotTargeting::class, Position::class,
                      Rotation::class, FactionComponent::class, Health::class) {
                entity, targeting, pos, rot, fc, _ ->
            if (!targeting.hasTarget) return@iterate

            // ── 1. Валидация цели: мертва/удалена → сброс, ротация сама найдёт новую ──
            val target = resolveTarget(targeting.targetId)
            if (target == null) { targeting.targetId = -1; return@iterate }

            val tPos = target[Position::class] ?: run { targeting.targetId = -1; return@iterate }

            // ── 2. Разворот к цели ────────────────────────────────────────────
            rot.degrees = Math.toDegrees(
                atan2((tPos.y - pos.y).toDouble(), (tPos.x - pos.x).toDouble())
            ).toFloat()

            // ── 3. Камикадзе: контактная детонация (стрельба не нужна) ────────
            if (entity contains KamikazeDroneTag::class) {
                val hb = target[Hitbox::class]
                val reach = KAMIKAZE_FUSE_R + (hb?.radius ?: 0f)
                val dx = tPos.x - pos.x; val dy = tPos.y - pos.y
                if (dx * dx + dy * dy < reach * reach) {
                    explode(pos, fc.faction)
                    entity.destroy()                   // дрон сгорает в детонации
                }
                return@iterate
            }

            // ── 4. Огневой цикл стрелковых типов ──────────────────────────────
            targeting.fireTimer -= delta
            if (targeting.fireTimer <= 0f && targeting.burstRemaining <= 0) {
                targeting.burstRemaining = BURST_SIZE
                targeting.burstTimer = 0f
                targeting.fireTimer = targeting.fireIntervalSec
            }
            if (targeting.burstRemaining > 0) {
                targeting.burstTimer -= delta
                if (targeting.burstTimer <= 0f) {
                    shootAt(entity, pos, fc, target, tPos)
                    targeting.burstRemaining--
                    targeting.burstTimer = targeting.burstFireInterval
                }
            }
        }
    }

    /** Живая ли цель ещё (существует и HP > 0)? Возвращает Entity или null */
    private fun resolveTarget(id: Int): Entity? {
        if (id < 0 || !world.entities.contains(id)) return null
        val e = world.entity(id)
        val hp = e[Health::class] ?: return e   // у цели нет HP — не валидная боевая цель
        return if (hp.current > 0) e else null
    }

    /** Упреждающий выстрел: снаряд летит туда, где цель БУДЕТ через время подлёта */
    private fun shootAt(from: Entity, pos: Position, fc: FactionComponent,
                        target: Entity, tPos: Position) {
        val tVel = target[Velocity::class]
        val dist = hypot((tPos.x - pos.x).toDouble(), (tPos.y - pos.y).toDouble()).toFloat()
        val tof = dist / BULLET_SPEED
        val leadX = tPos.x + (tVel?.vx ?: 0f) * tof
        val leadY = tPos.y + (tVel?.vy ?: 0f) * tof

        var dx = leadX - pos.x; var dy = leadY - pos.y
        val len = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(0.001f)
        dx /= len; dy /= len

        bullets.fire(
            BulletTemplate(pos.x + dx * MUZZLE_OFFSET, pos.y + dy * MUZZLE_OFFSET, dx, dy),
            faction = fc.faction,
            damage = BOT_SHOT_DAMAGE,
            speed = BULLET_SPEED,
            lifetime = 3f,
        )
    }

    /** Взрыв камикадзе: урон по площади ВСЕМ враждебным фракциям — и игроку, и ботам */
    private fun explode(at: Position, faction: Faction) {
        world.iterate(Position::class, Health::class, FactionComponent::class) { _, p, hp, ofc ->
            if (!faction.isHostileTo(ofc.faction)) return@iterate
            val dx = p.x - at.x; val dy = p.y - at.y
            if (dx * dx + dy * dy < KAMIKAZE_BLAST_R2) hp.current -= KAMIKAZE_DAMAGE
        }
    }

    companion object {
        const val BULLET_SPEED = 560f
        const val BOT_SHOT_DAMAGE = 10
        const val MUZZLE_OFFSET = 18f
        const val BURST_SIZE = 3           // короткие точные очереди охраны корпорации
        const val KAMIKAZE_FUSE_R = 16f
        const val KAMIKAZE_BLAST_R2 = 90f * 90f
        const val KAMIKAZE_DAMAGE = 35
    }
}
