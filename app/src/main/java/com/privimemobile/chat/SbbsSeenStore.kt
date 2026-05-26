package com.privimemobile.chat

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Persists SBBS inbox message ids we have already handled.
 * read_messages replays the full inbox on some wallets; without this, every poll
 * re-processes 20k+ messages (~35–50s) and blocks user sends.
 */
object SbbsSeenStore {
    private const val TAG = "SbbsSeenStore"
    private const val FILE_NAME = "sbbs_seen_ids.txt"
    private const val MAX_IDS = 30_000
    /** When at cap, drop this many oldest entries (LinkedHashSet insertion order). */
    private const val EVICT_COUNT = MAX_IDS / 2

    private val seen = java.util.Collections.synchronizedSet(java.util.LinkedHashSet<Long>())
    @Volatile private var loaded = false
    @Volatile private var dirty = false

    fun ensureLoaded(ctx: Context) {
        if (loaded) return
        synchronized(seen) {
            if (loaded) return
            try {
                val file = File(ctx.applicationContext.filesDir, FILE_NAME)
                if (file.exists()) {
                    file.readLines().forEach { line ->
                        line.trim().toLongOrNull()?.let { seen.add(it) }
                    }
                }
                loaded = true
                Log.d(TAG, "Loaded ${seen.size} seen SBBS ids")
            } catch (e: Exception) {
                Log.w(TAG, "Load failed: ${e.message}")
                loaded = true
            }
        }
    }

    fun isSeen(ctx: Context, sbbsId: Long?): Boolean {
        if (sbbsId == null) return false
        ensureLoaded(ctx)
        return seen.contains(sbbsId)
    }

    /** @return true if this id is new (caller should process); false = replay, skip. */
    fun claim(ctx: Context, sbbsId: Long?): Boolean {
        if (sbbsId == null) return true
        ensureLoaded(ctx)
        synchronized(seen) {
            if (seen.contains(sbbsId)) return false
            if (seen.size >= MAX_IDS) {
                evictOldestLocked()
            }
            seen.add(sbbsId)
            dirty = true
            return true
        }
    }

    /** Drop oldest half of seen ids (caller must hold lock on [seen]). */
    private fun evictOldestLocked() {
        var removed = 0
        val iter = seen.iterator()
        while (iter.hasNext() && removed < EVICT_COUNT) {
            iter.next()
            iter.remove()
            removed++
        }
        Log.w(TAG, "Seen-id set full — evicted $removed oldest ids (${seen.size} remain)")
    }

    fun flush(ctx: Context) {
        if (!dirty) return
        synchronized(seen) {
            if (!dirty) return
            try {
                val file = File(ctx.applicationContext.filesDir, FILE_NAME)
                file.writeText(seen.joinToString("\n"))
                dirty = false
                Log.d(TAG, "Saved ${seen.size} seen SBBS ids")
            } catch (e: Exception) {
                Log.w(TAG, "Save failed: ${e.message}")
            }
        }
    }
}
