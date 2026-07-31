package com.example.billiardtracker.domain.rules

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Test

class PayoutCalculatorTest {
    private val json = javaClass.classLoader!!.getResource("payout-cases.json")!!.readText()
    private val root = Json.parseToJsonElement(json).jsonObject
    private val cases = root["cases"]!!.jsonArray

    @Test
    fun `parity with backend for all 7 canonical cases`() {
        for (case in cases) {
            val name = case.jsonObject["name"]!!.jsonPrimitive.content
            val input = case.jsonObject["input"]!!.jsonObject
            val expected = case.jsonObject["expected"]!!.jsonObject

            val tournament = PayoutInputTournament(
                id = input["tournament"]!!.jsonObject["id"]!!.jsonPrimitive.long,
                moneyPerBallKop = input["tournament"]!!.jsonObject["moneyPerBallKop"]?.jsonPrimitive?.longOrNull,
            )
            val participants = input["participants"]!!.jsonArray.map { p ->
                val po = p.jsonObject
                PayoutInputParticipant(
                    id = po["id"]!!.jsonPrimitive.long,
                    handicapPoints = po["handicapPoints"]!!.jsonPrimitive.int,
                    perBallOverrideKop = po["perBallOverrideKop"]?.jsonPrimitive?.longOrNull,
                )
            }
            val shots = input["shots"]!!.jsonArray.map { s ->
                val so = s.jsonObject
                PayoutInputShot(
                    participantId = so["participantId"]!!.jsonPrimitive.long,
                    kind = so["kind"]!!.jsonPrimitive.content,
                    pointsDelta = so["pointsDelta"]!!.jsonPrimitive.int,
                )
            }

            val actual = PayoutCalculator.compute(tournament, participants, shots)

            // Scores
            val expectedScores = expected["scores"]!!.jsonObject.mapKeys { it.key.toLong() }
                .mapValues { it.value.jsonPrimitive.int }
            assertEquals("scores mismatch in case: $name", expectedScores, actual.scores)

            // Payouts (order-independent)
            val expectedPayouts = expected["payouts"]!!.jsonArray.map { p ->
                val po = p.jsonObject
                PayoutEntry(
                    fromParticipantId = po["fromParticipantId"]!!.jsonPrimitive.long,
                    toParticipantId = po["toParticipantId"]!!.jsonPrimitive.long,
                    amountKop = po["amountKop"]!!.jsonPrimitive.long,
                )
            }
            val sortKey: (PayoutEntry) -> String = { "${it.fromParticipantId}->${it.toParticipantId}" }
            assertEquals(
                "payouts mismatch in case: $name",
                expectedPayouts.sortedBy(sortKey),
                actual.payouts.sortedBy(sortKey),
            )
        }
    }
}
