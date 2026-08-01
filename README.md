# BilliardTracker (Android)

Android-приложение для учёта любительских турниров русского бильярда. Kotlin + Jetpack Compose + Material 3. Backend — `billiardtracker.alekseylosev.ru` (см. `E:\PROJECTS\LAV-Server\srv\billiardtracker\`).

## Что делает

- **14 дисциплин**: свободная / комбинированная / динамичная / классическая пирамида, малая и большая русская партия, алагёр, ярославская, колхоз, фишки, один карман, грош, свободная с продолжением, европейская пирамида. Правила в `shared/rules/*.md`, читаются приложением и бекендом.
- **Регистрация без SMS**: одна форма Имя + Телефон + confirm-диалог → атомарная связка `POST /api/auth/register` + `POST /api/tokens/ensure-default`. Никакого SMS-гейта.
- **Пути мастера**: opaque-токены как «каналы» турниров для группы друзей. Владелец создаёт/удаляет/ротирует, шарит ссылкой; получатель тапает — приложение через App Link подписывается и подгружает историю турниров. Активный путь фильтрует и вкладку «Игра», и «Статистику».
- **Команды-пресеты**: несколько именованных команд, каждая — список игроков. Активная команда используется при создании турнира; игроки автоподхватываются из контактов телефона.
- **Турнир до N побед (1–10)**: авто-выбор победителя каждой партии по счёту, счётчик побед, баннер чемпиона при достижении N.
- **Live-счёт**: маркёр вводит удары (шары, свояк, штраф, за борт, отменить), остальные видят обновления через SSE.
- **Payout с неттингом**: цепочки долгов A→B→C схлопываются до минимального числа транзакций. Учитывает per-participant handicap + individual ставки.
- **Statistics**: W/L, процент побед, всего забитых шаров, средний счёт за партию по активному пути мастера.
- **Автообновление в-приложении**: скачивание APK в кеш → системный installer (без похода в браузер). Требует включённое «Установка из неизвестных источников» для BilliardTracker.

## Bottom nav (6 табов)

`Игра` · `Команды` · `Профиль` · `Правила` · `Статистика` · `Настройки`

## Стек

- **UI**: Compose (BOM 2025.12+), Material 3, Navigation Compose, Coil.
- **Data**: Retrofit + kotlinx-serialization, OkHttp SSE, Room + KSP, DataStore Preferences.
- **DI**: ручной AppContainer (см. `di/AppContainer.kt`).
- **Deep links**: `https://billiardtracker.alekseylosev.ru/live/<token>` → App Link (assetlinks.json на бекенде).
- **minSdk 28, targetSdk 36, compileSdk 37**. R8 minify + shrinkResources → APK ~5 МБ.

## Разработка

```bash
./gradlew :app:assembleRelease   # требует BT_KEYSTORE_PATH / _KEY_ALIAS / _STORE_PASSWORD / _KEY_PASSWORD env
./gradlew :app:test              # unit-тесты (PayoutCalculator и др.)
```

## Релиз

Из репо `LAV-Server`:

```powershell
$env:BT_KEYSTORE_PATH="C:/Users/LAV/.keystores/billiardtracker.keystore"
$env:BT_KEY_ALIAS="billiardtracker"
$env:BT_STORE_PASSWORD="..."
$env:BT_KEY_PASSWORD="..."
node E:/PROJECTS/LAV-Server/bin/.deploy/release-billiardtracker.mjs
```

Скрипт:
1. Читает `versionCode`/`versionName` из `app/build.gradle.kts`.
2. Запускает `gradlew :app:assembleRelease`.
3. Загружает подписанный APK на VPS в `/srv/billiardtracker/releases/v{version}/billiardtracker.apk`.
4. Генерит `version.json`, обновляет симлинк `latest`.
5. Смартфоны юзеров получат алерт «Доступно обновление» через встроенный auto-updater (проверка на каждом `ON_RESUME`).

## SemVer

- `PATCH` — мелкий фикс/косметика.
- `MINOR` — новая фича (крупная UI-переработка, новый экран, новый endpoint).
- `MAJOR` — breaking-change сервера. Держим `1.x` (пока не выкатывали в Google Play).

## Файловая структура

```
app/src/main/java/com/example/billiardtracker/
├── data/            # local (Room), prefs (DataStore), remote (Retrofit + DTO), repo, contacts
├── di/              # AppContainer (ручной DI)
├── domain/          # rules (GameType, PayoutCalculator), usecase
├── ui/
│   ├── components/  # BilliardTopBar, UpdatePromptDialog
│   ├── nav/         # BilliardNavHost, TeamState, NewTournamentState
│   ├── screens/     # onboarding, home, team, profile, rules, stats, settings,
│   │                # gametype (PickGameType + StakeSetup), tournament (+ PayoutScreen), club
│   └── theme/       # Color, Theme (bilyard green + gold accent)
├── util/            # PhoneMask, ApkInstaller
└── MainActivity.kt  # deep-link entry, auto-updater host
shared/rules/        # markdown правил (используется app И backend)
```

## Backend

См. `E:\PROJECTS\LAV-Server\srv\billiardtracker\README.md`.
