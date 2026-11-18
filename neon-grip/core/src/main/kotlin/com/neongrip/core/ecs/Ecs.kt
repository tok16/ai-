package com.neongrip.core.ecs

/**
 * Внутренняя ECS-подложка (замена библиотечного Fleks, чей артефакт недоступен
 * в публичных Maven-репозиториях). Семантика DSL сохранена 1-в-1: world { component {} },
 * entity { add(...) }, systems { add(...) }, iterate(vararg KClass) { e, c1, c2 -> }.
 *
 * Хранение: dense ArrayList на класс компонента — системы идут по ним последовательно
 * (cache-friendly), GC не видит аллокаций в бою. Миграция обратно на Fleks = замена импортов.
 */
class Entity @PublishedApi internal constructor(
    @PublishedApi internal val idValue: Int,
    @PublishedApi internal val stores: MutableMap<kotlin.reflect.KClass<*>, MutableList<Any?>>,
    @PublishedApi internal val aliveFlags: MutableList<Boolean>,
) {
    fun id(): Int = idValue
    fun isAlive(): Boolean = aliveFlags.getOrNull(idValue) == true

    inline operator fun <reified T : Any> get(k: kotlin.reflect.KClass<T>): T? = storeFor(T::class).getOrNull(idValue) as? T
    inline fun <reified T : Any> has(): Boolean = storeFor(T::class).getOrNull(idValue) != null

    @PublishedApi
    internal fun storeFor(c: kotlin.reflect.KClass<*>): MutableList<Any?> = stores.getOrPut(c) { mutableListOf() }

    /** Проверка наличия компонента по KClass (Fleks-совместимый синтаксис `entity contains X::class`) */
    infix operator fun contains(c: kotlin.reflect.KClass<*>): Boolean = storeFor(c).getOrNull(idValue) != null

    /** Удаление сущности (коллбэк systems проверяют isAlive перед следующим кадром) */
    fun destroy() {
        aliveFlags[idValue] = false
        for (s in stores.values) if (idValue < s.size) s[idValue] = null
    }
}

class EntityBuilder internal constructor(private val ent: Entity) {
    fun <T : Any> add(component: T) {
        val s = ent.storeFor(component::class)
        while (s.size <= ent.id()) s.add(null)
        s[ent.id()] = component
    }
}

abstract class BaseSystem {
    /** Инъектируется миром перед первым апдейтом */
    var world: EWorld = EWorld.EMPTY
        internal set
    /** Кадровый delta (сек), устанавливается миром перед update() */
    var delta: Float = 0f
        internal set

    abstract fun update()
}

/** Система с фиксированным интервалом кадров (interval = 1 → каждый кадр) */
abstract class IntervalSystem : BaseSystem() {
    abstract var interval: Int
    private var tickCounter = 0
    fun updateTick(dt: Float) {
        delta = dt
        if (++tickCounter >= interval) { tickCounter = 0; update() }
    }
}

class SystemsBuilder internal constructor() {
    internal val list = ArrayList<BaseSystem>()
    fun add(vararg systems: BaseSystem) { systems.forEach { list += it } }
}

class EWorld constructor(builder: WorldBuilder) {

    private val stores = HashMap<kotlin.reflect.KClass<*>, MutableList<Any?>>()
    @PublishedApi internal val aliveFlags = ArrayList<Boolean>()
    @PublishedApi internal val denseStores: HashMap<kotlin.reflect.KClass<*>, MutableList<Any?>> get() = stores
    private var nextId = 0

    val entities: EntitySet = EntitySet(this)

    internal val systems: ArrayList<BaseSystem> = builder.systemsList.also { sl ->
        sl.forEach { it.world = this }
    }

    // ── API сущностей ────────────────────────────────────────────────
    fun entity(action: EntityBuilder.() -> Unit): Entity {
        val id = nextId++
        aliveFlags.add(true)
        val e = Entity(id, stores, aliveFlags)
        action(EntityBuilder(e))
        return e
    }

