package com.example.billiardtracker.domain.rules

/**
 * 14 дисциплин русского бильярда, поддерживаемых приложением.
 *
 * `ruleFileSlug` соответствует имени файла в `shared/rules/<slug>.md` и служит
 * ключом parity-словаря `RULE_PROFILES` в backend (`src/domain/rule-profiles.js`).
 */
enum class GameType(val displayName: String, val ruleFileSlug: String) {
    FREE_PYRAMID("Свободная", "svobodnaya-piramida"),
    COMBINED_PYRAMID("Московская", "kombinirovannaya-piramida"),
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
