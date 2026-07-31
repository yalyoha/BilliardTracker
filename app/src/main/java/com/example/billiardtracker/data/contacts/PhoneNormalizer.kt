package com.example.billiardtracker.data.contacts

/**
 * Normalize raw phone strings from Android contacts into RU E.164 form.
 * - 11 digits starting with 7 or 8 → "+7XXXXXXXXXX"
 * - 10 digits (assumed RU without country code) → "+7XXXXXXXXXX"
 * - 11..15 digits (international) → "+<digits>"
 * - anything else → "" (caller must skip)
 */
internal fun normalizePhone(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    return when {
        digits.length == 11 && (digits.startsWith("7") || digits.startsWith("8")) -> "+7${digits.drop(1)}"
        digits.length == 10 -> "+7$digits"
        digits.length in 11..15 -> "+$digits"
        else -> ""
    }
}
