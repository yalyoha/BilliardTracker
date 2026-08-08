# Swipeable Top-Level Tabs (v1.21.1) Design

**Goal:** Позволить пользователю переключать 5 главных табов (Игра · Составы · Профиль · Статистика · Настройки) горизонтальным свайпом с бесконечной цикличностью и slide-follows-finger анимацией. Тап по нижнему navbar продолжает работать как раньше.

**Non-goals:** Свайп на sub-экранах (Tournament, StakeSetup, Payout, ClubsAdmin, Rules, RuleDetail, AddClub, PickGameType) остаётся выключенным — там навигация через кнопки / hardware back.

---

## User Experience

- **Свайп справа налево** на главном экране → показывается следующий по порядку таб. Порядок: `Игра → Составы → Профиль → Статистика → Настройки → Игра → …` (∞-loop).
- **Свайп слева направо** → предыдущий таб. Из `Игра` → `Настройки`.
- **Slide follows finger:** экран движется вместе с пальцем в реальном времени; при отпускании — долетает до нового таба (если пересечён порог / достигнута fling velocity) или возвращается обратно.
- **Тап по табу в нижнем navbar:** плавная анимация к целевой странице по кратчайшему пути (Настройки → Игра = 1 шаг вправо, не 4 шага влево через все табы).
- **Sub-screen открыт** (например Tournament) → свайп между табами не работает; свайп можно использовать только когда открыт Main.
- **Подсветка активного таба в navbar** обновляется когда пейджер settle'ится на новую страницу.
- **State табов сохраняется:** scroll position каждого экрана переживает свайп в другой таб и обратно — все 5 pages живы в композиции пока Main на back-stack'е.
- **Bottom nav visibility:** остаётся всегда виден (включая на sub-screens), как сейчас — не меняем.

---

## Architecture

**Ключевое решение — единственный top-level route `Main`:**

Текущая структура: `NavHost` регистрирует 5 top-level routes (Game/Team/Profile/Stats/Settings) + 8 sub-routes. Bottom-nav клики → `nav.navigate(topRoute)` с `popUpTo(Game) { saveState = true }`.

Новая структура: убираем 5 top-level routes из `NavHost`; добавляем один `Route.Main`. NavHost содержит **только** `Route.Main` + все sub-routes. `Route.Main` рендерит `HorizontalPager` с 5 страницами (по одной на таб). Bottom-nav клики → **не** `nav.navigate`, а `pagerState.animateScrollToPage(target)`. Клики со sub-screen → `nav.popBackStack()` до Main + `pagerState.scrollToPage(target)`.

Пять существующих `Route.Game/Team/Profile/Stats/Settings` **удаляются**, потому что перестают быть nav-destination'ами (заменяются на pager-страницы). Единственное место где они использовались как destinations — навигационные лямбды и `popUpTo` (см. Migration Details).

**Infinite loop trick:**
```kotlin
val startIndex = Int.MAX_VALUE / 2 - (Int.MAX_VALUE / 2 % 5)  // округляем вниз до кратного 5 — стартовая страница = Игра (index 0)
val pagerState = rememberSaveable(saver = PagerState.Saver) {
    PagerState(currentPage = startIndex, pageCount = { Int.MAX_VALUE })
}
val activeTab = pagerState.currentPage % 5  // 0..4
```

Сажаем стартовую позицию глубоко в середину диапазона `Int`. Даже при 1000 свайпов/день юзеру не грозит упереться в край за десятки лет — «infinity в обе стороны».

**Root layout:**

