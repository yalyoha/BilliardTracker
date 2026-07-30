# BilliardTracker — план реализации

> **Для агентов-исполнителей:** REQUIRED SUB-SKILL — используйте `superpowers:subagent-driven-development` (рекомендуется) или `superpowers:executing-plans`, чтобы идти по чек-боксам (`- [ ]`).

**Goal:** MVP кроссплатформенного CRM для учёта любительских бильярдных турниров в Санкт-Петербурге и Ленобласти. Android-клиент (Kotlin + Compose) + облачный REST-бэкенд на `billiardtracker.alekseylosev.ru` + справочник клубов + официальные правила русского бильярда.

**Architecture.** Android-клиент (Compose, Material 3, offline-first через Room) синхронизируется с Node.js/Hono backend (SQLite + Drizzle) на VPS. Единственный источник истины — сервер: любой участник турнира может стать "Маркёром" (судья, редактирует счёт), остальные видят live-обновления через SSE. Идентификация участников по номеру телефона. Гео: определение клуба по координатам из справочника.

**Tech Stack.**
- Android: Kotlin 2.2, Jetpack Compose (Material 3), Retrofit + kotlinx.serialization, Room, DataStore, Coroutines/Flow, Coil, Play Services Location, ContactsContract.
- Backend: Node.js ≥20, Hono, Drizzle ORM, better-sqlite3, JWT (jose), SSE. Systemd unit, nginx reverse-proxy, certbot.
- Дев-инструменты: Vitest/node:test, ktlint, junit4, MockWebServer, Compose UI testing.

---

## Scope Check — почему один plan.md, а не три

По правилам `superpowers:writing-plans` многосубсистемный спек обычно бьётся на отдельные планы. Здесь одна доменная модель (турнир → участники → партии → удары), поэтому вместо трёх планов используем **фазы с явными gate'ами**:

- **Фаза 0** — инфра и безопасность (обязательно первым).
- **Фаза 1** — доменные данные и правила (общие для клиента и сервера).
- **Фаза 2** — Backend API + БД.
- **Фаза 3** — Android клиент (offline-first).
- **Фаза 4** — Клубы SPb/LO + гео-детект.
- **Фаза 5** — Продакшн-деплой (nginx, systemd, SSL).

После каждой фазы — checkpoint-коммит и ручная проверка "запускается / API отвечает / экран рисуется".

---

## Файловая структура (что появится к концу плана)

### Android (`E:\PROJECTS\BilliardTracker\`)
```
app/src/main/java/com/example/billiardtracker/
├── MainActivity.kt                 (уже есть; обвязать NavHost)
├── BilliardApp.kt                  (Application, DI, Room, DataStore init)
├── di/AppContainer.kt              (ручной DI без Hilt — YAGNI на MVP)
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt          (Room)
│   │   ├── dao/{TournamentDao,ParticipantDao,GameDao,ShotDao,ClubDao,RuleDao}.kt
│   │   └── entity/{TournamentEntity,ParticipantEntity,GameEntity,ShotEntity,ClubEntity,RuleEntity}.kt
│   ├── remote/
│   │   ├── ApiService.kt           (Retrofit interface)
│   │   ├── dto/*.kt                (network DTO + @Serializable)
│   │   ├── AuthInterceptor.kt      (JWT bearer)
│   │   └── SseClient.kt            (OkHttp EventSource wrapper для live-score)
│   ├── repo/
│   │   ├── AuthRepository.kt
│   │   ├── TournamentRepository.kt
│   │   ├── GameRepository.kt
│   │   ├── ClubRepository.kt
│   │   └── RuleRepository.kt
│   └── prefs/UserPrefs.kt          (DataStore: JWT, phone, referee-lock state)
├── domain/
│   ├── model/*.kt                  (чистые data-class: Tournament, Game, Shot, Player, RuleSet…)
│   ├── rules/
│   │   ├── GameType.kt             (enum всех 15 дисциплин)
│   │   ├── ScoreCalculator.kt      (перевод серии Shot→Score с учётом штрафов/форы/удвоений)
│   │   └── PayoutCalculator.kt     (игра на деньги — разность шаров × ставка, с индивидуальными ставками)
│   └── usecase/
│       ├── CreateTournamentUseCase.kt
│       ├── AddShotUseCase.kt
│       ├── ClaimRefereeUseCase.kt
│       ├── DetectClubUseCase.kt
│       └── SyncTournamentUseCase.kt
├── ui/
│   ├── theme/{Color,Theme,Type}.kt (уже есть — оставить)
│   ├── nav/BilliardNav.kt          (NavHost, route sealed class)
│   ├── screens/
│   │   ├── auth/PhoneAuthScreen.kt
│   │   ├── home/HomeScreen.kt      (список турниров + "новый турнир")
│   │   ├── contacts/PickParticipantsScreen.kt (ContactsContract + ручной ввод)
│   │   ├── gametype/PickGameTypeScreen.kt
│   │   ├── tournament/
│   │   │   ├── TournamentScreen.kt     (live-счёт, кнопки шаров, "Маркёр")
│   │   │   ├── RefereePanel.kt         (кнопки: забил / штраф / фола / свояк)
│   │   │   ├── ObserverPanel.kt        (read-only view)
│   │   │   └── PayoutSheet.kt          (итог: кто кому сколько)
│   │   ├── rules/
│   │   │   ├── RulesListScreen.kt
│   │   │   └── RuleDetailScreen.kt
│   │   └── clubs/
│   │       ├── ClubListScreen.kt
│   │       └── AddClubScreen.kt        (если не нашли — предложить добавить)
│   └── components/*.kt             (общие Composable: BallChip, ScoreCounter, ParticipantCard…)
└── util/{PhoneNormalizer,Haversine,SseFlow}.kt

app/src/test/java/…              (JUnit + kotlinx.coroutines.test — юниты чистой логики)
app/src/androidTest/java/…       (Compose UI test + Room in-memory)
```

