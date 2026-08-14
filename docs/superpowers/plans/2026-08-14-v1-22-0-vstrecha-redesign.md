# BilliardTracker v1.22.0 — UX-редизайн экрана встречи Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** экран встречи выглядит нативно под правила выбранной дисциплины: пирамиды с равноценными шарами получают простые ± кнопки, игры на выбывание (Алагёр / Грош) — сетку жизней, классическая/61/71 — знакомый ball-grid через bottom-sheet. Layout адаптируется под 2 / 3-4 / 5+ игроков в portrait.

**Architecture:** RuleProfile получает поле `scorerKind: ScorerKind` (Numbered/Counter/Lives + Balance/Fishki-fallback). Новый пакет `ui/screens/tournament/scorers/` содержит: `MatchLayout` (грид-раскладка по числу игроков), `ScoreTile` (общий контейнер), три scorer-composable (`CounterScorer`, `LivesScorer`, `NumberedBallGridScorer`), `MatchBottomBar` (undo + finish + target). `TournamentScreen` заменяет `RefereePanel`/`ObserverPanel` на `MatchLayout(profile.scorerKind, isReferee)`. Domain и data слои — без изменений (кроме нового поля в enum), бэкенд не трогаем.

**Tech Stack:**
- Kotlin, Jetpack Compose (BOM 2025.12+), Material 3.
- JUnit4 (unit tests для pure logic — layout choice, isEliminated derivation, decrementScore).
- Android Emulator `Pixel_6_API36` для смок-тестов.
- Релиз через `node E:/PROJECTS/LAV-Server/bin/.deploy/release-billiardtracker.mjs` (auto: assembleRelease → SFTP → VPS `latest` symlink → GH release с `latest-only` cleanup).

**Spec:** `docs/superpowers/specs/2026-08-13-vstrecha-redesign-design.md` §2 (v1.22.0 UX-редизайн).

---

## File Structure

**New (`app/src/main/java/com/example/billiardtracker/ui/screens/tournament/scorers/`):**
- `ScorerKind.kt` — enum: `NumberedBallGrid | Counter | Lives | Balance | Fishki`.
- `MatchLayout.kt` — раздаёт `ScoreTile`-ы по grid/split/list в зависимости от `participants.size`. Экспортирует pure fun `layoutFor(participantCount: Int): TileLayout` для юнит-теста.
- `ScoreTile.kt` — общий контейнер (name + маркёр + счёт + slot).
- `CounterScorer.kt` — `+1 / −1 / Свояк / Штраф` кнопки в теле плитки.
- `LivesScorer.kt` — сетка «крестиков»; тап убирает жизнь; экспортирует pure fun `isEliminated(participant, shots, lives): Boolean` для юнит-теста.
- `NumberedBallGridScorer.kt` — плитка = имя + счёт; тап на плитку раскрывает shared bottom-sheet с ball-grid 3×5 + Свояк/Штраф/За борт; выбор шара закрывает sheet.
- `MatchBottomBar.kt` — `↶ Undo`, `Партия окончена` (с confirm-dialog), target-индикатор («до N побед · A 3 · B 2»).

**New (test):**
- `app/src/test/java/com/example/billiardtracker/ui/screens/tournament/scorers/MatchLayoutTest.kt` — layoutFor(n) для n=2/3/4/5/6.
- `app/src/test/java/com/example/billiardtracker/ui/screens/tournament/scorers/LivesScorerLogicTest.kt` — isEliminated derivation.
- `app/src/test/java/com/example/billiardtracker/ui/screens/tournament/DecrementScoreTest.kt` — VM.decrementScore(pid) с fake repo.

**Modified:**
- `app/src/main/java/com/example/billiardtracker/domain/rules/RuleProfile.kt` — добавить поле `val scorerKind: ScorerKind` с default per game type.
- `app/src/main/java/com/example/billiardtracker/ui/screens/tournament/TournamentViewModel.kt` — добавить `fun decrementScore(pid: Long)`.
- `app/src/main/java/com/example/billiardtracker/ui/screens/tournament/TournamentScreen.kt` — заменить `RefereePanel`/`ObserverPanel` использование на `MatchLayout(scorerKind, isReferee = ui.isReferee)`.
- `app/src/main/java/com/example/billiardtracker/ui/screens/tournament/RefereePanel.kt` — **удалить** (заменено `NumberedBallGridScorer` + `MatchBottomBar`).
- `app/src/main/java/com/example/billiardtracker/ui/screens/tournament/ObserverPanel.kt` — **удалить** (заменено `MatchLayout(readonly=true)`).
- `app/src/test/java/com/example/billiardtracker/domain/rules/RuleProfileTest.kt` — добавить проверку `scorerKind` для всех 14 типов.
- `app/build.gradle.kts` — `versionCode = 78`, `versionName = "1.22.0"`.
- `README.md` — v1.22.0 note.

**НЕ трогаем:**
- Backend / `shared/rules/*.md` / `rule-profiles-expected.json` — `scorerKind` UI-only.
- `GameRepository` / `SyncManager` — shot протокол уже подходит (kind + ballNumber + pointsDelta).
- Landscape / rotation — весь дизайн portrait.
- Fishki / Kolkhoz — попадают в fallback (`NumberedBallGrid` + info-баннер), полноценный UI откладывается на v1.23.0+.

---

## Task 1: Добавить `ScorerKind` в `RuleProfile`

**Goal:** пометить все 14 дисциплин UI-паттерном, чтобы `TournamentScreen` мог выбирать scorer.

**Files:**
- Create: `app/src/main/java/com/example/billiardtracker/domain/rules/ScorerKind.kt`
- Modify: `app/src/main/java/com/example/billiardtracker/domain/rules/RuleProfile.kt`
- Modify: `app/src/test/java/com/example/billiardtracker/domain/rules/RuleProfileTest.kt`

- [ ] **Step 1: Write the failing test** — добавить в `RuleProfileTest.kt` (в конец класса):

```kotlin
@Test fun `every game type has the expected scorerKind`() {
    val expected = mapOf(
        // Group A — Numbered ball-grid (номер шара = очки)
        GameType.CLASSICAL_PYRAMID to ScorerKind.NumberedBallGrid,
        GameType.SMALL_RUSSIAN_PARTY to ScorerKind.NumberedBallGrid,
        GameType.BIG_RUSSIAN_PARTY to ScorerKind.NumberedBallGrid,
        // Group B — Counter (все шары по 1 очку, ±1 кнопки)
        GameType.FREE_PYRAMID to ScorerKind.Counter,
        GameType.COMBINED_PYRAMID to ScorerKind.Counter,
        GameType.DYNAMIC_PYRAMID to ScorerKind.Counter,
        GameType.FREE_PYRAMID_CONTINUATION to ScorerKind.Counter,
        GameType.YAROSLAVSKAYA to ScorerKind.Counter,
        GameType.EUROPEAN_PYRAMID to ScorerKind.Counter,
        GameType.ONE_POCKET_RU to ScorerKind.Counter,
        // Group C — Lives (жизни/кресты, на выбывание)
        GameType.ALAGYOR to ScorerKind.Lives,
        GameType.GROSH to ScorerKind.Lives,
        // Group D — Balance (v1.23.0+, пока Numbered fallback)
        GameType.KOLKHOZ to ScorerKind.NumberedBallGrid,
        // Group E — Fishki (v1.23.0+, пока Numbered fallback)
        GameType.FISHKI to ScorerKind.NumberedBallGrid,
    )
    for (t in GameType.entries) {
        val p = RuleProfile.forType(t)
        assertEquals("scorerKind[$t]", expected[t], p.scorerKind)
    }
    assertEquals("map must cover all 14 types", 14, expected.size)
}
```

