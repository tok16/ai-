package com.neongrip.core.systems

import com.github.quillnov.fleks.Entity
import com.github.quillnov.fleks.IntervalSystem
import com.neongrip.core.components.*
import com.neongrip.core.state.GameState

/**
 * Faction-aware коллизии «пуля ↔ сущность»: один проход по живым пулям за кадр.
 *
 * Ключевые правила:
 *  • Пуля наносит урон только сущностям враждебной фракции (Faction.isHostileTo):
 *      PLAYER-пуля → боты обоих отрядов охраны;
 *      HOSTILE-пуля бота Alpha → боты Bravo и игрок, и наоборот
 *      (crossfire: два отряда охраны убивают ДРУГ ДРУГА на глазах игрока);
 *      дружественный огонь внутри отряда выключен — волна не стирает себя сама.
 *  • Игрок в i-frames пересекает чужую пулю → piercedBullet=true → Адреналин
 *    (bullet-time 2 сек + автонаведение на уязвимые точки).
 *  • Щитоносцы (Фаланга): фронтальный щит блокирует урон с любой стороны —
 *    бить можно только со спины. Это работает и против ботов: фаланга прикрывает
 *    от перестрелки конкурентов, пока игрок не обойдёт её рывком.
 *  • Убийство засчитывается игроку ТОЛЬКО если последний удар нанёс его снаряд
 *    (LastDamagedBy) — синхронизация комбо «Нейросеть» не капает за чужие смерти.
 */
class CollisionSystem(
    private val state: GameState,
    private val bullets: BulletSystem,
) : IntervalSystem() {

    override var interval = 1

    /** Снимок живых целей на кадр: переиспользуемые массивы — ноль аллокаций в бою */
    private val ids = ArrayList<Int>(256)
    private val xs = FloatArray(512)
    private val ys = FloatArray(512)
    private val rs = FloatArray(512)
    private var count = 0

    override fun update() {
        rebuildSnapshot()

        bullets.forEachLive { b ->
            if (!b.alive) return@forEachLive          // могла погаснуть в этом же проходе
            val hit = findVictim(b)
            if (hit >= 0) applyHit(b, hit)
            b.alive = false                            // пуля гаснет: возврат в пул
        }
    }

    private fun rebuildSnapshot() {
        count = 0
        ids.clear()
        world.iterate(Position::class, Hitbox::class, Health::class, FactionComponent::class) {
                entity, pos, hb, _, _ ->
            if (count >= xs.size) return@iterate       // жёсткий потолок снимка (перф-гарантия)
            ids.add(entity.id())
            xs[count] = pos.x; ys[count] = pos.y; rs[count] = hb.radius
            count++
        }
    }

    /** Первый попавшийся враг фракции пули под траекторией; -1 если мимо всех (грубый скан) */
    private fun findVictim(b: Bullet): Int {
        for (i in 0 until count) {
            val dx = xs[i] - b.origin.x; val dy = ys[i] - b.origin.y
            val r = rs[i] + BULLET_HIT_R
            if (dx * dx + dy * dy < r * r && isTargetable(ids[i], b.faction)) return ids[i]
        }
        return -1
    }

    private fun isTargetable(id: Int, bulletFaction: Faction): Boolean {
        val e = world.entity(id)
        val fc = e[FactionComponent::class] ?: return false
        return bulletFaction.isHostileTo(fc.faction)
    }

    private fun applyHit(b: Bullet, victimId: Int) {
        val victim = world.entity(victimId)
        val vPos = victim[Position::class] ?: return
        val health = victim[Health::class] ?: return

        // ── Фаланга: фронтальный щит держит удар с ЛЮБОЙ враждебной стороны ──
        if (victim contains ShieldBearerTag::class) {
            val rot = victim[Rotation::class] ?: return
            val angleToShot = Math.toDegrees(
                Math.atan2((b.origin.y - vPos.y).toDouble(), (b.origin.x - vPos.x).toDouble())
            ).toFloat()
            val diff = kotlin.math.abs(((angleToShot - rot.degrees + 540) % 360) - 180)
            if (diff > 180 - SHIELD_ARC_DEG / 2f) return   // пуля звякнула о щит
        }

        // ── Игрок: i-frames Фазового Сдвига → phase-pierce → Адреналин ──────
        if (victim contains PlayerTag::class) {
            val phase = victim[PhaseShift::class]
            if (phase != null && phase.invulnRemaining > 0f) {
                phase.piercedBullet = true
                bullets.onPhasePiercedBullet()             // рывок сквозь пулю = bullet-time!
                return
            }
        }

        health.current -= b.damage
        victim[LastDamagedBy::class]?.let { it.faction = b.faction }
        showEnemyHealthBarFor(victim, seconds = 1f)        // diegetic HP: полоска на секунду

        if (health.current <= 0) onDeath(victim, b.faction, vPos)
    }

    private fun onDeath(victim: Entity, killerFaction: Faction, pos: Position) {
        when (killerFaction) {
            Faction.PLAYER -> state.addKill()              // комбо «Нейросеть» копится
            else -> state.recordBotCrossfireKill()         // статистика хаоса на арене
        }
        if (victim contains PlayerTag::class) {
            state.onPlayerKilled()
            return                                          // игрок не удаляется — экран сам решит
        }
        spawnDeathFx(pos)                                   // вспышка из FX-пула частиц
        victim.destroy()                                    // Fleks снимет все компоненты разом
    }

    private fun spawnDeathFx(pos: Position) { /* пул ParticleFx: неоновый взрыв в точке pos */ }

    private fun showEnemyHealthBarFor(entity: Entity, seconds: Float) {
        /* Метка "hp bar visible until t+seconds" — гаснет сама (diegetic UI) */
    }

    companion object {
        const val SHIELD_ARC_DEG = 140f   // фронтальная дуга защиты щитоносца
        const val BULLET_HIT_R = 12f
    }
}
