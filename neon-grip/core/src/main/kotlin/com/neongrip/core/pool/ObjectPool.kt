package com.neongrip.core.pool

/**
 * Object Pooling — критичная оптимизация для мобильных шутеров.
 *
 * Никогда не создавайте новые Bullet/Particle во время боя: аллокации провоцируют
 * микро-фризы сборщика мусора (GC), а на телефоне ещё и греют SoC / сажают батарею.
 * Пул из 500 пуль создаётся один раз при загрузке уровня, дальше объекты только
 * «переиспользуются» путём сброса полей.
 */
class Pool<T>(
    private val capacity: Int,
    private val factory: () -> T,
    private val reset: (T) -> Unit,
    private val isAlive: (T) -> Boolean,
) {
    private val slots = ArrayList<T>(capacity).also { list ->
        repeat(capacity) { list.add(factory()) } // предзаполнение до боя — zero-allocation в ране
    }
    private var cursor = 0

    /** Возвращает свободный объект или null, если пул исчерпан (кадры не должны терять пули молча) */
    fun obtain(): T? {
        // Ring-buffer скан: самый дешёвый поиск без перестановок и без аллокаций итераторов
        for (i in 0 until capacity) {
            val idx = (cursor + i) % capacity
            val candidate = slots[idx]
            if (!isAlive(candidate)) {
                reset(candidate)
                cursor = (idx + 1) % capacity
                return candidate
            }
        }
        return null // пул полон — система решает, что делать (например, протухает старая частица)
    }

    inline fun forEachAlive(action: (T) -> Unit) {
        for (slot in slots) if (isAlive(slot)) action(slot)
    }

    val aliveCount: Int get() = slots.count { isAlive(it) }
}