### Backend (`E:\PROJECTS\LAV-Server\srv\billiardtracker\`)
```
backend/
├── package.json
├── .env.example
├── src/
│   ├── index.js                    (buildApp + serve на :3021)
│   ├── db/
│   │   ├── index.js                (openDb)
│   │   └── schema.js               (drizzle-orm/sqlite-core)
│   ├── middleware/
│   │   ├── bearer.js               (JWT verify)
│   │   ├── referee.js              (проверка: current user = tournament.referee_id)
│   │   └── rate-limit.js
│   ├── routes/
│   │   ├── health.js
│   │   ├── auth.js                 (POST /request-code, /verify → JWT)
│   │   ├── users.js                (GET /me, PATCH /me/name)
│   │   ├── tournaments.js          (CRUD + list mine)
│   │   ├── participants.js         (add/remove, поиск по номеру)
│   │   ├── games.js                (start/finish партии)
│   │   ├── shots.js                (POST добавить удар — только маркёр)
│   │   ├── referee.js              (POST /tournaments/:id/claim-referee)
│   │   ├── stream.js               (SSE: live-обновления партии)
│   │   ├── clubs.js                (GET/POST — справочник)
│   │   └── rules.js                (GET — правила всех дисциплин)
│   ├── seed/
│   │   ├── seed-clubs.js           (справочник клубов SPb/LO)
│   │   ├── seed-rules.js           (тексты правил всех дисциплин + FRB-ссылки)
│   │   └── run-seed.js
│   └── scripts/migrate.js
├── data/                           (SQLite файл; .gitignored)
├── test/
│   ├── auth.test.js
│   ├── tournaments.test.js
│   ├── shots.test.js               (в т.ч. проверка referee-lock)
│   ├── stream.test.js
│   └── payout.test.js              (тот же алгоритм что в Android — параллельная проверка)
└── drizzle.config.js

systemd/lav-billiardtracker.service
```

### Общие ассеты (плоские файлы)
```
E:\PROJECTS\BilliardTracker\shared\
├── clubs.spb-lo.json               (справочник — фазa 4)
├── rules.russian-pyramid.md        (официальные правила — фазa 1)
├── rules.moscow-pyramid.md
├── … (по одному .md на дисциплину)
└── glossary.md
```

---

## Задачи

Ниже задачи проходят строго по TDD-циклу: test → run-fail → impl → run-pass → commit. Каждый шаг ≤5 минут.

---

# ФАЗА 0 — Инфра и безопасность

### Task 0.1: Устранить утечку токена, добавить .env в .gitignore, подключить git

**Files:**
- Modify: `E:\PROJECTS\BilliardTracker\.gitignore`
- Modify: `E:\PROJECTS\BilliardTracker\.env` (переименовать/удалить содержимое)
- Create: `E:\PROJECTS\BilliardTracker\.env.example`

- [ ] **Step 0.1.1: Проверить что в `.env` действительно есть live-токен.** Прочитать файл, если есть `GITHUB_TOKEN=ghp_…` — считать его скомпрометированным (был в открытом виде в WD).

- [ ] **Step 0.1.2: Добавить `.env` в `.gitignore`.**

```gitignore
*.iml
.gradle
/local.properties
/.idea/caches
/.idea/libraries
/.idea/modules.xml
/.idea/workspace.xml
/.idea/navEditor.xml
/.idea/assetWizardSettings.xml
.DS_Store
/build
/captures
.externalNativeBuild
.cxx
local.properties
.env
```

- [ ] **Step 0.1.3: Создать `.env.example` без секретов.**

```dotenv
# Personal Access Token с scope repo для деплоя (НЕ коммитить фактический токен)
GITHUB_TOKEN=
```

- [ ] **Step 0.1.4: Явно попросить пользователя ротировать токен на github.com/settings/tokens** (пометить чек-боксом в этом плане — не делаем автоматически, у нас нет прав удалять токены).

- [ ] **Step 0.1.5: `git init`, добавить remote, НЕ пушить.**

```powershell
git init
git branch -M main
git remote add origin https://github.com/yalyoha/BilliardTracker.git
git status  # убедиться, что .env в untracked но игнорится (не появится в git status)
```

- [ ] **Step 0.1.6: Commit skeleton.**

