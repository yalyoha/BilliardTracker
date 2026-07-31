package com.example.billiardtracker.domain.rules

/**
 * Профиль правил дисциплины — минимальный набор параметров, необходимый
 * calculator'у выплат, movement engine'у и UI (заказы / штрафы).
 *
 * Источник значений — соответствующий `shared/rules/<ruleFileSlug>.md`
 * (сверенный с ФБСР / клубной практикой в Task 1.2). Backend хранит
 * зеркальную копию в `src/domain/rule-profiles.js` — ключи там равны
 * `ruleFileSlug`, parity-тест это гарантирует.
 */
data class RuleProfile(
    val type: GameType,
    /**
     * Номиналы 15 шаров. Для «пирамид с равноценными шарами» — `List(15) { 1 }`;
     * для Классической — `(1..15).toList()`. Фишки/Один-карман тоже используют
     * 15 равноценных, а инвентарные особенности (кегли, целевая луза) описаны
     * отдельными флагами и `notes`.
     */
    val ballValues: List<Int>,
    /** Целевое число очков (null — победа определяется числом шаров / балансом). */
    val winTargetPoints: Int?,
    /** Целевое число шаров в свою лузу (null — победа по очкам / балансу). */
    val winTargetBalls: Int?,
    /** Разрешено ли забивать биток («свояк»). */
    val allowsSvoiak: Boolean,
    /** После свояка биток вводится строго из «дома» (true) или с любого места (false). */
    val svoiakReturnedToHome: Boolean,
    /** «Переход хода при любом ударе» — договорной house-rule. По умолчанию false. */
    val alternatesTurnAlways: Boolean,
    /** Поддерживает ли дисциплина фору (гандикап). */
    val allowsHandicap: Boolean,
    /** Право слабейшего на «дубль» (два удара подряд). */
    val allowsDouble: Boolean,
    /** На столе размещаются дополнительные предметы (кегли и т.п.). Только Фишки. */
    val tabbedBallsAllowed: Boolean,
    /** Пригодно ли для игры «на шар» / «на деньги». */
    val moneyPlayable: Boolean,
    /** Реракк 14 шаров при доигрывании (только «Свободная с продолжением»). */
    val hasContinuation: Boolean = false,
    /** Free-form guidance / оговорки по правилам. */
    val notes: String? = null,
) {
    companion object {
        fun forType(type: GameType): RuleProfile = when (type) {
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
                notes = "Американка: игра любым шаром по любому; свояк засчитывается 1 очком, биток остаётся на месте.",
            )

            GameType.COMBINED_PYRAMID -> RuleProfile(
                type = type,
                ballValues = List(15) { 1 },
                winTargetPoints = 8,
                winTargetBalls = null,
                allowsSvoiak = true,
                svoiakReturnedToHome = true,
                alternatesTurnAlways = false,
                allowsHandicap = true,
                allowsDouble = false,
                tabbedBallsAllowed = false,
                moneyPlayable = true,
                notes = "Московская (Комбинированная): играют только цветным битком; после свояка биток вводится из «дома», игрок дополнительно снимает 1 шар.",
            )

            GameType.DYNAMIC_PYRAMID -> RuleProfile(
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
                notes = "Невская/Сибирская: играют только цветным битком; после свояка биток вводится с руки с любого места стола (не только из «дома»).",
            )

            GameType.CLASSICAL_PYRAMID -> RuleProfile(
                type = type,
                ballValues = (1..15).toList(),
                winTargetPoints = 71,
                winTargetBalls = null,
                allowsSvoiak = false,
                svoiakReturnedToHome = false,
                alternatesTurnAlways = false,
                allowsHandicap = true,
                allowsDouble = false,
                tabbedBallsAllowed = false,
                moneyPlayable = true,
                notes = "Русская пирамида (71 очко): заказ шар–луза, туз = 11 очков, последний шар = +10; штраф –5/+5; максимум 140 очков в партии.",
            )

            GameType.FREE_PYRAMID_CONTINUATION -> RuleProfile(
                type = type,
                ballValues = List(15) { 1 },
                // Целевой счёт задаётся регламентом турнира (обычно 15..100+); фиксируем разумный дефолт.
                // TODO: verify — shared/rules/svobodnaya-s-prodolzheniem.md п. 1 и 5.5 (счёт задаётся регламентом).
                winTargetPoints = 50,
                winTargetBalls = null,
                allowsSvoiak = true,
                svoiakReturnedToHome = false,
                alternatesTurnAlways = false,
                allowsHandicap = true,
                allowsDouble = false,
                tabbedBallsAllowed = false,
                moneyPlayable = true,
                hasContinuation = true,
                notes = "Свободная пирамида 14+: после 14-го забитого шара пирамида пере-расставляется без 15-го шара; целевой счёт задаётся регламентом.",
            )

            GameType.SMALL_RUSSIAN_PARTY -> RuleProfile(
                type = type,
                ballValues = (1..15).toList(),
                winTargetPoints = 61,
                winTargetBalls = null,
                allowsSvoiak = false,
                svoiakReturnedToHome = false,
                alternatesTurnAlways = false,
                allowsHandicap = true,
                allowsDouble = false,
                tabbedBallsAllowed = false,
                moneyPlayable = true,
                notes = "Базовая «до 61»: играют только своим битком, забивать разрешено только прицельные; в расширенной версии — до 71 с премиями за туза/последний шар.",
            )

            GameType.BIG_RUSSIAN_PARTY -> RuleProfile(
                type = type,
                // Официального регламента ФБСР нет: в базовом любительском варианте
                // играют пронумерованными шарами (туз = 11, последний +10, максимум 140).
                // Вариант «Большая пирамида»/Каролина использует 5 цветных шаров
                // (60/30/30/20/20) — сумма 300; UI будет предлагать выбор в отдельном шаге.
                // TODO: verify — shared/rules/bolshaya-russkaya-partiya.md, «Каролина» vs
                // базовый вариант; здесь взят базовый (15 нумерованных).
                ballValues = (1..15).toList(),
                winTargetPoints = 71,
                winTargetBalls = null,
                allowsSvoiak = false,
                svoiakReturnedToHome = false,
                alternatesTurnAlways = false,
                allowsHandicap = true,
                allowsDouble = false,
                tabbedBallsAllowed = false,
                moneyPlayable = true,
                notes = "Официальной дисциплины «Большая русская партия» в Минспорта РФ нет. Базовый вариант: 15 нумерованных шаров, туз=11, последний +10, штраф –5/+5. Вариант «Каролина» (5 цветных: чёрный=60, розовые 2×30, красные 2×20; цель 151) должен настраиваться регламентом матча.",
            )

            GameType.ALAGYOR -> RuleProfile(
                type = type,
                // Очковой шкалы нет — учёт через «кресты» (см. shared/rules/alagyor.md).
                // Ставим placeholder ballValues, чтобы data-class был валиден.
                // TODO: verify — Алагёр не использует шаровую очковую модель;
                // engine должен переключаться на «жизни/кресты» для этой дисциплины.
                ballValues = List(15) { 1 },
                winTargetPoints = null,
                winTargetBalls = null,
                allowsSvoiak = false,
                svoiakReturnedToHome = false,
                alternatesTurnAlways = true,
                allowsHandicap = false,
                allowsDouble = false,
                tabbedBallsAllowed = false,
                moneyPlayable = true,
                notes = "Гусарская игра на выбывание: каждому игроку присваивается «именной» шар, побеждает последний оставшийся; учёт — по «крестам» (обычно 3), а не по очкам.",
            )

            GameType.YAROSLAVSKAYA -> RuleProfile(
                type = type,
                ballValues = List(15) { 1 },
                winTargetPoints = 8,
                winTargetBalls = null,
                allowsSvoiak = true,
                svoiakReturnedToHome = true,
                alternatesTurnAlways = false,
                allowsHandicap = true,
                allowsDouble = false,
                tabbedBallsAllowed = false,
                moneyPlayable = true,
                notes = "Разновидность Комбинированной: за свояк шар со стола снимает соперник (выбирая самый неудобный).",
            )

            GameType.KOLKHOZ -> RuleProfile(
                type = type,
                ballValues = List(15) { 1 },
                // Победитель — по итоговому балансу (плюс/минус со всеми участниками), не по фиксированному счёту.
                // TODO: verify — движок должен переключаться на pot-based scoring; здесь
                // просто фиксируем «нет целевого счёта».
                winTargetPoints = null,
                winTargetBalls = null,
                allowsSvoiak = false,
                svoiakReturnedToHome = false,
                alternatesTurnAlways = true,
                allowsHandicap = false,
                allowsDouble = false,
                tabbedBallsAllowed = false,
                moneyPlayable = true,
                notes = "3+ игроков, каждый сам за себя: за забитый шар игрок получает по 1 очку/сумме со всех остальных, за штрафной — платит всем. Победитель — с лучшим итоговым балансом.",
            )

            GameType.FISHKI -> RuleProfile(
                type = type,
                // Инвентарь: красный биток + розовый + чёрный (не 15 равноценных).
                // Для data-class'а фиксируем 15 равноценных как placeholder, а
                // фактический сценарий должен читаться из notes / отдельного FishkiRules.
                // TODO: verify — shared/rules/fishki.md, инвентарь принципиально
                // отличается от пирамиды (3 шара + 7 кеглей).
                ballValues = List(15) { 1 },
                // Победа: обнулить лимит «с горы» (обычно 100), забить оба прицельных,
                // сбить все кегли одним ударом или сделать 5 карамболей подряд.
                winTargetPoints = 100,
                winTargetBalls = null,
                allowsSvoiak = false,
                svoiakReturnedToHome = false,
                alternatesTurnAlways = false,
                allowsHandicap = true,
                allowsDouble = false,
                tabbedBallsAllowed = true,
                moneyPlayable = true,
                notes = "Карамболь с кеглями (7 фишек): большая кегля 25 (изолированно 50), 4 малые по 5, 2 боковые по 30, карамболь 5. Игра «с горы» — обнулить лимит.",
            )

            GameType.ONE_POCKET_RU -> RuleProfile(
                type = type,
                ballValues = List(15) { 1 },
                winTargetPoints = null,
                winTargetBalls = 8,
                allowsSvoiak = false,
                svoiakReturnedToHome = false,
                alternatesTurnAlways = false,
                allowsHandicap = true,
                allowsDouble = false,
                tabbedBallsAllowed = false,
                moneyPlayable = true,
                notes = "Каждому игроку — одна из двух ближних угловых луз; засчитываются только шары в свою лузу. Обычно до 8 шаров.",
            )

            GameType.GROSH -> RuleProfile(
                type = type,
                ballValues = List(15) { 1 },
                // Учёт — по «жизням/грошам» (обычно 3–5), а не по очкам.
                // TODO: verify — shared/rules/grosh.md, движок должен переключаться на
                // life-based scoring; здесь фиксируем «нет целевого счёта».
                winTargetPoints = null,
                winTargetBalls = null,
                allowsSvoiak = false,
                svoiakReturnedToHome = false,
                alternatesTurnAlways = true,
                allowsHandicap = false,
                allowsDouble = false,
                tabbedBallsAllowed = false,
                moneyPlayable = true,
                notes = "3+ игроков по кругу, один удар на игрока. Промах — минус «жизнь» (обычно из 3–5). Побеждает последний оставшийся.",
            )

            GameType.EUROPEAN_PYRAMID -> RuleProfile(
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
                notes = "Гибрид Свободной: свояк засчитывается только с заранее объявленной траекторией («накат» или «отскок»); незаказанный свояк — штраф.",
            )
        }
    }
}