```kotlin
val backStack by nav.currentBackStackEntryAsState()
val currentRoute = backStack?.destination?.route
val pagerState = rememberSaveable(saver = PagerState.Saver) {
    PagerState(currentPage = startIndex, pageCount = { Int.MAX_VALUE })
}
val scope = rememberCoroutineScope()

Scaffold(
    bottomBar = {
        BilliardBottomNav(
            activeTab = computeActiveTab(currentRoute, pagerState.currentPage),
            onTabClick = { targetTab ->
                scope.launch {
                    if (currentRoute != Route.Main.path) {
                        // На sub-screen: закрываем sub, потом ставим таб без анимации
                        nav.popBackStack(Route.Main.path, inclusive = false)
                        pagerState.scrollToPage(nearestPageForTab(pagerState.currentPage, targetTab))
                    } else {
                        // На Main: плавная анимация к ближайшей странице целевого таба
                        pagerState.animateScrollToPage(nearestPageForTab(pagerState.currentPage, targetTab))
                    }
                }
            },
        )
    },
) { padding ->
    Box(Modifier.padding(padding).fillMaxSize()) {
        NavHost(nav, startDestination = Route.Main.path) {
            composable(Route.Main.path) {
                MainPager(container, nav, pagerState)
            }
            composable(Route.StakeSetup.path) { ... }  // все sub-routes как раньше
            composable(Route.Tournament.path) { ... }
            composable(Route.Payout.path) { ... }
            composable(Route.PickGameType.path) { ... }
            composable(Route.RuleDetail.path) { ... }
            composable(Route.AddClub.path) { ... }
            composable(Route.ClubsAdmin.path) { ... }
            composable("rules-tab") { ... }  // RulesListScreen — доступен из Settings через nav.navigate
        }
        // pending-sync overlay — как сейчас
    }
}
```

**MainPager composable:**

```kotlin
@Composable
fun MainPager(container: AppContainer, nav: NavHostController, pagerState: PagerState) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        key = { it % 5 },  // одна инстанс на каждый tab, а не на каждую page-position
    ) { page ->
        when (page % 5) {
            0 -> GameTabHost(container, nav)
            1 -> TeamTabHost(container, nav)
            2 -> ProfileTabHost(container, nav)
            3 -> StatsTabHost(container, nav)
            else -> SettingsTabHost(container, nav)  // 4 + fallback
        }
    }
}
```

`key = { it % 5 }` критично: без него Pager считает каждую позицию (startIndex, startIndex+1, ...) уникальной страницей и создаёт свежий composable/ViewModel для соседа при каждом свайпе → перезагружаются данные с сервера, теряется scroll. С key = mod 5, HomeTabHost для `page=X` и `page=X+5` — тот же composable с тем же remembered state. ViewModel'ы создаются один раз на таб.

**Bottom nav — активный таб:**

```kotlin
private fun computeActiveTab(currentRoute: String?, currentPage: Int): Int = when {
    currentRoute == null -> 0                                            // startup, ещё не разложились
    currentRoute == Route.Main.path -> currentPage % 5                   // на Main — по пейджеру
    currentRoute in gameFlowRoutes -> 0                                  // как сейчас: StakeSetup/Tournament/... → Игра
    currentRoute in setOf(Route.Rules, Route.RuleDetail, Route.ClubsAdmin).map { it.path } -> 4  // Настройки
    else -> 0
}
```

Правило то же что сейчас в `BilliardNav`, только вместо матчинга по 5 topRoutes — pageMod'у.

**nearestPageForTab:**

```kotlin
/** Ближайший page-index, чей `% 5` = tab. Даёт правильное направление анимации: Настройки→Игра = +1, не -4. */
internal fun nearestPageForTab(fromPage: Int, targetTab: Int): Int {
    val fromMod = ((fromPage % 5) + 5) % 5
    val forwardDelta = ((targetTab - fromMod) + 5) % 5      // 0..4
    val backwardDelta = forwardDelta - 5                     // -5..-1
    val delta = if (forwardDelta <= -backwardDelta) forwardDelta else backwardDelta
    return fromPage + delta
}
```

Unit-тест обязателен (см. Testing).

**Tab host extraction:**

Пять top-level composables из текущего `NavHost` (`composable(Route.Game.path) { HomeScreen(...) }` etc.) выносятся в отдельные `@Composable fun <Name>TabHost(container: AppContainer, nav: NavHostController)` функции — там же где определены соответствующие ViewModel-ы:
- `ui/screens/home/GameTabHost.kt`
- `ui/screens/team/TeamTabHost.kt`
- `ui/screens/profile/ProfileTabHost.kt`
- `ui/screens/stats/StatsTabHost.kt`
- `ui/screens/settings/SettingsTabHost.kt`