```powershell
git add .gitignore .env.example app build.gradle.kts settings.gradle.kts gradle gradle.properties gradlew gradlew.bat todo.md plan.md
git commit -m "chore: initial Android skeleton + plan + gitignore

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

Ждём подтверждения пользователя перед `git push -u origin main` (destructive для пустого remote — может быть protected).

---

### Task 0.2: Инициализация backend-проекта

**Files:**
- Create: `E:\PROJECTS\LAV-Server\srv\billiardtracker\backend\package.json`
- Create: `E:\PROJECTS\LAV-Server\srv\billiardtracker\backend\.env.example`
- Create: `E:\PROJECTS\LAV-Server\srv\billiardtracker\backend\src\index.js`
- Create: `E:\PROJECTS\LAV-Server\srv\billiardtracker\backend\.gitignore`

- [ ] **Step 0.2.1: `package.json`** — по образцу `srv/whereami/backend/package.json`, порт 3021.

```json
{
  "name": "lav-billiardtracker-backend",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "node --watch src/index.js",
    "start": "node src/index.js",
    "test": "node --test --test-reporter=spec test/*.test.js",
    "db:generate": "drizzle-kit generate",
    "db:migrate": "node scripts/migrate.js",
    "db:seed": "node src/seed/run-seed.js"
  },
  "dependencies": {
    "@hono/node-server": "^1.13.0",
    "better-sqlite3": "^11.7.0",
    "dotenv": "^16.4.5",
    "drizzle-orm": "^0.38.0",
    "hono": "^4.6.0",
    "jose": "^5.9.6",
    "zod": "^3.23.8"
  },
  "devDependencies": {
    "drizzle-kit": "^0.30.0"
  }
}
```

- [ ] **Step 0.2.2: `.env.example`**

```dotenv
PORT=3021
JWT_SECRET=change-me-min-32-bytes
DB_PATH=./data/billiardtracker.sqlite
# SMS-провайдер (заглушка на dev: выводим код в лог)
SMS_PROVIDER=stub
SMS_API_KEY=
```

- [ ] **Step 0.2.3: `.gitignore`**

```gitignore
node_modules
data
.env
*.sqlite
*.sqlite-journal
```

- [ ] **Step 0.2.4: Скелет `src/index.js` с health-check.**

```javascript
import 'dotenv/config';
import { Hono } from 'hono';
import { serve } from '@hono/node-server';
import { logger } from 'hono/logger';

export function buildApp() {
  const app = new Hono();
  app.use('*', logger());
  app.get('/api/health', (c) => c.json({ ok: true, service: 'billiardtracker', ts: Date.now() }));
  return app;
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const port = Number(process.env.PORT ?? 3021);
  serve({ fetch: buildApp().fetch, port }, ({ port }) => {
    console.log(`[billiardtracker] listening on :${port}`);
  });
}
```

- [ ] **Step 0.2.5: `npm install && npm start` → curl `http://localhost:3021/api/health` → `{"ok":true,…}`.**

- [ ] **Step 0.2.6: Commit.**

```powershell
git -C E:\PROJECTS\LAV-Server add srv/billiardtracker
git -C E:\PROJECTS\LAV-Server commit -m "feat(billiardtracker): backend skeleton with health-check"
```

---

# ФАЗА 1 — Доменные данные (правила игр)

### Task 1.1: Заглушки правил всех дисциплин с TODO-ссылками на официальные источники

**Files:**
- Create: `E:\PROJECTS\BilliardTracker\shared\rules\` — по одному `.md` на дисциплину.

Дисциплины (из todo.md строк 18–31 + официальный реестр FRB):
1. `svobodnaya-piramida.md` — Свободная пирамида ("Американка")
2. `kombinirovannaya-piramida.md` — Комбинированная ("Московская")
3. `dinamichnaya-piramida.md` — Динамичная ("Невская"/"Сибирская")
4. `klassicheskaya-piramida.md` — Классическая ("Русская пирамида / 71 очко")
5. `svobodnaya-s-prodolzheniem.md` — Свободная с продолжением
6. `malaya-russkaya-partiya.md`
7. `bolshaya-russkaya-partiya.md`
8. `alagyor.md` — Алагёр
9. `yaroslavskaya-piramida.md`
10. `kolkhoz.md` — Колхоз / Купец / Шведка
11. `fishki.md` — Фишки (карамболь с фишками)
12. `odin-karman.md` — Один карман (One Pocket)
13. `grosh.md` — Грош / Круг
14. `evropeyskaya-piramida.md`

- [ ] **Step 1.1.1: Заголовки каждого файла.** Единый шаблон (заполнять контентом на шаге 1.2 после research):

```markdown
# <Название дисциплины>

**Официальный источник:** <URL FRB / документ / TODO — заполнить исследованием>

## Инвентарь
- <шары, биток, стол>

## Начальная расстановка
<схема / текст>

## Порядок ходов
<кто бьёт, переход хода>

## Начисление очков
<стоимость шаров, штрафы, свои>

## Условие победы
<до какого счёта / шаров>

## Особые правила
<фора, дубли, штрафы за биток в лузу и т.п.>
```

- [ ] **Step 1.1.2: Заполнить каждый файл кратким описанием из `todo.md`** (строки 18–31). Пометить `## Источник: TODO — сверить с FRB` — заполнение полноценным официальным текстом = Task 1.2, отдельный research-раунд с WebSearch/WebFetch.

- [ ] **Step 1.1.3: Commit.**

```powershell
git add shared/rules/*.md
git commit -m "docs(rules): stub описаний 14 дисциплин русского бильярда"
```

---

### Task 1.2: Research — официальные правила (отдельный сеанс с WebSearch)

- [ ] **Step 1.2.1: Диспатч Agent (`Explore` или `general-purpose`) с WebSearch/WebFetch:**
  - основной источник — Федерация бильярдного спорта России (FBSR / FRB): `https://frbsr.ru`;
  - альтернативы: Правила пирамиды 2019/2023 (PDF), IBSF pyramid rules;
  - задача: по каждой дисциплине из Task 1.1 вытащить оригинальные формулировки: расстановка, стоимость шаров, штрафы, свои, условие победы.
- [ ] **Step 1.2.2: Обновить каждый `shared/rules/*.md` дословными цитатами (с указанием источника и даты редакции документа).**
- [ ] **Step 1.2.3: Commit** `docs(rules): официальные формулировки из FRB`.

---

### Task 1.3: Определить GameType enum и профиль правил (общий для клиента/сервера)

