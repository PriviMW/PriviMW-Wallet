package com.privimemobile.ui.chat.scroll

import com.privimemobile.protocol.ChatMessage
import com.privimemobile.protocol.FileAttachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatListDeriveTest {

    private fun msg(id: String, sent: Boolean, from: String = "a", ts: Long = 1000L) =
        ChatMessage(id, from, "b", "t", ts, sent)

    private fun dateLabelFor(@Suppress("UNUSED_PARAMETER") m: ChatMessage) = "Today"

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
            curDateLabel = "Today",
            dateLabelFor = ::dateLabelFor,
        )
        assertTrue(flags.showDateSep)
        assertTrue(flags.isFirstInCluster)
        assertFalse(flags.isLastInCluster)
        assertFalse(flags.showTimestamp)

        val flagsNewer = ChatListDerive.computeClusterFlags(
            index = 0,
            reversedMessages = reversed,
            curDateLabel = "Today",
            dateLabelFor = ::dateLabelFor,
        )
        assertFalse(flagsNewer.showDateSep)
        assertFalse(flagsNewer.isFirstInCluster)
        assertTrue(flagsNewer.isLastInCluster)
        assertTrue(flagsNewer.showTimestamp)
    }

    @Test
    fun albumGroups_consecutiveImagesSameSender() {
        fun image(id: String, sent: Boolean, ts: Long) = ChatMessage(
            id = id,
            from = if (sent) "me" else "them",
            to = "x",
            text = "",
            timestamp = ts,
            sent = sent,
            type = "file",
            file = FileAttachment(mime = "image/jpeg"),
        )
        val reversed = listOf(
            image("3", sent = true, ts = 1020L),
            image("2", sent = true, ts = 1010L),
            image("1", sent = true, ts = 1000L),
            msg("t", sent = false, ts = 500L),
        )
        val layout = ChatListDerive.computeAlbumGroups(reversed)
        assertEquals(listOf("3", "2", "1"), layout.groups["3"])
        assertEquals(setOf("2", "1"), layout.skipIds)
    }

    @Test
    fun albumGroups_skipsCaptionedImages() {
        val withCaption = ChatMessage(
            id = "c",
            from = "me",
            to = "x",
            text = "look",
            timestamp = 1000L,
            sent = true,
            type = "file",
            file = FileAttachment(mime = "image/jpeg"),
        )
        val layout = ChatListDerive.computeAlbumGroups(listOf(withCaption))
        assertTrue(layout.groups.isEmpty())
        assertTrue(layout.skipIds.isEmpty())
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
            curDateLabel = "Today",
            dateLabelFor = { if (it.id == "1") "Yesterday" else "Today" },
        )
        assertFalse(flags.sameAsPrev)
        assertFalse(flags.sameAsNext)
    }

    @Test
    fun clusterFlags_albumAnchorIsFirstInClusterWhenOnlyAlbumFromSender() {
        fun image(id: String, from: String, ts: Long) = ChatMessage(
            id = id,
            from = from,
            to = "g",
            text = "",
            timestamp = ts,
            sent = false,
            type = "file",
            file = FileAttachment(mime = "image/jpeg"),
        )
        val reversed = listOf(
            image("3", from = "fae", ts = 1020L),
            image("2", from = "fae", ts = 1010L),
            image("1", from = "fae", ts = 1000L),
            msg("older", sent = false, from = "other", ts = 500L),
        )
        val albums = ChatListDerive.computeAlbumGroups(reversed)
        val flags = ChatListDerive.computeClusterFlags(
            index = 0,
            reversedMessages = reversed,
            curDateLabel = "Today",
            albumGroups = albums.groups,
            dateLabelFor = ::dateLabelFor,
        )
        assertTrue(flags.isFirstInCluster)
        assertTrue(flags.isLastInCluster)
    }
}
