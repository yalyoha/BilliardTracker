package com.example.billiardtracker.util

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Хранение: только цифры (до 11 штук, начинается с 7).
 * Отображение: +7(999)999-99-99.
 *
 * Использовать через [PhoneMaskInput] — там keyboard-type Phone + фильтрация цифр
 * + подстановка "7" в начало если пользователь начал набирать с 9.
 */
class PhoneVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text // ожидаем что уже отфильтровано в PhoneMaskInput
        val out = formatPhone(digits)
        return TransformedText(AnnotatedString(out), PhoneOffsetMapping(digits.length))
    }
}

/** "9994567890" → "+7(999)456-78-90". Ограничение — 11 цифр (начинается с 7). */
fun formatPhone(rawDigits: String): String {
    if (rawDigits.isEmpty()) return ""
    return buildString {
        append("+7")
        if (rawDigits.length > 1) {
            append("(")
            append(rawDigits.substring(1, minOf(4, rawDigits.length)))
        }
        if (rawDigits.length >= 4) {
            append(")")
            append(rawDigits.substring(4, minOf(7, rawDigits.length)))
        }
        if (rawDigits.length >= 7) {
            append("-")
            append(rawDigits.substring(7, minOf(9, rawDigits.length)))
        }
        if (rawDigits.length >= 9) {
            append("-")
            append(rawDigits.substring(9, minOf(11, rawDigits.length)))
        }
    }
}

/** Курсор всегда в конце отформатированной строки — MVP-версия. */
private class PhoneOffsetMapping(private val digitsLen: Int) : OffsetMapping {
    private val formattedLen = formatPhone("7".padEnd(digitsLen, '0').take(digitsLen)).length
    override fun originalToTransformed(offset: Int): Int = formattedLen
    override fun transformedToOriginal(offset: Int): Int = digitsLen
}

/**
 * Нормализация ввода — оставляет только цифры, приводит к формату +7 (11 цифр).
 * Если пользователь ввёл 8XXX → приводит к 7XXX. Если 9XX → добавляет 7 впереди.
 */
fun normalizeDigitsForRu(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    return when {
        digits.isEmpty() -> ""
        digits.startsWith("7") -> digits.take(11)
        digits.startsWith("8") -> "7" + digits.drop(1).take(10)
        else -> ("7" + digits).take(11)
    }
}

/** E.164 из UI-стейта: 11 цифр → "+79994567890". Пустое или неполное → null. */
fun digitsToE164(rawDigits: String): String? {
    val d = rawDigits.filter { it.isDigit() }
    return if (d.length == 11 && d.startsWith("7")) "+$d" else null
}

/** E.164 → цифры для UI: "+79994567890" → "79994567890". */
fun e164ToDigits(e164: String?): String {
    if (e164 == null) return ""
    return e164.filter { it.isDigit() }
}

/**
 * Готовая обёртка над OutlinedTextField с маской телефона.
 *
 * @param value  строка цифр (до 11), обычно из ViewModel
 * @param onChange  колбэк с нормализованной строкой цифр
 */
@Composable
fun PhoneMaskInput(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Телефон",
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> onChange(normalizeDigitsForRu(raw)) },
        label = { Text(label) },
        placeholder = { Text("+7(___)___-__-__") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        visualTransformation = PhoneVisualTransformation(),
        singleLine = true,
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(),
    )
}