**Files:**
- Create: `E:\PROJECTS\BilliardTracker\app\src\main\java\com\example\billiardtracker\domain\rules\GameType.kt`
- Create: `E:\PROJECTS\BilliardTracker\app\src\main\java\com\example\billiardtracker\domain\rules\RuleProfile.kt`
- Create: `E:\PROJECTS\LAV-Server\srv\billiardtracker\backend\src\domain\rule-profiles.js`
- Test: `E:\PROJECTS\BilliardTracker\app\src\test\java\…\rules\RuleProfileTest.kt`

- [ ] **Step 1.3.1: Написать unit-тест для профиля Классической пирамиды: 15 шаров с номиналами 1..15, target=71.**

```kotlin
class RuleProfileTest {
    @Test fun `classical pyramid has 15 balls valued 1 to 15 and target 71`() {
        val p = RuleProfile.forType(GameType.CLASSICAL_PYRAMID)
        assertEquals(15, p.ballValues.size)
        assertEquals((1..15).toList(), p.ballValues)
        assertEquals(71, p.winTargetPoints)
        assertTrue(p.allowsHandicap)
        assertFalse(p.tabbedBallsAllowed) // не фишки
    }
}
```

- [ ] **Step 1.3.2: Запустить — FAIL.**

```powershell
gradlew.bat :app:testDebugUnitTest --tests "*RuleProfileTest*"
```

- [ ] **Step 1.3.3: Реализовать `GameType`:**

```kotlin
package com.example.billiardtracker.domain.rules

enum class GameType(val displayName: String, val ruleFileSlug: String) {
    FREE_PYRAMID("Свободная пирамида", "svobodnaya-piramida"),
    COMBINED_PYRAMID("Комбинированная (Московская)", "kombinirovannaya-piramida"),
    DYNAMIC_PYRAMID("Динамичная (Невская)", "dinamichnaya-piramida"),
    CLASSICAL_PYRAMID("Классическая (71 очко)", "klassicheskaya-piramida"),
    FREE_PYRAMID_CONTINUATION("Свободная с продолжением", "svobodnaya-s-prodolzheniem"),
    SMALL_RUSSIAN_PARTY("Малая русская партия", "malaya-russkaya-partiya"),
    BIG_RUSSIAN_PARTY("Большая русская партия", "bolshaya-russkaya-partiya"),
    ALAGYOR("Алагёр", "alagyor"),
    YAROSLAVSKAYA("Ярославская пирамида", "yaroslavskaya-piramida"),
    KOLKHOZ("Колхоз (Купец / Шведка)", "kolkhoz"),
    FISHKI("Фишки", "fishki"),
    ONE_POCKET_RU("Один карман (по-русски)", "odin-karman"),
    GROSH("Грош / Круг", "grosh"),
    EUROPEAN_PYRAMID("Европейская пирамида", "evropeyskaya-piramida"),
}
```

- [ ] **Step 1.3.4: Реализовать `RuleProfile`:**

```kotlin
data class RuleProfile(
    val type: GameType,
    val ballValues: List<Int>,     // 15 или 16 (с фишками)
    val winTargetPoints: Int?,     // null = играют до фиксированного числа шаров
    val winTargetBalls: Int?,      // например 8 для One Pocket
    val allowsSvoiak: Boolean,     // забитие битка засчитывается очками
    val svoiakReturnedToHome: Boolean,
    val alternatesTurnAlways: Boolean, // "переход хода при любом ударе"
    val allowsHandicap: Boolean,
    val allowsDouble: Boolean,     // право слабому на дубль
    val tabbedBallsAllowed: Boolean,
    val moneyPlayable: Boolean,    // допускает игру на деньги/шар
) {
    companion object {
        fun forType(type: GameType): RuleProfile = when (type) {
            GameType.CLASSICAL_PYRAMID -> RuleProfile(
                type, ballValues = (1..15).toList(),
                winTargetPoints = 71, winTargetBalls = null,
                allowsSvoiak = false, svoiakReturnedToHome = false,
                alternatesTurnAlways = false, allowsHandicap = true,
                allowsDouble = false, tabbedBallsAllowed = false, moneyPlayable = true,
            )
            // …остальные 13 профилей — по одному case, значения из shared/rules/*.md
            else -> TODO("fill profile from shared/rules/${type.ruleFileSlug}.md")
        }
    }
}
```

- [ ] **Step 1.3.5: Запустить тест классической — PASS. Затем добавить тест-кейсы по каждой дисциплине и заполнить остальные `when`-ветки.**

- [ ] **Step 1.3.6: Зеркалировать enum + профили на backend (`src/domain/rule-profiles.js`) — тот же список, ключи snake_case.**

```javascript
export const RULE_PROFILES = {
  classical_pyramid: {
    ballValues: [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15],
    winTargetPoints: 71, /* … */
  },
  // …
};
```

- [ ] **Step 1.3.7: Тест на backend (`test/rule-profiles.test.js`) — parity: список ключей совпадает с Android enum.**

- [ ] **Step 1.3.8: Commit.**

```
feat(rules): GameType enum + RuleProfile для 14 дисциплин (Android + backend)
```

---

# ФАЗА 2 — Backend API

### Task 2.1: DB схема (Drizzle)

**Files:**
- Create: `backend/src/db/schema.js`
- Create: `backend/src/db/index.js`
- Test: `backend/test/db.test.js`

- [ ] **Step 2.1.1: Тест — миграция создаёт таблицы users/tournaments/participants/games/shots/clubs.**

