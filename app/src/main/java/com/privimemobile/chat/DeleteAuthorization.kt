package com.privimemobile.chat

/**
 * Delete-for-everyone authorization for group chats.
 *
 * Group SBBS payloads share one key per group; the `from` field is not cryptographically bound.
 * Honest clients only delete their own messages; receivers must enforce sender or admin rules.
 */
object DeleteAuthorization {

    /** Group roles: 0=member, 1=admin, 2=creator, 3=banned (see [GroupEntity.myRole]). */
    fun canDeleteGroupMessage(deleterRole: Int, deleterHandle: String, messageSenderHandle: String): Boolean {
        if (deleterRole == 3) return false
        val deleter = normalizeHandle(deleterHandle)
        val sender = normalizeHandle(messageSenderHandle)
        if (deleter.isEmpty() || sender.isEmpty()) return false
        if (deleter.equals(sender, ignoreCase = true)) return true
        return deleterRole >= 1
    }

    fun normalizeHandle(handle: String): String = handle.removePrefix("@").trim()
}
