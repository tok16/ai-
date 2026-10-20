package com.neongrip.core

import com.neongrip.core.state.GameState
import kotlin.test.*

/**
 * Тесты боевой экономики состояний (без GL-контекста — чистая логика).
 */
class GameStateTest {

    @Test fun `комбо нейросеть копит синхронизацию за убийства`() {
        val state = GameState()
        repeat(8) { state.addKill() }
        assertTrue(state.syncMeter.value > 0f)
        assertEquals(8, state.killsWithoutDamage.value)
    }

    @Test fun `урон обнуляет комбо-шкалу`() {
        val state = GameState()
        repeat(5) { state.addKill() }
        state.onPlayerDamaged()
        assertEquals(0f, state.syncMeter.value)
        assertEquals(0, state.killsWithoutDamage.value)
    }

    @Test fun `полная шкала расходуется ровно один раз на финишер рывка`() {
        val state = GameState()
        assertFalse(state.consumeSync())          // шкала пуста — финишера нет
        repeat(10) { state.addKill() }            // 10 * 0.12 >= 1.0 → максимум
        assertEquals(1f, state.syncMeter.value)
        assertTrue(state.consumeSync())           // следующий рывок оставит волну/мину
        assertFalse(state.consumeSync())          // и только один раз
    }

    @Test fun `перезарядка возвращает полный магазин`() {
        val state = GameState()
        repeat(GameState.DEFAULT_MAG_SIZE) { state.fire() }
        assertEquals(0, state.magazine.value)
        state.startReload(); assertTrue(state.reloading.value)
        state.finishReload()
        assertEquals(GameState.DEFAULT_MAG_SIZE, state.magazine.value)
        assertFalse(state.reloading.value)
    }
}
