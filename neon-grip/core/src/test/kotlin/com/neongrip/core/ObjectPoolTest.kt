package com.neongrip.core

import com.neongrip.core.pool.Pool
import kotlin.test.*

class ObjectPoolTest {

    private class Item(var alive: Boolean = false)

    @Test fun `пул переиспользует мёртвые объекты без аллокаций`() {
        var created = 0
        val pool = Pool(capacity = 4, factory = { created++; Item() },
            reset = { it.alive = false }, isAlive = { it.alive })
        val a = pool.obtain()!!; a.alive = true
        val b = pool.obtain()!!; b.alive = true
        b.alive = false // «пуля истекла»
        val c = pool.obtain()!!
        assertSame(b, c)   // тот же объект из памяти — ноль GC-давления в бою
        assertEquals(4, created) // capacity предзаполнен один раз при создании пула
    }

    @Test fun `исчерпанный пул возвращает null вместо новой аллокации`() {
        val pool = Pool(capacity = 2, factory = ::Item, reset = {}, isAlive = { true })
        assertNotNull(pool.obtain()); assertNotNull(pool.obtain())
        assertNull(pool.obtain()) // пул полон: выстрел пропускается, но фризов GC не будет
    }
}