    fun entity(id: Int): Entity = Entity(id, stores, aliveFlags)

    fun deleteEntity(id: Int) {
        if (id in aliveFlags.indices) aliveFlags[id] = false
        for (s in stores.values) if (id < s.size) s[id] = null
    }

    @PublishedApi
    internal fun storeRaw(c: kotlin.reflect.KClass<*>): MutableList<Any?>? = denseStores[c]
    @PublishedApi
    internal fun aliveAt(id: Int): Boolean = aliveFlags.getOrNull(id) == true
    @PublishedApi
    internal fun entityOf(id: Int): Entity = Entity(id, denseStores, aliveFlags)

    // ── Type-safe итераторы 1..7 компонентов (Fleks-совместимый DSL) ──────────
    inline fun <reified A : Any, reified B : Any> iterate(
        a: kotlin.reflect.KClass<A>, b: kotlin.reflect.KClass<B>, crossinline action: (Entity, A, B) -> Unit,
    ) { iterateN2(a, b) { e, x, y -> action(e, x, y) } }

    inline fun <reified A : Any, reified B : Any, reified C : Any> iterate(
        a: kotlin.reflect.KClass<A>, b: kotlin.reflect.KClass<B>, c: kotlin.reflect.KClass<C>,
        crossinline action: (Entity, A, B, C) -> Unit,
    ) { iterateN3(a, b, c) { e, x, y, z -> action(e, x, y, z) } }

    inline fun <reified A : Any, reified B : Any, reified C : Any, reified D : Any> iterate(
        a: kotlin.reflect.KClass<A>, b: kotlin.reflect.KClass<B>, c: kotlin.reflect.KClass<C>, d: kotlin.reflect.KClass<D>,
        crossinline action: (Entity, A, B, C, D) -> Unit,
    ) { iterateN4(a, b, c, d) { e, x, y, z, w -> action(e, x, y, z, w) } }

    inline fun <reified A : Any, reified B : Any, reified C : Any, reified D : Any, reified E2 : Any> iterate(
        a: kotlin.reflect.KClass<A>, b: kotlin.reflect.KClass<B>, c: kotlin.reflect.KClass<C>,
        d: kotlin.reflect.KClass<D>, e2: kotlin.reflect.KClass<E2>,
        crossinline action: (Entity, A, B, C, D, E2) -> Unit,
    ) { iterateN5(a, b, c, d, e2) { e, x, y, z, w, v -> action(e, x, y, z, w, v) } }

    inline fun <reified A : Any, reified B : Any, reified C : Any, reified D : Any, reified E2 : Any, reified F : Any> iterate(
        a: kotlin.reflect.KClass<A>, b: kotlin.reflect.KClass<B>, c: kotlin.reflect.KClass<C>,
        d: kotlin.reflect.KClass<D>, e2: kotlin.reflect.KClass<E2>, f: kotlin.reflect.KClass<F>,
        crossinline action: (Entity, A, B, C, D, E2, F) -> Unit,
    ) { iterateN6(a, b, c, d, e2, f) { e, x, y, z, w, v, u -> action(e, x, y, z, w, v, u) } }

    inline fun <reified A : Any, reified B : Any, reified C : Any, reified D : Any, reified E2 : Any, reified F : Any, reified G : Any> iterate(
        a: kotlin.reflect.KClass<A>, b: kotlin.reflect.KClass<B>, c: kotlin.reflect.KClass<C>,
        d: kotlin.reflect.KClass<D>, e2: kotlin.reflect.KClass<E2>, f: kotlin.reflect.KClass<F>,
        g: kotlin.reflect.KClass<G>,
        crossinline action: (Entity, A, B, C, D, E2, F, G) -> Unit,
    ) { iterateN7(a, b, c, d, e2, f, g) { e, x, y, z, w, v, u, t -> action(e, x, y, z, w, v, u, t) } }