Каждый TabHost содержит: `remember { ViewModel(...) }` + вызов Screen composable + wiring nav-лямбд (`onOpenTournament = { id -> nav.navigate(Route.Tournament.build(id)) }` etc.). Логика идентична текущим `composable(Route.X.path) { ... }` блокам.

**Nested scroll и жесты:** `HorizontalPager` из коробки консюмит только horizontal drag. Vertical scroll в LazyColumn работает штатно. Рискованных зон нет:
- RefereePanel с 15-балльной сеткой — sub-screen (не свайпится).
- ExposedDropdown в StakeSetup — sub-screen.
- Switch'ы в SettingsScreen — их драг горизонтальный, но thumb'а немного — на широком свайпе Pager перехватит; на коротком тапе — Switch сработает. Стандартное поведение Material.

**Deep links:** App-Link `https://billiardtracker.alekseylosev.ru/live/<token>` → нужно всё ещё работать. В текущем коде обработка в `BilliardNavHost` LaunchedEffect + adopt через `tokenRepository.subscribe`. Adopted token меняет `activeTokenId` — Pager на этот момент уже показывает Game (startup), никаких доп. переходов не надо. ОК.

---

## Migration Details

**Что удаляется:**
- `Route.Game`, `Route.Team`, `Route.Profile`, `Route.Stats`, `Route.Settings` — subclasses `Route`. **Заменяются** одним `Route.Main`.
- `composable(Route.Game.path) { ... }`, ... — пять блоков. Заменяются одним `composable(Route.Main.path) { MainPager(...) }`.
- В bottom-nav клик-хендлере: `nav.navigate(tab.route) { popUpTo(Route.Game.path) { saveState=true; inclusive=false } ... }` — заменяется на `pagerState.animateScrollToPage(...)` (см. выше).

**Что правится в sub-screen nav-лямбдах:**

Сейчас несколько мест зовут `Route.Game.path`:
1. `StakeSetupScreen.onCreated` → `nav.navigate(Route.Tournament.build(id)) { popUpTo(Route.Game.path) { inclusive = false } }`
2. `TournamentScreen.onOpenPayout` → `nav.navigate(Route.Payout.build(id)) { popUpTo(Route.Game.path) { inclusive = false } }`
3. `PayoutScreen.onClose` → `nav.navigate(Route.Game.path) { popUpTo(Route.Game.path) { inclusive = true }; launchSingleTop = true }`
4. `HomeScreen.onAddTeam` → `nav.navigate(Route.Team.path) { popUpTo(Route.Game.path) { saveState = true; inclusive = false } ... }`

Правки:
1. `popUpTo(Route.Main.path)` — то же поведение (закрыть весь стек до Main).
2. То же.
3. `PayoutScreen.onClose`: `nav.popBackStack(Route.Main.path, inclusive = false); pagerState.scrollToPage(nearestPageForTab(currentPage, 0))` — уходим на Main, выставляем таб Игра. Для этого `onClose` лямбда получает `pagerState` (пробрасывается через MainPager).
4. `HomeScreen.onAddTeam`: **удаляется как nav-jump**, заменяется на `scope.launch { pagerState.animateScrollToPage(nearestPageForTab(currentPage, 1)) }` — свайп-анимация на таб Составы. UX улучшается: юзер видит слайд к нужному табу вместо nav-transition.

Bottom-nav highlight правится (см. `computeActiveTab` выше).

---

## Data Flow

Нет data flow как такового — фича 100% UI. Использует Compose state (`rememberSaveable` для PagerState, `currentBackStackEntryAsState()`). Никаких изменений в repo/prefs/backend.

---

## Error Handling

Нет error paths. Единственный edge case — если `pagerState.currentPage % 5` даст не 0..4 (невозможно математически, `%` в Kotlin для положительного делителя всегда 0..4), в `when` есть `else -> SettingsTabHost` fallback как safety net (Settings — самый безобидный default).

---

## Testing

**Unit-тесты (JUnit4, новый файл):**
- `app/src/test/java/com/example/billiardtracker/ui/nav/NearestPageForTabTest.kt`

