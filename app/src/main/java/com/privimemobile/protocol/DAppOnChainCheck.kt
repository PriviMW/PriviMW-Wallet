package com.privimemobile.protocol

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Bounded wait for on-chain DApp Store queries during launch.
 * Prevents [DAppStore.checkAndUpdate] from blocking navigation indefinitely.
 */
object DAppOnChainCheck {
    private const val TAG = "DAppOnChainCheck"

    enum class Outcome {
        /** Store check applied a newer package — caller should refresh list, not navigate yet. */
        Updated,
        /** No update; safe to open the installed copy. */
        Unchanged,
        /** Chain query did not finish in time — open installed copy. */
        TimedOut,
    }

    suspend fun runCheck(
        timeoutMs: Long = Config.DAPP_STORE_ON_CHAIN_TIMEOUT_MS,
        check: suspend () -> Boolean,
    ): Outcome {
        return try {
            when (withTimeout(timeoutMs) { check() }) {
                true -> Outcome.Updated
                false -> Outcome.Unchanged
            }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "On-chain DApp Store check timed out after ${timeoutMs}ms")
            Outcome.TimedOut
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "On-chain DApp Store check failed: ${e.message}")
            Outcome.Unchanged
        }
    }
}
