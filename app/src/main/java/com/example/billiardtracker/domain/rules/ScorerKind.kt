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
