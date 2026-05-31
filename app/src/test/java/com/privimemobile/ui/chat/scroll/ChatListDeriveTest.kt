package com.privimemobile.ui.chat.scroll

import com.privimemobile.protocol.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatListDeriveTest {

    private fun msg(id: String, sent: Boolean, from: String = "a", ts: Long = 1000L) =
        ChatMessage(id, from, "b", "t", ts, sent)

    @Test
    fun messageItemKey_pollIncludesPollData() {
        assertEquals("id1", ChatListDerive.messageItemKey("id1", "dm", null))
        assertEquals("p1:{\"v\":2}", ChatListDerive.messageItemKey("p1", "poll", "{\"v\":2}"))
    }

    @Test
    fun dateSeparator_firstMessageAlwaysShows() {
        assertTrue(ChatListDerive.shouldShowDateSeparator(null, "Today"))
        assertTrue(ChatListDerive.shouldShowDateSeparator("Yesterday", "Today"))
        assertFalse(ChatListDerive.shouldShowDateSeparator("Today", "Today"))
    }

    @Test
    fun clusterFlags_groupsWithin60sSameDay() {
        val reversed = listOf(
            msg("2", sent = true, from = "me", ts = 1050L),
            msg("1", sent = true, from = "me", ts = 1000L),
        )
        val flags = ChatListDerive.computeClusterFlags(
            index = 1,
            reversedMessages = reversed,
            prevDateLabel = null,
            curDateLabel = "Today",
            nextDateLabel = "Today",
        )
        assertTrue(flags.showDateSep)
        assertTrue(flags.isFirstInCluster)
        assertFalse(flags.isLastInCluster)
        assertFalse(flags.showTimestamp)

        val flagsNewer = ChatListDerive.computeClusterFlags(
            index = 0,
            reversedMessages = reversed,
            prevDateLabel = "Today",
            curDateLabel = "Today",
            nextDateLabel = null,
        )
        assertFalse(flagsNewer.showDateSep)
        assertFalse(flagsNewer.isFirstInCluster)
        assertTrue(flagsNewer.isLastInCluster)
        assertTrue(flagsNewer.showTimestamp)
    }

    @Test
    fun clusterFlags_dateChangeBreaksCluster() {
        val reversed = listOf(
            msg("2", sent = false, ts = 2000L),
            msg("1", sent = false, ts = 1000L),
        )
        val flags = ChatListDerive.computeClusterFlags(
            index = 0,
            reversedMessages = reversed,
            prevDateLabel = "Yesterday",
            curDateLabel = "Today",
            nextDateLabel = "Yesterday",
        )
        assertFalse(flags.sameAsPrev)
        assertFalse(flags.sameAsNext)
    }
}