    // ── Внутренние проходы: ровно n классов, ровно n+1 аргументов лямбды.
    //    Лишние классы (последующие параметры лямбды не используются вызывающим кодом)
    //    подставляются как NOCOMP::class — их store всегда пуст, поэтому они никогда
    //    не ограничивают выборку. Пропущенные компоненты получают null в буфере. ──
    @PublishedApi
    internal inline fun <reified A : Any, reified B : Any> iterateN2(
        a: kotlin.reflect.KClass<A>, b: kotlin.reflect.KClass<B>,
        crossinline action: (Entity, A, B) -> Unit,
    ) {
        val sa = storeRaw(a) ?: return
        val sb = storeRaw(b) ?: return
        for (id in sa.indices) {
            if (!aliveAt(id)) continue
            val va = sa[id]; val vb = sb.getOrNull(id)
            if (va == null || vb == null) continue
            @Suppress("UNCHECKED_CAST") action(entityOf(id), va as A, vb as B)
        }
    }

    @PublishedApi
    internal inline fun <reified A : Any, reified B : Any, reified C : Any> iterateN3(
        a: kotlin.reflect.KClass<A>, b: kotlin.reflect.KClass<B>, c: kotlin.reflect.KClass<C>,
        crossinline action: (Entity, A, B, C) -> Unit,
    ) {
        val sa = storeRaw(a) ?: return
        val sb = storeRaw(b) ?: return
        val sc = storeRaw(c) ?: return
        for (id in sa.indices) {
            if (!aliveAt(id)) continue
            val va = sa[id]; val vb = sb.getOrNull(id); val vc = sc.getOrNull(id)
            if (va == null || vb == null || vc == null) continue
            @Suppress("UNCHECKED_CAST") action(entityOf(id), va as A, vb as B, vc as C)
        }
    }

    @PublishedApi
    internal inline fun <reified A : Any, reified B : Any, reified C : Any, reified D : Any> iterateN4(
        a: kotlin.reflect.KClass<A>, b: kotlin.reflect.KClass<B>, c: kotlin.reflect.KClass<C>, d: kotlin.reflect.KClass<D>,
        crossinline action: (Entity, A, B, C, D) -> Unit,
    ) {
        val sa = storeRaw(a) ?: return
        val sb = storeRaw(b) ?: return
        val sc = storeRaw(c) ?: return
        val sd = storeRaw(d) ?: return
        for (id in sa.indices) {
            if (!aliveAt(id)) continue
            val va = sa[id]; val vb = sb.getOrNull(id); val vc = sc.getOrNull(id); val vd = sd.getOrNull(id)
            if (va == null || vb == null || vc == null || vd == null) continue
            @Suppress("UNCHECKED_CAST") action(entityOf(id), va as A, vb as B, vc as C, vd as D)
        }
    }

    @PublishedApi
    internal inline fun <reified A : Any, reified B : Any, reified C : Any, reified D : Any, reified E2 : Any> iterateN5(
        a: kotlin.reflect.KClass<A>, b: kotlin.reflect.KClass<B>, c: kotlin.reflect.KClass<C>,
        d: kotlin.reflect.KClass<D>, e2: kotlin.reflect.KClass<E2>,
        crossinline action: (Entity, A, B, C, D, E2) -> Unit,
    ) {
        val sa = storeRaw(a) ?: return
        val sb = storeRaw(b) ?: return
        val sc = storeRaw(c) ?: return
        val sd = storeRaw(d) ?: return
        val se = storeRaw(e2) ?: return
        for (id in sa.indices) {
            if (!aliveAt(id)) continue
            val va = sa[id]; val vb = sb.getOrNull(id); val vc = sc.getOrNull(id)
            val vd = sd.getOrNull(id); val ve = se.getOrNull(id)
            if (va == null || vb == null || vc == null || vd == null || ve == null) continue
            @Suppress("UNCHECKED_CAST") action(entityOf(id), va as A, vb as B, vc as C, vd as D, ve as E2)
        }
    }

