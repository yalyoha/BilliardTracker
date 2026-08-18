package com.example.billiardtracker.ui.screens.tournament

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
        ts = 0L, enteredByUserId = 0L,
    )

    @Test fun `picks last positive shot of pid`() {
        val shots = listOf(
            shot(1, pid = 10, kind = "ball", delta = 1),
            shot(2, pid = 20, kind = "ball", delta = 1),
            shot(3, pid = 10, kind = "ball", delta = 1),
            shot(4, pid = 20, kind = "ball", delta = 1),
        )
        assertEquals(3L, pickShotToDecrement(shots, pid = 10)?.id)
    }

    @Test fun `skips foul (negative) shots — only rolls back positives`() {
        val shots = listOf(
            shot(1, pid = 10, kind = "ball", delta = 1),
            shot(2, pid = 10, kind = "foul", delta = -1),
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