```javascript
import test from 'node:test';
import assert from 'node:assert/strict';
import { openDb } from '../src/db/index.js';

test('schema creates all expected tables', () => {
  const db = openDb(':memory:');
  const rows = db.$client.prepare(`SELECT name FROM sqlite_master WHERE type='table'`).all();
  const names = rows.map(r => r.name).sort();
  ['users','tournaments','participants','games','shots','clubs'].forEach(t =>
    assert.ok(names.includes(t), `missing table ${t}`));
});
```

- [ ] **Step 2.1.2: Run — FAIL (no schema).**

- [ ] **Step 2.1.3: Написать `schema.js` (drizzle sqliteTable):**

```javascript
import { sqliteTable, integer, text, real } from 'drizzle-orm/sqlite-core';

export const users = sqliteTable('users', {
  id: integer('id').primaryKey({ autoIncrement: true }),
  phone: text('phone').notNull().unique(),          // E.164, +7…
  name: text('name'),
  createdAt: integer('created_at').notNull(),
});

export const clubs = sqliteTable('clubs', {
  id: integer('id').primaryKey({ autoIncrement: true }),
  name: text('name').notNull(),
  address: text('address'),
  lat: real('lat').notNull(),
  lon: real('lon').notNull(),
  city: text('city'),           // 'Санкт-Петербург' | 'Ленинградская область' | …
  userAdded: integer('user_added').notNull().default(0),
  addedByUserId: integer('added_by_user_id'),
});

export const tournaments = sqliteTable('tournaments', {
  id: integer('id').primaryKey({ autoIncrement: true }),
  title: text('title'),
  clubId: integer('club_id'),
  gameType: text('game_type').notNull(),   // ключ RULE_PROFILES
  moneyPerBallKop: integer('money_per_ball_kop'), // ставка в копейках, null = не денежная
  createdByUserId: integer('created_by_user_id').notNull(),
  refereeUserId: integer('referee_user_id'),      // текущий Маркёр
  status: text('status').notNull(),               // 'active' | 'finished'
  startedAt: integer('started_at').notNull(),
  finishedAt: integer('finished_at'),
});

export const participants = sqliteTable('participants', {
  id: integer('id').primaryKey({ autoIncrement: true }),
  tournamentId: integer('tournament_id').notNull(),
  userId: integer('user_id'),          // null = гость, только имя
  displayName: text('display_name').notNull(),
  handicapPoints: integer('handicap_points').notNull().default(0),
  perBallOverrideKop: integer('per_ball_override_kop'), // slabee игроку -
});

export const games = sqliteTable('games', {
  id: integer('id').primaryKey({ autoIncrement: true }),
  tournamentId: integer('tournament_id').notNull(),
  orderIndex: integer('order_index').notNull(),
  status: text('status').notNull(),    // 'active' | 'finished'
  startedAt: integer('started_at').notNull(),
  finishedAt: integer('finished_at'),
  winnerParticipantId: integer('winner_participant_id'),
});

export const shots = sqliteTable('shots', {
  id: integer('id').primaryKey({ autoIncrement: true }),
  gameId: integer('game_id').notNull(),
  participantId: integer('participant_id').notNull(),
  kind: text('kind').notNull(),        // 'ball' | 'svoiak' | 'foul' | 'ball_out'
  ballNumber: integer('ball_number'),  // 1..15, null для foul
  pointsDelta: integer('points_delta').notNull(),
  ts: integer('ts').notNull(),
  enteredByUserId: integer('entered_by_user_id').notNull(), // кто-был-маркёром
});
```

- [ ] **Step 2.1.4: `db/index.js`:**

```javascript
import Database from 'better-sqlite3';
import { drizzle } from 'drizzle-orm/better-sqlite3';
import fs from 'node:fs';
import path from 'node:path';

const SCHEMA_DDL = fs.readFileSync(new URL('./schema.sql', import.meta.url), 'utf-8');

export function openDb(dbPath = process.env.DB_PATH ?? './data/billiardtracker.sqlite') {
  if (dbPath !== ':memory:') fs.mkdirSync(path.dirname(dbPath), { recursive: true });
  const sqlite = new Database(dbPath);
  sqlite.pragma('journal_mode = WAL');
  sqlite.pragma('foreign_keys = ON');
  sqlite.exec(SCHEMA_DDL);
  return drizzle(sqlite);
}
```

Плюс сгенерировать `schema.sql` через `npm run db:generate` (drizzle-kit) — commit результат.

- [ ] **Step 2.1.5: Run test → PASS. Commit.**

---

### Task 2.2: Auth (телефон + SMS-код → JWT)

**Files:**
- Create: `backend/src/routes/auth.js`
- Create: `backend/src/middleware/bearer.js`
- Create: `backend/src/services/sms.js`  (stub-провайдер)
- Test: `backend/test/auth.test.js`

- [ ] **Step 2.2.1: Тест happy-path.**

```javascript
import test from 'node:test';
import assert from 'node:assert/strict';
import { buildApp } from '../src/index.js';
import { openDb } from '../src/db/index.js';

test('phone auth flow: request-code → verify → JWT', async () => {
  const db = openDb(':memory:');
  const app = buildApp({ db, env: { JWT_SECRET: 'a'.repeat(32), SMS_PROVIDER: 'stub' } });

  const r1 = await app.request('/api/auth/request-code', {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ phone: '+79111234567' }),
  });
  assert.equal(r1.status, 200);
  const { debugCode } = await r1.json();  // в dev-режиме возвращаем код в теле
  assert.match(debugCode, /^\d{4}$/);

  const r2 = await app.request('/api/auth/verify', {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ phone: '+79111234567', code: debugCode }),
  });
  assert.equal(r2.status, 200);
  const { token } = await r2.json();
  assert.ok(token.split('.').length === 3);
});
```

