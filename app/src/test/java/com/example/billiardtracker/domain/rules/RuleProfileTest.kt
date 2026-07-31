package com.example.billiardtracker.domain.rules

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    @Test fun `field-level parity — all 14 profiles match shared expected fixture`() {
        // The JSON file at app/src/test/resources/rule-profiles-expected.json is a copy of
        // BilliardTracker/shared/rule-profiles-expected.json (single source of truth).
        // The backend test loads the shared file directly via a relative path.
        val json = javaClass.classLoader!!
            .getResource("rule-profiles-expected.json")!!
            .readText()
        val expected = Json.parseToJsonElement(json).jsonObject

        var checked = 0
        for ((slug, node) in expected) {
            if (slug.startsWith("_")) continue // meta / comment keys
            val gameType = GameType.entries.firstOrNull { it.ruleFileSlug == slug }
                ?: fail("no GameType with ruleFileSlug=$slug") as Nothing
            val actual = RuleProfile.forType(gameType)
            val obj = node.jsonObject

            assertEquals(
                "ballValues[$slug]",
                obj["ballValues"]!!.jsonArray.map { it.jsonPrimitive.int },
                actual.ballValues,
            )
            assertEquals(
                "winTargetPoints[$slug]",
                obj["winTargetPoints"]?.jsonPrimitive?.intOrNull,
                actual.winTargetPoints,
            )
            assertEquals(
                "winTargetBalls[$slug]",
                obj["winTargetBalls"]?.jsonPrimitive?.intOrNull,
                actual.winTargetBalls,
            )
            assertEquals(
                "allowsSvoiak[$slug]",
                obj["allowsSvoiak"]!!.jsonPrimitive.boolean,
                actual.allowsSvoiak,
            )
            assertEquals(
                "svoiakReturnedToHome[$slug]",
                obj["svoiakReturnedToHome"]!!.jsonPrimitive.boolean,
                actual.svoiakReturnedToHome,
            )
            assertEquals(
                "alternatesTurnAlways[$slug]",
                obj["alternatesTurnAlways"]!!.jsonPrimitive.boolean,
                actual.alternatesTurnAlways,
            )
            assertEquals(
                "allowsHandicap[$slug]",
                obj["allowsHandicap"]!!.jsonPrimitive.boolean,
                actual.allowsHandicap,
            )
            assertEquals(
                "allowsDouble[$slug]",
                obj["allowsDouble"]!!.jsonPrimitive.boolean,
                actual.allowsDouble,
            )
            assertEquals(
                "tabbedBallsAllowed[$slug]",
                obj["tabbedBallsAllowed"]!!.jsonPrimitive.boolean,
                actual.tabbedBallsAllowed,
            )
            assertEquals(
                "moneyPlayable[$slug]",
                obj["moneyPlayable"]!!.jsonPrimitive.boolean,
                actual.moneyPlayable,
            )
            assertEquals(
                "hasContinuation[$slug]",
                obj["hasContinuation"]!!.jsonPrimitive.boolean,
                actual.hasContinuation,
            )
            checked++
        }
        assertEquals("must have checked all 14 disciplines", 14, checked)
    }
}
