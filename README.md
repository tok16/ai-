# 🎮 Neon Grip: Протокол «Хват»

Top-Down Twin-Stick Shooter (Bullet-Hell / Roguelite) в неоновом киберпанке. Kotlin + LibGDX + Fleks ECS.

## ⬇️ Скачать

| Версия | Файл | Для чего |
|--------|------|----------|
| **v1.0** | [📦 Проект для Android Studio (zip)](https://github.com/tok16/ai-/releases/download/v1.0/neon-grip-android-studio.zip) | Открыть и собрать самому |
| **v1.0** | [🤖 APK (когда соберётся CI)](https://github.com/tok16/ai-/releases/latest) | Установить на телефон |

Все релизы: https://github.com/tok16/ai-/releases — скачивание идёт через CDN GitHub, без регистрации.

### Как открыть проект
1. Распакуйте zip.
2. Android Studio → File → Open → выберите папку `neon-grip`.
3. Дождитесь Gradle Sync (нужен JDK 17 + Android SDK 34).
4. Сборка APK: `./gradlew :android:assembleDebug`

## 🤖 Автоматическая сборка APK

Файл `.github-workflow-android-release.yml` лежит в корне репозитория. GitHub запускает автосборку APK только если workflow находится по пути `.github/workflows/`. Включите его одним действием: на странице репозитория **Add file → Create new file → имя: `.github/workflows/android-release.yml` → содержимое скопируйте из файла в корне → Commit**. После этого при пуше тега `v*` Actions соберёт подписанный APK и сам прикрепит его к Release.

Также запуск можно сделать вручную: вкладка **Actions → Build Android APK → Run workflow**.

## 🕹️ Управление (хват 3 пальцев)
- **Левый большой палец** — плавающий стик передвижения (8 направлений)
- **Правый большой палец** — стик прицела, автоогонь при отклонении
- **Правый указательный палец (триггер)** — рывок / фазовый сдвиг; долгое удержание — перезарядка

Рывок сквозь пулю включает **bullet-time** и автонуведение. Убийства без урона заряжают шкалу **Синхронизации**.

## 🧱 Структура
```
neon-grip/
├── core/      # игровая логика ECS, системы, боты-перестрелщики (crossfire)
├── android/   # SurfaceView, мультитач, immersive mode, safe area
├── desktop/   # запуск на ПК для отладки
└── docs/      # дизайн-документация
```
