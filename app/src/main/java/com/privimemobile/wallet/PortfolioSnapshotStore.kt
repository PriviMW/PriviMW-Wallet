package com.privimemobile.wallet

import com.privimemobile.protocol.SecureStorage
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores hourly portfolio snapshots (raw BEAM groth + all currency rates) and computes change.
 *
 * By storing the raw BEAM balance and per-currency rates at snapshot time, we can
 * recompute the portfolio value in ANY currency on demand. This means currency changes
 * do not reset the change history — the 24h change is always accurate for the selected currency.
 *
 * Each snapshot: { "ts": epochSeconds, "groth": beamGroth, "rates": { "usd": 0.02, ... } }
 */
object PortfolioSnapshotStore {

    private const val KEY_SNAPSHOTS = "portfolio_snapshots_v2"
    private const val MAX_AGE_DAYS = 30

    /**
     * Save a new snapshot. Skips write if too recent (<5 min) and balance change <1%.
     * Updates the last snapshot if within 1 hour, otherwise appends new.
     */
    fun saveSnapshot(beamGroth: Long, rates: Map<String, Double>) {
        val now = System.currentTimeMillis() / 1000
        val snapshots = getSnapshots().toMutableList()
        val last = snapshots.lastOrNull()

        if (last != null) {
            val timeDiff = now - last.ts
            val valueDiff = if (last.groth > 0)
                kotlin.math.abs(beamGroth - last.groth).toDouble() / last.groth.toDouble()
            else 1.0
            if (timeDiff < 300 && valueDiff < 0.01) return // skip: too recent and too similar
            if (timeDiff < 3600) {
                snapshots[snapshots.lastIndex] = Snapshot(now, beamGroth, rates)
            } else {
                snapshots.add(Snapshot(now, beamGroth, rates))
            }
        } else {
            snapshots.add(Snapshot(now, beamGroth, rates))
        }

        val cutoff = now - (MAX_AGE_DAYS * 24 * 3600)
        val pruned = snapshots.filter { it.ts >= cutoff }
        putSnapshots(pruned)
    }

    /**
     * Compute 24h portfolio change for the given currency.
     * Finds snapshot closest to 24h ago (window 20-28h), recomputes old value
     * using the stored rates for that currency.
     *
     * Returns (diff, percent) or null if no qualifying snapshot.
     */
    fun get24hChange(currentValue: Double, currency: String): Pair<Double, Double>? {
        val now = System.currentTimeMillis() / 1000
        val snapshots = getSnapshots()
        if (snapshots.isEmpty()) return null

        val targetTs = now - 86400
        val windowMin = targetTs - (4 * 3600)
        val windowMax = targetTs + (4 * 3600)

        val rateKey = "beam_${currency.lowercase()}"
        val candidate = snapshots
            .filter { it.ts in windowMin..windowMax && it.rates.containsKey(rateKey) }
            .minByOrNull { kotlin.math.abs(it.ts - targetTs) }
            ?: return null

        val oldRate = candidate.rates[rateKey] ?: return null
        val oldValue = candidate.groth.toDouble() / 100_000_000.0 * oldRate
        val diff = currentValue - oldValue
        val percent = if (oldValue > 0) (diff / oldValue) * 100 else 0.0
        return Pair(diff, percent)
    }

    private fun getSnapshots(): List<Snapshot> {
        val json = SecureStorage.getString(KEY_SNAPSHOTS) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                val ratesObj = obj.optJSONObject("rates") ?: return@mapNotNull null
                val rates = mutableMapOf<String, Double>()
                ratesObj.keys().forEach { key -> rates[key] = ratesObj.getDouble(key) }
                Snapshot(obj.getLong("ts"), obj.getLong("groth"), rates)
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun putSnapshots(snapshots: List<Snapshot>) {
        val arr = JSONArray()
        for (s in snapshots) {
            val obj = JSONObject()
            obj.put("ts", s.ts)
            obj.put("groth", s.groth)
            val ratesObj = JSONObject()
            for ((key, value) in s.rates) { ratesObj.put(key, value) }
            obj.put("rates", ratesObj)
            arr.put(obj)
        }
        SecureStorage.putString(KEY_SNAPSHOTS, arr.toString())
    }

    private data class Snapshot(
        val ts: Long,
        val groth: Long,
        val rates: Map<String, Double>,
    )
}
