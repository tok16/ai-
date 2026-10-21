package com.neongrip.core

import com.neongrip.core.components.Faction
import com.neongrip.core.components.isHostileTo
import kotlin.test.*

/**
 * Тесты правил враждебности фракций — формальная спецификация механики
 * «боты убивают друг друга и игрока» (см. Faction.isHostileTo, TargetingSystem,
 * CollisionSystem). Чистая логика, без GL-контекста и Fleks-мира.
 */
class FactionHostilityTest {

    @Test fun `игрок враждебен обоим отрядам охраны`() {
        assertTrue(Faction.PLAYER.isHostileTo(Faction.HOSTILE))
        assertTrue(Faction.PLAYER.isHostileTo(Faction.HOSTILE_B))
        assertTrue(Faction.HOSTILE.isHostileTo(Faction.PLAYER))
        assertTrue(Faction.HOSTILE_B.isHostileTo(Faction.PLAYER))
    }

    @Test fun `отряды охраны враждебны ДРУГ ДРУГУ — crossfire включён`() {
        assertTrue(Faction.HOSTILE.isHostileTo(Faction.HOSTILE_B))
        assertTrue(Faction.HOSTILE_B.isHostileTo(Faction.HOSTILE))
    }

    @Test fun `дружественный огонь внутри отряда выключен`() {
        assertFalse(Faction.HOSTILE.isHostileTo(Faction.HOSTILE))
        assertFalse(Faction.HOSTILE_B.isHostileTo(Faction.HOSTILE_B))
        assertFalse(Faction.PLAYER.isHostileTo(Faction.PLAYER))
    }

    @Test fun `отношение симметрично — коллизии пуль работают в обе стороны`() {
        val factions = Faction.entries
        for (a in factions) for (b in factions) {
            assertEquals(a.isHostileTo(b), b.isHostileTo(a), "несимметрично: $a ↔ $b")
        }
    }
}