- [ ] **Step 2.2.2: Run → FAIL. Реализовать `auth.js`, `sms.js` (stub возвращает код в лог + в response при `SMS_PROVIDER=stub`), `bearer.js` (jose verify).**

- [ ] **Step 2.2.3: Тест ошибок: неверный код → 400, phone не в E.164 → 400, rate-limit `request-code` >5/мин.**

- [ ] **Step 2.2.4: Commit `feat(auth): phone+SMS-code auth with JWT`.**

---

### Task 2.3: Tournaments CRUD + participants

**Files:**
- Create: `backend/src/routes/tournaments.js`
- Create: `backend/src/routes/participants.js`
- Test: `backend/test/tournaments.test.js`

- [ ] **Step 2.3.1: Тесты:**
  - POST `/api/tournaments` создатель = referee по умолчанию.
  - GET `/api/tournaments/mine` — только те, где я участник.
  - POST `/api/tournaments/:id/participants` — добавляет по phone (создаёт `users` если нет) или гостя (без userId).
  - Валидация: только creator/referee может редактировать состав до начала первой партии.

- [ ] **Step 2.3.2: Реализовать. Commit.**

---

### Task 2.4: Games + shots + referee-lock

**Files:**
- Create: `backend/src/routes/games.js`, `backend/src/routes/shots.js`, `backend/src/routes/referee.js`
- Create: `backend/src/middleware/referee.js`
- Test: `backend/test/shots.test.js`, `backend/test/referee.test.js`

- [ ] **Step 2.4.1: Тесты правильного referee-lock:**
  - user A = referee → POST /shots ok.
  - user B (участник, не referee) → POST /shots 403.
  - POST /tournaments/:id/claim-referee → любой участник становится referee, предыдущий теряет права.

- [ ] **Step 2.4.2: Реализовать роуты + middleware. Каждый POST /shots публикует event в SSE-хаб.**

- [ ] **Step 2.4.3: Commit `feat(games): shots + referee-lock`.**

---

### Task 2.5: SSE-стрим live-обновлений

**Files:**
- Create: `backend/src/routes/stream.js`
- Create: `backend/src/services/sse-hub.js`
- Test: `backend/test/stream.test.js`

- [ ] **Step 2.5.1: Тест — подписываемся на `/api/tournaments/:id/stream`, POST-им shot, получаем event внутри 500ms.**

- [ ] **Step 2.5.2: Реализовать hub (Map<tournamentId, Set<controller>>). Commit.**

---

### Task 2.6: Clubs + rules routes

**Files:**
- Create: `backend/src/routes/clubs.js`, `backend/src/routes/rules.js`
- Create: `backend/src/seed/seed-rules.js`, `backend/src/seed/seed-clubs.js`, `backend/src/seed/run-seed.js`
- Create: `shared/clubs.spb-lo.json`
- Test: `backend/test/clubs.test.js`

- [ ] **Step 2.6.1: `clubs.spb-lo.json` — пустой стартовый массив (наполняется в фазе 4).**

- [ ] **Step 2.6.2: GET /api/clubs?near=lat,lon&radiusM=… → сортировка по haversine.**

- [ ] **Step 2.6.3: POST /api/clubs (auth) — пользовательский вклад, помечается `userAdded=1`.**

- [ ] **Step 2.6.4: GET /api/rules/:slug — возвращает markdown из `shared/rules/…md` (fs.readFileSync с кешем).**

- [ ] **Step 2.6.5: seed-скрипт заливает 14 rules + начальные клубы (пусто пока).**

- [ ] **Step 2.6.6: Commit.**

---

### Task 2.7: Расчёт выплат (Backend parity с Android)

**Files:**
- Create: `backend/src/domain/payout.js`
- Test: `backend/test/payout.test.js`

- [ ] **Step 2.7.1: Тесты (парные тем, что напишем в Android Task 3.5):**
  - 2 игрока, ставка 100 руб/шар, счёт 8:5 → проигравший должен победителю 300.
  - индивидуальная ставка слабому игроку 50 руб/шар → пересчёт по каждому шару отдельно.
  - handicap +3 очка слабому — вычитается из результата сильного при подсчёте разности.
  - штраф (`kind='foul'`) добавляет 1 шар сопернику по СТАВКЕ соперника.

- [ ] **Step 2.7.2: Реализация. Commit.**

---

### Task 2.8: Backend gate — интеграционный smoke-test

- [ ] **Step 2.8.1: Полный сценарий в одном тесте: 2 auth → create tournament → add 2 participants → start game → 5 shots → finish → payout.**
- [ ] **Step 2.8.2: `npm test` — весь suite зелёный.**
- [ ] **Step 2.8.3: Commit + tag `backend-mvp-v0.1`.**

---

# ФАЗА 3 — Android клиент

### Task 3.1: Зависимости

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 3.1.1: Добавить в libs.versions.toml:**

```toml
[versions]
retrofit = "2.11.0"
okhttp = "4.12.0"
kotlinxSerialization = "1.7.3"
kotlinxSerializationRetrofit = "1.0.0"
room = "2.7.0"
navCompose = "2.8.4"
coroutines = "1.9.0"
datastore = "1.1.1"
coil = "2.7.0"
playServicesLocation = "21.3.0"
mockwebserver = "4.12.0"

[libraries]
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
retrofit-kotlinx-serialization = { group = "com.jakewharton.retrofit", name = "retrofit2-kotlinx-serialization-converter", version.ref = "kotlinxSerializationRetrofit" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-sse = { group = "com.squareup.okhttp3", name = "okhttp-sse", version.ref = "okhttp" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx-nav-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navCompose" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }
play-services-location = { group = "com.google.android.gms", name = "play-services-location", version.ref = "playServicesLocation" }
mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "mockwebserver" }

[plugins]
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version = "2.2.10-1.0.28" }
```

