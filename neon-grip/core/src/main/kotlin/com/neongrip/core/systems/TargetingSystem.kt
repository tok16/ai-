package com.neongrip.core.systems

import com.neongrip.core.ecs.Entity
import com.neongrip.core.ecs.IntervalSystem
import com.neongrip.core.components.*

/**
 * TargetingSystem — «мозги» crossfire-механики: боты выбирают целями и игрока,
 * и друг друга.
 *
 * Каждые RETARGET_INTERVAL секунд каждый BotTargeting сканирует живых бойцов
 * (Position + Hitbox + Health + FactionComponent), фильтрует по враждебности
 * фракций (Faction.isHostileTo) и ставит целью ближайшего доступного в радиусе
 * attackRange:
 *   • ANTIPLAYER (корпоративная охрана отрядов Alpha/Bravo): игрок — приоритет №1;
 *     если он вне радиуса или «растворился» в Фазовом Сдвиге — цель переключается
 *     на конкурента (бот другого отряда). Так два ро́я охраны, промахнувшись по
 *     игроку, открывают огонь ДРУГ ПО ДРУГУ — арена превращается в хаос
 *     корпоративной зачистки.
 *
 * targetId = -1 → цели нет; BotCombatSystem сбрасывает невалидную (мёртвую) цель
 * немедленно, а ротация здесь подбирает новую в течение трети секунды.
 */
class TargetingSystem : IntervalSystem() {

    override var interval = 1

    /** Снимок кандидатов на кадр: переиспользуемые структуры — ноль аллокаций в бою */
    private val snapshotIds = ArrayList<Int>(256)
    private val snapshotX = FloatArray(256)
    private val snapshotY = FloatArray(256)
    private val snapshotFaction = Array(256) { Faction.HOSTILE }
    private var count = 0

    override fun update() {
        rebuildSnapshot()

        world.iterate(BotTag::class, BotTargeting::class, Position::class, FactionComponent::class) { _e ->
 {
                _, targeting, selfPos, selfFc ->
            targeting.retargetCooldown -= delta
            if (targeting.retargetCooldown > 0f && targeting.hasTarget) return@iterate

            targeting.retargetCooldown = RETARGET_INTERVAL_SEC

            // Приоритет ANTIPLAYER: игрок в радиусе и «виден» → цель = игрок
            if (targeting.mode == BotMode.ANTIPLAYER) {
                val player = findNearest(selfPos, selfFc.faction, targeting.attackRange, playersOnly = true)
                if (player >= 0) { targeting.targetId = player; return@iterate }
            }

            // Иначе — ближайший враждебный боец: бот конкурирующего отряда охраны или игрок
            targeting.targetId = findNearest(selfPos, selfFc.faction, targeting.attackRange, playersOnly = false)
        }
    }

    private fun rebuildSnapshot() {
        count = 0
        snapshotIds.clear()
        world.iterate(Position::class, Hitbox::class, Health::class, FactionComponent::class) { _e ->
 { e, p, _, hp, fc ->
            if (hp.current <= 0 || count >= snapshotX.size) return@iterate
            snapshotIds.add(e.id())
            snapshotX[count] = p.x; snapshotY[count] = p.y; snapshotFaction[count] = fc.faction
            count++
        }
    }

    /**
     * Ближайший враг myFaction в радиусе range; возвращает id или -1.
     * playersOnly=true — искать только PLAYER (для ANTIPLAYER-приоритета).
     * Правила видимости игрока:
     *   • Игрок в фазе Фазового Сдвига (i-frames) — «растворяется» для AI: рывок
     *     выводит его из-под прицела всех фракций сразу, и боты начинают косить друг друга.
     *   • В режиме Адреналина игрок наоборот «подсвечен» — автонаведение работает честно.
     */
    private fun findNearest(from: Position, myFaction: Faction, range: Float, playersOnly: Boolean): Int {
        var best = -1
        var bestD2 = range * range
        val playerVisible = adrenalineFlag || !playerInPhaseShift()
        for (i in 0 until count) {
            val other = snapshotFaction[i]
            if (!myFaction.isHostileTo(other)) continue
            if (playersOnly && other != Faction.PLAYER) continue
            if (!playersOnly && other == Faction.PLAYER && !playerVisible) continue
            val dx = snapshotX[i] - from.x; val dy = snapshotY[i] - from.y
            val d2 = dx * dx + dy * dy
            if (d2 < bestD2) { bestD2 = d2; best = snapshotIds[i] }
        }
        return best
    }

    /** Игрок в i-frames рывка — для AI он «растворяется»: уклонение вознаграждается огнём по ботам */
    private fun playerInPhaseShift(): Boolean {
        var shifting = false
        world.iterate(PlayerTag::class, PhaseShift::class) { _e ->
 { _, phase ->
            if (phase.invulnRemaining > 0f) shifting = true
        }
        return shifting
    }

    companion object {
        const val RETARGET_INTERVAL_SEC = 0.35f
        /** Синхронизируется из GameWorld.update(); флаг «игрок подсвечен адреналином» */
        @JvmStatic var adrenalineFlag: Boolean = false
    }
}
