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

    // Greedy debt simplification: largest creditor ↔ largest debtor.
    // Returns a list of PayoutEntry transfers. Modifies `net` in place.
    private fun settleNet(net: MutableMap<Long, Long>): List<PayoutEntry> {
        val payouts = mutableListOf<PayoutEntry>()
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
        return payouts
    }

    /**
     * Расчёт выплат по шарам (per_ball): каждый шар = moneyPerBallKop с проигравшего.
     */
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
        if (tournament.moneyPerBallKop == null) return PayoutResult(scores, mutableListOf())

        val rateOf: (PayoutInputParticipant) -> Long =
            { it.perBallOverrideKop ?: tournament.moneyPerBallKop }
        val byId = participants.associateBy { it.id }

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
        return PayoutResult(scores, settleNet(net))
    }

    /**
     * Расчёт для Колхоза: moneyPerBallKop — итоговая сумма ЗАБИВШЕМУ за 1 шар.
     * При N игроках с каждого соперника берётся priceKop / (N-1), чтобы в сумме
     * забивший получал ровно priceKop, независимо от числа участников.
     */
    fun computeKolkhoz(
        tournament: PayoutInputTournament,
        participants: List<PayoutInputParticipant>,
        shots: List<PayoutInputShot>,
    ): PayoutResult {
        val priceKop = tournament.moneyPerBallKop ?: return PayoutResult(emptyMap(), emptyList())
        val n = participants.size
        if (n < 2) return PayoutResult(emptyMap(), emptyList())
        val adjustedRate = priceKop / (n - 1)
        if (adjustedRate == 0L) return PayoutResult(emptyMap(), emptyList())

        val scores = mutableMapOf<Long, Int>()
        for (p in participants) scores[p.id] = 0
        for (s in shots) {
            if (scores.containsKey(s.participantId)) {
                scores[s.participantId] = scores[s.participantId]!! + s.pointsDelta
            }
        }

        val net = mutableMapOf<Long, Long>()
        val ids = participants.map { it.id }.sorted()
        for (i in ids.indices) {
            for (j in i + 1 until ids.size) {
                val a = ids[i]; val b = ids[j]
                val diff = (scores[a] ?: 0) - (scores[b] ?: 0)
                if (diff == 0) continue
                val amount = kotlin.math.abs(diff.toLong()) * adjustedRate
                if (diff > 0) {
                    net[a] = (net[a] ?: 0L) + amount
                    net[b] = (net[b] ?: 0L) - amount
                } else {
                    net[b] = (net[b] ?: 0L) + amount
                    net[a] = (net[a] ?: 0L) - amount
                }
            }
        }
        return PayoutResult(scores, settleNet(net))
    }

    /**
     * Расчёт выплат за встречу (per_match): победитель каждой партии получает
     * moneyPerBallKop (здесь = цена встречи) от каждого остального участника.
     * `gameWinners` — winnerParticipantId каждой завершённой партии.
     */
    fun computePerMatch(
        tournament: PayoutInputTournament,
        participants: List<PayoutInputParticipant>,
        gameWinners: List<Long?>,
    ): PayoutResult {
        val ids = participants.map { it.id }.toSet()
        // scores = число побед за встречу
        val scores = mutableMapOf<Long, Int>()
        for (p in participants) scores[p.id] = 0
        for (w in gameWinners) if (w != null && ids.contains(w)) scores[w] = (scores[w] ?: 0) + 1

        val priceKop = tournament.moneyPerBallKop ?: return PayoutResult(scores, emptyList())

        val net = mutableMapOf<Long, Long>()
        for (w in gameWinners) {
            if (w == null || !ids.contains(w)) continue
            for (pid in ids) {
                if (pid == w) continue
                net[w] = (net[w] ?: 0L) + priceKop
                net[pid] = (net[pid] ?: 0L) - priceKop
            }
        }
        return PayoutResult(scores, settleNet(net))
    }
}
