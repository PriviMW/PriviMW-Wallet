package com.privimemobile.chat

/**
 * Delete-for-everyone authorization for group chats.
 *
 * Group SBBS payloads share one key per group; the `from` field is not cryptographically bound.
 * Honest clients only delete their own messages; receivers must enforce sender or admin rules.
 */
object DeleteAuthorization {

    /** Group roles: 0=member, 1=admin, 2=creator, 3=banned (see GroupEntity.myRole). */

    /** Show delete-for-everyone in UI (own message, or group admin/creator moderating). */
    fun canOfferDeleteForEveryone(isGroup: Boolean, myGroupRole: Int, messageSentByMe: Boolean): Boolean {
        if (messageSentByMe) return true
        if (!isGroup || myGroupRole == 3) return false
        return myGroupRole >= 1
    }

    fun isGroupModerator(isGroup: Boolean, myGroupRole: Int): Boolean =
        isGroup && myGroupRole >= 1 && myGroupRole != 3

    /** Selected messages to fan out on SBBS delete-for-everyone (admin: all selected; member: own only). */
    fun <T> filterForDeleteForEveryoneBroadcast(
        isGroup: Boolean,
        myGroupRole: Int,
        selected: List<T>,
        isSentByMe: (T) -> Boolean,
    ): List<T> = if (isGroupModerator(isGroup, myGroupRole)) selected else selected.filter(isSentByMe)

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