- [ ] **Step 2: Run the test — expect FAIL** (compile error, `ScorerKind` not found):

```powershell
./gradlew :app:testDebugUnitTest --tests "com.example.billiardtracker.domain.rules.RuleProfileTest.every_game_type_has_the_expected_scorerKind"
```

Expected: BUILD FAILED — "unresolved reference: ScorerKind".

- [ ] **Step 3: Create `ScorerKind.kt`**:

```kotlin
package com.example.billiardtracker.domain.rules

/**
 * Какой UI-паттерн ввода счёта использовать для дисциплины.
 * TournamentScreen читает RuleProfile.scorerKind и выбирает соответствующий
 * scorer-composable. Значения — UI-implementation детали, но живут в domain,
 * потому что таблица «дисциплина → UI-паттерн» естественно принадлежит
 * профилю правил, а не UI-слою.
 *
 *  - NumberedBallGrid — 15 нумерованных шаров в bottom-sheet (Классика / 61 / 71).
 *  - Counter          — ±1 кнопки в плитке игрока (пирамиды с равноценными шарами).
 *  - Lives            — сетка «крестиков» (Алагёр / Грош — на выбывание).
 *  - Balance          — таблица баланса с каждым (Колхоз, v1.23.0+; пока fallback на NumberedBallGrid).
 *  - Fishki           — карамболь + кегли (v1.23.0+; пока fallback на NumberedBallGrid).
 */
enum class ScorerKind { NumberedBallGrid, Counter, Lives, Balance, Fishki }
```

- [ ] **Step 4: Modify `RuleProfile.kt`** — добавить `scorerKind` в data-class:

Find the data-class declaration:
```kotlin
data class RuleProfile(
    val type: GameType,
    ...
    val moneyPlayable: Boolean,
    val hasContinuation: Boolean = false,
    val notes: String? = null,
) {
```

Change to:
```kotlin
data class RuleProfile(
    val type: GameType,
    ...
    val moneyPlayable: Boolean,
    val hasContinuation: Boolean = false,
    val notes: String? = null,
    /**
     * UI-паттерн ввода счёта для этой дисциплины. См. [ScorerKind]. Влияет
     * только на UI (`TournamentScreen`), никак не участвует в парити-фикстуре
     * `rule-profiles-expected.json` (там только правила).
     */
    val scorerKind: ScorerKind = ScorerKind.NumberedBallGrid,
) {
```

Then in `forType()` — для каждого из 14 case-блоков добавить строку `scorerKind = ScorerKind.XXX,` перед `)`. Пример для FREE_PYRAMID:

```kotlin
GameType.FREE_PYRAMID -> RuleProfile(
    type = type,
    ballValues = List(15) { 1 },
    winTargetPoints = 8,
    winTargetBalls = null,
    allowsSvoiak = true,
    svoiakReturnedToHome = false,
    alternatesTurnAlways = false,
    allowsHandicap = true,
    allowsDouble = false,
    tabbedBallsAllowed = false,
    moneyPlayable = true,
    scorerKind = ScorerKind.Counter,   // ← добавить
    notes = "Американка: ...",
)
```

Полная таблица маппинга (используй её для каждого case-блока):

| GameType | scorerKind |
|---|---|
| FREE_PYRAMID | Counter |
| COMBINED_PYRAMID | Counter |
| DYNAMIC_PYRAMID | Counter |
| CLASSICAL_PYRAMID | NumberedBallGrid |
| FREE_PYRAMID_CONTINUATION | Counter |
| SMALL_RUSSIAN_PARTY | NumberedBallGrid |
| BIG_RUSSIAN_PARTY | NumberedBallGrid |
| ALAGYOR | Lives |
| YAROSLAVSKAYA | Counter |
| KOLKHOZ | NumberedBallGrid (fallback) |
| FISHKI | NumberedBallGrid (fallback) |
| ONE_POCKET_RU | Counter |
| GROSH | Lives |
| EUROPEAN_PYRAMID | Counter |

- [ ] **Step 5: Run the test — expect PASS**:

```powershell
./gradlew :app:testDebugUnitTest --tests "com.example.billiardtracker.domain.rules.RuleProfileTest"
```

Expected: BUILD SUCCESSFUL, all RuleProfileTest tests pass (existing 8 + new 1 = 9).

- [ ] **Step 6: Stage but do NOT commit** (plan batches commits at Task 11):

```powershell
git add app/src/main/java/com/example/billiardtracker/domain/rules/ScorerKind.kt `
        app/src/main/java/com/example/billiardtracker/domain/rules/RuleProfile.kt `
        app/src/test/java/com/example/billiardtracker/domain/rules/RuleProfileTest.kt
```

---

## Task 2: `MatchLayout` + `TileLayout` + layout-choice unit test

**Goal:** pure-Kotlin функция, которая по числу игроков возвращает раскладку. Composable-обёртка вокруг неё.

**Files:**
- Create: `app/src/main/java/com/example/billiardtracker/ui/screens/tournament/scorers/MatchLayout.kt`
- Create: `app/src/test/java/com/example/billiardtracker/ui/screens/tournament/scorers/MatchLayoutTest.kt`

- [ ] **Step 1: Write the failing test**:

```kotlin
package com.example.billiardtracker.ui.screens.tournament.scorers

import org.junit.Assert.assertEquals
import org.junit.Test

class MatchLayoutTest {
    @Test fun `2 players → SplitVertical`() {
        assertEquals(TileLayout.SplitVertical, layoutFor(2))
    }
    @Test fun `3 players → Grid2x2`() {
        assertEquals(TileLayout.Grid2x2, layoutFor(3))
    }
    @Test fun `4 players → Grid2x2`() {
        assertEquals(TileLayout.Grid2x2, layoutFor(4))
    }
    @Test fun `5 players → VerticalList`() {
        assertEquals(TileLayout.VerticalList, layoutFor(5))
    }
    @Test fun `10 players → VerticalList`() {
        assertEquals(TileLayout.VerticalList, layoutFor(10))
    }
    @Test fun `1 player → VerticalList (edge case, degrade gracefully)`() {
        // Нормально играть в одиночку нельзя, но UI не должен падать —
        // единственный участник рендерится плиткой во весь экран как в VerticalList.
        assertEquals(TileLayout.VerticalList, layoutFor(1))
    }
    @Test fun `0 players → VerticalList (empty state)`() {
        assertEquals(TileLayout.VerticalList, layoutFor(0))
    }
}
```

- [ ] **Step 2: Run — expect FAIL** (unresolved `TileLayout` / `layoutFor`):

```powershell
./gradlew :app:testDebugUnitTest --tests "com.example.billiardtracker.ui.screens.tournament.scorers.MatchLayoutTest"
```

- [ ] **Step 3: Create `MatchLayout.kt`** with the pure fn + enum + composable skeleton:

