# BilliardTracker (Android)

Android-приложение для учёта любительских турниров русского бильярда. Kotlin + Jetpack Compose + Material 3. Backend — `billiardtracker.alekseylosev.ru` (см. `E:\PROJECTS\LAV-Server\srv\billiardtracker\`).

## Что делает

- **14 дисциплин**: свободная / комбинированная / динамичная / классическая пирамида, малая и большая русская партия, алагёр, ярославская, колхоз, фишки, один карман, грош, свободная с продолжением, европейская пирамида. Правила в `shared/rules/*.md`, читаются приложением и бекендом.
- **Offline-first**: приложение полностью работает без сети. Все мутации (создать турнир, начать партию, забить шар, создать команду и т.д.) сначала пишутся в Room, затем ставятся в outbox. Sync-worker разгребает очередь по цепочке зависимостей при возврате интернета. Индикатор «⏳ N» в правом верхнем углу показывает число pending-операций. **v1.21.1** — id-remap gridlock после cascade `create_tournament → start_game`: ViewModel держал stale local negative id, а Room-игру SyncManager уже перевёл под серверный id → `getById(negId)` возвращал null → `resolveEndpoint` вставал в null → op'ы вечно pending, ⏳ рос. Фикс: in-memory + persistent (UserPrefs) tid/gid/pid remap + bypass id-resolve для self-creating ops (`create_tournament`, `start_game`) + multi-pass drain. `GameRepository.listGames`/`finishGame`/`addShot` + `TournamentRepository.fetchDetail` резолвят через SyncManager перед Room/API lookup. Как следствие починилась и «Партия окончена» (была симптомом gridlock — `finishGame(negGid)` промахивался мимо Room-игры).
- **Регистрация без SMS**: одна форма Имя + Телефон + confirm-диалог → атомарная связка `POST /api/auth/register` + `POST /api/tokens/ensure-default`. Никакого SMS-гейта.
- **Пути мастера**: opaque-токены как «каналы» турниров для группы друзей. Владелец создаёт/удаляет/ротирует, шарит ссылкой; получатель тапает — приложение через App Link подписывается и подгружает историю турниров. Активный путь фильтрует и вкладку «Игра», и «Статистику». При создании нового пути имя авто-инкрементируется («№1» → «№2») если уже занято.
- **Составы-пресеты (offline)**: несколько именованных составов, каждый — список игроков. Активный состав используется при создании встречи; игроки автоподхватываются из контактов телефона. Создание/переименование/удаление составов и игроков работает без сети. Владелец телефона больше не добавляется автоматом в участники встречи — на экране ставки кнопка «Добавить владельца телефона» (v1.24.0).
- **Фильтр дисциплин на главном (v1.21.0)**: юзер выбирает в Настройках, какие из 14 дисциплин показывать кнопками на главном при старте встречи. Полный справочник правил всегда доступен.
- **Главная = «Новая встреча» (v1.21.0)**: сразу выбор дисциплины одним тапом, под кнопками — идущие встречи. Сыгранные встречи ушли во вкладку Статистика (тап по карточке — открыть Итоги).
- **Турнир до N побед (1–10)**: авто-выбор победителя каждой партии по счёту, счётчик побед, баннер чемпиона при достижении N.
- **Live-счёт (v1.22.0 UX-редизайн + v1.24.0 плитки на 100% ширины + «+/Штраф/Свой/Чужой»)**: экран встречи адаптируется под правила дисциплины. Пирамиды с равноценными шарами (Свободная, Комбинированная, Динамичная, Ярославка, Европейская, One Pocket, Свободная-с-продолжением) — крупный «+» и «Штраф» в плитке игрока, ниже отдельные «Чужой» и «Свой» для явного вида шара. `Штраф` играет роль «−» (удаляет последний забитый шар, чтобы победитель не определялся по «меньше штрафа»). Классическая / Малая 61 / Большая 71 — знакомая сетка 15 шаров через bottom-sheet при тапе на плитку игрока. Алагёр / Грош — сетка «крестиков» жизней; тап убирает жизнь, 0 = «выбыл». Layout: 2–4 игрока → split top-to-bottom (каждая плитка во всю ширину), 5+ → скроллящийся список. Confirm-dialog на «Партия окончена» защищает от misdaction. Победитель партии проставляется оптимистично локально, чтобы 🏆 не пропадал в истории 2-й/3-й/… партии пока finish-op ещё в outbox. Kolkhoz / Fishki остаются на fallback через ball-grid (v1.23.0+). Маркёр вводит удары, остальные видят обновления через SSE.
- **Стать Маркёром**: любой участник может «перехватить» роль маркёра одним тапом — остальным приходит toast «X стал маркёром». Требует онлайн (SSE).
- **Payout с неттингом**: цепочки долгов A→B→C схлопываются до минимального числа транзакций. Учитывает per-participant handicap + individual ставки. Донат разработчику (3/5/10%) — опционально.
- **Statistics**: W/L, процент побед, всего забитых шаров, средний счёт за партию по активному пути мастера.
- **Клубы SPb+ЛО**: гео-детект ближайшего бара при создании турнира, авто-подстановка в название с выпадающим списком подсказок.
- **Автообновление в-приложении**: скачивание APK в кеш → системный installer (без похода в браузер). Требует включённое «Установка из неизвестных источников» для BilliardTracker.

## Bottom nav (5 табов)

`Игра` · `Составы` · `Профиль` · `Статистика` · `Настройки`

Правила дисциплин доступны через `Настройки → Правила игр` (перенесены из bottom-nav в v1.19.0 — освободили место).

## Стек

- **UI**: Compose (BOM 2025.12+), Material 3, Navigation Compose, Coil.
- **Data**: Retrofit + kotlinx-serialization, OkHttp SSE, Room (v4) + KSP, DataStore Preferences.
- **Offline**: outbox pattern (`data/sync/SyncManager` + `data/local/OutboxDao`), `NetworkMonitor` через `ConnectivityManager`, `LocalIdGenerator` (AtomicLong с отрицательными ID для local-first), cascade FK-remap при получении server-id.
- **DI**: ручной AppContainer (см. `di/AppContainer.kt`).
- **Deep links**: `https://billiardtracker.alekseylosev.ru/live/<token>` → App Link (assetlinks.json на бекенде).
- **minSdk 28, targetSdk 36, compileSdk 37**. R8 minify + shrinkResources → APK ~5 МБ.

## Разработка

```bash
./gradlew :app:assembleRelease   # требует BT_KEYSTORE_PATH / _KEY_ALIAS / _STORE_PASSWORD / _KEY_PASSWORD env
./gradlew :app:test              # unit-тесты (PayoutCalculator и др.)
```

## Релиз

Все секреты (`BT_KEYSTORE_PATH`, `BT_KEY_*`, `GITHUB_TOKEN`) — в `BilliardTracker/.env` (gitignored, бэкап в `~/.keystores/billiardtracker.env`). Скрипт сам их подхватит:

```bash
node E:/PROJECTS/LAV-Server/bin/.deploy/release-billiardtracker.mjs
```

Что делает:
1. Читает `versionCode`/`versionName` из `app/build.gradle.kts`.
2. `gradlew :app:assembleRelease` — подписанный APK.
3. SFTP на VPS → `/srv/billiardtracker/releases/v{version}/billiardtracker.apk` + `version.json`, обновляет симлинк `latest`.
4. Создаёт GH release `v{version}` c APK-asset'ом и удаляет ВСЕ старые release'ы + git-теги (на GH держим только последнюю — auto-updater юзеров ходит на VPS, GH — просто зеркало).
5. Смартфоны получат алерт «Доступно обновление» через встроенный auto-updater (проверка на каждом `ON_RESUME`).

Подробности подписи и `.env` — в [`SIGNING.md`](SIGNING.md).

## SemVer

- `PATCH` — мелкий фикс/косметика.
- `MINOR` — новая фича (крупная UI-переработка, новый экран, новый endpoint).
- `MAJOR` — breaking-change сервера. Держим `1.x` (пока не выкатывали в Google Play).

## Файловая структура

```
app/src/main/java/com/example/billiardtracker/
├── data/
│   ├── local/       # Room (AppDatabase v4, entities: tournament/game/shot/team/team_member/outbox_ops), LocalIdGenerator
│   ├── prefs/       # DataStore (UserPrefs, UpdatePrefs)
│   ├── remote/      # Retrofit + DTO + SseClient
│   ├── repo/        # Repositories (Tournament/Game/Team/Auth/Token/... — все offline-first)
│   ├── sync/        # NetworkMonitor + SyncManager (outbox drainer, cascade ID remap)
│   ├── location/    # LocationProvider (гео-детект клуба)
│   └── contacts/    # ContactsReader (READ_CONTACTS для подсказок)
├── di/              # AppContainer (ручной DI + Room migrations 1→2→3→4)
├── domain/          # rules (GameType, PayoutCalculator), usecase (DetectClubUseCase)
├── ui/
│   ├── components/  # BilliardTopBar, UpdatePromptDialog
│   ├── nav/         # BilliardNavHost (+ pending-sync overlay-бейдж), TeamState, NewTournamentState
│   ├── screens/     # onboarding, home, team, profile, rules, stats, settings,
│   │                # gametype (PickGameType + StakeSetup), tournament (+ PayoutScreen), club
│   └── theme/       # Color, Theme (bilyard green + gold accent)
├── util/            # PhoneMask, ApkInstaller
└── MainActivity.kt  # deep-link entry, auto-updater host
shared/rules/        # markdown правил (используется app И backend)
```

## Backend

См. `E:\PROJECTS\LAV-Server\srv\billiardtracker\README.md`.
