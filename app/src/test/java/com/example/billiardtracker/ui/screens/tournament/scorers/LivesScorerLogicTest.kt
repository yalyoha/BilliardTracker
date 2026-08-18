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
        ts = 0L, enteredByUserId = 0L,
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
            ShotDto(1, 0, 10, "ball", null, +1, 0L, 0L),
            ShotDto(2, 0, 10, "foul", null, -1, 0L, 0L),
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
