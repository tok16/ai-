package com.neongrip.core.ecs

/**
 * Внутренняя ECS-подложка (замена библиотечного Fleks, чей артефакт недоступен
 * в публичных Maven-репозиториях). Семантика DSL сохранена 1-в-1: world { component {} },
 * entity { add(...) }, systems { add(...) }, iterate(vararg KClass) { e, c1, c2 -> }.
 *
 * Хранение: dense ArrayList на класс компонента — системы идут по ним последовательно
 * (cache-friendly), GC не видит аллокаций в бою. Миграция обратно на Fleks = замена импортов.
 */
class Entity internal constructor(
    private val idValue: Int,
    private val stores: MutableMap<kotlin.reflect.KClass<*>, MutableList<Any?>>,
    private val aliveFlags: MutableList<Boolean>,
) {
    fun id(): Int = idValue
    fun isAlive(): Boolean = aliveFlags.getOrNull(idValue) == true

    inline operator fun <reified T : Any> get(k: kotlin.reflect.KClass<T>): T? = storeFor(T::class).getOrNull(idValue) as? T
    inline fun <reified T : Any> has(): Boolean = storeFor(T::class).getOrNull(idValue) != null

    @PublishedApi
    internal fun storeFor(c: kotlin.reflect.KClass<*>): MutableList<Any?> = stores.getOrPut(c) { mutableListOf() }
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
    private val aliveFlags = ArrayList<Boolean>()
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

    /** Плотный проход: коллбэк вызывается только для живых сущностей со ВСЕМИ компонентами */
    inline fun iterate(vararg classes: kotlin.reflect.KClass<*>, action: (Entity) -> Unit) {
        if (classes.isEmpty()) return
        val sa = stores[classes[0]] ?: return
        outer@ for (id in sa.indices) {
            if (aliveFlags.getOrNull(id) != true) continue
            for (c in classes) if (stores[c]?.getOrNull(id) == null) continue@outer
            action(Entity(id, stores, aliveFlags))
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
