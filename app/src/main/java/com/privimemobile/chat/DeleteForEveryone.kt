package com.privimemobile.chat

/**
 * Delete-for-everyone SBBS payloads.
 * Each control message needs a unique [ts] (second-granularity); bursts with the same [ts] can be dropped by the wallet.
 * [msg_ts] is the target message timestamp to remove.
 */
object DeleteForEveryone {
    fun payload(myHandle: String, to: String, msgTimestamp: Long): Map<String, Any?> {
        return payloads(myHandle, to, listOf(msgTimestamp)).single()
    }

    fun payloads(myHandle: String, to: String, msgTimestamps: List<Long>): List<Map<String, Any?>> {
        if (msgTimestamps.isEmpty()) return emptyList()
        var eventTs = System.currentTimeMillis() / 1000
        return msgTimestamps.map { msgTs ->
            mapOf(
                "v" to 1,
                "t" to "delete",
                "ts" to eventTs++,
                "from" to myHandle,
                "to" to to,
                "msg_ts" to msgTs,
            )
        }
    }
}
