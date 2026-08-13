# 2026-08-13 — Дизайн: hotfix v1.21.1 + редизайн встречи v1.22.0

## Контекст

`todo.md` фиксирует 6 хаотичных мыслей после ручного smoke-теста v1.21.0:

1. Экран встречи должен подстраиваться под правила выбранной игры (число игроков, важность номеров шаров).
2. Игры с равноценными шарами → быстрые ± кнопки вместо раскладки 15.
3. 2 игрока → split-screen.
4. 3+ игроков → равные части.
5. Кнопка «Партия окончена» не работает.
6. Sync с VPS сломан: ⏳ badge растёт, POST-ов на бэк нет.

Пункты 5–6 — блокирующие баги. Пункты 1–4 — UX-редизайн, который требует классификации всех 14 дисциплин по паттернам ввода счёта. Дизайн разбивает работу на два релиза.

---

## Часть 1. v1.21.1 — hotfix (пп. 5, 6)

Приложение сейчас непригодно: встречи не завершаются, счётчик несинхронизированных операций растёт неограниченно. Быстрый узкий релиз убирает блокер и даёт запас для крупного редизайна.

### 1.1 Sync-gridlock (todo #6)

**Корневая причина** (диагностирована в незакоммиченном diff `SyncManager.kt`):

- `create_tournament` кладёт в outbox с `localTournamentId = negTid` (отрицательный, локально сгенерированный).
- Как только SyncManager получает 201, `onSuccess`-каскад делает `delete(oldEntity)` + `insert(serverEntity)` в Room и remap'ит `localTournamentId` у зависимых outbox-ops.
- **Но:** ViewModel держит `negTid` в состоянии (fromRoom() возвращён до remap'а). Следующий `start_game` / `add_shot` заезжает в outbox с уже протухшим `negTid`. `tournamentDao.getById(negTid)` → `null` (entity удалена), `resolveEndpoint` возвращает `null`, op остаётся навсегда pending → gridlock, ⏳ растёт.

**Что уже сделано** (см. `git diff` — WIP, не закоммичено):

- In-memory `ConcurrentHashMap<Long, Long>` для tournament/game/participant id remap.
- Persistent-fallback через `UserPrefs.setIdRemap()` (переживает kill процесса между 201 и enqueue дочернего op).
- **Bypass для «self-creating» ops** — `create_tournament` и `start_game` не блокируются отсутствием serverId (их endpoint не содержит соответствующего id).
- **Multi-pass drain** — после каждого прохода re-fetch pending, повторяем пока `count` уменьшается (cascade разблокировал что-то) или максимум 8 проходов.
- `GameRepository.addShot` резолвит `participantId` через `syncManager.resolveParticipantId()`.
- `TournamentRepository.fetchDetail` резолвит `id` через `syncManager.resolveTournamentId()`.
- `versionCode = 77`, `versionName = 1.21.1` уже проставлены.

**Что осталось**:

- **Диагностика в эмуляторе**: полный сценарий (регистрация → создать встречу → партия → +1/-1 → «Партия окончена» → закрыть встречу). Проверить `/dev-logs`:
  - должны появиться события `tournament-remap` и `game-remap`;
  - `drain-end.passes` > 1 при cascade;
  - pending count после сценария = 0;
  - на VPS в БД появились соответствующие турнир/партия/удары.
- **Регрессия**: тот же сценарий offline → включить сеть → всё должно догнаться.
- **Мини-тест** (`SyncManagerTest`) на remap-fallback: unit-тест, эмулирующий последовательность create_tournament(201) → enqueue start_game(negTid) → drain должен успеть.
- **README/бэкенд-доки не трогаем** — фикс внутренний, публичного API не меняет.

### 1.2 «Партия окончена» (todo #5)

**Гипотеза A (наиболее вероятна)**: это визуальный симптом sync-gridlock.

- `GameRepository.finishGame` оптимистично ставит `game.status = "finished"` в Room, кладёт op в outbox, дёргает drain.
- ViewModel вызывает `refresh()` → `tournamentRepo.fetchDetail(tid)` — если tid отрицательный и не смапился, `fromRoom(tid)` возвращает исходную запись, где `status` **уже** обновлён (или, при race, ещё старый). При sync-gridlock партия локально финишится, но visual state / список партий может остаться неактуальным из-за stale fetch.

**Гипотеза B**: UI не перерисовывается — `refresh()` возвращает данные раньше, чем `upsert()` фиксирует Room-транзакцию.

**План диагностики** (после hotfix 1.1):

1. Запустить sync-фикс в эмуляторе.
2. Провести партию, нажать «Партия окончена».
3. Ожидание: `currentGame.status = "finished"` → `RefereePanel` скрывается → появляется «Начать партию» + строка в «Партии».
4. Если после sync-фикса кнопка работает — закрыть таск.
5. Если нет — залезть в `TournamentViewModel.finishGame` / `refresh()` / `fromRoom()` и найти реальный root cause (не переходить сразу к «пофиксил, потому что»).

### 1.3 Релиз v1.21.1

1. Закоммитить WIP в `SyncManager`, `GameRepository`, `TournamentRepository`, `UserPrefs`, `AppContainer`, `build.gradle.kts` одним коммитом `fix(sync): resolve id-remap gridlock for cascade create ops`.
2. Прогнать сценарий на эму + на реальном устройстве (auto-updater).
3. `./release-apk.sh` (или существующий скрипт релиза), GH release latest-only, VPS deploy.
4. Обновить `README` только если сломанное поведение было документировано.

---

## Часть 2. v1.22.0 — Редизайн экрана встречи (пп. 1–4)

**Цель**: экран встречи выглядит и работает нативно под правила выбранной дисциплины. Пользователь никогда не должен думать «а какие кнопки для этой игры».

### 2.1 Классификация 14 дисциплин по UI-паттернам

Из `RuleProfile.kt`:

| Группа | Дисциплины | UI-паттерн (`ScorerKind`) |
|---|---|---|
| **A. Numbered** — номер шара = очки | Classical (71), Small Russian (61), Big Russian (71) | `NumberedBallGrid` (текущий RefereePanel, доработать) |
| **B. Equal-ball** — все шары по 1 очку | Free, Combined, Dynamic, Free-Continuation, Yaroslavka, European, One Pocket (до 8 шаров) | `CounterScorer` (±1 крупные кнопки) |
| **C. Lives** — жизни/кресты, на выбывание | Alagyor, Grosh | `LivesScorer` (сетка «крестов», tap = минус жизнь) |
| **D. Balance** — баланс с каждым | Kolkhoz | `BalanceScorer` (v1.23.0, отложено) |
| **E. Special inventory** — карамболь + кегли | Fishki | `FishkiScorer` (v1.23.0, отложено) |

**Реализуем в v1.22.0**: A (обновление), B (новое), C (новое).
**Откладываем на v1.23.0** (YAGNI, крайне редкие сценарии): D, E — оставить fallback на `NumberedBallGrid` с note.

### 2.2 Layout по количеству игроков (`MatchLayout`)

Все в **portrait** — переворот экрана не трогаем. Тестируется одной рукой.

- **2 игрока** → split top/bottom. Каждая плитка = ½ высоты минус bottom action bar.
- **3–4 игрока** → 2×2 grid. При 3-х игроках нижняя-правая ячейка = «Действия партии» (undo, «Партия окончена»).
- **5+ игроков** → вертикальный список плиток, каждая ≈ 20% высоты, скроллится.

Ниже — общая нижняя панель (`MatchBottomBar`): `↶ Undo`, `Партия окончена`, target-индикатор («до 8 побед · Иван 3 · Пётр 2»).

**Плитка игрока** (`ScoreTile`):

```
┌─────────────────────┐
│ Иван 🎩         5   │  ← имя (+ маркёр 🎩 если он маркёр), крупный счёт
│                     │
│  [  −  ]   [  +  ]  │  ← ±1 (для группы B) — большие тап-таргеты
│                     │
│  Свояк   Штраф      │  ← опциональные действия по RuleProfile
└─────────────────────┘
```

Для группы A (`NumberedBallGrid`) плитка выглядит иначе — 3×5 сетка шаров под именем + счёт справа.
Для группы C (`LivesScorer`) — крупные ✕✕✕ (или ♥♥♥) под именем, тап = убрать одну; participants с 0 жизней уходят в «выбыл».

### 2.3 Архитектура

**Изменения в `RuleProfile`** (`app/src/main/java/com/example/billiardtracker/domain/rules/RuleProfile.kt`):

Добавить поле `scorerKind: ScorerKind`:

```kotlin
enum class ScorerKind { NumberedBallGrid, Counter, Lives, Balance, Fishki }
```

Заполнить для всех 14 типов согласно таблице 2.1.

**Новые composable** (`app/src/main/java/com/example/billiardtracker/ui/screens/tournament/scorers/`):

- `MatchLayout.kt` — распределяет `ScoreTile`-ы по grid/split в зависимости от `participants.size`.
- `ScoreTile.kt` — общий контейнер (имя, счёт, слот для scorer-специфичного контента).
- `CounterScorer.kt` — ±1 кнопки + Свояк/Штраф по флагам `RuleProfile`.
- `LivesScorer.kt` — сетка жизней, tap = decrement.
- `NumberedBallGridScorer.kt` — рефакторинг существующего `RefereePanel` в per-tile форму.
- `MatchBottomBar.kt` — undo + finish + target-индикатор.

**Замена** в `TournamentScreen.kt`:

```kotlin
if (ui.isReferee && cg?.status != "finished") {
    val profile = RuleProfile.forType(t.gameType)
    MatchLayout(
        participants = t.participants,
        scorerKind = profile.scorerKind,
        state = ui.currentGame,
        shots = ui.currentGameShots,
        onShot = viewModel::addShot,
        onUndo = viewModel::undoLastShot,
        onFinish = viewModel::finishGame,
    )
}
```

Минимальные изменения в `TournamentViewModel`: добавить `decrementScore(pid: Long)` — удаляет последний positive shot указанного участника в текущей партии (не путать с общим `undoLastShot`, который тянет **любой** последний shot независимо от игрока). `GameRepository.deleteShot` уже подходит. Для `CounterScorer` шлём `kind="ball", ballNumber=null, pointsDelta=+1` на плюсе.

### 2.4 UX-детали

- **Минус в CounterScorer** = «убрать последнее очко у этого игрока». Реализуем через новый `viewModel.decrementScore(pid)` — удалить последний positive shot этого игрока в текущей партии. Кнопка disabled когда `scores[pid] == 0`. Undo в bottom-bar остаётся общим (тянет **любой** последний shot).
- **Свояк** = отдельный shot `kind="own_ball", pointsDelta=+1` (уже есть в текущем `Shot`-протоколе). В MVP считаем только +1 у игрока — house-rules «Комбинированной» (+ право снять шар у соперника) и «Ярославки» (свояк = -1 у соперника через тап) выполняются вручную (просто минусует у соперника). Полноценная rule-driven логика — v1.23.0+.
- **Штраф** = `kind="foul", pointsDelta=-1` (или `-5` для Классики — на основе `RuleProfile`).
- **За борт** — только для группы A, там где актуально.
- **Смена маркёра**: 🎩 может быть у любого игрока. Только маркёр видит ± кнопки; observer видит только счёт (`ObserverPanel` заменяется на readonly-версию `MatchLayout`).
- **Target-индикатор**: `winTargetPoints` / `winTargetBalls` из `RuleProfile` показывается в `MatchBottomBar` («до 8» или «до 71»). Автовыбор победителя партии при достижении target остаётся через существующий `finishGame(null)`.

### 2.5 Что НЕ делаем в v1.22.0 (YAGNI)

- Balance-scorer (Колхоз) и Fishki — отложены. В обоих UI непопулярных дисциплин остаётся текущий `NumberedBallGrid` с notes-баннером «правила требуют ручного учёта».
- Landscape-layout. Всё portrait. Тесты быстрее, дизайн проще.
- Онлайн-претестирование multiplayer-tap (типа «каждый игрок с телефона тапает свою половину»). Только маркёр вводит счёт — остальные watch-only.
- Кастомный `winTargetPoints` per-match. Берём из RuleProfile.

### 2.6 Roadmap задач для v1.22.0

1. **RuleProfile: добавить `scorerKind`** — простое data-class изменение, обновить `forType()` для 14 типов, добавить unit-тест.
2. **MatchLayout + ScoreTile** — базовый скелет, без scorer'ов внутри (заглушки).
3. **CounterScorer** — реализовать ±1, Свояк, Штраф + `viewModel.decrementScore()`.
4. **LivesScorer** — сетка жизней, decrement по тапу, «выбыл» state.
5. **NumberedBallGridScorer** — плитка игрока показывает имя + текущий счёт; тап на плитку раскрывает **общую** bottom-sheet с сеткой 15 шаров + действиями (Свояк / Штраф / За борт), выбранный игрок помечен, ball-tap = shot на этого игрока и sheet автозакрывается. Плюс: сетка 3×5 остаётся крупной; нет тесноты внутри 2×2 grid.
6. **Wire routing** в `TournamentScreen`: заменить `RefereePanel`/`ObserverPanel` на `MatchLayout(profile.scorerKind, isReferee)`.
7. **Smoke-тест на эму**: пробежать по одной игре из каждой группы (A, B, C) с 2 и 3 игроками. Скриншоты в `design/`.
8. **Bump v1.22.0** (versionCode 78) + README + landing update + release.

### 2.7 Открытые дизайн-вопросы (решаются в план-фазе / имплементации)

- **Undo scope**: одна общая кнопка undo (топ-бар) или per-tile «↶» рядом с ±? Проще одна общая. Если игрок тапнул случайно ±, undo убирает последний shot независимо от игрока — это может быть неочевидно. Решим на smoke-тесте.
- **Long-press на ±** = +5 / −5? Полезно для Классики (туз=11) — но там всё равно NumberedBallGrid. Для CounterScorer вряд ли нужно. Решить в имплементации через feedback.
- **Confirm dialog на «Партия окончена»**: сейчас его нет — тап сразу финишит. Добавить `AlertDialog("Завершить партию? Победитель: Иван — 8 очков")`? Скорее да, чтобы избежать misdaction.

---

## Success criteria

### v1.21.1
- В `/dev-logs` за смок-сессию виден хотя бы один `tournament-remap` и `game-remap`.
- Pending outbox count после смок-сессии = 0.
- В backend БД видны созданные турниры/партии/удары.
- «Партия окончена» финишит партию и разблокирует «Начать партию».

### v1.22.0
- Открываю встречу «Свободная пирамида» с 2 игроками → вижу split top/bottom с крупными ± кнопками, без сетки 15 шаров.
- Открываю «Классическую» с 2 игроками → вижу знакомый ball-grid.
- Открываю «Алагёр» с 3 игроками → вижу 2×2 grid, у каждого 3 креста, тап убирает крест.
- Все три сценария завершаются партией и корректно синкаются на VPS.

---

## Не в scope этой спеки

- Онлайн-multiplayer (каждый со своего телефона). Только маркёрская модель.
- Backend-изменения. Всё UI-only + мелкие изменения в data-слое (ScorerKind в enum).
- Fishki и Kolkhoz UI (v1.23.0+).
- Landscape.
- Кастомные target'ы per-match.