```kotlin
package com.example.billiardtracker.ui.screens.tournament.scorers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.billiardtracker.data.remote.dto.ParticipantDto

enum class TileLayout { SplitVertical, Grid2x2, VerticalList }

/**
 * Портрет-режим (v1.22.0 не поддерживает landscape). Правила:
 *  - 2 игрока → split top/bottom (SplitVertical): каждая плитка ½ высоты.
 *  - 3–4 игрока → 2×2 grid (Grid2x2): для 3-х четвёртая ячейка = «Действия партии».
 *  - 5+ / 1 / 0 → VerticalList: скроллящийся список плиток ~20% высоты каждая.
 */
fun layoutFor(participantCount: Int): TileLayout = when (participantCount) {
    2 -> TileLayout.SplitVertical
    3, 4 -> TileLayout.Grid2x2
    else -> TileLayout.VerticalList
}

/**
 * Раскладывает `tileContent(participant)` по правилам [layoutFor]. Не знает
 * ничего о scorer-логике — только про геометрию.
 */
@Composable
fun MatchLayout(
    participants: List<ParticipantDto>,
    modifier: Modifier = Modifier,
    tileContent: @Composable (ParticipantDto) -> Unit,
) {
    when (layoutFor(participants.size)) {
        TileLayout.SplitVertical -> Column(
            modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            participants.forEach { p ->
                Row(Modifier.fillMaxWidth().weight(1f)) { tileContent(p) }
            }
        }
        TileLayout.Grid2x2 -> Column(
            modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            participants.chunked(2).forEach { rowPair ->
                Row(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowPair.forEach { p -> Row(Modifier.weight(1f)) { tileContent(p) } }
                    // Если в паре только 1 игрок — вторая ячейка пустая заглушка (spacer),
                    // NumberedBallGridScorer/CounterScorer/LivesScorer не рендерятся.
                    if (rowPair.size == 1) Row(Modifier.weight(1f)) {}
                }
            }
        }
        TileLayout.VerticalList -> LazyColumn(
            modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(participants, key = { it.id }) { p -> tileContent(p) }
        }
    }
}
```

- [ ] **Step 4: Run tests — expect PASS**:

```powershell
./gradlew :app:testDebugUnitTest --tests "com.example.billiardtracker.ui.screens.tournament.scorers.MatchLayoutTest"
```

Expected: 6/6 PASS.

- [ ] **Step 5: Stage**:

```powershell
git add app/src/main/java/com/example/billiardtracker/ui/screens/tournament/scorers/MatchLayout.kt `
        app/src/test/java/com/example/billiardtracker/ui/screens/tournament/scorers/MatchLayoutTest.kt
```

---

## Task 3: `ScoreTile` (общий контейнер)

**Goal:** Card с именем игрока, маркёром 🎩, крупным счётом, и slot'ом под scorer-специфичный контент.

**Files:**
- Create: `app/src/main/java/com/example/billiardtracker/ui/screens/tournament/scorers/ScoreTile.kt`

- [ ] **Step 1: Create `ScoreTile.kt`** (нет отдельного unit-теста — pure visual composable, покроем smoke-тестом в Task 9):

```kotlin
package com.example.billiardtracker.ui.screens.tournament.scorers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Общий контейнер плитки игрока в MatchLayout: имя (+ 🎩 если маркёр), крупный
 * счёт, slot под scorer. Заполняет доступное пространство от родителя
 * (`fillMaxSize` — потому что MatchLayout уже раздал weight каждому tile).
 */
@Composable
fun ScoreTile(
    name: String,
    isReferee: Boolean,
    score: Int,
    modifier: Modifier = Modifier,
    scorer: @Composable () -> Unit,
) {
    val badge = if (isReferee) " 🎩" else ""
    Column(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$name$badge",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "$score",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(Modifier.fillMaxSize()) { scorer() }
    }
}
```

- [ ] **Step 2: Verify compilation** (нет теста, только компилируется):

```powershell
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Stage**:

```powershell
git add app/src/main/java/com/example/billiardtracker/ui/screens/tournament/scorers/ScoreTile.kt
```

---

## Task 4: `TournamentViewModel.decrementScore(pid)` + unit test

**Goal:** новый VM-метод для «−» кнопки в `CounterScorer`: удалить последний positive shot указанного игрока в текущей партии. Отличается от `undoLastShot`, который тянет любой последний shot.

**Files:**
- Modify: `app/src/main/java/com/example/billiardtracker/ui/screens/tournament/TournamentViewModel.kt`
- Create: `app/src/test/java/com/example/billiardtracker/ui/screens/tournament/DecrementScoreTest.kt`

- [ ] **Step 1: Write the failing test**:

```kotlin
package com.example.billiardtracker.ui.screens.tournament

import com.example.billiardtracker.data.remote.dto.GameDto
import com.example.billiardtracker.data.remote.dto.ScoreDto
import com.example.billiardtracker.data.remote.dto.ShotDto
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Проверяем чистую логику выбора shot'а для decrement — не запускаем VM
 * целиком (потребовало бы Room + Retrofit + SSE fakes). Extractнутая fun
 * `pickShotToDecrement` — публичная internal в VM-файле, тестируется тут.
 */
class DecrementScoreTest {
    private val gid = 100L

    private fun shot(id: Long, pid: Long, kind: String, delta: Int) = ShotDto(
        id = id, gameId = gid, participantId = pid,
        kind = kind, ballNumber = null, pointsDelta = delta,
    )

    @Test fun `picks last positive shot of pid`() {
        val shots = listOf(
            shot(1, pid = 10, kind = "ball", delta = 1),
            shot(2, pid = 20, kind = "ball", delta = 1),
            shot(3, pid = 10, kind = "ball", delta = 1),  // ← это должно быть выбрано
            shot(4, pid = 20, kind = "ball", delta = 1),
        )
        assertEquals(3L, pickShotToDecrement(shots, pid = 10)?.id)
    }

    @Test fun `skips foul (negative) shots — only rolls back positives`() {
        val shots = listOf(
            shot(1, pid = 10, kind = "ball", delta = 1),
            shot(2, pid = 10, kind = "foul", delta = -1),  // не откатываем штраф через minus
        )
        assertEquals(1L, pickShotToDecrement(shots, pid = 10)?.id)
    }

    @Test fun `returns null if pid has no positive shots`() {
        val shots = listOf(shot(1, pid = 10, kind = "foul", delta = -1))
        assertEquals(null, pickShotToDecrement(shots, pid = 10))
    }

    @Test fun `returns null on empty shots`() {
        assertEquals(null, pickShotToDecrement(emptyList(), pid = 10))
    }
}
```

- [ ] **Step 2: Run — expect FAIL** (unresolved `pickShotToDecrement`):

```powershell
./gradlew :app:testDebugUnitTest --tests "com.example.billiardtracker.ui.screens.tournament.DecrementScoreTest"
```

- [ ] **Step 3: Modify `TournamentViewModel.kt`** — добавить top-level `internal fun pickShotToDecrement` и метод `decrementScore` в class.

Add ABOVE the `class TournamentViewModel(` line (top-level, so unit-test can import it):

