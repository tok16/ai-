package com.neongrip.core.input

import com.badlogic.gdx.math.Vector2
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Эргономика «философии 3 пальцев».
 *
 * Каждый палец — отдельный логический контроллер, отвязанный от позиции на экране.
 * Обработка идёт по уникальному pointerId: при отпускании одного пальца другие
 * продолжают работать (критично для мультитача на Android, где ACTION_UP одного
 * указателя не должен обнулять состояние остальных).
 */
class ThreeFingerGripController : com.badlogic.gdx.InputProcessor {

    // ── Пальцы как слоты ────────────────────────────────────────────────────
    private var leftThumbId = INVALID_POINTER   // большой палец левой руки → movement stick
    private var rightThumbId = INVALID_POINTER  // большой палец правой руки → aim & fire stick
    private var rightIndexId = INVALID_POINTER  // указательный правой → trigger (рывок/ульта/перезарядка)

    // ── Выходные векторы, которые читает PlayerControlSystem каждый кадр ────
    val moveVector = Vector2()   // 8 направлений (снап к октантам — как D-pad консоли)
    val aimVector = Vector2()    // ненулевой = автоматическая стрельба
    var triggerJustPressed = false; private set
    var triggerHeldMillis = 0L; private set      // долгое удержание = тактическая перезарядка
    var rightStickDoubleTap = false; private set // двойной тап по правому стика = перезарядка

    // Плавающий левый стик: появляется там, где впервые коснулись левой половиной экрана
    private val leftOrigin = Vector2()
    private val rightOrigin = Vector2()
    private val deadZone = STICK_DEAD_ZONE_PX
    private val maxRadius = STICK_MAX_RADIUS_PX

    private var lastRightTapUptime = 0L

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        val x = screenX.toFloat(); val y = screenY.toFloat()
        val w = screenWidthPx; val h = screenHeightPx

        when {
            // Триггер-зона: верхняя часть правой стороны, но сдвинутая на ~12% внутрь экрана —
            // иначе палец соскальзывает на физическую рамку/чехол (missed inputs).
            isInTriggerDeadZoneSafe(x, y, w, h) && rightIndexId == INVALID_POINTER -> {
                rightIndexId = pointer
                triggerJustPressed = true
                triggerHeldMillis = 0
            }

            // Левая половина → плавающий стик движения (рождается под пальцем)
            x < w * LEFT_HALF_RATIO && leftThumbId == INVALID_POINTER -> {
                leftThumbId = pointer
                leftOrigin.set(x, y)
            }

            // Правая нижняя половина → стик прицела/огня
            rightThumbId == INVALID_POINTER -> {
                // Двойной тап по зоне правого стика = тактическая перезарядка
                val now = System.currentTimeMillis()
                if (now - lastRightTapUptime < DOUBLE_TAP_MS) rightStickDoubleTap = true
                lastRightTapUptime = now
                rightThumbId = pointer
                rightOrigin.set(x, y)
            }
        }
        return true // потребляем событие: LibGDX не отдаст его системным обработчикам
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int) {
        when (pointer) {
            leftThumbId -> snapTo8(computeStick(leftOrigin, screenX, screenY), into = moveVector)
            rightThumbId -> aimVector.set(computeStick(rightOrigin, screenX, screenY))
        }
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int) {
        // ВАЖНО: освобождаем ТОЛЬКО соответствующий слот по pointerId.
        // Отпускание левого большого пальца не должно сбрасывать прицел правой руки.
        when (pointer) {
            leftThumbId -> { leftThumbId = INVALID_POINTER; moveVector.set(0f, 0f) }
            rightThumbId -> { rightThumbId = INVALID_POINTER; aimVector.set(0f, 0f) }
            rightIndexId -> { rightIndexId = INVALID_POINTER }
        }
    }

    // Остальные методы InputProcessor — дефолтные (false): нам нужны только касания
    override fun keyDown(keycode: Int) = false
    override fun keyUp(keycode: Int) = false
    override fun keyTyped(character: Char) = false
    override fun scrolled(amountX: Float, amountY: Float) = false
    override fun mouseMoved(screenX: Int, screenY: Int) = false

    /** Вызывается из GameLoop каждый кадр: считает время удержания триггера */
    fun update(dt: Float) {
        if (rightIndexId != INVALID_POINTER) triggerHeldMillis += (dt * 1000).toLong()
        else triggerHeldMillis = 0
        // Долгое удержание триггера (>=600мс) — перезарядка; короткий тап — рывок.
        // Разделение происходит здесь, чтобы системы получали уже семантические события.
    }

    fun consumeTriggerPress(): Boolean = triggerJustPressed.also { triggerJustPressed = false }
    fun consumeDoubleTap(): Boolean = rightStickDoubleTap.also { rightStickDoubleTap = false }
    fun isTriggerHeldLong(): Boolean = triggerHeldMillis >= LONG_PRESS_MS

    // ── Геометрия стиков ────────────────────────────────────────────────────
    private fun computeStick(origin: Vector2, sx: Int, sy: Int): Vector2 {
        val v = TMP.set((sx - origin.x), (origin.y - sy)) // экран Y инвертирован
        val len = v.len()
        if (len < deadZone) return ZERO
        return v.scl(minOf(len, maxRadius) / len) // нормализованный до 1.0
    }

    /** Снап к 8 направлениям — эмуляция D-pad: палец дрожит, направление стабильно */
    private fun snapTo8(v: Vector2, into: Vector2) {
        if (v.len2 < 0.0001f) { into.set(0f, 0f); return }
        val angleDeg = (atan2(v.y.toDouble(), v.x.toDouble()) * RAD2DEG).toFloat()
        val octant = (((angleDeg / 45f) + 0.5f).toInt() % 8 + 8) % 8
        into.set(OCTANTS[octant]).scl(if (v.len() > 0.9f) 1f else 0.7f)
    }

    companion object {
        const val INVALID_POINTER = -1
        const val STICK_DEAD_ZONE_PX = 24f
        const val STICK_MAX_RADIUS_PX = 140f
        const val LEFT_HALF_RATIO = 0.45f
        const val DOUBLE_TAP_MS = 280L
        const val LONG_PRESS_MS = 600L
        private const val RAD2DEG = 57.29578f

        var screenWidthPx = 1280
        var screenHeightPx = 720

        // Триггерная зона: правые ~18% ширины, верхние ~30% высоты, минус inset от края корпуса
        fun isInTriggerDeadZoneSafe(x: Float, y: Float, w: Float, h: Float): Boolean =
            x in (w * 0.70f)..(w * 0.88f) && y in (h * 0.02f)..(h * 0.30f)

        private val OCTANTS = Array(8) { i ->
            Vector2(cos(i * PI / 4).toFloat(), sin(i * PI / 4).toFloat())
        }
        private val TMP = Vector2()
        private val ZERO = Vector2(0f, 0f)
    }
}
