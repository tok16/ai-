# LibGDX использует рефлексию для сервисов — сохраняем её API
-keep class com.badlogic.gdx.** { *; }
-keep class com.neongrip.core.components.** { *; }  # ECS-компоненты мутируются по рефлексии Fleks
-dontwarn com.badlogic.gdx.backends.android.AndroidFragmentApplication