```kotlin
/**
 * Выбирает shot для отката при тапе «−» в CounterScorer: последний positive
 * (pointsDelta > 0) shot указанного участника в списке. Игнорирует штрафы
 * и off-table события — их откатывают через общий Undo, а не per-tile «−».
 * Extract'нута из VM ради юнит-теста (VM в целом требует Room/Retrofit fakes).
 */
internal fun pickShotToDecrement(
    shots: List<com.example.billiardtracker.data.remote.dto.ShotDto>,
    pid: Long,
): com.example.billiardtracker.data.remote.dto.ShotDto? =
    shots.lastOrNull { it.participantId == pid && it.pointsDelta > 0 }
```

Then inside `class TournamentViewModel(...)` — add after `finishGame(...)`:

```kotlin
/**
 * «−» в CounterScorer: удаляет последний positive shot указанного игрока.
 * Отличается от [undoLastShot], который тянет любой последний shot независимо
 * от игрока. Disabled на UI-уровне когда `pickShotToDecrement()` вернёт null.
 */
fun decrementScore(pid: Long) {
    val gid = _ui.value.currentGame?.id ?: return
    val shot = pickShotToDecrement(_ui.value.currentGameShots, pid) ?: return
    viewModelScope.launch {
        gameRepo.deleteShot(gid, shot.id).onSuccess {
            // Пересчитываем optimistic state как в undoLastShot.
            val newShots = _ui.value.currentGameShots.filterNot { it.id == shot.id }
            val updatedGame = _ui.value.currentGame?.let { g ->
                val by = newShots.filter { it.gameId == g.id }
                    .groupBy { it.participantId }
                    .mapValues { (_, list) -> list.sumOf { it.pointsDelta } }
                g.copy(scores = by.map {
                    com.example.billiardtracker.data.remote.dto.ScoreDto(it.key, it.value)
                })
            }
            _ui.value = _ui.value.copy(
                currentGameShots = newShots,
                currentGame = updatedGame,
            )
            refresh()
        }
    }
}
```

- [ ] **Step 4: Run — expect PASS**:

```powershell
./gradlew :app:testDebugUnitTest --tests "com.example.billiardtracker.ui.screens.tournament.DecrementScoreTest"
```

Expected: 4/4 PASS.

- [ ] **Step 5: Stage**:

```powershell
git add app/src/main/java/com/example/billiardtracker/ui/screens/tournament/TournamentViewModel.kt `
        app/src/test/java/com/example/billiardtracker/ui/screens/tournament/DecrementScoreTest.kt
```

---

## Task 5: `CounterScorer` composable (Group B)

**Goal:** плитка с крупными `−1 / +1` кнопками и опциональными `Свояк / Штраф` по флагам `RuleProfile`.

**Files:**
- Create: `app/src/main/java/com/example/billiardtracker/ui/screens/tournament/scorers/CounterScorer.kt`

- [ ] **Step 1: Create `CounterScorer.kt`**:

```kotlin
package com.example.billiardtracker.ui.screens.tournament.scorers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.billiardtracker.domain.rules.RuleProfile

/**
 * ±1 счётчик для дисциплин группы B (пирамиды с равноценными шарами).
 * `+`  → onShot(pid, kind="ball", ballNumber=null, pointsDelta=+1).
 * `−`  → onDecrement(pid); disabled когда currentScore == 0.
 * `Свояк` (если profile.allowsSvoiak) → onShot(pid, "svoiak", null, +1).
 * `Штраф` (всегда — «за борт» / промах биком) → onShot(pid, "foul", null, -1).
 */
@Composable
fun CounterScorer(
    pid: Long,
    profile: RuleProfile,
    currentScore: Int,
    onShot: (participantId: Long, kind: String, ballNumber: Int?, pointsDelta: Int) -> Unit,
    onDecrement: (participantId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { onDecrement(pid) },
                enabled = currentScore > 0,
                modifier = Modifier.weight(1f),
            ) { Text("−", style = MaterialTheme.typography.headlineSmall) }
            Button(
                onClick = { onShot(pid, "ball", null, +1) },
                modifier = Modifier.weight(1f),
            ) { Text("+", style = MaterialTheme.typography.headlineSmall) }
        }
        // Второй ряд: Свояк (опционально по правилам) + Штраф (всегда).
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (profile.allowsSvoiak) {
                OutlinedButton(
                    onClick = { onShot(pid, "svoiak", null, +1) },
                    modifier = Modifier.weight(1f),
                ) { Text("Свояк") }
            }
            OutlinedButton(
                onClick = { onShot(pid, "foul", null, -1) },
                modifier = Modifier.weight(1f),
            ) { Text("Штраф") }
        }
    }
}
```

- [ ] **Step 2: Verify compilation**:

```powershell
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Stage**:

```powershell
git add app/src/main/java/com/example/billiardtracker/ui/screens/tournament/scorers/CounterScorer.kt
```

---

## Task 6: `LivesScorer` + `isEliminated` pure logic

**Goal:** сетка «крестиков» (жизней), тап = минус жизнь. Игрок с 0 жизней помечен «выбыл».

**Files:**
- Create: `app/src/main/java/com/example/billiardtracker/ui/screens/tournament/scorers/LivesScorer.kt`
- Create: `app/src/test/java/com/example/billiardtracker/ui/screens/tournament/scorers/LivesScorerLogicTest.kt`

- [ ] **Step 1: Write the failing test**:

```kotlin
package com.example.billiardtracker.ui.screens.tournament.scorers

import com.example.billiardtracker.data.remote.dto.ShotDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivesScorerLogicTest {
    private fun life(pid: Long, id: Long = 0) = ShotDto(
        id = id, gameId = 0L, participantId = pid,
        kind = "life", ballNumber = null, pointsDelta = -1,
    )

    @Test fun `pid with 0 life-shots and 3-life game has 3 lives`() {
        assertEquals(3, livesRemaining(emptyList(), pid = 10, initialLives = 3))
    }
    @Test fun `each life-shot decrements`() {
        val shots = listOf(life(10), life(10))
        assertEquals(1, livesRemaining(shots, pid = 10, initialLives = 3))
    }
    @Test fun `shots of other pid don't affect`() {
        val shots = listOf(life(20), life(20))
        assertEquals(3, livesRemaining(shots, pid = 10, initialLives = 3))
    }
    @Test fun `non-life shots don't count`() {
        val shots = listOf(
            ShotDto(1, 0, 10, "ball", null, +1),
            ShotDto(2, 0, 10, "foul", null, -1),
        )
        assertEquals(3, livesRemaining(shots, pid = 10, initialLives = 3))
    }
    @Test fun `isEliminated true when lives = 0`() {
        val shots = List(3) { life(10, it.toLong()) }
        assertTrue(isEliminated(shots, pid = 10, initialLives = 3))
    }
    @Test fun `isEliminated false when lives = 1`() {
        val shots = List(2) { life(10, it.toLong()) }
        assertFalse(isEliminated(shots, pid = 10, initialLives = 3))
    }
    @Test fun `lives cannot go negative`() {
        val shots = List(5) { life(10, it.toLong()) }
        assertEquals(0, livesRemaining(shots, pid = 10, initialLives = 3))
    }
}
```

- [ ] **Step 2: Run — expect FAIL**:

```powershell
./gradlew :app:testDebugUnitTest --tests "com.example.billiardtracker.ui.screens.tournament.scorers.LivesScorerLogicTest"
```

- [ ] **Step 3: Create `LivesScorer.kt`**:

```kotlin
package com.example.billiardtracker.ui.screens.tournament.scorers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.billiardtracker.data.remote.dto.ShotDto

