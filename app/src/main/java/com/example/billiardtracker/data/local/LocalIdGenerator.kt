package com.example.billiardtracker.data.local

import java.util.concurrent.atomic.AtomicLong

/**
 * Отрицательные ID для локально созданных entities (tournament / game / shot /
 * participant / team / member). После sync'а серверный ID заменяет локальный.
 *
 * Стартуем от -currentTimeMillis() и уменьшаем — так две разные сессии
 * приложения гарантированно не сгенерят одинаковый ID (nanotime всегда
 * растёт → отрицательное представление всегда меньше предыдущего).
 * Один shared counter защищает от коллизий между репо.
 */
object LocalIdGenerator {
    private val counter = AtomicLong(-System.currentTimeMillis())
    fun next(): Long = counter.decrementAndGet()
}
