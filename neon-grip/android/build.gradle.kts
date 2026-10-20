// Модуль :android — точка входа, SurfaceView, иммерсивный режим, Safe Area (WindowInsets)
plugins {
    id("com.android.application")
    kotlin("android")
}

val libsGdxVersion: String = rootProject.extra["libgdxVersion"] as String
val coroutinesVersion: String = rootProject.extra["kotlinCoroutinesVersion"] as String

android {
    namespace = "com.neongrip.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.neongrip.protocolgrip"
        minSdk = 26            // Multi-pointer pointerId API стабилен с 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-prototip"
    }

    // Строго горизонтальная ориентация enforced через manifest + here
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(project(":core"))

    // LibGDX backend для Android (OpenGL ES 3.0 рендерер поверх SurfaceView)
    implementation("com.badlogicgames.gdx:gdx-backend-android:$libsGdxVersion")
    natives("com.badlogicgames.gdx:gdx-platform:$libsGdxVersion:natives-armeabi-v7a")
    natives("com.badlogicgames.gdx:gdx-platform:$libsGdxVersion:natives-arm64-v8a")

    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")
}

// Вспомогательная функция для нативных .so библиотек gdx-platform
fun DependencyHandlerScope.natives(notation: String) = add("implementation", notation)