/** Оставшиеся жизни pid'а в текущей партии. Life-shot = `kind="life"`. */
fun livesRemaining(shots: List<ShotDto>, pid: Long, initialLives: Int): Int {
    val lost = shots.count { it.participantId == pid && it.kind == "life" }
    return (initialLives - lost).coerceAtLeast(0)
}

/** Игрок выбыл (0 жизней). */
fun isEliminated(shots: List<ShotDto>, pid: Long, initialLives: Int): Boolean =
    livesRemaining(shots, pid, initialLives) == 0

/**
 * Сетка жизней для Алагёра/Гроша. По умолчанию 3 жизни (Алагёр — «кресты»,
 * обычно 3). Тап на любую крестик = onLifeLost(pid). Игрок с 0 жизней получает
 * disabled UI + бейдж «выбыл».
 */
@Composable
fun LivesScorer(
    pid: Long,
    shots: List<ShotDto>,
    initialLives: Int = 3,
    onLifeLost: (participantId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val remaining = livesRemaining(shots, pid, initialLives)
    val eliminated = remaining == 0
    androidx.compose.foundation.layout.Column(
        modifier.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (eliminated) {
            Text(
                "выбыл",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0 until initialLives) {
                val alive = i < remaining
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (alive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        .clickable(enabled = alive) { onLifeLost(pid) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (alive) "✕" else "·",
                        color = if (alive) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run — expect PASS**:

```powershell
./gradlew :app:testDebugUnitTest --tests "com.example.billiardtracker.ui.screens.tournament.scorers.LivesScorerLogicTest"
```

Expected: 7/7 PASS.

- [ ] **Step 5: Stage**:

```powershell
git add app/src/main/java/com/example/billiardtracker/ui/screens/tournament/scorers/LivesScorer.kt `
        app/src/test/java/com/example/billiardtracker/ui/screens/tournament/scorers/LivesScorerLogicTest.kt
```

---

## Task 7: `NumberedBallGridScorer` + shared bottom-sheet

**Goal:** для дисциплин группы A (Классика/61/71) — плитка компактная (имя + счёт), тап на плитку раскрывает shared `ModalBottomSheet` с сеткой 15 шаров + Свояк/Штраф/За борт. Ball-tap отправляет shot **на выбранного игрока** и закрывает sheet.

**Files:**
- Create: `app/src/main/java/com/example/billiardtracker/ui/screens/tournament/scorers/NumberedBallGridScorer.kt`

- [ ] **Step 1: Create `NumberedBallGridScorer.kt`** (переиспользуем цвета из старого `RefereePanel.kt` — они станут mёртвыми после Task 10 и удалятся):

```kotlin
package com.example.billiardtracker.ui.screens.tournament.scorers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val BallWhite = Color(0xFFF5EFD9)
private val BallGrey = Color(0xFF6B6B6B)
private val BallText = Color(0xFF1A1A1A)

/**
 * Плитка для Group A (Классика/61/71/Колхоз-fallback/Фишки-fallback). Компактна
 * (тап-таргет — весь Box плитки). При тапе — [onSelect] раскрывает shared
 * bottom-sheet со всеми шарами + действиями. Родитель (TournamentScreen)
 * держит `selectedPid` и `sheetOpen` state, потому что bottom-sheet у нас
 * общий на все плитки.
 */
@Composable
fun NumberedBallGridTile(
    pid: Long,
    onSelect: (pid: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .clickable { onSelect(pid) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Тап для ввода счёта",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Shared bottom-sheet со всеми шарами + Свояк/Штраф/За борт. `selectedPid`
 * приходит от родителя (выбор был через тап на плитку). Ball-tap отправляет
 * shot на selectedPid и закрывает sheet. `pottedBalls` — множество забитых
 * шаров в текущей партии (собирается TournamentScreen'ом).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberedBallGridSheet(
    selectedPid: Long,
    selectedName: String,
    pottedBalls: Set<Int>,
    onDismiss: () -> Unit,
    onShot: (participantId: Long, kind: String, ballNumber: Int?, pointsDelta: Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close = {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
        Unit
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Забитый шар — $selectedName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            for (row in 0..2) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (col in 0..4) {
                        val ball = row * 5 + col + 1
                        val potted = ball in pottedBalls
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(if (potted) BallGrey else BallWhite)
                                .clickable(enabled = !potted) {
                                    onShot(selectedPid, "ball", ball, 1)
                                    close()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$ball",
                                color = BallText,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 20.sp,
                            )
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onShot(selectedPid, "svoiak", null, 1); close() },
                    modifier = Modifier.weight(1f),
                ) { Text("Свояк") }
                OutlinedButton(
                    onClick = { onShot(selectedPid, "foul", null, -1); close() },
                    modifier = Modifier.weight(1f),
                ) { Text("Штраф") }
                OutlinedButton(
                    onClick = { onShot(selectedPid, "ball_out", null, -1); close() },
                    modifier = Modifier.weight(1f),
                ) { Text("За борт") }
            }
            Button(
                onClick = { close() },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            ) { Text("Закрыть") }
        }
    }
}
```

- [ ] **Step 2: Verify compilation**:

```powershell
./gradlew :app:compileDebugKotlin
```

- [ ] **Step 3: Stage**:

```powershell
git add app/src/main/java/com/example/billiardtracker/ui/screens/tournament/scorers/NumberedBallGridScorer.kt
```

---

## Task 8: `MatchBottomBar` с confirm dialog на «Партия окончена»

**Goal:** общая нижняя панель под MatchLayout: `↶ Undo` + `Партия окончена` с confirm-диалогом (spec §2.7 «скорее да»).

**Files:**
- Create: `app/src/main/java/com/example/billiardtracker/ui/screens/tournament/scorers/MatchBottomBar.kt`

- [ ] **Step 1: Create `MatchBottomBar.kt`**:

```kotlin
package com.example.billiardtracker.ui.screens.tournament.scorers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Нижняя панель под MatchLayout: undo + finish + target-hint.
 * Confirm-диалог на «Партия окончена» защищает от misdaction — spec §2.7 «скорее да».
 * `targetHint` — человекочитаемая строка, VM формирует («до 8 побед · A 3 · B 2»
 * или «до 8 · A 5 · B 3» для дисциплин с ball-target).
 */
@Composable
fun MatchBottomBar(
    targetHint: String,
    winnerName: String?,
    winnerScore: Int?,
    onUndo: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmOpen by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxWidth().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            targetHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onUndo, modifier = Modifier.weight(1f)) {
                Text("↶ Отменить")
            }
            Button(onClick = { confirmOpen = true }, modifier = Modifier.weight(1f)) {
                Text("Партия окончена")
            }
        }
    }
    if (confirmOpen) {
        AlertDialog(
            onDismissRequest = { confirmOpen = false },
            title = { Text("Завершить партию?") },
            text = {
                val msg = when {
                    winnerName != null && winnerScore != null ->
                        "Победитель — $winnerName ($winnerScore)."
                    else -> "Победитель определится по текущему счёту."
                }
                Text(msg)
            },
            confirmButton = {
                Button(onClick = { confirmOpen = false; onFinish() }) {
                    Text("Завершить")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmOpen = false }) { Text("Отмена") }
            },
        )
    }
}
```

- [ ] **Step 2: Verify compilation**:

```powershell
./gradlew :app:compileDebugKotlin
```

- [ ] **Step 3: Stage**:

```powershell
git add app/src/main/java/com/example/billiardtracker/ui/screens/tournament/scorers/MatchBottomBar.kt
```

---

## Task 9: Wire `TournamentScreen` — заменить `RefereePanel`/`ObserverPanel` на `MatchLayout`

**Goal:** финальная сборка всех scorer-ов в `TournamentScreen`. Наблюдатели видят readonly-версию (плитки без активных кнопок). Маркёр — с кнопками.

**Files:**
- Modify: `app/src/main/java/com/example/billiardtracker/ui/screens/tournament/TournamentScreen.kt`
- Delete (после проверки, что удалить безопасно): `app/src/main/java/com/example/billiardtracker/ui/screens/tournament/RefereePanel.kt` и `ObserverPanel.kt`

- [ ] **Step 1: Modify `TournamentScreen.kt`** — заменить блок ролей.

Найди блок:
```kotlin
// Panel by role
if (ui.isReferee) {
    val cg = ui.currentGame
    if (cg == null || cg.status == "finished") {
        Column(Modifier.padding(16.dp)) {
            Button(
                onClick = viewModel::startGame,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Начать партию") }
        }
    } else {
        val pottedBalls = ui.currentGameShots
            .filter { it.kind == "ball" && it.ballNumber != null }
            .mapNotNull { it.ballNumber }
            .toSet()
        RefereePanel(
            participants = t.participants,
            currentUserId = ui.myUserId,
            myLocalName = ui.myLocalName,
            pottedBalls = pottedBalls,
            onShot = viewModel::addShot,
            onUndo = viewModel::undoLastShot,
            onFinish = viewModel::finishGame,
        )
    }
} else {
    val refereeName = t.participants
        .firstOrNull { it.userId == t.refereeUserId }
        ?.effectiveName(ui.myUserId, ui.myLocalName)
        ?: "?"
    ObserverPanel(refereeName = refereeName)
}
```

Заменить на:

```kotlin
// Panel by role — MatchLayout для всех, наблюдатели получают readonly-плитки.
val cg = ui.currentGame
val profile = com.example.billiardtracker.domain.rules.RuleProfile.forType(
    com.example.billiardtracker.domain.rules.GameType.entries
        .firstOrNull { it.ruleFileSlug == t.gameType } ?: com.example.billiardtracker.domain.rules.GameType.FREE_PYRAMID
)
if (cg == null || cg.status == "finished") {
    if (ui.isReferee && t.status == "active") {
        Column(Modifier.padding(16.dp)) {
            Button(
                onClick = viewModel::startGame,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Начать партию") }
        }
    } else if (!ui.isReferee) {
        val refereeName = t.participants
            .firstOrNull { it.userId == t.refereeUserId }
            ?.effectiveName(ui.myUserId, ui.myLocalName)
            ?: "?"
        Text(
            "Партия не идёт. Маркёр — $refereeName.",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
} else {
    val pottedBalls = ui.currentGameShots
        .filter { it.kind == "ball" && it.ballNumber != null }
        .mapNotNull { it.ballNumber }
        .toSet()
    val scoresByPid: Map<Long, Int> = cg.scores.associate { it.participantId to it.points }
    var sheetPid by remember { mutableStateOf<Long?>(null) }
    com.example.billiardtracker.ui.screens.tournament.scorers.MatchLayout(
        participants = t.participants,
        modifier = Modifier.padding(horizontal = 8.dp),
    ) { p ->
        com.example.billiardtracker.ui.screens.tournament.scorers.ScoreTile(
            name = p.effectiveName(ui.myUserId, ui.myLocalName),
            isReferee = t.refereeUserId != null && p.userId == t.refereeUserId,
            score = scoresByPid[p.id] ?: 0,
        ) {
            if (!ui.isReferee) {
                // Observer — плитка без активных кнопок.
                Text(
                    "наблюдатель",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else when (profile.scorerKind) {
                com.example.billiardtracker.domain.rules.ScorerKind.Counter ->
                    com.example.billiardtracker.ui.screens.tournament.scorers.CounterScorer(
                        pid = p.id,
                        profile = profile,
                        currentScore = scoresByPid[p.id] ?: 0,
                        onShot = viewModel::addShot,
                        onDecrement = viewModel::decrementScore,
                    )
                com.example.billiardtracker.domain.rules.ScorerKind.Lives ->
                    com.example.billiardtracker.ui.screens.tournament.scorers.LivesScorer(
                        pid = p.id,
                        shots = ui.currentGameShots,
                        initialLives = 3,
                        onLifeLost = { pid ->
                            viewModel.addShot(pid, "life", null, -1)
                        },
                    )
                com.example.billiardtracker.domain.rules.ScorerKind.NumberedBallGrid,
                com.example.billiardtracker.domain.rules.ScorerKind.Balance,
                com.example.billiardtracker.domain.rules.ScorerKind.Fishki ->
                    com.example.billiardtracker.ui.screens.tournament.scorers.NumberedBallGridTile(
                        pid = p.id,
                        onSelect = { sheetPid = it },
                    )
            }
        }
    }
    // Общая нижняя панель — только у маркёра.
    if (ui.isReferee) {
        val targetHint = buildString {
            when {
                profile.winTargetPoints != null -> append("до ${profile.winTargetPoints} очков")
                profile.winTargetBalls != null -> append("до ${profile.winTargetBalls} шаров")
                else -> append("баланс / жизни")
            }
            t.participants.forEach { p ->
                val s = scoresByPid[p.id] ?: 0
                append(" · ${p.effectiveName(ui.myUserId, ui.myLocalName)} $s")
            }
        }
        // Auto-winner для confirm-диалога.
        val autoWinner = t.participants
            .maxByOrNull { scoresByPid[it.id] ?: 0 }
        com.example.billiardtracker.ui.screens.tournament.scorers.MatchBottomBar(
            targetHint = targetHint,
            winnerName = autoWinner?.effectiveName(ui.myUserId, ui.myLocalName),
            winnerScore = autoWinner?.let { scoresByPid[it.id] ?: 0 },
            onUndo = viewModel::undoLastShot,
            onFinish = { viewModel.finishGame() },
        )
    }
    // Bottom-sheet для NumberedBallGrid — вне MatchLayout, чтобы был поверх.
    sheetPid?.let { pid ->
        val name = t.participants.firstOrNull { it.id == pid }
            ?.effectiveName(ui.myUserId, ui.myLocalName) ?: ""
        com.example.billiardtracker.ui.screens.tournament.scorers.NumberedBallGridSheet(
            selectedPid = pid,
            selectedName = name,
            pottedBalls = pottedBalls,
            onDismiss = { sheetPid = null },
            onShot = viewModel::addShot,
        )
    }
}
```

Also add these imports to the top of TournamentScreen.kt if not already present:

```kotlin
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
```

- [ ] **Step 2: Delete `RefereePanel.kt` and `ObserverPanel.kt`** — они больше нигде не используются.

```powershell
Remove-Item app/src/main/java/com/example/billiardtracker/ui/screens/tournament/RefereePanel.kt
Remove-Item app/src/main/java/com/example/billiardtracker/ui/screens/tournament/ObserverPanel.kt
```

- [ ] **Step 3: Verify compilation + tests**:

```powershell
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest --tests "com.example.billiardtracker.ui.screens.tournament.*" --tests "com.example.billiardtracker.domain.rules.*"
```

Expected: BUILD SUCCESSFUL; all VM+RuleProfile+layout+lives+decrement tests PASS. (Pre-existing `PayoutCalculatorTest` fail is out of scope — do NOT run the full suite.)

- [ ] **Step 4: Stage**:

```powershell
git add app/src/main/java/com/example/billiardtracker/ui/screens/tournament/TournamentScreen.kt
git rm app/src/main/java/com/example/billiardtracker/ui/screens/tournament/RefereePanel.kt `
        app/src/main/java/com/example/billiardtracker/ui/screens/tournament/ObserverPanel.kt
```

---

## Task 10: Смок-тест на эмуляторе — 3 группы × 2/3 игроков

**Goal:** визуально подтвердить каждый scorer, проверить синк.

**Files:** none (runtime verification).

**Prereqs:** emulator `Pixel_6_API36` running.

- [ ] **Step 1: Install + wipe**:

```powershell
./gradlew :app:installDebug
adb shell pm clear com.example.billiardtracker
```

- [ ] **Step 2: Note dev-log cursor** (для фильтра after):

Открой `https://billiardtracker.alekseylosev.ru/api/dev-log?limit=1` — заметь `id` последнего события. Записать в переменную:

```powershell
$cursor = (curl -s "https://billiardtracker.alekseylosev.ru/api/dev-log?limit=1" | python -c "import json,sys; print(json.load(sys.stdin)['events'][0]['id'])")
Write-Host "cursor: $cursor"
```

Если endpoint недоступен — установи `$cursor = 0` (проверка Step 6 покажет все события от 0; будет шумнее, но всё видно).

- [ ] **Step 3: Group B smoke — «Свободная пирамида» (Counter), 2 игрока**:

Сценарий:
1. Регистрация SmokeTest22 + любой phone.
2. Создать состав TB2 с 1 игроком (Player1).
3. Выбрать «Свободная пирамида» → создать встречу (2 участника: SmokeTest22 + Player1).
4. Начать партию.
5. **Ожидание**: `MatchLayout` показывает split top/bottom, каждая плитка = имя + счёт `0` + ряд `−  +` + `Свояк Штраф`.
6. Тап `+` у первой плитки 3 раза → её счёт растёт до 3, `−` активен.
7. Тап `−` у первой плитки → счёт 2.
8. Тап `Свояк` у второй плитки → её счёт 1.
9. Тап «Партия окончена» → confirm dialog «Победитель — SmokeTest22 (2)» → «Завершить».
10. RefereePanel скрывается, «Начать партию» появляется.
11. Скриншот в `design/emu-v1-22-0-B-counter.png`.

- [ ] **Step 4: Group A smoke — «Классическая (71 очко)» (NumberedBallGrid), 2 игрока**:

Сценарий:
1. Из того же аккаунта — «Свободная пирамида» → назад → выбрать «Классическая (71 очко)» → создать встречу.
2. Начать партию.
3. **Ожидание**: две плитки со строкой «Тап для ввода счёта».
4. Тап на первую плитку → bottom-sheet раскрылся, «Забитый шар — SmokeTest22», сетка 15 шаров.
5. Тап на шар 11 → sheet закрылся, счёт SmokeTest22 = 11.
6. Тап на вторую плитку → sheet со шаром 11 disabled (уже забит).
7. Тап на шар 5 → счёт Player1 = 5.
8. Тап «Партия окончена» → confirm → завершить.
9. Скриншот в `design/emu-v1-22-0-A-numbered.png`.

- [ ] **Step 5: Group C smoke — «Алагёр» (Lives), 3 игрока**:

Сценарий:
1. Составить команду с 2 игроками (Player2 + Player3), плюс сам SmokeTest22 = 3.
2. Выбрать «Алагёр» → создать встречу (3 участника).
3. Начать партию.
4. **Ожидание**: `MatchLayout` = Grid 2×2, три ячейки с плитками (у каждой по 3 крестика ✕✕✕), четвёртая пустая.
5. Тап крестик первого игрока → у него 2 жизни.
6. Тап ещё 2 раза → 0 жизней, у плитки бейдж «выбыл», крестики disabled.
7. Тап «Партия окончена» → confirm → завершить (auto-winner = игрок с макс жизней).
8. Скриншот в `design/emu-v1-22-0-C-lives-3p.png`.

- [ ] **Step 6: Verify dev-log**:

```powershell
$cutoff = $cursor
curl -s "https://billiardtracker.alekseylosev.ru/api/dev-log?limit=200" | python -c "
import json, sys, os
sys.stdout.reconfigure(encoding='utf-8')
cutoff = int(os.environ.get('CUTOFF', '0'))
data = json.loads(sys.stdin.read())
for e in data['events']:
    if e.get('id') <= cutoff: continue
    a = e.get('action','')
    if 'op-' in a or 'remap' in a or 'drain-max' in a or 'op-exception' in a:
        print(f\"[{e['id']}] {a} ok={e.get('ok')} payload={e.get('payload')}\")
" $env:CUTOFF=$cutoff
```

Expected: три `create_tournament` + `start_game` + все `add_shot` = op-ok; ноль `drain-max-passes`, ноль `op-exception`. ⏳ badge в приложении = 0.

- [ ] **Step 7: Report** (не commit) — если что-то сломалось, фиксить и повторять этот task.

---

## Task 11: Bump version + README + commit

**Goal:** единый commit v1.22.0 с bump и README-нотой.

**Files:**
- Modify: `app/build.gradle.kts` — `versionCode = 78`, `versionName = "1.22.0"`.
- Modify: `README.md` — inline (v1.22.0) tag там где описывается экран встречи.

- [ ] **Step 1: Modify `app/build.gradle.kts`**:

Find:
```kotlin
versionCode = 77
versionName = "1.21.1"
```

Change to:
```kotlin
versionCode = 78
versionName = "1.22.0"
```

- [ ] **Step 2: Modify `README.md`** — найти существующий bullet про «Live-счёт: маркёр вводит удары...» и добавить `(v1.22.0)` note:

```markdown
- **Live-счёт (v1.22.0 UX-редизайн)**: экран встречи адаптируется под правила
  дисциплины. Пирамиды с равноценными шарами (Свободная, Комбинированная,
  Динамичная, Ярославка, Европейская, One Pocket, Свободная-с-продолжением) —
  крупные ± кнопки в плитке игрока, никакой раскладки шаров. Классическая /
  Малая 61 / Большая 71 — знакомая сетка 15 шаров через bottom-sheet при тапе
  на плитку игрока. Алагёр / Грош — сетка «крестиков» жизней; тап убирает
  жизнь, 0 = «выбыл». Layout адаптивный: 2 игрока → split top/bottom, 3–4 →
  2×2 grid, 5+ → скроллящийся список плиток. Confirm-dialog на «Партия
  окончена» защищает от misdaction. Kolkhoz / Fishki остаются на fallback
  через ball-grid (v1.23.0+). Маркёр вводит удары, остальные видят
  обновления через SSE.
```

Удалить старый bullet про «Live-счёт: маркёр вводит удары...» (он full-replaced).

- [ ] **Step 3: Stage все Task 1-11 файлы**:

```powershell
git add app/build.gradle.kts README.md `
        app/src/main/java/com/example/billiardtracker/domain/rules/ScorerKind.kt `
        app/src/main/java/com/example/billiardtracker/domain/rules/RuleProfile.kt `
        app/src/main/java/com/example/billiardtracker/ui/screens/tournament/scorers/ `
        app/src/main/java/com/example/billiardtracker/ui/screens/tournament/TournamentViewModel.kt `
        app/src/main/java/com/example/billiardtracker/ui/screens/tournament/TournamentScreen.kt `
        app/src/test/java/com/example/billiardtracker/domain/rules/RuleProfileTest.kt `
        app/src/test/java/com/example/billiardtracker/ui/screens/tournament/
git rm app/src/main/java/com/example/billiardtracker/ui/screens/tournament/RefereePanel.kt `
        app/src/main/java/com/example/billiardtracker/ui/screens/tournament/ObserverPanel.kt
```

- [ ] **Step 4: Verify staged state**:

```powershell
git status --short
```

Expected: `M` on build.gradle.kts + README + RuleProfile + TournamentViewModel + TournamentScreen + RuleProfileTest; `A` on 7 new scorer files + 3 new tests + ScorerKind.kt; `D` on RefereePanel.kt + ObserverPanel.kt.

- [ ] **Step 5: Commit**:

```powershell
git commit -m @'
feat(android v1.21.1→v1.22.0): встреча-редизайн под правила дисциплины

RuleProfile получил поле `scorerKind: ScorerKind` — таблица «дисциплина →
UI-паттерн ввода счёта». UI слой в новом пакете `ui/screens/tournament/
scorers/` реализует три scorer'а:
- CounterScorer: ±1 / Свояк / Штраф для пирамид с равноценными шарами
  (Свободная, Комбинированная, Динамичная, Ярославка, Европейская,
  One Pocket, Свободная-с-продолжением).
- LivesScorer: сетка «крестиков» жизней для Алагёра / Гроша, 3 жизни
  по умолчанию, 0 = бейдж «выбыл».
- NumberedBallGridScorer: плитка компактная, тап раскрывает shared
  bottom-sheet с 15 шарами + Свояк/Штраф/За борт (Классика / 61 / 71
  + Kolkhoz/Fishki fallback).

MatchLayout адаптируется под число игроков: 2 → split top/bottom,
3-4 → 2×2 grid, 5+ → скроллящийся список плиток. Только portrait —
landscape не в scope. Confirm-dialog на «Партия окончена» защищает
от misdaction (spec §2.7).

TournamentViewModel получил `decrementScore(pid)` — pure fun
`pickShotToDecrement` extracted для юнит-теста. Удалены RefereePanel
+ ObserverPanel, оба заменены MatchLayout(readonly=!isReferee).

Тесты: RuleProfileTest покрывает scorerKind для всех 14 типов;
MatchLayoutTest — layoutFor(n) для n=2..10; LivesScorerLogicTest —
livesRemaining / isEliminated; DecrementScoreTest — pickShotToDecrement.

Спека: docs/superpowers/specs/2026-08-13-vstrecha-redesign-design.md §2.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@
```

- [ ] **Step 6: Verify commit**:

```powershell
git log -1 --stat
```

Expected: single new commit; ~7-10 files created, ~5 modified, 2 deleted.

---

## Task 12: Push + release

**Goal:** отгрузить APK v1.22.0 в прод.

**Files:** none.

- [ ] **Step 1: Push**:

```powershell
git push origin main
```

- [ ] **Step 2: Run release script**:

```powershell
node E:/PROJECTS/LAV-Server/bin/.deploy/release-billiardtracker.mjs
```

- [ ] **Step 3: Verify auto-updater endpoint**:

```powershell
curl https://billiardtracker.alekseylosev.ru/version.json
```

Expected JSON: `"versionCode":78,"versionName":"1.22.0"`.

- [ ] **Step 4: Verify GH release (только v1.22.0, старый v1.21.1 удалён)**:

```powershell
curl -s https://api.github.com/repos/yalyoha/BilliardTracker/releases | python -c "import json,sys; data=json.load(sys.stdin); [print(f\"{r['tag_name']} ({r['published_at']})\") for r in data]"
```

Expected: single line `v1.22.0 (...)`.

---

## Definition of Done

- [ ] `RuleProfile.scorerKind` populated for all 14 GameType entries (RuleProfileTest 9/9 pass).
- [ ] `MatchLayout` maps participant counts to `TileLayout` per spec (MatchLayoutTest 6/6 pass).
- [ ] `TournamentViewModel.decrementScore(pid)` extracts + tests `pickShotToDecrement` (DecrementScoreTest 4/4 pass).
- [ ] `LivesScorer` derives `livesRemaining` / `isEliminated` correctly (LivesScorerLogicTest 7/7 pass).
- [ ] Три scorer-composable + `ScoreTile` + `MatchBottomBar` компилируются, покрыты смок-сценариями Task 10.
- [ ] `TournamentScreen` использует `MatchLayout` + `MatchBottomBar` + `NumberedBallGridSheet`; `RefereePanel.kt` и `ObserverPanel.kt` удалены.
- [ ] Confirm-dialog на «Партия окончена» открывается и корректно завершает.
- [ ] Emu смок Task 10 прошёл для Group A + B + C с 2 и 3 игроками; dev-log показывает ноль `op-exception`; ⏳ badge = 0.
- [ ] Скриншоты в `design/emu-v1-22-0-A-numbered.png`, `emu-v1-22-0-B-counter.png`, `emu-v1-22-0-C-lives-3p.png`.
- [ ] `versionCode = 78`, `versionName = "1.22.0"`.
- [ ] README bullet про Live-счёт обновлён с (v1.22.0) note.
- [ ] `curl https://billiardtracker.alekseylosev.ru/version.json` возвращает `"versionName":"1.22.0"`.
- [ ] GH release `v1.22.0` — единственный (v1.21.1 удалён).

## Not in scope of v1.22.0

- **Balance-scorer** (Колхоз) и **Fishki-scorer** (карамболь + кегли) — v1.23.0+. Сейчас fallback на NumberedBallGrid + banner-note (banner будет в v1.23.0 когда специалист-scorer реально появится; в v1.22.0 достаточно молчаливого fallback).
- **Landscape layout** — весь дизайн portrait.
- **Онлайн-мультиплеер-tap** (каждый со своего телефона тапает свою половину). Только маркёрская модель.
- **Кастомный `winTargetPoints`** per-match. Берём из RuleProfile.
- **Long-press на ± = +5/-5** — YAGNI для v1.22.0 (spec §2.7).
- **Swipeable tabs** (`docs/superpowers/specs/2026-08-08-swipeable-tabs-design.md`) — переезжают на v1.23.0+.
- **Follow-ups от финального ревью v1.21.1** (add_shot payload freeze, listShots resolve, remap purge, extended SyncManager tests) — отдельный v1.22.1 hotfix при необходимости.
