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

        // Compute per-player NET balance by summing pairwise differences.
        // Positive net = owed money, negative = owes money.
        val net = mutableMapOf<Long, Long>()
        val ids = participants.map { it.id }.sorted()
        for (i in ids.indices) {
            for (j in i + 1 until ids.size) {
                val a = ids[i]; val b = ids[j]
                val diff = (scores[a] ?: 0) - (scores[b] ?: 0)
                if (diff == 0) continue
                val rate = minOf(rateOf(byId[a]!!), rateOf(byId[b]!!))
                val amount = kotlin.math.abs(diff.toLong()) * rate
                if (diff > 0) {
                    net[a] = (net[a] ?: 0L) + amount
                    net[b] = (net[b] ?: 0L) - amount
                } else {
                    net[b] = (net[b] ?: 0L) + amount
                    net[a] = (net[a] ?: 0L) - amount
                }
            }
        }

        // Debt simplification: greedy match largest debtor and creditor,
        // repeatedly transferring min(|debt|, credit). Collapses chains like
        // A→B, B→C into a single A→C — what the user actually cares about
        // when settling up.
        val creditors = net.filter { it.value > 0 }
            .toList().sortedByDescending { it.second }.toMutableList()
        val debtors = net.filter { it.value < 0 }
            .toList().sortedBy { it.second }.toMutableList()

        while (creditors.isNotEmpty() && debtors.isNotEmpty()) {
            val (creditorId, creditorAmt) = creditors.first()
            val (debtorId, debtorAmt) = debtors.first()
            val transfer = minOf(creditorAmt, -debtorAmt)
            payouts += PayoutEntry(
                fromParticipantId = debtorId,
                toParticipantId = creditorId,
                amountKop = transfer,
            )
            val newCredit = creditorAmt - transfer
            val newDebt = debtorAmt + transfer
            creditors.removeAt(0)
            debtors.removeAt(0)
            if (newCredit > 0) creditors.add(0, creditorId to newCredit)
            if (newDebt < 0) debtors.add(0, debtorId to newDebt)
        }
        return PayoutResult(scores, payouts)
    }
}