Покрытие функции `nearestPageForTab`:
- `nearestPageForTab(fromPage=100, targetTab=0)` где `100 % 5 == 0` → 100 (стоим на месте).
- Соседи вперёд: `nearestPageForTab(100, 1) → 101`, `nearestPageForTab(100, 2) → 102`.
- Соседи назад: `nearestPageForTab(100, 4) → 99` (не 104), `nearestPageForTab(100, 3) → 98` (не 103 — расстояния равны, выбираем forward по `<=`).
- ∞-loop wrap: `nearestPageForTab(0, 4) → -1`, `nearestPageForTab(Int.MAX_VALUE-1, 0) → Int.MAX_VALUE` (осторожно с overflow).

**Manual regression в Pixel_6_API36:**
1. На каждом из 5 табов свайпнуть влево и вправо → переключение на соседний.
2. ∞-loop: с Настройки свайпнуть влево (→ Игра); с Игры свайпнуть вправо (→ Настройки).
3. Тап bottom nav: с Игры на Настройки → анимация 1 шаг влево (короткий путь), не 4 шага вправо. Симметрично.
4. Открыть Tournament (создать встречу до конца StakeSetup) — свайп на Tournament не работает.
5. Открыть sub-screen → back → таб остался тот же с которого уходили.
6. Scroll на Настройках вниз → свайпнуть в Игру → свайпнуть обратно на Настройки → scroll position сохранён.
7. Тап bottom nav "Составы" когда открыт Tournament → закрывает Tournament, показывает Составы.
8. HomeScreen «Добавить состав» кнопка → плавно свайпает на таб Составы (не nav-transition).
9. Прервать свайп в середине жестом → корректно отпустить (Pager сам разрулит fling/settle).

---

## File Map

**Modified:**
- `app/src/main/java/com/example/billiardtracker/ui/nav/BilliardNav.kt` — переработка: единый Route.Main, вырезаются 5 top-composables, добавляется MainPager + функции computeActiveTab/nearestPageForTab.
- `app/src/main/java/com/example/billiardtracker/ui/screens/home/HomeScreen.kt` — `onAddTeam` signature: `() -> Unit` остаётся, но реализация в `GameTabHost` вызывает pagerState вместо nav.
- `app/src/main/java/com/example/billiardtracker/ui/screens/tournament/PayoutScreen.kt` — `onClose` реализация в NavHost блоке использует pagerState (сама композабл не меняется).
- `app/build.gradle.kts` — versionCode 76→77, versionName 1.21.0→1.21.1.

**Created:**
- `app/src/main/java/com/example/billiardtracker/ui/screens/home/GameTabHost.kt`
- `app/src/main/java/com/example/billiardtracker/ui/screens/team/TeamTabHost.kt`
- `app/src/main/java/com/example/billiardtracker/ui/screens/profile/ProfileTabHost.kt`
- `app/src/main/java/com/example/billiardtracker/ui/screens/stats/StatsTabHost.kt`
- `app/src/main/java/com/example/billiardtracker/ui/screens/settings/SettingsTabHost.kt`
- `app/src/test/java/com/example/billiardtracker/ui/nav/NearestPageForTabTest.kt`

Каждый `<Name>TabHost` содержит логику создания ViewModel + вызова соответствующего Screen composable + wiring nav-lambdas. TabHost'ы принимают `pagerState: PagerState` там где нужно (Game — для onAddTeam swipe; остальные — nav-only).

**No changes:**
- Все `<Name>Screen.kt` composables — их API не меняется (кроме HomeScreen как отмечено выше — API стабильно, меняется только call site).
- `AppContainer.kt`, prefs, repos, backend — не затронуты.
- Deep-link handling — не затронуто.

---

## Compat / Release

- Мелкая UX-фича без breaking-change → PATCH bump 1.21.0 → 1.21.1 (по `feedback_versioning`).
- Релиз через `release-billiardtracker.mjs` (auto-updater юзеров подхватит на ближайшем ON_RESUME).
- README не обновляется — фича неявная, юзер обнаружит естественно.

---

## Open Questions

Нет. Все ключевые решения приняты в брэйнстормг-фазе.
