package com.example.billiardtracker.domain.rules

import org.junit.Assert.*
import org.junit.Test

class RuleProfileTest {

    @Test fun `classical pyramid has 15 balls valued 1 to 15 and target 71 points`() {
        val p = RuleProfile.forType(GameType.CLASSICAL_PYRAMID)
        assertEquals(15, p.ballValues.size)
        assertEquals((1..15).toList(), p.ballValues)
        assertEquals(71, p.winTargetPoints)
        assertNull(p.winTargetBalls)
        assertTrue(p.allowsHandicap)
        assertFalse(p.tabbedBallsAllowed)
        assertFalse(p.hasContinuation)
        assertTrue(p.moneyPlayable)
    }

    @Test fun `free pyramid — americana — allows svoiak and equal ball values`() {
        val p = RuleProfile.forType(GameType.FREE_PYRAMID)
        assertEquals(15, p.ballValues.size)
        assertTrue("all balls should have equal value in Americana", p.ballValues.all { it == p.ballValues[0] })
        assertTrue(p.allowsSvoiak)
        assertFalse("Americana does not return svoiak to home", p.svoiakReturnedToHome)
        assertTrue(p.moneyPlayable)
    }

    @Test fun `combined pyramid — moscow — svoiak returned to home`() {
        val p = RuleProfile.forType(GameType.COMBINED_PYRAMID)
        assertTrue(p.allowsSvoiak)
        assertTrue("Moscow variant returns cue ball to home after svoiak", p.svoiakReturnedToHome)
    }

    @Test fun `free pyramid with continuation flags hasContinuation`() {
        val p = RuleProfile.forType(GameType.FREE_PYRAMID_CONTINUATION)
        assertTrue(p.hasContinuation)
    }

    @Test fun `fishki flags tabbedBallsAllowed`() {
        val p = RuleProfile.forType(GameType.FISHKI)
        assertTrue(p.tabbedBallsAllowed)
    }

    @Test fun `one pocket wins by balls (8), not points`() {
        val p = RuleProfile.forType(GameType.ONE_POCKET_RU)
        assertEquals(8, p.winTargetBalls)
        assertNull(p.winTargetPoints)
    }

    @Test fun `every game type has a defined profile`() {
        // No TODOs — exhaustive when must cover all 14
        for (t in GameType.entries) {
            val p = RuleProfile.forType(t)
            assertEquals(t, p.type)
        }
    }
}
