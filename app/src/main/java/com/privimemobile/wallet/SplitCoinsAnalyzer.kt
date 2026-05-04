package com.privimemobile.wallet

/**
 * UTXO split analysis — ported from VSnation Beam-Light-Wallet (app.js).
 *
 * Detects problematic UTXO distributions and recommends split counts
 * so users can avoid "all funds locked in one coin" transaction failures.
 */
object SplitCoinsAnalyzer {

    const val GROTH = 100_000_000L

    enum class Severity { OK, WARNING, CRITICAL }

    data class Recommendation(
        val assetId: Int,
        val count: Int,
        val totalAmount: Long,
        val largest: Long,
        val largestPct: Double,
        val severity: Severity,
        val message: String,
        val suggestedSplits: Int,
    )

    data class UtxoInfo(
        val amount: Long,
        val status: Int,
        val assetId: Int,
        val isShielded: Boolean = false,
    )

    /**
     * Analyze UTXOs grouped by asset and return split recommendations.
     * Only "available" (status==1) UTXOs are considered.
     * LP tokens are skipped.
     */
    fun analyze(utxos: List<UtxoInfo>): Map<Int, Recommendation> {
        val byAsset = mutableMapOf<Int, MutableList<UtxoInfo>>()
        utxos.filter { it.status == 1 }.forEach { u ->
            byAsset.getOrPut(u.assetId) { mutableListOf() }.add(u)
        }

        val recommendations = mutableMapOf<Int, Recommendation>()

        for ((assetId, assetUtxos) in byAsset) {
            val totalAmount = assetUtxos.sumOf { it.amount }
            val count = assetUtxos.size
            if (totalAmount == 0L) continue

            val largest = assetUtxos.maxOf { it.amount }
            val largestPct = largest.toDouble() / totalAmount * 100.0
            val remainder = totalAmount - largest
            val remainderInsufficient = remainder < 100_000L // < 0.001 BEAM (insufficient for fees)

            var severity = Severity.OK
            var message = ""
            var suggestedSplits = 0

            when {
                count == 1 -> {
                    severity = Severity.CRITICAL
                    message = "All funds in 1 coin — any transaction will lock your entire balance"
                    suggestedSplits = minOf(10, maxOf(3, (totalAmount.toDouble() / (10 * GROTH)).toInt().coerceAtLeast(2)))
                }
                largestPct > 90.0 && remainderInsufficient -> {
                    severity = Severity.CRITICAL
                    message = "One coin holds ${largestPct.toInt()}% — remainder too small to cover fees"
                    suggestedSplits = minOf(10, maxOf(3, (totalAmount.toDouble() / (10 * GROTH)).toInt().coerceAtLeast(2)))
                }
                count == 2 && largestPct > 80.0 -> {
                    severity = Severity.WARNING
                    message = "Only 2 coins, one dominates — consider splitting"
                    suggestedSplits = 4
                }
                largestPct > 90.0 -> {
                    severity = Severity.WARNING
                    message = "One coin dominates your balance"
                    suggestedSplits = 3
                }
            }

            if (severity != Severity.OK) {
                recommendations[assetId] = Recommendation(
                    assetId = assetId,
                    count = count,
                    totalAmount = totalAmount,
                    largest = largest,
                    largestPct = largestPct,
                    severity = severity,
                    message = message,
                    suggestedSplits = suggestedSplits,
                )
            }
        }

        return recommendations
    }

    /**
     * Build split coin amounts for tx_split API call.
     * Returns list of coin amounts + the fee that will be deducted.
     */
    fun buildSplitCoins(totalAmount: Long, splitCount: Int, isBeam: Boolean): SplitResult {
        val fee = maxOf(50000L + splitCount * 20000L, 100000L)

        val splitAmount = if (isBeam) {
            (totalAmount - fee) / splitCount
        } else {
            totalAmount / splitCount
        }

        val coins = mutableListOf<Long>()
        for (i in 0 until splitCount - 1) {
            coins.add(splitAmount)
        }
        val remainder = if (isBeam) {
            totalAmount - fee - splitAmount * (splitCount - 1)
        } else {
            totalAmount - splitAmount * (splitCount - 1)
        }
        coins.add(remainder)

        return SplitResult(coins = coins, fee = fee)
    }

    data class SplitResult(
        val coins: List<Long>,
        val fee: Long,
    )
}