    @PublishedApi
    internal inline fun <reified A : Any, reified B : Any, reified C : Any, reified D : Any, reified E2 : Any, reified F : Any> iterateN6(
        a: kotlin.reflect.KClass<A>, b: kotlin.reflect.KClass<B>, c: kotlin.reflect.KClass<C>,
        d: kotlin.reflect.KClass<D>, e2: kotlin.reflect.KClass<E2>, f: kotlin.reflect.KClass<F>,
        crossinline action: (Entity, A, B, C, D, E2, F) -> Unit,
    ) {
        val sa = storeRaw(a) ?: return
        val sb = storeRaw(b) ?: return
        val sc = storeRaw(c) ?: return
        val sd = storeRaw(d) ?: return
        val se = storeRaw(e2) ?: return
        val sf = storeRaw(f) ?: return
        for (id in sa.indices) {
            if (!aliveAt(id)) continue
            val va = sa[id]; val vb = sb.getOrNull(id); val vc = sc.getOrNull(id)
            val vd = sd.getOrNull(id); val ve = se.getOrNull(id); val vf = sf.getOrNull(id)
            if (va == null || vb == null || vc == null || vd == null || ve == null || vf == null) continue
            @Suppress("UNCHECKED_CAST") action(entityOf(id), va as A, vb as B, vc as C, vd as D, ve as E2, vf as F)
        }
    }

    @PublishedApi
    internal inline fun <reified A : Any, reified B : Any, reified C : Any, reified D : Any, reified E2 : Any, reified F : Any, reified G : Any> iterateN7(
        a: kotlin.reflect.KClass<A>, b: kotlin.reflect.KClass<B>, c: kotlin.reflect.KClass<C>,
        d: kotlin.reflect.KClass<D>, e2: kotlin.reflect.KClass<E2>, f: kotlin.reflect.KClass<F>,
        g: kotlin.reflect.KClass<G>,
        crossinline action: (Entity, A, B, C, D, E2, F, G) -> Unit,
    ) {
        val sa = storeRaw(a) ?: return
        val sb = storeRaw(b) ?: return
        val sc = storeRaw(c) ?: return
        val sd = storeRaw(d) ?: return
        val se = storeRaw(e2) ?: return
        val sf = storeRaw(f) ?: return
        val sg = storeRaw(g) ?: return
        for (id in sa.indices) {
            if (!aliveAt(id)) continue
            val va = sa[id]; val vb = sb.getOrNull(id); val vc = sc.getOrNull(id)
            val vd = sd.getOrNull(id); val ve = se.getOrNull(id); val vf = sf.getOrNull(id)
            val vg = sg.getOrNull(id)
            if (va == null || vb == null || vc == null || vd == null || ve == null || vf == null || vg == null) continue
            @Suppress("UNCHECKED_CAST") action(entityOf(id), va as A, vb as B, vc as C, vd as D, ve as E2, vf as F, vg as G)
        }
    }

    // ── Жизненный цикл ───────────────────────────────────────────────
    fun update(dt: Float) {
        for (s in systems) s.updateTickPublic(dt)
    }

    fun dispose() { systems.clear(); stores.clear(); aliveFlags.clear() }

    internal fun flags(): MutableList<Boolean> = aliveFlags

    companion object {
        internal val EMPTY = EWorld(WorldBuilder())
        fun make(builder: WorldBuilder): EWorld = EWorld(builder)
    }
}

private fun BaseSystem.updateTickPublic(dt: Float) {
    when (this) {
        is IntervalSystem -> updateTick(dt)
        else -> { delta = dt; update() }
    }
}

class EntitySet internal constructor(private val w: EWorld) {
    fun contains(id: Int): Boolean = w.flags().getOrNull(id) == true
}

class WorldBuilder {
    val systemsList = ArrayList<BaseSystem>()
    fun component(default: () -> Any) { /* lazy-stores: регистрация no-op */ default() }
    fun systems(block: SystemsBuilder.() -> Unit) { systemsList += SystemsBuilder().apply(block).list }
}

fun world(block: WorldBuilder.() -> Unit): EWorld = EWorld(WorldBuilder().apply(block))