- [ ] **Step 3.1.2: Прописать плагины/зависимости в `app/build.gradle.kts`.**

- [ ] **Step 3.1.3: Sync + build — успех. Commit.**

---

### Task 3.2: Room + миграция моделей

**Files:** entity/, dao/, `AppDatabase.kt`, `AppContainer.kt`, `BilliardApp.kt`
**Test:** `app/src/androidTest/java/…/data/local/DaoTest.kt`

- [ ] **Step 3.2.1: androidTest в Room `inMemoryDatabaseBuilder` — вставка Tournament + Participant + Shot, чтение обратно.**
- [ ] **Step 3.2.2: Реализовать entities/DAO/Database. `@Database(version=1, entities=[…])`.**
- [ ] **Step 3.2.3: `BilliardApp` регистрирует DI-container. Commit.**

---

### Task 3.3: Retrofit + DTO + AuthInterceptor + PhoneAuthScreen

**Files:** `data/remote/*`, `ui/screens/auth/PhoneAuthScreen.kt`, `data/repo/AuthRepository.kt`
**Test:** MockWebServer в androidTest.

- [ ] **Step 3.3.1: Экран: два поля (телефон, код), кнопка "Получить код", "Войти". State-holder = ViewModel.**
- [ ] **Step 3.3.2: MockWebServer тест — mock ответы, проверка что при 200 JWT сохраняется в DataStore.**
- [ ] **Step 3.3.3: Commit.**

---

### Task 3.4: HomeScreen + список турниров

- [ ] **Step 3.4.1: LazyColumn турниров пользователя (Room source-of-truth + refresh from API).**
- [ ] **Step 3.4.2: FAB "Новый турнир" → PickParticipantsScreen.**
- [ ] **Step 3.4.3: Compose-preview + скриншот-тест (Paparazzi опционально; на MVP хватит preview).**
- [ ] **Step 3.4.4: Commit.**

---

### Task 3.5: Пикер участников (Contacts + ручной ввод)

**Files:** `ui/screens/contacts/PickParticipantsScreen.kt`, `util/ContactsReader.kt`
**Manifest:** добавить `<uses-permission android:name="android.permission.READ_CONTACTS" />` (dangerous, спросить в рантайме).

- [ ] **Step 3.5.1: Runtime-permission через `rememberLauncherForActivityResult`. Отказ = fallback "введите вручную".**
- [ ] **Step 3.5.2: Список контактов с чекбоксами + поле "Добавить гостя" (имя + телефон опционально).**
- [ ] **Step 3.5.3: `next` → PickGameTypeScreen с выбранными участниками в SavedStateHandle.**
- [ ] **Step 3.5.4: Тест `ContactsReader` с фейковым ContentResolver. Commit.**

---

### Task 3.6: PickGameTypeScreen + настройка ставки/форы

- [ ] **Step 3.6.1: LazyColumn из `GameType.entries` — карточка с названием + краткое описание + "Правила" (кнопка → RuleDetailScreen).**
- [ ] **Step 3.6.2: После выбора — экран настроек: ставка руб/шар (только если `profile.moneyPlayable`), индивидуальные ставки и handicap на каждого участника.**
- [ ] **Step 3.6.3: POST /api/tournaments + participants. Commit.**

---

### Task 3.7: TournamentScreen — live-счёт, RefereePanel/ObserverPanel

**Files:** `ui/screens/tournament/*`

- [ ] **Step 3.7.1: Определение роли: `if (tournament.refereeUserId == currentUserId) RefereePanel else ObserverPanel`.**
- [ ] **Step 3.7.2: RefereePanel кнопки:**
  - "Забил №N" (сетка 1..15 или кнопки-шары),
  - "Свояк",
  - "Штраф (биток в лузу)",
  - "Вылет за борт",
  - "Отменить последний удар".

  Каждое нажатие → POST /shots + оптимистичное обновление Room.

- [ ] **Step 3.7.3: ObserverPanel: read-only счёт участников, подписка на SSE (`SseFlow`).**
- [ ] **Step 3.7.4: Кнопка "Маркёр" в TopAppBar в настройках — POST /claim-referee, роли меняются local + через SSE.**
- [ ] **Step 3.7.5: Тесты `AddShotUseCase`, `ClaimRefereeUseCase` (unit, MockWebServer).**
- [ ] **Step 3.7.6: Compose UI test — при `refereeUserId != me` кнопки disabled/скрыты.**
- [ ] **Step 3.7.7: Commit.**

---

### Task 3.8: PayoutSheet — итог по деньгам

**Files:** `ui/screens/tournament/PayoutSheet.kt`, `domain/rules/PayoutCalculator.kt`
**Test:** `PayoutCalculatorTest.kt` — parity с backend Task 2.7.

- [ ] **Step 3.8.1: Тесты один-в-один с backend/test/payout.test.js.**
- [ ] **Step 3.8.2: BottomSheet: "Финал партии" → таблица "кто → кому → сумма руб".**
- [ ] **Step 3.8.3: Commit.**

---

### Task 3.9: Правила — offline-first читалка

**Files:** `ui/screens/rules/*`, `data/repo/RuleRepository.kt`
- [ ] Кешировать markdown в Room `rules` table после первого GET /api/rules/:slug.
- [ ] Compose-Markdown рендерер (используем `dev.jeziellago:compose-markdown` или ручной парсер жирного/списков — YAGNI, взять готовую либу).

