package com.neongrip.android

import android.os.Bundle
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.neongrip.core.NeonGripGame
import com.neongrip.core.SafeArea
import kotlinx.coroutines.launch

/**
 * Точка входа Neon Grip. Наследуется от AndroidApplication (LibGDX backend),
 * который внутри создаёт SurfaceView — прямой OpenGL ES 3.0 рендер.
 *
 * Закрытые «подводные камни» Android из ТЗ:
 * 1. Мультитач: LibGDX прокидывает все pointerId из MotionEvent; immersive sticky
 *    режим глушит системные свайпы от края экрана, иначе ACTION_CANCEL убивает жест.
 * 2. Edge-to-Edge + Notch: WindowInsets → Safe Area → StateFlow в ядре; спавн врагов
 *    и diegetic UI сдвигаются внутрь безопасной зоны.
 * 3. Ориентация: строго landscape (manifest sensorLandscape), FLAG_KEEP_SCREEN_ON.
 */
class NeonGripActivity : AndroidApplication() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Edge-to-edge: контент рисуется под статус-баром и навигацией ──────────
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true       // LibGDX сам прячет system bars (sticky-immersive)
            useWakelock = false           // не держим CPU wake-lock: батарея важнее
            useAccelerometer = false
            useCompass = false
        }
        initialize(NeonGripGame(), config)

        // ── Читаем insets и транслируем Safe Area в игровой стейт ─────────────────
        view.setOnApplyWindowInsetsListener { _, insets ->
            lifecycleScope.launch {
                val cutout = if (android.os.Build.VERSION.SDK_INT >= 28)
                    insets.displayCutout?.let { dc ->
                        SafeArea(dc.safeInsetLeft.toFloat(), dc.safeInsetTop.toFloat(),
                                 dc.safeInsetRight.toFloat(), dc.safeInsetBottom.toFloat())
                    } ?: SafeArea.EMPTY else SafeArea.EMPTY
                NeonGripGame.safeAreaFlow.value = maxOfInsets(
                    SafeArea(insets.systemWindowInsetLeft.toFloat(),
                             insets.systemWindowInsetTop.toFloat(),
                             insets.systemWindowInsetRight.toFloat(),
                             insets.systemWindowInsetBottom.toFloat()),
                    cutout,
                )
            }
            insets
        }

        enableImmersiveSticky()
    }

    /** Полная блокировка системных жестов: back/swipe не должен перехватывать касания у краёв */
    private fun enableImmersiveSticky() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.hide(WindowInsetsCompat.Type.displayCutout())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveSticky() // после возврата из фона жесты снова нужно заглушить
    }

    private fun maxOfInsets(a: SafeArea, b: SafeArea) = SafeArea(
        left = maxOf(a.left, b.left), top = maxOf(a.top, b.top),
        right = maxOf(a.right, b.right), bottom = maxOf(a.bottom, b.bottom),
    )
}
