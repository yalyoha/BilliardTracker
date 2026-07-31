package com.example.billiardtracker.domain.rules

data class PayoutInputTournament(val id: Long, val moneyPerBallKop: Long?)

data class PayoutInputParticipant(
    val id: Long,
    val handicapPoints: Int,
    val perBallOverrideKop: Long?,
)

data class PayoutInputShot(
    val participantId: Long,
    val kind: String,
    val pointsDelta: Int,
)

data class PayoutEntry(
    val fromParticipantId: Long,
    val toParticipantId: Long,
    val amountKop: Long,
)

data class PayoutResult(
    val scores: Map<Long, Int>,
    val payouts: List<PayoutEntry>,
)

object PayoutCalculator {
    fun compute(
        tournament: PayoutInputTournament,
        participants: List<PayoutInputParticipant>,
        shots: List<PayoutInputShot>,
    ): PayoutResult {
        val scores = mutableMapOf<Long, Int>()
        for (p in participants) scores[p.id] = p.handicapPoints
        for (s in shots) {
            if (scores.containsKey(s.participantId)) {
                scores[s.participantId] = scores[s.participantId]!! + s.pointsDelta
            }
        }
        val payouts = mutableListOf<PayoutEntry>()
        if (tournament.moneyPerBallKop == null) return PayoutResult(scores, payouts)

        val rateOf: (PayoutInputParticipant) -> Long =
            { it.perBallOverrideKop ?: tournament.moneyPerBallKop }
        val byId = participants.associateBy { it.id }

        val ids = participants.map { it.id }.sorted()
        for (i in ids.indices) {
            for (j in i + 1 until ids.size) {
                val a = ids[i]; val b = ids[j]
                val diff = (scores[a] ?: 0) - (scores[b] ?: 0)
                if (diff == 0) continue
                val rate = minOf(rateOf(byId[a]!!), rateOf(byId[b]!!))
                if (diff > 0) {
                    payouts += PayoutEntry(fromParticipantId = b, toParticipantId = a, amountKop = (diff.toLong()) * rate)
                } else {
                    payouts += PayoutEntry(fromParticipantId = a, toParticipantId = b, amountKop = (-diff.toLong()) * rate)
                }
            }
        }
        return PayoutResult(scores, payouts)
    }
}