---

### Task 3.10: Android gate — smoke E2E

- [ ] Ручной прогон: auth → создать турнир на 2 контакта → добавить 5 шаров → передать маркёра → закрыть партию → увидеть payout.
- [ ] `gradlew :app:connectedAndroidTest` зелёный.
- [ ] Commit + tag `android-mvp-v0.1`.

---

# ФАЗА 4 — Клубы SPb/ЛО + гео-детект

### Task 4.1: Собрать справочник клубов

- [ ] **Step 4.1.1: Research-агент с WebSearch:**
  - 2GIS / Яндекс.Карты / rusbilliard.ru;
  - выбрать клубы Санкт-Петербурга и Ленобласти с русским бильярдом (столы 12ft);
  - для каждого: name, address, lat, lon, city.
- [ ] **Step 4.1.2: Записать в `shared/clubs.spb-lo.json` (массив объектов).**
- [ ] **Step 4.1.3: `npm run db:seed` заливает клубы в backend.**
- [ ] **Step 4.1.4: Commit `data(clubs): справочник клубов SPb+LO`.**

### Task 4.2: Гео-детект клуба на старте приложения

**Files:** `domain/usecase/DetectClubUseCase.kt`, `util/Haversine.kt`, HomeScreen
**Manifest:** `ACCESS_COARSE_LOCATION` + `ACCESS_FINE_LOCATION`.

- [ ] **Step 4.2.1: Тест `Haversine.distanceMeters(...)` (парный с backend haversine).**
- [ ] **Step 4.2.2: FusedLocationProviderClient → одна фиксация; поиск клуба в радиусе 200 м → prefill в new-tournament dialog.**
- [ ] **Step 4.2.3: Если не нашли — dialog "Добавить клуб?" → AddClubScreen → POST /api/clubs.**
- [ ] **Step 4.2.4: Commit.**

---

# ФАЗА 5 — Продакшн-деплой

### Task 5.1: systemd + nginx

**Files:**
- Create: `E:\PROJECTS\LAV-Server\srv\billiardtracker\systemd\lav-billiardtracker.service`
- Create: `E:\PROJECTS\LAV-Server\srv\billiardtracker\nginx.conf.snippet`

- [ ] **Step 5.1.1: Unit-файл по образцу `srv/whereami/systemd/lav-whereami.service` — порт 3021, env-file `/srv/billiardtracker/backend/.env`.**
- [ ] **Step 5.1.2: nginx snippet: `server_name billiardtracker.alekseylosev.ru;`, `location /api/ { proxy_pass http://127.0.0.1:3021; proxy_set_header Host $host; proxy_read_timeout 3600s; proxy_buffering off; }` (буферизация off для SSE).**
- [ ] **Step 5.1.3: Deploy-runner в стиле `LAV-Server/bin/.deploy/deploy-whereami.mjs` → `deploy-billiardtracker.mjs`.**
- [ ] **Step 5.1.4: `certbot --nginx -d billiardtracker.alekseylosev.ru`. НЕ автоматизируем — пользователь запускает на сервере вручную. Пометить чек-боксом.**
- [ ] **Step 5.1.5: `curl https://billiardtracker.alekseylosev.ru/api/health` → 200. Commit.**

---

## Self-review (проведён)

- ✅ **Спек-coverage:**
  - Контакты + ручной ввод — Task 3.5.
  - Выбор игры — 3.6, GameType — 1.3.
  - Облачное хранение → API — фаза 2, отправка друзьям = share ссылки/pull `/api/tournaments/:id` (JWT-guest), TODO: если нужен public-share — отдельная задача, добавлена в бэклог ниже.
  - Кнопка "Маркёр" + блокировка редактирования — 2.4, 3.7.
  - Игра на деньги + подсчёт разности + выплата — 2.7, 3.8.
  - Форa / индивидуальная стоимость — покрыто `handicapPoints`, `perBallOverrideKop` в схеме + payout-tests.
  - Варианты изменения очереди (переход хода/дубли) — флаги в `RuleProfile` (Task 1.3.4), логика применяется в UseCase (кто-ходит-следующий).
  - Штраф "биток в лузу", "вылет за борт" — `kind` в shots (Task 2.1.3, 3.7.2) + payout (2.7.1).
  - Все 14 дисциплин перечислены — Task 1.1.
  - Правила русского бильярда — Task 1.2 (research).
  - Список клубов SPb/LO + гео-детект + добавление — фаза 4.
  - Раздел с правилами — Task 3.9.
- ✅ **Placeholder-скан:** `TODO()` в Kotlin на Step 1.3.4 — сознательный (профили заполняются подстановочно на Step 1.3.5). Остальные TODO — на пользовательские действия (ротация токена, certbot).
- ✅ **Тип-консистентность:** `refereeUserId` / `entered_by_user_id` / `moneyPerBallKop` (копейки, не рубли) — единообразно во всех местах.

## Бэклог (после MVP)

- Public share-ссылка турнира без JWT (query-token).
- Экспорт статистики CSV.
- Push-уведомления "тебя добавили в турнир" (FCM).
- iOS-клиент.
- Импорт клубов из 2GIS API.

---

## Execution Handoff

Plan готов. Два варианта запуска:

1. **Subagent-Driven (рекомендуется)** — отдельный subagent на каждую Task, ревью между шагами, быстрая итерация. Использует `superpowers:subagent-driven-development`.
2. **Inline Execution** — все Task в этом сеансе, чекпоинты в конце фазы. Использует `superpowers:executing-plans`.

Какой подход?
